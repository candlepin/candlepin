require 'spec_helper'
require 'candlepin_scenarios'

describe 'User Resource' do

  include CandlepinMethods

  before(:each) do
    test_owner_key = random_string('testowner')
    @test_owner = create_owner(test_owner_key)
    @username = random_string 'user'
    @user_cp = user_client(@test_owner, @username)
  end

  it "should return a 410 for deleting an unknown user" do
    # Try listing for the test user:
    lambda {
      @cp.delete_user(random_string('unknown-user'))
    }.should raise_exception(RestClient::Gone)
  end

  it "should return a 409 for creating an existing user" do
    # Try listing for the test user:
    lambda {
      create_user(@test_owner, @username, 'password')
    }.should raise_exception(RestClient::Conflict)
  end

  it "should allow users to update their info" do
    # Try listing for the test user:
    @username = random_string 'user'
    user = create_user(@test_owner, @username, 'password')
    new_username = random_string 'username'
    user["username"]= new_username
    newuser = @cp.update_user(user, @username)
    newuser["username"].should == new_username
  end

  it "should return a non empty list of users" do
    user_list = @cp.list_users
    user_list.size.should > 0
  end

  it 'should allow a user to list their owners' do
    visible_owners = @user_cp.list_users_owners(@username)
    visible_owners.size.should == 1
  end

  it "should prevent a user from listing another user's owners" do
    user2_cp = user_client(@test_owner, random_string('user'))
    # Try listing for the test user:
    lambda {
      user2_cp.list_users_owners(@username)
    }.should raise_exception(RestClient::Forbidden)
  end

  it "should be able to get user roles" do
    alice = random_string 'user'
    alice_cp = user_client(@test_owner, alice)
    roles = alice_cp.get_user_roles(alice)
    roles.size.should == 1 #users have access to their own roles by default

    #make a new role, add a permission
    new_perm = [{
      :type => 'OWNER',
      :owner => {:key => @test_owner['key']},
      :access => 'READ_ONLY',
    }]

    new_role = @cp.create_role(random_string('testrole'), new_perm)
    @cp.add_role_user(new_role['id'], alice)

    #make sure we see the extra role
    roles = alice_cp.get_user_roles(alice)
    roles.size.should == 2
    #bob should not see alice's user on his role obj
    bob = random_string 'user'
    bob_cp = user_client(@test_owner, bob)

    @cp.add_role_user(new_role['id'], bob)
    roles = bob_cp.get_user_roles(bob)
    roles.size.should == 2
    roles.each { |role|
      role['users'].select { |u| u['username'] == alice }.should be_empty
    }

    #admin should see both users on the role obj (note the different API call)
    userlist = @cp.get_role(new_role['id'])['users']
    userlist.select { |u| u['username'] == alice }.should_not be_empty
    userlist.select { |u| u['username'] == bob }.should_not be_empty

  end

  it "should not be able to see role for another user" do
    mallory = random_string 'user'
    mallory_cp = user_client(@test_owner, mallory)
    mallory_cp.get_user_roles(mallory)
    lambda {
      mallory_cp.get_user_roles(@username)
    }.should raise_exception(RestClient::Forbidden)
  end

end
