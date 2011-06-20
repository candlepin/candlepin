require 'candlepin_scenarios'

describe 'User Resource' do

  include CandlepinMethods
  include CandlepinScenarios

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

#  it 'should allow a user to list their owners' do
#    visible_owners = @user_cp.list_users_owners(@username)
#    visible_owners.size.should == 1
#  end

#  it "should prevent a user from listing another user's owners" do
#    user2_cp = user_client(@test_owner, random_string('user'))
#    # Try listing for the test user:
#    lambda {
#      user2_cp.list_users_owners(@username)
#    }.should raise_exception(RestClient::Forbidden)
#  end


end


