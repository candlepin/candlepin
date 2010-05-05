require 'spec/expectations'
require 'candlepin_api'

Then /^The first pool's product names have "([^\"]*)"$/ do |product_name|
  products = []
  @candlepin.get_pools do |pool|
	products.push(pool['pool']['productname'])
 	#pool['pool']['productName'].should == product_name
  end
  products.include?(product_name)
end
