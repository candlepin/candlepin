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
      create_pool_and_subscription(@owner['key'], @some_product.id, 2,
				[], '', '', '', nil, nil, true)
      create_pool_and_subscription(@owner['key'], @another_product.id, 3,
				[], '', '', '', nil, nil, true)
      create_pool_and_subscription(@owner['key'], @one_more_product.id, 2)
      @cp.list_subscriptions(@owner['key']).size.should == 3
  end

  it 'should allow admins to delete subscriptions' do
      pool = create_pool_and_subscription(@owner['key'], @monitoring_product.id, 5)
      @cp.list_subscriptions(@owner['key']).size.should == 1
      delete_pool_and_subscription(pool)
      @cp.list_subscriptions(@owner['key']).size.should == 0
  end

  it 'should activate subscription' do
      skip("candlepin running in standalone mode") unless is_hosted?
      pool = create_pool_and_subscription(@owner['key'], @monitoring_product.id, 5)
      @cp.list_subscriptions(@owner['key']).size.should == 1
      consumer = @cp.register("goofy", :hypervisor, nil, {}, nil, @owner['key'], [], [])

      @cp.activate_subscription(consumer.uuid, "mail", "locale")
  end
end
