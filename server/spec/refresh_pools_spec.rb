require 'spec_helper'
require 'candlepin_scenarios'

require 'rubygems'
require 'rest_client'

describe 'Refresh Pools' do
  include CandlepinMethods
  include VirtHelper

  before(:each) do
    pending("candlepin running in standalone mode") if not is_hosted?
  end

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

    if (not is_hosted?)
      result.should be_nil
    else
      result.should == "Pools refreshed for owner #{test_owner}"
    end
  end

  it 'creates the correct number of pools' do
    owner = create_owner random_string('some-owner')

    # Create 6 subscriptions to different products
    6.times do |i|
      name = random_string("product-#{i}")
      product = create_product(name, name, :owner => owner['key'])

      create_pool_and_subscription(owner['key'], product.id)
    end

    # @cp.refresh_pools(owner['key'])

    @cp.list_pools({:owner => owner.id}).length.should == 6
  end

  it 'dispatches the correct number of events' do
    owner = create_owner random_string('some-owner')

    # Create 6 subscriptions to different products
    6.times do |i|
      name = random_string("product-#{i}")
      product = create_product(name, name, :owner => owner['key'])

      create_pool_and_subscription(owner['key'], product.id)
    end

    @cp.refresh_pools(owner['key'])
    sleep 1

    events = @cp.list_owner_events(owner['key'])
    pool_created_events = events.find_all { |event| event['target'] == 'POOL' && event['type'] == 'CREATED'}
    pool_created_events.size.should == 6
  end

  it 'detects changes in provided products' do
    owner = create_owner random_string
    product = create_product(random_string, random_string, :owner => owner['key'])
    provided1 = create_product(random_string, random_string, :owner => owner['key'])
    provided2 = create_product(random_string, random_string, :owner => owner['key'])
    provided3 = create_product(random_string, random_string, :owner => owner['key'])
    pool = create_pool_and_subscription(owner['key'], product.id, 500, [provided1.id, provided2.id])
    pools = @cp.list_pools({:owner => owner.id})
    pools.length.should == 1
    pools[0].providedProducts.length.should == 2
    # Remove the old provided products and add a new one:
    sub = get_hostedtest_subscription(pool.subscriptionId)
    sub.providedProducts = [@cp.get_product(owner['key'], provided3.id)]
    update_hostedtest_subscription(sub)
    @cp.refresh_pools(owner['key'])
    pools = @cp.list_pools({:owner => owner.id})
    pools[0].providedProducts.length.should == 1
  end

  it 'deletes expired subscriptions\' pools and entitlements' do
    owner = create_owner random_string
    product = create_product(random_string, random_string, :owner => owner['key'])
    pool = create_pool_and_subscription(owner['key'], product.id, 500, [])
    pools = @cp.list_pools({:owner => owner.id})
    pools.length.should == 1

    user = user_client(owner, random_string("user"))

    consumer_id = random_string("consumer")
    consumer = consumer_client(user, consumer_id)
    consumer.consume_pool(pools.first.id, {:quantity => 1}).size.should == 1

    # Update the subscription to be expired so that pool, and entitlements are removed.
    sub = get_hostedtest_subscription(pool.subscriptionId)
    sub.startDate = Date.today - 20
    sub.endDate = Date.today - 10
    update_hostedtest_subscription(sub)
    @cp.refresh_pools(owner['key'])
    @cp.list_pools({:owner => owner.id}).size.should == 0
    @cp.get_consumer(consumer.uuid).entitlementCount.should == 0
  end

  it 'regenerates entitlements' do
    owner = create_owner random_string
    product = create_product(random_string, random_string, :owner => owner['key'])
    new_product = create_product(random_string, random_string, :owner => owner['key'])
    pool = create_pool_and_subscription(owner['key'], product.id, 500, [])
    pools = @cp.list_pools({:owner => owner.id})
    pools.length.should == 1

    user = user_client(owner, random_string("user"))

    consumer_id = random_string("consumer")
    consumer = consumer_client(user, consumer_id)
    ents = consumer.consume_pool(pools.first.id, {:quantity => 1})
    ents.size.should == 1
    ent = ents[0]
    old_cert = ent['certificates'][0]
    old_serial = old_cert['serial']['serial']

    # Change the product on subscription to trigger a regenerate:
    sub = get_hostedtest_subscription(pool.subscriptionId)
    sub['product'] = {'id' => new_product['id']}
    update_hostedtest_subscription(sub)
    @cp.refresh_pools(owner['key'], false, false, true)
    ent = @cp.get_entitlement(ent['id'])
    new_cert = ent['certificates'][0]
    new_serial = new_cert['serial']['serial']
    new_serial.should_not == old_serial

    @cp.get_consumer(consumer.uuid).entitlementCount.should == 1
  end

  it 'handle derived products being removed' do
   # 998317: is caused by refresh pools dying with an NPE
   # this happens when subscriptions no longer have
   # derived products resulting in a null during the refresh
   # which we didn't handle in all cases.

    owner = create_owner random_string
    # create subscription with sub-pool data:
    datacenter_product = create_product(nil, nil, {
      :attributes => {
        :virt_limit => "unlimited",
        :stacking_id => "stackme",
        :sockets => "2",
        'multi-entitlement' => "yes"
      },
      :owner => owner['key']
    })
    derived_product = create_product(nil, nil, {
      :attributes => {
          :cores => 2,
          :sockets=>4
      },
      :owner => owner['key']
    })
    eng_product = create_product('300', nil, :owner => owner['key'])

    pool1 = create_pool_and_subscription(owner['key'], datacenter_product.id,
      10, [], '', '', '', nil, nil, false,
      {
        :derived_product_id => derived_product['id'],
        :derived_provided_products => ['300']
      })
    # extra unmapped guest pool will be labeled with provided product
    pools = @cp.list_pools :owner => owner.id,
      :product => datacenter_product.id
    pools.size.should == 1
    pools[0]['derivedProvidedProducts'].length.should == 1

    # let's remove the derivedProducts - this simulates
    # the scenario that caues the bug
    sub1 = get_hostedtest_subscription(pool1.subscriptionId)
    sub1['derivedProduct'] = nil
    sub1['derivedProvidedProducts'] = nil
    update_hostedtest_subscription(sub1)

    # this is the refresh we are actually testing
    # it should succeed
    @cp.refresh_pools(owner['key'])

    # let's verify it removed them correctly
    # extra unmapped pool now shows datacenter product
    pools = @cp.list_pools :owner => owner.id, \
      :product => datacenter_product.id
    pools.length.should == 2
    pools[0]['derivedProvidedProducts'].length.should == 0
  end

  it 'can migrate subscription' do
    # Create the initial owner and generate the pools.
    owner1 = create_owner random_string('initial-owner')
    name = random_string("product")
    product1 = create_product(name, name, :owner => owner1['key'])
    pool = create_pool_and_subscription(owner1['key'], product1.id)
    owner1_pools = @cp.list_pools({:owner => owner1.id})
    owner1_pools.length.should == 1

    # Create another owner and migrate the subscription
    owner2 = create_owner random_string('migrated-owner')
    product2 = create_product(name, name, :owner => owner2['key'])

    # migrate the subscription to another owner.
    sub = get_hostedtest_subscription(pool.subscriptionId)
    sub["owner"] = owner2
    update_hostedtest_subscription(sub)
    @cp.refresh_pools(owner1["key"])
    @cp.refresh_pools(owner2["key"])

    # Check that the pools are removed from the first owner
    @cp.list_pools({:owner => owner1.id}).length.should == 0
    @cp.list_pools({:owner => owner2.id}).length.should == 1
  end

  it 'removes pools from other owners when subscription is migrated' do
    # Create the initial owner and generate the pools.
    owner1 = create_owner random_string('initial-owner')
    name = random_string("product")
    product1 = create_product(name, name, :owner => owner1['key'])
    pool = create_pool_and_subscription(owner1['key'], product1.id)
    owner1_pools = @cp.list_pools({:owner => owner1.id})
    owner1_pools.length.should == 1

    # Create another owner and migrate the subscription
    owner2 = create_owner random_string('migrated-owner')
    product2 = create_product(name, name, :owner => owner2['key'])

    # migrate the subscription to another owner.
    sub = get_hostedtest_subscription(pool.subscriptionId)
    sub["owner"] = owner2
    update_hostedtest_subscription(sub)

    # Refresh the second owner so that the pools are updated.
    @cp.refresh_pools(owner2["key"], true)
    sleep 1
    # Initial owner should have all pools removed.
    @cp.list_pools({:owner => owner1.id}).length.should == 0

    # Pools should now be created for the second owner since
    # the subscription was migrated.
    @cp.list_pools({:owner => owner2.id}).length.should == 1
  end

  # Testing bug #1150234:
  it 'can change attributes and revoke entitlements at same time' do
    owner = create_owner random_string
    user = user_client(owner, random_string('virt_user'))
    product = create_product(
      random_string,
      random_string,
      {
        :attributes => {'multi-entitlement' => "yes"},
        :owner => owner['key']
      }
    )
    create_pool_and_subscription(owner['key'], product.id, 2)

    user = user_client(owner, random_string("user"))
    host = consumer_client(user, 'host', :system, nil)

    pools = @cp.list_pools({:owner => owner.id, :product => product.id})
    pools.length.should == 1
    # We'll consume quantity 2, later we will reduce the pool to 1 forcing a
    # revoke of this entitlement:
    @cp.consume_pool(pools[0]['id'], {:uuid => host.uuid, :quantity => 2})
    @cp.refresh_pools(owner['key'], false, false, false)

    pool = @cp.list_pools({:owner => owner.id, :product => product.id})[0]

    # Modify product attributes:
    attrs = product['attributes']
    attrs << {:name => 'newattribute', :value => 'something'}
    @cp.update_product(owner['key'], product.id, :attributes => attrs)

    # Reduce the subscription quantity:
    sub = get_hostedtest_subscription(pool.subscriptionId)
    sub['quantity'] = 1
    update_hostedtest_subscription(sub)

    @cp.refresh_pools(owner['key'], false, false, false)
    pools = @cp.list_pools({:owner => owner.id, :product => product.id})
    pools.length.should == 1
  end

end
