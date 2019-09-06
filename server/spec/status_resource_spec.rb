require 'spec_helper'
require 'candlepin_scenarios'

describe 'Status Resource' do

  include CandlepinMethods

  it 'should return a valid candlepin version and release' do
    json = @cp.get_status()
    json['release'].should_not == "Unknown"
    json['release'].should_not == "${release}"
    json['version'].should_not == "Unknown"
    json['version'].should_not == "${version}"
    json['authRealm'].should_not == "Unknown"
    json['resource'].should_not == "Unknown"
    json['realm'].should_not == "Unknown"

    # Try a cast on the release to be sure, it should be an integer I think:
    release = json['release'].to_i()
  end

end
