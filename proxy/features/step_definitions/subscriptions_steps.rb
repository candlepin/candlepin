require 'spec/expectations'
require 'candlepin_api'

Before do
  @subscriptions = {}
end


Given /^owner "([^\"]*)" has a subscription for product "([^\"]*)" with quantity (\d+)$/ do |owner_name, product_name, quantity|
  owner_id = @owners[owner_name]['id']
  product_id = @products[product_name]['id']
  @candlepin.create_subscription(owner_id, product_id, quantity)

  # Just refesh the entitlement pools each time
  @candlepin.refresh_pools(@owners[owner_name]['key'])
end

# Deprecated - use named owners instead of @test_owner

Given /^test owner has (\d+) entitlements for "([^\"]*)"$/ do |quantity, product|
  create_subscription(product, quantity)

  # Just refesh the entitlement pools each time
  @candlepin.refresh_pools(@test_owner['key'])
end

Given /^test owner has a subscription for "([^\"]*)" with quantity (\d+) and token "([^\"]*)"$/ do |product, quantity, token|
    sub = create_subscription(product, quantity)

    token_hash = {
        'token' => token,
        # XXX HACK
        'subscription' => {:id => sub['id']}
    }
    @candlepin.create_subscription_token(token_hash)

  # NOTE: do not refresh pools here, we just want a subscription created.
end

When /^I delete the subscription for product "([^\"]*)"$/ do |product|
  subscription_id = @subscriptions[product]['id']
  
  @candlepin.delete_subscription(subscription_id)
end

Then /^I have (\d+) subscriptions$/ do |subscription_size|
    subscriptions = @current_owner_cp.get_subscriptions(@test_owner['id'])
    subscriptions.length.should == subscription_size.to_i
end

# NOTE: product may not exist in the db, which works for now but could cause problems
# later if anything expects it to be there.
def create_subscription(product, quantity)
  
  p = @candlepin.get_product(product.hash.abs)
  created = @candlepin.create_subscription(@test_owner['id'], p['id'], quantity)

  @subscriptions[product] = created
end

When /^test owner changes the "([^\"]*)" of the subscription by (-{0,1}\d+) days$/ do |field, d|
  subscription = @current_owner_cp.get_subscriptions(@test_owner['id'])[0]
  subscription[field] = Date.strptime(subscription[field], "%Y-%m-%d") + d.to_i
  @candlepin.update_subscription(@test_owner['id'], subscription)
end

When /^he refreshes the pools$/ do
  @old_certs = @consumer_cp.get_certificates()
  @candlepin.refresh_pools(@test_owner['key'])
end

When /^test owner changes the quantity of the subscription by (-{0,1}\d+)$/ do |q|
 subscription = @current_owner_cp.get_subscriptions(@test_owner['id'])[0]
 subscription['quantity'] = subscription['quantity'].to_i + q.to_i
 @candlepin.update_subscription(@test_owner['id'], subscription)
end
