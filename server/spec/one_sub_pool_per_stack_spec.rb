require 'spec_helper'
require 'candlepin_scenarios'

# This spec tests virt limited products in a standalone Candlepin deployment.
# (which we assume to be testing against)
describe 'One Sub Pool Per Stack' do
  include CandlepinMethods
  include VirtHelper
  include SpecUtils
  include AttributeHelper

  before(:each) do
    @now = DateTime.now

    skip("candlepin running in standalone mode") if is_hosted?
    @owner = create_owner random_string('virt_owner')
    @user = user_client(@owner, random_string('virt_user'))

    @stack_id = 'mixed-stack'

    @virt_limit_provided_product = create_product()

    @virt_limit_product = create_product('vlprod', 'vlprod', {
        :attributes => {
            'virt_limit' => 3,
            'stacking_id' => @stack_id,
            'multi-entitlement' => 'yes'
        },
      :providedProducts => [@virt_limit_provided_product['id']]
    })

    @virt_limit_product2 = create_product('vlprod2', 'vlprod2', {
        :attributes => {
            'virt_limit' => 6,
            'stacking_id' => @stack_id,
            'multi-entitlement' => 'yes'
        },
      :providedProducts => [@virt_limit_provided_product['id']]
    })

    @regular_stacked_provided_product =  create_product()

    @regular_stacked_product = create_product('target-id', 'target product', {
        :attributes => {
            'stacking_id' => @stack_id,
            'multi-entitlement' => 'yes',
            'sockets' => 6
        },
      :providedProducts => [@regular_stacked_provided_product['id']]
    })



    @non_stacked_product = create_product(nil, nil, {
        :attributes => {
            'sockets' => '2',
            'cores' => '4'
        }
    })

    @derived_provided_product = create_product()

    @stacked_datacenter_product = create_product(nil, nil, {
      :attributes => {
        :virt_limit => "unlimited",
        :stacking_id => @stack_id,
        :sockets => "2",
        'multi-entitlement' => "yes"
      },
      :providedProducts => [@derived_provided_product['id']]
    })


    @derived_product = create_product(nil, nil, {
      :attributes => {
          :cores => '6',
          :sockets=>'8'
      },
      :providedProducts => [@derived_provided_product['id']]
    })


    # Create a different stackable product but with a different stack id.
    @stack_id2 = "diff-stack-id"
    @stacked_provided_product2 =  create_product()
    @stacked_product_diff_id = create_product(nil, nil, {
        :attributes => {
            'stacking_id' => @stack_id2,
            'multi-entitlement' => 'yes',
            'sockets' => 6
        },
      :providedProducts => [@stacked_provided_product2['id']]
    })

    create_pool_and_subscription(@owner['key'],
      @virt_limit_product.id, 10, [@virt_limit_provided_product.id], "123", "321", "333",
      nil, nil, true)
    create_pool_and_subscription(@owner['key'],
      @virt_limit_product.id, 10, [], "456", '', '', nil, @now + 380, true)
    create_pool_and_subscription(@owner['key'],
      @virt_limit_product2.id, 10, [], "444", '', '', nil, @now + 380, true)
    create_pool_and_subscription(@owner['key'],
      @regular_stacked_product.id, 4, [@regular_stacked_provided_product.id], "789",
      "","", nil, nil, true)
    create_pool_and_subscription(@owner['key'],
      @non_stacked_product.id, 2, [], "234", "", "", nil, nil, true)
    create_pool_and_subscription(@owner['key'], @stacked_datacenter_product.id,
      10, [], '222', '', '', nil, nil, true,
      {
        :derived_product_id => @derived_product.id,
        :derived_provided_products => [@derived_provided_product.id]
      })
    create_pool_and_subscription(@owner['key'],
      @stacked_product_diff_id.id, 2, [], "888", '', '', @now - 3, @now + 6)

    # Determine our pools by matching on contract number.
    pools = @user.list_pools :owner => @owner.id

    # test does not use unmapped guest pools
    filter_unmapped_guest_pools(pools)

    @initial_pool_count = pools.size
    @initial_pool_count.should == 7

    @stacked_virt_pool1 = pools.detect { |p| p['contractNumber'] == "123" }
    @stacked_virt_pool1.should_not be_nil

    @stacked_virt_pool2 = pools.detect { |p| p['contractNumber'] == "456" }
    @stacked_virt_pool2.should_not be_nil

    @stacked_virt_pool3 = pools.detect { |p| p['contractNumber'] == "444" }
    @stacked_virt_pool3.should_not be_nil

    @stacked_non_virt_pool = pools.detect { |p| p['contractNumber'] == "789" }
    @stacked_non_virt_pool.should_not be_nil

    @non_stacked_pool = pools.detect { |p| p['contractNumber'] == "234" }
    @non_stacked_pool.should_not be_nil

    @datacenter_pool = pools.detect { |p| p['contractNumber'] == '222' }
    @datacenter_pool.should_not be_nil

    @regular_stacked_with_diff_stackid = pools.detect { |p| p['contractNumber'] == '888' }
    @regular_stacked_with_diff_stackid.should_not be_nil

    # Setup two a guest consumer:
    @guest_uuid = random_string('system.uuid')
    @guest = @user.register(random_string('guest'), :system, nil,
      {'virt.uuid' => @guest_uuid, 'virt.is_guest' => 'true'}, nil, nil, [], [])
    @guest_client = Candlepin.new(nil, nil, @guest['idCert']['cert'], @guest['idCert']['key'])

    @host = @user.register(random_string('host'), :system, nil,
      {}, nil, nil, [], [])
    @host_client = Candlepin.new(nil, nil, @host['idCert']['cert'], @host['idCert']['key'])

    @host2 = @user.register(random_string('host'), :system, nil,
      {}, nil, nil, [], [])
    @host2_client = Candlepin.new(nil, nil, @host2['idCert']['cert'], @host2['idCert']['key'])

    # Link the host and the guest:
    @host_client.update_consumer({:guestIds => [{'guestId' => @guest_uuid}]})
    @cp.get_consumer_guests(@host['uuid']).length.should == 1

    @host_client.list_pools(:consumer => @host['uuid']).length.should eq(@initial_pool_count)
    @guest_client.list_pools(:consumer => @guest['uuid']).length.should eq(@initial_pool_count)
  end

  it 'should create one sub pool when host binds to stackable virt_limit pool' do
    host_ent = @host_client.consume_pool(@stacked_virt_pool1['id'], {:quantity => 1})[0]
    host_ent.should_not be_nil

    @host_client.list_pools(:consumer => @host['uuid']).length.should eq(@initial_pool_count)
    # sub pool should have been created
    guest_pools = @guest_client.list_pools(:consumer => @guest['uuid'])
    guest_pools.length.should eq(@initial_pool_count + 1)

    sub_pools = guest_pools.find_all { |i| !i['sourceStackId'].nil? }
    sub_pools.length.should eq(1)
    sub_pool = sub_pools[0]
    sub_pool['sourceStackId'].should == @stack_id

    actual_value = get_attribute_value(sub_pool['attributes'], 'requires_host')
    expect(actual_value).to eq(@host.uuid)
  end

  it 'should not include host entitlements from another stack' do
    ent1 = @host_client.consume_pool(@stacked_virt_pool1['id'], {:quantity => 1})[0]
    ent2 = @host_client.consume_pool(@regular_stacked_with_diff_stackid['id'], {:quantity => 1})[0]
    sub_pool = find_sub_pool(@guest_client, @guest['uuid'], @stack_id)
    sub_pool.should_not be_nil
    expect(has_attribute(sub_pool["productAttributes"], "sockets")).to be false
    sub_pool['startDate'].should == ent1['startDate']
    sub_pool['endDate'].should == ent1['endDate']
  end

  it 'should delete sub pool when all host entitlements are removed from the stack' do
    ent1 = @host_client.consume_pool(@stacked_virt_pool1['id'], {:quantity => 1})[0]
    ent2 = @host_client.consume_pool(@stacked_virt_pool2['id'], {:quantity => 1})[0]
    ent3 = @host_client.consume_pool(@stacked_non_virt_pool['id'], {:quantity => 1})[0]
    @host_client.list_entitlements.length.should == 3

    sub_pool = find_sub_pool(@guest_client, @guest['uuid'], @stack_id)
    sub_pool.should_not be_nil

    @host_client.unbind_entitlement(ent1['id'])
    @host_client.unbind_entitlement(ent2['id'])
    @host_client.unbind_entitlement(ent3['id'])
    @host_client.list_entitlements.length.should == 0

    find_sub_pool(@guest_client, @guest['uuid'], @stack_id).should be_nil
  end

  it 'should update sub pool date range when another stacked entitlement is added' do
    ent1 = @host_client.consume_pool(@stacked_virt_pool1['id'], {:quantity => 1})[0]
    ent2 = @host_client.consume_pool(@stacked_virt_pool2['id'], {:quantity => 1})[0]

    sub_pool = find_sub_pool(@guest_client, @guest['uuid'], @stack_id)
    sub_pool.should_not be_nil

    sub_pool['startDate'].should == ent1['startDate']
    sub_pool['endDate'].should == ent2['endDate']
  end

  it 'should update product data on adding entitlement of same stack' do
    ent = @host_client.consume_pool(@stacked_virt_pool1['id'], {:quantity => 1})[0]
    sub_pool = find_sub_pool(@guest_client, @guest['uuid'], @stack_id)
    sub_pool.should_not be_nil
    sub_pool['productId'].should == @stacked_virt_pool1['productId']

    @host_client.consume_pool(@stacked_non_virt_pool['id'], {:quantity => 1})[0]

    sub_pool = find_sub_pool(@guest_client, @guest['uuid'], @stack_id)
    sub_pool.should_not be_nil
    #verify product has not changed
    sub_pool['productId'].should == @stacked_virt_pool1['productId']
    check_product_attr_value(sub_pool, "virt_limit", '3')
    check_product_attr_value(sub_pool, "multi-entitlement", 'yes')
    check_product_attr_value(sub_pool, "stacking_id", @stack_id)

    @host_client.unbind_entitlement(ent['id'])
    sub_pool = find_sub_pool(@guest_client, @guest['uuid'], @stack_id)
    sub_pool.should_not be_nil
    #verify product has changed
    sub_pool['productId'].should == @stacked_non_virt_pool['productId']
    attr = sub_pool['productAttributes'].detect { |a| a['name'] == 'virt_limit' }
    attr.should == nil
    check_product_attr_value(sub_pool, "multi-entitlement", 'yes')
    check_product_attr_value(sub_pool, "stacking_id", @stack_id)

  end

  it 'should update provided products when stacked entitlements change' do
    ent1 = @host_client.consume_pool(@stacked_virt_pool1['id'], {:quantity => 1})[0]

    sub_pool = find_sub_pool(@guest_client, @guest['uuid'], @stack_id)
    sub_pool.should_not be_nil
    sub_pool['providedProducts'].length.should == 1
    sub_pool['providedProducts'][0]['productId'].should == @virt_limit_provided_product.id

    @host_client.consume_pool(@stacked_non_virt_pool['id'], {:quantity => 1})[0]

    sub_pool = find_sub_pool(@guest_client, @guest['uuid'], @stack_id)
    sub_pool.should_not be_nil
    sub_pool['providedProducts'].length.should == 2

    @host_client.unbind_entitlement(ent1['id'])

    sub_pool = find_sub_pool(@guest_client, @guest['uuid'], @stack_id)
    sub_pool.should_not be_nil
    sub_pool['providedProducts'].length.should == 1
    sub_pool['providedProducts'][0]['productId'].should == @regular_stacked_provided_product.id
  end

  it 'should include derived provided products if supporting entitlements are in stack' do
    ent1 = @host_client.consume_pool(@datacenter_pool['id'], {:quantity => 1})[0]
    ent1.should_not be_nil

    @guest_client.list_pools(:consumer => @guest['uuid']).size.should == 8
    sub_pool = find_sub_pool(@guest_client, @guest['uuid'], @stack_id)
    sub_pool.should_not be_nil
    sub_pool['productId'].should == @derived_product.id
    sub_pool['providedProducts'].length.should == 1
    sub_pool['providedProducts'][0]['productId'].should == @derived_provided_product.id

    @host_client.consume_pool(@stacked_non_virt_pool['id'], {:quantity => 1})[0]

    sub_pool = find_sub_pool(@guest_client, @guest['uuid'], @stack_id)
    sub_pool.should_not be_nil
    sub_pool['providedProducts'].length.should == 2

    @host_client.unbind_entitlement(ent1['id'])

    sub_pool = find_sub_pool(@guest_client, @guest['uuid'], @stack_id)
    sub_pool.should_not be_nil
    sub_pool['providedProducts'].length.should == 1
    sub_pool['providedProducts'][0]['productId'].should == @regular_stacked_provided_product.id
  end

  it 'should update product data on removing entitlement of same stack' do
    ent1 = @host_client.consume_pool(@stacked_virt_pool1['id'], {:quantity => 1})[0]
    @host_client.consume_pool(@stacked_non_virt_pool['id'], {:quantity => 1})[0]
    @host_client.unbind_entitlement(ent1['id'])

    sub_pool = find_sub_pool(@guest_client, @guest['uuid'], @stack_id)
    sub_pool.should_not be_nil

    expect(has_attribute(sub_pool["productAttributes"], "virt_limit")).to be false
    check_product_attr_value(sub_pool, "multi-entitlement", 'yes')
    check_product_attr_value(sub_pool, "sockets", "6")
    check_product_attr_value(sub_pool, "stacking_id", @stack_id)
  end

  it 'should not update product data from products not in the stack' do
    @host_client.consume_pool(@stacked_virt_pool1['id'], {:quantity => 1})[0]
    ent2 = @host_client.consume_pool(@non_stacked_pool['id'], {:quantity => 1})[0]
    ent2.should_not be_nil

    sub_pool = find_sub_pool(@guest_client, @guest['uuid'], @stack_id)
    sub_pool.should_not be_nil

    # Check that cores was not added from the non stacked ent.
    expect(has_attribute(sub_pool["productAttributes"], "cores")).to be false
  end

  it 'should revoke guest entitlement from sub pool when last host ent in stack is removed' do
    ent1 = @host_client.consume_pool(@stacked_virt_pool1['id'], {:quantity => 1})[0]
    ent2 = @host_client.consume_pool(@stacked_virt_pool2['id'], {:quantity => 1})[0]
    ent3 = @host_client.consume_pool(@stacked_non_virt_pool['id'], {:quantity => 1})[0]
    @host_client.list_entitlements.length.should == 3

    sub_pool = find_sub_pool(@guest_client, @guest['uuid'], @stack_id)
    sub_pool.should_not be_nil

    @guest_client.consume_pool(sub_pool['id'], {:quantity => 1})
    @guest_client.list_entitlements.length.should == 1

    @host_client.unbind_entitlement(ent1['id'])
    @host_client.unbind_entitlement(ent2['id'])
    @host_client.unbind_entitlement(ent3['id'])
    @host_client.list_entitlements.length.should == 0

    # Guest entitlement should now be revoked.
    @guest_client.list_entitlements.length.should == 0
  end

  it 'should remove guest entitlement when host unregisters' do
    @host_client.consume_pool(@stacked_virt_pool1['id'], {:quantity => 1})[0]
    @host_client.consume_pool(@stacked_virt_pool2['id'], {:quantity => 1})[0]
    @host_client.consume_pool(@stacked_non_virt_pool['id'], {:quantity => 1})[0]
    @host_client.list_entitlements.length.should == 3

    sub_pool = find_sub_pool(@guest_client, @guest['uuid'], @stack_id)
    sub_pool.should_not be_nil

    @guest_client.consume_pool(sub_pool['id'], {:quantity => 1})
    @guest_client.list_entitlements.length.should == 1

    @host_client.unregister

    # Guest entitlement should now be revoked.
    @guest_client.list_entitlements.length.should == 0
  end

  it 'should remove guest entitlement when guest is migrated' do
    @host_client.consume_pool(@stacked_virt_pool1['id'], {:quantity => 1})[0]
    sub_pool = find_sub_pool(@guest_client, @guest['uuid'], @stack_id)
    sub_pool.should_not be_nil

    @guest_client.consume_pool(sub_pool['id'], {:quantity => 1})
    @guest_client.list_entitlements.length.should == 1

    # MySQL before 5.6.4 doesn't store fractional seconds on timestamps
    # and getHost() method in ConsumerCurator (which is what tells us which
    # host a guest is associated with) sorts results by updated time.
    sleep 1

    # Simulate migration
    @host2_client.update_consumer({:guestIds => [{'guestId' => @guest_uuid}]})

    # Guest entitlement should now be revoked.
    @guest_client.list_entitlements.length.should == 0
  end

  # TODO:
  # This test needs attention. Specifically, product changes need to trigger a refresh with the
  # changes listed, as these are no longer detectable after the fact.

  # it 'should update guest sub pool ent when product is updated' do
  #   # Attach sub to host and ensure sub pool is created.
  #   result = @host_client.consume_pool(@stacked_virt_pool1['id'], {:quantity => 1})[0]
  #   sub_pool = find_sub_pool(@guest_client, @guest['uuid'], @stack_id)
  #   sub_pool.should_not be_nil

  #   # Consumer ent for guest
  #   initial_guest_ent = @guest_client.consume_pool(sub_pool['id'], {:quantity => 1})[0]
  #   initial_guest_ent.should_not be_nil
  #   expect(has_attribute(initial_guest_ent.pool["productAttributes"], "sockets")).to be false

  #   attrs = @virt_limit_product['attributes']
  #   attrs << {"name" => "sockets", "value" => "4"}
  #   @cp.update_product(@owner['key'], @virt_limit_product.id, :attributes => attrs)
  #   @cp.get_product(@owner['key'], @virt_limit_product.id)

  #   @cp.refresh_pools(@owner['key'])

  #   updated_ent = @guest_client.list_entitlements[0]
  #   check_product_attr_value(updated_ent.pool, "sockets", "4")
  # end

  it 'should update guest sub pool ent as host stack is updated' do
    result = @host_client.consume_pool(@stacked_virt_pool1['id'], {:quantity => 1})
    ent = @host_client.consume_pool(@stacked_non_virt_pool['id'], {:quantity => 1})[0]

    sub_pool = find_sub_pool(@guest_client, @guest['uuid'], @stack_id)
    sub_pool.should_not be_nil

    @guest_client.consume_pool(sub_pool['id'], {:quantity => 1})[0]

    # Remove an ent from the host so that the guest ent will be updated.
    @host_client.unbind_entitlement(ent['id'])

    # Check that the product data was copied.
    updated_ent = @guest_client.list_entitlements[0]
    check_product_attr_value(updated_ent.pool, "virt_limit", '3')
    check_product_attr_value(updated_ent.pool, "multi-entitlement", 'yes')
    check_product_attr_value(updated_ent.pool, "stacking_id", @stack_id)
    expect(has_attribute(updated_ent.pool["productAttributes"], "sockets")).to be false
  end

  def verify_qty_and_product(qty, productId)
    sub_pool = find_sub_pool(@guest_client, @guest['uuid'], @stack_id)
    sub_pool.should_not be_nil
    sub_pool['quantity'].should == qty
    sub_pool.productId.should == productId
  end

  it 'should update quantity of sub pool when stack changes' do
    ent1 = @host_client.consume_pool(@stacked_virt_pool1['id'], {:quantity => 1})[0]
    verify_qty_and_product(3,@stacked_virt_pool1.productId)
    #sleep to ensure ent2 is later
    sleep 2
    @host_client.consume_pool(@stacked_non_virt_pool['id'], {:quantity => 1})[0]
    verify_qty_and_product(3,@stacked_virt_pool1.productId)
    #sleep to ensure ent3 is later
    sleep 2
    ent3 = @host_client.consume_pool(@stacked_virt_pool3['id'], {:quantity => 1})[0]
    verify_qty_and_product(3,@stacked_virt_pool1.productId)
    @host_client.list_entitlements.length.should == 3


    @host_client.unbind_entitlement(ent1['id'])
    verify_qty_and_product(6,@stacked_non_virt_pool.productId)
    @host_client.unbind_entitlement(ent3['id'])
    # quantity should not have changed since there was no entitlement
    # specifying virt_limit -- use the last instead.
    verify_qty_and_product(6,@stacked_non_virt_pool.productId)
  end

  it 'should regenerate ent certs when sub pool is update and client checks in' do
    @host_client.consume_pool(@stacked_virt_pool1['id'], {:quantity => 1})

    sub_pool = find_sub_pool(@guest_client, @guest['uuid'], @stack_id)
    sub_pool.should_not be_nil
    initial_guest_ent = @guest_client.consume_pool(sub_pool['id'], {:quantity => 1})[0]
    initial_guest_ent.should_not be_nil
    initial_guest_ent["certificates"].length.should == 1
    initial_guest_cert = initial_guest_ent['certificates'][0]

    # Grab another ent for the host to force a change in the guest's cert.
    @host_client.consume_pool(@stacked_virt_pool2['id'], {:quantity => 1})[0]

    # Listing the certs will cause a regeneration of dirty ents before
    # returning them (simulate client checkin).
    guest_certs = @guest_client.list_certificates()
    guest_certs.length.should == 1
    regenerated_cert = guest_certs[0]
    # Make sure that it was regenerated.
    regenerated_cert['id'].should_not == initial_guest_cert['id']

    # Make sure the entitlement picked up the pool's date change:
    initial_guest_ent = @guest_client.get_entitlement(initial_guest_ent['id'])
    sub_pool = @guest_client.get_pool(sub_pool['id'])
    initial_guest_ent['startDate'].should == sub_pool['startDate']
    initial_guest_ent['endDate'].should == sub_pool['endDate']
  end

  it 'should not regenerate certs on refresh pools when sub pool has not been changed' do
    @host_client.consume_pool(@stacked_virt_pool1['id'], {:quantity => 1})

    sub_pool = find_sub_pool(@guest_client, @guest['uuid'], @stack_id)
    sub_pool.should_not be_nil
    initial_guest_ent = @guest_client.consume_pool(sub_pool['id'], {:quantity => 1})[0]
    initial_guest_ent.should_not be_nil
    initial_guest_ent["certificates"].length.should == 1
    initial_guest_cert = initial_guest_ent['certificates'][0]

    # Perform refresh pools -- an old bug marked sub pool as dirty
    @cp.refresh_pools(@owner['key'])

    # Listing the certs will cause a regeneration of dirty ents before
    # returning them (simulate client checkin).
    guest_certs = @guest_client.list_certificates()
    guest_certs.length.should == 1
    regenerated_cert = guest_certs[0]
    # Make sure that it was not regenerated.
    regenerated_cert['id'].should == initial_guest_cert['id']
  end

  def check_product_attr_value(pool, attribute_name, expected_value)
    actual_value = get_attribute_value(pool['productAttributes'], attribute_name)
    expect(actual_value).to eq(expected_value)
  end

  def find_sub_pool(guest_client, guest_uuid, stack_id)
    guest_pools = guest_client.list_pools(:consumer => guest_uuid)
    return guest_pools.detect { |i| i['sourceStackId'] == stack_id }
  end
end
