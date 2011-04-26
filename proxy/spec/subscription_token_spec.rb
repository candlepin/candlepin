require 'candlepin_scenarios'

# XXX: all these tests need work (but so do tokens)
describe 'Subscription Token' do

  include CandlepinMethods
  include CandlepinScenarios

  before(:each) do
    @owner = create_owner random_string('test_owner')
    @some_product = create_product(name='some_product')
    
    @sub = @cp.create_subscription(@owner['key'], @some_product['id'], 37)
    token = {
        'token' => random_string('test_token'),
        'subscription' => {:id => @sub['id']}
    }
    @token = @cp.create_subscription_token(token)
  end

  it 'should allow owners to list existing subscription tokens' do
    tokens = @cp.list_subscription_tokens()
    tokens.length.should >= 1
  end

  it 'should allow owners to delete their subscription tokens' do
    sub = @cp.create_subscription(@owner['key'], @some_product['id'], 37)
    token = {
        'token' => random_string('test_token'),
        'subscription' => {:id => @sub['id']}
    }
    token = @cp.create_subscription_token(token)
    @cp.delete_subscription_token(token['id'])
  end

  it 'should allow owners to create subscription tokens' do
    sub = @cp.create_subscription(@owner['key'], @some_product['id'], 37)
    token = {
        'token' => random_string('test_token'),
        'subscription' => {:id => @sub['id']}
    }
    token = @cp.create_subscription_token(token)
    tokens = @cp.list_subscription_tokens()
    tokens.length.should >= 1
  end

  it 'creates a pool if a new subscription is returned when binding by token' do
    user = user_client(@owner, random_string("test_user"))
    consumer = consumer_client(user, 'guest_consumer')
    consumer.consume_token(@token['token'])

    @cp.list_pools({:owner => @owner['id']}).length.should == 1
  end
end
