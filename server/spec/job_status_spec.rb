require 'spec_helper'
require 'candlepin_scenarios'

describe 'Job Status' do
  include CandlepinMethods

  before(:each) do
    @owner = create_owner(random_string("test_owner"))
    @user = user_client(@owner, random_string("test_user"))
    @monitoring = create_product

    @cp.create_subscription(@owner['key'], @monitoring.id, 4)
  end

  it 'should contain the owner key' do
    pending("candlepin not running in standalone mode") if not is_hosted?
    status = @cp.refresh_pools(@owner['key'], true)
    status['targetId'].should == @owner['key']

    wait_for_job(status['id'], 15)
  end

  it 'should contain the target type' do
    pending("candlepin not running in standalone mode") if not is_hosted?
    status = @cp.refresh_pools(@owner['key'], true)
    status['targetType'].should == "owner"

    wait_for_job(status['id'], 15)
  end


  it 'should be findable by owner key' do
    pending("candlepin not running in standalone mode") if not is_hosted?

    jobs = []
    3.times {
        jobs << @cp.refresh_pools(@owner['key'], true)
        wait_for_job(jobs[-1]['id'], 15)
    }

    @cp.list_jobs(@owner['key']).length.should == 3
  end

  it 'should only find jobs with the correct owner key' do
    pending("candlepin not running in standalone mode") if not is_hosted?

    owner2 = create_owner(random_string('some_owner'))
    product = create_product(nil, nil, :owner => owner2['key'])
    @cp.create_subscription(owner2['key'], product.id, 100)

    jobs = []
    # Just some random numbers here
    4.times {
        jobs << @cp.refresh_pools(owner2['key'], true)
        jobs << @cp.refresh_pools(@owner['key'], true)
        wait_for_job(jobs[-1]['id'], 15)
        wait_for_job(jobs[-2]['id'], 15)
    }
    2.times {
        jobs << @cp.refresh_pools(owner2['key'], true)
        wait_for_job(jobs[-1]['id'], 15)
    }

    @cp.list_jobs(@owner['key']).length.should == 4
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
    pending("candlepin not running in standalone mode") if not is_hosted?
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

    wait_for_job(status['id'], 15)
  end


end

