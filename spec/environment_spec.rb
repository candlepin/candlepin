require 'spec_helper'
require 'candlepin_scenarios'

def promote_content(content, env)
  job = @cp.promote_content(env['id'], [{ :contentId => content['id'] }])
  wait_for_job(job['id'], 15)
end

def demote_content(content, env)
  job = @cp.demote_content(env['id'], [ content['id'] ])
  wait_for_job(job['id'], 15)
end

describe 'Environments' do
  include CandlepinMethods
  include CertificateMethods

  before(:each) do
    @expected_env_id = random_string('testenv1')
    @owner = create_owner random_string('test_owner')
    @org_admin = user_client(@owner, random_string('guy'))
    @env = @org_admin.create_environment(@owner['key'], @expected_env_id, "My Test Env 1", "For test systems only.")
  end

  it 'can be created by owner admin' do
    @env['id'].should == @expected_env_id
    @env['owner']['key'].should == @owner['key']
    @org_admin.list_environments(@owner['key']).length.should == 1
  end

  it 'can be deleted by owner admin' do
    @org_admin.delete_environment(@env['id'])

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
      foreign_admin.promote_content(@env['id'], [{:contentId => content['id']}])
    }.should raise_exception(RestClient::ResourceNotFound)
  end

  it 'can be searched by environment name' do
    another_env = @org_admin.create_environment(@owner['key'], 'testenv2',
      "Another Env")
    envs = @org_admin.list_environments(@owner['key'], "Another Env")
    envs.size.should == 1
    envs[0]['id'].should == 'testenv2'
  end

  it 'sets content.enabled to nil by default' do
    content = create_content
    job = @org_admin.promote_content(@env['id'], [{:contentId => content['id']}])
    wait_for_job(job['id'], 15)
    @env = @org_admin.get_environment(@env['id'])
    @env['environmentContent'][0]['enabled'].should == nil
  end

  it 'can have enabled content' do
    content = create_content
    job = @org_admin.promote_content(@env['id'], [{:contentId => content['id'],:enabled => true}])
    wait_for_job(job['id'], 15)
    @env = @org_admin.get_environment(@env['id'])
    @env['environmentContent'][0]['enabled'].should == true
  end

  it 'can have disabled content' do
    content = create_content
    job = @org_admin.promote_content(@env['id'], [{:contentId => content['id'],:enabled => false}])
    wait_for_job(job['id'], 15)
    @env = @org_admin.get_environment(@env['id'])
    @env['environmentContent'][0]['enabled'].should == false
  end

  it 'can have promoted content' do
    content = create_content
    job = @org_admin.promote_content(@env['id'], [{:contentId => content['id']}])
    wait_for_job(job['id'], 15)
    @env = @org_admin.get_environment(@env['id'])
    @env['environmentContent'].size.should == 1
  end

  it 'does not allow content to be promoted more than once' do
    content1 = create_content
    content2 = create_content

    job = @org_admin.promote_content(@env['id'], [{:contentId => content1['id']}])
    wait_for_job(job['id'], 15)

    lambda do
      @org_admin.promote_content(
        @env['id'], [{:contentId => content2['id']}, {:contentId => content1['id']}]
      )
    end.should raise_exception(RestClient::Conflict)

    # The promotion of content2 should have been aborted due to the conflict with content1
    @env = @org_admin.get_environment(@env['id'])
    @env['environmentContent'].size.should == 1
  end

  it 'can promote multiple contents' do
    content = create_content
    job = @org_admin.promote_content(@env['id'], [{:contentId => content['id']}])
    wait_for_job(job['id'], 15)

    content2 = create_content
    job = @org_admin.promote_content(@env['id'], [{:contentId => content2['id']}])
    wait_for_job(job['id'], 15)

    content3 = create_content
    content4 = create_content
    job = @org_admin.promote_content(
      @env['id'], [{:contentId => content3['id']}, {:contentId => content4['id']}]
    )
    wait_for_job(job['id'], 15)

    @env = @org_admin.get_environment(@env['id'])
    @env['environmentContent'].size.should == 4
  end

  it 'cleans up env content when content is deleted' do
    content1 = create_content
    content2 = create_content
    job = @org_admin.promote_content(
      @env['id'], [{:contentId => content1['id']}, {:contentId => content2['id']}]
    )

    wait_for_job(job['id'], 15)

    @env = @org_admin.get_environment(@env['id'])
    @env['environmentContent'].size.should == 2

    @cp.delete_content(@owner['key'], content1['id'])
    @env = @org_admin.get_environment(@env['id'])
    @env['environmentContent'].size.should == 1

    @cp.delete_content(@owner['key'], content2['id'])
    @env = @org_admin.get_environment(@env['id'])
    @env['environmentContent'].size.should == 0
  end

  it 'can demote content' do
    content = create_content
    content2 = create_content
    job = @org_admin.promote_content(@env['id'], [{:contentId => content['id']}])
    wait_for_job(job['id'], 15)
    job = @org_admin.promote_content(@env['id'], [{:contentId => content2['id']}])
    wait_for_job(job['id'], 15)
    job = @org_admin.demote_content(@env['id'], [content['id'], content2['id']])
    wait_for_job(job['id'], 15)
    @env = @org_admin.get_environment(@env['id'])
    @env['environmentContent'].size.should == 0
  end

  it 'gracefully aborts on invalid content during demotion' do
    content = create_content
    content2 = create_content
    job = @org_admin.promote_content(@env['id'], [{:contentId => content['id']}])
    wait_for_job(job['id'], 15)
    job = @org_admin.promote_content(@env['id'], [{:contentId => content2['id']}])
    wait_for_job(job['id'], 15)

    lambda do
      @org_admin.demote_content(@env['id'], ['bad_content_id'])
    end.should raise_exception(RestClient::ResourceNotFound)
    @env = @org_admin.get_environment(@env['id'])
    @env['environmentContent'].size.should == 2

    lambda do
      @org_admin.demote_content(@env['id'], [content['id'], 'bad_content_id', content2['id']])
    end.should raise_exception(RestClient::ResourceNotFound)
    @env = @org_admin.get_environment(@env['id'])
    @env['environmentContent'].size.should == 2

    lambda do
      @org_admin.demote_content(@env['id'], [content['id'], content2['id'], 'bad_content_id'])
    end.should raise_exception(RestClient::ResourceNotFound)
    @env = @org_admin.get_environment(@env['id'])
    @env['environmentContent'].size.should == 2

    job = @org_admin.demote_content(@env['id'], [content['id'], content2['id']])
    wait_for_job(job['id'], 15)
    @env = @org_admin.get_environment(@env['id'])
    @env['environmentContent'].size.should == 0
  end

  it 'filters content not promoted to environment' do
    consumer = @org_admin.register(random_string('testsystem'), :system, nil, {},
        nil, nil, [], [], [{ 'id' => @env['id']}])
    consumer_cp = Candlepin.new(nil, nil, consumer['idCert']['cert'],
      consumer['idCert']['key'])

    product = create_product
    content = create_content # promoted
    content2 = create_content # not promoted
    @cp.add_content_to_product(@owner['key'], product['id'], content['id'])
    @cp.add_content_to_product(@owner['key'], product['id'], content2['id'])

    # Override enabled to false:
    job = @org_admin.promote_content(@env['id'],
        [{
          :contentId => content['id'],
          :enabled => false,
        }])
    wait_for_job(job['id'], 15)

    pool = @cp.create_pool(@owner['key'], product['id'])
    ent = consumer_cp.consume_pool(pool['id'], {:quantity => 1})[0]

    x509 = OpenSSL::X509::Certificate.new(ent['certificates'][0]['cert'])
    extensions_hash = Hash[x509.extensions.collect { |ext| [ext.oid, ext.value] }]
    extensions_hash.has_key?("1.3.6.1.4.1.2312.9.2.#{content2['id']}.1").should_not be true
    extensions_hash.has_key?("1.3.6.1.4.1.2312.9.2.#{content['id']}.1").should be true

    # Make sure the enabled field was overridden to false:
    extension_from_cert(ent['certificates'][0]['cert'],
        "1.3.6.1.4.1.2312.9.2.#{content['id']}.1.8").should == "0"
  end

  it 'regenerates certificates after promoting/demoting content' do
    consumer = @org_admin.register(random_string('testsystem'), :system, nil, {}, nil, nil, [], [], [{ 'id' => @env['id']}])
    consumer_cp = Candlepin.new(nil, nil, consumer['idCert']['cert'], consumer['idCert']['key'])

    product = create_product
    content = create_content # promoted
    content2 = create_content # not promoted
    @cp.add_content_to_product(@owner['key'], product['id'], content['id'])
    @cp.add_content_to_product(@owner['key'], product['id'], content2['id'])

    # Override enabled to false:
    job = @org_admin.promote_content(@env['id'],
        [{
          :contentId => content['id'],
        }])
    wait_for_job(job['id'], 15)

    pool = @cp.create_pool(@owner['key'], product['id'])
    ent = consumer_cp.consume_pool(pool['id'], {:quantity => 1})[0]

    x509 = OpenSSL::X509::Certificate.new(ent['certificates'][0]['cert'])
    extensions_hash = Hash[x509.extensions.collect { |ext| [ext.oid, ext.value] }]
    extensions_hash.has_key?("1.3.6.1.4.1.2312.9.2.#{content['id']}.1").should be true
    extensions_hash.has_key?("1.3.6.1.4.1.2312.9.2.#{content2['id']}.1").should_not be true
    serial = ent['certificates'][0]['serial']['serial']

    # Promote the other content set and make sure certs were regenerated:
    job = @org_admin.promote_content(@env['id'],
        [{
          :contentId => content2['id'],
        }])
    wait_for_job(job['id'], 15)

    ent = consumer_cp.list_entitlements()[0]
    x509 = OpenSSL::X509::Certificate.new(ent['certificates'][0]['cert'])
    extensions_hash = Hash[x509.extensions.collect { |ext| [ext.oid, ext.value] }]
    extensions_hash.has_key?("1.3.6.1.4.1.2312.9.2.#{content['id']}.1").should be true
    extensions_hash.has_key?("1.3.6.1.4.1.2312.9.2.#{content2['id']}.1").should be true
    new_serial = ent['certificates'][0]['serial']['serial']
    new_serial.should_not == serial

    # Demote it and check again:
    job = @org_admin.demote_content(@env['id'], [content2['id']])
    wait_for_job(job['id'], 15)
    ent = consumer_cp.list_entitlements()[0]
    x509 = OpenSSL::X509::Certificate.new(ent['certificates'][0]['cert'])
    extensions_hash = Hash[x509.extensions.collect { |ext| [ext.oid, ext.value] }]
    extensions_hash.has_key?("1.3.6.1.4.1.2312.9.2.#{content['id']}.1").should be true
    extensions_hash.has_key?("1.3.6.1.4.1.2312.9.2.#{content2['id']}.1").should_not be true
    another_serial = ent['certificates'][0]['serial']['serial']
    another_serial.should_not == new_serial
  end

  it 'lists environments with populated entity collections' do
    content1 = create_content()
    content2 = create_content()

    # We'll use @env as env1 with nothing on it

    env2 = @cp.create_environment(@owner['key'], random_string("env2"), random_string("env2"))
    promote_content(content1, env2)
    promote_content(content2, env2)

    env3 = @cp.create_environment(@owner['key'], random_string("env3"), random_string("env3"))
    promote_content(content1, env3)
    promote_content(content2, env3)
    @cp.register(random_string("consumer1"), :system, nil, {}, random_string("consumer1"), @owner['key'], [], [], [{ 'id' => env3['id']}])
    @cp.register(random_string("consumer2"), :system, nil, {}, random_string("consumer2"), @owner['key'], [], [], [{ 'id' => env3['id']}])

    environments = @cp.list_environments(@owner['key'])
    expect(environments.length).to be >= 3

    # At the time of writing, we're only including environment content in the serialized output, so
    # that's all we can verify

    processed = []
    environments.each do |environment|
      if processed.include? environment['id']
        fail("duplicate environments received: #{environment['id']}")
      end

      case environment['id']
        when @env['id']
          expect(environment['environmentContent'].length).to eq(0)

        when env2['id']
          expect(environment['environmentContent'].length).to eq(2)

        when env3['id']
          expect(environment['environmentContent'].length).to eq(2)

        # else: unexpected (preexisting) environment, ignore it
      end

      processed.push(environment['id'])
    end
  end

  it 'should able to create consumer in multiple environment using legacy registration endpoint' do
    # create consumer for only one environment (backward compatible)
    @cp.create_environment(@owner['key'], "env_priority_1", "env_priority_1")
    consumer = @cp.create_consumer_in_environments("env_priority_1", random_string('consumer'))

    expect(consumer).not_to be_nil
    expect(consumer['environments']).not_to be_nil
    expect(consumer['environments'].length).to eq(1)
    expect(consumer['environments'][0].id).to eq("env_priority_1")
    expect(consumer['environment']).not_to be_nil
    expect(consumer['environment'].name).to eq("env_priority_1")

    # create consumer for multiple environment
    @cp.create_environment(@owner['key'], "env_priority_2", "env_priority_2")
    envIds = "env_priority_2, env_priority_1"
    consumer = @cp.create_consumer_in_environments(envIds, random_string('consumer'))

    expect(consumer).not_to be_nil
    expect(consumer['environments']).not_to be_nil
    expect(consumer['environments'].length).to eq(2)
    expect(consumer['environment']).not_to be_nil
    envNames = consumer['environment'].name.split(",")
    expect(envNames.length).to eq(2)

    # check the priority ordering for envNames
    expect(envNames[0]).to eq("env_priority_2")
    expect(envNames[1]).to eq("env_priority_1")

    # check the priority ordering for environments
    expect(consumer['environments'][0].id).to eq("env_priority_2")
    expect(consumer['environments'][1].id).to eq("env_priority_1")
  end

  it 'should fail to create consumer if environment does not exists when using legacy registration endpoint' do
    expect {
      @cp.create_consumer_in_environments("randomEnvIdThatDoesNotExists", random_string('consumer'))
    }.to raise_error(RestClient::ResourceNotFound)
  end


  it 'legacy registration endpoint should pass activation key auth' do
    no_auth_client = Candlepin.new
    prod1 = create_product(random_string('product1'), random_string('product1'),
                           :attributes => { :'multi-entitlement' => 'yes'})
    @cp.create_pool(@owner['key'], prod1.id, {:quantity => 10})
    pool1 = @cp.list_pools({:owner => @owner['id']}).first

    key1 = @cp.create_activation_key(@owner['key'], 'key1')
    @cp.add_pool_to_key(key1['id'], pool1['id'], 3)
    @cp.create_activation_key(@owner['key'], 'key2')
    @cp.create_environment(@owner['key'], "env_priority_1", "env_priority_1")
    consumer = no_auth_client.create_consumer_in_environments("env_priority_1", random_string('consumer'),
                                                              :system, @owner['key'], ["key1", "key2"])

    expect(consumer).not_to be_nil
    expect(consumer['environments']).not_to be_nil
    expect(consumer['environments'].length).to eq(1)
    expect(consumer['environments'][0].id).to eq("env_priority_1")
    expect(consumer['environment']).not_to be_nil
    expect(consumer['environment'].name).to eq("env_priority_1")
    @cp.get_pool(pool1.id).consumed.should == 3
  end

  it 'consumer should get content from multiple environments' do
    product = create_product
    content1 = new_content(1)
    content2 = new_content(2)
    content3 = new_content(3)

    @cp.add_content_to_product(@owner['key'], product['id'], content1['id'])
    @cp.add_content_to_product(@owner['key'], product['id'], content2['id'])
    @cp.add_content_to_product(@owner['key'], product['id'], content3['id'])

    env1 = @cp.create_environment(@owner['key'], random_string("env1"), random_string("env1"))
    env2 = @cp.create_environment(@owner['key'], random_string("env2"), random_string("env2"))
    env3 = @cp.create_environment(@owner['key'], random_string("env3"), random_string("env3"))
    promote_content(content1, env1)
    promote_content(content2, env2)
    promote_content(content3, env3)
    consumer = @cp.register(random_string("consumer1"), :system, nil,
       facts={'system.certificate_version' => '3.3', "uname.machine" => "x86_64"}, random_string("consumer1"),
       @owner['key'], [], [], [{ 'id' => env1['id']}, { 'id' => env2['id']}, { 'id' => env3['id']}])
    consumer_cp = Candlepin.new(nil, nil, consumer['idCert']['cert'], consumer['idCert']['key'])

    pool = @cp.create_pool(@owner['key'], product['id'])
    consumer_cp.consume_pool(pool['id'], {:quantity => 1})[0]
    certs = consumer_cp.list_certificates
    certs.length.should == 1

    json_body = extract_payload(certs[0]['cert'])
    json_body['products'].length.should == 1
    json_body['products'][0]['content'].size.should == 3

    urls = [content1['contentUrl'], content2['contentUrl'], content3['contentUrl']]
    json_body['products'][0]['content'].each { |content|
      urls.should include(content['path'])
    }

  end

  it 'content should use owner prefix' do
    @owner = create_owner(random_string("test_owner"), nil, { :contentPrefix => "/#{random_string('test_prefix')}" })
    product = create_product
    content1 = new_content(1)
    content2 = new_content(2)
    content3 = new_content(3)

    @cp.add_content_to_product(@owner['key'], product['id'], content1['id'])
    @cp.add_content_to_product(@owner['key'], product['id'], content2['id'])
    @cp.add_content_to_product(@owner['key'], product['id'], content3['id'])

    env1 = @cp.create_environment(@owner['key'], random_string("env1"), random_string("env1"))
    env2 = @cp.create_environment(@owner['key'], random_string("env2"), random_string("env2"))
    env3 = @cp.create_environment(@owner['key'], random_string("env3"), random_string("env3"))
    promote_content(content1, env1)
    promote_content(content2, env2)
    promote_content(content3, env3)
    consumer = @cp.register(random_string("consumer1"), :system, nil,
       facts={'system.certificate_version' => '3.3', "uname.machine" => "x86_64"}, random_string("consumer1"),
       @owner['key'], [], [], [{ 'id' => env1['id']}, { 'id' => env2['id']}, { 'id' => env3['id']}])
    consumer_cp = Candlepin.new(nil, nil, consumer['idCert']['cert'], consumer['idCert']['key'])

    pool = @cp.create_pool(@owner['key'], product['id'])
    consumer_cp.consume_pool(pool['id'], {:quantity => 1})[0]
    certs = consumer_cp.list_certificates
    certs.length.should == 1

    json_body = extract_payload(certs[0]['cert'])
    json_body['products'].length.should == 1
    json_body['products'][0]['content'].size.should == 3

    json_body['products'][0]['content'].each { |content|
      content['path'].should include(@owner.contentPrefix)
    }
  end

  it 'content should get prefix from environment' do
    @owner = create_owner(random_string("test_owner"), nil, { :contentPrefix => "/#{random_string('test_prefix')}/$env" })
    product = create_product
    content1 = new_content(1)
    content2 = new_content(2)
    content3 = new_content(3)

    @cp.add_content_to_product(@owner['key'], product['id'], content1['id'])
    @cp.add_content_to_product(@owner['key'], product['id'], content2['id'])
    @cp.add_content_to_product(@owner['key'], product['id'], content3['id'])

    env1 = @cp.create_environment(@owner['key'], random_string("env1"), random_string("test_env_1"))
    env2 = @cp.create_environment(@owner['key'], random_string("env2"), random_string("test_env_2"))
    env3 = @cp.create_environment(@owner['key'], random_string("env3"), random_string("test_env_3"))
    promote_content(content1, env1)
    promote_content(content2, env2)
    promote_content(content3, env3)
    consumer = @cp.register(random_string("consumer1"), :system, nil,
                            facts={'system.certificate_version' => '3.3', "uname.machine" => "x86_64"}, random_string("consumer1"),
                            @owner['key'], [], [], [{ 'id' => env1['id']}, { 'id' => env2['id']}, { 'id' => env3['id']}])
    consumer_cp = Candlepin.new(nil, nil, consumer['idCert']['cert'], consumer['idCert']['key'])

    pool = @cp.create_pool(@owner['key'], product['id'])
    consumer_cp.consume_pool(pool['id'], {:quantity => 1})[0]
    certs = consumer_cp.list_certificates
    certs.length.should == 1

    json_body = extract_payload(certs[0]['cert'])
    json_body['products'].length.should == 1
    json_body['products'][0]['content'].size.should == 3

    env_names = [env1.name, env2.name, env3.name]
    json_body['products'][0]['content'].each { |content|
      env_names.any? { |env_name| content['path'].include? env_name }
    }
  end

  it 'should deduplicate content from multiple environments' do
    @owner = create_owner(random_string("test_owner"), nil, { :contentPrefix => "/#{random_string('test_prefix')}/$env" })
    product = create_product
    content1 = new_content(1)
    content2 = new_content(2)
    content3 = new_content(3)

    @cp.add_content_to_product(@owner['key'], product['id'], content1['id'])
    @cp.add_content_to_product(@owner['key'], product['id'], content2['id'])
    @cp.add_content_to_product(@owner['key'], product['id'], content3['id'])

    env1 = @cp.create_environment(@owner['key'], random_string("env1"), random_string("test_env_1"))
    env2 = @cp.create_environment(@owner['key'], random_string("env2"), random_string("test_env_2"))
    env3 = @cp.create_environment(@owner['key'], random_string("env3"), random_string("test_env_3"))
    promote_content(content1, env1)
    promote_content(content1, env2)
    promote_content(content1, env3)
    promote_content(content2, env1)
    promote_content(content2, env2)
    promote_content(content2, env3)
    promote_content(content3, env3)
    consumer = @cp.register(random_string("consumer1"), :system, nil,
                            facts={'system.certificate_version' => '3.3', "uname.machine" => "x86_64"}, random_string("consumer1"),
                            @owner['key'], [], [], [{ 'id' => env1['id']}, { 'id' => env2['id']}, { 'id' => env3['id']}])
    consumer_cp = Candlepin.new(nil, nil, consumer['idCert']['cert'], consumer['idCert']['key'])

    pool = @cp.create_pool(@owner['key'], product['id'])
    consumer_cp.consume_pool(pool['id'], {:quantity => 1})[0]
    certs = consumer_cp.list_certificates
    certs.length.should == 1

    json_body = extract_payload(certs[0]['cert'])
    json_body['products'].length.should == 1
    json_body['products'][0]['content'].size.should == 3

    # Verify content prioritization
    json_body['products'][0]['content'].each { |content|
      if content['id'] == content1.id
        content['path'].should include(env1['name'])
      elsif content['id'] == content2.id
        content['path'].should include(env1['name'])
      else
        content['path'].should include(env3['name'])
      end
    }
  end

  it 'should delete consumer together with his last environment' do
    product = create_product
    content1 = new_content(1)
    content2 = new_content(2)

    @cp.add_content_to_product(@owner['key'], product['id'], content1['id'])
    @cp.add_content_to_product(@owner['key'], product['id'], content2['id'])

    env1 = @cp.create_environment(@owner['key'], random_string("env1"), random_string("test_env_1"))
    env2 = @cp.create_environment(@owner['key'], random_string("env2"), random_string("test_env_2"))
    promote_content(content1, env1)
    promote_content(content2, env1)
    promote_content(content2, env2)
    consumer1 = @cp.register(random_string("consumer1"), :system, nil,
                            facts={'system.certificate_version' => '3.3', "uname.machine" => "x86_64"}, random_string("consumer1"),
                            @owner['key'], [], [], [{ 'id' => env1['id']}])
    consumer2 = @cp.register(random_string("consumer2"), :system, nil,
                            facts={'system.certificate_version' => '3.3', "uname.machine" => "x86_64"}, random_string("consumer2"),
                            @owner['key'], [], [], [{ 'id' => env1['id']}, { 'id' => env2['id']}])

    @cp.delete_environment(env1['id'])

    lambda {
      @cp.get_consumer(consumer1['uuid'])
    }.should raise_exception(RestClient::Gone)

    cons = @cp.get_consumer(consumer2['uuid'])
    cons['environments'].length.should == 1
  end

  it 'should regenerate only entitlements affected by a deleted environment' do
    product1 = create_product
    product2 = create_product
    content1 = new_content(1)
    content2 = new_content(2)

    @cp.add_content_to_product(@owner['key'], product1['id'], content1['id'])
    @cp.add_content_to_product(@owner['key'], product2['id'], content2['id'])

    env1 = @cp.create_environment(@owner['key'], random_string("env1"), random_string("test_env_1"))
    env2 = @cp.create_environment(@owner['key'], random_string("env2"), random_string("test_env_2"))
    promote_content(content1, env1)
    promote_content(content2, env2)
    consumer = @cp.register(random_string("consumer2"), :system, nil,
                            facts={'system.certificate_version' => '3.3', "uname.machine" => "x86_64"}, random_string("consumer2"),
                            @owner['key'], [], [], [{ 'id' => env1['id']}, { 'id' => env2['id']}])
    consumer_cp = Candlepin.new(nil, nil, consumer['idCert']['cert'], consumer['idCert']['key'])

    pool1 = @cp.create_pool(@owner['key'], product1['id'])
    pool2 = @cp.create_pool(@owner['key'], product2['id'])
    ent1 = consumer_cp.consume_pool(pool1['id'], { :quantity => 1 })[0]
    ent2 = consumer_cp.consume_pool(pool2['id'], {:quantity => 1})[0]
    pool_1_serial = ent1['certificates'][0]['serial']['id']
    pool_2_serial = ent2['certificates'][0]['serial']['id']

    certs = consumer_cp.list_certificates
    certs.length.should == 2

    @cp.delete_environment(env1['id'])

    current_serials = consumer_cp.list_entitlements.map { |ent| ent['certificates'][0]['serial']['id']}
    current_serials.should_not include(pool_1_serial)
    current_serials.should include(pool_2_serial)

    certs = consumer_cp.list_certificates
    certs.length.should == 2
    certs.each { |cert|
      json_body = extract_payload(cert['cert'])
      json_body['products'].length.should == 1
      content = json_body['products'][0]['content']
      if cert['serial']['id'] == pool_2_serial
        content.length.should == 1
      else
        content.length.should == 0
      end
    }
  end

  def new_content(id)
    return @cp.create_content(
      @owner['key'], "cname#{id}", "test-content#{id}", random_string("clabel#{id}"), "ctype#{id}", "cvendor#{id}",
      {:content_url=> "/this/is/the/path/#{id}", :arches => "x86_64"}, true)
  end

  it 'should re-gen consumer entitlement cert when higher priority environment is added where provided
    content is NOT same as higher priority environment' do
    consumer = @org_admin.register(random_string('testsystem'), :system, nil, {}, nil, nil, [], [],
      [{ 'id' => @env['id']}])

    expect(consumer['environments']).not_to be_nil

    consumer_cp = Candlepin.new(nil, nil, consumer['idCert']['cert'], consumer['idCert']['key'])
    product = create_product
    contentA = create_content
    contentB = create_content
    contentC = create_content
    @cp.add_content_to_product(@owner['key'], product['id'], contentA['id'])
    @cp.add_content_to_product(@owner['key'], product['id'], contentB['id'])
    @cp.add_content_to_product(@owner['key'], product['id'], contentC['id'])
    job = @org_admin.promote_content(@env['id'], [{:contentId => contentB['id']}])
    wait_for_job(job['id'], 15)
    pool = @cp.create_pool(@owner['key'], product['id'])
    ent = consumer_cp.consume_pool(pool['id'], {:quantity => 1})[0]
    x509 = OpenSSL::X509::Certificate.new(ent['certificates'][0]['cert'])
    extensions_hash = Hash[x509.extensions.collect { |ext| [ext.oid, ext.value] }]
    serial = ent['certificates'][0]['serial']['serial']

    expect(extensions_hash.has_key?("1.3.6.1.4.1.2312.9.2.#{contentA['id']}.1")).to be false
    expect(extensions_hash.has_key?("1.3.6.1.4.1.2312.9.2.#{contentB['id']}.1")).to be true
    expect(extensions_hash.has_key?("1.3.6.1.4.1.2312.9.2.#{contentC['id']}.1")).to be false

    envB = @org_admin.create_environment(@owner['key'], 'envB', "My Test Env 2", "For test systems only.")
    job = @org_admin.promote_content(envB['id'],[{:contentId => contentC['id'],}])
    wait_for_job(job['id'], 15)
    consumer_cp.update_consumer({:environments => [{:name => envB.name}, {:name => @env.name}]})
    ent = consumer_cp.list_entitlements()[0]
    x509 = OpenSSL::X509::Certificate.new(ent['certificates'][0]['cert'])
    extensions_hash = Hash[x509.extensions.collect { |ext| [ext.oid, ext.value] }]
    new_serial = ent['certificates'][0]['serial']['serial']

    expect(new_serial).not_to eq(serial)
    expect(extensions_hash.has_key?("1.3.6.1.4.1.2312.9.2.#{contentA['id']}.1")).to be false
    expect(extensions_hash.has_key?("1.3.6.1.4.1.2312.9.2.#{contentB['id']}.1")).to be true
    expect(extensions_hash.has_key?("1.3.6.1.4.1.2312.9.2.#{contentC['id']}.1")).to be true
  end

  it 'should re-gen consumer entitlement cert when higher priority environment is added where provided
    content is NOT same as higher priority environment (production)' do
    # This spec test is same as above only difference is that,
    # the content is present on provided products instead of on main product
    # just like we have on production environment
    consumer = @org_admin.register(random_string('testsystem'), :system, nil, {}, nil, nil, [], [],
      [{ 'id' => @env['id']}])

    expect(consumer['environments']).not_to be_nil

    consumer_cp = Candlepin.new(nil, nil, consumer['idCert']['cert'], consumer['idCert']['key'])
    provided_prod = create_product("123", "123", { :owner => @owner['key']})
    contentA = create_content
    contentB = create_content
    contentC = create_content
    @cp.add_content_to_product(@owner['key'], provided_prod['id'], contentA['id'])
    @cp.add_content_to_product(@owner['key'], provided_prod['id'], contentB['id'])
    @cp.add_content_to_product(@owner['key'], provided_prod['id'], contentC['id'])
    product = create_product("12345", "12345", {:owner => @owner['key'], :providedProducts => [provided_prod.id]})

    job = @org_admin.promote_content(@env['id'], [{:contentId => contentB['id']}])
    wait_for_job(job['id'], 15)
    pool = @cp.create_pool(@owner['key'], product['id'])
    ent = consumer_cp.consume_pool(pool['id'], {:quantity => 1})[0]
    x509 = OpenSSL::X509::Certificate.new(ent['certificates'][0]['cert'])
    extensions_hash = Hash[x509.extensions.collect { |ext| [ext.oid, ext.value] }]
    serial = ent['certificates'][0]['serial']['serial']

    expect(extensions_hash.has_key?("1.3.6.1.4.1.2312.9.2.#{contentA['id']}.1")).to be false
    expect(extensions_hash.has_key?("1.3.6.1.4.1.2312.9.2.#{contentB['id']}.1")).to be true
    expect(extensions_hash.has_key?("1.3.6.1.4.1.2312.9.2.#{contentC['id']}.1")).to be false

    envB = @org_admin.create_environment(@owner['key'], 'envB', "My Test Env 2", "For test systems only.")
    job = @org_admin.promote_content(envB['id'],[{:contentId => contentC['id'],}])
    wait_for_job(job['id'], 15)
    consumer_cp.update_consumer({:environments => [{:name => envB.name}, {:name => @env.name}]})
    ent = consumer_cp.list_entitlements()[0]
    x509 = OpenSSL::X509::Certificate.new(ent['certificates'][0]['cert'])
    extensions_hash = Hash[x509.extensions.collect { |ext| [ext.oid, ext.value] }]
    new_serial = ent['certificates'][0]['serial']['serial']

    expect(new_serial).not_to eq(serial)
    expect(extensions_hash.has_key?("1.3.6.1.4.1.2312.9.2.#{contentA['id']}.1")).to be false
    expect(extensions_hash.has_key?("1.3.6.1.4.1.2312.9.2.#{contentB['id']}.1")).to be true
    expect(extensions_hash.has_key?("1.3.6.1.4.1.2312.9.2.#{contentC['id']}.1")).to be true
  end

  it 'should NOT re-gen consumer entitlement cert when lower priority environment is added where provided
    content is same as higher priority environment' do
    consumer = @org_admin.register(random_string('testsystem'), :system, nil, {}, nil, nil, [], [],
      [{ 'id' => @env['id']}])

    expect(consumer['environments']).not_to be_nil

    consumer_cp = Candlepin.new(nil, nil, consumer['idCert']['cert'], consumer['idCert']['key'])
    product = create_product
    contentA = create_content
    contentB = create_content
    contentC = create_content
    @cp.add_content_to_product(@owner['key'], product['id'], contentA['id'])
    @cp.add_content_to_product(@owner['key'], product['id'], contentB['id'])
    @cp.add_content_to_product(@owner['key'], product['id'], contentC['id'])

    job = @org_admin.promote_content(@env['id'],[{:contentId => contentB['id']}])
    wait_for_job(job['id'], 15)
    pool = @cp.create_pool(@owner['key'], product['id'])
    ent = consumer_cp.consume_pool(pool['id'], {:quantity => 1})[0]
    x509 = OpenSSL::X509::Certificate.new(ent['certificates'][0]['cert'])
    extensions_hash = Hash[x509.extensions.collect { |ext| [ext.oid, ext.value] }]
    serial = ent['certificates'][0]['serial']['serial']

    expect(extensions_hash.has_key?("1.3.6.1.4.1.2312.9.2.#{contentB['id']}.1")).to be true
    expect(extensions_hash.has_key?("1.3.6.1.4.1.2312.9.2.#{contentA['id']}.1")).to be false
    expect(extensions_hash.has_key?("1.3.6.1.4.1.2312.9.2.#{contentC['id']}.1")).to be false

    envB = @org_admin.create_environment(@owner['key'], 'envB', "My Test Env 2", "For test systems only.")
    job = @org_admin.promote_content(envB['id'],[{:contentId => contentB['id']}])
    wait_for_job(job['id'], 15)
    consumer_cp.update_consumer({:environments => [{:name => @env.name}, {:name => envB.name}]})
    ent = consumer_cp.list_entitlements()[0]
    x509 = OpenSSL::X509::Certificate.new(ent['certificates'][0]['cert'])
    extensions_hash = Hash[x509.extensions.collect { |ext| [ext.oid, ext.value] }]
    new_serial = ent['certificates'][0]['serial']['serial']

    expect(new_serial).to eq(serial)
    expect(extensions_hash.has_key?("1.3.6.1.4.1.2312.9.2.#{contentB['id']}.1")).to be true
    expect(extensions_hash.has_key?("1.3.6.1.4.1.2312.9.2.#{contentA['id']}.1")).to be false
    expect(extensions_hash.has_key?("1.3.6.1.4.1.2312.9.2.#{contentC['id']}.1")).to be false
  end

  it 'should re-gen consumer entitlement cert when environment is added having unique content' do
    consumer = @org_admin.register(random_string('testsystem'), :system, nil, {}, nil, nil, [], [],
      [{ 'id' => @env['id']}])

    expect(consumer['environments']).not_to be_nil

    consumer_cp = Candlepin.new(nil, nil, consumer['idCert']['cert'], consumer['idCert']['key'])
    product = create_product
    contentA = create_content
    contentB = create_content
    contentC = create_content
    @cp.add_content_to_product(@owner['key'], product['id'], contentA['id'])
    @cp.add_content_to_product(@owner['key'], product['id'], contentB['id'])
    @cp.add_content_to_product(@owner['key'], product['id'], contentC['id'])
    job = @org_admin.promote_content(@env['id'],[{:contentId => contentB['id']}])
    wait_for_job(job['id'], 15)
    pool = @cp.create_pool(@owner['key'], product['id'])
    ent = consumer_cp.consume_pool(pool['id'], {:quantity => 1})[0]
    x509 = OpenSSL::X509::Certificate.new(ent['certificates'][0]['cert'])
    extensions_hash = Hash[x509.extensions.collect { |ext| [ext.oid, ext.value] }]
    serial = ent['certificates'][0]['serial']['serial']

    expect(extensions_hash.has_key?("1.3.6.1.4.1.2312.9.2.#{contentB['id']}.1")).to be true
    expect(extensions_hash.has_key?("1.3.6.1.4.1.2312.9.2.#{contentA['id']}.1")).to be false
    expect(extensions_hash.has_key?("1.3.6.1.4.1.2312.9.2.#{contentC['id']}.1")).to be false

    envB = @org_admin.create_environment(@owner['key'], 'envB', "envB", "For test systems only.")
    job = @org_admin.promote_content(envB['id'],[{:contentId => contentC['id']}])
    wait_for_job(job['id'], 15)
    consumer_cp.update_consumer({:environments => [{:name => @env.name}, {:name => envB.name}]})
    ent = consumer_cp.list_entitlements()[0]
    x509 = OpenSSL::X509::Certificate.new(ent['certificates'][0]['cert'])
    extensions_hash = Hash[x509.extensions.collect { |ext| [ext.oid, ext.value] }]
    new_serial = ent['certificates'][0]['serial']['serial']

    expect(new_serial).not_to eq(serial)
    expect(extensions_hash.has_key?("1.3.6.1.4.1.2312.9.2.#{contentA['id']}.1")).to be false
    expect(extensions_hash.has_key?("1.3.6.1.4.1.2312.9.2.#{contentB['id']}.1")).to be true
    expect(extensions_hash.has_key?("1.3.6.1.4.1.2312.9.2.#{contentC['id']}.1")).to be true
  end

  it 'should NOT re-gen consumer entitlement cert when environment is added where entitlement does
    not provide content' do
    consumer = @org_admin.register(random_string('testsystem'), :system, nil, {}, nil, nil, [], [],
      [{ 'id' => @env['id']}])

    expect(consumer['environments']).not_to be_nil

    consumer_cp = Candlepin.new(nil, nil, consumer['idCert']['cert'], consumer['idCert']['key'])
    product = create_product
    contentA = create_content
    contentB = create_content
    contentC = create_content
    contentD = create_content
    @cp.add_content_to_product(@owner['key'], product['id'], contentA['id'])
    @cp.add_content_to_product(@owner['key'], product['id'], contentB['id'])
    @cp.add_content_to_product(@owner['key'], product['id'], contentC['id'])
    job = @org_admin.promote_content(@env['id'],[{:contentId => contentB['id'],}])
    wait_for_job(job['id'], 15)
    pool = @cp.create_pool(@owner['key'], product['id'])
    ent = consumer_cp.consume_pool(pool['id'], {:quantity => 1})[0]
    x509 = OpenSSL::X509::Certificate.new(ent['certificates'][0]['cert'])
    extensions_hash = Hash[x509.extensions.collect { |ext| [ext.oid, ext.value] }]
    serial = ent['certificates'][0]['serial']['serial']

    expect(extensions_hash.has_key?("1.3.6.1.4.1.2312.9.2.#{contentB['id']}.1")).to be true
    expect(extensions_hash.has_key?("1.3.6.1.4.1.2312.9.2.#{contentA['id']}.1")).to be false
    expect(extensions_hash.has_key?("1.3.6.1.4.1.2312.9.2.#{contentC['id']}.1")).to be false

    envB = @org_admin.create_environment(@owner['key'], 'envB', "envB", "For test systems only.")
    job = @org_admin.promote_content(@env['id'],[{:contentId => contentD['id'],}])
    wait_for_job(job['id'], 15)
    consumer_cp.update_consumer({:environments => [{:name => @env.name}, {:name => envB.name}]})
    ent = consumer_cp.list_entitlements()[0]
    x509 = OpenSSL::X509::Certificate.new(ent['certificates'][0]['cert'])
    extensions_hash = Hash[x509.extensions.collect { |ext| [ext.oid, ext.value] }]
    new_serial = ent['certificates'][0]['serial']['serial']

    expect(new_serial).to eq(serial)
    expect(extensions_hash.has_key?("1.3.6.1.4.1.2312.9.2.#{contentB['id']}.1")).to be true
    expect(extensions_hash.has_key?("1.3.6.1.4.1.2312.9.2.#{contentA['id']}.1")).to be false
    expect(extensions_hash.has_key?("1.3.6.1.4.1.2312.9.2.#{contentC['id']}.1")).to be false
  end

  it 'should re-gen consumer entitlement cert when environment priority are reversed and content
    originates from different environment' do
    envB = @org_admin.create_environment(@owner['key'], 'envB', "envB", "For test systems only.")
    consumer = @org_admin.register(random_string('testsystem'), :system, nil, {}, nil, nil, [], [],
      [{ 'id' => @env['id']}, { 'id' => envB['id']}])

    expect(consumer['environments']).not_to be_nil

    consumer_cp = Candlepin.new(nil, nil, consumer['idCert']['cert'], consumer['idCert']['key'])
    product = create_product
    contentA = create_content
    @cp.add_content_to_product(@owner['key'], product['id'], contentA['id'])

    job = @org_admin.promote_content(@env['id'],[{:contentId => contentA['id'],}])
    wait_for_job(job['id'], 15)
    job = @org_admin.promote_content(envB['id'],[{:contentId => contentA['id'],}])
    wait_for_job(job['id'], 15)

    pool = @cp.create_pool(@owner['key'], product['id'])
    ent = consumer_cp.consume_pool(pool['id'], {:quantity => 1})[0]
    x509 = OpenSSL::X509::Certificate.new(ent['certificates'][0]['cert'])
    extensions_hash = Hash[x509.extensions.collect { |ext| [ext.oid, ext.value] }]
    serial = ent['certificates'][0]['serial']['serial']

    expect(extensions_hash.has_key?("1.3.6.1.4.1.2312.9.2.#{contentA['id']}.1")).to be true

    consumer_cp.update_consumer({:environments => [{:name => envB.name}, {:name => @env.name}]})
    ent = consumer_cp.list_entitlements()[0]
    x509 = OpenSSL::X509::Certificate.new(ent['certificates'][0]['cert'])
    extensions_hash = Hash[x509.extensions.collect { |ext| [ext.oid, ext.value] }]
    new_serial = ent['certificates'][0]['serial']['serial']

    expect(new_serial).not_to eq(serial)
    expect(extensions_hash.has_key?("1.3.6.1.4.1.2312.9.2.#{contentA['id']}.1")).to be true
  end

  it 'should NOT re-gen consumer entitlement cert when environment priority are reordered excluding
    higher priority environment' do
    envB = @org_admin.create_environment(@owner['key'], 'envB', "envB", "For test systems only.")
    envC = @org_admin.create_environment(@owner['key'], 'envC', "envC", "For test systems only.")
    consumer = @org_admin.register(random_string('testsystem'), :system, nil, {}, nil, nil, [], [],
      [{ 'id' => @env['id']}, { 'id' => envB['id']}, { 'id' => envC['id']}])

    expect(consumer['environments']).not_to be_nil

    consumer_cp = Candlepin.new(nil, nil, consumer['idCert']['cert'], consumer['idCert']['key'])
    product = create_product
    contentA = create_content
    contentB = create_content
    contentC = create_content
    @cp.add_content_to_product(@owner['key'], product['id'], contentA['id'])
    @cp.add_content_to_product(@owner['key'], product['id'], contentB['id'])
    @cp.add_content_to_product(@owner['key'], product['id'], contentC['id'])
    job = @org_admin.promote_content(@env['id'],[{:contentId => contentB['id'],}])
    wait_for_job(job['id'], 15)
    job = @org_admin.promote_content(envB['id'],[{:contentId => contentB['id'],}])
    wait_for_job(job['id'], 15)
    job = @org_admin.promote_content(envC['id'],[{:contentId => contentB['id'],}])
    wait_for_job(job['id'], 15)
    pool = @cp.create_pool(@owner['key'], product['id'])
    ent = consumer_cp.consume_pool(pool['id'], {:quantity => 1})[0]
    x509 = OpenSSL::X509::Certificate.new(ent['certificates'][0]['cert'])
    extensions_hash = Hash[x509.extensions.collect { |ext| [ext.oid, ext.value] }]
    serial = ent['certificates'][0]['serial']['serial']

    expect(extensions_hash.has_key?("1.3.6.1.4.1.2312.9.2.#{contentB['id']}.1")).to be true

    consumer_cp.update_consumer({:environments => [{:name => @env.name}, {:name => envC.name},
      {:name => envB.name}]})
    ent = consumer_cp.list_entitlements()[0]
    x509 = OpenSSL::X509::Certificate.new(ent['certificates'][0]['cert'])
    extensions_hash = Hash[x509.extensions.collect { |ext| [ext.oid, ext.value] }]
    new_serial = ent['certificates'][0]['serial']['serial']

    expect(new_serial).to eq(serial)
    expect(extensions_hash.has_key?("1.3.6.1.4.1.2312.9.2.#{contentB['id']}.1")).to be true
  end

  it 'should re-gen consumer entitlement cert when environment priority are reordered and entitlement
    does not provide specific content' do
    envB = @org_admin.create_environment(@owner['key'], 'envB', "envB", "For test systems only.")
    consumer = @org_admin.register(random_string('testsystem'), :system, nil, {}, nil, nil, [], [],
      [{ 'id' => @env['id']}, {'id' => envB.name}])

    expect(consumer['environments']).not_to be_nil

    consumer_cp = Candlepin.new(nil, nil, consumer['idCert']['cert'], consumer['idCert']['key'])
    product = create_product
    contentA = create_content
    contentB = create_content
    contentC = create_content
    contentD = create_content # not added to product
    @cp.add_content_to_product(@owner['key'], product['id'], contentA['id'])
    @cp.add_content_to_product(@owner['key'], product['id'], contentB['id'])
    @cp.add_content_to_product(@owner['key'], product['id'], contentC['id'])

    job = @org_admin.promote_content(@env['id'],[{:contentId => contentB['id'],}])
    wait_for_job(job['id'], 15)
    job = @org_admin.promote_content(envB['id'],[{:contentId => contentD['id'],}])
    wait_for_job(job['id'], 15)
    job = @org_admin.promote_content(envB['id'],[{:contentId => contentB['id'],}])
    wait_for_job(job['id'], 15)
    pool = @cp.create_pool(@owner['key'], product['id'])
    ent = consumer_cp.consume_pool(pool['id'], {:quantity => 1})[0]
    x509 = OpenSSL::X509::Certificate.new(ent['certificates'][0]['cert'])
    extensions_hash = Hash[x509.extensions.collect { |ext| [ext.oid, ext.value] }]
    serial = ent['certificates'][0]['serial']['serial']

    expect(extensions_hash.has_key?("1.3.6.1.4.1.2312.9.2.#{contentB['id']}.1")).to be true
    expect(extensions_hash.has_key?("1.3.6.1.4.1.2312.9.2.#{contentD['id']}.1")).to be false

    consumer_cp.update_consumer({:environments => [{:name => envB.name}, {:name => @env.name}]})
    ent = consumer_cp.list_entitlements()[0]
    x509 = OpenSSL::X509::Certificate.new(ent['certificates'][0]['cert'])
    extensions_hash = Hash[x509.extensions.collect { |ext| [ext.oid, ext.value] }]
    new_serial = ent['certificates'][0]['serial']['serial']

    expect(new_serial).not_to eq(serial)
    expect(extensions_hash.has_key?("1.3.6.1.4.1.2312.9.2.#{contentB['id']}.1")).to be true
    expect(extensions_hash.has_key?("1.3.6.1.4.1.2312.9.2.#{contentD['id']}.1")).to be false
  end

  it 'should re-gen consumer entitlement cert when environment of higher priority is removed that
    provided same content as lower priority environment' do
    envB = @org_admin.create_environment(@owner['key'], 'envB', "envB", "For test systems only.")
    consumer = @org_admin.register(random_string('testsystem'), :system, nil, {}, nil, nil, [], [],
      [{ 'id' => @env['id']}, { 'id' => envB['id']}])

    expect(consumer['environments']).not_to be_nil

    consumer_cp = Candlepin.new(nil, nil, consumer['idCert']['cert'], consumer['idCert']['key'])
    product = create_product
    contentA = create_content
    contentB = create_content
    @cp.add_content_to_product(@owner['key'], product['id'], contentA['id'])
    @cp.add_content_to_product(@owner['key'], product['id'], contentB['id'])

    job = @org_admin.promote_content(@env['id'],[{:contentId => contentB['id'],}])
    wait_for_job(job['id'], 15)
    job = @org_admin.promote_content(envB['id'],[{:contentId => contentB['id'],}])
    wait_for_job(job['id'], 15)

    pool = @cp.create_pool(@owner['key'], product['id'])
    ent = consumer_cp.consume_pool(pool['id'], {:quantity => 1})[0]
    x509 = OpenSSL::X509::Certificate.new(ent['certificates'][0]['cert'])
    extensions_hash = Hash[x509.extensions.collect { |ext| [ext.oid, ext.value] }]
    serial = ent['certificates'][0]['serial']['serial']

    expect(extensions_hash.has_key?("1.3.6.1.4.1.2312.9.2.#{contentB['id']}.1")).to be true
    consumer_cp.update_consumer({:environments => [{:name => envB.name}]})
    ent = consumer_cp.list_entitlements()[0]
    x509 = OpenSSL::X509::Certificate.new(ent['certificates'][0]['cert'])
    extensions_hash = Hash[x509.extensions.collect { |ext| [ext.oid, ext.value] }]
    new_serial = ent['certificates'][0]['serial']['serial']

    expect(new_serial).not_to eq(serial)
    expect(extensions_hash.has_key?("1.3.6.1.4.1.2312.9.2.#{contentB['id']}.1")).to be true
  end

  it 'should NOT re-gen consumer entitlement cert when environment of lower priority is removed
    that provided content which was already provided by higher priority environment' do
    envB = @org_admin.create_environment(@owner['key'], 'envB', "envB", "For test systems only.")
    consumer = @org_admin.register(random_string('testsystem'), :system, nil, {}, nil, nil, [], [],
      [{ 'id' => @env['id']}, { 'id' => envB['id']}])

    expect(consumer['environments']).not_to be_nil

    consumer_cp = Candlepin.new(nil, nil, consumer['idCert']['cert'], consumer['idCert']['key'])
    product = create_product
    contentA = create_content
    contentB = create_content
    @cp.add_content_to_product(@owner['key'], product['id'], contentA['id'])
    @cp.add_content_to_product(@owner['key'], product['id'], contentB['id'])

    job = @org_admin.promote_content(@env['id'],[{:contentId => contentB['id'],}])
    wait_for_job(job['id'], 15)
    job = @org_admin.promote_content(envB['id'],[{:contentId => contentB['id'],}])
    wait_for_job(job['id'], 15)
    pool = @cp.create_pool(@owner['key'], product['id'])
    ent = consumer_cp.consume_pool(pool['id'], {:quantity => 1})[0]
    x509 = OpenSSL::X509::Certificate.new(ent['certificates'][0]['cert'])
    extensions_hash = Hash[x509.extensions.collect { |ext| [ext.oid, ext.value] }]
    serial = ent['certificates'][0]['serial']['serial']

    expect(extensions_hash.has_key?("1.3.6.1.4.1.2312.9.2.#{contentB['id']}.1")).to be true
    expect(extensions_hash.has_key?("1.3.6.1.4.1.2312.9.2.#{contentA['id']}.1")).to be false

    consumer_cp.update_consumer({:environments => [{:name => @env.name}]})
    ent = consumer_cp.list_entitlements()[0]
    x509 = OpenSSL::X509::Certificate.new(ent['certificates'][0]['cert'])
    extensions_hash = Hash[x509.extensions.collect { |ext| [ext.oid, ext.value] }]
    new_serial = ent['certificates'][0]['serial']['serial']

    expect(new_serial).to eq(serial)
    expect(extensions_hash.has_key?("1.3.6.1.4.1.2312.9.2.#{contentB['id']}.1")).to be true
    expect(extensions_hash.has_key?("1.3.6.1.4.1.2312.9.2.#{contentA['id']}.1")).to be false
  end

  it 'should re-gen consumer entitlement cert when environment of higher priority is removed which
    was providing unique content' do
    envB = @org_admin.create_environment(@owner['key'], 'envB', "envB", "For test systems only.")
    consumer = @org_admin.register(random_string('testsystem'), :system, nil, {}, nil, nil, [], [],
      [{ 'id' => envB['id']}, { 'id' => @env['id']}])

    expect(consumer['environments']).not_to be_nil

    consumer_cp = Candlepin.new(nil, nil, consumer['idCert']['cert'], consumer['idCert']['key'])
    product = create_product
    contentA = create_content
    contentB = create_content

    @cp.add_content_to_product(@owner['key'], product['id'], contentA['id'])
    @cp.add_content_to_product(@owner['key'], product['id'], contentB['id'])

    job = @org_admin.promote_content(@env['id'],[{:contentId => contentA['id'],}])
    wait_for_job(job['id'], 15)
    job = @org_admin.promote_content(envB['id'],[{:contentId => contentB['id'],}])
    wait_for_job(job['id'], 15)
    pool = @cp.create_pool(@owner['key'], product['id'])
    ent = consumer_cp.consume_pool(pool['id'], {:quantity => 1})[0]
    x509 = OpenSSL::X509::Certificate.new(ent['certificates'][0]['cert'])
    extensions_hash = Hash[x509.extensions.collect { |ext| [ext.oid, ext.value] }]
    serial = ent['certificates'][0]['serial']['serial']

    expect(extensions_hash.has_key?("1.3.6.1.4.1.2312.9.2.#{contentB['id']}.1")).to be true
    expect(extensions_hash.has_key?("1.3.6.1.4.1.2312.9.2.#{contentA['id']}.1")).to be true

    consumer_cp.update_consumer({:environments => [{:name => @env.name}]})
    ent = consumer_cp.list_entitlements()[0]
    x509 = OpenSSL::X509::Certificate.new(ent['certificates'][0]['cert'])
    extensions_hash = Hash[x509.extensions.collect { |ext| [ext.oid, ext.value] }]
    new_serial = ent['certificates'][0]['serial']['serial']

    expect(new_serial).not_to eq(serial)
    expect(extensions_hash.has_key?("1.3.6.1.4.1.2312.9.2.#{contentA['id']}.1")).to be true
    expect(extensions_hash.has_key?("1.3.6.1.4.1.2312.9.2.#{contentB['id']}.1")).to be false
  end

  it 'should NOT re-gen consumer entitlement cert when environment is removed that provide content
    which is not provided by entitlement' do
    envB = @org_admin.create_environment(@owner['key'], 'envB', "envB", "For test systems only.")
    consumer = @org_admin.register(random_string('testsystem'), :system, nil, {}, nil, nil, [], [],
      [{ 'id' => @env['id']}, { 'id' => envB['id']}])

    expect(consumer['environments']).not_to be_nil

    consumer_cp = Candlepin.new(nil, nil, consumer['idCert']['cert'], consumer['idCert']['key'])
    product = create_product
    contentA = create_content
    contentB = create_content
    contentC = create_content # not added to product
    contentD = create_content # not added to product
    @cp.add_content_to_product(@owner['key'], product['id'], contentA['id'])
    @cp.add_content_to_product(@owner['key'], product['id'], contentB['id'])

    job = @org_admin.promote_content(@env['id'],[{:contentId => contentC['id'],}])
    wait_for_job(job['id'], 15)
    job = @org_admin.promote_content(envB['id'],[{:contentId => contentD['id'],}])
    wait_for_job(job['id'], 15)
    pool = @cp.create_pool(@owner['key'], product['id'])
    ent = consumer_cp.consume_pool(pool['id'], {:quantity => 1})[0]
    x509 = OpenSSL::X509::Certificate.new(ent['certificates'][0]['cert'])
    extensions_hash = Hash[x509.extensions.collect { |ext| [ext.oid, ext.value] }]
    serial = ent['certificates'][0]['serial']['serial']

    # no content provided
    expect(extensions_hash.has_key?("1.3.6.1.4.1.2312.9.2.#{contentA['id']}.1")).to be false
    expect(extensions_hash.has_key?("1.3.6.1.4.1.2312.9.2.#{contentB['id']}.1")).to be false
    expect(extensions_hash.has_key?("1.3.6.1.4.1.2312.9.2.#{contentC['id']}.1")).to be false
    expect(extensions_hash.has_key?("1.3.6.1.4.1.2312.9.2.#{contentD['id']}.1")).to be false

    consumer_cp.update_consumer({:environments => [{:name => @env.name}]})
    ent = consumer_cp.list_entitlements()[0]
    x509 = OpenSSL::X509::Certificate.new(ent['certificates'][0]['cert'])
    extensions_hash = Hash[x509.extensions.collect { |ext| [ext.oid, ext.value] }]
    new_serial = ent['certificates'][0]['serial']['serial']

    expect(new_serial).to eq(serial)
    # no content provided
    expect(extensions_hash.has_key?("1.3.6.1.4.1.2312.9.2.#{contentA['id']}.1")).to be false
    expect(extensions_hash.has_key?("1.3.6.1.4.1.2312.9.2.#{contentB['id']}.1")).to be false
    expect(extensions_hash.has_key?("1.3.6.1.4.1.2312.9.2.#{contentC['id']}.1")).to be false
    expect(extensions_hash.has_key?("1.3.6.1.4.1.2312.9.2.#{contentD['id']}.1")).to be false
  end
end
