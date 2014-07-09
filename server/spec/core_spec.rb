require 'spec_helper'
require 'candlepin_scenarios'

describe 'Core Limiting' do
  include CandlepinMethods

  before(:each) do
    @owner = create_owner random_string('test_owner')

    # Create a product limiting by core only.
    @core_product = create_product(nil, random_string("Product1"), :attributes =>
                {:version => '6.4',
                 :cores => 8,
                 :warning_period => 15,
                 :management_enabled => true,
                 :support_level => 'standard',
                 :support_type => 'excellent',})
    @core_sub = @cp.create_subscription(@owner['key'], @core_product.id, 10, [], '1888', '1234')

    # Create a product limiting by core and sockets.
    @core_and_socket_product = create_product(nil, random_string("Product2"), :attributes =>
                {:version => '1.2',
                 :cores => 16,
                 :sockets => 4,
                 :warning_period => 15,
                 :management_enabled => true,
                 :support_level => 'standard',
                 :support_type => 'excellent',})
    @core_socket_sub = @cp.create_subscription(@owner['key'], @core_and_socket_product.id, 10,
                                              [], '18881', '1222')

    # Refresh pools so that the subscription pools will be available to the test systems.
    @cp.refresh_pools(@owner['key'])

    @user = user_client(@owner, random_string('test-user'))
  end

  it 'can consume core entitlement if requesting v3.2 certificate' do
    system = consumer_client(@user, random_string('system1'), :system, nil,
                {'system.certificate_version' => '3.2'})
    entitlement = system.consume_product(@core_product.id)[0]
    entitlement.should_not == nil
  end

  it 'consumer status should be valid when consumer core is covered' do
    system = consumer_client(@user, random_string('system1'), :system, nil,
                {'system.certificate_version' => '3.2',
                 # Simulate 8 cores as would be returned from system fact
                 'cpu.core(s)_per_socket' => '8'})
    installed = [
        {'productId' => @core_product.id, 'productName' => @core_product.name}
    ]
    system.update_consumer({:installedProducts => installed})

    entitlement = system.consume_product(@core_product.id)[0]
    entitlement.should_not == nil

    compliance_status = system.get_compliance(consumer_id=system.uuid)
    compliance_status['status'].should == 'valid'
    compliance_status['compliant'].should == true
    compliant_products = compliance_status['compliantProducts']
    compliant_products.should have_key(@core_product.id)
  end

  it 'consumer status should be partial when consumer core not covered' do
    system = consumer_client(@user, random_string('system1'), :system, nil,
                {'system.certificate_version' => '3.2',
                 # Simulate 16 cores as would be returned from system fact
                 'cpu.core(s)_per_socket' => '16'})
    installed = [
        {'productId' => @core_product.id, 'productName' => @core_product.name}
    ]
    system.update_consumer({:installedProducts => installed})

    pool = find_pool(@owner.id, @core_sub.id)
    pool.should_not == nil

    entitlement = system.consume_pool(pool.id, {:quantity => 1})
    entitlement.should_not == nil

    compliance_status = system.get_compliance(consumer_id=system.uuid)
    compliance_status['status'].should == 'partial'
    compliance_status['compliant'].should == false
    partially_compliant_products = compliance_status['partiallyCompliantProducts']
    partially_compliant_products.should have_key(@core_product.id)
  end

  it 'consumer status should be partial when consumer core covered but not sockets' do
    system = consumer_client(@user, random_string('system1'), :system, nil,
                {'system.certificate_version' => '3.2',
                 # Simulate 2 cores as would be returned from system fact
                 # which should be covered by the enitlement when consumed.
                 'cpu.core(s)_per_socket' => '2',
                 # Simulate system having 12 sockets which won't be covered after consuming
                 # the entitlement
                 'cpu.cpu_socket(s)' => '8'})
    installed = [
        {'productId' => @core_and_socket_product.id,
        'productName' => @core_and_socket_product.name}
    ]
    system.update_consumer({:installedProducts => installed})

    pool = find_pool(@owner.id, @core_socket_sub.id)
    pool.should_not == nil

    entitlement = system.consume_pool(pool.id, {:quantity => 1})
    entitlement.should_not == nil

    compliance_status = system.get_compliance(consumer_id=system.uuid)
    compliance_status['status'].should == 'partial'
    compliance_status['compliant'].should == false
    partially_compliant_products = compliance_status['partiallyCompliantProducts']
    partially_compliant_products.should have_key(@core_and_socket_product.id)
  end

  it 'consumer status should be partial when consumer sockets covered but not core' do
    system = consumer_client(@user, random_string('system1'), :system, nil,
                {'system.certificate_version' => '3.2',
                 # Simulate 16 cores as would be returned from system fact
                 # which will not be covered by the enitlement when consumed.
                 'cpu.core(s)_per_socket' => '8',
                 # Simulate system having 4 sockets which will be covered after consuming
                 # the entitlement
                 'cpu.cpu_socket(s)' => '4'})
    installed = [
        {'productId' => @core_and_socket_product.id,
        'productName' => @core_and_socket_product.name}
    ]
    system.update_consumer({:installedProducts => installed})

    pool = find_pool(@owner.id, @core_socket_sub.id)
    pool.should_not == nil

    entitlement = system.consume_pool(pool.id, {:quantity => 1})
    entitlement.should_not == nil

    compliance_status = system.get_compliance(consumer_id=system.uuid)
    compliance_status['status'].should == 'partial'
    compliance_status['compliant'].should == false
    partially_compliant_products = compliance_status['partiallyCompliantProducts']
    partially_compliant_products.should have_key(@core_and_socket_product.id)
  end

  it 'consumer status is valid when both core and sockets are covered' do
    system = consumer_client(@user, random_string('system1'), :system, nil,
                {'system.certificate_version' => '3.2',
                 # Simulate 8 cores as would be returned from system fact
                 # which will be covered by the enitlement when consumed.
                 'cpu.core(s)_per_socket' => '4',
                 # Simulate system having 4 sockets which will be covered after consuming
                 # the entitlement
                 'cpu.cpu_socket(s)' => '4'})
    installed = [
        {'productId' => @core_and_socket_product.id,
        'productName' => @core_and_socket_product.name}
    ]
    system.update_consumer({:installedProducts => installed})
    entitlement = system.consume_product(@core_and_socket_product.id)[0]
    entitlement.should_not == nil

    compliance_status = system.get_compliance(consumer_id=system.uuid)
    compliance_status['status'].should == 'valid'
    compliance_status['compliant'].should == true
    compliant_products = compliance_status['compliantProducts']
    compliant_products.should have_key(@core_and_socket_product.id)
  end

  it 'can heal when core limited' do
    system = consumer_client(@user, random_string('system1'), :system, nil,
                {'system.certificate_version' => '3.2',
                 # Simulate 8 cores as would be returned from system fact
                 'cpu.core(s)_per_socket' => '8'})
    installed = [
        {'productId' => @core_product.id, 'productName' => @core_product.name}
    ]
    system.update_consumer({:installedProducts => installed})

    # Perform healing
    ents = system.consume_product()
    ents.size.should == 1
  end

  it 'will not heal when system core is not covered by any entitlements' do
    system = consumer_client(@user, random_string('system1'), :system, nil,
                {'system.certificate_version' => '3.2',
                 # Simulate 12 corse as would be returned from system fact
                 'cpu.core(s)_per_socket' => '12'})
    installed = [
        {'productId' => @core_product.id, 'productName' => @core_product.name}
    ]
    system.update_consumer({:installedProducts => installed})

    # Perform healing
    ents = system.consume_product()
    ents.should == nil
  end

  it 'can heal when both core and socket limited' do
    system = consumer_client(@user, random_string('system1'), :system, nil,
                {'system.certificate_version' => '3.2',
                 # Simulate 8 cores as would be returned from system fact
                 # which will be covered by the enitlement when consumed.
                 'cpu.core(s)_per_socket' => '4',
                 # Simulate system having 4 sockets which will be covered after consuming
                 # the entitlement
                 'cpu.cpu_socket(s)' => '4'})
    installed = [
        {'productId' => @core_and_socket_product.id,
        'productName' => @core_and_socket_product.name}
    ]
    system.update_consumer({:installedProducts => installed})

    # Perform healing
    ents = system.consume_product()
    ents.size.should == 1
  end

end

