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

  it 'can be searched by environment name' do
    another_env = @org_admin.create_environment(@owner['key'], 'testenv2',
      "Another Env")
    envs = @org_admin.list_environments(@owner['key'], "Another Env")
    envs.size.should == 1
    envs[0]['id'].should == 'testenv2'
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

  it 'filters content not promoted to environment' do
    consumer = @org_admin.register(random_string('testsystem'), :system, nil, {},
        nil, nil, [], [], @env['id'])
    consumer['environment'].should_not be_nil
    consumer_cp = Candlepin.new(nil, nil, consumer['idCert']['cert'],
      consumer['idCert']['key'])

    product = create_product
    content = create_content # promoted
    content2 = create_content # not promoted
    @cp.add_content_to_product(product['id'], content['id'])
    @cp.add_content_to_product(product['id'], content2['id'])

    @org_admin.promote_content(@env['id'], content['id'])

    @cp.create_subscription(@owner['key'], product['id'], 10)
    @cp.refresh_pools(@owner['key'])

    pools = @cp.list_pools(:owner => @owner['id'], :product => product['id'])
    ent = consumer_cp.consume_pool(pools[0]['id'])[0]

    x509 = OpenSSL::X509::Certificate.new(ent['certificates'][0]['cert'])
    extensions_hash = Hash[x509.extensions.collect { |ext| [ext.oid, ext.value] }]
    extensions_hash.has_key?("1.3.6.1.4.1.2312.9.2.#{content2['id']}.1").should_not be_true
    extensions_hash.has_key?("1.3.6.1.4.1.2312.9.2.#{content['id']}.1").should be_true
  end

end
