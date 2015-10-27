require 'spec_helper'
require 'candlepin_scenarios'

describe 'Entitlement Migrate' do

  include CandlepinMethods

  before(:each) do
    @owner = create_owner random_string 'owner'
    @product = create_product(nil, random_string('product'),
                 :attributes => {:cores => 8})
    create_pool_and_subscription(@owner['key'], @product.id, 25)

    #create consumer
    @user = user_client(@owner, random_string('user'))
    @dist1 = consumer_client(@user, random_string('consumer'), :candlepin)
    @cp.update_consumer(:uuid => @dist1.get_consumer()['uuid'], :capabilities => ['cores'])
    @dist2 = consumer_client(@user, random_string('consumer'), :candlepin)
    @cp.update_consumer(:uuid => @dist2.get_consumer()['uuid'], :capabilities => ['cores'])
  end

  it 'should allow entire entitlement counts to be moved to another distributor' do
    pool = @dist1.list_pools(:owner => @owner['id'], :product => @product['id'])[0]
    entitlement = @dist1.consume_pool(pool.id, {:quantity => 25})[0]
    @cp.migrate_entitlement({:id => entitlement.id, :dest => @dist2.get_consumer()['uuid'], :quantity => 25})
    @dist1.list_entitlements(:product_id => @product.id).should have(0).things
    new_ent = @dist2.list_entitlements(:product_id => @product.id)[0]
    new_ent['quantity'].should == 25
    new_ent.id.should_not == entitlement.id
  end

  it 'should allow partial entitlement counts to be moved to another distributor' do
    pool = @dist1.list_pools(:owner => @owner['id'], :product => @product['id'])[0]
    org_ent = @dist1.consume_pool(pool.id, {:quantity => 25})[0]
    @cp.migrate_entitlement({:id => org_ent.id, :dest => @dist2.get_consumer()['uuid'], :quantity => 15})
    source_ent = @dist1.list_entitlements(:product_id => @product.id)[0]
    source_ent['quantity'].should == 10
    source_ent.id.should == org_ent.id
    dest_ent = @dist2.list_entitlements(:product_id => @product.id)[0]
    dest_ent['quantity'].should == 15
    dest_ent.id.should_not == org_ent.id
  end

  it 'should not allow migration when destination lacks capability' do
    pool = @dist1.list_pools(:owner => @owner['id'], :product => @product['id'])[0]
    entitlement = @dist1.consume_pool(pool.id, {:quantity => 25})[0]
    @cp.update_consumer(:uuid => @dist2.get_consumer()['uuid'], :capabilities => [])
    lambda {
      x = @cp.migrate_entitlement({:id => entitlement.id, :dest => @dist2.get_consumer()['uuid'], :quantity => 25})
    }.should raise_exception(RestClient::BadRequest)
  end

  it 'should move entire entitlement count when none specified' do
    pool = @dist1.list_pools(:owner => @owner['id'], :product => @product['id'])[0]
    entitlement = @dist1.consume_pool(pool.id, {:quantity => 25})[0]
    @cp.migrate_entitlement({:id => entitlement.id, :dest => @dist2.get_consumer()['uuid']})
    @dist1.list_entitlements(:product_id => @product.id).should have(0).things
    new_ent = @dist2.list_entitlements(:product_id => @product.id)[0]
    new_ent['quantity'].should == 25
    new_ent.id.should_not == entitlement.id
  end
end
