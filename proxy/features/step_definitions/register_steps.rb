require 'spec/expectations'
require 'candlepin_api'

Given /I have username (\w+)/ do |username|
    # Not using username right now
    @username = username
end

Given /I have password (\w+)/ do |password|
    # Not using password right now
    @password = password
end

Given /I am a Consumer (\w+)/ do |consumer_name|
    # Just hardcodes in username/password so that
    # all the features don't have to spell it out

    @username = 'Dude'
    @password = 'password'
    When "I Register a New Consumer #{consumer_name}"
end

When /I Register a New Consumer (\w+)/ do |consumer_name|
    consumer = {
        :consumer => {
            :type => {:label => :system},
            :name => consumer_name,
        }
    }

    @candlepin.register(consumer, @username, @password)
end

When /I Revoke All My Entitlements/ do
    @candlepin.revoke_all_entitlements
end

Then /The (\w+) on my Identity Certificate's Subject is My ([\w ]+)'s (\w+)/ do |subject_property, entity, property|
    expected = @candlepin.send(to_name(entity))[ to_name(property) ]
    subject_value(subject_property).should == expected
end

Then /The (\w+) on my Identity Certificate's Subject is (\w+)$/ do |subject_property, expected|
    subject_value(subject_property).should == expected
end

# Grabs the value of a key=value pair in the identity cert's subject
def subject_value(key)
    subject = @candlepin.identity_certificate.subject
    subject.to_s.scan(/\/#{key}=([^\/=]+)/)[0][0]
end 
