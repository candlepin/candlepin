# encoding: utf-8
require 'spec_helper'
require 'candlepin_scenarios'
require 'json'
require 'unpack'
require 'time'

describe 'Content Access' do

  include CandlepinMethods
  include CertificateMethods
  include Unpack
  include SimpleContentAccessMethods

  before(:each) do
    @owner = create_owner_in_sca_mode

    @org_admin = user_client(@owner, random_string('guy'))

    @username = random_string("user")
    @consumername = random_string("consumer")
    @consumername2 = random_string("consumer")
    @user = user_client(@owner, @username)

    ## this product is here to show that the modified product does not affect the content in the golden ticket
    @modified_product = create_product()

    @content = @cp.create_content(
        @owner['key'], "cname", 'test-content', random_string("clabel"), "ctype", "cvendor",
        {:content_url=> '/this/is/the/path', :modified_products => [@modified_product["id"]], :arches => "x86_64"}, true)

    @content_id = @content['id']

    @product = create_product('test-product', 'some product', :attributes => {:arch => 'x86_64'})
    @cp.add_content_to_product(@owner['key'], @product['id'], @content_id)
    @pool = @cp.create_pool(@owner['key'], @product['id'], {:quantity => 10})

    # We need to sleep here to ensure enough time passes from the last content update to whatever
    # cert fetching we do at the start of most of these tests.
    sleep 1
  end

  it "should filter content with mismatched architecture" do
    # We expect this content to NOT be filtered out due to a match with the system's architecture
    content1 = @cp.create_content(
      @owner['key'], "cname1", 'test-content1', random_string("clabel1"), "ctype1", "cvendor1",
      {:content_url=> '/this/is/the/path', :arches => "ppc64"}, true)

    # We expect this content to be filtered out due to a mismatch with the system's architecture
    content2 = @cp.create_content(
      @owner['key'], "cname2", 'test-content2', random_string("clabel2"), "ctype2", "cvendor2",
      {:content_url=> '/this/is/the/path/2', :arches => "x86_64"}, true)

    # We expect this content to NOT be filtered out due it not specifying an architecture
    content3 = @cp.create_content(
      @owner['key'], "cname3", 'test-content3', random_string("clabel3"), "ctype3", "cvendor3",
      {:content_url=> '/this/is/the/path/3' }, true)

    product = create_product(nil, random_string('some product'))

    @cp.add_content_to_product(@owner['key'], product['id'], content1['id'])
    @cp.add_content_to_product(@owner['key'], product['id'], content2['id'])
    @cp.add_content_to_product(@owner['key'], product['id'], content3['id'])

    @cp.create_pool(@owner['key'], product['id'], {:quantity => 10})

    consumer = consumer_client(@user, random_string('consumer-name'), type=:system, username=nil,
      facts={'system.certificate_version' => '3.3', "uname.machine" => "ppc64"}, owner_key=@owner['key'])

    certs = consumer.list_certificates
    certs.length.should == 1

    json_body = extract_payload(certs[0]['cert'])
    json_body['products'].length.should == 1
    json_body['products'][0]['content'].length.should == 2

    # figure out the order of content in the cert, so we can assert properly
    first_content_name = json_body['products'][0]['content'][0].name
    if first_content_name == content1.name
      content1_output = json_body['products'][0]['content'][0]
      content3_output = json_body['products'][0]['content'][1]
    else
      content1_output = json_body['products'][0]['content'][1]
      content3_output = json_body['products'][0]['content'][0]
    end

    if is_standalone?
      expected_content1_url = '/' + @owner['key'] + content1.contentUrl
      expected_content3_url = '/' + @owner['key'] + content3.contentUrl
    else
      expected_content1_url = content1.contentUrl
      expected_content3_url = content3.contentUrl
    end

    expect(content1_output['type']).to eq(content1.type)
    expect(content1_output['name']).to eq(content1.name)
    expect(content1_output['label']).to eq(content1.label)
    expect(content1_output['vendor']).to eq(content1.vendor)
    expect(content1_output['path']).to eq(expected_content1_url)
    expect(content1_output['arches'][0]).to eq(content1.arches)

    expect(content3_output['type']).to eq(content3.type)
    expect(content3_output['name']).to eq(content3.name)
    expect(content3_output['label']).to eq(content3.label)
    expect(content3_output['vendor']).to eq(content3.vendor)
    expect(content3_output['path']).to eq(expected_content3_url)
    expect(content3_output['arches'][0]).to eq(content3.arches)
  end

  it "does allow changing the content access mode and mode list" do

    @cp.update_owner(@owner['key'], {'contentAccessMode' => "entitlement"})
    @owner = @cp.get_owner(@owner['key'])

    expect(@owner['contentAccessModeList']).to eq("org_environment,entitlement")
    expect(@owner['contentAccessMode']).to eq("entitlement")

    @cp.update_owner(@owner['key'], {'contentAccessModeList' => "entitlement"})
    @owner = @cp.get_owner(@owner['key'])

    expect(@owner['contentAccessModeList']).to eq("entitlement")
    expect(@owner['contentAccessMode']).to eq("entitlement")
  end

  it "will assign the default mode and list when none is specified" do
    owner = @cp.create_owner random_string("test_owner")

    expect(owner['contentAccessModeList']).to eq("entitlement,org_environment")
    expect(owner['contentAccessMode']).to eq("org_environment")
  end

  it "will assign the default mode and list when empty strings are specified" do
    owner = create_owner(random_string("test_owner"), nil, {
      'contentAccessModeList' => '',
      'contentAccessMode' => ''
    })
    expect(owner['contentAccessModeList']).to eq("entitlement,org_environment")
    expect(owner['contentAccessMode']).to eq("org_environment")
  end

  it "leave mode and list unchanged when nil is specified" do
    owner = @cp.update_owner(@owner['key'], {
      'contentAccessModeList' => nil,
      'contentAccessMode' => nil
    })

    expect(owner['contentAccessModeList']).to eq(@owner['contentAccessModeList'])
    expect(owner['contentAccessMode']).to eq(@owner['contentAccessMode'])
  end

  it "must assign a mode from the list" do
    expect {
      @owner = create_owner(random_string("test_owner"), nil, {
        'contentAccessModeList' => 'entitlement',
        'contentAccessMode' => 'org_environment'
      })
    }.to raise_exception(RestClient::BadRequest)

    expect {
      @cp.update_owner(@owner['key'], {'contentAccessMode' => "invalid_mode"})
    }.to raise_exception(RestClient::BadRequest)
  end

  it "sets mode to default when the list is updated to no longer have the original mode value" do

    # The owner is in SCA mode
    @owner = @cp.get_owner(@owner['key'])
    expect(@owner['contentAccessModeList']).to eq("org_environment,entitlement")
    expect(@owner['contentAccessMode']).to eq("org_environment")

    # If we remove SCA mode from the list, the mode should also be forced to the default (entitlement)
    @cp.update_owner(@owner['key'], {'contentAccessModeList' => "entitlement"})
    @owner = @cp.get_owner(@owner['key'])
    expect(@owner['contentAccessModeList']).to eq("entitlement")
    expect(@owner['contentAccessMode']).to eq("entitlement")
  end

  it "does produce a content access certificate for the consumer on registration" do
      @consumer = consumer_client(@user, @consumername, type=:system, username=nil, facts= {'system.certificate_version' => '3.3'})
      certs = @consumer.list_certificates
      certs.length.should == 1

      json_body = extract_payload(certs[0]['cert'])

      content = json_body['products'][0]['content'][0]
      expect(content['type']).to eq('ctype')
      expect(content['name']).to eq(@content.name)
      expect(content['label']).to eq(@content.label)
      expect(content['vendor']).to eq(@content.vendor)
      expect(content['arches'][0]).to eq(@content.arches)
      value = extension_from_cert(certs[0]['cert'], "1.3.6.1.4.1.2312.9.7")

      # Check that Standalone uses the owner key as prefix for content url, while Hosted does not
      if is_standalone?
        expect(content['path']).to eq('/' + @owner['key'] + @content.contentUrl)
        expect(are_content_urls_present(value, ['/' + @owner['key']])).to eq(true)
      else
        expect(content['path']).to eq(@content.contentUrl)
        expect(are_content_urls_present(value, ['/sca/' + @owner['key']])).to eq(true)
      end

      type = extension_from_cert(certs[0]['cert'], "1.3.6.1.4.1.2312.9.8")
      type.should == 'OrgLevel'
  end

  it "does not produce a content access certificate a V1 consumer on registration" do
      @consumer = consumer_client(@user, @consumername, type=:system, username=nil, facts= {'system.certificate_version' => '1.0'})
      certs = @consumer.list_certificates
      certs.length.should == 0
  end

  it "includes environment for the content access cert ONLY in standalone mode" do
      @env = @user.create_environment(@owner['key'], random_string('testenv'),
        "My Test Env 1", "For test systems only.")

      promote_content_to_environment(@user, @env, @content, false)

      consumer = @user.register(random_string('consumer'), :system, nil,
          {'system.certificate_version' => '3.3'},nil, nil, [], [], [{'id' => @env['id']}])
      expect(consumer['environments']).not_to eq([])
      @consumer = Candlepin.new(nil, nil, consumer['idCert']['cert'], consumer['idCert']['key'])
      certs = @consumer.list_certificates
      certs.length.should == 1
      json_body = extract_payload(certs[0]['cert'])

      content = json_body['products'][0]['content'][0]
      content['enabled'].should == false

      value = extension_from_cert(certs[0]['cert'], "1.3.6.1.4.1.2312.9.7")

      # Check that Standalone uses the owner key and environment name as prefix for content url, while Hosted does not
      if is_standalone?
        # We URL encode the environment name when generating the prefix, which replaces spaces with +
        encoded_env_name = @env['name'].gsub(' ', '+')
        expect(content['path']).to eq('/' + @owner['key'] + '/' + encoded_env_name + @content.contentUrl)
        expect(are_content_urls_present(value, ['/' + @owner['key'] + '/' + encoded_env_name])).to eq(true)
      else
        expect(content['path']).to eq(@content.contentUrl)
        expect(are_content_urls_present(value, ['/sca/' + @owner['key']])).to eq(true)
      end
  end

  it "content access cert should handle multiple environments" do
      env1 = @user.create_environment(@owner['key'], random_string('testenv'),
        "My Test Env 1", "For test systems only.")
      env2 = @user.create_environment(@owner['key'], random_string('testenv'),
        "My Test Env 2", "For test systems only.")
      content2 = @cp.create_content(
        @owner['key'], "cname2", 'test-content2', random_string("clabel2"), "ctype", "cvendor",
        {:content_url=> '/this/is/the/path2', :modified_products => [@modified_product["id"]], :arches => "x86_64"}, true)
      @cp.add_content_to_product(@owner['key'], @product['id'], content2['id'])

      promote_content_to_environment(@user, env1, @content, false)
      promote_content_to_environment(@user, env2, content2, false)

      consumer = @user.register(random_string('consumer'), :system, nil,
          {'system.certificate_version' => '3.3'},nil, nil, [], [], [{'id' => env1['id']}, {'id' => env2['id']}])
      expect(consumer['environments']).not_to eq([])
      @consumer = Candlepin.new(nil, nil, consumer['idCert']['cert'], consumer['idCert']['key'])
      certs = @consumer.list_certificates
      certs.length.should == 1
      json_body = extract_payload(certs[0]['cert'])

      contents = json_body['products'][0]['content']
      contents.length().should == 2
      env_contents = { env1['name'] => @content,  env2['name'] => content2 }
      content_envs = { @content['id'] => env1,  content2['id'] => env2 }

      contents.each { |content|
        content['enabled'].should == false
        value = extension_from_cert(certs[0]['cert'], "1.3.6.1.4.1.2312.9.7")

        env = content_envs[content['id']]
        content_url = env_contents[env['name']].contentUrl

        # Check that Standalone uses the owner key and environment name as prefix for content url, while Hosted does not
        if is_standalone?
          # We URL encode the environment name when generating the prefix, which replaces spaces with +
          encoded_env_name = env['name'].gsub(' ', '+')

          expect(content['path']).to eq('/' + @owner['key'] + '/' + encoded_env_name + content_url)
          expect(are_content_urls_present(value, ['/' + @owner['key'] + '/' + encoded_env_name])).to eq(true)
        else
          expect(content['path']).to eq(content_url)
          expect(are_content_urls_present(value, ['/sca/' + @owner['key']])).to eq(true)
        end
      }
  end

  it "environment content changes show in content access cert" do
    @env = @user.create_environment(@owner['key'], random_string('testenv'),
      "My Test Env 1", "For test systems only.")
    expect(@env['environmentContent'].size).to eq(0)

    consumer = @user.register(random_string('consumer'), :system, nil,
      {'system.certificate_version' => '3.3'},nil, nil, [], [], [{'id' => @env['id']}])
    expect(consumer['environments']).to_not eq([])

    @consumer = Candlepin.new(nil, nil, consumer['idCert']['cert'], consumer['idCert']['key'])
    certs = @consumer.list_certificates
    certs.length.should == 1
    cert = certs[0]['cert']
    json_body = extract_payload(cert)
    expect(json_body['products'][0]['content'].length).to eq(0)

    promote_content_to_environment(@user, @env, @content, false)
    @env = @user.get_environment(@env['id'])
    @env['environmentContent'].size.should == 1

    certs = @consumer.list_certificates
    certs.length.should == 1
    cert = certs[0]['cert']
    json_body = extract_payload(cert)
    json_body['products'][0]['content'].length.should == 1
    content = json_body['products'][0]['content'][0]
    content['id'].should == @content['id']

    job = @user.demote_content(@env['id'], [@content['id']])
    wait_for_job(job['id'], 15)
    @env = @user.get_environment(@env['id'])
    @env['environmentContent'].size.should == 0

    certs = @consumer.list_certificates
    certs.length.should == 1
    cert = certs[0]['cert']
    json_body = extract_payload(cert)
    json_body['products'][0]['content'].length.should == 0
  end

  it "environment change shows in content access cert" do
    env1 = @user.create_environment(@owner['key'], random_string('testenv1'), "My Test Env 1", "For test systems only.")
    env2 = @user.create_environment(@owner['key'], random_string('testenv2'), "My Test Env 2", "For test systems only.")

    consumer = @user.register(random_string('consumer'), :system, nil, {'system.certificate_version' => '3.3'}, nil, nil, [], [], [{'id' => env1['id']}])
    consumer['environments'].should_not be_nil
    consumer_cp = Candlepin.new(nil, nil, consumer['idCert']['cert'], consumer['idCert']['key'])
    certs = consumer_cp.list_certificates
    certs.length.should == 1
    value = extension_from_cert(certs[0]['cert'], "1.3.6.1.4.1.2312.9.7")

    if is_standalone?
      # We URL encode the environment name when generating the prefix, which replaces spaces with +
      encoded_env_name = env1['name'].gsub(' ', '+')
      expect(are_content_urls_present(value, ['/' + @owner['key'] + '/' + encoded_env_name])).to eq(true)
    else
      expect(are_content_urls_present(value, ['/sca/' + @owner['key']])).to eq(true)
    end

    consumer_cp.update_consumer({:environments => [env2]})
    changed_consumer = consumer_cp.get_consumer();
    changed_consumer.environments[0]['id'].should == env2.id

    certs = consumer_cp.list_certificates
    certs.length.should == 1
    value = extension_from_cert(certs[0]['cert'], "1.3.6.1.4.1.2312.9.7")
    if is_standalone?
      # We URL encode the environment name when generating the prefix, which replaces spaces with +
      encoded_env_name = env2['name'].gsub(' ', '+')
      expect(are_content_urls_present(value, ['/' + @owner['key'] + '/' + encoded_env_name])).to eq(true)
    else
      expect(are_content_urls_present(value, ['/sca/' + @owner['key']])).to eq(true)
    end
  end

  it "refresh command results in new content access cert" do
    consumer = @user.register(random_string('consumer'), :system, nil, {'system.certificate_version' => '3.3'})
    consumer_cp = Candlepin.new(nil, nil, consumer['idCert']['cert'], consumer['idCert']['key'])
    old_certs = consumer_cp.list_certificates()
    old_certs.length.should == 1
    type = extension_from_cert(old_certs[0]['cert'], "1.3.6.1.4.1.2312.9.8")
    type.should == 'OrgLevel'
    consumer_cp.regenerate_entitlement_certificates()
    new_certs = consumer_cp.list_certificates()
    old_certs.size.should == new_certs.size
    type = extension_from_cert(new_certs[0]['cert'], "1.3.6.1.4.1.2312.9.8")
    type.should == 'OrgLevel'
    old_ids = old_certs.map { |cert| cert['serial']['id']}
    new_ids = new_certs.map { |cert| cert['serial']['id']}
    (old_ids & new_ids).size.should == 0
  end

  it "can remove the content access certificate from the consumer when org content access mode removed" do
    @consumer = consumer_client(@user, @consumername, type=:system, username=nil, facts= {'system.certificate_version' => '3.3'})
    certs = @consumer.list_certificates
    certs.length.should == 1

    @cp.update_owner(@owner['key'], {'contentAccessMode' => "entitlement"})

    certs = @consumer.list_certificates
    certs.length.should == 0
  end

  it "can create the content access certificate for the consumer when org content access mode added" do
    @cp.update_owner(@owner['key'], {'contentAccessMode' => "entitlement"})

    @consumer = consumer_client(@user, @consumername, type=:system, username=nil, facts= {'system.certificate_version' => '3.3'})

    certs = @consumer.list_certificates
    certs.length.should == 0

    @cp.update_owner(@owner['key'], {'contentAccessMode' => "org_environment"})

    certs = @consumer.list_certificates
    certs.length.should == 1
  end

  it "can retrieve the content access cert body for the consumer" do
    @consumer = consumer_client(@user, @consumername, type=:system, username=nil, facts= {'system.certificate_version' => '3.3'})

    content_body = @consumer.get_content_access_body()

    value = extension_from_cert(content_body.contentListing.values[0][0], "1.3.6.1.4.1.2312.9.7")
    if is_standalone?
      expect(are_content_urls_present(value, ['/' + @owner['key']])).to eq(true)
    else
      expect(are_content_urls_present(value, ['/sca/' + @owner['key']])).to eq(true)
    end

    listing = content_body.contentListing.values[0][1]
    json_body = extract_payload(listing)
    content = json_body['products'][0]['content'][0]
    if is_standalone?
      expect(content['path']).to eq('/' + @owner['key'] + @content.contentUrl)
    else
      expect(content['path']).to eq(@content['contentUrl'])
    end
  end

  it "change in content only regenerates content part of the content access certificate" do
    @consumer = consumer_client(@user, @consumername, type=:system, username=nil, facts= {'system.certificate_version' => '3.3'})

    content_body = @consumer.get_content_access_body()
    old_cert = content_body.contentListing.values[0]
    old_x509 = old_cert[0]
    old_content = old_cert[1]
    old_last_update = content_body.lastUpdate

    @cp.update_content(@owner["key"], @content_id, { :name => "new content name" })

    # Sleep a bit to make sure the 'lastUpdate' has more than a second change
    sleep 1

    content_body = @consumer.get_content_access_body()
    cert = content_body.contentListing.values[0]
    x509 = cert[0]
    content = cert[1]
    last_update = content_body.lastUpdate

    expect(x509).to eq(old_x509)
    expect(content).to_not eq(old_content)
    expect(last_update).to_not eq(old_last_update)
  end

  it "change in content only regenerates content part of the content access certificate (with environment)" do
    environment = @user.create_environment(@owner['key'], random_string('testenv1'), "My Test Env 1", "For test systems only.")
    promote_content_to_environment(@user, environment, @content, true)

    @consumer = consumer_client(@user, @consumername, type=:system, username=nil, facts= {'system.certificate_version' => '3.3'})
    @consumer.update_consumer({:environment => environment})

    content_body = @consumer.get_content_access_body()
    old_cert = content_body.contentListing.values[0]
    old_x509 = old_cert[0]
    old_content = old_cert[1]
    old_last_update = content_body.lastUpdate

    @cp.update_content(@owner["key"], @content_id, { :name => "new content name" })

    # Sleep a bit to make sure the 'lastUpdate' has more than a second change
    sleep 1

    content_body = @consumer.get_content_access_body()
    cert = content_body.contentListing.values[0]
    x509 = cert[0]
    content = cert[1]
    last_update = content_body.lastUpdate

    expect(x509).to eq(old_x509)
    expect(content).to_not eq(old_content)
    expect(last_update).to_not eq(old_last_update)
  end

  it "does return a not modified return code when the data has not been updated since date" do
    @consumer = consumer_client(@user, @consumername, type=:system, username=nil, facts= {'system.certificate_version' => '3.3'})
    content_body = @consumer.get_content_access_body()
    last_update = Time.parse(content_body.lastUpdate).httpdate

    lambda do
      listing = @consumer.get_content_access_body({:since => last_update})
    end.should raise_exception(RestClient::NotModified)
  end

  it "does update exisiting content access cert content when product data changes" do
    @consumer = consumer_client(@user, @consumername, type=:system, username=nil, facts= {'system.certificate_version' => '3.3'})
    certs = @consumer.list_certificates
    certs.length.should == 1
    cert = certs[0]['cert']
    sleep 1

    @cp.update_content(@owner['key'], @content_id, {:name=>'cname-extreme'})

    certs = @consumer.list_certificates
    json_body = extract_payload(certs[0]['cert'])
    content = json_body['products'][0]['content'][0]

    expect(certs[0]['cert']).to_not eq(cert)
    expect(content['name']).to eq('cname-extreme')
  end

  it "does update second existing content access cert content when product data changes" do
    @consumer1 = consumer_client(@user, @consumername, type=:system, username=nil, facts= {'system.certificate_version' => '3.3'})
    @consumer2 = consumer_client(@user, @consumername2, type=:system, username=nil, facts= {'system.certificate_version' => '3.3'})
    certs1 = @consumer1.list_certificates
    certs2 = @consumer2.list_certificates

    expect(certs1.length).to eq(1)
    expect(certs2.length).to eq(1)

    cert_1 = certs1[0]['cert']
    cert_2 = certs2[0]['cert']

    sleep 1

    @cp.update_content(@owner['key'], @content_id, {:name=>'cname-extreme'})

    certs1 = @consumer1.list_certificates
    certs2 = @consumer2.list_certificates

    expect(certs1.length).to eq(1)
    expect(certs2.length).to eq(1)

    json_body = extract_payload(certs2[0]['cert'])
    content = json_body['products'][0]['content'][0]

    # The cert should have changed due to the content change, but both consumers should still have the
    # same cert
    expect(certs1[0]['cert']).to_not eq(cert_1)
    expect(certs2[0]['cert']).to_not eq(cert_2)

    expect(content['name']).to eq('cname-extreme')
  end

  it "does not update existing content access cert content when no data changes" do
    @consumer1 = consumer_client(@user, @consumername, type=:system, username=nil, facts= {'system.certificate_version' => '3.3'})
    certs = @consumer1.list_certificates
    serial_id = certs[0]['serial']['serial']
    updated = certs[0]['updated']

    sleep 1

    certs = @consumer1.list_certificates
    expect(certs[0]['serial']['serial']).to eq(serial_id)
    expect(certs[0]['updated']).to eq(updated)
  end

  it "does include the content access cert serial in serial list" do
    @consumer1 = consumer_client(@user, @consumername, type=:system, username=nil, facts= {'system.certificate_version' => '3.3'})
    certs = @consumer1.list_certificates
    serial_id = certs[0]['serial']['serial']

    @consumer1.list_certificate_serials[0]['serial'].should == serial_id
  end

  it "can set the correct content access mode for a manifest consumer" do
    consumer = @user.register(random_string('consumer'), :candlepin, nil, {}, nil, nil, [], [], [])
    @consumer = Candlepin.new(nil, nil, consumer['idCert']['cert'], consumer['idCert']['key'])

    @consumer.update_consumer({'contentAccessMode' => "entitlement"})
    consumer_now = @consumer.get_consumer()
    expect(consumer_now["contentAccessMode"]).to eq("entitlement")

    @consumer.update_consumer({'contentAccessMode' => "org_environment"})
    consumer_now = @consumer.get_consumer()
    expect(consumer_now["contentAccessMode"]).to eq("org_environment")

    # This one, not so much
    expect {
      @consumer.update_consumer({'contentAccessMode' => "invalid"})
    }.to raise_exception(RestClient::BadRequest)

    # We should be able to remove the content access mode with an empty string
    @consumer.update_consumer({'contentAccessMode' => ""})
    consumer_now = @consumer.get_consumer()
    expect(consumer_now["contentAccessMode"]).to be_nil

    # We should also be able to set the correct content access mode when registering (instead of updating)
    consumer2 = @user.register(random_string('consumer'), :candlepin, nil, {}, nil, nil, [], [], [], [],
        nil, [], nil, nil, nil, nil, nil, 0, nil, nil, nil, nil, [], nil, nil, "org_environment")
    expect(consumer2["contentAccessMode"]).to eq("org_environment")
  end

  it "will show up in the export for the distributor consumer" do
    cp_export = StandardExporter.new
    owner = cp_export.owner

    @cp.update_owner(owner['key'],{'contentAccessMode' => "entitlement"})
    candlepin_client = cp_export.candlepin_client
    candlepin_client.update_consumer({'contentAccessMode' => "entitlement"})
    cp_export.create_candlepin_export()
    exported_consumer = JSON.parse File.read(
      File.join(cp_export.export_dir, 'consumer.json')
    )
    expect(exported_consumer['contentAccessMode']).to eq("entitlement")
    cp_export.cleanup()
  end


  it "will not set the content access mode for a regular consumer" do
    @consumer = consumer_client(@user, @consumername, type=:system, username=nil, facts= {'system.certificate_version' => '3.3'})

    expect {
      @consumer.update_consumer({'contentAccessMode' => "org_environment"})
    }.to raise_exception(RestClient::BadRequest)
  end

  it "does produce a pre-dated content access certificate" do
    @consumer = consumer_client(@user, @consumername, type=:system, username=nil, facts= {'system.certificate_version' => '3.3'})
    before = (Time.now - 59 * 60)
    certs = @consumer.list_certificates
    certs.length.should == 1
    access_cert = OpenSSL::X509::Certificate.new(certs[0]['cert'])
    cert_time = access_cert.not_before
    cert_time.should < before
  end

  it 'should not auto-attach when org_environment is set for owner' do
    mkt_product1 = create_product(random_string('product'), random_string('product'), {:owner => @owner['key']})
    eng_product = create_product(random_string('product'), random_string('product'), {:owner => @owner['key']})
    @cp.create_pool(@owner['key'], mkt_product1['id'], {:quantity => 10})

    installed = [{'productId' => eng_product.id, 'productName' => eng_product['name']}]

    consumer_cp = consumer_client(@user, @consumername, type=:system, username=nil,
        facts= {'system.certificate_version' => '3.3'})
    consumer_cp.update_consumer({:installedProducts => installed})

    consumer_cp.consume_product()
    entitlements = consumer_cp.list_entitlements()
    expect(entitlements.length).to eq(0)

    # confirm that there is a content access cert
    #  and only a content access cert
    certs = consumer_cp.list_certificates()
    certs.length.should == 1

    json_body = extract_payload(certs[0]['cert'])
    #puts ("json_body:%s" % json_body)

    content = json_body['products'][0]['content'][0]
    expect(content['type']).to eq('ctype')
    expect(content['name']).to eq(@content.name)
    expect(content['label']).to eq(@content.label)
    expect(content['vendor']).to eq(@content.vendor)
    if is_standalone?
      expect(content['path']).to eq('/' + @owner['key'] + @content.contentUrl)
    else
      expect(content['path']).to eq(@content.contentUrl)
    end
  end

  it 'should regenerate SCA cert when environment content changes' do
    environment = @user.create_environment(@owner['key'], random_string('testenv1'), "My Test Env 1", "For test systems only.")
    promote_content_to_environment(@user, environment, @content, false)

    consumer = @user.register(random_string('consumer'), :system, nil, {'system.certificate_version' => '3.3'}, nil, nil, [], [], [{'id' => environment['id']}])
    expect(consumer['environment']).to_not eq([])

    client = Candlepin.new(nil, nil, consumer.idCert.cert, consumer.idCert['key'])

    regenerate_cert_test(client) do
      @cp.update_content(@owner['key'], @content['id'], { 'content_url' => '/updated/path' })
      sleep 1
    end
  end

  def promote_content_to_environment(user, environment, content, enabled = true)
    job = user.promote_content(environment['id'], [
      {
        :contentId => content['id'],
        :enabled => enabled
      }
    ])


    wait_for_job(job['id'], 15)
  end

  it 'should honour the content defaults for owner in SCA mode' do
    product = create_product('test-product-p1', 'some product-p1')

    # Content enabled = true
    content_c1 = @cp.create_content(
        @owner['key'], "cname-c1", 'test-content-c1', random_string("clabel"), "ctype", "cvendor",
        {:content_url=> '/this/is/the/path',  :modified_products => [@modified_product["id"]]}, true)
    @cp.add_content_to_product(@owner['key'], product['id'], content_c1['id'], true)

    # Content enabled = false
    content_c2 = @cp.create_content(
        @owner['key'], "cname-c2", 'test-content-c2', random_string("clabel"), "ctype", "cvendor",
        {:content_url=> '/this/is/the/path',  :modified_products => [@modified_product["id"]]}, true)
    @cp.add_content_to_product(@owner['key'], product['id'], content_c2['id'], false)

    @cp.create_pool(@owner['key'], product['id'], {:quantity => 10})
    @consumer = consumer_client(@user, @consumername, type=:system, username=nil,
        facts= {'system.certificate_version' => '3.3'})
    certs = @consumer.list_certificates

    expect(certs.length).to eq(1)

    cert = certs[0]['cert']
    json_body = extract_payload(cert)

    expect(json_body['products'][0]['content'].length).to eq(3)

    # Check content status
    json_body['products'][0]['content'].each do |content|
      if content.id == content_c1.id
          expect(content.enabled).to be_nil
      end
      if content.id == content_c2.id
          expect(content.enabled).to eq(false)
      end
    end
  end

  it 'filter out content not promoted to environment when owner is in SCA mode' do
    @env = @org_admin.create_environment(@owner['key'], random_string('testenv1'), "My Test Env 1", "For test systems only.")
    consumer = @org_admin.register(random_string('testsystem'), :system, nil,
        {'system.certificate_version' => '3.1'}, nil, nil, [], [],  [{'id' => @env['id']}] )

    expect(consumer['environment']).to_not eq([])

    consumer_cp = Candlepin.new(nil, nil, consumer['idCert']['cert'], consumer['idCert']['key'])
    product = create_product
    content = create_content # promoted
    content2 = create_content # not promoted

    # content enabled = true
    @cp.add_content_to_product(@owner['key'], product['id'], content['id'], true)
    @cp.add_content_to_product(@owner['key'], product['id'], content2['id'], true)

    # Promote content with enabled false
    promote_content_to_environment(@user, @env, content, false)

    pool = @cp.create_pool(@owner['key'], product['id'], {:quantity => 10})
    ent = consumer_cp.consume_pool(pool['id'], {:quantity => 1})[0]
    value = extension_from_cert(ent['certificates'][0]['cert'], "1.3.6.1.4.1.2312.9.6")

    expect(value).to eq("3.4")

    json_body = extract_payload(ent['certificates'][0]['cert'])

    expect(json_body['products'][0]['content'].size).to eq(1)
    expect(json_body['products'][0]['content'][0]['id']).to eq(content['id'])
    expect(json_body['products'][0]['content'][0]['enabled']).to eq(false)
  end

  it 'should handle mixed enablement of content for owner in SCA mode' do
    product_1 = create_product('test-product-p1', 'some product-p1')
    product_2 = create_product('test-product-p2', 'some product-p2')

    content_c1 = @cp.create_content(
        @owner['key'], "cname-c1", 'test-content-c1', random_string("clabel"), "ctype", "cvendor",
        {:content_url=> '/this/is/the/path',  :modified_products => [@modified_product["id"]]}, true)

    content_c2 = @cp.create_content(
        @owner['key'], "cname-c2", 'test-content-c2', random_string("clabel"), "ctype", "cvendor",
        {:content_url=> '/this/is/the/path',  :modified_products => [@modified_product["id"]]}, true)

    content_c3 = @cp.create_content(
        @owner['key'], "cname-c3", 'test-content-c3', random_string("clabel"), "ctype", "cvendor",
        {:content_url=> '/this/is/the/path',  :modified_products => [@modified_product["id"]]}, true)

    # Content enabled in both product
    @cp.add_content_to_product(@owner['key'], product_1['id'], content_c1['id'], true)
    @cp.add_content_to_product(@owner['key'], product_2['id'], content_c1['id'], true)

    # Mixed content enablement in both product
    @cp.add_content_to_product(@owner['key'], product_1['id'], content_c2['id'], false)
    @cp.add_content_to_product(@owner['key'], product_2['id'], content_c2['id'], true)

    # Content disabled in both product
    @cp.add_content_to_product(@owner['key'], product_1['id'], content_c3['id'], false)
    @cp.add_content_to_product(@owner['key'], product_2['id'], content_c3['id'], false)

    @cp.create_pool(@owner['key'], product_1['id'], {:quantity => 10})
    @cp.create_pool(@owner['key'], product_2['id'], {:quantity => 10})
    @consumer = consumer_client(@user, @consumername, type=:system, username=nil,
        facts= {'system.certificate_version' => '3.3'})
    certs = @consumer.list_certificates

    expect(certs.length).to eq(1)

    cert = certs[0]['cert']
    json_body = extract_payload(cert)

    expect(json_body['products'][0]['content'].length).to eq(4)

    json_body['products'][0]['content'].each do |content|
      if content.id == content_c1.id
          expect(content.enabled).to be_nil()
      end
      if content.id == content_c2.id
          expect(content.enabled).to be_nil()
      end
      if content.id == content_c3.id
          expect(content.enabled).to eq(false)
      end
    end
  end

  it 'should export content access certs for a consumer belonging to owner in SCA mode' do
    product = create_product('test-product-p1', 'some product-p1')
    content_c1 = @cp.create_content(
        @owner['key'], "cname-c1", 'test-content-c1', random_string("clabel"), "ctype", "cvendor",
        {:content_url=> '/this/is/the/path',  :modified_products => [@modified_product["id"]]}, true)

    # Content enabled in product
    @cp.add_content_to_product(@owner['key'], product['id'], content_c1['id'], true)
    consumer = consumer_client(@user, @consumername, type=:system, username=nil,
        facts= {'system.certificate_version' => '3.3'})
    pool = @cp.create_pool(@owner['key'], product['id'], {:quantity => 10})
    consumer.consume_pool(pool['id'], {:quantity => 1})[0]

    cert_export = StandardExporter.new
    cert_export.create_certificate_export_for_client(consumer)
    content_access_cert_dir = File.join(cert_export.export_dir, 'content_access_certificates')
    entitlement_cert_dir = File.join(cert_export.export_dir, 'entitlement_certificates')

    exported_content_access_cert = Dir.entries(content_access_cert_dir).select {|e| e != '.' and e != '..' }
    exported_entitlement_cert = Dir.entries(entitlement_cert_dir).select {|e| e != '.' and e != '..' }

    # Check if content access certs are present in exported zip file.
    exported_content_access_cert.each do |file|
      exported_cert = File.read(File.join(content_access_cert_dir, file))
      expect(exported_cert[0..26]).to include("-----BEGIN CERTIFICATE-----")
    end

    # Check if entitlement certs are present in exported zip file.
    exported_entitlement_cert.each do |file|
      exported_cert = File.read(File.join(entitlement_cert_dir, file))
      expect(exported_cert[0..26]).to include("-----BEGIN CERTIFICATE-----")
    end

    cert_export.cleanup
  end

  it 'should only add content from active pools on the SCA certificate' do
    product_1 = create_product('test-product-p1', 'some product-p1')
    product_2 = create_product('test-product-p2', 'some product-p2')

    content_c1 = @cp.create_content(
        @owner['key'], "cname-c1", 'test-content-c1', random_string("clabel"), "ctype", "cvendor",
        {:content_url=> '/this/is/the/path',  :modified_products => [@modified_product["id"]]}, true)
    @cp.add_content_to_product(@owner['key'], product_1['id'], content_c1['id'], true)

    content_c2 = @cp.create_content(
        @owner['key'], "cname-c2", 'test-content-c2', random_string("clabel"), "ctype", "cvendor",
        {:content_url=> '/this/is/the/path',  :modified_products => [@modified_product["id"]]}, true)
    @cp.add_content_to_product(@owner['key'], product_2['id'], content_c2['id'], true)

    @cp.create_pool(@owner['key'], product_2['id'], {:quantity => 10})

    @consumer = consumer_client(@user, @consumername, type=:system, username=nil,
        facts= {'system.certificate_version' => '3.3'})
    certs = @consumer.list_certificates

    expect(certs.length).to eq(1)

    cert = certs[0]['cert']
    json_body = extract_payload(cert)
    expect(json_body['products'][0]['content'].length).to eq(2)

    # Make sure that content c1 is not present in cert,
    # since product_1 does not have active pool
    json_body['products'][0]['content'].each do |content|
        expect(content.id).to_not eq(content_c1.id)
    end
  end

  it 'should include content from all products associated with active pool to SCA cert' do
    dev_eng_product = create_product(random_string('productp3'), random_string('product'), {
      :owner => @owner['key']
    })

    derived_product = create_product(random_string('productp4'), random_string('product'), {
      :owner => @owner['key'],
      :providedProducts => [dev_eng_product.id]
    })

    eng_product = create_product(random_string('productp2'), random_string('product'), {
      :owner => @owner['key']
    })

    mkt_product1 = create_product(random_string('productp1'), random_string('product'), {
      :owner => @owner['key'],
      :providedProducts => [eng_product.id],
      :derivedProduct => derived_product
    })

    # Content enabled = true
    content_c1 = @cp.create_content(
        @owner['key'], "content_c1", 'test-content-c1', random_string("clabel"), "ctype", "cvendor",
        {:content_url=> '/this/is/the/path',  :modified_products => [@modified_product["id"]]}, true)
    @cp.add_content_to_product(@owner['key'], eng_product['id'], content_c1['id'], true)

    content_c2 = @cp.create_content(
        @owner['key'], "content_c2", 'test-content-c2', random_string("clabel"), "ctype", "cvendor",
        {:content_url=> '/this/is/the/path',  :modified_products => [@modified_product["id"]]}, true)
    @cp.add_content_to_product(@owner['key'], mkt_product1['id'], content_c2['id'], true)

    content_c3 = @cp.create_content(
        @owner['key'], "content_c3", 'test-content-c3', random_string("clabel"), "ctype", "cvendor",
        {:content_url=> '/this/is/the/path',  :modified_products => [@modified_product["id"]]}, true)
    @cp.add_content_to_product(@owner['key'], derived_product['id'], content_c3['id'], true)

    content_c4 = @cp.create_content(
        @owner['key'], "content_c4", 'test-content-c4', random_string("clabel"), "ctype", "cvendor",
        {:content_url=> '/this/is/the/path',  :modified_products => [@modified_product["id"]]}, true)
    @cp.add_content_to_product(@owner['key'], dev_eng_product['id'], content_c4['id'], true)

    @cp.create_pool(@owner['key'], mkt_product1['id'], { :quantity => 10 })

    @consumer = consumer_client(@user, @consumername, type=:system, username=nil, facts={'system.certificate_version' => '3.3'})
    certs = @consumer.list_certificates

    expect(certs.length).to eq(1)

    cert = certs[0]['cert']
    json_body = extract_payload(cert)

    expect(json_body['products']).to_not be_nil
    expect(json_body['products'].length).to eq(1)
    expect(json_body['products'][0]['content']).to_not be_nil
    expect(json_body['products'][0]['content'].length).to eq(5)

    returned_uuids = []
    json_body['products'][0]['content'].each do |content|
        returned_uuids << content['id']
    end

    expect(returned_uuids).to include(content_c1.id)
    expect(returned_uuids).to include(content_c2.id)
    expect(returned_uuids).to include(content_c3.id)
    expect(returned_uuids).to include(content_c4.id)
  end

  def regenerate_cert_test(consumer_client, &updater)
    certs = consumer_client.list_certificates()
    expect(certs).to_not be_nil
    expect(certs.length).to eq(1)
    cert_serial = certs[0]['serial']
    cert = certs[0]['cert']
    expect(cert_serial).to_not be_nil

    updater.call()

    updated_certs = consumer_client.list_certificates()
    expect(updated_certs.length).to eq(1)
    updated_cert_serial = updated_certs[0]['serial']
    updated_cert = updated_certs[0]['cert']
    expect(updated_cert_serial).to_not be_nil

    expect(updated_cert).to_not eq(cert)
  end

  it 'should regenerate SCA cert when content changes affect content view' do
    client = consumer_client(@user, @consumername, type=:system, username=nil, facts= {'system.certificate_version' => '3.3'})

    regenerate_cert_test(client) do
      @cp.update_content(@owner['key'], @content['id'], { 'content_url' => '/updated/path' })
      sleep 1
    end
  end

  it 'should regenerate SCA cert when product changes affect content view' do
    client = consumer_client(@user, @consumername, type=:system, username=nil, facts= {'system.certificate_version' => '3.3'})

    regenerate_cert_test(client) do
      @cp.remove_content_from_product(@owner['key'], @product['id'], @content['id'])
    end
  end

  it 'should regenerate SCA cert when pool changes affect content view' do
    client = consumer_client(@user, @consumername, type=:system, username=nil, facts= {'system.certificate_version' => '3.3'})

    regenerate_cert_test(client) do
      @pool['start_date'] = (DateTime.now + 1)
      @cp.update_pool(@owner['key'], @pool)
      sleep 1
    end
  end

  it 'should be disabled for owner in SCA mode' do
    consumer = @user.register(random_string('testsystem'), :system, nil,
               {'system.certificate_version' => '3.1'}, nil, nil, [], [], [])

    # System purpose status
    status = @cp.get_purpose_compliance(consumer['uuid'])
    expect(status['status']).to eq("disabled")

    # compliance status
    status = @cp.get_compliance(consumer['uuid'])
    expect(status['status']).to eq("disabled")
  end

  it 'should revoke sca certs upon un-registration' do
    consumer = consumer_client(@user, @consumername, type=:system, username=nil,
      facts= {'system.certificate_version' => '3.3'})
    certs = consumer.list_certificates()

    expect(certs.length).to eq(1)

    cert_serial = certs[0]['serial']

    expect(cert_serial).to_not be_nil
    expect(cert_serial.revoked).to be(false)

    consumer.unregister(consumer.uuid)
    serial_after_unregistration = @cp.get_serial(cert_serial.id)

    expect(serial_after_unregistration.revoked).to be(true)
  end

  it 'environment deletion should regenerate sca certificate of affected consumers' do
    product1 = create_product
    content1 = @cp.create_content(
      @owner['key'], "cname99", "test-content99", random_string("clabel99"), "ctype99", "cvendor99",
      {:content_url=> "/this/is/the/path/99", :arches => "x86_64"}, true)

    @cp.add_content_to_product(@owner['key'], product1['id'], content1['id'])

    env1 = @cp.create_environment(@owner['key'], random_string("env1"), random_string("test_env_1"))
    env2 = @cp.create_environment(@owner['key'], random_string("env1"), random_string("test_env_1"))
    job = @cp.promote_content(env1['id'], [{ :contentId => content1['id'] }])
    wait_for_job(job['id'], 15)

    consumer = @cp.register(random_string("consumer2"), :system, nil,
                            facts={'system.certificate_version' => '3.3', "uname.machine" => "x86_64"}, random_string("consumer2"),
                            @owner['key'], [], [], [{ 'id' => env1['id']}, { 'id' => env2['id']}])
    consumer_cp = Candlepin.new(nil, nil, consumer['idCert']['cert'], consumer['idCert']['key'])

    old_certs = consumer_cp.list_certificates
    old_certs.length.should == 1

    regenerate_cert_test(consumer_cp) do
      @cp.delete_environment(env1['id'])
      sleep 1
    end

    new_certs = consumer_cp.list_certificates
    new_certs.length.should == 1

    old_serials = old_certs.map { |cert| cert['serial']['id']}
    new_serials = new_certs.map { |cert| cert['serial']['id']}
    old_serials.should_not include(new_serials)


  end

end
