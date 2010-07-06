require 'spec/expectations'
require 'candlepin_api'

When /I perform export/ do 
  @export_filename = @consumer_cp.export_consumer
end

Then /I get an archived extract of data/ do
  File.exist?(@export_filename).should == true
end

After do
  File.delete(@export_filename) if File.exist?(@export_filename)
end
