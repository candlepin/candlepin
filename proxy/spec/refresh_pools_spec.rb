require 'candlepin_scenarios'

require 'rubygems'
require 'rest_client'

describe 'Refresh Pools' do
  include CandlepinMethods
  include CandlepinScenarios

  it 'creates a valid job' do
    owner = create_owner random_string('test_owner')

    status = @cp.refresh_pools(owner['key'], true)
    status.state.should == 'CREATED'

    # URI returned is valid - use post to clean up
    @cp.post(status.statusPath).state.should_not be_nil
  end

  it 'contains the proper return value' do
    test_owner = random_string('test_owner')
    owner = create_owner test_owner
    result = @cp.refresh_pools(owner['key'])

    result.should == "Pools refreshed for owner #{test_owner}"
  end

  it 'creates the correct number of pools' do
    # ------- Given ---------
    owner = create_owner 'some-owner'

    # Create 6 subscriptions to different products
    6.times do |i|
      name = random_string("product-#{i}")
      product = create_product(name, name)

      @cp.create_subscription(owner['key'], product.id)
    end

    # -------- When ----------
    @cp.refresh_pools(owner['key'])

    # -------- Then ----------
    @cp.list_pools({:owner => owner.id}).length.should == 6
  end

  it 'detects changes in provided products' do
    owner = create_owner random_string
    product = create_product(random_string, random_string)
    provided1 = create_product(random_string, random_string)
    provided2 = create_product(random_string, random_string)
    provided3 = create_product(random_string, random_string)
    sub = @cp.create_subscription(owner['key'], product.id, 500,
      [provided1.id, provided2.id])
    @cp.refresh_pools(owner['key'])
    pools = @cp.list_pools({:owner => owner.id})
    pools.length.should == 1
    pools[0].providedProducts.length.should == 2

    # Remove the old provided products and add a new one:
    sub.providedProducts = [@cp.get_product(provided3.id)]
    @cp.update_subscription(sub)
    sub2 = @cp.get_subscription(sub.id)
    @cp.refresh_pools(owner['key'])
    pools = @cp.list_pools({:owner => owner.id})
    pools[0].providedProducts.length.should == 1
  end

  it 'deletes expired subscriptions along with pools and entitlements' do
    owner = create_owner random_string
    product = create_product(random_string, random_string)
    sub = @cp.create_subscription(owner['key'], product.id, 500,
      [])
    @cp.refresh_pools(owner['key'])
    pools = @cp.list_pools({:owner => owner.id})
    pools.length.should == 1

    user = user_client(owner, random_string("user"))

    consumer_id = random_string("consumer")
    consumer = consumer_client(user, consumer_id)
    consumer.consume_pool(pools.first.id).size.should == 1

    # Update the subscription to be expired so that
    # sub, pool, and entitlements are removed.
    sub.startDate = Date.today - 20
    sub.endDate = Date.today - 10
    @cp.update_subscription(sub)
    @cp.refresh_pools(owner['key'])

    @cp.list_subscriptions(owner['key']).size.should == 0
    @cp.list_pools({:owner => owner.id}).size.should == 0
    @cp.get_consumer(consumer.uuid).entitlementCount.should == 0
  end

  it 'regenerates entitlements' do
    owner = create_owner random_string
    product = create_product(random_string, random_string)
    new_product = create_product(random_string, random_string)
    sub = @cp.create_subscription(owner['key'], product.id, 500,
      [])
    @cp.refresh_pools(owner['key'])
    pools = @cp.list_pools({:owner => owner.id})
    pools.length.should == 1

    user = user_client(owner, random_string("user"))

    consumer_id = random_string("consumer")
    consumer = consumer_client(user, consumer_id)
    ents = consumer.consume_pool(pools.first.id)
    ents.size.should == 1
    ent = ents[0]
    old_cert = ent['certificates'][0]
    old_serial = old_cert['serial']['serial']
    pp old_serial

    # Change the product on subscription to trigger a regenerate:
    sub['product'] = {'id' => new_product['id']}
    @cp.update_subscription(sub)
    @cp.refresh_pools(owner['key'], false, false, true)
    ent = @cp.get_entitlement(ent['id'])
    new_cert = ent['certificates'][0]
    new_serial = new_cert['serial']['serial']
    pp new_serial
    new_serial.should_not == old_serial

    @cp.get_consumer(consumer.uuid).entitlementCount.should == 1
  end


end
