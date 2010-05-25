require 'spec/expectations'
require 'candlepin_api'

Then /^I should be able to view my consumer data$/ do
  consumer_uuid = @consumer['uuid']

  consumer = @current_consumer_cp.get_consumer(consumer_uuid)
  consumer['uuid'].should == consumer_uuid
end

Then /^I should not be able to view consumer "([^\"]*)"$/ do |consumer_name|
    target_uuid = @consumers[consumer_name].uuid
    begin
        @current_consumer_cp.get_consumer(target_uuid)
    rescue RestClient::Exception => e
        e.http_code.should == 403
    end
end

Then /^I should not find consumer "([^\"]*)"$/ do |consumer_uuid|
    begin
        @current_consumer_cp.get_consumer(consumer_uuid)
    rescue RestClient::Exception => e
        e.http_code.should == 404
    end

end

