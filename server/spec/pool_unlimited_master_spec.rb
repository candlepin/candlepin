require 'spec_helper'
require 'candlepin_scenarios'

describe 'Unlimited Master Pools' do
  include CandlepinMethods
  include SpecUtils
  include CertificateMethods
  include AttributeHelper

  before(:each) do
    @owner = create_owner random_string('owner')
    @user = user_client(@owner, random_string('user'))

    @uuid = random_string('system.uuid')

    @physical_sys = @user.register(random_string('host'), :system)
    @physical_client = Candlepin.new(nil, nil, @physical_sys['idCert']['cert'], @physical_sys['idCert']['key'])
    @physical_client.update_consumer({:guestIds => [{'guestId' => @uuid}]})

    @guest = @user.register(random_string('guest'), :system, nil, {'virt.uuid' => @uuid, 'virt.is_guest' => 'true'})
    @guest_client = Candlepin.new(nil, nil, @guest['idCert']['cert'], @guest['idCert']['key'])

    @guest_unmapped = @user.register(random_string('guest'), :system, nil, {'virt.is_guest' => 'true'})
    @guest_client_unmapped = Candlepin.new(nil, nil, @guest_unmapped['idCert']['cert'], @guest_unmapped['idCert']['key'])

    @product_no_virt = create_product(nil, nil, {
      :attributes => {
        'multi-entitlement' => "yes"
      }
    })

    @product_unlimited_virt = create_product(nil, nil, {
      :attributes => {
        :virt_limit => "unlimited",
        'multi-entitlement' => "yes"
      }
    })

    @product_virt = create_product(nil, nil, {
      :attributes => {
        :virt_limit => "5",
        'multi-entitlement' => "yes"
      }
    })

    @product_virt_host_dep = create_product(nil, nil, {
      :attributes => {
        :virt_limit => "5",
        'multi-entitlement' => "yes",
        'host-dependent' => 'true'
      }
    })

    @product_virt_instance_multiplier = create_product(nil, nil, {
      :attributes => {
        :virt_limit => "8",
        'multi-entitlement' => "yes",
        "instance_multiplier" => 6
      }
    })

    @product_virt_product_multiplier = create_product(nil, nil, {
      :attributes => {
        'multi-entitlement' => "yes",
        :virt_limit => "8",
      },
      :multiplier => 100
    })

    @pool_no_virt = create_pool_and_subscription(@owner['key'], @product_no_virt.id, -1, [], '', '', '', nil, nil, false)
    @pool_unlimited_virt = create_pool_and_subscription(@owner['key'], @product_unlimited_virt.id, -1, [], '', '', '', nil, nil, false)
    @pool_virt = create_pool_and_subscription(@owner['key'], @product_virt.id, -1, [], '', '', '', nil, nil, false)
    @pool_virt_host_dep = create_pool_and_subscription(@owner['key'], @product_virt_host_dep.id, -1, [], '', '', '', nil, nil, false)
    @pool_virt_product_multiplier = create_pool_and_subscription(@owner['key'], @product_virt_product_multiplier.id, -1, [], '', '', '', nil, nil, false)
    @pool_virt_instance_multiplier = create_pool_and_subscription(@owner['key'], @product_virt_instance_multiplier.id, -1, [], '', '', '', nil, nil, false)

    @pools = @cp.list_pools :owner => @owner.id, :product => @product_unlimited_virt.id
    @pools.size.should == 2
    @pools = @cp.list_pools :owner => @owner.id, :product => @product_no_virt.id
    @pools.size.should == 1
    @pools = @cp.list_pools :owner => @owner.id, :product => @product_virt.id
    @pools.size.should == 2
    @pools = @cp.list_pools :owner => @owner.id, :product => @product_virt_host_dep.id
    @pools.size.should == 2
    @pools = @cp.list_pools :owner => @owner.id, :product => @product_virt_product_multiplier.id
    @pools.size.should == 2
    @pools = @cp.list_pools :owner => @owner.id, :product => @product_virt_instance_multiplier.id
    @pools.size.should == 2
  end

  it 'allows system to consume unlimited quantity pool' do
    @physical_client.consume_pool(@pool_no_virt['id'], {:quantity => 300})
    ents = @physical_client.list_entitlements
    ents.size.should == 1
    ents[0].quantity.should == 300
  end

 it 'allows system to consume limited virt quantity pool' do
    skip("candlepin running in hosted mode") if is_hosted?
    @pools = @cp.list_pools :owner => @owner.id, :product => @product_virt.id
    guest_pool = nil
    @pools.each do |pool|
        if get_attribute_value(pool['attributes'], "pool_derived") == "true" and pool.type == 'UNMAPPED_GUEST'
          guest_pool = pool
        end
    end
    @guest_client_unmapped.consume_pool(guest_pool['id'], {:uuid => @guest_unmapped.uuid, :quantity => 4000})
    ents = @guest_client_unmapped.list_entitlements
    ents.size.should == 1
    ents[0].quantity.should == 4000
 end

 it 'allows mapped guest to consume unlimited quantity pool' do
    skip("candlepin running in hosted mode") if is_hosted?
    @physical_client.consume_pool(@pool_unlimited_virt['id'], {:quantity => 300})
    @pools = @cp.list_pools :owner => @owner.id, :product => @product_unlimited_virt.id
    guest_pool = nil
    @pools.each do |pool|
        if get_attribute_value(pool['attributes'], "pool_derived") == "true" and pool.type == 'ENTITLEMENT_DERIVED'
           guest_pool = pool
        end
    end
    @guest_client.consume_pool(guest_pool['id'], {:uuid => @guest.uuid, :quantity => 5000})
    ents = @guest_client.list_entitlements
    ents.size.should == 1
    ents[0].quantity.should == 5000
  end

 it 'allows mapped guest to consume limited host dependendent pool' do
    skip("candlepin running in hosted mode") if is_hosted?
    @physical_client.consume_pool(@pool_virt_host_dep['id'], {:quantity => 300})
    @pools = @cp.list_pools :owner => @owner.id, :product => @product_virt_host_dep.id
    guest_pool = nil
    @pools.each do |pool|
        if get_attribute_value(pool['attributes'], "pool_derived") == "true" and pool.type == 'ENTITLEMENT_DERIVED'
           pool.quantity.should == 5
           guest_pool = pool
        end
    end
    @guest_client.consume_pool(guest_pool['id'], {:uuid => @guest.uuid, :quantity => 4})
    ents = @guest_client.list_entitlements
    ents.size.should == 1
    ents[0].quantity.should == 4
  end

 it 'allows unmapped guest to consume unlimited quantity pool' do
    skip("candlepin running in hosted mode") if is_hosted?
    @pools = @cp.list_pools :owner => @owner.id, :product => @product_unlimited_virt.id
    guest_pool = nil
    @pools.each do |pool|
        if get_attribute_value(pool['attributes'], "pool_derived") == "true" and pool.type == 'UNMAPPED_GUEST'
           guest_pool = pool
        end
    end
    
    guest_pool.should_not be nil
    @guest_client_unmapped.consume_pool(guest_pool['id'], {:uuid => @guest_unmapped.uuid, :quantity => 600})
    ents = @guest_client_unmapped.list_entitlements
    ents.size.should == 1
    ents[0].quantity.should == 600
 end

 it 'product multiplier should have no effect on unlimited master pool quantity' do
    # master pool quantity expected to be -1
    master_pool = @cp.get_pool(@pool_virt_product_multiplier['id'])
    expect(master_pool['quantity']).to eq(-1)

    # consume master pool with physical client in any quantity
    @physical_client.consume_pool(master_pool['id'], {:quantity => 1000})
    master_pool = @cp.get_pool(master_pool['id'])
    expect(master_pool['quantity']).to eq(-1)

    pools = @cp.list_owner_pools(@owner['key'])
    sub_pool = pools.select do |pool|
       pool['subscriptionId'] == @pool_virt_product_multiplier['subscriptionId'] &&
          pool['type'] != 'NORMAL' && (pool['type'] == 'UNMAPPED_GUEST' || pool['type'] == 'BONUS')
    end

    # Quantity is expected to be unlimited
    sub_pool = @cp.get_pool(sub_pool[0]['id'])
    expect(sub_pool['quantity']).to eq(-1)
 end

 it 'instance multiplier should have no effect on unlimited master pool quantity' do
    # master pool quantity expected to be -1
    master_pool = @cp.get_pool(@pool_virt_instance_multiplier['id'])
    expect(master_pool['quantity']).to eq(-1)

    # consume master pool in any quantity
    @guest_client.consume_pool(master_pool['id'], {:quantity => 100})
    master_pool = @cp.get_pool(master_pool['id'])
    expect(master_pool['quantity']).to eq(-1)

    pools = @cp.list_owner_pools(@owner['key'])
    sub_pools = pools.select do |pool|
        pool['subscriptionId'] == @pool_virt_instance_multiplier['subscriptionId'] &&
          pool['type'] != 'NORMAL'
    end
    sub_pool = sub_pools[0]

    # sub pool quantity is expected to be unlimited
    sub_pool = @cp.get_pool(sub_pool['id'])
    expect(sub_pool['quantity']).to eq(-1)
 end

 it 'unlimited master pool quantity should always be equal to -1' do
    product = create_product(nil, nil, { :attributes => {:virt_limit => "4", 'multi-entitlement' => "yes"}})
    # create pool with -100 quantity
    pool = create_pool_and_subscription(@owner['key'], product.id, -100, [], '', '', '', nil, nil, false)

    # master pool quantity expected to be -1
    master_pool = @cp.get_pool(pool['id'])
    expect(master_pool['quantity']).to eq(-1)

    # consume master pool in any quantity
    @physical_client.consume_pool(master_pool['id'], {:quantity => 1000})
    master_pool = @cp.get_pool(master_pool['id'])
    expect(master_pool['quantity']).to eq(-1)
 end
end
