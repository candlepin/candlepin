require 'spec_helper'
require 'candlepin_scenarios'

describe 'Virt Only Pools' do
  include CandlepinMethods
  include AttributeHelper

  before(:each) do
    @owner = create_owner random_string('virt_owner')
    @user = user_client(@owner, random_string('virt_user'))
    @owner['autobindHypervisorDisabled'] = true
  end

  it 'should allow virt guests to consume from virt_only pools' do
    virt_product = new_product true
    guest = consumer_client(@user, 'virty', :system, nil, {
      'virt.is_guest' => true
    })

    entitlement = guest.consume_product(virt_product.id)
    entitlement.first.quantity.should == 1
  end

  it 'should allow virt guests to consume from physical pools' do
    physical_product = new_product false
    guest = consumer_client(@user, 'virty', :system, nil, {
      'virt.is_guest' => true
    })

    entitlement = guest.consume_product(physical_product.id)
    entitlement.first.quantity.should == 1
  end

  it 'should deny physical consumers from virt_only pools' do
    virt_product = new_product true
    guest = consumer_client(@user, 'metal', :system, nil, {
      'virt.is_guest' => false
    })

    entitlement = guest.consume_product(virt_product.id)
    entitlement.should == []
  end

  it 'virt_only product should result in virt_only pool' do
    virt_product = new_product true
    guest = consumer_client(@user, 'virty', :system, nil, {
      'virt.is_guest' => true
    })

    entitlement = guest.consume_product(virt_product.id)

    guest_pool = guest.get_pool(entitlement.first.pool.id)

    virt_only = get_attribute_value(guest_pool['attributes'], 'virt_only')
    expect(virt_only).to eq('true')
  end

  it 'should allow virt_only pools to be listed for manifest consumers' do
    virt_product = new_product true
    manifest = consumer_client(@user, 'virty', :candlepin, nil, {})

    pools = manifest.list_pools({:consumer => manifest.uuid})
    pools.size.should == 1

    virt_only = get_attribute_value(pools[0]['attributes'], 'virt_only')
    expect(virt_only).to eq('true')
  end

  private

  def new_product(virt_only)
    product = create_product(nil, nil, {
      :attributes => {
        :virt_only => virt_only
      }
    })

    create_pool_and_subscription(@owner['key'], product.id, 10)

    product
  end
end
