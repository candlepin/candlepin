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
      @owner = create_owner(random_string("test_owner"), nil,{'contentAccessMode' => "org_environment"})
      @username = random_string("user")
      @consumername = random_string("consumer")
      @consumername2 = random_string("consumer")
      @user = user_client(@owner, @username)

  end

  it "does allow addition of the content access level" do
      @cp.update_owner(@owner['key'], {'contentAccessMode' => "test_access_mode"})
      @owner = @cp.get_owner(@owner['key'])
      @owner['contentAccessMode'].should == "test_access_mode"
  end

  it "does not allow assignment of incorrect content access mode" do
      lambda do
          @cp.update_owner(@owner['key'], {'contentAccessMode' => "super_mode"})
      end.should raise_exception(RestClient::BadRequest)
  end

 it "can set the correct content access mode for a manifest consumer" do
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
      @consumer = consumer_client(@user, @consumername)
      lambda do
          @consumer.update_consumer({'contentAccessMode' => "org_environment"})
      end.should raise_exception(RestClient::BadRequest)
 end

 it "will show up in the distributor on export" do
      @cp = Candlepin.new('admin', 'admin')
      @cp_export = StandardExporter.new
      owner = @cp_export.owner
      @cp.update_owner(owner['key'],{'contentAccessMode' => "test_access_mode"})
      candlepin_client = @cp_export.candlepin_client
      candlepin_client.update_consumer({'contentAccessMode' => "test_access_mode"})
      @cp_export.create_candlepin_export()
      @cp_export_file = @cp_export.export_filename

      @candlepin_consumer = @cp_export.candlepin_client.get_consumer()
      @candlepin_consumer['contentAccessMode'].should == "test_access_mode"

      @cp_export.cleanup()
 end

end
