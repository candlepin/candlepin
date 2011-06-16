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

  it "lets owners list users" do
    owner = create_owner random_string("test_owner1")
    
    user1 = create_user(owner, random_string("test_user1"), "password")
    user2 = create_user(owner, random_string("test_user2"), "password")

    users = @cp.list_users_by_owner owner.key
   
    users.length.should == 2
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

end
