require 'candlepin_scenarios'

describe 'Domain Consumer' do
  include CandlepinMethods

  it_should_behave_like 'Candlepin Scenarios'

  before(:each) do
    @owner = create_owner 'test_owner'
    @user = user_client(@owner, 'test_user')
    @monitoring = create_product('8439203', 'monitoring')
    @domain_product = create_product('873923', 'domain_product', {
      :attributes => { :requires_consumer_type => :domain }
    })

    @cp.create_subscription(@owner.key, @monitoring.id, 4)
    @cp.create_subscription(@owner.key, @domain_product.id, 4)

    @cp.refresh_pools @owner.key
  end

  it 'should not be able to consume non domain specific products' do
    consumer = consumer_client(@user, 'guest_consumer', :domain)

    lambda do
      consumer.consume_product @monitoring.id
    end.should raise_exception(RestClient::Forbidden)
  end

  it 'should be able to consume domain products' do
    consumer = consumer_client(@user, 'other_consumer', :domain)

    entitlements = consumer.consume_product @domain_product.id
    entitlements.should have(1).things
  end

  it 'should ONLY be able to consume domain products' do
    system = consumer_client(@user, 'non-domain')

    lambda do
      system.consume_product(@domain_product.id)
    end.should raise_exception(RestClient::Forbidden)
  end

end

