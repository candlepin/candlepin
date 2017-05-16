# encoding: utf-8
require 'spec_helper'
require 'candlepin_scenarios'
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
      @owner['contentAccessMode'].should == "test_access_mode"
  end

  it "does not allow assignment of incorrect content access mode" do
      skip("candlepin running in standalone mode") unless is_hosted?
      lambda do
          @cp.update_owner(@owner['key'], {'contentAccessMode' => "super_mode"})
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
      wait_for_job(job['id'], 15)

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
      content['path'].should == '/' + @owner['key'] + '/' + @env['id'] + '/this/is/the/path'
      content['enabled'].should == false

      value = extension_from_cert(certs[0]['cert'], "1.3.6.1.4.1.2312.9.7")
      urls = []
      urls[0] = '/' + @owner['key'] + '/' + @env['id']
      are_content_urls_present(value, urls).should == true
  end

  it "can remove the content access certificate from the consumer when org content access mode removed" do
      skip("candlepin running in standalone mode") unless is_hosted?
      @consumer = consumer_client(@user, @consumername, type=:system, username=nil, facts= {'system.certificate_version' => '3.3'})
      certs = @consumer.list_certificates
      certs.length.should == 1

      @cp.update_owner(@owner['key'], {'contentAccessMode' => ""})
      certs = @consumer.list_certificates
      certs.length.should == 0
  end

  it "can create the content access certificate for the consumer when org content access mode added" do
      skip("candlepin running in standalone mode") unless is_hosted?
      @cp.update_owner(@owner['key'], {'contentAccessMode' => ""})
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


 it "does update exisitng content access cert content when product data changes" do
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

end
