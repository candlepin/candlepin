require 'spec/expectations'
require 'candlepin_api'

Before do
    @subscription = {
        'subscription' => {'startDate' => '2007-07-13T00:00:00-04:00',
                           'endDate'   => '2010-07-13T00:00:00-04:00',
                           'quantity'  =>  37,
                           'productId' => 'provisioning'
        }
    }
end

Given /^owner "([^\"]*)" has (\d+) entitlements for "([^\"]*)"$/ do |owner, quantity, product|
  subscription = {
    'subscription' => {'startDate' => '2007-07-13T00:00:00-04:00',
      'endDate'   => '2012-07-13T00:00:00-04:00',
      'quantity'  =>  quantity,
      'productId' => product
    }
  }

  @candlepin.create_subscription(subscription)
end

Then /^I have at least (\d+) subscriptions$/ do |subscription_size|
    @candlepin.use_credentials(@username, @password)
    subscriptions = @candlepin.get_subscriptions()
    subscriptions.length.should >= subscription_size.to_i
end

Then /^I can delete a subscription$/ do
    @candlepin.use_credentials(@username, @password)
    result = @candlepin.create_subscription(@subscription)
    @candlepin.delete_subscription(result['subscription']['id'])
end

Then /^I can create a new subscription$/ do
    @candlepin.use_credentials(@username, @password)
    result = @candlepin.create_subscription(@subscription)
end

