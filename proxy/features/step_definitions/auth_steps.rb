require 'spec/expectations'
require 'candlepin_api'


Given /^I am logged in as "([^\"]*)"$/ do |username|
    @owner_admin_cp = connect(username=username, password="password")
end

Given /^I am logged in as consumer "([^\"]*)"$/ do |consumer_name|
    @current_consumer_cp = @consumers[consumer_name]
end
