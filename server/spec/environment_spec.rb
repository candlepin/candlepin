require 'spec_helper'
require 'candlepin_scenarios'

describe 'Environments' do
  include CandlepinMethods

  before(:each) do
    @expected_env_id = random_string('testenv1')
    @owner = create_owner random_string('test_owner')
    @org_admin = user_client(@owner, random_string('guy'))
    @env = @org_admin.create_environment(@owner['key'], @expected_env_id,
      "My Test Env 1", "For test systems only.")
  end

  it 'can be created by owner admin' do
    @env['id'].should == @expected_env_id
    @env['owner']['key'].should == @owner['key']
    @org_admin.list_environments(@owner['key']).length.should == 1
  end

  it 'can be deleted by owner admin' do
    consumer = @org_admin.register(random_string('testsystem'), :system, nil, {},
        nil, nil, [], [], @env['id'])
    @org_admin.delete_environment(@env['id'])
    lambda {
      @org_admin.get_consumer(consumer['uuid'])
    }.should raise_exception(RestClient::Gone)
    @org_admin.list_environments(@owner['key']).length.should == 0
  end

  it 'cannot be created by foreign owner admin' do
    foreign_owner = create_owner(random_string('test_owner'))
    foreign_admin = user_client(foreign_owner, random_string('bill'))
    lambda {
      env = foreign_admin.create_environment(@owner['key'], 'testenv2',
        "My test env 2")
    }.should raise_exception(RestClient::ResourceNotFound)
  end

  it 'cannot be accessed by foreign owner admin' do
    foreign_owner = create_owner(random_string('test_owner'))
    foreign_admin = user_client(foreign_owner, random_string('bill'))

    lambda {
      foreign_admin.list_environments(@owner['key'])
    }.should raise_exception(RestClient::ResourceNotFound)

    lambda {
      foreign_admin.get_environment(@env['id'])
    }.should raise_exception(RestClient::ResourceNotFound)

    lambda {
      foreign_admin.delete_environment(@env['id'])
    }.should raise_exception(RestClient::ResourceNotFound)

    lambda {
      content = create_content
      foreign_admin.promote_content(@env['id'],
        [{:contentId => content['id']}])
    }.should raise_exception(RestClient::ResourceNotFound)
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
    @org_admin.promote_content(@env['id'],
        [{:contentId => content['id']}])
    @env = @org_admin.get_environment(@env['id'])
    @env['environmentContent'].size.should == 1
  end

  it 'cleans up env content when content is deleted' do
    content = create_content
    @org_admin.promote_content(@env['id'],
        [{:contentId => content['id']}])
    @env = @org_admin.get_environment(@env['id'])
    @env['environmentContent'].size.should == 1
    @cp.delete_content(content['id'])
    @env = @org_admin.get_environment(@env['id'])
    @env['environmentContent'].size.should == 0
  end

  it 'can demote content' do
    content = create_content
    content2 = create_content
    @org_admin.promote_content(@env['id'],
        [{:contentId => content['id']}])
    @org_admin.promote_content(@env['id'],
        [{:contentId => content2['id']}])
    @org_admin.demote_content(@env['id'], [content['id'], content2['id']])
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

    # Override enabled to false:
    @org_admin.promote_content(@env['id'],
        [{
          :contentId => content['id'],
          :enabled => false,
        }])

    @cp.create_subscription(@owner['key'], product['id'], 10)
    @cp.refresh_pools(@owner['key'])

    pools = consumer_cp.list_pools(:owner => @owner['id'], :product => product['id'])
    ent = consumer_cp.consume_pool(pools[0]['id'], {:quantity => 1})[0]

    x509 = OpenSSL::X509::Certificate.new(ent['certificates'][0]['cert'])
    extensions_hash = Hash[x509.extensions.collect { |ext| [ext.oid, ext.value] }]
    extensions_hash.has_key?("1.3.6.1.4.1.2312.9.2.#{content2['id']}.1").should_not be_true
    extensions_hash.has_key?("1.3.6.1.4.1.2312.9.2.#{content['id']}.1").should be_true

    # Make sure the enabled field was overridden to false:
    extension_from_cert(ent['certificates'][0]['cert'],
        "1.3.6.1.4.1.2312.9.2.#{content['id']}.1.8").should == "0"
  end

  it 'regenerates certificates after promoting/demoting content' do
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

    # Override enabled to false:
    @org_admin.promote_content(@env['id'],
        [{
          :contentId => content['id'],
        }])

    @cp.create_subscription(@owner['key'], product['id'], 10)
    @cp.refresh_pools(@owner['key'])

    pools = consumer_cp.list_pools(:owner => @owner['id'], :product => product['id'])
    ent = consumer_cp.consume_pool(pools[0]['id'], {:quantity => 1})[0]

    x509 = OpenSSL::X509::Certificate.new(ent['certificates'][0]['cert'])
    extensions_hash = Hash[x509.extensions.collect { |ext| [ext.oid, ext.value] }]
    extensions_hash.has_key?("1.3.6.1.4.1.2312.9.2.#{content['id']}.1").should be_true
    extensions_hash.has_key?("1.3.6.1.4.1.2312.9.2.#{content2['id']}.1").should_not be_true
    serial = ent['certificates'][0]['serial']['serial']

    # Promote the other content set and make sure certs were regenerated:
    @org_admin.promote_content(@env['id'],
        [{
          :contentId => content2['id'],
        }])
    sleep 1
    ent = consumer_cp.list_entitlements()[0]
    x509 = OpenSSL::X509::Certificate.new(ent['certificates'][0]['cert'])
    extensions_hash = Hash[x509.extensions.collect { |ext| [ext.oid, ext.value] }]
    extensions_hash.has_key?("1.3.6.1.4.1.2312.9.2.#{content['id']}.1").should be_true
    extensions_hash.has_key?("1.3.6.1.4.1.2312.9.2.#{content2['id']}.1").should be_true
    new_serial = ent['certificates'][0]['serial']['serial']
    new_serial.should_not == serial

    # Demote it and check again:
    @org_admin.demote_content(@env['id'], [content2['id']])
    sleep 1
    ent = consumer_cp.list_entitlements()[0]
    x509 = OpenSSL::X509::Certificate.new(ent['certificates'][0]['cert'])
    extensions_hash = Hash[x509.extensions.collect { |ext| [ext.oid, ext.value] }]
    extensions_hash.has_key?("1.3.6.1.4.1.2312.9.2.#{content['id']}.1").should be_true
    extensions_hash.has_key?("1.3.6.1.4.1.2312.9.2.#{content2['id']}.1").should_not be_true
    another_serial = ent['certificates'][0]['serial']['serial']
    another_serial.should_not == new_serial
  end

end
