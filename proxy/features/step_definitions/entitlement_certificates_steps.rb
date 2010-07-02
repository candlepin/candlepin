require 'spec/expectations'
require 'candlepin_api'

Before do
    @serials = []
end

# TODO: this test should actually be using ?serials=x,y,z to test serial filtering
# server side, not client side:
When /^I filter certificates on the serial number for "([^\"]*)"$/ do |entitlement|
    certificates = @consumer_cp.get_certificates()
    found = certificates.find {|item|
        item['entitlement']['pool']['productId'] == entitlement.hash.abs.to_s}
    @serials << found['serial']['id']
end

Then /^I have (\d+) filtered certificate[s]?$/ do |certificates_size|
    @consumer_cp.get_certificates(@serials).length.should == certificates_size.to_i
end

Then /^the filtered certificates are revoked$/ do
  @serials.each { |serial| @candlepin.get_serial(serial)['revoked'].should == true }
end

Then /^the filtered certificates are not revoked$/ do
  @serials.each { |serial| @candlepin.get_serial(serial)['revoked'].should == false }
end
