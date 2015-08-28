require 'spec_helper'
require 'candlepin_scenarios'
require 'candlepin_api'
require 'pp'
require 'base64'

describe 'Rules Import', :serial => true do

  before(:each) do
    # Make sure we're using the rpm rules by deleting any custom
    # ones that may have been left in the database:
    @cp.delete_rules

    @orig_ver = @cp.get_status()['rulesVersion']
    rules_major_ver = @orig_ver.split(".")[0]
    # Come up with a rules version we know is greater than the current:
    @new_ver = "#{rules_major_ver}.10000"

    # Dummy rules we can upload:
    @rules = "//Version: #{@new_ver}\nvar a=1.0;"
  end

  after(:each) do
    # Revert to rpm rules:
    @cp.delete_rules
  end

  it 'gets rules' do
    js_rules = @cp.list_rules
  end

  def upload_dummy_rules
    # need to ensure a different timestamp in mysql
    sleep 2
    encoded_rules = Base64.encode64(@rules)
    result = @cp.upload_rules(encoded_rules)
    fetched_rules = @cp.list_rules
    decoded_fetched_rules = Base64.decode64(fetched_rules)
    (decoded_fetched_rules == @rules).should be_true
    @cp.get_status()['rulesVersion'].should == @new_ver
  end

  it "posts and gets rules" do
    upload_dummy_rules
  end

  it "deletes rules" do
    upload_dummy_rules

    deleted = @cp.delete_rules()
    rules = @cp.list_rules

    # Version should be back to original:
    @cp.get_status()['rulesVersion'].should == @orig_ver

    # Shouldn't cause an error if there are none in db:
    @cp.delete_rules
  end

end
