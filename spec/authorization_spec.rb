require 'spec_helper'
require 'candlepin_scenarios'

require 'rubygems'
require 'rest_client'

describe 'Authorization' do
  include CandlepinMethods
  include SimpleContentAccessMethods

  before(:each) do
    @owner = create_owner random_string('test_owner')
    @user = user_client(@owner, random_string('guy'))
  end

  it 'returns a 401 if user credentials are invalid' do
    lambda { Candlepin.new('random', 'not valid').list_consumer_types() }.should \
      raise_exception(RestClient::Request::Unauthorized)
  end

  it 'does not return a 401 for the root level, since it is not protected' do
    lambda { Candlepin.new('random', 'not valid')}.should_not \
      raise_error
  end

  it 'allows trusted consumer clients' do
    consumer_cp = consumer_client(@user, random_string('test'))
    trusted_cp = trusted_consumer_client(consumer_cp.uuid)
    trusted_cp.list_entitlements()
  end

  it 'catches invalid trusted consumer ids' do
    trusted_cp = trusted_consumer_client("JarJarBinksIsMyCoPilot")
    lambda do
      trusted_cp.list_entitlements
    end.should raise_exception(RestClient::Request::Unauthorized)
  end

  it 'allows in trusted users' do
    ownerID = random_string('test_owner1')
    owner1 = create_owner ownerID
    username = random_string("user1")
    user1 = user_client(owner1, username)
    trusted_user_cp = trusted_user_client(username)
    trusted_user_cp.list_consumers :owner => owner1['key']
  end

  it "should not be able to use SCA certificate for authentication" do
      owner = create_owner_in_sca_mode
      user = user_client(owner, random_string('guy'))
      @consumer = consumer_client(user, random_string("consumer"), type=:system, username=nil,
          facts= {'system.certificate_version' => '3.3'}, owner['key'])
      certs = @consumer.list_certificates
      certs.length.should == 1

      malicious_consumer = Candlepin.new(nil, nil, certs[0]['cert'], certs[0]['key'])
      lambda do
        malicious_consumer.get_consumer()
      end.should raise_exception(RestClient::Unauthorized)
    end

  it "super admin endpoints should reject activation key auth" do
    no_auth_client = Candlepin.new
    @cp.create_activation_key(@owner['key'], 'key1')

    lambda do
      no_auth_client.create_owner_with_activation_key(random_string("test_owner"), @owner['key'], ['key1'])
    end.should raise_exception(RestClient::Forbidden)
  end

  it "verified endpoints should reject activation key auth" do
    no_auth_client = Candlepin.new
    @cp.create_activation_key(@owner['key'], 'key1')

    lambda do
      no_auth_client.get_owner_with_activation_key(@owner['key'], ['key1'])
    end.should raise_exception(RestClient::Forbidden)
  end

  it "default security hole should reject activation key auth" do
    no_auth_client = Candlepin.new
    @cp.create_activation_key(@owner['key'], 'key1')

    lambda do
      no_auth_client.get_product_with_activation_key(@owner['key'], 'test_prod', ['key1'])
    end.should raise_exception(RestClient::Forbidden)
  end

  it "activation key auth requires owner" do
    no_auth_client = Candlepin.new

    lambda do
      no_auth_client.register(random_string('consumer'), :system, nil, {}, nil, nil, ["key1"])
    end.should raise_exception(RestClient::BadRequest)
  end

  it "activation key auth requires existing owner" do
    no_auth_client = Candlepin.new

    lambda do
      no_auth_client.register(random_string('consumer'), :system, nil, {}, nil, 'some_owner', ["key1"])
    end.should raise_exception(RestClient::Unauthorized)
  end

  it "activation key auth requires activation keys" do
    no_auth_client = Candlepin.new

    lambda do
      no_auth_client.register(random_string('consumer'), :system, nil, {}, nil, @owner['key'])
    end.should raise_exception(RestClient::Unauthorized)
  end

  it "activation key auth should fail with activation keys and username present together" do
    no_auth_client = Candlepin.new

    lambda do
      no_auth_client.register(random_string('consumer'), :system, nil, {}, 'username', @owner['key'], ["key1"])
    end.should raise_exception(RestClient::BadRequest)
  end

  it "activation key with two keys one valid should pass" do
    no_auth_client = Candlepin.new
    @cp.create_activation_key(@owner['key'], 'key1')

    consumer = no_auth_client.register(random_string('consumer'), :system, nil, {}, nil, @owner['key'], ["key1", "key2"])
    consumer.should_not == nil
  end


  it "activation key with two keys none valid should fail" do
    no_auth_client = Candlepin.new

    lambda do
      no_auth_client.register(random_string('consumer'), :system, nil, {}, nil, @owner['key'], ["key1", "key2"])
    end.should raise_exception(RestClient::Unauthorized)
  end

end
