require 'spec/expectations'
require 'candlepin_api'

When /I Consume an Entitlement for the "(\w+)" Product/ do |product|
    @candlepin.consume_product(product)
end

Then /I Have (\d+) Entitlement[s]?/ do |entitlement_size|
    @candlepin.list_entitlements.length.should == entitlement_size.to_i
end

Then /I Have (\d+) Certificate[s]?/ do |certificates_size|
    @candlepin.get_certificates.length.should == certificates_size.to_i
end

Then /^I Have an Entitlement for the "([^\"]*)" Product$/ do |product_id|
    product_ids = @candlepin.list_entitlements.collect do |entitlement|
        entitlement['entitlement']['pool']['productId']
    end

    product_ids.should include product_id
end

When /I Consume an Entitlement for the "(\w+)" Pool$/ do |pool|
  all_pools = @candlepin.get_pools({:consumer => @candlepin.consumer['uuid']})
  
  product_pools = all_pools.select {|p| p['pool'].has_value?(pool)}
  product_pools.empty?.should == false

  results = @candlepin.consume_pool(product_pools[0]['pool']['id'])
end

Then /^I Get (\d+) Entitlement When I Filter by Product ID "(\w+)"$/ do |entitlement_size, product_id|
  @candlepin.list_entitlements(product_id).length.should == entitlement_size.to_i
end

Then /^I Get an Exception If I Filter by Product ID "(\w+)"$/ do |product_id|
  begin
    @candlepin.list_entitlements(product_id)
  rescue RestClient::Exception => e
    puts "response: #{e.response}"
    puts "message: #{e.message}"
    puts "error_code: #{e.http_code}"
    puts "http_body: #{e.http_body}"
  end
end
