require 'candlepin_scenarios'

# This spec tests virt limited products in a standalone Candlepin deployment.
# (which we assume to be testing against)
describe 'Standalone Virt-Limit Subscriptions' do
  include CandlepinMethods
  include CandlepinScenarios

  before(:each) do
    pending("candlepin running in standalone mode") if is_hosted?

    @owner = create_owner random_string('virt_owner')
    @user = user_client(@owner, random_string('virt_user'))

    # Create a sub for a virt limited product:
    @virt_limit_product = create_product(nil, nil, {
      :attributes => {
        :virt_limit => 3
      }
    })
    @sub = @cp.create_subscription(@owner.key,
      @virt_limit_product.id, 10)
    @cp.refresh_pools(@owner.key)

    @pools = @user.list_pools :owner => @owner.id, \
      :product => @virt_limit_product.id
    @pools.size.should == 1
    @virt_limit_pool = @pools[0]

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

    @host_ent = @host1_client.consume_pool(@virt_limit_pool['id'])[0]

    # After binding the host should see no pools available:
    pools = @host1_client.list_pools :consumer => @host1['uuid']
    pools.should be_empty

    # Setup two virt guest consumers:
    @uuid1 = random_string('system.uuid')
    @uuid2 = random_string('system.uuid')
    @guest1 = @user.register(random_string('guest'), :system, nil,
      {'virt.uuid' => @uuid1, 'virt.is_guest' => 'true'}, nil, nil, [], [])
    @guest1_client = Candlepin.new(username=nil, password=nil,
        cert=@guest1['idCert']['cert'],
        key=@guest1['idCert']['key'])

    @guest2 = @user.register(random_string('guest'), :system, nil,
      {'virt.uuid' => @uuid2, 'virt.is_guest' => 'true'}, nil, nil, [], [])
    @guest2_client = Candlepin.new(username=nil, password=nil,
        cert=@guest2['idCert']['cert'],
        key=@guest2['idCert']['key'])

    # Link the host and the guest:
    @host1_client.update_consumer({:guestIds => [{'guestId' => @uuid1}]})

    @cp.get_consumer_guests(@host1['uuid']).length.should == 1

    # Find the host-restricted pool:
    pools = @guest1_client.list_pools :consumer => @guest1['uuid']
    pools.should have(2).things
    @guest_pool = pools.find_all { |i| !i['sourceEntitlement'].nil? }[0]

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
    pools.should have(1).things
  end

  it 'should revoke guest entitlements when host unbinds' do
    # Guest 1 should be able to use the pool:
    @guest1_client.consume_pool(@guest_pool['id'])
    @guest1_client.list_entitlements.length.should == 1

    @host1_client.unbind_entitlement(@host_ent['id'])

    @guest1_client.list_entitlements.length.should == 0
  end

  it 'should revoke guest entitlements when host stops reporting guest ID' do
    @guest1_client.consume_pool(@guest_pool['id'])
    @guest1_client.list_entitlements.length.should == 1

    # Host 1 stops reporting guest:
    @host1_client.update_consumer({:guestIds => []})

    # Entitlement should be gone:
    @guest1_client.list_entitlements.length.should == 0
  end

  it 'should revoke guest entitlements when guest is added by another host' do
    @guest1_client.consume_pool(@guest_pool['id'])
    @guest1_client.list_entitlements.length.should == 1

    # Add guest 2 to host 1 so we can make sure that only guest1's
    # entitlements are revoked.
    @host1_client.update_consumer({:guestIds => [{'guestId' => @uuid2}]});
    @guest2_client.consume_pool(@guest_pool['id'])
    @guest2_client.list_entitlements.length.should == 1

    # Host 2 reports the new guest before Host 1 reports it removed.
    @host2_client.update_consumer({:guestIds => [{'guestId' => @uuid1}]})

    # Entitlement should be removed from guest 1 as host is changed.
    # Note: This does not necessarily mean that host 1 will never report it again.
    @guest1_client.list_entitlements.length.should == 0

    # Entitlements should have remained the same for guest 2 and its host
    # is the same.
    @guest2_client.list_entitlements.length.should == 1
  end

end
