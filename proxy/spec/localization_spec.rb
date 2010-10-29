require 'candlepin_scenarios'
require 'candlepin_api'

require 'json'

describe 'Localization' do
  include CandlepinMethods
  it_should_behave_like 'Candlepin Scenarios'

  it 'returns a translated error message on a failed login' do
      lambda { Candlepin.new('admin', 'badpass', nil, nil, 'localhost', 8443,
                             'de-DE').list_consumer_types() }.should raise_error(
        RestClient::Unauthorized) { |error|
        expected = "Ungültiger Benutzername oder Kennwort"
        JSON.parse(error.http_body)["displayMessage"].should == expected
      }
  end
end
