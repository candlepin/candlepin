Then /^attempting to Consume an entitlement for the "([^\"]*)" product is forbidden$/ do |name|
    begin
        @consumer_cp.consume_product(name)
    rescue RestClient::Exception => e
        e.message.should == "Forbidden"
        e.http_code.should == 403
    else
        assert(fail, "Excepted exception was not raised")
    end
end

