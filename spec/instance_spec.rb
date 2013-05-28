require 'candlepin_scenarios'

describe 'Instance Based Subscriptions' do
  include CandlepinMethods
  include CandlepinScenarios

  before(:each) do
    @owner = create_owner random_string('instance_owner')
    @user = user_client(@owner, random_string('virt_user'))

    installed_prods = [{'productId' => '300', 'productName' => '300'}]

    # For linking the host and the guest:
    @uuid = random_string('system.uuid')

    @physical_sys = @user.register(random_string('host'), :system, nil,
      {"cpu.cpu_socket(s)" => 8}, nil, nil, [], installed_prods, nil)
    @physical_client = Candlepin.new(username=nil, password=nil,
        cert=@physical_sys['idCert']['cert'],
        key=@physical_sys['idCert']['key'])
    @physical_client.update_consumer({:guestIds => [{'guestId' => @uuid}]})

    @guest1 = @user.register(random_string('guest'), :system, nil,
      {'virt.uuid' => @uuid, 'virt.is_guest' => 'true'}, nil, nil,
      [], installed_prods)
    @guest_client = Candlepin.new(username=nil, password=nil,
        cert=@guest1['idCert']['cert'], key=@guest1['idCert']['key'])

    # create instance based subscription:
    @instance_product = create_product(nil, nil, {
      :attributes => {
        :instance_multiplier => "2",
        :virt_limit => "1",
        :stacking_id => "stackme",
        :sockets => "2",
        :host_limited => "true",
        'multi-entitlement' => "yes"
      }
    })
    @eng_product = create_product('300')

    @sub1 = @cp.create_subscription(@owner['key'], @instance_product.id,
      10, ['300'])
    @cp.refresh_pools(@owner['key'])
    @pools = @cp.list_pools :owner => @owner.id, \
      :product => @instance_product.id
    @pools.size.should == 1
    @instance_pool = @pools[0]
    @instance_pool.quantity.should == 20
  end

  it 'should auto-subscribe physical systems with quantity 2 per socket pair' do
    @guest_client.list_pools({:consumer => @guest_client.uuid}).size.should == 1

    @physical_client.consume_product
    ents = @physical_client.list_entitlements
    ents.size.should == 1

    ents[0].quantity.should == 8

    # Guest should now see additional sub-pool:
    @guest_client.list_pools({:consumer => @guest_client.uuid}).size.should == 2
  end

  it 'should auto-subscribe guest systems with quantity 1' do
    @guest_client.consume_product
    ents = @guest_client.list_entitlements
    ents.size.should == 1
    ents[0].quantity.should == 1
  end

end
