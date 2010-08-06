require 'openssl'
require 'candlepin_scenarios'

describe 'Sub-Pool' do
  include CandlepinMethods
  it_should_behave_like 'Candlepin Scenarios'

  it 'should inherit order number extension from parent pool' do
    # ===== Given =====
    owner = create_owner 'test_owner'
    derived_product = create_product 'Awesome Linux'
    parent_product = create_product('Unlimited Install Linux',
      nil, 1, 1, 'ALL','ALL', 'SVC',[], {
      'user_license' => 'unlimited',
      'user_license_product' => derived_product.id,
      'requires_consumer_type' => 'person'
    })

    # Create a subscription
    subscription = @cp.create_subscription(owner.id, parent_product.id, 5)
    @cp.refresh_pools owner.key

    # Set up user
    billy = user_client(owner, 'billy')

    # ===== When =====
    # Register his personal consumer
    billy_consumer = consumer_client(billy, 'billy_consumer', :person)

    # Subscribe to the parent pool
    billy_consumer.consume_product parent_product.id

    # Now register a system
    system1 = consumer_client(billy, 'system1')

    # And subscribe to the created sub-pool
    system1.consume_product derived_product.id

    # ===== Then =====
    entitlement_cert = system1.get_certificates.first
    cert = OpenSSL::X509::Certificate.new(entitlement_cert.cert)

    # TODO:  This magic OID should be refactored...
    order_number = get_extension(cert, '1.3.6.1.4.1.2312.9.4.2')

    order_number.should == subscription.id.to_s
  end

  # TODO:  This might be better if it were added to
  # the OpenSSL::X509::Certificate class
  def get_extension(cert, oid)
    extension = cert.extensions.select { |ext| ext.oid == oid }[0]

    return nil if extension.nil?

    value = extension.value
    # Weird ssl cert issue - have to strip the leading dots:
    value = value[2..-1] if value.match(/^\.\./)    

    return value
  end
end
