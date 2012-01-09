require 'candlepin_scenarios'

describe 'Environments' do
  include CandlepinMethods
  include CandlepinScenarios

  before(:each) do
    @owner = create_owner random_string('test_owner')
    @org_admin = user_client(@owner, 'guy')
    @env = @org_admin.create_environment(@owner['key'], 'testenv1',
      "My Test Env 1")
  end

  it 'can be created by owner admin' do
    @env['id'].should == 'testenv1'
    @env['owner']['key'].should == @owner['key']
    @org_admin.list_environments(@owner['key']).length.should == 1
  end

  it 'can be deleted by owner admin' do
    @org_admin.delete_environment(@env['id'])
    @org_admin.list_environments(@owner['key']).length.should == 0
  end

  it 'cannot be created by foreign owner admin' do
    foreign_owner = create_owner(random_string('test_owner'))
    foreign_admin = user_client(foreign_owner, 'bill')
    lambda {
      env = foreign_admin.create_environment(@owner['key'], 'testenv2',
        "My test env 2")
    }.should raise_exception(RestClient::Forbidden)
  end

  it 'cannot be accessed by foreign owner admin' do
    foreign_owner = create_owner(random_string('test_owner'))
    foreign_admin = user_client(foreign_owner, 'bill')

    lambda {
      foreign_admin.list_environments(@owner['key'])
    }.should raise_exception(RestClient::Forbidden)

    lambda {
      foreign_admin.get_environment(@env['id'])
    }.should raise_exception(RestClient::Forbidden)

    lambda {
      foreign_admin.delete_environment(@env['id'])
    }.should raise_exception(RestClient::Forbidden)

    lambda {
      content = create_content
      foreign_admin.promote_content(@env['id'], content['id'])
    }.should raise_exception(RestClient::Forbidden)
  end

  it 'can have promoted content' do
    content = create_content
    @org_admin.promote_content(@env['id'], content['id'])
    @env = @org_admin.get_environment(@env['id'])
    @env['environmentContent'].size.should == 1
  end

  it 'can demote content' do
    content = create_content
    @org_admin.promote_content(@env['id'], content['id'])
    @org_admin.demote_content(@env['id'], content['id'])
    @env = @org_admin.get_environment(@env['id'])
    @env['environmentContent'].size.should == 0
  end

end
