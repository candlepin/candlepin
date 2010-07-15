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

Then /^the filtered certificates are in the CRL$/ do
  @serials.each { |serial| revoked_serials.should include(serial) }
end

Then /^the filtered certificates are not in the CRL$/ do
  @serials.each { |serial| revoked_serials.should_not include(serial) }
end

When /^I regenerate all my entitlement certificates$/ do
  @old_certs = @consumer_cp.get_certificates()
  @consumer_cp.regenerate_entitlement_certificates()
end

Then /^I have new entitlement certificates$/ do
  new_certs = @consumer_cp.get_certificates()
  @old_certs.size.should == new_certs.size
  old_ids = @old_certs.map { |cert| cert['serial']['id']}
  new_ids = new_certs.map { |cert| cert['serial']['id']}

  (old_ids & new_ids).size.should == 0
end

def revoked_serials
  @candlepin.get_crl.revoked.collect { |entry| entry.serial }
end
