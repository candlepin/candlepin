require 'candlepin_scenarios'

describe 'Sub-pool Subscriptions Should' do
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

    # create subscription with sub-pool data:
    @datacenter_product = create_product(nil, nil, {
      :attributes => {
        :virt_limit => "unlimited",
        :stacking_id => "stackme",
        :sockets => "2",
        'multi-entitlement' => "yes"
      }
    })
    @sub_product = create_product(nil, nil, {
      :attributes => {
      }
    })
    @eng_product = create_product('300')

    @sub1 = @cp.create_subscription(@owner['key'], @datacenter_product.id,
      10, [], '', '', '', nil, nil,
      {
        'sub_product_id' => @sub_product['id'],
        'sub_provided_products' => ['300']
      })
    @cp.refresh_pools(@owner['key'])
    @pools = @cp.list_pools :owner => @owner.id, \
      :product => @datacenter_product.id
    @pools.size.should == 1
    @main_pool = @pools[0]
  end

  it 'transfers sub-product data to main pool' do
    @main_pool['subProductId'].should == @sub_product['id']
    @main_pool['subProvidedProducts'].size.should == 1
    @main_pool['subProvidedProducts'][0]['productId'].should ==
      @eng_product['id']

    @physical_client.consume_pool @main_pool['id']
    ents = @physical_client.list_entitlements
    ents.size.should == 1

    # Guest should now see additional sub-pool:
    @guest_client.list_pools({:consumer => @guest_client.uuid}).size.should == 2
  end

end
