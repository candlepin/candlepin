
Then /^I see a "([^\"]*)" event$/ do |event|
  events = @candlepin.list_consumer_events(@consumer_cp.uuid)

  # Punting on this for now...
  events.should include event
end

Then /^I do not see a "([^\"]*)" event$/ do |event|
  events = @candlepin.list_consumer_events(@consumer_cp.uuid)

  # Punting on this for now...
  events.should_not include event
end
