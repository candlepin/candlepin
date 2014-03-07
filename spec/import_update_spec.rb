require 'spec_helper'
require 'candlepin_scenarios'

describe 'Import Update', :serial => true do

  include CandlepinMethods

  before(:all) do
    @cp = Candlepin.new('admin', 'admin')
    @exporter = StandardExporter.new
    base_export = @exporter.create_candlepin_export()

    @import_owner = @cp.create_owner(random_string("test_owner"))
    @import_username = random_string("import-user")
    @import_owner_client = user_client(@import_owner, @import_username)
    @cp.import(@import_owner['key'], base_export.export_filename)
    @sublist = @cp.list_subscriptions(@import_owner['key'])
  end

  after(:all) do
    @cp.delete_user(@import_username)
    @cp.delete_owner(@import_owner['key'])
    @exporter.cleanup()
  end

  it 'should successfully update the import' do
    updated_export = @exporter.create_candlepin_export_update()

    @cp.import(@import_owner['key'], updated_export.export_filename)

    @sublist.size().should == 5
    new_sublist = @cp.list_subscriptions(@import_owner['key'])
    new_sublist.size().should == 6

    hasChanged = false
    new_sublist.each do |new_sub|
      @sublist.each do |sub|
        if(sub.id == new_sub.id)
          if(sub.certificate.serial.id != new_sub.certificate.serial.id)
            hasChanged = true
          end
        end
      end
    end

    hasChanged.should be_true
  end


  it 'should remove all imported subscriptions if import has no entitlements' do
    no_ent_export = @exporter.create_candlepin_export_update_no_ent()

    @cp.import(@import_owner['key'], no_ent_export.export_filename)
    # manifest consumer
    @exporter.candlepin_client.list_entitlements().size.should == 0
    # import owner 
    @cp.list_subscriptions(@import_owner['key']).size.should == 0
  end
end
