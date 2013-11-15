require 'spec_helper'
require 'candlepin_scenarios'

describe 'System admins' do
  include CandlepinMethods

  before(:each) do
    @owner = create_owner(random_string('test'))
    perms = [{
      :type => 'USERNAME_CONSUMERS',
      :owner => {:key => @owner['key']},
      :access => 'ALL',
    }]
    @username = random_string 'user'
    @user_cp = user_client(@owner, @username)
    sysadmin_role = @cp.create_role(random_string('testrole'), perms)
    @cp.add_role_user(sysadmin_role['id'], @username)

    # Registered by our system admin:
    @consumer1 = @user_cp.register("someconsumer", :system, nil, {},
      @username, @owner['key'])

    # Registered by somebody else:
    @consumer2 = @cp.register("someconsumer2", :system, nil, {},
      'admin', @owner['key'])
  end

  it 'can only see their systems' do
    # Admin should see both systems in the org:
    @cp.list_consumers({:owner => @owner['key']}).size.should == 2

    # User should just see one:
    my_systems = @user_cp.list_consumers({:owner => @owner['key']})
    my_systems.size.should == 1
    my_systems[0]['uuid'].should == @consumer1['uuid']

    lambda do
      @user_cp.get_consumer(@consumer2['uuid'])
    end.should raise_exception(RestClient::ResourceNotFound)
  end

  it 'can list pools for only their systems' do
    pools = @user_cp.list_pools({:consumer => @consumer1['uuid']})
    lambda do
      @user_cp.list_pools({:consumer => @consumer2['uuid']})
    end.should raise_exception(RestClient::ResourceNotFound)
  end

  it 'can unregister only their systems' do
    # Should succeed:
    @user_cp.unregister(@consumer1['uuid'])

    lambda do
      @user_cp.unregister(@consumer2['uuid'])
    end.should raise_exception(RestClient::ResourceNotFound)
  end
end

# Testing users who can manage systems they registered, as well as view all other systems:
describe 'Read-only system admins' do
  include CandlepinMethods

  before(:each) do
    @owner = create_owner(random_string('test'))
    perms = [
      {
        :type => 'USERNAME_CONSUMERS',
        :owner => {:key => @owner['key']},
        :access => 'ALL'
      },
      {
        :type => 'OWNER',
        :owner => {:key => @owner['key']},
        :access => 'READ_ONLY'
      },
    ]
    @username = random_string 'user'
    @user_cp = user_client(@owner, @username)
    sysadmin_role = @cp.create_role(random_string('testrole'), perms)
    @cp.add_role_user(sysadmin_role['id'], @username)

    # Registered by our system admin:
    @consumer1 = @user_cp.register("someconsumer", :system, nil, {},
      @username, @owner['key'])

    # Registered by somebody else:
    @consumer2 = @cp.register("someconsumer2", :system, nil, {},
      'admin', @owner['key'])
  end

  it 'can see all systems in org' do
    # Admin should see both systems in the org:
    @cp.list_consumers({:owner => @owner['key']}).size.should == 2

    # User should just see one:
    my_systems = @user_cp.list_consumers({:owner => @owner['key']})
    my_systems.size.should == 2

    lookedup = @user_cp.get_consumer(@consumer2['uuid'])
    lookedup['uuid'].should == @consumer2['uuid']
  end

  it 'can list pools for all systems in org' do
    pools = @user_cp.list_pools({:consumer => @consumer1['uuid']})
    lambda do
      @user_cp.list_pools({:consumer => @consumer1['uuid']})
    end.should raise_exception(RestClient::ResourceNotFound)
  end


  it 'can only unregister their systems' do
    # Should succeed:
    @user_cp.unregister(@consumer1['uuid'])

    lambda do
      @user_cp.unregister(@consumer2['uuid'])
    end.should raise_exception(RestClient::Forbidden)
  end
end
