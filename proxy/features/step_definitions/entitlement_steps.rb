require 'spec/expectations'
require 'candlepin_api'

Before do
  # Map to store named entitlements
  @entitlements = {}
  @consume_exception = nil
  @pool_id = nil
end

Given /^I have an entitlement named "([^\"]*)" for the "([^\"]*)" product$/ do |name, product|
  @entitlements[name] = @consumer_cp.consume_product(product)
end

When /I Consume an Entitlement for the "([^\"]*)" Product/ do |product|
    @consumer_cp.consume_product(product)
end

Then /I Have (\d+) Entitlement[s]?/ do |entitlement_size|
    @consumer_cp.list_entitlements.length.should == entitlement_size.to_i
end

Then /I Have (\d+) Certificate[s]?/ do |certificates_size|
    @consumer_cp.get_certificates.length.should == certificates_size.to_i
end

Then /^I Have an Entitlement for the "([^\"]*)" Product$/ do |product_id|
    product_ids = @consumer_cp.list_entitlements.collect do |entitlement|
        entitlement['entitlement']['pool']['productId']
    end

    product_ids.should include(product_id)
end

# need a test for a Pool created with a productid that doesn't exist...

When /I Consume an Entitlement for the "([^\"]*)" Pool$/ do |pool|
  all_pools = @consumer_cp.get_pools({:consumer => @consumer_cp.consumer['uuid']})
 
  product_pools = all_pools.select {|p| p['pool'].has_value?(pool)}
  product_pools.empty?.should == false

  # needed for trying to consume the same pool twice
  @pool_id = product_pools[0]['pool']['id']
  results = @consumer_cp.consume_pool(@pool_id)
end

Then /^I Get (\d+) Entitlement When I Filter by Product ID "([^\"]*)"$/ do |entitlement_size, product_id|
  @consumer_cp.list_entitlements(product_id).length.should == entitlement_size.to_i
end

Then /^I Get an Exception If I Filter by Product ID "(\w+)"$/ do |product_id|
  begin
    @consumer_cp.list_entitlements(product_id)
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

When /^I try to consume an Entitlement for the "([^\"]*)" Product again$/ do |product|

  begin
    @consumer_cp.consume_product(product)
  rescue RestClient::Exception => e
      @consume_exception = e
  end
end

When /^I try to consume an Entitlement for the "([^\"]*)" Pool again$/ do |pool|
  begin
    @consumer_cp.consume_pool(@pool_id)
  rescue RestClient::Exception => e
      @consume_exception = e
  end
end

Then /^I recieve an http forbidden response$/ do
    @consume_exception.http_code.should == 403
end

