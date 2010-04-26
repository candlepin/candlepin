require 'spec/expectations'
require 'candlepin_api'

Then /^The first pool's product name is "([^\"]*)"$/ do |product_name|
  @candlepin.get_pools[0]['pool']['productName'].should == product_name
end
