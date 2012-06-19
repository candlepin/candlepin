require 'openssl'
require 'candlepin_scenarios'

describe 'Sub-Pool' do
  include CandlepinMethods
  include CandlepinScenarios

  before(:each) do
    @owner = create_owner random_string('test_owner')
    @derived_product = create_product()
    @parent_product = create_product(nil, nil, {:attributes => {
            'user_license' => 'unlimited',
            'user_license_product' => @derived_product.id,
            'requires_consumer_type' => 'person'
    }})

    @provided_product1 = create_product()
    @provided_product2 = create_product()

    # Create a subscription
    @subscription = @cp.create_subscription(@owner['key'], @parent_product.id, 5,
      [@provided_product1.id, @provided_product2.id])
    @cp.refresh_pools(@owner['key'])

    # Set up user
    @user_client = user_client(@owner, 'billy')

    # ===== When =====
    # Register his personal consumer
    @person_client = consumer_client(@user_client, 'billy_consumer',
        :person)

    # Subscribe to the parent pool
    @parent_ent = @person_client.consume_product(@parent_product.id)[0]

    # Now register a system
    @system = consumer_client(@user_client, 'system1')

    # And subscribe to the created sub-pool
    @system.consume_product @derived_product.id
  end

  it 'should un-entitle system when unregistering person consumer' do
    @system.list_entitlements.size.should == 1
    @cp.unregister(@person_client.uuid)
    @system.list_entitlements.size.should == 0
  end

  it 'inherits provided products from parent pool' do
    derived_pool = @cp.list_pools({:product => @derived_product.id,
      :owner => @owner.id})[0]
    derived_pool['providedProducts'].size.should == 2
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
    order_number = order_number[2, order_number.size - 2]
    order_number.should == @subscription.id
  end

  it 'allows unregister as consumer with outstanding entitlements' do
    @person_client.unregister(@person_client.uuid)
  end

  it 'allows unregister as admin with outstanding entitlements' do
    @cp.unregister(@person_client.uuid)
  end

  it 'allows unbind as consumer with outstanding entitlements' do
    @person_client.unbind_entitlement(@parent_ent['id'])
  end

  it 'should use the correct name for the child pools' do
    entitlement_cert = @system.list_certificates.first
    cert = OpenSSL::X509::Certificate.new(entitlement_cert.cert)

    # TODO:  This magic OID should be refactored...
    order_name = get_extension(cert, '1.3.6.1.4.1.2312.9.4.1')
    order_name.should == @derived_product.name
  end


end
