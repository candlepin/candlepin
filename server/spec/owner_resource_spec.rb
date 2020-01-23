# encoding: utf-8
require 'spec_helper'
require 'candlepin_scenarios'

require 'rubygems'
require 'rest_client'
require 'time'
require 'json'

describe 'Owner Resource' do
  include CandlepinMethods

  it 'lets an owner see only their system consumer types' do
    owner1 = create_owner random_string('test_owner1')
    username1 = random_string("user1")
    consumername1 = random_string("consumer1")
    user1 = user_client(owner1, username1)
    consumer1 = consumer_client(user1, consumername1)

    user1.list_owner_consumers(owner1['key'], ['system']).length.should == 1
  end

  it 'lets an owner admin see only their consumers' do
    owner1 = create_owner random_string('test_owner1')
    username1 = random_string("user1")
    consumername1 = random_string("consumer1")
    user1 = user_client(owner1, username1)
    consumer1 = consumer_client(user1, consumername1)

    user1.list_owner_consumers(owner1['key']).length.should == 1
  end

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
    create_pool_and_subscription(owner['key'], product1.id, 10)

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
    end.should raise_exception(RestClient::ResourceNotFound)
  end

  it "lets owners list pools" do
    owner = create_owner random_string("test_owner1")
    product = create_product(nil, nil, :owner => owner['key'])
    create_pool_and_subscription(owner['key'], product.id, 10)
    pools = @cp.list_owner_pools(owner['key'])
    pools.length.should == 1
  end

  it "lets owners list pools in pages" do
    owner = create_owner random_string("test_owner1")
    product = create_product(nil, nil, :owner => owner['key'])
    (1..4).each do |i|
      create_pool_and_subscription(owner['key'], product.id, 10)
    end
    pools = @cp.list_owner_pools(owner['key'], {:page => 1, :per_page => 2, :sort_by => "id", :order => "asc"})
    pools.length.should == 2
    (pools[0].id <=> pools[1].id).should == -1
  end

  it "lets owners update subscription" do
    owner = create_owner random_string("test_owner1")
    product = create_product(nil, nil, :owner => owner['key'])
    pool = create_pool_and_subscription(owner['key'], product.id, 10)
    poolOrSub = get_pool_or_subscription(pool)
    tomorrow = (DateTime.now + 1)
    poolOrSub.startDate = tomorrow
    update_pool_or_subscription(poolOrSub)
    updatedPool = @cp.get_pool(pool.id)

    # parse the received start date and convert it back to our local time zone
    startDate = DateTime.strptime(updatedPool.startDate).new_offset(tomorrow.offset)

    expect(startDate.to_s).to eq(tomorrow.to_s)
  end

  it "lets owners list pools in pages for a consumer" do
    owner = create_owner random_string("test_owner1")
    user = user_client(owner, random_string("bob"))
    system = consumer_client(user, "system")
    product = create_product(nil, nil, :owner => owner['key'])
    (1..4).each do |i|
      create_pool_and_subscription(owner['key'], product.id, 10)
    end
    # Make sure there are 4 available pools
    @cp.list_owner_pools(owner['key'], {:consumer => system.uuid}).length.should == 4
    # Get page 2, per bz 1038273
    pools = @cp.list_owner_pools(owner['key'], {:page => 2, :per_page => 2, :sort_by => "id", :order => "asc", :consumer => system.uuid})
    pools.length.should == 2
    (pools[0].id <=> pools[1].id).should == -1
    # Make sure the total count of pools is returned properly
    response = @cp.list_owner_pools(owner['key'], {:page => 2, :per_page => 2, :sort_by => "id", :order => "asc", :consumer => system.uuid}, [], true)
    response.headers[:x_total_count].should == '4'
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

  it "updates owners on a refresh" do
    skip("candlepin running in standalone mode") unless is_hosted?
    now = Time.now
    owner_key = random_string("new_owner1")
    @cp.refresh_pools(owner_key, false, true)

    new_owner = @cp.get_owner(owner_key)
    @owners << new_owner # this will clean up new_owner at the end of the test.

    # Zero the milliseconds by converting to int since MySQL doesn't have millisecond resolution
    expect(Time.parse(new_owner['lastRefreshed']).utc.to_i).to be >= now.utc.to_i
  end

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

  it "does not let read only users register systems" do
    owner = create_owner random_string('test_owner')
    ro_owner_client = user_client(owner, random_string('testuser'), true)
    rw_owner_client = user_client(owner, random_string('testuser'), false)

    #this will work
    rw_owner_client.register('systemBar')
    #and this will fail
    lambda do
      ro_owner_client.register('systemFoo')
    end.should raise_exception(RestClient::Forbidden)
  end

  it "does not let users register system when owner belong to principal" do
    first_owner = create_owner random_string('test_owner_1')
    second_owner = create_owner random_string('test_owner_2')
    owner_client = user_client(first_owner, random_string('testuser'), true)

    lambda do
      owner_client.register('systemFoo', type=:system, uuid=nil, facts={}, username=nil,
                            owner_key=second_owner['key'])
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
    create_pool_and_subscription(owner['key'], product1.id, 10)
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
    create_pool_and_subscription(owner['key'], product1.id, 10,
      [], '', '', '', nil, nil, true)
    create_pool_and_subscription(owner['key'], product2.id, 10)

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
    create_owner random_string('Ακμή корпорация')
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
    create_pool_and_subscription(owner['key'], product1.id, 10)
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
    create_pool_and_subscription(owner['key'], product2.id, 10)
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
    create_pool_and_subscription(owner['key'], product3.id, 10)
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

    create_pool_and_subscription(owner['key'], product1.id, 10,
      [], '', '', '', nil, nil, true)
    create_pool_and_subscription(owner['key'], product2.id, 10,
      [], '', '', '', nil, nil, true)
    create_pool_and_subscription(owner['key'], product3.id, 10)

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
    create_pool_and_subscription(owner['key'], product.id, 10)

    user = user_client(owner, random_string("billy"))
    system = consumer_client(user, "system")

    pools = @cp.list_owner_pools(owner['key'], {:consumer => system.uuid})
    pool = pools.select { |p| p['owner']['key'] == owner['key'] }.first
    pool['calculatedAttributes']['suggested_quantity'].should == "1"
  end

  it 'can create custom floating pools' do
    owner = create_owner random_string("owner1")
    provided1 = create_product(nil, nil, {:owner => owner['key']})
    provided2 = create_product(nil, nil, {:owner => owner['key']})
    product = create_product(nil, nil, :owner => owner['key'],
      :providedProducts => [provided1['id'], provided2['id']])

    derived_provided = create_product(nil, nil, {:owner => owner['key']})
    derived_product = create_product(nil, nil, :owner => owner['key'],
      :providedProducts => [derived_provided['id']])



    @cp.create_pool(owner['key'], product['id'],
      {
        :provided_products => [provided1['id'], provided2['id']],
        :derived_product_id => derived_product['id'],
        :derived_provided_products => [derived_provided['id']]
    })
    pools = @cp.list_owner_pools(owner['key'])
    pools.size.should == 1
    pool = pools[0]
    pool['providedProducts'].size.should == 2
    pool['derivedProductId'].should == derived_product['id']
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
    create_pool_and_subscription(owner['key'], product.id, 10)

    user = user_client(owner, "robot ninja")
    system = consumer_client(user, "system")
    installed = [{'productId' => product.id, 'productName' => product.name}]
    system.update_consumer({:installedProducts => installed})

    job = user.autoheal_org(owner['key'])
    wait_for_job(job['id'], 30)
    c = @cp.get_consumer(system.uuid)
    c['entitlementCount'].should == 1

    create_pool_and_subscription(owner['key'], product.id, 10)

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

  it 'allows updating autobindDisabled on an owner' do
    owner = create_owner random_string("test_owner2")
    owner_key = owner['key']
    expect(owner['autobindDisabled']).to be false

    # disable autobind
    owner['autobindDisabled'] = true
    @cp.update_owner(owner_key, owner)

    owner = @cp.get_owner(owner_key)
    expect(owner['autobindDisabled']).to be true

    # re-enable autobind
    owner['autobindDisabled'] = false
    @cp.update_owner(owner_key, owner)

    owner = @cp.get_owner(owner_key)
    expect(owner['autobindDisabled']).to be false
  end

  it 'ignores autobindDisabled when not set on incoming owner' do
    owner = create_owner random_string("test_owner2")
    owner_key = owner['key']
    expect(owner['autobindDisabled']).to be false

    # disable autobind
    owner['autobindDisabled'] = true
    @cp.update_owner(owner_key, owner)

    owner = @cp.get_owner(owner_key)
    expect(owner['autobindDisabled']).to be true

    # Attempt to update owner display name
    # and expect no update to the autobindDiabled
    # field.
    owner['autobindDisabled'] = nil
    @cp.update_owner(owner_key, owner)

    owner = @cp.get_owner(owner_key)
    expect(owner['autobindDisabled']).to be true
  end

  it 'lists owners with populated entity collections' do
    owner1 = create_owner(random_string("owner1"))

    owner2 = create_owner(random_string("owner2"))
    @cp.create_activation_key(owner2['key'], random_string("actkey1"))
    @cp.create_activation_key(owner2['key'], random_string("actkey2"))

    owner3 = create_owner(random_string("owner3"))
    @cp.create_activation_key(owner3['key'], random_string("actkey1"))
    @cp.create_activation_key(owner3['key'], random_string("actkey2"))
    @cp.register(random_string("consumer1"), :system, nil, {}, random_string("consumer1"), owner3['key'])
    @cp.register(random_string("consumer2"), :system, nil, {}, random_string("consumer2"), owner3['key'])

    owner4 = create_owner(random_string("owner4"))
    @cp.create_activation_key(owner4['key'], random_string("actkey1"))
    @cp.create_activation_key(owner4['key'], random_string("actkey2"))
    @cp.register(random_string("consumer1"), :system, nil, {}, random_string("consumer1"), owner4['key'])
    @cp.register(random_string("consumer2"), :system, nil, {}, random_string("consumer2"), owner4['key'])
    @cp.create_environment(owner4['key'], random_string("env1"), random_string("env1"))
    @cp.create_environment(owner4['key'], random_string("env2"), random_string("env2"))

    owners = @cp.list_owners()
    expect(owners.length).to be >= 4

    # At the time of writing, we don't actually include any of the above objects in the serialized
    # output for owners, so we don't need to verify their presence.
  end

  it 'lists system purpose attributes of its products' do
    owner_key = random_string("owner")
    @cp.create_owner(owner_key)

    product = create_product(random_string("p1"), random_string("Product1"),
      {
        :owner => owner_key,
        :attributes => {:usage => "Development", :roles => "Server1,Server2", :addons => "addon1,addon2", :support_level => "mysla"}
      }
    )
    create_pool_and_subscription(owner_key, product.id, 10, [], '', '', '', nil, nil, true)
    res = @cp.get_owner_syspurpose(owner_key)
    expect(res["owner"]["key"]).to eq(owner_key)
    expect(res["systemPurposeAttributes"]["usage"]).to include("Development")
    expect(res["systemPurposeAttributes"]["roles"]).to include("Server1")
    expect(res["systemPurposeAttributes"]["roles"]).to include("Server2")
    expect(res["systemPurposeAttributes"]["addons"]).to include("addon1")
    expect(res["systemPurposeAttributes"]["addons"]).to include("addon2")
    expect(res["systemPurposeAttributes"]["support_level"]).to include("mysla")
  end

  it 'lists system purpose attributes of its consumers' do
    owner1_key = random_string("owner1")
    owner1 = @cp.create_owner(owner1_key)

    username1 = random_string("user1")
    user1 = user_client(owner1, username1)

    consumer1 = user1.register(random_string('consumer1'), :system, nil, {}, random_string('consumer1'), owner1_key,
      [], [], nil, [], nil, [], nil, nil, nil, nil, nil, nil, nil, 'sla1', 'common_role', 'usage1', ['addon1'])
    consumer2 = user1.register(random_string('consumer2'), :system, nil, {}, random_string('consumer2'), owner1_key,
      [], [], nil, [], nil, [], nil, nil, nil, nil, nil, nil, nil, 'sla2', 'common_role', 'usage2', ['addon2'])
    consumer3 = user1.register(random_string('consumer3'), :system, nil, {}, random_string('consumer3'), owner1_key,
      [], [], nil, [], nil, [], nil, nil, nil, nil, nil, nil, nil, nil, nil, 'usage3', [''])
    consumer4 = user1.register(random_string('consumer4'), :system, nil, {}, random_string('consumer4'), owner1_key,
      [], [], nil, [], nil, [], nil, nil, nil, nil, nil, nil, nil, nil, '', 'usage4', nil)

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

    create_pool_and_subscription(owner['key'], product.id, 10, [provided_product.id, provided_product2.id, provided_product3.id])

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

  it 'can filter all entitlements by using matches param' do
    ents = @cp.list_ents_via_owners_resource(@owner['key'], {:matches => "virtualization"})
    ents.length.should eq(1)
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

  it 'should count consumers only with specific skus' do
    c = @owner_cp.register('consumer_name')
    sku1 = create_product_and_bint_it_to_consumer_return_sku(c)
    sku2 = create_product_and_bint_it_to_consumer_return_sku(c)
    sku3 = create_consumer_with_binding_to_new_product_return_sku
    expect(sku1).not_to be(sku2)
    expect(sku2).not_to be(sku3)

    json_count = @owner_cp.count_owner_consumers(@owner['key'], [], ["not-existing-sku"])
    expect(Integer(json_count)).to be == 0

    json_count = @owner_cp.count_owner_consumers(@owner['key'], [], [sku1])
    expect(Integer(json_count)).to be == 1

    json_count = @owner_cp.count_owner_consumers(@owner['key'], [], [sku1, sku2])
    expect(Integer(json_count)).to be == 1

    json_count = @owner_cp.count_owner_consumers(@owner['key'], [], [sku1, sku2, sku3])
    expect(Integer(json_count)).to be == 2
  end

  it 'should count consumers only with specific subscriptionIds' do
    c = @owner_cp.register('consumer_name')
    subId1 = create_product_and_bint_it_to_consumer_return_subId(c)
    subId2 = create_product_and_bint_it_to_consumer_return_subId(c)
    subId3 = create_consumer_with_binding_to_new_product_return_subId
    expect(subId1).not_to be(subId2)
    expect(subId2).not_to be(subId3)

    json_count = @owner_cp.count_owner_consumers(@owner['key'], [], [], ["not-existing-subId"])
    expect(Integer(json_count)).to be == 0

    json_count = @owner_cp.count_owner_consumers(@owner['key'], [], [], [subId1])
    expect(Integer(json_count)).to be == 1

    json_count = @owner_cp.count_owner_consumers(@owner['key'], [], [], [subId1, subId2])
    expect(Integer(json_count)).to be == 1

    json_count = @owner_cp.count_owner_consumers(@owner['key'], [], [], [subId1, subId2, subId3])
    expect(Integer(json_count)).to be == 2
  end

  it 'should count consumer only with specific contract numbers' do
    c = @owner_cp.register('consumer_name')
    cn1 = create_product_and_bint_it_to_consumer_return_contractNr(c)
    cn2 = create_product_and_bint_it_to_consumer_return_contractNr(c)
    cn3 = create_consumer_with_binding_to_new_product_return_contractNr
    expect(cn1).not_to be(cn2)
    expect(cn2).not_to be(cn3)

    json_count = @owner_cp.count_owner_consumers(@owner['key'], [], [], [], ["not-exisitng-cn"])
    expect(Integer(json_count)).to be == 0

    json_count = @owner_cp.count_owner_consumers(@owner['key'], [], [], [], [cn1])
    expect(Integer(json_count)).to be == 1

    json_count = @owner_cp.count_owner_consumers(@owner['key'], [], [], [], [cn1, cn2])
    expect(Integer(json_count)).to be == 1

    json_count = @owner_cp.count_owner_consumers(@owner['key'], [], [], [], [cn1, cn2, cn3])
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
