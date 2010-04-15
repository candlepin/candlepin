require 'spec/expectations'
require 'candlepin_api'

Then /^I should be able to view my Consumer data$/ do
  consumer_uuid = @candlepin.consumer['uuid']

  consumer = @candlepin.get_consumer(consumer_uuid)
  consumer['consumer']['uuid'].should == consumer_uuid
end

Then /^I should not be able to view Consumer "([^\"]*)"$/ do |consumer_uuid|
  begin
    @candlepin.get_consumer(consumer_uuid)
  rescue RestClient::Exception => e
    e.http_code.should == 403
  end
end
