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
end
