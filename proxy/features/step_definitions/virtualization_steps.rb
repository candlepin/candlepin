Given /^I am a consumer "([^\"]*)" of type "([^\"]*)"$/ do |consumer_name, type|
    # XXX prodbably don't want to hardcode these
    @username = 'Default Org'
    @password = 'Default Org'

    consumer = {
        :consumer => {
            :type => {:label => type},
            :name => consumer_name,
        }
    }

    @candlepin.register(consumer, @username, @password)
end

Then /^attempting to Consume an entitlement for the "([^\"]*)" product is forbidden$/ do |name|
    begin
        @candlepin.consume_product(name)
    rescue RestClient::Exception => e
        e.message.should == "Forbidden"
        e.http_code.should == 403
    else
        assert(fail, "Excepted exception was not raised")
    end
end

