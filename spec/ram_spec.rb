require 'candlepin_scenarios'

describe 'RAM Limiting' do
  include CandlepinMethods
  include CandlepinScenarios

  before(:each) do
    @owner = create_owner random_string('test_owner')
    @product = create_product(nil, nil, :attributes => 
                {:version => '6.4',
                 :arch => 'i386, x86_64',
                 :ram => 8,
                 :warning_period => 15,
                 :management_enabled => true,
                 :support_level => 'standard',
                 :support_type => 'excellent',})

    @subscription = @cp.create_subscription(@owner['key'], @product.id, 10, [], '1888', '1234')
    @cp.refresh_pools(@owner['key'])

    @user = user_client(@owner, random_string('test-user'))
  end

  it 'can consume ram entitlement if requesting v3.1 certificate' do
    system = consumer_client(@user, random_string('system1'), :candlepin, nil,
                {'system.certificate_version' => '3.1',
                 # Since cert v3 is disabled by default, configure consumer bypass.
                 'system.testing' => 'true'})
    entitlement = system.consume_product(@product.id)[0]
  end

  it 'can not consume ram entitlement when requesting less than v3.1 certificate' do
    system = consumer_client(@user, random_string('system1'), :candlepin, nil,
                {'system.certificate_version' => '3.0',
                 # Since cert v3 is disabled by default, configure consumer bypass.
                 'system.testing' => 'true'})
    expected_error = "Please upgrade to a newer client to use subscription: %s" % [@product.name]
    begin
      response = system.consume_product(@product.id)
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
      response = system.consume_product(@product.id)
      #end.should raise_exception(RestClient::Conflict)
      fail("Conflict error should have been raised since system's certificate version is incorrect.")
    rescue RestClient::Conflict => e
      message = JSON.parse(e.http_body)['displayMessage']
      message.should == expected_error
    end
  end
end
