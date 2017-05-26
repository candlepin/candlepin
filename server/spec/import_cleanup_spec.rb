require 'spec_helper'
require 'candlepin_scenarios'
require 'json'

describe 'Import cleanup', :serial => true do

  include CandlepinMethods
  include VirtHelper

  before(:all) do
    @cp = Candlepin.new('admin', 'admin')
    skip("candlepin running in hosted mode") if is_hosted?

    @cp_export = StandardExporter.new
    @cp_export.create_candlepin_export()
    @cp_export_file = @cp_export.export_filename

    @candlepin_consumer = @cp_export.candlepin_client.get_consumer()
    @candlepin_consumer.unregister @candlepin_consumer['uuid']

    @import_owner = @cp.create_owner(random_string("test_owner"))
    @import_username = random_string("import-user")
    @import_owner_client = user_client(@import_owner, @import_username)
    import_record = @cp.import(@import_owner['key'], @cp_export_file)
    import_record.status.should == 'SUCCESS'
    import_record.statusMessage.should == "#{@import_owner['key']} file imported successfully."
    @exporters = [@cp_export]
  end

  after(:all) do
    @cp.delete_user(@import_username) if @import_username
    @cp.delete_owner(@import_owner['key']) if @import_owner

    if @exporters
      @exporters.each do |e|
        e.cleanup()
      end
    end
  end

  it 'should remove stack derived pool when parent ent is gone' do
    normal = @import_owner_client.list_pools({
           :owner => @import_owner['id'],
           :product => @cp_export.products[:product_vdc].id})
    # NORMAL pool
    normal.length.should == 1

    unmapped = @import_owner_client.list_pools({
           :owner => @import_owner['id'],
           :product => @cp_export.products[:product_dc].id})
    # UNMAPPED GUEST POOL
    unmapped.length.should == 1

    consumer = consumer_client(@import_owner_client, 'test-consumer')
    # Consume NORMAL Pool
    entitlement = consumer.consume_pool(normal[0].id, {:quantity => 1})
    entitlement.length.should == 1

    # STACK DERIVED POOL is created
    stack = @import_owner_client.list_pools({
           :owner => @import_owner['id'],
           :product => @cp_export.products[:product_dc].id})
           .select{ |p| p.type=="STACK_DERIVED"}
    stack.length.should == 1

    #Create a new manifest without product_vdc subscription!
    updated_export = @cp_export.create_candlepin_export_update_no_ent()
    @cp.import(@import_owner['key'], updated_export.export_filename)

    # All the pools for that owner should be removed
    normal = @import_owner_client.list_pools({:owner => @import_owner['id']} )
    normal.length.should == 0
  end

end
