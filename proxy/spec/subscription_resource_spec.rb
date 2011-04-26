require 'candlepin_scenarios'

describe 'Subscription Resource' do

  include CandlepinMethods
  include CandlepinScenarios

  before do
    @owner = create_owner random_string('test_owner')
    @some_product = create_product(name='some_product')
    @another_product = create_product('another_product')
    @one_more_product = create_product('one_more_product')
    @monitoring_product = create_product('monitoring')
  end

  it 'should allow owners to create subscriptions and retrieve all' do
      @cp.create_subscription(@owner.key, @some_product.id, 2)
      @cp.create_subscription(@owner.key, @another_product.id, 3)
      @cp.create_subscription(@owner.key, @one_more_product.id, 2)
      @cp.list_subscriptions(@owner.key).size.should == 3
  end

  it 'should allow admins to delete subscriptions' do
      subs = @cp.create_subscription(@owner.key, @monitoring_product.id, 5)
      @cp.refresh_pools(@owner.key)

      # Add a subscription token to make sure this gets cleaned up too:
      token = {
          'token' => random_string('test_token'),
          'subscription' => {:id => subs['id']}
      }
      token = @cp.create_subscription_token(token)

      @cp.delete_subscription(subs.id)
      @cp.list_subscriptions(@owner.key).size.should == 0
  end

end
