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

  it 'should be findable by owner key' do
    3.times { @cp.refresh_pools(@owner.key, true) }

    @cp.list_jobs(@owner.key).length.should == 3
  end

  it 'should only find jobs with the correct owner key' do
    owner2 = create_owner(random_string('some_owner'))
    @cp.create_subscription(owner2.key, @monitoring.id, 100)

    # Just some random numbers here
    6.times { @cp.refresh_pools(owner2.key, true) }
    4.times { @cp.refresh_pools(@owner.key, true) }

    @cp.list_jobs(@owner.key).length.should == 4
  end

  it 'should find an empty list if the owner key is wrong' do
    @cp.list_jobs('totaly_made_up').should be_empty
  end

  it 'should return an error if no owner key is supplied' do
    lambda do
      @cp.list_jobs('')
    end.should raise_exception(RestClient::BadRequest)
  end

end

