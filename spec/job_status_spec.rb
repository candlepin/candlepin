require 'candlepin_scenarios'

describe 'Job Status' do
  include CandlepinMethods
  include CandlepinScenarios

  before(:each) do
    @owner = create_owner(random_string("test_owner"))
    @user = user_client(@owner, random_string("test_user"))
    @monitoring = create_product

    @cp.create_subscription(@owner['key'], @monitoring.id, 4)
  end

  def finish_job(status)
    #let it finish
    while status != nil && status['state'].downcase != 'finished'
      sleep 1
      # POSTing here will delete the job once it has finished
      status = @owner.post(status['statusPath'])
    end
  end
 
  it 'should contain the owner key' do
    status = @cp.refresh_pools(@owner['key'], true)
    status['targetId'].should == @owner['key']

    finish_job(status)
  end

  it 'should contain the target type' do
    status = @cp.refresh_pools(@owner['key'], true)
    status['targetType'].should == "owner"

    finish_job(status)
  end


  it 'should be findable by owner key' do
    jobs = []
    3.times { jobs << @cp.refresh_pools(@owner['key'], true) }

    @cp.list_jobs(@owner['key']).length.should == 3

    jobs.each do |job|
      finish_job(job)
    end
  end

  it 'should only find jobs with the correct owner key' do
    owner2 = create_owner(random_string('some_owner'))
    @cp.create_subscription(owner2['key'], @monitoring.id, 100)

    jobs = []
    # Just some random numbers here
    6.times { jobs << @cp.refresh_pools(owner2['key'], true) }
    4.times { jobs << @cp.refresh_pools(@owner['key'], true) }

    @cp.list_jobs(@owner['key']).length.should == 4

    jobs.each do |job|
      finish_job(job)
    end
  end

  it 'should find an empty list if the owner key is wrong' do
    @cp.list_jobs('totaly_made_up').should be_empty
  end

  it 'should return an error if no owner key is supplied' do
    lambda do
      @cp.list_jobs('')
    end.should raise_exception(RestClient::BadRequest)
  end

  it 'should cancel a job' do
    @cp.set_scheduler_status(false)
    job = @cp.refresh_pools(@owner['key'], true)
    #make sure we see a job waiting to go
    joblist = @cp.list_jobs(@owner['key'])
    joblist.find { |j| j['id'] == job['id'] }['state'].should == 'CREATED'

    @cp.cancel_job(job['id'])
    #make sure we see a job canceled
    joblist = @cp.list_jobs(@owner['key'])
    joblist.find { |j| j['id'] == job['id'] }['state'].should == 'CANCELED'

    @cp.set_scheduler_status(true)
    sleep 1 #let the job queue drain..
    #make sure job didn't flip to FINISHED
    joblist = @cp.list_jobs(@owner['key'])
    joblist.find { |j| j['id'] == job['id'] }['state'].should == 'CANCELED'
  end

  it 'should contain the system target type and id for async binds' do
    @cp.refresh_pools(@owner['key'])
    system = consumer_client(@user, 'system6')

    status = system.consume_product(@monitoring.id, { :async => true })

    status['targetType'].should == "consumer"
    status['targetId'].should == system.uuid

    finish_job(status)
  end


end

