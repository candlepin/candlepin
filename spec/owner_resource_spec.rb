# encoding: utf-8
require 'spec_helper'
require 'candlepin_scenarios'

require 'rubygems'
require 'rest_client'
require 'time'
require 'json'

describe 'Owner Resource' do
  include CandlepinMethods

  # todo ported without hosted part
  it "lets only superadmin users refresh pools" do
    owner = create_owner random_string('test_owner')
    ro_owner_client = user_client(owner, random_string('testuser'), true)
    rw_owner_client = user_client(owner, random_string('testuser'), false)
    super_admin_client = user_client(owner, random_string('testuser'), false, true)
    product = create_product(nil, nil, :owner => owner['key'])
    create_pool_and_subscription(owner['key'], product.id, 10)

    lambda do
      ro_owner_client.refresh_pools(owner['key'])
    end.should raise_exception(RestClient::Forbidden)

    lambda do
      rw_owner_client.refresh_pools(owner['key'])
    end.should raise_exception(RestClient::Forbidden)

    super_admin_client.refresh_pools(owner['key'])

  end

  it 'user with owner pools permission can see system purpose of the owner products' do
    test_owner = @cp.create_owner(random_string('test_owner'))

    # Create user with the OwnerPoolsPermission
    perms = [
      {
        :type => 'OWNER_POOLS',
        :owner => {:key => test_owner['key']},
        :access => 'ALL',
      }
    ]
    username = "USER_WITH_OWNER_POOLS_PERM"
    user_client = user_client_with_perms(username, 'password', perms)

    # Make sure we see the role
    roles = user_client.get_user_roles(username)
    roles.size.should == 1

    # The user should have access to the owner's system purpose attributes (expecting this will not return
    # an error)
    res = user_client.get_owner_syspurpose(test_owner['key'])
    expect(res["owner"]["key"]).to eq(test_owner['key'])
  end

  it 'lists system purpose attributes of its consumers' do
    owner1_key = random_string("owner1")
    owner1 = @cp.create_owner(owner1_key)

    username1 = random_string("user1")
    user1 = user_client(owner1, username1)

    consumer1 = user1.register(random_string('consumer1'), :system, nil, {}, random_string('consumer1'), owner1_key,
      [], [], [], [], nil, [], nil, nil, nil, nil, nil, nil, nil, 'sla1', 'common_role', 'usage1', ['addon1'],
      nil, nil, nil, nil, 'test_service-type1')
    consumer2 = user1.register(random_string('consumer2'), :system, nil, {}, random_string('consumer2'), owner1_key,
      [], [], [], [], nil, [], nil, nil, nil, nil, nil, nil, nil, 'sla2', 'common_role', 'usage2', ['addon2'],
      nil, nil, nil, nil, 'test_service-type2')
    consumer3 = user1.register(random_string('consumer3'), :system, nil, {}, random_string('consumer3'), owner1_key,
      [], [], [], [], nil, [], nil, nil, nil, nil, nil, nil, nil, nil, nil, 'usage3', [''],
      nil, nil, nil, nil, 'test_service-type3')
    consumer4 = user1.register(random_string('consumer4'), :system, nil, {}, random_string('consumer4'), owner1_key,
      [], [], [], [], nil, [], nil, nil, nil, nil, nil, nil, nil, nil, '', 'usage4', nil,
      nil, nil, nil, nil, 'test_service-type4')

    user1.list_owner_consumers(owner1_key).length.should == 4

    res = @cp.get_owner_consumers_syspurpose(owner1_key)
    expect(res["owner"]["key"]).to eq(owner1_key)
    expect(res["systemPurposeAttributes"]["usage"]).to include("usage1")
    expect(res["systemPurposeAttributes"]["usage"]).to include("usage2")
    expect(res["systemPurposeAttributes"]["usage"]).to include("usage3")
    expect(res["systemPurposeAttributes"]["usage"]).to include("usage4")

    expect(res["systemPurposeAttributes"]["addons"]).to include("addon1")
    expect(res["systemPurposeAttributes"]["addons"]).to include("addon2")
    # Make sure to filter null & empty addons.
    expect(res["systemPurposeAttributes"]["addons"]).to_not include(nil)
    expect(res["systemPurposeAttributes"]["addons"]).to_not include("")

    expect(res["systemPurposeAttributes"]["support_level"]).to include("sla1")
    expect(res["systemPurposeAttributes"]["support_level"]).to include("sla2")
    # Empty serviceLevel means no serviceLevel, so we have to make sure those are filtered those out.
    expect(res["systemPurposeAttributes"]["support_level"]).to_not include("")

    expect(res["systemPurposeAttributes"]["roles"]).to include("common_role")
    # Make sure to filter null null & empty roles.
    expect(res["systemPurposeAttributes"]["roles"]).to_not include(nil)
    expect(res["systemPurposeAttributes"]["roles"]).to_not include("")
    # Even though 2 consumers have both specified the 'common_role', output should be deduplicated
    # and only include one instance of each unique value.
    res["systemPurposeAttributes"]["roles"].length.should == 1

    expect(res["systemPurposeAttributes"]["support_type"]).to include("test_service-type1")
    expect(res["systemPurposeAttributes"]["support_type"]).to include("test_service-type2")
    expect(res["systemPurposeAttributes"]["support_type"]).to include("test_service-type3")
    expect(res["systemPurposeAttributes"]["support_type"]).to include("test_service-type4")
  end
end

describe 'Owner Resource Pool Filter Tests' do

  include CandlepinMethods

  before(:each) do
    @owner = create_owner(random_string("test_owner"))
    @product1 = create_product(random_string("prod-1"),
      random_string("Product1"),
      {
        :attributes => {:support_level => 'VIP'}
      }
    )

    @product2 = create_product(random_string("prod-2"),
      random_string("Product2"),
      {
        :attributes => {
          :support_level => 'Supurb',
          :cores => '4'
        }
      }
    )

    @product3 = create_product(random_string("prod-3"), random_string("Product3"));

    create_pool_and_subscription(@owner['key'], @product1.id, 10, [], '', '', '', nil, nil, true)
    create_pool_and_subscription(@owner['key'], @product2.id, 10)
    create_pool_and_subscription(@owner['key'], @product3.id, 10)

    @pools = @cp.list_owner_pools(@owner['key'])
    @pools.length.should == 3
  end

  it "lets owners filter pools by single filter" do
    pools = @cp.list_owner_pools(@owner['key'], {}, ["support_level:VIP"])
    pools.length.should == 1
    pools[0].productId.should == @product1.id

    # Now with wildcards:
    pools = @cp.list_owner_pools(@owner['key'], {}, ["support_level:V*P"])
    pools.length.should == 1
    pools[0].productId.should == @product1.id

    pools = @cp.list_owner_pools(@owner['key'], {}, ["support_level:V??"])
    pools.length.should == 1
    pools[0].productId.should == @product1.id
  end

  it "lets owners filter pools by multiple filter" do
    pools = @cp.list_owner_pools(@owner['key'], {}, ["support_level:Supurb", "cores:4"])
    pools.length.should == 1
    pools[0].productId.should == @product2.id
  end

  it 'list pools with matches against provided products' do
    owner = create_owner(random_string('owner'))

    owner_client = user_client(owner, random_string('testuser'))

    consumer = owner_client.register('somesystem')
    consumer_client = Candlepin.new(nil, nil, consumer['idCert']['cert'], consumer['idCert']['key'])

    target_prod_name = random_string("product1")
    provided_product = create_product(random_string("prod1"), target_prod_name, {:owner => owner['key']})
    provided_product2 = create_product(random_string("prod2"), random_string("product2"), {:owner => owner['key']})
    provided_product3 = create_product(random_string("prod3"), random_string("product3"), {:owner => owner['key']})

    product = create_product(
      random_string("test_id"),
      random_string("test_name"),
      :owner => owner['key'], :providedProducts => [ provided_product.id, provided_product2.id,
        provided_product3.id ]
    )

    @cp.create_pool(owner['key'], product.id, { :quantity => 10 })

    pools = @cp.list_owner_pools(owner['key'], { :consumer => consumer.uuid, :matches => target_prod_name })
    pools.length.should eq(1)

    test_pool = pools[0]
    test_pool.owner['key'].should == owner['key']
    test_pool.productId.should == product.id
    test_pool.providedProducts.size.should == 3
  end

  it 'should allow user to list standard pool by subscription id' do
      product = create_product(nil, nil)
      pool = create_pool_and_subscription(@owner['key'], product.id, 5)
      user = user_client(@owner, random_string('user'))
      # needs to be an owner level user
      user.get_pools_for_subscription(@owner['key'], pool.subscriptionId).size.should == 1
  end

  it 'should allow user to list bonus pool also by subscription id' do
      product = create_product(nil, nil, {:attributes => {"virt_limit" => "unlimited"}})
      pool = create_pool_and_subscription(@owner['key'], product.id, 5)
      user = user_client(@owner, random_string('user'))
      # needs to be an owner level user
      user.get_pools_for_subscription(@owner['key'], pool.subscriptionId).size.should == 2
  end

  it "lets owners filter pools by pool ID" do
    pools = @cp.list_owner_pools(@owner['key'], { :poolid => @pools[0].id })
    expect(pools.length).to eq(1)
    expect(pools[0].id).to eq(@pools[0].id)

    pools = @cp.list_owner_pools(@owner['key'], { :poolid => @pools[1].id })
    expect(pools.length).to eq(1)
    expect(pools[0].id).to eq(@pools[1].id)

    pools = @cp.list_owner_pools(@owner['key'], { :poolid => @pools[2].id })
    expect(pools.length).to eq(1)
    expect(pools[0].id).to eq(@pools[2].id)
  end

  it "lets owners filter pools by multiple pool IDs" do
    poolIds = [@pools[0].id, @pools[2].id]

    pools = @cp.list_owner_pools(@owner['key'], { :poolid => poolIds })

    expect(pools.length).to eq(2)
    expect(pools[0].id).to_not eq(pools[1].id)
    expect(poolIds).to include(pools[0].id)
    expect(poolIds).to include(pools[1].id)
  end

  it "lets owners filter pools by activation_key" do
    activation_key = @cp.create_activation_key(@owner['key'], random_string('test_key'))

    @cp.add_pool_to_key(activation_key['id'], @pools[0].id)
    @cp.add_pool_to_key(activation_key['id'], @pools[1].id)

    # ignores the pools already with the activation key
    pools = @cp.list_owner_pools(@owner['key'],{ :activation_key => activation_key['name']})
    pools.length.should == 1
  end

end

describe 'Owner Resource Consumer Fact Filter Tests' do

  include CandlepinMethods

  before(:each) do
    @owner = create_owner(random_string("test_owner"))
    @owner_client = user_client(@owner, random_string('bill'))
    @consumer1 = @owner_client.register('c1', :system, nil, {'key' => 'value', 'otherkey' => 'otherval'})
    @consumer2 = @owner_client.register('c2', :system, nil, {'key' => 'value', 'otherkey' => 'someval'})
    @consumer3 = @owner_client.register('c3', :system, nil, {'newkey' => 'somevalue'})
  end

  it 'lets owners filter consumers by a single fact' do
    consumers = @cp.list_owner_consumers(@owner['key'], [], ['key:value'])
    consumers.length.should == 2

    consumers = @cp.list_owner_consumers(@owner['key'], [], ['newkey:somevalue'])
    consumers.length.should == 1
    consumers[0]['uuid'].should == @consumer3['uuid']
  end

  it 'lets owners filter consumers by multiple facts' do
    consumers = @cp.list_owner_consumers(@owner['key'], [], ['key:value', 'otherkey:someval'])
    consumers.length.should == 1
    consumers[0]['uuid'].should == @consumer2['uuid']
  end

  it 'lets owners filter consumers by multiple facts same key as OR' do
    consumers = @cp.list_owner_consumers(@owner['key'], [], ['otherkey:someval', 'otherkey:otherval'])
    consumers.length.should == 2
  end

  it 'lets owners filter consumers by facts with wildcards' do
    consumers = @cp.list_owner_consumers(@owner['key'], [], ['*key*:*val*'])
    consumers.length.should == 3

    # Also make sure the value half is checked case insensitively
    consumers = @cp.list_owner_consumers(@owner['key'], [], ['ot*key:SOme*'])
    consumers.length.should == 1
    consumers[0]['uuid'].should == @consumer2['uuid']
  end
end

describe 'Owner Resource Owner Info Tests' do

  include CandlepinMethods

  before(:each) do
    @owner = create_owner(random_string("an_owner"))

    # Create a product limiting by all of our attributes.
    @product = create_product(nil, random_string("Product1"), :attributes =>
                {"version" => '6.4', "sockets" => 2, "multi-entitlement" => "true"})
    create_pool_and_subscription(@owner['key'], @product.id, 100, [], '1888', '1234')

    @owner_client = user_client(@owner, random_string('owner_admin_user'))
    @owner_client.register(random_string('system_consumer'), :system, nil, {})
    @owner_client.register(random_string('system_consumer_guest'), :system, nil, {"virt.is_guest" => "true"})
  end

  it 'my systems user should filter consumer counts in owner info' do
    perms = [{
      :type => 'USERNAME_CONSUMERS',
      :owner => {:key => @owner['key']},
      :access => 'READ_ONLY',
    }]
    my_systems_user = user_client_with_perms(random_string('my_systems_user'), 'password', perms)

    installed_products = [
        {'productId' => @product.id, 'productName' => @product.name}
    ]

    pool = @cp.list_owner_pools(@owner['key'], {:product => @product.id}).first
    pool.should_not be_nil

    # Create a physical system with valid status
    c1 = my_systems_user.register(random_string('system_consumer'), :system, nil, {"cpu.cpu_socket(s)" => 4}, nil, nil, [], installed_products)
    c1_client = registered_consumer_client(c1)
    ents = c1_client.consume_pool(pool.id, {:quantity => 1})
    ents.should_not be_nil

    # Create a guest system with a partial status
    c2 = my_systems_user.register(random_string('system_consumer_guest1'), :system, nil, {"virt.is_guest" => "true"}, nil, nil, [], installed_products)
    c2_client = registered_consumer_client(c2)
    ents = c2_client.consume_pool(pool.id, {:quantity => 1})
    ents.should_not be_nil

    # Create a guest system with invalid status
    my_systems_user.register(random_string('system_consumer_guest2'), :system, nil, {"virt.is_guest" => "true"}, nil, nil, [], installed_products)

    admin_owner_info = @owner_client.get_owner_info(@owner['key'])
    admin_owner_info['consumerCounts']['system'].should == 5

    my_systems_owner_info = my_systems_user.get_owner_info(@owner['key'])
    my_systems_owner_info['consumerCounts']['system'].should == 3
    my_systems_owner_info['consumerGuestCounts']['physical'].should == 1
    my_systems_owner_info['consumerGuestCounts']['guest'].should == 2

    my_systems_owner_info['entitlementsConsumedByType']['system'].should == 2
    my_systems_owner_info['entitlementsConsumedByType']['person'].should == 0

    my_systems_owner_info['consumerCountsByComplianceStatus']['valid'].should == 1
    my_systems_owner_info['consumerCountsByComplianceStatus']['partial'].should == 1
    my_systems_owner_info['consumerCountsByComplianceStatus']['invalid'].should == 1
  end

end

describe 'Owner Resource Entitlement List Tests' do
  include AttributeHelper
  include CandlepinMethods

  before(:each) do
    @owner = create_owner random_string 'test_owner'
    @monitoring_prod = create_product(nil, 'monitoring',
      :attributes => { 'variant' => "Satellite Starter Pack" })
    @virt_prod= create_product(nil, 'virtualization')

    #entitle owner for the virt and monitoring products.
    create_pool_and_subscription(@owner['key'], @monitoring_prod.id, 6,
      [], '', '', '', nil, nil, true)
    create_pool_and_subscription(@owner['key'], @virt_prod.id, 6)

    #create consumer
    user = user_client(@owner, random_string('billy'))
    @system = consumer_client(user, 'system6')
    @system.consume_product(@monitoring_prod.id)
    @system.consume_product(@virt_prod.id)
  end

  it 'can fetch all entitlements of an owner' do
    ents = @cp.list_ents_via_owners_resource(@owner['key'])
    ents.length.should eq(2)
  end

  it 'can filter consumer entitlements by product attribute' do
    ents = @cp.list_ents_via_owners_resource(@owner['key'], {
      :attr_filters => { "variant" => "Satellite Starter Pack" }
    })

    ents.length.should eq(1)

    variant = get_attribute_value(ents[0].pool.productAttributes, "variant")
    expect(variant).to eq("Satellite Starter Pack")
  end
end

describe 'Owner Resource Future Pool Tests' do

  include CandlepinMethods

  before(:each) do
    @now = DateTime.now
    @owner = create_owner random_string 'test_owner'
    @product1 = create_product(random_string('product'), random_string('product'),{:owner => @owner['key']})
    @product2 = create_product(random_string('product'), random_string('product'),{:owner => @owner['key']})
    @current_pool = create_pool_and_subscription(@owner['key'], @product1.id, 10, [], '', '', '', @now - 1)
    start1 = @now + 400
    start2 = @now + 800
    @future_pool1 = create_pool_and_subscription(@owner['key'], @product2.id, 10, [], '', '', '', start1)
    @future_pool2 = create_pool_and_subscription(@owner['key'], @product2.id, 10, [], '', '', '', start2)
  end

  it 'can fetch current pools' do
    pools = @cp.list_owner_pools(@owner['key'])
    pools.length.should eq(1)
    pools[0].id.should eq(@current_pool.id)
  end

  it 'can fetch current and future pools' do
    pools = @cp.list_owner_pools(@owner['key'],{:add_future => "true"})
    pools.length.should eq(3)
  end

  it 'can fetch future pools' do
    pools = @cp.list_owner_pools(@owner['key'],{:only_future => "true"})
    pools.length.should eq(2)
    pools[0].id.should_not eq(@current_pool.id)
    pools[1].id.should_not eq(@current_pool.id)
  end

  it 'can fetch future pools based on activeon date' do
    test_date = @now + 500

    pools = @cp.list_owner_pools(@owner['key'],{:only_future => "true", :activeon => test_date.to_s})
    pools.length.should eq(1)
    pools[0].id.should eq(@future_pool2.id)
  end

  it 'can fetch pools that start after specified date' do
    test_date_1 = @now + 350
    test_date_2 = @now + 750
    pools = @cp.list_owner_pools(@owner['key'],{:after => test_date_1.to_s})
    pools.length.should eq(2)
    pools[0].id.should_not eq(@current_pool.id)
    pools[1].id.should_not eq(@current_pool.id)
    pools = @cp.list_owner_pools(@owner['key'],{:after => test_date_2.to_s})
    pools.length.should eq(1)
    pools[0].id.should eq(@future_pool2.id)
  end

  it 'cannot use both add_future and only_future flags' do
    lambda do
        pools = @cp.list_owner_pools(@owner['key'],{:only_future => "true", :add_future => "true"})
     end.should raise_exception(RestClient::BadRequest)
  end

  it 'cannot use after and either add_future or only_future flags' do
    lambda do
      pools = @cp.list_owner_pools(@owner['key'],{:after => @now+500, :only_future => "true"})
    end.should raise_exception(RestClient::BadRequest)
    lambda do
      pools = @cp.list_owner_pools(@owner['key'],{:after => @now+500, :add_future => "true"})
    end.should raise_exception(RestClient::BadRequest)
  end

  it 'can consume future pools' do
    user = user_client(@owner, 'username')
    consumer = consumer_client(user, random_string('consumer'))

    consumer.consume_pool(@future_pool1['id'])
  end

  it 'can fetch consumer future pools with entitlements' do
    user = user_client(@owner, 'username')
    consumer = user.register(random_string('consumer'), :system, nil, {}, nil, @owner['key'])
    consumer_client = Candlepin.new(nil, nil, consumer.idCert.cert, consumer.idCert['key'])

    consumer_client.consume_pool(@future_pool1['id'])

    test_date = @now + 350
    pools = consumer_client.list_owner_pools(@owner['key'], {:consumer => consumer['uuid'], :after => test_date.to_s})
    expect(pools.length).to eq(1)
    expect(pools[0]['id']).to eq(@future_pool2['id'])
  end

end

describe 'Owner Resource counting feature' do

  include CandlepinMethods

  before(:each) do
    @owner = create_owner(random_string("test_owner"))
    @owner_cp = user_client(@owner, random_string('user_name'))
  end

  it 'should count all consumers of given owner' do
    @owner_cp.register('consumer_name1')
    @owner_cp.register('consumer_name2')

    json_count = @owner_cp.count_owner_consumers(@owner['key'])
    expect(Integer(json_count)).to be == 2

    other_owner = create_owner(random_string("test_owner"))
    other_cp = user_client(other_owner, random_string('bill'))
    other_cp.register('consumer_name3')

    json_count = @cp.count_owner_consumers(other_owner['key'])
    expect(Integer(json_count)).to be == 1
  end

  it 'should count only consumers specified by type label' do
    @owner_cp.register('consumer_name1', type=:system)
    @owner_cp.register('consumer_name2', type=:hypervisor)

    json_count = @owner_cp.count_owner_consumers(@owner['key'], type=[:system])
    expect(Integer(json_count)).to be == 1

    json_count = @owner_cp.count_owner_consumers(@owner['key'], type=[:system, :hypervisor])
    expect(Integer(json_count)).to be == 2
  end

  def create_consumer_with_binding_to_new_product_return_sku(product_type='MKT')
    c = @owner_cp.register('consumer_name')
    create_product_and_bint_it_to_consumer_return_sku(c, product_type)
  end

  def create_product_and_bint_it_to_consumer_return_sku(consumer, product_type='MKT')
    params = {:attributes => {'type' => product_type}}
    p = create_product(@owner['key'], params)

    pool = create_pool_and_subscription(@owner['key'], p.id, 1)
    @owner_cp.consume_pool(pool.id, params={:uuid => consumer.uuid, :quantity => 1})
    p.id #sku
  end

  def create_consumer_with_binding_to_new_product_return_subId
    c = @owner_cp.register('consumer_name')

    create_product_and_bint_it_to_consumer_return_subId(c)
  end

  def create_product_and_bint_it_to_consumer_return_subId(consumer)
    p = create_product(@owner['key'])
    pool = create_pool_and_subscription(@owner['key'], p.id, 1)
    @owner_cp.consume_pool(pool.id, params={:uuid => consumer.uuid, :quantity => 1})
    pool.subscriptionId
  end

  def create_consumer_with_binding_to_new_product_return_contractNr
    c = @owner_cp.register('consumer_name')

    create_product_and_bint_it_to_consumer_return_contractNr(c)
  end

  def create_product_and_bint_it_to_consumer_return_contractNr(consumer)
    p = create_product(@owner['key'])
    cn = random_string('contract_nr')
    pool = create_pool_and_subscription(@owner['key'], p.id, 1, [], cn)
    @owner_cp.consume_pool(pool.id, params={:uuid => consumer.uuid, :quantity => 1})
    pool.contractNumber
  end

  def create_product(owner_key, params={})
    id = random_string('sku')
    name = 'prod_name-' + id
    product = @cp.create_product(owner_key, id, name, params)
  end

end
