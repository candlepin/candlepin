require 'candlepin_scenarios'
require 'date'

describe 'Flex Expiry' do

  include CandlepinMethods
  it_should_behave_like 'Candlepin Scenarios'

  before(:each) do
    @owner = create_owner random_string('test_owner')
    owner_client = user_client(@owner, random_string('testuser'))
    product_id = random_string('testproduct')
    @flex_days = 30
    flex_product =  create_product(product_id, product_id, {:attributes =>
          {'flex_expiry' => @flex_days}})

    # Create a subscription and refresh pools:
    @end_date = Date.new(2050, 5, 1)
    @sub = @cp.create_subscription(@owner.id, flex_product.id, 100, [], '',
        "2010-05-01", @end_date)
    @cp.refresh_pools @owner.key
    @pool = @cp.list_pools(:owner => @owner.id).first

    # Bind to create an entitlement cert:
    @consumer_client = consumer_client(owner_client, random_string('testsystem'))
    @consumer_client.consume_pool @pool.id
  end

  it 'creates extended entitlement certificates' do
    # Make sure we got the right pool:
    @pool.subscriptionId.should == @sub.id
    @sub['endDate'].should == @pool['endDate']

    ent_cert = @consumer_client.list_certificates.first
    verify_cert_dates(ent_cert, @end_date, @flex_days)
  end

  it 're-applies after a subscription end date change' do
    ent_cert = @consumer_client.list_certificates.first
    verify_cert_dates(ent_cert, @end_date, @flex_days)

    # Add ten days to the subscription end date:
    @end_date = @end_date + 10
    @sub['endDate'] = @end_date
    @cp.update_subscription(nil, @sub)
    @cp.refresh_pools(@owner.key)

    ent_cert = @consumer_client.list_certificates.first
    verify_cert_dates(ent_cert, @end_date, @flex_days)
  end

end
