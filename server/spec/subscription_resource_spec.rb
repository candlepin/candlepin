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

  it 'subscriptions derived/provided products should return empty array when null' do

      # Product does not have derived/provided products
      pool = @cp.create_pool(@owner['key'], @some_product.id, { :quantity => 2, :subscriptionId => "test-subscription1" })
      sublist = @cp.list_subscriptions(@owner['key'])
      expect(sublist[0]['providedProducts']).to eq([])
      expect(sublist[0]['derivedProvidedProducts']).to eq([])
      @cp.delete_pool(pool.id)

      # Product provided products only
      provided_product = create_product(random_string('provided_product'))
      product1 = create_product(random_string('product1'), random_string('product1'), {:providedProducts => [provided_product.id]})
      pool = @cp.create_pool(@owner['key'], product1.id, { :quantity => 2, :subscriptionId => "test-subscription2" })
      sublist = @cp.list_subscriptions(@owner['key'])
      expect(sublist[0]['providedProducts']).to_not eq([])
      expect(sublist[0]['derivedProvidedProducts']).to eq([])
      @cp.delete_pool(pool.id)

      # Product have derived provided products only
      derived_product = create_product(random_string('derived_product'), nil, {:providedProducts => [provided_product.id]})
      product2 = create_product(random_string('product2'), random_string('product2'), {:derivedProduct => derived_product})
      pool = @cp.create_pool(@owner['key'], product2.id, { :quantity => 2, :subscriptionId => "test-subscription3" })
      sublist = @cp.list_subscriptions(@owner['key'])
      expect(sublist[0]['providedProducts']).to eq([])
      expect(sublist[0]['derivedProvidedProducts']).to_not eq([])
      @cp.delete_pool(pool.id)
  end

end
