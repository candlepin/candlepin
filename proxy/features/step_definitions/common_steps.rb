require 'spec/expectations'
require 'candlepin_api'

Before do
    @candlepin = Candlepin.new
end

Then /My ([\w ]+) exists/ do |property|
    @candlepin.send(to_name(property)).should_not be_nil
end

Then /My ([\w ]+)'s (\w+) is "([^"]+)"/ do |entity, property, expected|
    self.send(to_name(entity))[property].should == expected
end

def to_name(text)
    text.downcase.gsub(/\s/, '_')
end
