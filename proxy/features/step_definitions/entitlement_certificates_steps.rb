require 'spec/expectations'
require 'candlepin_api'

Before do
    @serials = []
end

Then /^I have (\d+) certificate$/ do |certificates|
    @consumer_cp.get_certificates.length.should == certificates.to_i
end

# TODO: this test should actually be using ?serials=x,y,z to test serial filtering
# server side, not client side:
When /^I filter certificates on the serial number for "([^\"]*)"$/ do |entitlement|
    certificates = @consumer_cp.get_certificates()
    found = certificates.find {|item|
        item['entitlement']['pool']['productId'] == entitlement.hash.abs.to_s}
    @serials << found['serial']['id']
end

Then /^I see (\d+) certificate$/ do |serials|
    @consumer_cp.get_certificates(@serials).length.should == serials.to_i
end

Then /^I have (\d+) certificate serial number$/ do |serials|
    @consumer_cp.get_certificate_serials.length.should == serials.to_i
end
