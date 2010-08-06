require 'candlepin_scenarios'
require 'date'

describe 'Flex Expiry' do

  include CandlepinMethods
  it_should_behave_like 'Candlepin Scenarios'

  def create_flex_expiry_product
    product_id = random_string('testproduct')
    return create_product(product_id, product_id, 1, 1, 'ALL', 'ALL', 'MKT', [],
      {'flex_expiry' => '30'})
  end

  it 'should create extended entitlement certificates' do
    owner = create_owner random_string('test_owner')
    owner_client = user_client(owner, random_string('testuser'))
    flex_product = create_flex_expiry_product

    sub = @cp.create_subscription(owner.id, flex_product.id, 100, [], '',
      "2010-05-01", "2050-05-01")
    @cp.refresh_pools owner.key
    pool = @cp.get_pools(:owner => owner.id).first

    # Make sure we got the right pool:
    pool.subscriptionId.should == sub.id
    sub['endDate'].should == pool['endDate']

    # Bind to create an entitlement cert:
    consumer_client = consumer_client(owner_client, random_string('testsystem'))

    result = consumer_client.consume_pool pool.id
    ent_cert = consumer_client.get_certificates.first

    ent_cert.entitlement.flexExpiryDays.should == 30

    cert = OpenSSL::X509::Certificate.new(ent_cert.cert)

    # Check the entitlement end date:
    end_date = cert.not_after
    end_date.year.should == 2050
    end_date.month.should == 5
    end_date.day.should == 31

    # Check the order namespace end date, this one should not have changed:
    order_end_date = Date.strptime(get_extension(cert,
        "1.3.6.1.4.1.2312.9.4.7"))
    order_end_date.year.should == 2050
    order_end_date.month.should == 5
    order_end_date.day.should == 1

  end

end
