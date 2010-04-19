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

Then /^I have at least (\d+) subscription token[s]?$/ do |token_size|
    @candlepin.use_credentials(@username, @password)
    tokens = @candlepin.get_subscription_tokens()
    tokens.length.should >= token_size.to_i
end

Given /^I have a subscription token called "([^\"]*)"$/ do |token_name|
    @candlepin.use_credentials(@username, @password)

    token_id = get_token_id(token_name)
    if not token_id
        result = @candlepin.create_subscription(@subscription)
        token = {
            'subscriptionToken' => {'token' => token_name,
                                    'subscription' => result['subscription'] }}
        @candlepin.create_subscription_token(token)
    end
end

Given /^there is no subscription token called "([^\"]*)"$/ do |token_name|
    @candlepin.use_credentials(@username, @password)

    token_id = get_token_id(token_name)
    if token_id
        @candlepin.delete_subscription_token(token_id)
    end
end

Then /^I can create a subscription token "([^\"]*)"$/ do |token_name|
    @candlepin.use_credentials(@username, @password)
    result = @candlepin.create_subscription(@subscription)
    token = {'subscriptionToken' => {'token' => token_name,
                                     'subscription' => result['subscription'] }}
    @candlepin.create_subscription_token(token)
end

Then /^I can delete a subscription token "([^\"]*)"$/ do |token_name|
    @candlepin.use_credentials(@username, @password)
    @candlepin.delete_subscription_token(get_token_id(token_name))
end

def get_token_id(token_name)
    @candlepin.use_credentials(@username, @password)
    tokens = @candlepin.get_subscription_tokens()
    matches = tokens.find_all{|token|
        token['subscriptionToken']['token'] == token_name}

    token_id = nil
    token_id = matches[0]['subscriptionToken']['id'] if matches.length > 0

    return token_id
end
