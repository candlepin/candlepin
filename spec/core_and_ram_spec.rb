require 'spec_helper'
require 'candlepin_scenarios'

describe 'Core and RAM Limiting' do
  include CandlepinMethods

  before(:each) do
    @owner = create_owner random_string('test_owner')

    # Create a product limiting by core and sockets and ram.
    @core_and_socket_product = create_product(nil, random_string("Product1"), :attributes =>
                {:version => '1.2',
                 :cores => 16,
                 :ram => 8,
                 :sockets => 4,
                 :warning_period => 15,
                 :management_enabled => true,
                 :support_level => 'standard',
                 :support_type => 'excellent',})
    @core_socket_sub = @cp.create_subscription(@owner['key'], @core_and_socket_product.id, 10,
                                              [], '18881', '1222')

    # Create a product limiting by core and sockets and ram for multi-entitlement.
    @core_and_socket_product_2 = create_product(nil, random_string("Product1"), :attributes =>
                {:version => '1.2',
                 :cores => 16,
                 :ram => 8,
                 :sockets => 4,
                 :warning_period => 15,
                 :management_enabled => true,
                 :support_level => 'standard',
                 :support_type => 'excellent',
                 :'multi-entitlement' => 'yes',
                 :stacking_id => '88888888'})
    @core_socket_sub_2 = @cp.create_subscription(@owner['key'], @core_and_socket_product_2.id, 100,
                                              [], '18882', '1223')


    # Refresh pools so that the subscription pools will be available to the test systems.
    @cp.refresh_pools(@owner['key'])

    @user = user_client(@owner, random_string('test-user'))
  end

  it 'consumer status should be valid when cores, ram and sockets are covered' do
    system = consumer_client(@user, random_string('system1'), :system, nil,
                {'system.certificate_version' => '3.2',
                 'cpu.core(s)_per_socket' => '4',
                 'memory.memtotal' => '8000000',
                 'cpu.cpu_socket(s)' => '4'})
    installed = [
        {'productId' => @core_and_socket_product.id, 'productName' => @core_and_socket_product.name}
    ]
    system.update_consumer({:installedProducts => installed})

    pool = find_pool(@owner.id, @core_socket_sub.id)
    pool.should_not == nil

    entitlement = system.consume_pool(pool.id, {:quantity => 1})
    entitlement.should_not == nil

    compliance_status = system.get_compliance(consumer_id=system.uuid)
    compliance_status['status'].should == 'valid'
    compliance_status['compliant'].should == true
    compliant_products = compliance_status['compliantProducts']
    compliant_products.should have_key(@core_and_socket_product.id)
  end

  # entitle by getting single entitlement and checking status
  it 'consumer status should be partial when consumer core only not covered' do
    system = consumer_client(@user, random_string('system1'), :system, nil,
                {'system.certificate_version' => '3.2',
                 'cpu.core(s)_per_socket' => '8',
                 'memory.memtotal' => '8000000',
                 'cpu.cpu_socket(s)' => '4'})
    installed = [
        {'productId' => @core_and_socket_product.id, 'productName' => @core_and_socket_product.name}
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

  it 'consumer status should be partial when consumer ram only not covered' do
    system = consumer_client(@user, random_string('system1'), :system, nil,
                {'system.certificate_version' => '3.2',
                 'cpu.core(s)_per_socket' => '4',
                 'memory.memtotal' => '16000000',
                 'cpu.cpu_socket(s)' => '4'})
    installed = [
        {'productId' => @core_and_socket_product.id, 'productName' => @core_and_socket_product.name}
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

  it 'consumer status should be partial when consumer sockets only not covered' do
    system = consumer_client(@user, random_string('system1'), :system, nil,
                {'system.certificate_version' => '3.2',
                 'cpu.core(s)_per_socket' => '2',
                 'memory.memtotal' => '8000000',
                 'cpu.cpu_socket(s)' => '8'})
    installed = [
        {'productId' => @core_and_socket_product.id, 'productName' => @core_and_socket_product.name}
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

  # autobind tests. can we get a full bind to cover the limiting reagent?
  it 'consumer status should be valid when consumer core requires extra entitlement' do
    system = consumer_client(@user, random_string('system1'), :system, nil,
                {'system.certificate_version' => '3.2',
                 'cpu.core(s)_per_socket' => '8',
                 'memory.memtotal' => '8000000',
                 'cpu.cpu_socket(s)' => '4'})
    installed = [
        {'productId' => @core_and_socket_product_2.id, 'productName' => @core_and_socket_product_2.name}
    ]
    system.update_consumer({:installedProducts => installed})

    entitlement = system.consume_product(@core_and_socket_product_2.id)[0]
    entitlement.should_not == nil
    entitlement.quantity.should == 2

    compliance_status = system.get_compliance(consumer_id=system.uuid)
    compliance_status['status'].should == 'valid'
    compliance_status['compliant'].should == true
    compliant_products = compliance_status['compliantProducts']
    compliant_products.should have_key(@core_and_socket_product_2.id)
  end

  it 'consumer status should be invalid when consumer ram exceeds entitlement' do
    system = consumer_client(@user, random_string('system1'), :system, nil,
                {'system.certificate_version' => '3.2',
                 'cpu.core(s)_per_socket' => '4',
                 'memory.memtotal' => '16000000',
                 'cpu.cpu_socket(s)' => '4'})
    installed = [
        {'productId' => @core_and_socket_product_2.id, 'productName' => @core_and_socket_product_2.name}
    ]
    system.update_consumer({:installedProducts => installed})


    pool = find_pool(@owner.id, @core_socket_sub_2.id)
    pool.should_not == nil

    entitlement = system.consume_pool(pool.id, {:quantity => 1})
    entitlement.should_not == nil

    compliance_status = system.get_compliance(consumer_id=system.uuid)
    compliance_status['status'].should == 'partial'
    compliance_status['compliant'].should == false
    partially_compliant_products = compliance_status['partiallyCompliantProducts']
    partially_compliant_products.size.should == 1
    partially_compliant_products.should have_key(@core_and_socket_product_2.id)
  end

  it 'consumer status should be valid when consumer socket requires extra entitlement' do
    system = consumer_client(@user, random_string('system1'), :system, nil,
                {'system.certificate_version' => '3.2',
                 'cpu.core(s)_per_socket' => '2',
                 'memory.memtotal' => '8000000',
                 'cpu.cpu_socket(s)' => '8'})
    installed = [
        {'productId' => @core_and_socket_product_2.id, 'productName' => @core_and_socket_product_2.name}
    ]
    system.update_consumer({:installedProducts => installed})

    entitlement = system.consume_product(@core_and_socket_product_2.id)[0]
    entitlement.should_not == nil
    entitlement.quantity.should == 2

    compliance_status = system.get_compliance(consumer_id=system.uuid)
    compliance_status['status'].should == 'valid'
    compliance_status['compliant'].should == true
    compliant_products = compliance_status['compliantProducts']
    compliant_products.should have_key(@core_and_socket_product_2.id)
  end

  it 'should allow consumer to be compliant for socket and core quantity across stacked pools' do
    owner = create_owner random_string('test_owner')
    user = user_client(owner, random_string('test-user'))
    system = consumer_client(user, random_string('system'), :system, nil,
                {'system.certificate_version' => '3.2',
                 'cpu.cpu_socket(s)' => '4',
                 'cpu.core(s)_per_socket' => '8'})
    prod1 = create_product(random_string('product'), random_string('product'), :attributes =>
                          { :sockets => '2',
                            :cores => '8',
                            :'multi-entitlement' => 'yes',
                            :stacking_id => '9999'})
    prod2 = create_product(random_string('product'), random_string('product'), :attributes =>
                          { :sockets => '2',
                            :cores => '8',
                            :'multi-entitlement' => 'yes',
                            :stacking_id => '9999'})
    installed = [
        {'productId' => prod1.id, 'productName' => prod1.name},
    ]
    system.update_consumer({:installedProducts => installed})

    @cp.create_subscription(owner['key'], prod1.id, 2)
    @cp.create_subscription(owner['key'], prod2.id, 3)
    @cp.refresh_pools(owner['key'])

    entitlements=[]
    for pool in system.list_owner_pools(owner['key']) do
        system.consume_pool(pool.id, {:quantity => 2})
    end
    total = 0
    system.list_entitlements.each {|ent|  total += ent.quantity}
    total.should == 4

    compliance_status = system.get_compliance(consumer_id=system.uuid)
    compliance_status['status'].should == 'valid'
    compliance_status['compliant'].should == true
    compliant_products = compliance_status['compliantProducts']
    compliant_products.should have_key(prod1.id)
  end
end

