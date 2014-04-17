require 'spec_helper'
require 'candlepin_scenarios'

require 'rubygems'
require 'rest_client'

describe 'Refresh Pools' do
  include CandlepinMethods

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
    owner = create_owner random_string('some-owner')

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
    consumer.consume_pool(pools.first.id, {:quantity => 1}).size.should == 1

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
    ents = consumer.consume_pool(pools.first.id, {:quantity => 1})
    ents.size.should == 1
    ent = ents[0]
    old_cert = ent['certificates'][0]
    old_serial = old_cert['serial']['serial']

    # Change the product on subscription to trigger a regenerate:
    sub['product'] = {'id' => new_product['id']}
    @cp.update_subscription(sub)
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
      }
    })
    derived_product = create_product(nil, nil, {
      :attributes => {
          :cores => 2,
          :sockets=>4
      }
    })
    eng_product = create_product('300')

    sub1 = @cp.create_subscription(owner['key'], datacenter_product.id,
      10, [], '', '', '', nil, nil,
      {
        'derived_product_id' => derived_product['id'],
        'derived_provided_products' => ['300']
      })
    # refresh so the owner has it
    @cp.refresh_pools(owner['key'])
    pools = @cp.list_pools :owner => owner.id, \
      :product => datacenter_product.id
    pools.size.should == 1
    pools[0]['derivedProvidedProducts'].length.should == 1

    # let's remove the derivedProducts - this simulates
    # the scenario that caues the bug
    sub1['derivedProduct'] = nil
    sub1['derivedProvidedProducts'] = nil
    puts "updating subscription"
    @cp.update_subscription(sub1)

    # this is the refresh we are actually testing
    # it should succeed
    @cp.refresh_pools(owner['key'])

    # let's verify it removed them correctly
    pools = @cp.list_pools({:owner => owner.id})
    pools.length.should == 1
    pools[0]['derivedProvidedProducts'].length.should == 0
  end

  it 'can migrate subscription' do
    # Create the initial owner and generate the pools.
    owner1 = create_owner random_string('initial-owner')
    name = random_string("product")
    product = create_product(name, name)
    sub = @cp.create_subscription(owner1['key'], product.id)
    @cp.refresh_pools(owner1["key"])
    owner1_pools = @cp.list_pools({:owner => owner1.id})
    owner1_pools.length.should == 1

    # Create another owner and migrate the subscription
    owner2 = create_owner random_string('migrated-owner')
    
    # migrate the subscription to another owner.
    sub["owner"] = owner2
    @cp.update_subscription(sub)
    @cp.list_subscriptions(owner1["key"]).length.should == 0
    @cp.list_subscriptions(owner2["key"]).length.should == 1
    
    # Refresh the first owner so that the pools are removed.
    @cp.refresh_pools(owner1["key"])
    @cp.list_pools({:owner => owner1.id}).length.should == 0
    # No pools on the second owner until it is refreshed.
    @cp.list_pools({:owner => owner2.id}).length.should == 0
        
    # Pools should be created for the second owner after refresh.
    @cp.refresh_pools(owner2["key"])
    @cp.list_pools({:owner => owner2.id}).length.should == 1
  end

  it 'removes pools from other owners when subscription is migrated' do
    # Create the initial owner and generate the pools.
    owner1 = create_owner random_string('initial-owner')
    name = random_string("product")
    product = create_product(name, name)
    sub = @cp.create_subscription(owner1['key'], product.id)
    @cp.refresh_pools(owner1["key"])
    owner1_pools = @cp.list_pools({:owner => owner1.id})
    owner1_pools.length.should == 1

    # Create another owner and migrate the subscription
    owner2 = create_owner random_string('migrated-owner')
    
    # migrate the subscription to another owner.
    sub["owner"] = owner2
    @cp.update_subscription(sub)
    @cp.list_subscriptions(owner1["key"]).length.should == 0
    @cp.list_subscriptions(owner2["key"]).length.should == 1
    
    # Refresh the second owner so that the pools are updated.
    @cp.refresh_pools(owner2["key"])
    
    # Initial owner should have all pools removed.
    @cp.list_pools({:owner => owner1.id}).length.should == 0
    
    # Pools should now be created for the second owner since
    # the subscription was migrated.
    @cp.list_pools({:owner => owner2.id}).length.should == 1
  end
    
end
