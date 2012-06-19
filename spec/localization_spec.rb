# encoding: utf-8

require 'candlepin_scenarios'
require 'json'

describe 'Localization' do
  include CandlepinMethods
  include CandlepinScenarios

  it 'returns a translated error message on a failed login' do
      lambda { Candlepin.new('admin', 'badpass', nil, nil, 'localhost', 8443,
                             'de-DE').list_consumer_types() }.should raise_error(
        RestClient::Unauthorized) { |error|
        expected = "Ungültige Berechtigungnachweise"
        JSON.parse(error.http_body)["displayMessage"].should == expected
      }
  end
end
