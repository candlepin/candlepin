require 'candlepin_scenarios'
require 'candlepin_api'
require 'pp'
require 'base64'

describe 'Rules Import' do

  include CandlepinScenarios
  #include CandlepinMethods

  it 'gets rules' do
    js_rules = @cp.list_rules
  end

  it 'posts rules' do
    rules = 'log.debug("rules loaded");'
    result = @cp.upload_rules(Base64.encode64(rules))
    @cp.delete_rules()
  end

  it "posts and gets rules" do
    rules = "var a=1.0;"
    encoded_rules = Base64.encode64(rules)
    result = @cp.upload_rules(encoded_rules)
    fetched_rules = @cp.list_rules
    decoded_fetched_rules = Base64.decode64(fetched_rules)
    (decoded_fetched_rules == rules).should be_true
  end

  it "deletes rules" do
    rules_orig = @cp.list_rules
    deleted = @cp.delete_rules()
    rules = @cp.list_rules
    @cp.delete_rules
  end

  it "deletes a rule" do
    deleted = @cp.delete_rules()
  end

end
