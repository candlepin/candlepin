require 'candlepin_scenarios'
require 'virt_fixture'

# This spec tests virt limited products in a standalone Candlepin deployment.
# (which we assume to be testing against)
describe 'Standalone Virt-Limit Subscriptions' do
  include CandlepinMethods
  include CandlepinScenarios
  include VirtFixture

  before(:each) do
    pending("candlepin running in standalone mode") if is_hosted?

    # Setup two virt host consumers:
    @host1 = @user.register(random_string('host'), :system, nil,
      {}, nil, nil, [], [])
    @host1_client = Candlepin.new(username=nil, password=nil,
        cert=@host1['idCert']['cert'],
        key=@host1['idCert']['key'])

    @host2 = @user.register(random_string('host'), :system, nil,
      {}, nil, nil, [], [])
    @host2_client = Candlepin.new(username=nil, password=nil,
        cert=@host2['idCert']['cert'],
        key=@host2['idCert']['key'])

    pools = @host1_client.list_pools :consumer => @host1['uuid']
    @host_ent = @host1_client.consume_pool(@virt_limit_pool['id'])[0]

    pools = @host2_client.list_pools :consumer => @host2['uuid']
    host2_ent = @host2_client.consume_pool(@virt_limit_pool['id'])[0]
    # After binding the host should see no pools available:
    pools = @host2_client.list_pools :consumer => @host2['uuid']

    # Link the host and the guest:
    @host1_client.update_consumer({:guestIds => [{'guestId' => @uuid1}]})

    @cp.get_consumer_guests(@host1['uuid']).length.should == 1

    # Find the host-restricted pool:
    pools = @guest1_client.list_pools :consumer => @guest1['uuid']

    pools.should have(3).things
    @guest_pool = pools.find_all { |i| !i['sourceStackId'].nil? }[0]
  end

  it 'should re-source guest pool when other stacked entitlements exist' do
    @cp.list_owner_pools(@owner['key']).length.should == 4
    @guest_pool['sourceConsumer']['uuid'].should == @host1['uuid']
    @guest_pool['contractNumber'].should == "123"
    # Use another pool in the stack:
    host_ent_2 = @host1_client.consume_pool(@pools[1]['id'])[0]
    # No new guest pool should have been created because we already had one
    # for that stack:
    @cp.list_owner_pools(@owner['key']).length.should == 4

    # Delete the original entitlement:
    @host1_client.unbind_entitlement(@host_ent['id'])
    @guest_pool = @host1_client.get_pool(@guest_pool['id'])
    @guest_pool['sourceEntitlement']['id'].should == host_ent_2['id']
    @guest_pool['contractNumber'].should == "456"
  end

  it 'should create a virt_only pool for hosts guests' do
    # Get the attribute that indicates which host:
    requires_host = @guest_pool['attributes'].find_all {
      |i| i['name'] == 'requires_host' }[0]
    requires_host['value'].should == @host1['uuid']

    # Guest 1 should be able to use the pool:
    @guest1_client.consume_pool(@guest_pool['id'])

    # Should not be able to use the pool as this guest is not on the correct
    # host:
    lambda do
        @guest2_client.consume_pool(@guest_pool['id'])
    end.should raise_exception(RestClient::Forbidden)
  end

  it 'should list host restricted pool only for its guests' do
    # Other guest shouldn't be able to see the virt sub-pool:
    pools = @guest2_client.list_pools :consumer => @guest2['uuid']
    pools.should have(2).things
  end

  it 'should revoke guest entitlements when host unbinds' do
    # Guest 1 should be able to use the pool:
    @guest1_client.consume_pool(@guest_pool['id'])
    @guest1_client.list_entitlements.length.should == 1

    @host1_client.unbind_entitlement(@host_ent['id'])

    @guest1_client.list_entitlements.length.should == 0
  end

  it 'should revoke guest entitlements when host unregisters' do
    # Guest 1 should be able to use the pool:
    @host1_client.update_consumer({:guestIds => [{'guestId' => @uuid2}, {'guestId' => @uuid1}]});
    @guest1_client.consume_pool(@guest_pool['id'])
    @guest1_client.list_entitlements.length.should == 1

    @guest2_client.consume_pool(@guest_pool['id'])
    @guest2_client.list_entitlements.length.should == 1

    # without the fix for #811581, this will 500
    @host1_client.unregister()

    @guest1_client.list_entitlements.length.should == 0
  end

  it 'should revoke guest entitlements and remove activation keys when host unbinds' do
    activation_key = @cp.create_activation_key(@owner['key'], random_string('test_token'))
    @cp.add_pool_to_key(activation_key['id'], @guest_pool['id'])
    # Guest 1 should be able to use the pool:
    @guest1_client.consume_pool(@guest_pool['id'])
    @guest1_client.list_entitlements.length.should == 1

    @host1_client.unbind_entitlement(@host_ent['id'])

    @guest1_client.list_entitlements.length.should == 0
  end

  it 'should not revoke guest entitlements when host stops reporting guest ID' do
    @guest1_client.consume_pool(@guest_pool['id'])
    @guest1_client.list_entitlements.length.should == 1

    # Host 1 stops reporting guest:
    @host1_client.update_consumer({:guestIds => []})

    # Entitlement should not be gone:
    @guest1_client.list_entitlements.length.should == 1
  end

  it 'should lose entitlement when guest stops and is restarted elsewhere' do
    @guest1_client.consume_pool(@guest_pool['id'])
    @guest1_client.list_entitlements.length.should == 1

    # Host 1 stops reporting guest:
    @host1_client.update_consumer({:guestIds => []})
    @host2_client.update_consumer({:guestIds => [{'guestId' => @uuid1}]})

    # Entitlement should be gone:
    @guest1_client.list_entitlements.length.should == 0
  end

  it 'should not obtain a new entitlement when guest is migrated to another host' do

    # create a second product in order to test bz #786730

    @second_product = create_product()
    @cp.create_subscription(@owner['key'], @second_product.id, 1)
    @cp.refresh_pools(@owner['key'])

    @installed_product_list = [
    {'productId' => @virt_limit_product.id, 'productName' => @virt_limit_product.name},
    {'productId' => @second_product.id, 'productName' => @second_product.name}]

    @guest1_client.update_consumer({:installedProducts => @installed_product_list})
    @guest1_client.consume_product()
    @guest1_client.list_entitlements.length.should == 2

    # Add guest 2 to host 1 so we can make sure that only guest1's
    # entitlements are revoked.
    @host1_client.update_consumer({:guestIds => [{'guestId' => @uuid2}, {'guestId' => @uuid1}]});

    @guest2_client.consume_pool(@guest_pool['id'])
    @guest2_client.list_entitlements.length.should == 1

    # Host 2 reports the new guest before Host 1 reports it removed.
    # this is where the error would occur without the 786730 fix
    @host2_client.update_consumer({:guestIds => [{'guestId' => @uuid1}]})

    # host-specific entitlement should not be on the guest anymore (see 768872 comment #41)
    # second_product's entitlement should still be there, though.
    @guest1_client.list_entitlements(:product_id => @second_product.id).length.should == 1
    @guest1_client.list_entitlements(:product_id => @virt_limit_product.id).length.should == 0

    # Entitlements should have remained the same for guest 2 and its host
    # is the same.
    @guest2_client.list_entitlements.length.should == 1
  end

  it 'should not change the quantity on sub-pool when the source entitlement quantity changes' do
    # Create a sub for a virt limited product:
    product = create_product(random_string('product'), random_string('product'),
                      :attributes => { :virt_limit => 3, :'multi-entitlement' => 'yes'})
    sub = @cp.create_subscription(@owner['key'], product.id, 10)
    @cp.refresh_pools(@owner['key'])

    pools = @user.list_pools :owner => @owner.id, \
           :product => product.id
    pools.size.should == 1
    pool = pools[0]

    host_ent = @host1_client.consume_pool(pool['id'], {:quantity => 3})[0]
    pools = @user.list_pools :owner => @owner.id, \
           :product => product.id
    pools.size.should == 2
    pools.each do |now_pool|
        if now_pool['id'] != pool['id']
            now_pool['quantity'].should == 3
        end
    end
    # reduce entitlement
    @host1_client.update_entitlement({:id => host_ent.id, :quantity => 2})
    pools = @user.list_pools :owner => @owner.id, \
           :product => product.id
    pools.size.should == 2
    pools.each do |now_pool|
        if now_pool['id'] != pool['id']
            now_pool['quantity'].should == 3
        end
    end
  end

  it 'should not block a virt guest' do
    @instance_based = create_product(nil, random_string('instance_based'),
                                    :attributes => { 'instance_multiplier' => 2,
                                        'multi-entitlement' => 'yes' })
    @cp.create_subscription(@owner['key'], @instance_based.id, 10)
    @cp.refresh_pools(@owner['key'])

    pool = @guest1_client.list_pools(:product => @instance_based.id,
        :consumer => @guest1_client.uuid).first
    @guest1_client.consume_pool(pool.id, {:quantity => 3})
  end

end
