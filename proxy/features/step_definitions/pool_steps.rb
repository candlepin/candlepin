require 'spec/expectations'
require 'candlepin_api'

Then /^the first pool's product name is "([^\"]*)"$/ do |product_name|
  @consumer_cp.get_pools(:consumer => @consumer_cp.uuid)[0]['pool']['productName'].should == product_name
end
