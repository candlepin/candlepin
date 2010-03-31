require 'spec/expectations'
require 'candlepin_api'



When /^I Download Original Rules$/ do
  @orig_rules = @candlepin.list_rules 
#  pending # express the regexp above with the code you wish you had
end

Then /^I Upload Original Rules$/ do
  @candlepin.upload_rules(@orig_rules)
end


Then /^I Have Rules$/ do
  @candlepin.list_rules.length == 1
end


Given /^I have ruleset "([^\"]*)"$/ do |arg1|
  @ruleset = arg1
end

When /^I Upload Ruleset$/ do
  @candlepin.upload_rules(@ruleset)
end

When /^I Download Rules$/ do
  @rules = @candlepin.list_rules
end
