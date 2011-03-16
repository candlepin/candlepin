require 'spec/expectations'
require 'candlepin_api'

Given /^I am a consumer "([^\"]*)"$/ do |consumer_name|
  Given "I am a consumer \"#{consumer_name}\" registered by \"#{@username}\""
end

Given /^I am a consumer "([^\"]*)" registered by "([^\"]*)"$/ do |consumer_name, user_name|
  Given "I am logged in as \"#{user_name}\""
  When "I register a consumer \"#{consumer_name}\""
end

Given /^I register a consumer "([^\"]*)" with the following facts:$/ do |consumer_name, facts_table|
  # facts_table is a Cucumber::Ast::Table
  facts = facts_table.rows_hash.delete_if { |key, val| key == 'Name' }
  set_consumer(@current_owner_cp.register(consumer_name, :system, nil, facts))
end

Given /^I am a consumer "([^\"]*)" of type "([^\"]*)" with facts:$/ do |consumer_name, consumer_type, facts_table|
  Given "I am logged in as \"#{@username}\""
  facts = facts_table.rows_hash.delete_if { |key, val| key == 'Name' }
  set_consumer(@current_owner_cp.register(consumer_name, consumer_type, nil, facts))
end


def set_consumer(created_consumer)
    @consumer = created_consumer
    @x509_cert = OpenSSL::X509::Certificate.new(@consumer['idCert']['cert'])
    @cert_extensions = {}
    @x509_cert.extensions.each do |ext|
        @cert_extensions[ext.oid] = ext.value
    end
    @consumer_cp = connect(username=nil, password=nil,
                           cert=@consumer['idCert']['cert'],
                           key=@consumer['idCert']['key'])
    @consumer_cp.consumer = @consumer
    @consumers[created_consumer['name']] = @consumer_cp
end

When /I register a consumer "(\w+)"$/ do |consumer_name|
  set_consumer(@current_owner_cp.register(consumer_name))
end

When /I register a consumer "([^\"]*)" with uuid "([^\"]*)"$/ do |consumer_name, uuid|
  set_consumer(@current_owner_cp.register(consumer_name, :system, uuid))
end

Given /^I have registered a personal consumer with uuid "([^\"]*)"$/ do |uuid|
  set_consumer(@current_owner_cp.register('test', :person, uuid))
end

Given /^I am a consumer "([^\"]*)" of type "([^\"]*)"$/ do |consumer_name, type|
  # This will register with the user you are logged in as
  Given "I am logged in as \"#{@username}\""

  set_consumer(@current_owner_cp.register(consumer_name, type))
end


Given /^Consumer "([^\"]*)" exists with uuid "([^\"]*)"$/ do |consumer_name, uuid|
    When "I register a consumer \"#{consumer_name}\" with uuid \"#{uuid}\""
end

Then /^registering another consumer with uuid "([^\"]*)" causes a bad request$/ do |uuid|

    begin
      @candlepin.register('any name', uuid=uuid)
    rescue RestClient::Exception => e
        e.http_code.should == 400
    else
        assert(fail, "Excepted exception was not raised")
    end

end

Then /^searching for a consumer with uuid "([^\"]*)" causes a not found$/ do |uuid|

    lambda {@candlepin.get_consumer(uuid)}.should raise_error
    begin
        @candlepin.get_consumer(uuid)
    rescue RestClient::Exception => e
        e.http_code.should == 404
    end

end

When /I revoke all my entitlements/ do
    @consumer_cp.revoke_all_entitlements
end
