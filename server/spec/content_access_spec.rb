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

  before(:each) do
      @owner = create_owner(random_string("test_owner"), nil, {
        'contentAccessModeList' => 'org_environment,test_access_mode,entitlement',
        'contentAccessMode' => "org_environment"
      })

      @username = random_string("user")
      @consumername = random_string("consumer")
      @consumername2 = random_string("consumer")
      @user = user_client(@owner, @username)

      @content = @cp.create_content(
          @owner['key'], "cname", 'test-content', random_string("clabel"), "ctype", "cvendor", {:content_url=>'/this/is/the/path'}, true
      )

      @content_id = @content['id']

      @product = create_product('test-product', 'some product')
      @cp.add_content_to_product(@owner['key'], @product['id'], @content_id)
  end

  it "does allow addition of the content access level" do
      skip("candlepin running in standalone mode") unless is_hosted?
      @cp.update_owner(@owner['key'], {'contentAccessMode' => "test_access_mode"})
      @owner = @cp.get_owner(@owner['key'])
      @owner['contentAccessModeList'].should == "org_environment,test_access_mode,entitlement"
      @owner['contentAccessMode'].should == "test_access_mode"
  end

  it "will assign the default mode and list when none is specified" do
      owner = @cp.create_owner random_string("test_owner")
      owner['contentAccessModeList'].should == "entitlement"
      owner['contentAccessMode'].should == "entitlement"
  end

  it "must assign a mode from the list" do
    lambda do
      owner = create_owner(random_string("test_owner"), nil, {
        'contentAccessModeList' => 'org_environment,test_access_mode'
      })
    end.should raise_exception(RestClient::BadRequest)
  end

  it "does not allow assignment of incorrect content access mode or blank" do
      skip("candlepin running in standalone mode") unless is_hosted?
      lambda do
        @cp.update_owner(@owner['key'], {'contentAccessMode' => "super_mode"})
      end.should raise_exception(RestClient::BadRequest)
      lambda do
        @cp.update_owner(@owner['key'], {'contentAccessMode' => ""})
      end.should raise_exception(RestClient::BadRequest)
  end

  it "does produce a content access certificate for the consumer on registration" do
      @consumer = consumer_client(@user, @consumername, type=:system, username=nil, facts= {'system.certificate_version' => '3.3'})
      certs = @consumer.list_certificates
      certs.length.should == 1

      json_body = extract_payload(certs[0]['cert'])
      #puts ("json_body:%s" % json_body)

      content = json_body['products'][0]['content'][0]
      content['type'].should == 'ctype'
      content['name'].should == @content.name
      content['label'].should == @content.label
      content['vendor'].should == @content.vendor
      content['path'].should == '/' + @owner['key'] + '/this/is/the/path'

      value = extension_from_cert(certs[0]['cert'], "1.3.6.1.4.1.2312.9.7")
      urls = []
      urls[0] = '/' + @owner['key']
      are_content_urls_present(value, urls).should == true
      type = extension_from_cert(certs[0]['cert'], "1.3.6.1.4.1.2312.9.8")
      type.should == 'OrgLevel'
  end

  it "does not produce a content access certificate a V1 consumer on registration" do
      @consumer = consumer_client(@user, @consumername, type=:system, username=nil, facts= {'system.certificate_version' => '1.0'})
      certs = @consumer.list_certificates
      certs.length.should == 0
  end

  it "does have both owner and environment in the path for the content access cert" do
      @env = @user.create_environment(@owner['key'], random_string('testenv'),
        "My Test Env 1", "For test systems only.")

      job = @user.promote_content(@env['id'],
          [{
            :contentId => @content['id'],
            :enabled => false,
          }])
      wait_for_async_job(job['id'], 15)

      consumer = @user.register(random_string('consumer'), :system, nil,
          {'system.certificate_version' => '3.3'},nil, nil, [], [], @env['id'])
      consumer['environment'].should_not be_nil
      @consumer = Candlepin.new(nil, nil, consumer['idCert']['cert'],
          consumer['idCert']['key'])
      certs = @consumer.list_certificates
      certs.length.should == 1
      json_body = extract_payload(certs[0]['cert'])
      # puts ("json_body:%s" % json_body)

      content = json_body['products'][0]['content'][0]
      content['path'].should == '/' + @owner['key'] + '/' + @env['name'] + '/this/is/the/path'
      content['enabled'].should == false

      value = extension_from_cert(certs[0]['cert'], "1.3.6.1.4.1.2312.9.7")
      urls = []
      urls[0] = '/' + @owner['key'] + '/' + @env['name']
      are_content_urls_present(value, urls).should == true
  end

  it "environment content changes show in content access cert" do
    @env = @user.create_environment(@owner['key'], random_string('testenv'),
                                    "My Test Env 1", "For test systems only.")
    @env['environmentContent'].size.should == 0

    consumer = @user.register(random_string('consumer'), :system, nil,
                              {'system.certificate_version' => '3.3'},nil, nil, [], [], @env['id'])
    consumer['environment'].should_not be_nil
    @consumer = Candlepin.new(nil, nil, consumer['idCert']['cert'], consumer['idCert']['key'])
    certs = @consumer.list_certificates
    certs.length.should == 1
    json_body = extract_payload(certs[0]['cert'])
    json_body['products'][0]['content'].length.should == 0

    job = @user.promote_content(@env['id'],
            [{
                 :contentId => @content['id'],
                 :enabled => false,
             }])
    wait_for_async_job(job['id'], 15)
    @env = @user.get_environment(@env['id'])
    @env['environmentContent'].size.should == 1

    certs = @consumer.list_certificates
    certs.length.should == 1
    json_body = extract_payload(certs[0]['cert'])
    json_body['products'][0]['content'].length.should == 1
    content = json_body['products'][0]['content'][0]
    content['id'].should == @content['id']

    job = @user.demote_content(@env['id'], [@content['id']])
    wait_for_async_job(job['id'], 15)
    @env = @user.get_environment(@env['id'])
    @env['environmentContent'].size.should == 0

    certs = @consumer.list_certificates
    certs.length.should == 1
    json_body = extract_payload(certs[0]['cert'])
    json_body['products'][0]['content'].length.should == 0
  end

  it "environment change shows in content access cert" do
    env1 = @user.create_environment(@owner['key'], random_string('testenv1'),
                                    "My Test Env 1", "For test systems only.")
    env2 = @user.create_environment(@owner['key'], random_string('testenv2'),
                                    "My Test Env 2", "For test systems only.")

    consumer = @user.register(random_string('consumer'), :system, nil,
                              {'system.certificate_version' => '3.3'},nil, nil, [], [], env1['id'])
    consumer['environment'].should_not be_nil
    consumer_cp = Candlepin.new(nil, nil, consumer['idCert']['cert'], consumer['idCert']['key'])
    certs = consumer_cp.list_certificates
    certs.length.should == 1
    value = (extension_from_cert(certs[0]['cert'], "1.3.6.1.4.1.2312.9.7"))
    urls = []
    urls[0] = '/' + @owner['key'] + '/' + env1['name']
    are_content_urls_present(value, urls).should == true

    consumer_cp.update_consumer({:environment => env2})
    changed_consumer = consumer_cp.get_consumer();
    changed_consumer.environment['id'].should == env2.id

    certs = consumer_cp.list_certificates
    certs.length.should == 1
    value = (extension_from_cert(certs[0]['cert'], "1.3.6.1.4.1.2312.9.7"))
    urls = []
    urls[0] = '/' + @owner['key'] + '/' + env2['name']
    are_content_urls_present(value, urls).should == true
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
      skip("candlepin running in standalone mode") unless is_hosted?
      @consumer = consumer_client(@user, @consumername, type=:system, username=nil, facts= {'system.certificate_version' => '3.3'})
      certs = @consumer.list_certificates
      certs.length.should == 1

      @cp.update_owner(@owner['key'], {'contentAccessMode' => "entitlement"})
      certs = @consumer.list_certificates
      certs.length.should == 0
  end

  it "can create the content access certificate for the consumer when org content access mode added" do
      skip("candlepin running in standalone mode") unless is_hosted?
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
      urls = []
      urls[0] = '/' + @owner['key']
      are_content_urls_present(value, urls).should == true

      listing = content_body.contentListing.values[0][1]
      json_body = extract_payload(listing)
      content = json_body['products'][0]['content'][0]
      content['path'].should == '/' + @owner['key'] + '/this/is/the/path'
  end

  it "does return a not modified return code when the data has not been updated since date" do
      @consumer = consumer_client(@user, @consumername, type=:system, username=nil, facts= {'system.certificate_version' => '3.3'})
      certs = @consumer.list_certificates
      certs.length.should == 1
      sleep 1
      date = Time.now
      lambda do
        listing = @consumer.get_content_access_body({:since => date.httpdate})
      end.should raise_exception(RestClient::NotModified)
  end


 it "does update exisiting content access cert content when product data changes" do
      @consumer = consumer_client(@user, @consumername, type=:system, username=nil, facts= {'system.certificate_version' => '3.3'})
      certs = @consumer.list_certificates
      certs.length.should == 1
      serial_id = certs[0]['serial']['serial']
      sleep 1

      @cp.update_content(@owner['key'], @content_id, {:name=>'cname-extreme'})

      certs = @consumer.list_certificates
      json_body = extract_payload(certs[0]['cert'])
      content = json_body['products'][0]['content'][0]
      certs[0]['serial']['serial'].should == serial_id
      content['name'].should == 'cname-extreme'
 end

 it "does update second exisitng content access cert content when product data changes" do
      @consumer1 = consumer_client(@user, @consumername, type=:system, username=nil, facts= {'system.certificate_version' => '3.3'})
      @consumer2 = consumer_client(@user, @consumername2, type=:system, username=nil, facts= {'system.certificate_version' => '3.3'})
      certs1 = @consumer1.list_certificates
      certs2 = @consumer2.list_certificates
      certs2.length.should == 1
      serial_id2 = certs2[0]['serial']['serial']
      sleep 1

      @cp.update_content(@owner['key'], @content_id, {:name=>'cname-extreme'})

      certs1 = @consumer1.list_certificates
      certs2 = @consumer2.list_certificates
      json_body = extract_payload(certs2[0]['cert'])
      content = json_body['products'][0]['content'][0]
      certs2[0]['serial']['serial'].should == serial_id2
      content['name'].should == 'cname-extreme'
 end

 it "does not update exisitng content access cert content when no data changes" do
      @consumer1 = consumer_client(@user, @consumername, type=:system, username=nil, facts= {'system.certificate_version' => '3.3'})
      certs = @consumer1.list_certificates
      serial_id = certs[0]['serial']['serial']
      updated = certs[0]['updated']
      sleep 1

      certs = @consumer1.list_certificates
      certs[0]['serial']['serial'].should == serial_id
      certs[0]['updated'].should == updated
 end

 it "does include the content access cert serial in serial list" do
      @consumer1 = consumer_client(@user, @consumername, type=:system, username=nil, facts= {'system.certificate_version' => '3.3'})
      certs = @consumer1.list_certificates
      serial_id = certs[0]['serial']['serial']

      @consumer1.list_certificate_serials[0]['serial'].should == serial_id

 end

 it "can set the correct content access mode for a manifest consumer" do
      skip("candlepin running in standalone mode") unless is_hosted?
     consumer = @user.register(random_string('consumer'), :candlepin, nil,
         {},nil, nil, [], [], nil)
     @consumer = Candlepin.new(nil, nil, consumer['idCert']['cert'],
         consumer['idCert']['key'])

     @consumer.update_consumer({'contentAccessMode' => "test_access_mode"})
     consumer_now = @consumer.get_consumer()
     consumer_now["contentAccessMode"].should == "test_access_mode"
     lambda do
         @consumer.update_consumer({'contentAccessMode' => "org"})
     end.should raise_exception(RestClient::BadRequest)
 end

 it "will not set the content access mode for a regular consumer" do
      @consumer = consumer_client(@user, @consumername, type=:system, username=nil, facts= {'system.certificate_version' => '3.3'})
      lambda do
          @consumer.update_consumer({'contentAccessMode' => "org_environment"})
      end.should raise_exception(RestClient::BadRequest)
 end

 it "will show up in the owner at distributor on export/import" do
      skip("candlepin running in standalone mode") unless is_hosted?
      @cp = Candlepin.new('admin', 'admin')
      @cp_export = StandardExporter.new
      owner = @cp_export.owner

      @cp.update_owner(owner['key'],{'contentAccessMode' => "test_access_mode"})
      candlepin_client = @cp_export.candlepin_client
      candlepin_client.update_consumer({'contentAccessMode' => "test_access_mode"})
      @cp_export.create_candlepin_export()
      @cp_export_file = @cp_export.export_filename

      @candlepin_consumer = @cp_export.candlepin_client.get_consumer()
      @candlepin_consumer.unregister @candlepin_consumer['uuid']

      @import_owner = @cp.create_owner(random_string("test_owner"))
      @import_username = random_string("import-user")
      @import_owner_client = user_client(@import_owner, @import_username)
      import_record = @cp.import(@import_owner['key'], @cp_export_file)
      import_record.status.should == 'SUCCESS'
      import_record.statusMessage.should == "#{@import_owner['key']} file imported successfully."

      owner = @cp.get_owner(@import_owner['key'])
      owner['contentAccessMode'].should == 'test_access_mode'
      owner['contentAccessModeList'].should == 'test_access_mode'

      uc = owner.upstreamConsumer['contentAccessMode'].should == 'test_access_mode'

      @cp.delete_owner(@import_owner['key'])
      @cp_export.cleanup()
 end


  RSpec.shared_examples "manifest import" do |async|
    it "will import a manifest when only the access mode changes" do
      skip("candlepin running in standalone mode") unless is_hosted?

      cp = Candlepin.new('admin', 'admin')
      cp_export = async ? AsyncStandardExporter.new : StandardExporter.new
      owner = cp_export.owner

      export1 = cp_export.create_candlepin_export()
      export1_file = cp_export.export_filename

      # Sleep long enough to ensure the timestamps would change
      sleep 1

      candlepin_client = cp_export.candlepin_client
      candlepin_client.update_consumer({'contentAccessMode' => "org_environment"})
      export2 = cp_export.create_candlepin_export()
      export2_file = cp_export.export_filename

      def read_json_file(filename)
        file = File.open(filename)
        json = JSON.load(file)
        file.close()

        return json
      end

      # Verify the exports are indeed different
      export1_metadata = read_json_file("#{export1.export_dir}/meta.json")
      export2_metadata = read_json_file("#{export2.export_dir}/meta.json")

      expect(export2_metadata['created']).to_not eq(export1_metadata['created'])

      # Verify the consumer access mode is updated and correct in the second export
      export1_consumer = read_json_file("#{export1.export_dir}/consumer.json")
      export2_consumer = read_json_file("#{export2.export_dir}/consumer.json")

      expect(export2_consumer['contentAccessMode']).to_not eq(export1_consumer['contentAccessMode'])
      expect(export2_consumer['contentAccessMode']).to eq("org_environment")

      candlepin_consumer = candlepin_client.get_consumer()
      candlepin_consumer.unregister(candlepin_consumer['uuid'])

      # Import the first manifest
      import_owner = create_owner(random_string("import_org"), nil, {
        'contentAccessModeList' => 'org_environment,test_access_mode,entitlement',
        'contentAccessMode' => "org_environment"
      })
      import_username = random_string("import-user")
      import_owner_client = user_client(import_owner, import_username)

      import_record = @cp.import(import_owner['key'], export1_file)
      import_record.status.should == 'SUCCESS'
      import_record.statusMessage.should == "#{import_owner['key']} file imported successfully."

      owner = @cp.get_owner(import_owner['key'])
      expect(owner['contentAccessMode']).to_not eq(export2_consumer['contentAccessMode'])

      pools = @cp.list_owner_pools(import_owner['key'])

      # Import the second manifest. This should set the content access mode properly
      import_record = @cp.import(import_owner['key'], export2_file)
      import_record.status.should == 'SUCCESS'
      import_record.statusMessage.should == "#{import_owner['key']} file imported successfully."

      owner = @cp.get_owner(import_owner['key'])
      expect(owner['contentAccessMode']).to eq(export2_consumer['contentAccessMode'])

      pools2 = @cp.list_owner_pools(import_owner['key'])

      # Remove last updated time, since that changes anyway...? This seems odd, but as long as no other
      # details change, it's fine... I guess.
      pools.each { |p| p.delete("updated") }
      pools2.each { |p| p.delete("updated") }

      # Sort the objects by UUIDs so we don't get false negatives there.
      pools = pools.sort_by { |p| p.id }
      pools2 = pools2.sort_by { |p| p.id }

      expect(pools2).to eq(pools)

      cp_export.cleanup()
    end
  end

  describe "Async Tests" do
    it_should_behave_like "manifest import", true
  end

  describe "Sync import tests" do
    it_should_behave_like "manifest import", false
  end


 it "will express on consumers at the distributor" do
      skip("candlepin running in standalone mode") unless is_hosted?
      @cp = Candlepin.new('admin', 'admin')
      @cp_export = StandardExporter.new
      owner = @cp_export.owner
      @cp.update_owner(owner['key'],{'contentAccessMode' => "org_environment"})
      candlepin_client = @cp_export.candlepin_client
      candlepin_client.update_consumer({'contentAccessMode' => "org_environment"})
      @cp_export.create_candlepin_export()
      @cp_export_file = @cp_export.export_filename

      @candlepin_consumer = @cp_export.candlepin_client.get_consumer()
      @candlepin_consumer.unregister @candlepin_consumer['uuid']

      @import_owner = @cp.create_owner(random_string("test_owner"))
      @import_username = random_string("import-user")
      @import_owner_client = user_client(@import_owner, @import_username)
      import_record = @cp.import(@import_owner['key'], @cp_export_file)
      import_record.status.should == 'SUCCESS'
      import_record.statusMessage.should == "#{@import_owner['key']} file imported successfully."

      owner = @cp.get_owner(@import_owner['key'])
      @user = user_client(owner, random_string("user"))
      @consumer = consumer_client(@user, random_string("consumer"), type=:system, username=nil, facts= {'system.certificate_version' => '3.3'})
      @consumer.list_certificate_serials.size.should == 1
      @consumer.list_certificates.size.should == 1

      @cp.delete_owner(@import_owner['key'])
      @cp_export.cleanup()
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
   mkt_product1 = create_product(random_string('product'),
                                 random_string('product'),
                                 {:owner => @owner['key']})
   eng_product = create_product(random_string('product'),
                                random_string('product'),
                                {:owner => @owner['key']})
   p1 = create_pool_and_subscription(@owner['key'], mkt_product1.id, 10, [eng_product.id])

   installed = [
       {'productId' => eng_product.id, 'productName' => eng_product['name']}]

   consumer_cp = consumer_client(@user, @consumername, type=:system, username=nil, facts= {'system.certificate_version' => '3.3'})
   consumer_cp.update_consumer({:installedProducts => installed})

   lambda do
     consumer_cp.consume_product()
   end.should raise_exception(RestClient::BadRequest)

   # confirm that there is a content access cert
   #  and only a content access cert
   certs = consumer_cp.list_certificates()
   certs.length.should == 1

   json_body = extract_payload(certs[0]['cert'])
   #puts ("json_body:%s" % json_body)

   content = json_body['products'][0]['content'][0]
   content['type'].should == 'ctype'
   content['name'].should == @content.name
   content['label'].should == @content.label
   content['vendor'].should == @content.vendor
   content['path'].should == '/' + @owner['key'] + '/this/is/the/path'
 end
end
