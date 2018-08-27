require 'spec_helper'
require 'candlepin_scenarios'

describe 'Role Resource' do

  include CandlepinMethods

  before(:each) do
    test_owner_key = random_string('testowner')
    @test_owner = create_owner(test_owner_key)
    @username = random_string 'user'
    @user_cp = user_client(@test_owner, @username)
  end

  it 'should create roles' do
    role_name = random_string("created_role")
    create_role(role_name, @test_owner['key'], 'ALL')
    @cp.list_roles().map { |i| i['name'] }.should include(role_name)
  end

  it 'should not allow org admins to list all roles' do
      lambda do
        @user_cp.list_roles()
      end.should raise_exception(RestClient::Forbidden)
  end

  it 'should update just the name' do
    role_name = random_string("test_role")
    updated_name = "#{role_name}-updated"

    new_role = create_role(role_name, @test_owner['key'], 'ALL')
    new_role['name'] = updated_name
    # perm = {
    #   :type => 'OWNER',
    #   :owner => {:key => @test_owner['key']},
    #   :access => 'READ_ONLY',
    # }
    # new_role.permissions[0] = perm

    @cp.update_role(role_name, new_role)
    updatedrole = @cp.get_role(new_role['name'])
    expect(updatedrole['name']).to eq(updated_name)
    # updatedrole.permissions[0].access.should == 'ALL'

    # TODO: Figure out why whoever wrote this test expected it to not update the role's permissions after
    # providing a new permission during the update.
  end

  it 'should delete roles' do
    perms = [{
      :type => 'OWNER',
      :owner => {:key => @test_owner['key']},
      :access => 'ALL',
    }]
    role_name = random_string("role_to_delete")
    new_role = @cp.create_role(role_name, perms)
    @cp.list_roles().map { |i| i['name'] }.should include(role_name)
    @cp.delete_role(new_role['name'])
    @cp.list_roles().map { |i| i['name'] }.should_not include(role_name)
  end

  it 'should add users to a role, then delete user from the role' do
    role = create_role(nil, @test_owner['key'], 'ALL')
    role['users'].size.should == 0

    role = @cp.add_role_user(role['name'], @username)
    role['users'].size.should == 1

    role = @cp.get_role(role['name'])
    role['users'].size.should == 1

    @cp.delete_role_user(role['name'], @username)
    next_role = @cp.get_role(role['name'])
    next_role['users'].size.should == 0
  end

  it 'should add a new permission to a role, then delete the original permission' do
    perms = [{
      :type => 'OWNER',
      :owner => {:key => @test_owner['key']},
      :access => 'ALL',
    }]

    new_role = @cp.create_role(random_string('testrole'), perms)
    role_perm = new_role.permissions[0]

    perm = {
      :type => 'OWNER',
      :owner => {:key => @test_owner['key']},
      :access => 'READ_ONLY',
    }

    @cp.add_role_permission(new_role['name'], perm)
    role = @cp.get_role(new_role['name'])
    role.permissions.size.should == 2

    @cp.delete_role_permission(role['name'], role_perm['id'])
    role = @cp.get_role(new_role['name'])
    role.permissions.size.should == 1
    role.permissions[0].access.should == 'READ_ONLY'

    @cp.delete_role(new_role['name'])
  end

end



