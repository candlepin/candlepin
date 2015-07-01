# encoding: utf-8
require 'spec_helper'
require 'candlepin_scenarios'

require 'rubygems'
require 'rest_client'

describe 'Owner Resource' do

  include CandlepinMethods

  it 'allows consumers to view their service levels' do
    owner = create_owner random_string('owner1')
    owner_admin = user_client(owner, random_string('bill'))
    owner2 = create_owner random_string('owner2')

    consumer = owner_admin.register('somesystem')
    consumer_client = Candlepin.new(nil, nil, consumer['idCert']['cert'], consumer['idCert']['key'])

    product1 = create_product(
      random_string("test_id"),
      random_string("test_name"),
      {
        :attributes => {:support_level => 'VIP'},
        :owner => owner['key']
      }
    )
    @cp.create_subscription(owner['key'], product1.id, 10)

    @cp.refresh_pools(owner['key'])
    levels = consumer_client.list_owner_service_levels(owner['key'])
    levels.size.should == 1
    levels[0].should == 'VIP'

    # Should be rejected listing another owner's service levels:
    lambda do
      consumer_client.list_owner_service_levels(owner2['key'])
    end.should raise_exception(RestClient::ResourceNotFound)
  end

  it 'should allow a client to create an owner with parent' do
    owner = create_owner random_string('test_owner')
    child_owner = create_owner(random_string('test_owner'), owner)
    child_owner.parentOwner.id.should == owner.id
  end

  it 'should throw bad request exception when parentOwner is invalid' do
    fake_parent_owner = {
      'key' => 'something',
      'displayName' => 'something something',
      'id' => 'doesNotExist'
    }

    lambda do
      @cp.create_owner(random_string('child owner'), {:parent => fake_parent_owner})
    end.should raise_exception(RestClient::BadRequest)
  end

  it "lets owners list pools" do
    owner = create_owner random_string("test_owner1")
    product = create_product(nil, nil, :owner => owner['key'])
    @cp.create_subscription(owner['key'], product.id, 10)
    @cp.refresh_pools(owner['key'])
    pools = @cp.list_owner_pools(owner['key'])
    pools.length.should == 1
  end

  it "lets owners list pools in pages" do
    owner = create_owner random_string("test_owner1")
    product = create_product(nil, nil, :owner => owner['key'])
    (1..4).each do |i|
      @cp.create_subscription(owner['key'], product.id, 10)
    end
    @cp.refresh_pools(owner['key'])
    pools = @cp.list_owner_pools(owner['key'], {:page => 1, :per_page => 2, :sort_by => "id", :order => "asc"})
    pools.length.should == 2
    (pools[0].id <=> pools[1].id).should == -1
  end

  it "lets owners list pools in pages for a consumer" do
    owner = create_owner random_string("test_owner1")
    user = user_client(owner, random_string("bob"))
    system = consumer_client(user, "system")
    product = create_product(nil, nil, :owner => owner['key'])
    (1..4).each do |i|
      @cp.create_subscription(owner['key'], product.id, 10)
    end
    @cp.refresh_pools(owner['key'])
    # Make sure there are 4 available pools
    @cp.list_owner_pools(owner['key'], {:consumer => system.uuid}).length.should == 4
    # Get page 2, per bz 1038273
    pools = @cp.list_owner_pools(owner['key'], {:page => 2, :per_page => 2, :sort_by => "id", :order => "asc", :consumer => system.uuid})
    pools.length.should == 2
    (pools[0].id <=> pools[1].id).should == -1
  end

  it "lets owners be created and refreshed at the same time" do
    owner_key = random_string("new_owner1")
    @cp.refresh_pools(owner_key, false, true)

    new_owner = @cp.get_owner(owner_key)
    @owners << new_owner # this will clean up new_owner at the end of the test.

    new_owner['key'].should == owner_key
    pools = @cp.list_owner_pools(owner_key)
    pools.length.should == 0
  end

  it "does not let read only users refresh pools" do
    owner = create_owner random_string('test_owner')
    ro_owner_client = user_client(owner, random_string('testuser'), true)
    rw_owner_client = user_client(owner, random_string('testuser'), true)
    product = create_product(nil, nil, :owner => owner['key'])
    @cp.create_subscription(owner['key'], product.id, 10)


    #these should both fail, only superadmin can refresh pools
    lambda do
      ro_owner_client.refresh_pools(owner['key'])
    end.should raise_exception(RestClient::Forbidden)

    lambda do
      rw_owner_client.refresh_pools(owner['key'])
    end.should raise_exception(RestClient::Forbidden)

  end

  it "does not let read only users register systems" do
    owner = create_owner random_string('test_owner')
    ro_owner_client = user_client(owner, random_string('testuser'), true)
    rw_owner_client = user_client(owner, random_string('testuser'), false)

    #this will work
    rw_owner_client.register('systemBar')
    #and this will fail
    lambda do
      ro_owner_client.register('systemFoo')
    end.should raise_exception(RestClient::ResourceNotFound)
  end

  it "does not let the owner key get updated" do
    owner = create_owner random_string("test_owner2")
    original_key = owner['key']
    owner['key']= random_string("test_owner4")

    @cp.update_owner(original_key, owner)
    lambda do
      @cp.get_owner(owner['key'])
    end.should raise_exception(RestClient::ResourceNotFound)
    ## set back local key for delete
    owner['key'] = original_key
  end

  it "does allow the parent owner only to get updated" do
    # information passed had no other data elements
    parent_owner1 = create_owner random_string('test_owner')
    parent_owner2 = create_owner random_string('test_owner')
    child_owner = create_owner(random_string('test_owner'), parent_owner1)
    child_owner.parentOwner.id.should == parent_owner1.id

    @cp.update_owner(child_owner['key'], {:parentOwner => parent_owner2})
    child_owner = @cp.get_owner(child_owner['key'])
    child_owner.parentOwner.id.should == parent_owner2.id
  end

  it "does allow the default service level only to get updated" do
    # information passed has no other data elements
    owner = create_owner random_string("test_owner")
    product1 = create_product(
      random_string("test_id"),
      random_string("test_name"),
      {
        :attributes => {:support_level => 'VIP'},
        :owner => owner['key']
      }
    )
    @cp.create_subscription(owner['key'], product1.id, 10)
    @cp.refresh_pools(owner['key'])
    owner = @cp.get_owner(owner['key'])
    owner['defaultServiceLevel'].should be_nil

    @cp.update_owner(owner['key'], {:defaultServiceLevel => 'VIP'})
    owner = @cp.get_owner(owner['key'])
    owner['defaultServiceLevel'].should == 'VIP'
  end

  it "lets owners update their default service level" do
    owner = create_owner random_string("test_owner2")
    owner_key = owner['key']
    owner['defaultServiceLevel'].should be_nil

    # Create a subscription with a service level so we have
    # something available:
    product1 = create_product(
      random_string("test_id"),
      random_string("test_name"),
      {
        :attributes => {:support_level => 'VIP'},
        :owner => owner['key']
      }
    )
    product2 = create_product(
      random_string("test_id"),
      random_string("test_name"),
      {
        :attributes => {
          :support_level => 'Layered',
          :support_level_exempt => 'true'
        },
        :owner => owner['key']
      }
    )
    @cp.create_subscription(owner['key'], product1.id, 10)
    @cp.create_subscription(owner['key'], product2.id, 10)
    @cp.refresh_pools(owner['key'])

    # Set an initial service level:
    owner['defaultServiceLevel'] = 'VIP'
    @cp.update_owner(owner_key, owner)
    new_owner = @cp.get_owner(owner_key)
    new_owner['defaultServiceLevel'].should == 'VIP'

    # Try setting a service level not available in the org:
    owner['defaultServiceLevel'] = 'TooElite'
    lambda do
      @cp.update_owner(owner_key, owner)
    end.should raise_exception(RestClient::BadRequest)

    # Make sure we can 'unset' with empty string:
    owner['defaultServiceLevel'] = ''
    @cp.update_owner(owner_key, owner)
    new_owner = @cp.get_owner(owner_key)
    new_owner['defaultServiceLevel'].should be_nil

    # Set an initial service level different casing:
    owner['defaultServiceLevel'] = 'vip'
    @cp.update_owner(owner_key, owner)
    new_owner = @cp.get_owner(owner_key)
    new_owner['defaultServiceLevel'].should == 'vip'

    # Cannot set exempt level:
    owner['defaultServiceLevel'] = 'Layered'
    lambda do
      @cp.update_owner(owner_key, owner)
    end.should raise_exception(RestClient::BadRequest)

  end

  it 'returns a 404 for a non-existant owner' do
    lambda do
      @cp.get_owner('fake-uuid')
    end.should raise_exception(RestClient::ResourceNotFound)
  end

  it 'should allow unicode owner creation' do
    create_owner random_string('☠pirate org yarr☠')
  end

  it "lets owners show service levels" do
    owner = create_owner random_string("test_owner1")
    product1 = create_product(
      random_string("test_id"),
      random_string("test_name"),
      {
        :attributes => {
          :support_level => 'Really High'
        },
        :owner => owner['key']
      }
    )
    @cp.create_subscription(owner['key'], product1.id, 10)
    product2 = create_product(
      random_string("test_id"),
      random_string("test_name"),
      {
        :attributes => {
          :support_level => 'Really Low'
        },
        :owner => owner['key']
      }
    )
    @cp.create_subscription(owner['key'], product2.id, 10)
    product3 = create_product(
      random_string("test_id"),
      random_string("test_name"),
      {
        :attributes => {
          :support_level => 'Really Low'
        },
        :owner => owner['key']
      }
    )
    @cp.create_subscription(owner['key'], product3.id, 10)

    @cp.refresh_pools(owner['key'])
    levels = @cp.list_owner_service_levels(owner['key'])
    levels.length.should == 2
  end

  it 'allows service level exempt service levels to be filtered out' do
    owner = create_owner random_string('owner1')
    owner_admin = user_client(owner, random_string('bill'))

    consumer = owner_admin.register('somesystem')
    consumer_client = Candlepin.new(nil, nil, consumer['idCert']['cert'], consumer['idCert']['key'])

    product1 = create_product(
      random_string("test_id"),
      random_string("test_name"),
      {
        :attributes => {:support_level => 'VIP'},
        :owner => owner['key']
      }
    )
    product2 = create_product(
      random_string("test_id"),
      random_string("test_name"),
      {
        :attributes => {
          :support_level => 'Layered',
          :support_level_exempt => 'true'
        },
        :owner => owner['key']
      }
    )
    # the exempt attribute will cover here as well
    # despite the casing
    product3 = create_product(
      random_string("test_id"),
      random_string("test_name"),
      {
        :attributes => {:support_level => 'LAYered'},
        :owner => owner['key']
      }
    )

    @cp.create_subscription(owner['key'], product1.id, 10)
    @cp.create_subscription(owner['key'], product2.id, 10)
    @cp.create_subscription(owner['key'], product3.id, 10)

    @cp.refresh_pools(owner['key'])
    levels = consumer_client.list_owner_service_levels(owner['key'])
    levels.size.should == 1
    levels[0].should == 'VIP'

    levels = consumer_client.list_owner_service_levels(owner['key'], true)
    levels.size.should == 1
    levels[0].should == 'Layered'
  end

  it 'should return calculated attributes' do
    owner = create_owner random_string("owner1")
    product = create_product(
      random_string("test_id"),
      random_string("test_name"),
      :owner => owner['key']
    )
    @cp.create_subscription(owner['key'], product.id, 10)
    @cp.refresh_pools(owner['key'])

    user = user_client(owner, random_string("billy"))
    system = consumer_client(user, "system")

    pools = @cp.list_owner_pools(owner['key'], {:consumer => system.uuid})
    pool = pools.select { |p| p['owner']['key'] == owner['key'] }.first
  end

  it 'can create custom floating pools' do
    owner = create_owner random_string("owner1")
    prod = create_product(nil, nil, {:owner => owner['key']})
    provided1 = create_product(nil, nil, {:owner => owner['key']})
    provided2 = create_product(nil, nil, {:owner => owner['key']})
    provided3 = create_product(nil, nil, {:owner => owner['key']})
    provided4 = create_product(nil, nil, {:owner => owner['key']})
    @cp.create_pool(owner['key'], prod['id'],
      {
        :provided_products => [provided1['id'], provided2['id']],
        :derived_product_id => provided3['id'],
        :derived_provided_products => [provided4['id']]
    })
    pools = @cp.list_owner_pools(owner['key'])
    pools.size.should == 1
    pool = pools[0]
    pool['providedProducts'].size.should == 2
    pool['derivedProductId'].should == provided3['id']
    pool['derivedProvidedProducts'].size.should == 1

    # Refresh should have no effect:
    @cp.refresh_pools(owner['key'])
    @cp.list_owner_pools(owner['key']).size.should == 1

    # Satellite will need to be able to clean it up as well:
    @cp.delete_pool(pool['id'])
    @cp.list_owner_pools(owner['key']).size.should == 0
  end

  it 'should not double bind when healing an org' do
    # BZ 988549
    owner = create_owner(random_string("owner1"))
    product = create_product(
      random_string("test_id"),
      random_string("test_name"),
      :owner => owner['key']
    )
    @cp.create_subscription(owner['key'], product.id, 10)
    @cp.refresh_pools(owner['key'])

    user = user_client(owner, "robot ninja")
    system = consumer_client(user, "system")
    installed = [{'productId' => product.id, 'productName' => product.name}]
    system.update_consumer({:installedProducts => installed})

    job = user.autoheal_org(owner['key'])
    wait_for_job(job['id'], 30)
    c = @cp.get_consumer(system.uuid)
    c['entitlementCount'].should == 1

    @cp.create_subscription(owner['key'], product.id, 10)
    @cp.refresh_pools(owner['key'])

    job = user.autoheal_org(owner['key'])
    wait_for_job(job['id'], 30)
    c = @cp.get_consumer(system.uuid)
    c['entitlementCount'].should == 1
  end

  it 'should allow admin users to set org debug mode' do
    owner = create_owner(random_string("debug_owner"))
    @cp.set_owner_log_level(owner['key'])

    owner = @cp.get_owner(owner['key'])
    owner['logLevel'].should == "DEBUG"

    @cp.delete_owner_log_level(owner['key'])
    owner = @cp.get_owner(owner['key'])
    owner['logLevel'].should be_nil
  end

  it 'should not allow setting bad log levels' do
    owner = create_owner(random_string("debug_owner"))
    lambda do
      @cp.set_owner_log_level(owner['key'], "THISLEVELISBAD")
    end.should raise_exception(RestClient::BadRequest)
  end

  it 'should allow consumer lookup by consumer types' do
    owner = create_owner random_string("type-owner")
    owner_admin = user_client(owner, random_string('type-owner-user'))

    owner_admin.register("system1-consumer")
    owner_admin.register("system2-consumer")
    owner_admin.register("hypervisor-consumer", type=:hypervisor)
    owner_admin.register("distributor-consumer", type=:candlepin)

    systems = owner_admin.list_owner_consumers(owner['key'], types=["system"])
    systems.length.should == 2
    systems.each { |consumer| consumer['type']['label'].should == "system" }

    hypervisors = owner_admin.list_owner_consumers(owner['key'], types=["hypervisor"])
    hypervisors.length.should == 1
    hypervisors.each { |consumer| consumer['type']['label'].should == "hypervisor" }

    distributors = owner_admin.list_owner_consumers(owner['key'], types=["candlepin"])
    distributors.length.should == 1
    distributors.each { |consumer| consumer['type']['label'].should == "candlepin" }

    # Now that we have our counts we can do a lookup for multiple types
    consumers = owner_admin.list_owner_consumers(owner['key'], types=["hypervisor", "candlepin"])
    consumers.length.should == 2
    found = []
    consumers.each { |consumer| found << consumer['type']['label']}
    found.length.should == 2
    found.delete("hypervisor").should_not be_nil
    found.delete("candlepin").should_not be_nil
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

    @cp.create_subscription(@owner['key'], @product1.id, 10)
    @cp.create_subscription(@owner['key'], @product2.id, 10)

    @cp.refresh_pools(@owner['key'])
    pools = @cp.list_owner_pools(@owner['key'])
    pools.length.should == 2
  end

  it "lets owners filter pools by single filter" do
    pools = @cp.list_owner_pools(@owner['key'], {}, ["support_level:VIP"])
    pools.length.should == 1
    pools[0].productId.should == @product1.id

    # Now with wildcards:
    pools = @cp.list_owner_pools(@owner['key'], {}, ["sup*evel:V*P"])
    pools.length.should == 1
    pools[0].productId.should == @product1.id

    pools = @cp.list_owner_pools(@owner['key'], {}, ["sup?ort_level:V??"])
    pools.length.should == 1
    pools[0].productId.should == @product1.id
  end

  it "lets owners filter pools by multiple filter" do
    pools = @cp.list_owner_pools(@owner['key'], {}, ["support_level:Supurb", "cores:4"])
    pools.length.should == 1
    pools[0].productId.should == @product2.id
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

  it 'lets owners filterconsumers by facts with wildcards' do
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
    @subscription = @cp.create_subscription(@owner['key'], @product.id, 100, [], '1888', '1234')

    # Refresh pools so that the subscription pools will be available to the test systems.
    @cp.refresh_pools(@owner['key'])

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
    my_systems_user = user_client_with_perms(@owner, random_string('my_systems_user'), 'password', perms)

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

    my_systems_owner_info['consumerCountsByComplianceStatus']['valid'].should == 1
    my_systems_owner_info['consumerCountsByComplianceStatus']['partial'].should == 1
    my_systems_owner_info['consumerCountsByComplianceStatus']['invalid'].should == 1
  end

end

