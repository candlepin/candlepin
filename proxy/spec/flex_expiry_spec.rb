require 'candlepin_scenarios'
require 'date'

describe 'Flex Expiry' do

  include CandlepinMethods
  it_should_behave_like 'Candlepin Scenarios'

  def create_flex_expiry_product
    product_id = random_string('testproduct')
    return create_product(product_id, product_id, {:attributes =>
          {'flex_expiry' => '30'}})
  end

  it 'creates extended entitlement certificates' do
    owner = create_owner random_string('test_owner')
    owner_client = user_client(owner, random_string('testuser'))
    flex_product = create_flex_expiry_product

    end_date = Date.new(2050, 5, 1)
    sub = @cp.create_subscription(owner.id, flex_product.id, 100, [], '',
        "2010-05-01", end_date)
    @cp.refresh_pools owner.key
    pool = @cp.list_pools(:owner => owner.id).first

    # Make sure we got the right pool:
    pool.subscriptionId.should == sub.id
    sub['endDate'].should == pool['endDate']

    # Bind to create an entitlement cert:
    consumer_client = consumer_client(owner_client, random_string('testsystem'))

    result = consumer_client.consume_pool pool.id
    ent_cert = consumer_client.list_certificates.first

    verify_cert_dates(ent_cert, end_date, 30)

  end

end
