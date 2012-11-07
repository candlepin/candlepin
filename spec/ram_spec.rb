require 'candlepin_scenarios'

describe 'RAM Limiting' do
  include CandlepinMethods
  include CandlepinScenarios

  before(:each) do
    @owner = create_owner random_string('test_owner')
    
    # Create a product limiting by RAM only.
    @ram_product = create_product(nil, nil, :attributes => 
                {:version => '6.4',
                 :arch => 'i386, x86_64',
                 :ram => 8,
                 :warning_period => 15,
                 :management_enabled => true,
                 :support_level => 'standard',
                 :support_type => 'excellent',})
    @cp.create_subscription(@owner['key'], @ram_product.id, 10, [], '1888', '1234')

    # Create a product limiting by RAM and sockets.
    @ram_and_socket_product = create_product(nil, nil, :attributes => 
                {:version => '1.2',
                 :arch => 'x86_64',
                 :ram => 8,
                 :sockets => 4,
                 :warning_period => 15,
                 :management_enabled => true,
                 :support_level => 'standard',
                 :support_type => 'excellent',})
    @cp.create_subscription(@owner['key'], @ram_and_socket_product.id, 5, [], '18881', '1222')

    # Refresh pools so that the subscription pools will be available to the test systems.
    @cp.refresh_pools(@owner['key'])

    @user = user_client(@owner, random_string('test-user'))
  end

  it 'can consume ram entitlement if requesting v3.1 certificate' do
    system = consumer_client(@user, random_string('system1'), :candlepin, nil,
                {'system.certificate_version' => '3.1',
                 # Since cert v3 is disabled by default, configure consumer bypass.
                 'system.testing' => 'true'})
    entitlement = system.consume_product(@ram_product.id)[0]
    entitlement.should_not == nil
  end

  it 'can not consume ram entitlement when requesting less than v3.1 certificate' do
    system = consumer_client(@user, random_string('system1'), :candlepin, nil,
                {'system.certificate_version' => '3.0',
                 # Since cert v3 is disabled by default, configure consumer bypass.
                 'system.testing' => 'true'})
    expected_error = "Please upgrade to a newer client to use subscription: %s" % [@ram_product.name]
    begin
      response = system.consume_product(@ram_product.id)
      #end.should raise_exception(RestClient::Conflict)
      fail("Conflict error should have been raised since system's certificate version is incorrect.")
    rescue RestClient::Conflict => e
      message = JSON.parse(e.http_body)['displayMessage']
      message.should == expected_error
    end
  end

  it 'can not consume ram entitlement when server does not support cert V3' do
    system = consumer_client(@user, random_string('system1'), :candlepin, nil,
                # cert v3 is currently disabled by default.
                {'system.certificate_version' => '3.1'})
    expected_error = "The server does not support subscriptions requiring V3 certificates."
    begin
      response = system.consume_product(@ram_product.id)
      #end.should raise_exception(RestClient::Conflict)
      fail("Conflict error should have been raised since system's certificate version is incorrect.")
    rescue RestClient::Conflict => e
      message = JSON.parse(e.http_body)['displayMessage']
      message.should == expected_error
    end
  end

  it 'consumer status should be valid when consumer RAM is covered' do
    system = consumer_client(@user, random_string('system1'), :candlepin, nil,
                {'system.certificate_version' => '3.1',
                 # Simulate 8 GB of RAM as would be returned from system fact (kb)
                 'memory.memtotal' => '8000000',
                 # Since cert v3 is disabled by default, configure consumer bypass.
                 'system.testing' => 'true'})
    installed = [
        {'productId' => @ram_product.id, 'productName' => @ram_product.name}
    ]
    system.update_consumer({:installedProducts => installed})
    
    entitlement = system.consume_product(@ram_product.id)[0]
    entitlement.should_not == nil
    
    compliance_status = @cp.get_compliance(consumer_id=system.uuid)
    compliance_status['status'].should == 'valid'
    compliance_status['compliant'].should == true
    compliant_products = compliance_status['compliantProducts']
    compliant_products.should have_key(@ram_product.id)
  end

  it 'consumer status should be partial when consumer RAM not covered' do
    system = consumer_client(@user, random_string('system1'), :candlepin, nil,
                {'system.certificate_version' => '3.1',
                 # Simulate 16 GB of RAM as would be returned from system fact (kb)
                 'memory.memtotal' => '16000000',
                 # Since cert v3 is disabled by default, configure consumer bypass.
                 'system.testing' => 'true'})
    installed = [
        {'productId' => @ram_product.id, 'productName' => @ram_product.name}
    ]
    system.update_consumer({:installedProducts => installed})
    
    entitlement = system.consume_product(@ram_product.id)[0]
    entitlement.should_not == nil
    
    compliance_status = @cp.get_compliance(consumer_id=system.uuid)
    compliance_status['status'].should == 'partial'
    compliance_status['compliant'].should == false
    partially_compliant_products = compliance_status['partiallyCompliantProducts']
    partially_compliant_products.should have_key(@ram_product.id)
  end
  
  it 'consumer status should be partial when consumer RAM covered but not sockets' do
    system = consumer_client(@user, random_string('system1'), :candlepin, nil,
                {'system.certificate_version' => '3.1',
                 # Simulate 8 GB of RAM as would be returned from system fact (kb)
                 # which should be covered by the enitlement when consumed.
                 'memory.memtotal' => '8000000',
                 # Simulate system having 12 sockets which won't be covered after consuming
                 # the entitlement
                 'cpu.cpu_socket(s)' => '12',
                 # Since cert v3 is disabled by default, configure consumer bypass.
                 'system.testing' => 'true'})
    installed = [
        {'productId' => @ram_and_socket_product.id,
        'productName' => @ram_and_socket_product.name}
    ]
    system.update_consumer({:installedProducts => installed})
    entitlement = system.consume_product(@ram_and_socket_product.id)[0]
    entitlement.should_not == nil
    
    compliance_status = @cp.get_compliance(consumer_id=system.uuid)
    compliance_status['status'].should == 'partial'
    compliance_status['compliant'].should == false
    partially_compliant_products = compliance_status['partiallyCompliantProducts']
    partially_compliant_products.should have_key(@ram_and_socket_product.id)
  end
  
  it 'consumer status should be partial when consumer sockets covered but not RAM' do
    system = consumer_client(@user, random_string('system1'), :candlepin, nil,
                {'system.certificate_version' => '3.1',
                 # Simulate 16 GB of RAM as would be returned from system fact (kb)
                 # which will not be covered by the enitlement when consumed.
                 'memory.memtotal' => '16000000',
                 # Simulate system having 4 sockets which will be covered after consuming
                 # the entitlement
                 'cpu.cpu_socket(s)' => '4',
                 # Since cert v3 is disabled by default, configure consumer bypass.
                 'system.testing' => 'true'})
    installed = [
        {'productId' => @ram_and_socket_product.id,
        'productName' => @ram_and_socket_product.name}
    ]
    system.update_consumer({:installedProducts => installed})
    entitlement = system.consume_product(@ram_and_socket_product.id)[0]
    entitlement.should_not == nil
    
    compliance_status = @cp.get_compliance(consumer_id=system.uuid)
    compliance_status['status'].should == 'partial'
    compliance_status['compliant'].should == false
    partially_compliant_products = compliance_status['partiallyCompliantProducts']
    partially_compliant_products.should have_key(@ram_and_socket_product.id)
  end
  
  it 'consumer status is valid when both RAM and sockets are covered' do
    system = consumer_client(@user, random_string('system1'), :candlepin, nil,
                {'system.certificate_version' => '3.1',
                 # Simulate 8 GB of RAM as would be returned from system fact (kb)
                 # which will be covered by the enitlement when consumed.
                 'memory.memtotal' => '8000000',
                 # Simulate system having 4 sockets which will be covered after consuming
                 # the entitlement
                 'cpu.cpu_socket(s)' => '4',
                 # Since cert v3 is disabled by default, configure consumer bypass.
                 'system.testing' => 'true'})
    installed = [
        {'productId' => @ram_and_socket_product.id,
        'productName' => @ram_and_socket_product.name}
    ]
    system.update_consumer({:installedProducts => installed})
    entitlement = system.consume_product(@ram_and_socket_product.id)[0]
    entitlement.should_not == nil
    
    compliance_status = @cp.get_compliance(consumer_id=system.uuid)
    compliance_status['status'].should == 'valid'
    compliance_status['compliant'].should == true
    compliant_products = compliance_status['compliantProducts']
    compliant_products.should have_key(@ram_and_socket_product.id)
  end
  
end

