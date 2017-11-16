require 'spec_helper'
require 'candlepin_scenarios'

describe 'Subscription Resource' do

  include CandlepinMethods

  before do
    @owner = create_owner random_string('test_owner')
    @some_product = create_product(@owner['key'], "some_product", :multiplier => 2)
    @another_product = create_product('another_product')
    @one_more_product = create_product('one_more_product')
    @monitoring_product = create_product('monitoring')
  end

  it 'should allow owners to create subscriptions and retrieve all' do
      @cp.create_pool(@owner['key'], @some_product.id, {
        :quantity => 2,
        :subscription_id => random_string('source_sub'),
        :upstream_pool_id => random_string('upstream')
      })

      @cp.create_pool(@owner['key'], @another_product.id, {
        :quantity => 3,
        :subscription_id => random_string('source_sub'),
        :upstream_pool_id => random_string('upstream')
      })

      @cp.create_pool(@owner['key'], @one_more_product.id, {
        :quantity => 2,
        :subscription_id => random_string('source_sub'),
        :upstream_pool_id => random_string('upstream')
      })

      @cp.list_subscriptions(@owner['key']).size.should == 3
  end

  it 'should allow admins to delete subscriptions' do
      pool = @cp.create_pool(@owner['key'], @monitoring_product.id, {
        :quantity => 5,
        :subscription_id => random_string('source_sub'),
        :upstream_pool_id => random_string('upstream')
      })

      @cp.list_subscriptions(@owner['key']).size.should == 1

      @cp.delete_pool(pool.id)
      @cp.list_subscriptions(@owner['key']).size.should == 0
  end

  it 'should not allow clients to fetch subscriptions using id' do
      pool = @cp.create_pool(@owner['key'], @one_more_product.id, {
        :quantity => 2,
        :subscription_id => random_string('source_sub'),
        :upstream_pool_id => random_string('upstream')
      })

      begin
          @cp.get_subscription(pool['subscriptionId'])
          fail("Should not allow to fetch subscription")
      rescue URI::InvalidURIError => e
          e.to_s.eql? "bad URI(is not URI?): pools/{pool_id}"
      end
  end

  it 'should not allow clients to fetch subscription cert using subscription id' do
      pool = @cp.create_pool(@owner['key'], @one_more_product.id, {
        :quantity => 2,
        :subscription_id => random_string('source_sub'),
        :upstream_pool_id => random_string('upstream')
      })

      begin
          @cp.get_subscription_cert(pool['subscriptionId'])
          fail("Should not allow to fetch subscription")
      rescue URI::InvalidURIError => e
          e.to_s.eql? "bad URI(is not URI?): pools/{pool_id}/cert"
      end
  end
end
