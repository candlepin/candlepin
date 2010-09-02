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

  it 'contains the proper return value' do
    owner = create_owner 'test_owner'
    result = @cp.refresh_pools(owner.key)

    result.should == 'Pools refreshed for owner test_owner'
  end

  it 'creates the correct number of pools' do
    # ------- Given ---------
    owner = create_owner 'some-owner'
        
    # Create 6 subscriptions to different products
    6.times do |i|
      name = "product-#{i}"
      product = create_product(name, name)

      @cp.create_subscription(owner.key, product.id)
    end

    # -------- When ----------
    @cp.refresh_pools(owner.key)

    # -------- Then ----------
    @cp.list_pools({:owner => owner.id}).length.should == 6
  end

end
