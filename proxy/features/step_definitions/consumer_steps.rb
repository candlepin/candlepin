


When /^I update my facts to:$/ do |facts_table|
  # facts_table is a Cucumber::Ast::Table
  facts = facts_table.rows_hash.delete_if { |key, val| key == 'Name' }
  @consumer_cp.update_facts(facts)
end

Then /^my fact "([^\"]*)" is "([^\"]*)"$/ do |fact, expected|
  consumer = @consumer_cp.get_consumer
  consumer['facts'][fact].should == expected
end

Then /^I have no fact "([^\"]*)"$/ do |fact|
  consumer = @consumer_cp.get_consumer
  consumer['facts'][fact].should be_nil
end
