require 'spec_helper'
require 'candlepin_scenarios'
require 'json'
require 'zip/zip'

describe 'Import Warning', :serial => true do

  include CandlepinMethods
  include VirtHelper

  before(:all) do
    @cp = Candlepin.new('admin', 'admin')
    pending("candlepin running in hosted mode") if is_hosted?

    @cp_export = StandardExporter.new
    @cp_export.create_candlepin_export()
    @cp_export_file = @cp_export.export_filename

    @import_owner = @cp.create_owner(random_string("test_owner"))
    @cp.import(@import_owner['key'], @cp_export_file)

    @exporters = [@cp_export]
  end

  after(:all) do
    @cp.delete_owner(@import_owner['key'])
    @exporters.each do |e|
      e.cleanup()
    end
  end

  it 'warns about inactive subscriptions' do
    # it is not feasible to export an expired manifest.
    # there is also a bug in the ZipFile::replace api which
    # corrupts the archive checksum.
    # So we use a valid export, unzip it, tweak an entitlement
    # and create an archive again.
    entitlements_dir = File.join(@cp_export.export_dir, 'entitlements')

    # pick any entitlement and expire it
    first_file = Dir.entries(entitlements_dir).reject{
	    |entry| entry == "." || entry == ".."}[0]
    entitlement_file = File.join(entitlements_dir, first_file)
    fileText = File.read(entitlement_file)
    entitlement = JSON.parse(fileText)
    entitlement['pool']['endDate'] = (Date.today - 1).strftime
    File.write(entitlement_file, entitlement.to_json)

    # create a consumer_export.zip from scratch with the updated file
    recursive_path = File.join(@cp_export.export_dir, "**", "**")
    updated_inner_zip = File.join(@cp_export.tmp_dir, "updatedinner.zip")
    Zip::ZipFile::open(updated_inner_zip, Zip::ZipFile::CREATE) {
      |zipfile|
      Dir[recursive_path].each do |file|
        if File.file?(file)
      zipfile.add(file.sub(File.join(@cp_export.tmp_dir,''),''),file)
    end
      end
    }

    #add the inner zip to a new manifest
    updated_export = File.join(@cp_export.tmp_dir, "updatedexport.zip")
    Zip::ZipFile::open(updated_export, Zip::ZipFile::CREATE) {
      |zipfile|
       zipfile.add("consumer_export.zip", updated_inner_zip)
       zipfile.add("signature", File.join(@cp_export.tmp_dir, "signature"))
    }

    #verify we get a warning
    import_record = @cp.import(@import_owner['key'],
                      updated_export,
                      {:force => ["SIGNATURE_CONFLICT", 'MANIFEST_SAME']})
    import_record.status.should == 'SUCCESS_WITH_WARNING'
    import_record.statusMessage.should == "#{@import_owner['key']} file imported forcibly."\
       "One or more inactive subscriptions found in the file."

  end

  it 'warns about no active subscriptions' do
    empty_export = @cp_export.create_candlepin_export_update_no_ent()
    import_record = @cp.import(@import_owner['key'], empty_export.export_filename)
    import_record.status.should == 'SUCCESS_WITH_WARNING'
    import_record.statusMessage.should == "#{@import_owner['key']} file imported successfully.No active subscriptions found in the file."

  end

end
