require 'spec_helper'
require 'canadianTenPin_scenarios'

describe 'Status Resource' do

  include CanadianTenPinMethods

  it 'should return a valid canadianTenPin version and release' do
    json = @cp.get_status()
    json['release'].should_not == "Unknown"
    json['release'].should_not == "${hash}"
    json['version'].should_not == "Unknown"
    json['version'].should_not == "${version}"

    # Try a cast on the release to be sure, it should be an integer I think:
    release = json['release'].to_i()
  end

end
