require 'candlepin_scenarios'

describe 'Job Status' do
  include CandlepinMethods
  include CandlepinScenarios

  before(:each) do
    @owner = create_owner(random_string("test_owner"))
    @user = user_client(@owner, random_string("test_user"))
    @monitoring = create_product

    @cp.create_subscription(@owner.key, @monitoring.id, 4)
  end

  it 'should contain the owner key' do
    status = @cp.refresh_pools(@owner.key, true)
    status['ownerKey'].should == @owner.key
  end

end

