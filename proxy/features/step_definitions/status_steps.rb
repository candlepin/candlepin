require 'spec/expectations'
require 'candlepin_api'

When /^I visit the status URI$/ do ||
    # Don't really want to do anything here... doing so would mean I need a 
    # global variable to pass the results on to the Then.
end

Then /^status should be known$/ do ||
    json = @candlepin.get_status()
    json['release'].should_not == "Unknown"
    json['release'].should_not == "${hash}"
    json['version'].should_not == "Unknown"
    json['version'].should_not == "${version}"

    # Try a cast on the release to be sure, it should be an integer I think:
    release = json['release'].to_i() 
end

