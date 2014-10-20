require 'spec_helper'
require 'candlepin_scenarios'

require 'rubygems'
require 'rest_client'

describe 'Remove Virt Limit' do
  include CandlepinMethods

  it 'detects changes in virt limits' do
    owner = create_owner random_string
    user = user_client(owner, random_string('virt_user'))
    product = create_product(random_string, random_string,{
      :attributes => {'multi-entitlement' => "yes",
                      :virt_limit => 5}})
    sub = @cp.create_subscription(owner['key'], product.id, 500)
    user = user_client(owner, random_string("user"))
    host = consumer_client(user, 'host', :system, nil)
    guest_uuid = random_string('system.uuid')
    guest = consumer_client(user, 'virt', :system, nil, {
      'virt.uuid' => guest_uuid, 'virt.is_guest' => true
    })

    @cp.refresh_pools(owner['key'], false, false, false)
    pools = @cp.list_pools({:owner => owner.id, :product => product.id})
    if is_hosted?
        pools.length.should == 2
    else
        pools.length.should == 1
        @cp.consume_pool(pools[0]['id'], {:uuid => host.uuid, :quantity => 1})
        @cp.refresh_pools(owner['key'], false, false, false)
        pools = @cp.list_pools({:owner => owner.id, :product => product.id})
        pools.length.should == 2
        host.update_consumer({:guestIds => [{'guestId' => guest_uuid}]})
     end

    for pool in pools
        for att in pool['attributes']
            if att['name'] == "pool_derived"
                @cp.consume_pool(pool['id'], {:uuid => guest.uuid, :quantity => 1})
            end
        end
    end

    attrs = product['attributes']
    for dict in attrs
        if dict['name'] == "virt_limit"
            attrs.delete(dict)
        end
    end

    @cp.update_product(product.id, :attributes => attrs)
    @cp.refresh_pools(owner['key'], false, false, false)
    pools = @cp.list_pools({:owner => owner.id, :product => product.id})
    pools.length.should == 1
  end
end
