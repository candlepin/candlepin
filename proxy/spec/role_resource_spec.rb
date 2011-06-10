require 'candlepin_scenarios'

describe 'Role Resource' do

  include CandlepinMethods
  include CandlepinScenarios

  before(:each) do 
    test_owner_key = random_string('testowner')
    @test_owner = create_owner(test_owner_key)
    @username = random_string 'user' 
    @user_cp = user_client(@test_owner, @username)

  end

  it 'should create roles' do
    orig_count = @cp.list_roles().size

    new_role = create_role(nil, @test_owner['key'], 'ALL')

    @cp.list_roles().size.should == orig_count + 1
  end



  it 'should delete roles' do
    orig_count = @cp.list_roles().size

    perms = [{
      :owner => {:key => @test_owner['key']},
      :access => 'ALL',
    }]
    new_role = @cp.create_role(random_string('testrole'), perms)

    @cp.list_roles().size.should == orig_count + 1
    @cp.delete_role(new_role['id'])
    @cp.list_roles().size.should == orig_count
  end

  it 'should add users to a role' do
    role = create_role(nil, @test_owner['key'], 'ALL')
    role['users'].size.should == 0

    role = @cp.add_role_user(role['id'], @username)
    role['users'].size.should == 1

    role = @cp.get_role(role['id'])
    role['users'].size.should == 1
  end

  it 'should add a new permission to a role, then delete the original permission' do

    perms = [{
      :owner => {:key => @test_owner['key']},
      :access => 'ALL',
    }]
    new_role = @cp.create_role(random_string('testrole'), perms)
    role_perm = new_role.permissions[0]

    perm = {
      :owner => @test_owner['key'],
      :access => 'READ_ONLY',
    }
    @cp.add_role_permission(new_role['id'], perm)
    role = @cp.get_role(new_role['id'])
    role.permissions.size.should == 2

    @cp.delete_role_permission(role['id'], role_perm['id'])
    role = @cp.get_role(new_role['id'])
    role.permissions.size.should == 1

    @cp.delete_role(new_role['id'])
  end
 
end



