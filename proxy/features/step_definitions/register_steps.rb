require 'spec/expectations'
require 'candlepin_api'

Given /^I have username "(\w+)"$/ do |username|
    # Not using username right now
    @username = username
end

Given /^I have password "(\w+)"$/ do |password|
    # Not using password right now
    @password = password
end

Given /^I am a Consumer "([^\"]*)"$/ do |consumer_name|
  # Just hardcodes in username/password so that
  # all the features don't have to spell it out

  # Currently this lines up with the default user that is defined
  # in common_steps

  When "I register a consumer \"#{consumer_name}\""
end

Given /^I am user "([^\"]*)" with password "([^\"]*)"$/ do |username, password|
  @candlepin.use_credentials(username, password)
end

When /^I become user "([^\"]*)" with password "([^\"]*)"$/ do |username, password|
  @candlepin.use_credentials(username, password)
end

When /I register a consumer "(\w+)"$/ do |consumer_name|
    consumer = {
        :consumer => {
            :type => {:label => :system},
            :name => consumer_name,
        }
    }
    @owner_admin_cp.register(consumer)
end

Given /^there is no consumer with uuid "([^\"]*)"$/ do |uuid|
    begin
        @owner_admin_cp.unregister(uuid)
    rescue RestClient::Exception => e
        # If it doesn't exist already, then we don't care if the unregister
        # failed
        e.message.should == "Resource Not Found"
        e.http_code.should == 404
    end
end

Given /^Consumer "([^\"]*)" exists with uuid "([^\"]*)"$/ do |consumer_name, uuid|
    # Again - bad!
    @username = 'foo'
    @password = 'password'
    Given "there is no consumer with uuid \"#{uuid}\""
    When "I register a consumer \"#{consumer_name}\" with uuid \"#{uuid}\""
end

When /I register a consumer "([^\"]*)" with uuid "([^\"]*)"$/ do |consumer_name, uuid|
    consumer = {
        :consumer => {
            :type => {:label => :system},
            :name => consumer_name,
            :uuid => uuid
        }
    }

    @owner_admin_cp.register(consumer)
end

Then /^Registering another Consumer with uuid "([^\"]*)" causes a bad request$/ do |uuid|
    consumer = {
        :consumer => {
            :type => {:label => :system},
            :name => "any name",
            :uuid => uuid
        }
    }

    begin
        @candlepin.register(consumer, @username, @password)
    rescue RestClient::Exception => e
        e.message.should == "Bad Request"
        e.http_code.should == 400
    else
        assert(fail, "Excepted exception was not raised")
    end

end

Then /^Searching for a Consumer with uuid "([^\"]*)" causes a not found$/ do |uuid|

    lambda {@candlepin.get_consumer(uuid)}.should raise_error
    begin
        @candlepin.get_consumer(uuid)
    rescue RestClient::Exception => e
        e.message.should == "Resource Not Found"
        e.http_code.should == 404
    end

end

When /I Revoke All My Entitlements/ do
    @candlepin.revoke_all_entitlements
end

Then /^my consumer should have an identity certificate$/ do
      @owner_admin_cp.send('identity_certificate').should_not be_nil
end

Then /the (\w+) on my identity certificate's subject is my ([\w ]+)'s (\w+)/ do |subject_property, entity, property|
    expected = @owner_admin_cp.send(to_name(entity))[ to_name(property) ]
    subject_value(subject_property).should == expected
end

Then /the (\w+) on my identity certificate's subject is (\w+)$/ do |subject_property, expected|
    subject_value(subject_property).should == expected
end

# Grabs the value of a key=value pair in the identity cert's subject
def subject_value(key)
    subject = @owner_admin_cp.identity_certificate.subject
    subject.to_s.scan(/\/#{key}=([^\/=]+)/)[0][0]
end 
