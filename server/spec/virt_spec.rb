require 'spec_helper'
require 'candlepin_scenarios'

# This spec tests virt limited products in a standalone Candlepin deployment.
# (which we assume to be testing against)
describe 'Standalone Virt-Limit Subscriptions', :type => :virt do
  include AttributeHelper
  include CandlepinMethods
  include VirtHelper
  include SpecUtils

  before(:each) do
    skip("candlepin running in standalone mode") if is_hosted?

    # Setup two virt host consumers:
    @host1 = @user.register(random_string('host'), :system, nil,
      {}, nil, nil, [], [])
    @host1_client = Candlepin.new(nil, nil, @host1['idCert']['cert'], @host1['idCert']['key'])

    @host2 = @user.register(random_string('host'), :system, nil,
      {}, nil, nil, [], [])
    @host2_client = Candlepin.new(nil, nil, @host2['idCert']['cert'], @host2['idCert']['key'])

    pools = @host1_client.list_pools :consumer => @host1['uuid']
    @host_ent = @host1_client.consume_pool(@virt_limit_pool['id'], {:quantity => 1})[0]
    # After binding the host should see no pools available:
    pools = @host1_client.list_pools :consumer => @host1['uuid']
    # one should remain
    pools.length.should == 1

    pools = @host2_client.list_pools :consumer => @host2['uuid']
    @host2_client.consume_pool(@virt_limit_pool['id'], {:quantity => 1})[0]
    # After binding the host should see no pools available:
    pools = @host2_client.list_pools :consumer => @host2['uuid']

    # Link the host and the guest:
    @host1_client.update_consumer({:guestIds => [{'guestId' => @uuid1}]})

    @cp.get_consumer_guests(@host1['uuid']).length.should == 1

    # Find the host-restricted pool:
    pools = @guest1_client.list_pools :consumer => @guest1['uuid']

    pools.length.should eq(3)
    @guest_pool = pools.find_all { |i| !i['sourceEntitlement'].nil? }[0]

  end

  it 'should attach host provided pools before other available pools' do

    datacenter_product_1 = create_product(nil, nil, {
        :attributes => {
            :virt_limit => "unlimited",
            :stacking_id => "stackme1",
            :sockets => "2",
            'multi-entitlement' => "yes"
        }
    })
    derived_product_1 = create_product(nil, nil, {
        :attributes => {
            :cores => 2,
            :stacking_id => "stackme1-derived",
            :sockets=>4
        }
    })
    datacenter_product_2 = create_product(nil, nil, {
        :attributes => {
            :virt_limit => "unlimited",
            :stacking_id => "stackme2",
            :sockets => "2",
            'multi-entitlement' => "yes"
        }
    })
    derived_product_2 = create_product(nil, nil, {
        :attributes => {
            :cores => 2,
            :stacking_id => "stackme2-derived",
            :sockets => 4
        }
    })

    @both_products = create_product(nil, nil, {
        :attributes => {
            :type => 'MKT'
        },
        :providedProducts => [derived_product_1.id, derived_product_2.id]
    })
    # We'd like there to be three subs, two that require a specific host and one that provides both required products
    # in one. These first two are similar to VDC subscriptions, hence the name datacenter.
    @cp.create_pool(@owner['key'], datacenter_product_1.id, {:quantity => 10, :derived_product_id => derived_product_1['id']})
    @cp.create_pool(@owner['key'], datacenter_product_2.id, {:quantity => 10, :derived_product_id => derived_product_2['id']})
    @cp.create_pool(@owner['key'], @both_products.id, {:quantity => 1, :provided_products => [derived_product_1.id, derived_product_2.id]})

    @cp.refresh_pools(@owner['key'])

    @installed_product_list = [
        {'productId' => derived_product_1.id, 'productName' => derived_product_1.name},
        {'productId' => derived_product_2.id, 'productName' => derived_product_2.name}]
    @guest2_client.update_consumer({:installedProducts => @installed_product_list})
    @host1_client.consume_product(product=datacenter_product_1.id)
    @host1_client.consume_product(product=datacenter_product_2.id)

    @host2_client.consume_product(product=datacenter_product_1.id)
    @host2_client.consume_product(product=datacenter_product_2.id)

    @host1_client.update_consumer({:guestIds => [{'guestId' => @uuid2}]})

    @guest2_client.list_entitlements.length.should == 0

    @guest2_client.consume_product()
    @guest2_client.list_entitlements.length.should == 2

    @guest2_client.list_entitlements.each { |ent|
      [derived_product_1.id, derived_product_2.id].should include(ent['pool']['productId'])
      found_requires_host = false
      ent['pool']['attributes'].each { |attribute|
        if attribute['name'] == 'requires_host'
          attribute['value'].should == @host1['uuid']
          found_requires_host = true
        end
        attribute['value'].should == @host1['uuid'] if attribute['name'] == 'requires_host'
      }
      # A failure on the line below means one of the entitlements the guest has does not have the requires_host attr
      found_requires_host.should == true
    }
    # A similar set of pools should be chosen during guest migration
    # So remove the guest from the first host and add the guest to the second host
    @host1_client.update_consumer({:guestIds => []})
    @host2_client.update_consumer({:guestIds => [{'guestId' => @uuid2}]})

    @guest2_client.list_entitlements.length.should == 2

    @guest2_client.list_entitlements.each { |ent|
      [derived_product_1.id, derived_product_2.id].should include(ent['pool']['productId'])
      ent['pool']['attributes'].each { |attribute|
        attribute['value'].should == @host2['uuid'] if attribute['name'] == 'requires_host'
      }
    }
  end

  it 'should create a virt_only pool for hosts guests' do
    # Get the attribute that indicates which host:
    host = get_attribute_value(@guest_pool['attributes'], "requires_host")
    expect(host).to eq(@host1['uuid'])

    # Guest 1 should be able to use the pool:
    @guest1_client.consume_pool(@guest_pool['id'], {:quantity => 1})

    # Should not be able to use the pool as this guest is not on the correct
    # host:
    lambda do
        @guest2_client.consume_pool(@guest_pool['id'], {:quantity => 1})
    end.should raise_exception(RestClient::Forbidden)
  end

  it 'should list host restricted pool only for its guests' do
    # Other guest shouldn't be able to see the virt sub-pool:
    pools = @guest2_client.list_pools :consumer => @guest2['uuid']
    # unmapped guest show 2 base and 2 unmapped guest pools
    pools.length.should eq(4)
    filter_unmapped_guest_pools(pools)
    pools.length.should eq(2)
  end

  it 'should check arch matches and guest_limit enforced on restricted subpools' do
    # Make sure that guest_limit and arch are still imposed
    # upon host restricted subpools
    stack_id = random_string('test-stack-id')
    arch_virt_product = create_product(nil, nil,
      :attributes => {
        :virt_limit => 3,
        :guest_limit => 1,
        :arch => 'ppc64',
        :'multi-entitlement' => 'yes',
        :stacking_id => stack_id,
      })
    create_pool_and_subscription(@owner['key'], arch_virt_product.id, 10)
    arch_virt_pools = @user.list_pools(:owner => @owner.id, :product => arch_virt_product.id)
    #unmapped guest pool not part of test
    arch_virt_pool = filter_unmapped_guest_pools(arch_virt_pools)[0]

    @host1_client.consume_pool(arch_virt_pool['id'], {:quantity => 1})[0]
    @cp.refresh_pools(@owner['key'])

    # Find the host-restricted pool:
    pools = @guest1_client.list_pools :consumer => @guest1['uuid'], :listall => true, :product => arch_virt_product.id
    pools.length.should eq(2)
    # Find the correct host-restricted subpool
    guest_pool_with_arch = pools.find_all { |i| get_attribute_value(i['attributes'], 'requires_host') == @host1.uuid }[0]
    guest_pool_with_arch.should_not == nil

    @guest1_client.update_consumer({:guestIds => [
        {'guestId' => 'testing2', 'attributes' => {'active' => '1', 'virtWhoType'=> 'libvirt'}},
        {'guestId' => 'testing1', 'attributes' => {'active' => '1', 'virtWhoType'=> 'libvirt'}}]})
    @guest1_client.consume_pool(guest_pool_with_arch['id'], {:quantity => 1})
    compliance = @guest1_client.get_compliance(@guest1_client.uuid)
    compliance.reasons.length.should == 2
    reasonKeys = compliance.reasons.map {|r| r['key']}
    (reasonKeys.include? 'GUEST_LIMIT').should == true
    (reasonKeys.include? 'ARCH').should == true
  end

  # Covers BZ 1379849
  it 'should revoke guest entitlements when migration happens' do
    #New hypervisor
    host3 = @user.register(random_string('host'), :system, nil,
      {}, nil, nil, [], [])
    host3_client = Candlepin.new(nil, nil, host3['idCert']['cert'], host3['idCert']['key'])
     # Adding a product to consumer that cannot be covered
    @guest1_client.update_consumer({:installedProducts =>
       [{'productId' => 'someNonExistentProduct', 'productName' => 'nonExistentProduct'}]
    })

    # Guest 1 should be able to use the pool:
    @guest1_client.consume_pool(@guest_pool['id'], {:quantity => 1})
    @guest1_client.list_entitlements.length.should == 1

    sleep 3
    #Guest changes the hypervisor. This will trigger revocation of
    #the entitlement to guest_pool (because it requires_host) and
    #it will also trigger unsuccessfull autobind (because the
    #someNonExistentProduct cannot be covered)
    host3_client.update_consumer({:guestIds => [{'guestId' => @uuid1}]})
    @guest1_client.list_entitlements.length.should == 0
  end

  it 'should revoke guest entitlements when host unbinds' do
    # Guest 1 should be able to use the pool:
    @guest1_client.consume_pool(@guest_pool['id'], {:quantity => 1})
    @guest1_client.list_entitlements.length.should == 1

    @host1_client.unbind_entitlement(@host_ent['id'])

    @guest1_client.list_entitlements.length.should == 0
  end

  it 'should revoke guest entitlements when host unregisters' do
    # Guest 1 should be able to use the pool:
    @host1_client.update_consumer({:guestIds => [{'guestId' => @uuid2}, {'guestId' => @uuid1}]});
    @guest1_client.consume_pool(@guest_pool['id'], {:quantity => 1})
    @guest1_client.list_entitlements.length.should == 1

    @guest2_client.consume_pool(@guest_pool['id'], {:quantity => 1})
    @guest2_client.list_entitlements.length.should == 1

    # without the fix for #811581, this will 500
    @host1_client.unregister()

    @guest1_client.list_entitlements.length.should == 0
  end

  it 'should revoke guest entitlements and remove activation keys when host unbinds' do
    activation_key = @cp.create_activation_key(@owner['key'], random_string('test_token'))
    @cp.add_pool_to_key(activation_key['id'], @guest_pool['id'])
    # Guest 1 should be able to use the pool:
    @guest1_client.consume_pool(@guest_pool['id'], {:quantity => 1})
    @guest1_client.list_entitlements.length.should == 1

    @host1_client.unbind_entitlement(@host_ent['id'])

    @guest1_client.list_entitlements.length.should == 0
  end

  it 'should not revoke guest entitlements when host stops reporting guest ID' do
    @guest1_client.consume_pool(@guest_pool['id'], {:quantity => 1})
    @guest1_client.list_entitlements.length.should == 1

    # Host 1 stops reporting guest:
    @host1_client.update_consumer({:guestIds => []})

    # Entitlement should not be gone:
    @guest1_client.list_entitlements.length.should == 1
  end

  it 'should not revoke guest entitlements when host removes guest ID through new api' do
    @guest1_client.consume_pool(@guest_pool['id'], {:quantity => 1})
    @guest1_client.list_entitlements.length.should == 1

    # Host 1 stops reporting guest:
    @uuid = @host1['uuid']
    @host1_client.delete_guestid(@uuid1)

    # Entitlement should not be gone:
    @guest1_client.list_entitlements.length.should == 1
  end

  it 'should lose entitlement when guest stops and is restarted elsewhere' do
    @guest1_client.consume_pool(@guest_pool['id'], {:quantity => 1})
    @guest1_client.list_entitlements.length.should == 1

    # Host 1 stops reporting guest:
    @host1_client.update_consumer({:guestIds => []})
    @host2_client.update_consumer({:guestIds => [{'guestId' => @uuid1}]})

    # Entitlement should be gone:
    @guest1_client.list_entitlements.length.should == 0
  end

  it 'should auto-heal when guest is migrated to another host' do

    # create a second product in order to test bz #786730

    @second_product = create_product()
    create_pool_and_subscription(@owner['key'], @second_product.id, 1)

    @installed_product_list = [
    {'productId' => @virt_limit_product.id, 'productName' => @virt_limit_product.name},
    {'productId' => @second_product.id, 'productName' => @second_product.name}]

    @guest1_client.update_consumer({:installedProducts => @installed_product_list})
    @guest1_client.consume_product()
    ents = @guest1_client.list_entitlements
    ents.length.should == 2
    original_virt_limit_pool = ents.select do |e|
      e['product_id'] = @virt_limit_product.id
    end.first['pool']['id']

    # Add guest 2 to host 1 so we can make sure that only guest1's
    # entitlements are revoked.
    @host1_client.update_consumer({:guestIds => [{'guestId' => @uuid2}, {'guestId' => @uuid1}]});

    @guest2_client.consume_pool(@guest_pool['id'], {:quantity => 1})
    @guest2_client.list_entitlements.length.should == 1

    sleep 3

    # Host 2 reports the new guest before Host 1 reports it removed.
    # this is where the error would occur without the 786730 fix
    @host2_client.update_consumer({:guestIds => [{'guestId' => @uuid1}]})

    # The old host-specific entitlement should not be on the guest anymore (see 768872 comment #41)
    # Instead the guest should be auto-healed for host2
    # second_product's entitlement should still be there, though.
    @guest1_client.list_entitlements(:product => @second_product.id).length.should == 1
    ents = @guest1_client.list_entitlements(:product => @virt_limit_product.id).first

    ents['pool']['id'].should_not == original_virt_limit_pool

    # Entitlements should have remained the same for guest 2 and its host
    # is the same.
    @guest2_client.list_entitlements.length.should == 1
  end

  it 'should heal the host before healing itself' do
    @cp.refresh_pools(@owner['key'])

    @installed_product_list = [
        {'productId' => @virt_limit_product.id, 'productName' => @virt_limit_product.name}]

    @guest1_client.update_consumer({:installedProducts => @installed_product_list})
    @host1_client.update_consumer({:guestIds => [{'guestId' => @uuid1}]});
    @host1_client.update_consumer({:installedProducts => []})

    @host1_client.update_consumer({:autoheal => true})
    for ent in @host1_client.list_entitlements do
        @host1_client.unbind_entitlement(ent.id)
    end
    for ent in @guest1_client.list_entitlements do
        @guest1_client.unbind_entitlement(ent.id)
    end
    @host1_client.list_entitlements.length.should == 0
    @guest1_client.list_entitlements.length.should == 0
    @guest1_client.consume_product()
    # After the guest autobinds, the host should also be healed
    @guest1_client.list_entitlements.length.should == 1
    @host1_client.list_entitlements.length.should == 1
  end

  it 'should not bind products on host if virt_only are already available for guest' do
    @second_product = create_product(nil, nil, {:attributes => { :virt_only => true },
      :providedProducts => [@virt_limit_product.id]})
    @cp.create_pool(@owner['key'],
      @second_product.id, {:quantity => 10, :provided_products => [@virt_limit_product.id]})
    @cp.refresh_pools(@owner['key'])

    @installed_product_list = [
        {'productId' => @virt_limit_product.id, 'productName' => @virt_limit_product.name}]

    @guest1_client.update_consumer({:installedProducts => @installed_product_list})
    @host1_client.update_consumer({:guestIds => [{'guestId' => @uuid1}]});
    @host1_client.update_consumer({:installedProducts => []})

    @host1_client.update_consumer({:autoheal => true})
    for ent in @host1_client.list_entitlements do
        @host1_client.unbind_entitlement(ent.id)
    end
    for ent in @guest1_client.list_entitlements do
        @guest1_client.unbind_entitlement(ent.id)
    end
    @host1_client.list_entitlements.length.should == 0
    @guest1_client.list_entitlements.length.should == 0
    @guest1_client.consume_product()
    # After the guest autobinds, the host should also be healed
    @guest1_client.list_entitlements.length.should == 1
    @host1_client.list_entitlements.length.should == 0
  end

  it 'should not heal host if nothing is installed' do
    @cp.refresh_pools(@owner['key'])

    @installed_product_list = [
        {'productId' => @virt_limit_product.id, 'productName' => @virt_limit_product.name}]

    @host1_client.update_consumer({:installedProducts => @installed_product_list})
    @host1_client.update_consumer({:guestIds => [{'guestId' => @uuid1}]});
    @guest1_client.update_consumer({:installedProducts => []})

    @host1_client.update_consumer({:autoheal => true})
    for ent in @host1_client.list_entitlements do
        @host1_client.unbind_entitlement(ent.id)
    end
    for ent in @guest1_client.list_entitlements do
        @guest1_client.unbind_entitlement(ent.id)
    end
    @host1_client.list_entitlements.length.should == 0
    @guest1_client.list_entitlements.length.should == 0
    @guest1_client.consume_product()
    # After the guest autobinds, the host should also be healed
    @guest1_client.list_entitlements.length.should == 0
    @host1_client.list_entitlements.length.should == 0
  end

  it 'should not heal the host if the product is already compliant' do
    @second_product = create_product(nil, nil, :providedProducts => [@virt_limit_product.id])
    @cp.create_pool(@owner['key'],
      @second_product.id, {:quantity => 10, :provided_products => [@virt_limit_product.id]})
    @cp.refresh_pools(@owner['key'])

    @installed_product_list = [
        {'productId' => @virt_limit_product.id, 'productName' => @virt_limit_product.name}]

    @guest1_client.update_consumer({:installedProducts => @installed_product_list})
    @host1_client.update_consumer({:guestIds => [{'guestId' => @uuid1}]});
    @host1_client.update_consumer({:installedProducts => @installed_product_list})

    @host1_client.update_consumer({:autoheal => true})
    for ent in @host1_client.list_entitlements do
        @host1_client.unbind_entitlement(ent.id)
    end
    for ent in @guest1_client.list_entitlements do
        @guest1_client.unbind_entitlement(ent.id)
    end
    @guest1_client.list_entitlements.length.should == 0
    for pool in @host1_client.list_pools({:owner => @owner['id']}) do
        if pool['productId'] == @second_product.id
            @host1_client.consume_pool(pool['id'])
            break
        end
    end
    @host1_client.list_entitlements.length.should == 1
    @guest1_client.consume_product()
    # After the guest autobinds, the host should also be healed
    @guest1_client.list_entitlements.length.should == 1
    @host1_client.list_entitlements.length.should == 1
    @host1_client.list_entitlements[0]['pool']['productId'].should == @second_product.id
  end

  it 'should not heal other host products' do
    @second_product = create_product()
    @cp.create_pool(@owner['key'], @second_product.id, {:quantity => 1})
    @cp.refresh_pools(@owner['key'])

    @guest_installed_product_list = [
        {'productId' => @virt_limit_product.id, 'productName' => @virt_limit_product.name}]
    @host_installed_product_list = [
        {'productId' => @second_product.id, 'productName' => @second_product.name}]

    @guest1_client.update_consumer({:installedProducts => @guest_installed_product_list})
    @host1_client.update_consumer({:guestIds => [{'guestId' => @uuid1}]});
    @host1_client.update_consumer({:installedProducts => @host_installed_product_list})

    @host1_client.update_consumer({:autoheal => true})
    for ent in @host1_client.list_entitlements do
        @host1_client.unbind_entitlement(ent.id)
    end
    for ent in @guest1_client.list_entitlements do
        @guest1_client.unbind_entitlement(ent.id)
    end
    @host1_client.list_entitlements.length.should == 0
    @guest1_client.list_entitlements.length.should == 0
    @guest1_client.consume_product()
    # After the guest autobinds, the host should also be healed
    @guest1_client.list_entitlements.length.should == 1
    @host1_client.list_entitlements.length.should == 1
    @host1_client.list_entitlements[0]['pool']['productId'].should == @virt_limit_product.id
  end

  it 'should not autobind virt-limiting products that do not cover guests' do
    @very_virt_limit_product = create_product(nil, nil, {
      :attributes => {
        :guest_limit => 1
      }
    })
    @virt_limit_sub = @cp.create_pool(@owner['key'], @very_virt_limit_product.id, {:quantity => 10})
    @cp.refresh_pools(@owner['key'])
    @host1_client.update_consumer({:installedProducts => [{'productId' => @very_virt_limit_product.id,
      'productName' => @very_virt_limit_product.name}]})
    @host1_client.update_consumer({:guestIds => [
        {'guestId' => @uuid1, 'attributes' => {'active' => '1', 'virtWhoType'=> 'libvirt'}},
        {'guestId' => @uuid2, 'attributes' => {'active' => '1', 'virtWhoType'=> 'libvirt'}}]});
    @host1_client.get_consumer_guests.length.should == 2
    @host1_client.list_entitlements.length.should == 1
    @host1_client.consume_product()
    # Should not have any more entitlements
    @host1_client.list_entitlements.length.should == 1
  end

  it 'should autobind virt-limiting products that do cover guests' do
    @not_so_virt_limit_product = create_product(nil, nil, {
      :attributes => {
        :virt_limit => 8
      }
    })
    @virt_limit_sub = create_pool_and_subscription(@owner['key'], @not_so_virt_limit_product.id, 10)
    @cp.refresh_pools(@owner['key'])
    @host1_client.update_consumer({:installedProducts => [{'productId' => @not_so_virt_limit_product.id,
      'productName' => @not_so_virt_limit_product.name}]})
    @host1_client.update_consumer({:guestIds => [
        {'guestId' => @uuid1, 'attributes' => {'active' => '1', 'virtWhoType'=> 'libvirt'}},
        {'guestId' => @uuid2, 'attributes' => {'active' => '1', 'virtWhoType'=> 'libvirt'}}]});
    @host1_client.list_entitlements.length.should == 1
    @host1_client.consume_product()
    # Should not have any more entitlements
    @host1_client.list_entitlements.length.should == 2
  end

  it 'should not change the quantity on sub-pool when the source entitlement quantity changes' do
    # Create a sub for a virt limited product:
    product = create_product(random_string('product'), random_string('product'),
                      :attributes => { :virt_limit => 3, :'multi-entitlement' => 'yes'})
    create_pool_and_subscription(@owner['key'], product.id, 10)
    @cp.refresh_pools(@owner['key'])

    pools = @user.list_pools :owner => @owner.id, \
           :product => product.id
    # unmapped guest pool is also created
    pools.size.should == 2
    pool = filter_unmapped_guest_pools(pools)[0]

    host_ent = @host1_client.consume_pool(pool['id'], {:quantity => 3})[0]
    pools = @user.list_pools :owner => @owner.id, \
           :product => product.id
    pools.size.should == 3
    #unmapped guest pool not part of test
    filter_unmapped_guest_pools(pools).each do |now_pool|
        if now_pool['id'] != pool['id']
            now_pool['quantity'].should == 3
        end
    end
    # reduce entitlement
    @host1_client.update_entitlement({:id => host_ent.id, :quantity => 2})
    pools = @user.list_pools :owner => @owner.id, \
           :product => product.id
    pools.size.should == 3
    #unmapped guest pool not part of test
    filter_unmapped_guest_pools(pools).each do |now_pool|
        if now_pool['id'] != pool['id']
            now_pool['quantity'].should == 3
        end
    end
  end

  it 'should not block a virt guest' do
    @instance_based = create_product(nil, random_string('instance_based'),
                                    :attributes => { 'instance_multiplier' => 2,
                                        'multi-entitlement' => 'yes' })
    @cp.create_pool(@owner['key'], @instance_based.id, {:quantity => 10})
    @cp.refresh_pools(@owner['key'])

    pool = @guest1_client.list_pools(:product => @instance_based.id,
        :consumer => @guest1_client.uuid).first
    @guest1_client.consume_pool(pool.id, {:quantity => 3})
  end

end
