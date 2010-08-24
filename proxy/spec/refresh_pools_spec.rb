require 'candlepin_scenarios'
require 'candlepin_api'

require 'rubygems'
require 'rest_client'

describe 'Refresh Pools' do
  include CandlepinMethods
  it_should_behave_like 'Candlepin Scenarios'

  it 'creates a valid job' do
    owner = create_owner 'test_owner'

    status = @cp.refresh_pools(owner.key, true)
    status.state.should == 'CREATED'

    # URI returned is valid - use post to clean up
    @cp.post(status.statusPath).state.should_not be_nil
  end
end
