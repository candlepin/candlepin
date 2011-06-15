require 'candlepin_scenarios'

require 'rubygems'
require 'rest_client'

describe 'Authorization' do
  include CandlepinMethods
  include CandlepinScenarios

  before(:each) do
    @owner = create_owner random_string('test_owner')
    @user = user_client(@owner, 'guy')
  end 

  it 'returns a 401 if user credentials are invalid' do
    lambda { Candlepin.new('random', 'not valid').list_consumer_types() }.should \
      raise_exception(RestClient::Request::Unauthorized)
  end

  it 'does not return a 401 for the root level, since it is not protected' do
    lambda { Candlepin.new('random', 'not valid')}.should \
      be_true
  end

  it 'updates consumer\'s last checkin time' do
    consumer_cp = consumer_client(@user, random_string('test'))
    consumer_cp.list_entitlements()
    consumer = @cp.get_consumer(consumer_cp.uuid)
    last_checkin1 = consumer['lastCheckin']

    # Do something as the consumer, should cause last checkin time to be updated:
    consumer_cp.list_entitlements()
    consumer = @cp.get_consumer(consumer_cp.uuid)
    last_checkin2 = consumer['lastCheckin']

    (last_checkin2 > last_checkin1).should be_true
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
    end.should raise_exception(RestClient::Forbidden)
  end

  it 'allows in trused users' do
    ownerID = random_string('test_owner1')
    owner1 = create_owner ownerID
    username = random_string("user1")
    user1 = user_client(owner1, username)
    trusted_user_cp = trusted_user_client(username)
    trusted_user_cp.list_consumers :owner => owner1.key
  end

  it 'forbids invalid trused users' do
    usernameBad = random_string("ANAKINSKYWALKER")

    lambda do
      trusted_user_client(usernameBad)
    end.should raise_exception(RestClient::BadRequest)
  end
end
