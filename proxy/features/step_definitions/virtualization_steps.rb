Given /^I am a Consumer "([^\"]*)" of type "([^\"]*)"$/ do |consumer_name, type|
    # XXX prodbably don't want to hardcode these
    @username = 'Spacewalk Public Cert'
    @password = 'password'

    consumer = {
        :consumer => {
            :type => {:label => type},
            :name => consumer_name,
        }
    }

    @candlepin.register(consumer, @username, @password)
end

Then /^attempting to Consume an entitlement for the "([^\"]*)" product is forbidden$/ do |name|
    lambda {@candlepin.consume_product}.should raise_error
    begin
        @candlepin.consume_product(name)
    rescue RestClient::Exception => e
        e.message.should == "Forbidden"
        e.http_code.should == 403
    end
end

