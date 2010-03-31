require 'spec/expectations'
require 'candlepin_api'

When /I Consume an Entitlement for the "(\w+)" Product/ do |product|
    @candlepin.consume_product(product)
end

Then /I Have (\d+) Entitlement[s]?/ do |entitlement_size|
    @candlepin.list_entitlements.length.should == entitlement_size.to_i
end

Then /^I Have an Entitlement for the "([^\"]*)" Product$/ do |product_id|
    product_ids = @candlepin.list_entitlements.collect do |entitlement|
        entitlement['entitlement']['pool']['productId']
    end

    product_ids.should include product_id
end

When /I Consume an Entitlement for the "(\w+)" Pool/ do |pool|
  all_pools = @candlepin.get_pools({:consumer => @candlepin.consumer['uuid']})
  
  product_pools = all_pools.select {|p| p['pool'].has_value?(pool)}
  product_pools.empty?.should == false

  results = @candlepin.consume_pool(product_pools[0]['pool']['id'])
end
