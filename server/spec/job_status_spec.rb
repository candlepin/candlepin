require 'spec_helper'
require 'candlepin_scenarios'

describe 'Job Status' do
  include CandlepinMethods

  before(:each) do
    @owner = create_owner(random_string("test_owner"))
    @owner2 = create_owner(random_string("test_owner_2"))
    @user = user_client(@owner, random_string("test_user"))
    @monitoring = create_product

    create_pool_and_subscription(@owner['key'], @monitoring.id, 4)
  end

  it 'should contain the owner key' do
    status = @cp.autoheal_org(@owner['key'])
    status['targetId'].should == @owner['key']

    wait_for_job(status['id'], 15)
  end

  it 'should contain the target type' do
    status = @cp.autoheal_org(@owner['key'])
    status['targetType'].should == "owner"

    wait_for_job(status['id'], 15)
  end


  it 'should be findable by owner key' do
    jobs = []
    3.times {
        jobs << @cp.autoheal_org(@owner['key'])
        wait_for_job(jobs[-1]['id'], 15)
    }
    # in hosted mode we will get a refresh pools job
    jobs = @cp.list_jobs(@owner['key'])
    jobs = jobs.select{ |job| job.id.start_with?('heal_entire_org') }
    jobs.length.should == 3
  end

  it 'should only find jobs with the correct owner key' do
    owner2 = create_owner(random_string('some_owner'))
    product = create_product(nil, nil, :owner => owner2['key'])
    create_pool_and_subscription(owner2['key'], product.id, 100)

    jobs = []
    # Just some random numbers here
    4.times {
        jobs << @cp.autoheal_org(owner2['key'])
        jobs << @cp.autoheal_org(@owner['key'])
        wait_for_job(jobs[-1]['id'], 15)
        wait_for_job(jobs[-2]['id'], 15)
    }
    2.times {
        jobs << @cp.autoheal_org(owner2['key'])
        wait_for_job(jobs[-1]['id'], 15)
    }
    jobs2 = []
    jobs2 = @cp.list_jobs(@owner['key'])
    jobs2.each do |job|
      job.targetId.should == @owner['key']
    end
    # in hosted mode we will get a refresh pools job
    jobs2 = jobs2.select{ |job| job.id.start_with?('heal_entire_org') }
    jobs2.length.should == 4
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
    job = @cp.autoheal_org(@owner['key'])
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
    @cp.autoheal_org(@owner['key'])
    system = consumer_client(@user, 'system6')

    status = system.consume_product(@monitoring.id, { :async => true })

    status['targetType'].should == "consumer"
    status['targetId'].should == system.uuid

    wait_for_job(status['id'], 15)
  end

  it 'should allow admin to view any job status' do
    job = @cp.autoheal_org(@owner['key'])
    wait_for_job(job['id'], 15)
    status = @cp.get_job(job['id'])
    status['id'].should == job['id']
  end

  it 'should allow user to view status of own job' do
    job = @user.autoheal_org(@owner['key'])
    wait_for_job(job['id'], 15)
    status = @user.get_job(job['id'])
    status['id'].should == job['id']
  end

  it 'should allow user to view job status of consumer in managed org' do
    system = consumer_client(@user, 's1')
    job = system.consume_product(@monitoring.id, { :async => true })
    status = @user.get_job(job['id'])
    status['id'].should == job['id']
  end

  it 'should not allow user to view job status outside of managed org' do
    other_user = user_client(@owner2, random_string("other_user"))
    system = consumer_client(other_user, random_string("another_system"))
    job = system.consume_product(@monitoring.id, { :async => true })

    lambda do
      @user.get_job(job['id'])
    end.should raise_exception(RestClient::Forbidden)

  end

  it 'should allow consumer to view status of own job' do
    system = consumer_client(@user, 'system7')
    job = system.consume_product(@monitoring.id, { :async => true })
    status = system.get_job(job['id'])
    status['id'].should == job['id']
  end

  it 'should not allow consumer to access another consumers job status' do
    system1 = consumer_client(@user, 's1')
    system2 = consumer_client(@user, 's2')

    job = system1.consume_product(@monitoring.id, { :async => true })
    status = system1.get_job(job['id'])

    lambda do
      system2.get_job(job['id'])
    end.should raise_exception(RestClient::Forbidden)
  end

end

