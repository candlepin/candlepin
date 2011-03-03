require 'candlepin_scenarios'
require 'candlepin_api'

require 'rubygems'
require 'rest_client'

describe 'Owner Resource' do
  include CandlepinMethods
  it_should_behave_like 'Candlepin Scenarios'

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
      @cp.create_owner(random_string('child owner'), fake_parent_owner)
    end.should raise_exception(RestClient::BadRequest)
  end

  it "lets owners list users" do
    owner = create_owner random_string("test_owner1")
    
    user1 = @cp.create_user(owner.key, random_string("test_user1"), "password")
    user2 = @cp.create_user(owner.key, random_string("test_user2"), "password")

    users = @cp.list_users_by_owner owner.key
   
    users.length.should == 2
  end

  it "lets owners be updated" do
    owner = create_owner random_string("test_owner2")
    original_key = owner.key
    owner.key= random_string("test_owner4")
    
    @cp.update_owner(original_key, owner)
    new_owner = @cp.get_owner(owner.key)
    new_owner.key.should == owner.key
  end

  it 'emits an event when migrated' do
    owner = create_owner random_string('migrating_owner')
    @cp.migrate_owner(owner.key, 'https://localhost:8443/candlepin/')

    # TODO:  Fix me!

    #@cp.list_events.find do |e|
    #  e['entityId'] == owner.id && 
    #  e['type'] == 'MODIFIED' &&
    #  e['target'] == 'OWNER'
    #end.should_not be_nil
  end
end
