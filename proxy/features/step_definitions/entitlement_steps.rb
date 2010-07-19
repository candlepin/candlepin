require 'spec/expectations'

When /^consumer "([^\"]*)" consumes an entitlement for product "([^\"]*)"$/ do |consumer_name, product_name|
  product_id = @products[product_name]['id']
  @consumer_clients[consumer_name].consume_product(product_id)
end

# Deprecated - let's stop using 'I'
Before do
  # Map to store named entitlements
  @entitlements = {}
  @consume_exception = nil
  @pool_id = nil
end

Given /^I have an entitlement named "([^\"]*)" for the "([^\"]*)" product$/ do |name, product|
  @entitlements[name] = @consumer_cp.consume_product(product.hash.abs)
end

When /^I consume an entitlement for the "([^\"]*)" product$/ do |product|
  @consumer_cp.consume_product(product.hash.abs)
end

When /^I consume an entitlement for the "([^\"]*)" product with a quantity of (\d+)$/ do |product, quantity|
  @consumer_cp.consume_product(product.hash.abs, quantity)
end

Then /I have (\d+) entitlements? with a quantity of (\d+)/ do |entitlement_size, quantity|
  entitlements = @consumer_cp.list_entitlements

  entitlements.select { |ent| ent['quantity'] == quantity.to_i }.length.should == entitlement_size.to_i
end

Then /^I have (\d+) entitlement[s]?$/ do |entitlement_size|
    @consumer_cp.list_entitlements.length.should == entitlement_size.to_i
end

Then /^I have (\d+) certificate[s]?$/ do |certificates_size|
    @consumer_cp.get_certificates.length.should == certificates_size.to_i
end

Then /^I have (\d+) certificate serial number$/ do |serials_size|
    @consumer_cp.get_certificate_serials.length.should == serials_size.to_i
end

Then /^I have an entitlement for the "([^\"]*)" product$/ do |product_id|
    product_ids = @consumer_cp.list_entitlements.collect do |entitlement|
        entitlement['pool']['productId']
    end

    product_ids.should include(product_id.hash.abs.to_s)
end

# need a test for a Pool created with a productid that doesn't exist...

When /I consume an entitlement for the "([^\"]*)" pool$/ do |pool|
  all_pools = @consumer_cp.get_pools({:consumer => @consumer_cp.consumer['uuid']})
 
  product_pools = all_pools.select {|p| p.has_value?(pool)}
  product_pools.empty?.should == false

  # needed for trying to consume the same pool twice
  @pool_id = product_pools[0]['id']
  results = @consumer_cp.consume_pool(@pool_id)
end

Then /^I get (\d+) entitlements? when I filter by product ID "([^\"]*)"$/ do |entitlement_size, product_id|
  @consumer_cp.list_entitlements(product_id.hash.abs).length.should == entitlement_size.to_i
end

Then /^I get an exception if I filter by product ID "(\w+)"$/ do |product_id|
  begin
    @consumer_cp.list_entitlements(product_id)
  rescue RestClient::Exception => e
    response = JSON.parse(e.http_body)
    response['displayMessage'].should == "No such product: non_existent"
    e.message.should == "Bad Request"
    e.http_code.should == 400
  end
end

Then /^The entitlement named "([^\"]*)" should not exist$/ do |name|
  entitlement = @entitlements[name]

  # TODO:  There is probably an official rspec way to do this
  begin
    @candlepin.get_entitlement(entitlement[0]['id'])
  rescue RestClient::Exception => e
    e.http_code.should == 404
  end
end

When /^I try to consume an entitlement for the "([^\"]*)" product again$/ do |product|

  begin
    @consumer_cp.consume_product(product.hash.abs)
  rescue RestClient::Exception => e
      @consume_exception = e
  end
end

When /^I try to consume an entitlement for the "([^\"]*)" pool again$/ do |pool|
  begin
    @consumer_cp.consume_pool(@pool_id)
  rescue RestClient::Exception => e
      @consume_exception = e
  end
end

Then /^I recieve an http forbidden response$/ do
    @consume_exception.http_code.should == 403
end

When /^consumer "([^\"]*)" binds by token "([^\"]*)"$/ do |consumer_name, token|
    @consumers[consumer_name].consume_token(token)
end
