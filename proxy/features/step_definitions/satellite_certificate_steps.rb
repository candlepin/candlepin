require 'spec/expectations'
require 'candlepin_api'

Given /^I have imported a Satellite Certificate$/ do
    # XXX no-op for now, assumed deploy has loaded the certificate
end

Then /^I should have (\d+) products available$/ do |pools|
    @candlepin.get_pools.length.should == pools.to_i
end

