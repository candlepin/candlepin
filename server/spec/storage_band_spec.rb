require 'spec_helper'
require 'candlepin_scenarios'

describe 'Band Limiting' do
  include CandlepinMethods

  before(:each) do

    @owner = create_owner random_string('test_owner')

    # Create a product limiting by band.
    @ceph_product = create_product(
        random_string("storage-limited-sku"),
        random_string("Storage Limited"),
        :multiplier => 256,
        :attributes =>
                {:version => '6.4',
                 # storage_band will always be defined as 1, or not set.
                 :storage_band => 1,
                 :warning_period => 15,
                 :stacking_id => "ceph-node",
                 :'multi-entitlement' => "yes",
                 :support_level => 'standard',
                 :support_type => 'excellent'}
         )
    @ceph_sub = @cp.create_subscription(@owner['key'], @ceph_product.id, 2, [], '1888', '1234')
    @cp.refresh_pools(@owner['key'])

    @user = user_client(@owner, random_string('test-user'))

  end

  it 'pool should have the correct quantity based off of the product multiplier' do
    pool = find_pool(@owner.id, @ceph_sub.id)
    # sub.quantity * multiplier
    pool.quantity.should == 512
  end

  # band.storage.usage fact is in TB.
  it 'system status should be valid when all storage band usage is covered' do
    system = consumer_client(@user, random_string("test_system"), :system, nil,
                             { 'band.storage.usage' => "256" })
    installed_products = [{ 'productId' => @ceph_product.id, 'productName' => @ceph_product.name }]
    system.update_consumer({ :installedProducts => installed_products })

    pool = find_pool(@owner.id, @ceph_sub.id)
    pool.should_not be_nil

    system.consume_pool(pool.id, {:quantity => 256}).should_not be_nil

    status = system.get_compliance(consumer_id = system.uuid)
    status['status'].should == 'valid'
    status['compliant'].should == true
  end

  it 'system status should be partial when only part of the storage band usage is covered' do
    system = consumer_client(@user, random_string("test_system"), :system, nil,
                             { 'band.storage.usage' => "256" })
    installed_products = [{ 'productId' => @ceph_product.id, 'productName' => @ceph_product.name }]
    system.update_consumer({ :installedProducts => installed_products })

    pool = find_pool(@owner.id, @ceph_sub.id)
    pool.should_not be_nil

    system.consume_pool(pool.id, {:quantity => 128}).should_not be_nil

    status = system.get_compliance(consumer_id = system.uuid)
    status['status'].should == 'partial'
    status['compliant'].should == false
  end

  it 'storage band entitlements from same subscription can be stacked to cover entire system' do
    system = consumer_client(@user, random_string("test_system"), :system, nil,
                             { 'band.storage.usage' => "256" })
    installed_products = [{ 'productId' => @ceph_product.id, 'productName' => @ceph_product.name }]
    system.update_consumer({ :installedProducts => installed_products })

    pool = find_pool(@owner.id, @ceph_sub.id)
    pool.should_not be_nil

    # Partial stack
    system.consume_pool(pool.id, {:quantity => 128}).should_not be_nil
    status = system.get_compliance(consumer_id = system.uuid)
    status['status'].should == 'partial'
    status['compliant'].should == false

    # Complete the stack
    system.consume_pool(pool.id, {:quantity => 128}).should_not be_nil
    status = system.get_compliance(consumer_id = system.uuid)
    status['status'].should == 'valid'
    status['compliant'].should == true

    entitlements = @cp.list_entitlements(:uuid => system.uuid)
    entitlements.length.should == 2
    entitlements[0].quantity.should == 128
    entitlements[1].quantity.should == 128
  end

  it 'storage band entitlements will auto-attach correct quantity' do
    system = consumer_client(@user, random_string("test_system"), :system, nil,
                             { 'band.storage.usage' => "256" })
    installed_products = [{ 'productId' => @ceph_product.id, 'productName' => @ceph_product.name }]
    system.update_consumer({ :installedProducts => installed_products })

    entitlements = system.consume_product()
    entitlements.size.should == 1
    entitlements[0].quantity.should == 256

    status = system.get_compliance(consumer_id = system.uuid)
    status['status'].should == 'valid'
    status['compliant'].should == true

  end

  it 'storage band entitlements will auto-heal correctly' do
    system = consumer_client(@user, random_string("test_system"), :system, nil,
                             { 'band.storage.usage' => "256" })
    installed_products = [{ 'productId' => @ceph_product.id, 'productName' => @ceph_product.name }]
    system.update_consumer({ :installedProducts => installed_products })

    pool = find_pool(@owner.id, @ceph_sub.id)
    pool.should_not == nil

    entitlement = system.consume_pool(pool.id, {:quantity => 56})
    entitlement.should_not == nil

    entitlements = system.consume_product()
    entitlements.size.should == 1
    entitlements[0].quantity.should == 200

    status = system.get_compliance(consumer_id = system.uuid)
    status['status'].should == 'valid'
    status['compliant'].should == true
  end

  it 'storage band entitlement auto-attach without fact set consumes one entitlement' do
    system = consumer_client(@user, random_string("test_system"), :system, nil,
                             { })
    installed_products = [{ 'productId' => @ceph_product.id, 'productName' => @ceph_product.name }]
    system.update_consumer({ :installedProducts => installed_products })

    entitlements = system.consume_product()
    entitlements.size.should == 1
    entitlements[0].quantity.should == 1

    status = system.get_compliance(consumer_id = system.uuid)
    status['status'].should == 'valid'
    status['compliant'].should == true
  end

end
