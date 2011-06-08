require 'candlepin_scenarios'

describe 'Role Resource' do

  include CandlepinMethods
  include CandlepinScenarios

  before(:each) do 
    test_owner_key = random_string('testowner')
    @test_owner = create_owner(test_owner_key)
    @username = random_string 'user' 
    @user_cp = user_client(@test_owner, @username)

    @role = create_role(nil, @test_owner['key'], 'ALL')

  end

  it 'should create roles' do
    orig_count = @cp.list_roles().size

    new_role = create_role(nil, @test_owner['key'], 'ALL')

    @cp.list_roles().size.should == orig_count + 1
  end



  it 'should delete roles' do
    orig_count = @cp.list_roles().size

    new_role = create_role(nil, @test_owner['key'], 'ALL')

    @cp.list_roles().size.should == orig_count + 1
    @cp.delete_role(new_role['id'])
    @cp.list_roles().size.should == orig_count
  end

  it 'should add users to a role' do
  end
 
#  it 'should add permissions to existing roles' do
#    # TODO: failing right now, PUT implementation not yet working.
#    role = create_role(nil, [])
#    new_role = create_role(nil, @test_owner['key'], 'ALL')
#    role['permissions'].size.should == 0
#    role['permissions'] << {
#      :owner => {:key => @test_owner['key']},
#      :access => 'ALL',
#    }
#    role = @cp.update_role(role)
#    role['permissions'].size.should == 1
#  end


end



