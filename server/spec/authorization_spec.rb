require 'spec_helper'
require 'candlepin_scenarios'

require 'rubygems'
require 'rest_client'

describe 'Authorization' do
  include CandlepinMethods

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
      skip("candlepin running in standalone mode") unless is_hosted?

      owner = create_owner(random_string("test_owner"), nil, {
        'contentAccessModeList' => 'org_environment,entitlement',
        'contentAccessMode' => "org_environment"
      })
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

end
