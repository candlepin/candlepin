require 'spec/expectations'

Then /^consumer "([^\"]*)" has access to a pool for product "([^\"]*)"$/ do |consumer_name, product_name|
  consumer = @consumer_clients[consumer_name]

  pools = consumer.list_pools(:consumer => consumer.uuid)
  products = pools.collect { |pool| pool['productName'] }

  products.should include(product_name)
end

# Deprecated - Get rid of the 'I's
Before do
    @found_pools = []
end

Given /^I have a pool of quantity (\d+) for "([^\"]*)"$/ do |quantity, product|
  p = @candlepin.get_product(product.hash.abs)
  @candlepin.create_pool(p['id'], @test_owner['id'], nil)
end

Given /^I have a pool of quantity (\d+) for "([^\"]*)" restricted to user "([^\"]*)"$/ do |quantity, product, user|
  p = @candlepin.get_product(product.hash.abs)
  @candlepin.create_pool(p['id'], @test_owner['id'], nil, {}, nil, nil, quantity, user)
end

Given /^I have a pool of quantity (\d+) for "([^\"]*)" with the following attributes:$/ do |quantity, product, table|
  p = @candlepin.get_product(product.hash.abs)
  attrs = table.rows_hash.delete_if { |key, val| key == 'Name' }

  @candlepin.create_pool(p['id'], @test_owner['id'], nil, attrs, nil, nil, quantity)
end

When /^I view all of my pools$/ do
  @found_pools = @consumer_cp.list_pools(:consumer => @consumer_cp.uuid,
                                        :listall => true)
end

When /^I view all pools for my owner$/ do
  @found_pools = @candlepin.list_pools(:owner => @test_owner['id'])
end

Then /^I have access to a pool for product "([^\"]*)"$/ do |product_name|
  pools = @consumer_cp.list_pools(:consumer => @consumer_cp.uuid)
  products = pools.collect { |pool| pool['productName'] }

  products.should include(product_name)
end

Then /^I do not have access to a pool for product "([^\"]*)"$/ do |product_name|
  pools = @consumer_cp.list_pools(:consumer => @consumer_cp.uuid)
  products = pools.collect { |pool| pool['productName'] }

  products.should_not include(product_name)
end

Then /^I have access to (\d*) pools?$/ do |num_pools|
  pools = @consumer_cp.list_pools(:consumer => @consumer_cp.uuid)

  pools.length.should == num_pools.to_i
end

Then /^I see (\d+) pools?$/ do |num_pools|
  @found_pools.length.should == num_pools.to_i
end

Then /^I see (\d*) available entitlements$/ do |num_entitlements|
  available = @found_pools.inject(0) do |sum, pool|
    sum += pool['quantity'] - pool['consumed']
  end

  available.should == num_entitlements.to_i
end

Given /^test owner has no pools for "([^\"]*)"$/ do |productid|
  pools = @candlepin.list_pools({:owner => @test_owner['id'], :product => productid.hash.abs})
  pools.length.should == 0
end

Then /^test owner has (\d+) pool for "([^\"]*)"$/ do |count, productid|
  pools = @candlepin.list_pools({:owner => @test_owner['id'], :product => productid.hash.abs})
  pools.length.should == count.to_i
end
