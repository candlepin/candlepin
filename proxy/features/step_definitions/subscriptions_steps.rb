require 'spec/expectations'
require 'candlepin_api'
require 'openssl'
Before do
  @subscriptions = {}
end
class String
  def to_date
    return Date.strptime(self, "%Y-%m-%d")
  end
end

Given /^owner "([^\"]*)" has a subscription for product "([^\"]*)" with quantity (\d+)$/ do |owner_name, product_name, quantity|
  owner_key = @owners[owner_name]['key']
  product_id = @products[product_name]['id']
  @candlepin.create_subscription(owner_key, product_id, quantity)
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

Then /^I have (\d+) subscriptions$/ do |subscription_size|
    subscriptions = @current_owner_cp.list_subscriptions(@test_owner['key'])
    subscriptions.length.should == subscription_size.to_i
end

# NOTE: product may not exist in the db, which works for now but could cause problems
# later if anything expects it to be there.
def create_subscription(product, quantity)
  
  p = @candlepin.get_product(product.hash.abs)
  created = @candlepin.create_subscription(@test_owner['key'], p['id'], quantity)

  @subscriptions[product] = created
end
