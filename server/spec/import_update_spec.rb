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

    # Create a pre-existing content instance that will get overwritten by the content from the manifest
    content = @exporter.content[:content1]

    preexisting_content = @cp.create_content(@import_owner['key'], content['name'], content['id'], content['label'], content['type'], content['vendor'], {
      :required_tags => "dummy tags"
    })

    @cp.import(@import_owner['key'], base_export.export_filename)
    @sublist = @cp.list_subscriptions(@import_owner['key'])

    # Verify the manifest changed our content
    content = @cp.get_content(@import_owner['key'], preexisting_content.id)

    expect(content).to_not be_nil
    expect(content).to_not eq(preexisting_content)
  end

  after(:all) do
    @cp.delete_user(@import_username)
    @cp.delete_owner(@import_owner['key'])
    @exporter.cleanup()
  end

  it 'should successfully update the import' do
    product = @cp.get_product(@import_owner['key'], @exporter.products[:product1].id)
    content = @cp.get_content(@import_owner['key'], @exporter.content[:content1].id)

    expect(product).to_not be_nil
    expect(content).to_not be_nil

    updated_export = @exporter.create_candlepin_export_update()

    @cp.import(@import_owner['key'], updated_export.export_filename)

    @sublist.size().should == 6
    new_sublist = @cp.list_subscriptions(@import_owner['key'])
    new_sublist.size().should == 7

    new_sublist.each do |new_sub|
      @sublist.each do |sub|
        if(sub.id == new_sub.id)
          sub.certificate.serial.id.should_not == new_sub.certificate.serial.id
        end
      end
    end

    # Verify various updated entities have changed
    updated_product = @cp.get_product(@import_owner['key'], @exporter.products[:product1].id)
    updated_content = @cp.get_content(@import_owner['key'], @exporter.content[:content1].id)

    expect(updated_product).to_not be_nil
    expect(updated_content).to_not be_nil

    expect(updated_product).to_not eq(product)
    expect(updated_content).to_not eq(content)
  end

  it 'should remove all imported subscriptions if import has no entitlements' do

    # The manifest metadata can end up
    #  with a created date that is a fraction of a second ahead of
    #  the created date in the cp_export_metadata table.
    #  This results into the manifest metadata conflict with error
    #  "Import is the same as existing data". Hence to avoid this,
    #  adding a sleep before creating another export.
    sleep 2

    no_ent_export = @exporter.create_candlepin_export_update_no_ent()

    @cp.import(@import_owner['key'], no_ent_export.export_filename)
    # manifest consumer
    @exporter.candlepin_client.list_entitlements().size.should == 0
    # import owner
    @cp.list_subscriptions(@import_owner['key']).size.should == 0
  end
end
