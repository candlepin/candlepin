require 'candlepin_scenarios'
require 'candlepin_api'

require 'rubygems'
require 'rest_client'

describe 'Refresh Pools' do
  include CandlepinMethods
  it_should_behave_like 'Candlepin Scenarios'

  it 'creates a valid job' do
    owner = create_owner random_string('test_owner')

    status = @cp.refresh_pools(owner.key, true)
    status.state.should == 'CREATED'

    # URI returned is valid - use post to clean up
    @cp.post(status.statusPath).state.should_not be_nil
  end

  it 'contains the proper return value' do
    test_owner = random_string('test_owner')
    owner = create_owner test_owner
    result = @cp.refresh_pools(owner.key)

    result.should == "Pools refreshed for owner #{test_owner}"
  end

  it 'creates the correct number of pools' do
    # ------- Given ---------
    owner = create_owner 'some-owner'
        
    # Create 6 subscriptions to different products
    6.times do |i|
      name = random_string("product-#{i}")
      product = create_product(name, name)

      @cp.create_subscription(owner.key, product.id)
    end

    # -------- When ----------
    @cp.refresh_pools(owner.key)

    # -------- Then ----------
    @cp.list_pools({:owner => owner.id}).length.should == 6
  end

  it 'detects changes in provided products' do
    owner = create_owner random_string
    product = create_product(random_string, random_string)
    provided1 = create_product(random_string, random_string)
    provided2 = create_product(random_string, random_string)
    provided3 = create_product(random_string, random_string)
    sub = @cp.create_subscription(owner.key, product.id, 500,
      [provided1.id, provided2.id])
    @cp.refresh_pools(owner.key)
    pools = @cp.list_pools({:owner => owner.id})
    pools.length.should == 1
    pools[0].providedProducts.length.should == 2

    # Remove the old provided products and add a new one:
    sub.providedProducts = [@cp.get_product(provided3.id)]
    @cp.update_subscription(sub)
    sub2 = @cp.get_subscription(sub.id)
    @cp.refresh_pools(owner.key)
    pools = @cp.list_pools({:owner => owner.id})
    pools[0].providedProducts.length.should == 1
  end

end
