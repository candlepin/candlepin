require 'openssl'
require 'candlepin_scenarios'

describe 'Sub-Pool' do
  include CandlepinMethods
  it_should_behave_like 'Candlepin Scenarios'

  before(:each) do
    owner = create_owner random_string('test_owner')
    derived_product = create_product()
    parent_product = create_product(nil, nil, {:attributes => {
            'user_license' => 'unlimited',
            'user_license_product' => derived_product.id,
            'requires_consumer_type' => 'person'
    }})

    # Create a subscription
    @subscription = @cp.create_subscription(owner.key, parent_product.id, 5)
    @cp.refresh_pools(owner.key)
    
    # Set up user
    @user_client = user_client(owner, 'billy')

    # ===== When =====
    # Register his personal consumer
    @person_client = consumer_client(@user_client, 'billy_consumer', 
        :person)

    # Subscribe to the parent pool
    @parent_ent = @person_client.consume_product(parent_product.id)[0]

    # Now register a system
    @system = consumer_client(@user_client, 'system1')

    # And subscribe to the created sub-pool
    @system.consume_product derived_product.id
  end
  
  it 'should un-entitle system when unregistering person consumer' do
    @system.list_entitlements.size.should == 1
    @cp.unregister(@person_client.uuid)
    @system.list_entitlements.size.should == 0
  end
  
  it 'unregistering system consumer should not result in deletion of the parent pool' do
    @system.unregister(@system.uuid)
    @system = consumer_client(@user_client, 'system2')
    @system.list_pools({:consumer => @system.uuid}).size.should == 1
  end

  it 'inherits order number extension from parent pool' do
    # ===== Then =====
    entitlement_cert = @system.list_certificates.first
    cert = OpenSSL::X509::Certificate.new(entitlement_cert.cert)

    # TODO:  This magic OID should be refactored...
    order_number = get_extension(cert, '1.3.6.1.4.1.2312.9.4.2')

    order_number.should == @subscription.id.to_s
  end

  it 'prevents unregister as consumer with outstanding entitlements' do
    lambda {
      @person_client.unregister(@person_client.uuid)
    }.should raise_exception(RestClient::Forbidden)
  end

  it 'allows unregister as admin with outstanding entitlements' do
    @cp.unregister(@person_client.uuid)
  end

  it 'prevents unbind as consumer with outstanding entitlements' do
    lambda {
      @person_client.unbind_entitlement(@parent_ent['id'])
    }.should raise_exception(RestClient::Forbidden)
  end


end
