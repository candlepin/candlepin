require 'spec/expectations'
require 'candlepin_api'

Before do
  @subscriptions = {}
end

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
  subscription = {
      'startDate' => '2007-07-13',
      'endDate'   => '2012-07-13',
      'quantity'  =>  quantity,
      'productId' => product
  }

  created = @candlepin.create_subscription(@test_owner['id'], subscription)

  @subscriptions[product] = created
end

