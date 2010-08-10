require 'candlepin_scenarios'
require 'candlepin_api'

require 'rubygems'
require 'rest_client'

describe 'Authorization' do
  include CandlepinMethods
  it_should_behave_like 'Candlepin Scenarios'

  it 'should return a 401 if user credentials are invalid' do
    cp = Candlepin.new('random', 'not valid')
    lambda {cp.list_owners}.should raise_exception(RestClient::Request::Unauthorized)
  end
end
