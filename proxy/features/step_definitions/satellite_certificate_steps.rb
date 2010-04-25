require 'spec/expectations'
require 'candlepin_api'

Given /^I have imported a Satellite Certificate$/ do
    # XXX no-op for now, assumed deploy has loaded the certificate
end

Then /^I should have at least (\d+) products available$/ do |pools|
    @candlepin.get_pools.length.should >= pools.to_i
end

Then /^importing the Satelite Certificate again should cause a bad request$/ do
    certificate = File.new("code/scripts/spacewalk-public.cert", "r").read
    begin
        @candlepin.upload_satellite_certificate(certificate)
    rescue RestClient::Exception => e
        e.message.should == "Bad Request"
        e.http_code.should == 400
    else
        assert(fail, "Excepted exception was not raised")
    end
end

