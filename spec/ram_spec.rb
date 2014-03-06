require 'spec_helper'
require 'candlepin_scenarios'

describe 'RAM Limiting' do
  include CandlepinMethods

  before(:each) do
    @owner = create_owner random_string('test_owner')

    # Create a product limiting by RAM only.
    @ram_product = create_product(nil, random_string("Product1"), :attributes =>
                {:version => '6.4',
                 :ram => 8,
                 :warning_period => 15,
                 :management_enabled => true,
                 :support_level => 'standard',
                 :support_type => 'excellent',})
    @ram_sub = @cp.create_subscription(@owner['key'], @ram_product.id, 10, [], '1888', '1234')

    # Create a product limiting by RAM and sockets.
    @ram_and_socket_product = create_product(nil, random_string("Product2"), :attributes =>
                {:version => '1.2',
                 :ram => 8,
                 :sockets => 4,
                 :warning_period => 15,
                 :management_enabled => true,
                 :support_level => 'standard',
                 :support_type => 'excellent',})
    @ram_socket_sub = @cp.create_subscription(@owner['key'], @ram_and_socket_product.id, 5,
                                              [], '18881', '1222')

    # Create a stackable RAM product.
    @stackable_ram_product = create_product(nil, random_string("Product1"), :attributes =>
                {:version => '1.2',
                 :ram => 2,
                 :warning_period => 15,
                 :support_level => 'standard',
                 :support_type => 'excellent',
                 :'multi-entitlement' => 'yes',
                 :stacking_id => '2421'})
    @stackable_ram_sub = @cp.create_subscription(@owner['key'], @stackable_ram_product.id, 10)


    # Refresh pools so that the subscription pools will be available to the test systems.
    @cp.refresh_pools(@owner['key'])

    @user = user_client(@owner, random_string('test-user'))
  end

  it 'can consume ram entitlement if requesting v3.1 certificate' do
    system = consumer_client(@user, random_string('system1'), :system, nil,
                {'system.certificate_version' => '3.1'})
    entitlement = system.consume_product(@ram_product.id)[0]
    entitlement.should_not == nil
  end

  it 'consumer status should be valid when consumer RAM is covered' do
    system = consumer_client(@user, random_string('system1'), :system, nil,
                {'system.certificate_version' => '3.1',
                 # Simulate 8 GB of RAM as would be returned from system fact (kb)
                 'memory.memtotal' => '8000000'})
    installed = [
        {'productId' => @ram_product.id, 'productName' => @ram_product.name}
    ]
    system.update_consumer({:installedProducts => installed})

    entitlement = system.consume_product(@ram_product.id)[0]
    entitlement.should_not == nil

    compliance_status = system.get_compliance(consumer_id=system.uuid)
    compliance_status['status'].should == 'valid'
    compliance_status['compliant'].should == true
    compliant_products = compliance_status['compliantProducts']
    compliant_products.should have_key(@ram_product.id)
  end

  it 'consumer status should be partial when consumer RAM not covered' do
    system = consumer_client(@user, random_string('system1'), :system, nil,
                {'system.certificate_version' => '3.1',
                 # Simulate 16 GB of RAM as would be returned from system fact (kb)
                 'memory.memtotal' => '16000000'})
    installed = [
        {'productId' => @ram_product.id, 'productName' => @ram_product.name}
    ]
    system.update_consumer({:installedProducts => installed})

    pool = find_pool(@owner.id, @ram_sub.id)
    pool.should_not == nil

    entitlement = system.consume_pool(pool.id, {:quantity => 1})
    entitlement.should_not == nil

    compliance_status = system.get_compliance(consumer_id=system.uuid)
    compliance_status['status'].should == 'partial'
    compliance_status['compliant'].should == false
    partially_compliant_products = compliance_status['partiallyCompliantProducts']
    partially_compliant_products.should have_key(@ram_product.id)
  end

  it 'consumer status should be partial when consumer RAM covered but not sockets' do
    system = consumer_client(@user, random_string('system1'), :system, nil,
                {'system.certificate_version' => '3.1',
                 # Simulate 8 GB of RAM as would be returned from system fact (kb)
                 # which should be covered by the enitlement when consumed.
                 'memory.memtotal' => '8000000',
                 # Simulate system having 12 sockets which won't be covered after consuming
                 # the entitlement
                 'cpu.cpu_socket(s)' => '12'})
    installed = [
        {'productId' => @ram_and_socket_product.id,
        'productName' => @ram_and_socket_product.name}
    ]
    system.update_consumer({:installedProducts => installed})

    pool = find_pool(@owner.id, @ram_socket_sub.id)
    pool.should_not == nil

    entitlement = system.consume_pool(pool.id, {:quantity => 1})
    entitlement.should_not == nil

    compliance_status = system.get_compliance(consumer_id=system.uuid)
    compliance_status['status'].should == 'partial'
    compliance_status['compliant'].should == false
    partially_compliant_products = compliance_status['partiallyCompliantProducts']
    partially_compliant_products.should have_key(@ram_and_socket_product.id)
  end

  it 'consumer status should be partial when consumer sockets covered but not RAM' do
    system = consumer_client(@user, random_string('system1'), :system, nil,
                {'system.certificate_version' => '3.1',
                 # Simulate 16 GB of RAM as would be returned from system fact (kb)
                 # which will not be covered by the enitlement when consumed.
                 'memory.memtotal' => '16000000',
                 # Simulate system having 4 sockets which will be covered after consuming
                 # the entitlement
                 'cpu.cpu_socket(s)' => '4'})
    installed = [
        {'productId' => @ram_and_socket_product.id,
        'productName' => @ram_and_socket_product.name}
    ]
    system.update_consumer({:installedProducts => installed})

    pool = find_pool(@owner.id, @ram_socket_sub.id)
    pool.should_not == nil

    entitlement = system.consume_pool(pool.id, {:quantity => 1})
    entitlement.should_not == nil

    compliance_status = system.get_compliance(consumer_id=system.uuid)
    compliance_status['status'].should == 'partial'
    compliance_status['compliant'].should == false
    partially_compliant_products = compliance_status['partiallyCompliantProducts']
    partially_compliant_products.should have_key(@ram_and_socket_product.id)
  end

  it 'consumer status is valid when both RAM and sockets are covered' do
    system = consumer_client(@user, random_string('system1'), :system, nil,
                {'system.certificate_version' => '3.1',
                 # Simulate 8 GB of RAM as would be returned from system fact (kb)
                 # which will be covered by the enitlement when consumed.
                 'memory.memtotal' => '8000000',
                 # Simulate system having 4 sockets which will be covered after consuming
                 # the entitlement
                 'cpu.cpu_socket(s)' => '4'})
    installed = [
        {'productId' => @ram_and_socket_product.id,
        'productName' => @ram_and_socket_product.name}
    ]
    system.update_consumer({:installedProducts => installed})
    entitlement = system.consume_product(@ram_and_socket_product.id)[0]
    entitlement.should_not == nil

    compliance_status = system.get_compliance(consumer_id=system.uuid)
    compliance_status['status'].should == 'valid'
    compliance_status['compliant'].should == true
    compliant_products = compliance_status['compliantProducts']
    compliant_products.should have_key(@ram_and_socket_product.id)
  end

  it 'can heal when RAM limited' do
    system = consumer_client(@user, random_string('system1'), :system, nil,
                {'system.certificate_version' => '3.1',
                 # Simulate 8 GB of RAM as would be returned from system fact (kb)
                 'memory.memtotal' => '8000000'})
    installed = [
        {'productId' => @ram_product.id, 'productName' => @ram_product.name}
    ]
    system.update_consumer({:installedProducts => installed})

    # Perform healing
    ents = system.consume_product()
    ents.size.should == 1
  end

  it 'will not heal when system RAM is not covered by any entitlements' do
    system = consumer_client(@user, random_string('system1'), :system, nil,
                {'system.certificate_version' => '3.1',
                 # Simulate 12 GB of RAM as would be returned from system fact (kb)
                 'memory.memtotal' => '12000000'})
    installed = [
        {'productId' => @ram_product.id, 'productName' => @ram_product.name}
    ]
    system.update_consumer({:installedProducts => installed})

    # Perform healing
    ents = system.consume_product()
    ents.should == nil
  end

  it 'can heal when both RAM and socket limited' do
    system = consumer_client(@user, random_string('system1'), :system, nil,
                {'system.certificate_version' => '3.1',
                 # Simulate 8 GB of RAM as would be returned from system fact (kb)
                 # which will be covered by the enitlement when consumed.
                 'memory.memtotal' => '8000000',
                 # Simulate system having 4 sockets which will be covered after consuming
                 # the entitlement
                 'cpu.cpu_socket(s)' => '4'})
    installed = [
        {'productId' => @ram_and_socket_product.id,
        'productName' => @ram_and_socket_product.name}
    ]
    system.update_consumer({:installedProducts => installed})

    # Perform healing
    ents = system.consume_product()
    ents.size.should == 1
  end

  #
  # Ram stacking tests
  #
  it 'consumer status should be green when stacking a single ram entitlement' do
    system = consumer_client(@user, random_string('system1'), :system, nil,
                {'system.certificate_version' => '3.1',
                 # Simulate 2 GB of RAM as would be returned from system fact (kb)
                 # which will be covered by the enitlement when consumed.
                 'memory.memtotal' => '2000000',
                 # Since cert v3 is disabled by default, configure consumer bypass.
                 'system.testing' => 'true'})
    installed = [
        {'productId' => @stackable_ram_product.id,
        'productName' => @stackable_ram_product.name}
    ]
    system.update_consumer({:installedProducts => installed})
    
    pool = find_pool(@owner.id, @stackable_ram_sub.id)
    pool.should_not == nil

    entitlement = system.consume_pool(pool.id, {:quantity => 1})
    entitlement.should_not == nil

    compliance_status = system.get_compliance(consumer_id=system.uuid)
    compliance_status['status'].should == 'valid'
    compliance_status['compliant'].should == true
    compliant_products = compliance_status['compliantProducts']
    compliant_products.should have_key(@stackable_ram_product.id)
    
  end
  
  it 'consumer status should be green when stacking a single ram entitlement with proper quantity' do
    system = consumer_client(@user, random_string('system1'), :system, nil,
                {'system.certificate_version' => '3.1',
                 # Simulate 4 GB of RAM as would be returned from system fact (kb)
                 # which will be covered by the enitlement when consumed.
                 'memory.memtotal' => '4000000',
                 # Since cert v3 is disabled by default, configure consumer bypass.
                 'system.testing' => 'true'})
    installed = [
        {'productId' => @stackable_ram_product.id,
        'productName' => @stackable_ram_product.name}
    ]
    system.update_consumer({:installedProducts => installed})
    
    pool = find_pool(@owner.id, @stackable_ram_sub.id)
    pool.should_not == nil

    entitlement = system.consume_pool(pool.id, {:quantity => 2})
    entitlement.should_not == nil
    
    compliance_status = system.get_compliance(consumer_id=system.uuid)
    compliance_status['status'].should == 'valid'
    compliance_status['compliant'].should == true
    compliant_products = compliance_status['compliantProducts']
    compliant_products.should have_key(@stackable_ram_product.id)
    
  end

  it 'autobind should completely cover ram products' do
    system = consumer_client(@user, random_string('system1'), :system, nil,
                {'system.certificate_version' => '3.1',
                 # Simulate 4 GB of RAM as would be returned from system fact (kb)
                 # which will be covered by the enitlement when consumed.
                 'memory.memtotal' => '4000000',
                 # Since cert v3 is disabled by default, configure consumer bypass.
                 'system.testing' => 'true'})
    installed = [
        {'productId' => @stackable_ram_product.id,
        'productName' => @stackable_ram_product.name}
    ]
    system.update_consumer({:installedProducts => installed})
    
    entitlements = system.consume_product()
    entitlements.size.should == 1
    
    entitlement = entitlements[0]
    entitlement.should_not == nil
    entitlement.quantity.should == 2

    compliance_status = system.get_compliance(consumer_id=system.uuid)
    compliance_status['status'].should == 'valid'
    compliance_status['compliant'].should == true
    compliant_products = compliance_status['compliantProducts']
    compliant_products.should have_key(@stackable_ram_product.id)
  end

  it 'healing should add ram entitlements to cover consumer' do
    system = consumer_client(@user, random_string('system1'), :system, nil,
                {'system.certificate_version' => '3.1',
                 # Simulate 4 GB of RAM as would be returned from system fact (kb)
                 # which will be covered by the enitlement when consumed.
                 'memory.memtotal' => '4000000',
                 # Since cert v3 is disabled by default, configure consumer bypass.
                 'system.testing' => 'true'})
    installed = [
        {'productId' => @stackable_ram_product.id,
        'productName' => @stackable_ram_product.name}
    ]
    system.update_consumer({:installedProducts => installed})
    
    pool = find_pool(@owner.id, @stackable_ram_sub.id)
    pool.should_not == nil

    entitlement = system.consume_pool(pool.id, {:quantity => 1})
    entitlement.should_not == nil
    
    entitlements = system.consume_product()
    entitlements.size.should == 1
    
    entitlement = entitlements[0]
    entitlement.should_not == nil
    entitlement.quantity.should == 1

    compliance_status = system.get_compliance(consumer_id=system.uuid)
    compliance_status['status'].should == 'valid'
    compliance_status['compliant'].should == true
    compliant_products = compliance_status['compliantProducts']
    compliant_products.should have_key(@stackable_ram_product.id)
  end
  
end

