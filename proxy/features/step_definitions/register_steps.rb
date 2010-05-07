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

Given /^I am a consumer "([^\"]*)"$/ do |consumer_name|
  # This will register with the user you are logged in as
  Given "I am logged in as \"#{@username}\"" 
  When "I register a consumer \"#{consumer_name}\""
end

When /I register a consumer "(\w+)"$/ do |consumer_name|
    consumer = {
        :consumer => {
            :type => {:label => :system},
            :name => consumer_name,
        }
    }
    @consumer = @owner_admin_cp.register(consumer)
    @x509_cert = OpenSSL::X509::Certificate.new(@consumer['idCert']['cert'])
    @consumer_cp = connect(username=nil, password=nil,
                           cert=@consumer['idCert']['cert'],
                           key=@consumer['idCert']['key'])
    @consumer_cp.consumer = @consumer
end

When /I register a consumer "([^\"]*)" with uuid "([^\"]*)"$/ do |consumer_name, uuid|
    consumer = {
        :consumer => {
            :type => {:label => :system},
            :name => consumer_name,
            :uuid => uuid
        }
    }

    @consumer = @owner_admin_cp.register(consumer)
    @x509_cert = OpenSSL::X509::Certificate.new(@consumer['idCert']['cert'])
    @consumer_cp = connect(username=nil, password=nil,
                           cert=@consumer['idCert']['cert'],
                           key=@consumer['idCert']['key'])
    @consumer_cp.consumer = @consumer
end

Given /^Consumer "([^\"]*)" exists with uuid "([^\"]*)"$/ do |consumer_name, uuid|
    # Again - bad!
    @username = 'foo'
    @password = 'password'
    When "I register a consumer \"#{consumer_name}\" with uuid \"#{uuid}\""
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
    @consumer_cp.revoke_all_entitlements
end

Then /^my consumer should have an identity certificate$/ do
    @consumer['idCert']['cert'][0, 3].should eql('---')
    @consumer['idCert']['key'][0, 3].should eql('---')
end

Then /the (\w+) on my identity certificate's subject is my consumer's UUID/ do |subject_property|
    uuid = @consumer['uuid']
    subject_value(@x509_cert, subject_property).should == uuid
end

Then /the (\w+) on my identity certificate's subject is (\w+)$/ do |subject_property, expected|
    subject_value(@x509_cert, subject_property).should == expected
end

# Grabs the value of a key=value pair in the identity cert's subject
def subject_value(x509_cert, key)
    subject = x509_cert.subject
    subject.to_s.scan(/\/#{key}=([^\/=]+)/)[0][0]
end 
