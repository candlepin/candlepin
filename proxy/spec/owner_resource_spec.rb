# encoding: utf-8

require 'candlepin_scenarios'

require 'rubygems'
require 'rest_client'

describe 'Owner Resource' do

  include CandlepinMethods
  include CandlepinScenarios

  it 'allows consumers to view their service levels' do
    owner = create_owner random_string('owner1')
    owner_admin = user_client(owner, 'bill')
    owner2 = create_owner random_string('owner2')

    consumer = owner_admin.register('somesystem')
    consumer_client = Candlepin.new(username=nil, password=nil,
        cert=consumer['idCert']['cert'],
        key=consumer['idCert']['key'])

    product1 = create_product(random_string("test_id"),
      random_string("test_name"),
      {:attributes => {:support_level => 'VIP'}})
    @cp.create_subscription(owner['key'], product1.id, 10)

    @cp.refresh_pools(owner['key'])
    levels = consumer_client.list_owner_service_levels(owner['key'])
    levels.size.should == 1
    levels[0].should == 'VIP'

    # Should be rejected listing another owner's service levels:
    lambda do
      consumer_client.list_owner_service_levels(owner2['key'])
    end.should raise_exception(RestClient::Forbidden)
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
    new_owner['key'].should == owner_key
    pools = @cp.list_owner_pools(owner_key)
    pools.length.should == 0
  end

  it "does not let read only users refresh pools" do
    owner = create_owner random_string('test_owner')
    ro_owner_client = user_client(owner, random_string('testuser'), true)
    rw_owner_client = user_client(owner, random_string('testuser'), true)
    product = create_product
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
    end.should raise_exception(RestClient::Forbidden)
  end

  it "lets owners be updated" do
    owner = create_owner random_string("test_owner2")
    original_key = owner['key']
    owner['key']= random_string("test_owner4")

    @cp.update_owner(original_key, owner)
    new_owner = @cp.get_owner(owner['key'])
    new_owner['key'].should == owner['key']
  end

  it "lets owners update their default service level" do
    owner = create_owner random_string("test_owner2")
    owner_key = owner['key']
    owner['defaultServiceLevel'].should be_nil

    # Create a subscription with a service level so we have
    # something available:
    product1 = create_product(random_string("test_id"),
      random_string("test_name"),
      {:attributes => {:support_level => 'VIP'}})
    product2 = create_product(random_string("test_id"),
      random_string("test_name"),
      {:attributes => {:support_level => 'Layered',
                       :support_level_exempt => 'true'}})
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
    owner = create_owner random_string('☠pirate org yarr☠')
  end

  it "lets owners show service levels" do
    owner = create_owner random_string("test_owner1")
    product1 = create_product(random_string("test_id"),
                              random_string("test_name"),
                              {:attributes => {:support_level => 'Really High'}})
    @cp.create_subscription(owner['key'], product1.id, 10)
    product2 = create_product(random_string("test_id"),
                              random_string("test_name"),
                              {:attributes => {:support_level => 'Really Low'}})
    @cp.create_subscription(owner['key'], product2.id, 10)
    product3 = create_product(random_string("test_id"),
                              random_string("test_name"),
                              {:attributes => {:support_level => 'Really Low'}})
    @cp.create_subscription(owner['key'], product3.id, 10)

    @cp.refresh_pools(owner['key'])
    levels = @cp.list_owner_service_levels(owner['key'])
    levels.length.should == 2
  end

  it 'allows service level exempt service levels to be filtered out' do
    owner = create_owner random_string('owner1')
    owner_admin = user_client(owner, 'bill')

    consumer = owner_admin.register('somesystem')
    consumer_client = Candlepin.new(username=nil, password=nil,
        cert=consumer['idCert']['cert'],
        key=consumer['idCert']['key'])

    product1 = create_product(random_string("test_id"),
      random_string("test_name"),
      {:attributes => {:support_level => 'VIP'}})
    product2 = create_product(random_string("test_id"),
      random_string("test_name"),
      {:attributes => {:support_level => 'Layered',
                       :support_level_exempt => 'true'}})
    # the exempt attribute will cover here as well
    # despite the casing
    product3 = create_product(random_string("test_id"),
      random_string("test_name"),
      {:attributes => {:support_level => 'LAYered'}})

    @cp.create_subscription(owner['key'], product1.id, 10)
    @cp.create_subscription(owner['key'], product2.id, 10)
    @cp.create_subscription(owner['key'], product3.id, 10)

    @cp.refresh_pools(owner['key'])
    levels = consumer_client.list_owner_service_levels(owner['key'])
    levels.size.should == 1
    levels[0].should == 'VIP'
  end

end
