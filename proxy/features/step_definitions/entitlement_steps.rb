require 'spec/expectations'
require 'candlepin_api'

When /I Consume an Entitlement for the (\w+) Product/ do |product|
    @entitlement = @candlepin.consume_product(product)
    @entitlement_pool = @entitlement['pool']
end

Then /I Have (\d+) Entitlement[s]?/ do |entitlement_size|
    @candlepin.list_entitlements.length.should == entitlement_size.to_i
end

def entitlement
    @entitlement
end

def entitlement_pool
    @entitlement_pool
end
