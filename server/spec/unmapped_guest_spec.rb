require 'spec_helper'
require 'candlepin_scenarios'

# This spec tests virt limited products in a standalone Candlepin deployment.
# (which we assume to be testing against)
describe 'Unmapped Guest Pools' do
  include CandlepinMethods
  include VirtHelper

  before(:each) do
    @owner = create_owner random_string('owner')
    @user = user_client(@owner, random_string('user'))

    @virt_limit_product = create_product(nil, nil, {
      :attributes => {
        :virt_limit => 'unlimited',
        :host_limited => 'true',
        :physical_only => 'true'
      }
    })
    @sub1 = @cp.create_subscription(@owner['key'],@virt_limit_product.id, 10)
    @cp.refresh_pools(@owner['key'])

    # should only be the base pool and the bonus pool for unmapped guests
    @pools = @user.list_pools :owner => @owner.id, :product => @virt_limit_product.id
    @pools.size.should == 2

    # we'll need a guest for this
    @uuid1 = random_string('system.uuid')
    @guest1 = @user.register(random_string('guest'), :system, nil,
      {'virt.uuid' => @uuid1, 'virt.is_guest' => 'true', 'uname.machine' => 'x86_64'}, nil, nil, [], [])
    @guest1_client = Candlepin.new(nil, nil, @guest1['idCert']['cert'], @guest1['idCert']['key'])
    @host1 = @user.register(random_string('host'), :system)
    @host1_client = Candlepin.new(nil, nil, @host1['idCert']['cert'], @host1['idCert']['key'])

  end

  it 'allows a new guest with no host to attach to an unmapped guest pool' do
    all_pools = @user.list_pools :owner => @owner.id, :product => @virt_limit_product.id
    for pool in all_pools
        unmapped = pool['attributes'].find_all {|i| i['name'] == 'unmapped_guests_only' }[0]
        if unmapped and unmapped['value'] == 'true'
            @guest1_client.consume_pool(pool['id'], {:quantity => 1})
        end
    end
    ents = @guest1_client.list_entitlements()
    ents.should have(1).things
  end

  it 'does not allow a new guest with a host to attach to an unmapped guest pool' do
    @host1_client.update_consumer({:guestIds => [{'guestId' => @uuid1}]});
    all_pools = @user.list_pools :owner => @owner.id, :product => @virt_limit_product.id
    for pool in all_pools
        unmapped = pool['attributes'].find_all {|i| i['name'] == 'unmapped_guests_only' }[0]
        if unmapped and unmapped['value'] == 'true'
            lambda do
                @guest1_client.consume_pool(pool['id'], {:quantity => 1})
            end.should raise_exception(RestClient::Forbidden)
        end
    end
  end

  it 'ensures unmapped guest will attach to unmapped guest pool on auto attach' do
    all_pools = @user.list_pools :owner => @owner.id, :product => @virt_limit_product.id
    for pool in all_pools
        unmapped = pool['attributes'].find_all {|i| i['name'] == 'unmapped_guests_only' }[0]
        if !unmapped or unmapped['value'] != 'true'
            @host1_client.consume_pool(pool['id'], {:quantity => 1})
        end
    end
    @cp.refresh_pools(@owner['key'])   

    # should be the base pool, the bonus pool for unmapped guests, plus a pool for the host's guests
    @pools = @user.list_pools :owner => @owner.id, :product => @virt_limit_product.id
    @pools.size.should == 3

    @guest1_client.consume_product(@virt_limit_product.id)
    ents = @guest1_client.list_entitlements()
    ents.should have(1).things

    bound_pool = ents[0].pool
    bound_pool['attributes'].find_all {|i| i['name'] == 'unmapped_guests_only' }[0].should_not be nil
  end

end
