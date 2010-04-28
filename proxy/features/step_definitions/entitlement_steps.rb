require 'spec/expectations'
require 'candlepin_api'

Before do
  # Map to store named entitlements
  @entitlements = {}
end

Given /^I have an Entitlement named "([^\"]*)" for the "([^\"]*)" Product$/ do |name, product|
  @entitlements[name] = @candlepin.consume_product(product)
end

When /I Consume an Entitlement for the "(\w+)" Product/ do |product|
    @candlepin.consume_product(product)
end

Then /I Have (\d+) Entitlement[s]?/ do |entitlement_size|
    print JSON.pretty_generate(@candlepin.list_products)
    @candlepin.list_entitlements.length.should == entitlement_size.to_i
end

Then /I Have (\d+) Certificate[s]?/ do |certificates_size|
    @candlepin.get_certificates.length.should == certificates_size.to_i
end

Then /^I Have an Entitlement for the "([^\"]*)" Product$/ do |product_id|
    product_ids = @candlepin.list_entitlements.collect do |entitlement|
        entitlement['entitlement']['pool']['productId']
    end

    product_ids.should include(product_id)
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
    response = JSON.parse(e.http_body)
    response['exceptionMessage']['displayMessage'].should == "No such product: non_existent"
    e.message.should == "Bad Request"
    e.http_code.should == 400
  end
end

Then /^The entitlement named "([^\"]*)" should not exist$/ do |name|
  entitlement = @entitlements[name]

  # TODO:  There is probably an official rspec way to do this
  begin
    @candlepin.get_entitlement(entitlement[0]['entitlement']['id'])
  rescue RestClient::Exception => e
    e.http_code.should == 404
  end
end
