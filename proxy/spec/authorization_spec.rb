require 'candlepin_scenarios'
require 'candlepin_api'

require 'rubygems'
require 'rest_client'

describe 'Authorization' do
  include CandlepinMethods
  it_should_behave_like 'Candlepin Scenarios'

  it 'returns a 401 if user credentials are invalid' do
    lambda { Candlepin.new('random', 'not valid') }.should \
      raise_exception(RestClient::Request::Unauthorized)
  end

  it 'updates consumer\'s last checkin time' do
    consumer_cp = consumer_client(@cp, random_string('test'))
    consumer = @cp.get_consumer(consumer_cp.uuid)
    last_checkin1 = consumer['lastCheckin']

    # Do something as the consumer, should cause last checkin time to be updated:
    consumer_cp.list_entitlements()
    consumer = @cp.get_consumer(consumer_cp.uuid)
    last_checkin2 = consumer['lastCheckin']

    (last_checkin2 > last_checkin1).should be_true
  end
end
