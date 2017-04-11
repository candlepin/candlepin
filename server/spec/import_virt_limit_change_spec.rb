require 'spec_helper'
require 'candlepin_scenarios'
require 'json'
require 'zip/zip'

describe 'Import Virt Limit Change', :serial => true do

  include CandlepinMethods
  include VirtHelper

  before(:all) do
    @cp = Candlepin.new('admin', 'admin')

    @cp_export = StandardExporter.new
    @cp_export.create_candlepin_export()
    @cp_export_file = @cp_export.export_filename

    @import_owner = @cp.create_owner(random_string("test_owner"))
    @cp.import(@import_owner['key'], @cp_export_file)

    @owner_client = user_client(@import_owner, random_string('user'))
    @system = consumer_client(@owner_client, random_string('host'), :hypervisor)

    @exporters = [@cp_export]
  end

  after(:all) do
    @cp.delete_owner(@import_owner['key'])
    @exporters.each do |e|
      e.cleanup()
    end
  end

  it 'allows change to virt limit subscriptions hosted' do
    skip("candlepin running in standalone mode") if not is_hosted?
    # check starting quantity of derived pool
    bonus_pool = @cp.list_pools(:owner => @import_owner.id, :product => 'derived_product_virt_limit')[0]
    bonus_pool['quantity'].should == 7

    # it is not feasible to export an expired manifest.
    # there is also a bug in the ZipFile::replace api which
    # corrupts the archive checksum.
    # So we use a valid export, unzip it, tweak a product
    # and create an archive again.
    products_dir = File.join(@cp_export.export_dir, 'products')

    # pick the virt limit product and alter it
    Dir.entries(products_dir).reject{|entry| entry == "." || entry == ".."}.each do |file|
        product_file = File.join(products_dir, file)
        if file.end_with? "product_virt_limit.json"
            fileText = File.read(product_file)
            product = JSON.parse(fileText)
            product['attributes'].each do |attribute|
               if attribute['name'] == "virt_limit"
                  attribute['value'] = "4"
               end
            end
            File.write(product_file, product.to_json)
        end
    end

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
    import_record.status.should == 'SUCCESS'

    bonus_pool = @cp.list_pools(:owner => @import_owner.id, :product => 'derived_product_virt_limit')[0]
    bonus_pool['quantity'].should == 4
  end

  it 'allows change to virt limit subscriptions standalone' do
    skip("candlepin running in hosted mode") if is_hosted?
    # check starting quantity of derived pool
    bonus_pools = @cp.list_pools(:owner => @import_owner.id, :product => 'derived_product_virt_limit')
    bonus_pools.size.should == 1
    master_pool = @cp.list_pools(:owner => @import_owner.id, :product => 'product_virt_limit')[0]
    @system.consume_pool(master_pool.id, {:quantity => 1})
    bonus_pools = @cp.list_pools(:owner => @import_owner.id, :product => 'derived_product_virt_limit')
    bonus_pools.size.should == 2
    bonus_pools[0]['quantity'].should == 7
    bonus_pools[1]['quantity'].should == 7

    # it is not feasible to export an expired manifest.
    # there is also a bug in the ZipFile::replace api which
    # corrupts the archive checksum.
    # So we use a valid export, unzip it, tweak a product
    # and create an archive again.
    products_dir = File.join(@cp_export.export_dir, 'products')

    # pick the virt limit product and alter it
    Dir.entries(products_dir).reject{|entry| entry == "." || entry == ".."}.each do |file|
        product_file = File.join(products_dir, file)
        if file.end_with? "product_virt_limit.json"
            fileText = File.read(product_file)
            product = JSON.parse(fileText)
            product['attributes'].each do |attribute|
               if attribute['name'] == "virt_limit"
                  attribute['value'] = "4"
               end
            end
            File.write(product_file, product.to_json)
        end
    end

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
    import_record.status.should == 'SUCCESS'

    bonus_pools = @cp.list_pools(:owner => @import_owner.id, :product => 'derived_product_virt_limit')
    bonus_pools.size.should == 2
    bonus_pools[0]['quantity'].should == 4
    bonus_pools[1]['quantity'].should == 4
  end

end
