require 'spec/expectations'
require 'candlepin_api'

Given /^an owner admin "([^\"]*)"$/ do |username|
    @username = username
    @password = 'password'
    @candlepin.create_user(@test_owner['key'], @username, @password)
end

