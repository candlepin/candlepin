require 'candlepin_scenarios'

require 'rubygems'
require 'rest_client'

describe 'Owner Resource' do
  include CandlepinMethods
  include CandlepinScenarios

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
    product = create_product
    @cp.create_subscription(owner['key'], product.id, 10)
    @cp.refresh_pools(owner['key'])
    pools = @cp.list_owner_pools(owner['key'])
    pools.length.should == 1
  end

  it "lets owners be created and refreshed at the same time" do
    owner_key = random_string("new_owner1")
    @cp.refresh_pools(owner_key, false, true)
    new_owner = @cp.get_owner(owner_key)
    new_owner.key.should == owner_key
    pools = @cp.list_owner_pools(owner_key)
    pools.length.should == 0
  end

  it "does not let read only users refresh pools" do
    owner = create_owner random_string('test_owner')
    ro_owner_client = user_client(owner, random_string('testuser'), true)
    rw_owner_client = user_client(owner, random_string('testuser'), true)
    product = create_product
    @cp.create_subscription(owner.key, product.id, 10)


    #these should both fail, only superadmin can refresh pools
    lambda do
      ro_owner_client.refresh_pools(owner.key)
    end.should raise_exception(RestClient::Forbidden)

    lambda do
      rw_owner_client.refresh_pools(owner.key)
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
    end.should raise_exception(RestClient::Forbidden)
  end

  it "lets owners be updated" do
    owner = create_owner random_string("test_owner2")
    original_key = owner.key
    owner.key= random_string("test_owner4")

    @cp.update_owner(original_key, owner)
    new_owner = @cp.get_owner(owner.key)
    new_owner.key.should == owner.key
  end

  it "updates consumed entitlement count" do
    #TODO put this into candlepin_api.rb if others want to use it
    def stats_helper(owner, expected_available, expected_consumed)
        @cp.refresh_pools owner.key
        @cp.generate_statistics

        info = @cp.get_owner_info(owner.key)
        info.totalSubscriptionCount.first.value.should == expected_available

        stat = info.totalSubscriptionsConsumed.select {|stat| stat.valueType == 'RAW' && stat.entryType == 'TOTALSUBSCRIPTIONCONSUMED' }
        total_consumed = 0
        stat.each{ |s| total_consumed += s.value}
        total_consumed == expected_consumed
    end

    #TODO maybe move to a before(:each)
    owner = create_owner random_string('test_owner')
    user = user_client(owner, random_string('guy'))
    product = create_product(nil, random_string('consume-me'))

    @cp.create_subscription(owner.key, product.id, 4)
    @cp.create_subscription(owner.key, product.id, 4)
    @cp.refresh_pools owner.key

    consumer = consumer_client(user, random_string('consumer'))
    pool = consumer.list_pools(
      :product => product.id,
      :consumer => consumer.uuid).first
    self.stats_helper(owner, 8, 0)
    consumer.consume_pool(pool.id).first
    self.stats_helper(owner, 8, 1)

  end

  it "finds nearest entitlement to expiration" do
    #TODO maybe move to a before(:each)
    owner = create_owner random_string('test_owner')
    user = user_client(owner, random_string('guy'))
    product = create_product(nil, random_string('consume-me'))
    consumer = consumer_client(user, random_string('consumer'))

    @cp.create_subscription(owner.key, product.id, 1, [], nil, '432', nil, end_date=Date.today + 10)
    @cp.refresh_pools owner.key
    info = @cp.get_owner_info(owner.key)
    pool = consumer.list_pools(
      :product => product.id,
      :consumer => consumer.uuid)
    pool1 = info['poolNearestToExpiry']

    @cp.create_subscription(owner.key, product.id, 1, [], nil, '43', nil, end_date=Date.today + 5)
    @cp.refresh_pools owner.key
    info = @cp.get_owner_info(owner.key)
    pool = consumer.list_pools(
      :product => product.id,
      :consumer => consumer.uuid)
    pool2 = info['poolNearestToExpiry']

    pool1.should_not == pool2
  end

  it 'returns a 404 for a non-existant owner' do
    lambda do
      @cp.get_owner('fake-uuid')
    end.should raise_exception(RestClient::ResourceNotFound)
  end

  it 'should allow unicode owner creation' do
    owner = create_owner random_string('☠pirate org yarr☠')
  end

end
