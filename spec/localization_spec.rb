# encoding: utf-8
require 'spec_helper'

require 'candlepin_scenarios'
require 'json'

describe 'Localization' do
  include CandlepinMethods

  it 'returns a translated error message on a failed login' do
      lambda { Candlepin.new('admin', 'badpass', nil, nil, 'localhost', 8443,
                             'de-DE').list_consumer_types() }.should raise_error(
        RestClient::Unauthorized) { |error|
        expected = "Ungültige Berechtigungsnachweise"
        JSON.parse(error.http_body)["displayMessage"].should == expected
      }
  end

  it 'returns a translated error message for deleted conusmer' do
      cp = Candlepin.new('admin', 'admin', nil, nil, 'localhost', 8443,
                             'de-DE')
      owner = cp.create_owner random_string('test_owner')
      user = user_client(owner, random_string("user"))
      consumer = consumer_client(user, random_string("consumer"))
      consumer.unregister(consumer.uuid)
      lambda {cp.get_consumer(consumer.uuid)}.should raise_exception(
        RestClient::Gone) { |exception|
        expected = "Einheit #{consumer.uuid} wurde gelöscht"
        JSON.parse(exception.http_body)["displayMessage"].should == expected
     }
  end
end
