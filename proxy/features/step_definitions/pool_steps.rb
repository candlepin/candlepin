require 'spec/expectations'
require 'candlepin_api'

Before do
    @found_pools = []
end

Then /^the first pool's product names have "([^\"]*)"$/ do |product_name|
  products = []
  @candlepin.get_pools do |pool|
	products.push(pool['pool']['productname'])
 	#pool['pool']['productName'].should == product_name
  end
  products.include?(product_name)
end

When /^I view all pools for my owner$/ do
  @found_pools = @consumer_cp.get_pools(:consumer => @consumer_cp.uuid,
                                        :listall => true)
end

Then /^I see (\d*) pools$/ do |num_pools|
  @found_pools.length.should == num_pools.to_i
end

Then /^I see (\d*) available entitlements$/ do |num_entitlements|
  available = 0
  @found_pools.each do |pool|
      available += pool['pool']['quantity'] - pool['pool']['consumed']
  end
  available.should == num_entitlements.to_i
end
