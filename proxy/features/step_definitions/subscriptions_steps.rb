require 'spec/expectations'
require 'candlepin_api'

Before do
  @subscriptions = {}
end

Given /^owner "([^\"]*)" has (\d+) entitlements for "([^\"]*)"$/ do |owner, quantity, product|
  create_subscription(product, quantity)

  # Just refesh the entitlement pools each time
  @candlepin.refresh_pools(@test_owner['key'])
end

When /^I delete the subscription for product "([^\"]*)"$/ do |product|
  subscription_id = @subscriptions[product]['subscription']['id']
  
  @candlepin.delete_subscription(subscription_id)
end

Then /^I have (\d+) subscriptions$/ do |subscription_size|
    subscriptions = @candlepin.get_subscriptions()
    subscriptions.length.should == subscription_size.to_i
end

def create_subscription(product, quantity)
  subscription = {
    'subscription' => {'startDate' => '2007-07-13T00:00:00-04:00',
      'endDate'   => '2012-07-13T00:00:00-04:00',
      'quantity'  =>  quantity,
      'productId' => product
    }
  }

  # TODO:  Actually use the owner!
  created = @candlepin.create_subscription(@test_owner['id'], subscription)

  @subscriptions[product] = created
end

