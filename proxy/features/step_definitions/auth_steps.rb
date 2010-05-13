require 'spec/expectations'
require 'candlepin_api'


Given /^I am logged in as "([^\"]*)"$/ do |username|
    @current_owner_cp = connect(username=username, password="password")
end

Given /^I am logged in as consumer "([^\"]*)"$/ do |consumer_name|
    @current_consumer_cp = @consumers[consumer_name]
end


