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

  it 'should find an empty list if the owner key is wrong' do
    @cp.list_jobs('totaly_made_up').should be_empty
  end

  it 'should cancel a job' do
    begin
      @cp.set_scheduler_status(false)

      job = @cp.autoheal_org(@owner['key'])
      #make sure we see a job waiting to go
      joblist = @cp.list_jobs(@owner['key'])
      joblist.find { |j| j['id'] == job['id'] }['state'].should == 'QUEUED'

      @cp.cancel_job(job['id'])
      #make sure we see a job canceled
      joblist = @cp.list_jobs(@owner['key'])
      joblist.find { |j| j['id'] == job['id'] }['state'].should == 'CANCELED'

      @cp.set_scheduler_status(true)
      sleep 1 #let the job queue drain..
      #make sure job didn't flip to FINISHED
      joblist = @cp.list_jobs(@owner['key'])
      joblist.find { |j| j['id'] == job['id'] }['state'].should == 'CANCELED'
    ensure
      @cp.set_scheduler_status(true)
    end
  end

  it 'should contain the system id for async binds' do
    @cp.autoheal_org(@owner['key'])
    system = consumer_client(@user, 'system6')

    status = system.consume_product(@monitoring.id, { :async => true })

    status['principal'].should == system.uuid

    wait_for_job(status['id'], 15)
  end

  it 'should allow admin to view any job status' do
    job = @cp.autoheal_org(@owner['key'])
    wait_for_job(job['id'], 15)
    status = @cp.get_job(job['id'])
    status['id'].should == job['id']
  end

  it 'should successfully run jobs concurrently' do
    total_threads = 6
    t_count = 0
    threads = []
    jobs = []

    # First create as many owners as the total threads we have
    owners = []
    total_threads.times do
      owners << create_owner(random_string("test_owner"))
    end

    # For each owner create a Thread which refreshes that owner, and saves the job status
    owners.each { |owner|
      t = Thread.new do
          jobs << @cp.autoheal_org(owner['key'])
        t_count = t_count + 1
      end
      threads << t
    }

    # Run all the threads
    threads.each(&:join)
    t_count.should == total_threads

    # Check that all jobs finished successfully
    jobs.each { |job|
        wait_for_job(job['id'], 30)
        status = @cp.get_job(job['id'])
        status['state'].should == 'FINISHED'
    }
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
    # wait for job to complete, or test clean up will conflict with the asynchronous job.
    wait_for_job(status['id'], 15)
  end

  it 'should not allow user to cancel job from another user' do
    other_user = user_client(@owner,  random_string("other_user"))
    job = @user.autoheal_org(@owner['key'])
    lambda do
      other_user.cancel_job(job['id'])
    end.should raise_exception(RestClient::Forbidden)
  end

  it 'should allow user to cancel a job it initiated' do
    begin
      @cp.set_scheduler_status(false)

      job = @user.autoheal_org(@owner['key'])
      #make sure we see a job waiting to go
      joblist = @cp.list_jobs(@owner['key'])
      expect(joblist.find { |j| j['id'] == job['id'] }['state']).to eq('QUEUED')

      @user.cancel_job(job['id'])
      #make sure we see a job canceled
      joblist = @cp.list_jobs(@owner['key'])
      expect(joblist.find { |j| j['id'] == job['id'] }['state']).to eq('CANCELED')

      @cp.set_scheduler_status(true)
      sleep 1 #let the job queue drain..
      #make sure job didn't flip to FINISHED
      joblist = @cp.list_jobs(@owner['key'])
      expect(joblist.find { |j| j['id'] == job['id'] }['state']).to eq('CANCELED')
    ensure
      @cp.set_scheduler_status(true)
    end
  end

  it 'should not allow user to cancel a job it did not initiate' do
    system = consumer_client(@user, 'system7')
    job = system.consume_product(@monitoring.id, { :async => true })
    status = system.get_job(job['id'])
    lambda do
      @user.cancel_job(job['id'])
    end.should raise_exception(RestClient::Forbidden)
  end

  it 'should not allow user to view job status outside of managed org' do
    other_user = user_client(@owner2, random_string("other_user"))
    system = consumer_client(other_user, random_string("another_system"))
    job = system.consume_product(@monitoring.id, { :async => true })
    # wait for job to complete, or test clean up will conflict with the asynchronous job.
    wait_for_job(job['id'], 15)
    lambda do
      @user.get_job(job['id'])
    end.should raise_exception(RestClient::Forbidden)
  end

  it 'should allow consumer to view status of own job' do
    system = consumer_client(@user, 'system7')
    job = system.consume_product(@monitoring.id, { :async => true })
    status = system.get_job(job['id'])
    status['id'].should eq(job['id'])
    # wait for job to complete, or test clean up will conflict with the asynchronous job.
    wait_for_job(status['id'], 15)
  end

  it 'should not allow consumer to access another consumers job status' do
    system1 = consumer_client(@user, 's1')
    system2 = consumer_client(@user, 's2')

    job = system1.consume_product(@monitoring.id, { :async => true })
    status = system1.get_job(job['id'])
    # wait for job to complete, or test clean up will conflict with the asynchronous job.
    wait_for_job(status['id'], 15)

    lambda do
      system2.get_job(job['id'])
    end.should raise_exception(RestClient::Forbidden)
  end

  it 'should allow consumer to cancel own job' do
    begin
      @cp.set_scheduler_status(false)

      system = consumer_client(@user, 'system7', :system,  nil,  {}, @owner['key'])
      job = system.consume_product(@monitoring.id, { :async => true })
      status = system.get_job(job['id'])
      system.cancel_job(job['id'])
      # wait for job to complete, or test clean up will conflict with the asynchronous job.
      wait_for_job(status['id'], 15)
    ensure
      @cp.set_scheduler_status(true)
    end
  end

  it 'should fail to cancel terminal job' do
    system = consumer_client(@user, 'system7', :system,  nil,  {}, @owner['key'])
    job = system.consume_product(@monitoring.id, { :async => true })
    status = system.get_job(job['id'])
    wait_for_job(status['id'], 15)

    lambda do
      system.cancel_job(job['id'])
    end.should raise_exception(RestClient::BadRequest)
  end

  it 'should not allow consumer to cancel another consumers job' do
    system1 = consumer_client(@user, 's1')
    system2 = consumer_client(@user, 's2')

    job = system1.consume_product(@monitoring.id, { :async => true })
    status = system1.get_job(job['id'])
    lambda do
      system2.cancel_job(job['id'])
    end.should raise_exception(RestClient::Forbidden)
  end


end

