require 'candlepin_scenarios'
require 'virt_fixture'

# This spec tests virt limited products in a standalone Candlepin deployment.
# (which we assume to be testing against)
describe 'One Sub Pool Per Stack' do
  include CandlepinMethods
  include CandlepinScenarios

  before(:each) do
    pending("candlepin running in standalone mode") if is_hosted?
    @owner = create_owner random_string('virt_owner')
    @user = user_client(@owner, random_string('virt_user'))

    @stack_id = 'mixed-stack'

    @virt_limit_product = create_product(nil, nil, {
        :attributes => {
            'virt_limit' => 3,
            'stacking_id' => @stack_id,
            'multi-entitlement' => 'yes'
        }
    })

    @regular_stacked_product = create_product(nil, nil, {
        :attributes => {
            'stacking_id' => @stack_id,
            'multi-entitlement' => 'yes',
            'sockets' => 6
        }
    })


    @non_stacked_product = create_product(nil, nil, {
        :attributes => {
            'sockets' => '2',
            'cores' => '4'
        }
    })

    @stacked_virt_sub1 = @cp.create_subscription(@owner['key'],
      @virt_limit_product.id, 10, [], "123", "321", "333")
    @stacked_virt_sub2 = @cp.create_subscription(@owner['key'],
      @virt_limit_product.id, 10, [], "456", '', '', nil, Date.today + 380)
    @stacked_non_virt_sub = @cp.create_subscription(@owner['key'],
      @regular_stacked_product.id, 4, [], "789")
    @non_stacked_sub = @cp.create_subscription(@owner['key'],
      @non_stacked_product.id, 2, [], "234")
    @cp.refresh_pools(@owner['key'])
    
    # Determine our pools by matching on contract number.
    pools = @user.list_pools :owner => @owner.id
    pools.size.should == 4
    
    @stacked_virt_pool1 = pools.detect { |p| p['contractNumber'] == "123" }
    @stacked_virt_pool1.should_not be_nil

    @stacked_virt_pool2 = pools.detect { |p| p['contractNumber'] == "456" }
    @stacked_virt_pool2.should_not be_nil

    @stacked_non_virt_pool = pools.detect { |p| p['contractNumber'] == "789" }
    @stacked_non_virt_pool.should_not be_nil

    @non_stacked_pool = pools.detect { |p| p['contractNumber'] == "234" }
    @non_stacked_pool.should_not be_nil
    
    # Setup two a guest consumer:
    @guest_uuid = random_string('system.uuid')
    @guest = @user.register(random_string('guest'), :system, nil,
      {'virt.uuid' => @guest_uuid, 'virt.is_guest' => 'true'}, nil, nil, [], [])
    @guest_client = Candlepin.new(username=nil, password=nil,
        cert=@guest['idCert']['cert'],
        key=@guest['idCert']['key'])

    @host = @user.register(random_string('host'), :system, nil,
      {}, nil, nil, [], [])
    @host_client = Candlepin.new(username=nil, password=nil,
        cert=@host['idCert']['cert'],
        key=@host['idCert']['key'])

    # Link the host and the guest:
    @host_client.update_consumer({:guestIds => [{'guestId' => @guest_uuid}]})
    @cp.get_consumer_guests(@host['uuid']).length.should == 1

    @host_client.list_pools(:consumer => @host['uuid']).should have(4).things
    @guest_client.list_pools(:consumer => @guest['uuid']).should have(4).things
  end

  it 'should create one sub pool when host binds to stackable virt_limit pool' do
    host_ent = @host_client.consume_pool(@stacked_virt_pool1['id'])[0]
    host_ent.should_not be_nil

    @host_client.list_pools(:consumer => @host['uuid']).should have(4).things
    # sub pool should have been created
    guest_pools = @guest_client.list_pools(:consumer => @guest['uuid'])
    guest_pools.should have(5).things

    sub_pools = guest_pools.find_all { |i| !i['sourceStackId'].nil? }
    sub_pools.should have(1).thing
    sub_pool = sub_pools[0]
    sub_pool['sourceStackId'].should == @stack_id
    sub_pool['sourceConsumer']['uuid'].should == @host['uuid']
  end

  it 'should delete sub pool when all host entitlements are removed from the stack' do
    ent1 = @host_client.consume_pool(@stacked_virt_pool1['id'])[0]
    ent2 = @host_client.consume_pool(@stacked_virt_pool2['id'])[0]
    ent3 = @host_client.consume_pool(@stacked_non_virt_pool['id'])[0]
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
    ent1 = @host_client.consume_pool(@stacked_virt_pool1['id'])[0]
    ent2 = @host_client.consume_pool(@stacked_virt_pool2['id'])[0]
   
    sub_pool = find_sub_pool(@guest_client, @guest['uuid'], @stack_id)
    sub_pool.should_not be_nil

    sub_pool['startDate'].should == ent1['startDate']
    sub_pool['endDate'].should == ent2['endDate']
  end

  it 'should update product data on adding entitlement of same stack' do
    ent1 = @host_client.consume_pool(@stacked_virt_pool1['id'])[0]
    ent2 = @host_client.consume_pool(@stacked_non_virt_pool['id'])[0]

    sub_pool = find_sub_pool(@guest_client, @guest['uuid'], @stack_id)
    sub_pool.should_not be_nil
    
    # Check that the product data was copied.
    check_product_attr_value(sub_pool, "virt_limit", '3')
    check_product_attr_value(sub_pool, "multi-entitlement", 'yes')
    check_product_attr_value(sub_pool, "sockets", "6")
    check_product_attr_value(sub_pool, "stacking_id", @stack_id)
  end
  
  it 'should update product data on removing entitlement of same stack' do
    ent1 = @host_client.consume_pool(@stacked_virt_pool1['id'])[0]
    ent2 = @host_client.consume_pool(@stacked_non_virt_pool['id'])[0]
    @host_client.unbind_entitlement(ent1['id'])
    
    sub_pool = find_sub_pool(@guest_client, @guest['uuid'], @stack_id)
    sub_pool.should_not be_nil
    
    find_product_attribute(sub_pool, "virt_limit").should be_nil
    check_product_attr_value(sub_pool, "multi-entitlement", 'yes')
    check_product_attr_value(sub_pool, "sockets", "6")
    check_product_attr_value(sub_pool, "stacking_id", @stack_id)
  end

  it 'should not update product data from products not in the stack' do
    ent1 = @host_client.consume_pool(@stacked_virt_pool1['id'])[0]
    ent2 = @host_client.consume_pool(@non_stacked_pool['id'])[0]
    ent2.should_not be_nil
    
    sub_pool = find_sub_pool(@guest_client, @guest['uuid'], @stack_id)
    sub_pool.should_not be_nil
    
    # Check that cores was not added from the non stacked ent.
    find_product_attribute(sub_pool, "cores").should be_nil
  end
  
  it 'should revoke guest entitlement from sub pool when last host ent in stack is removed' do
    ent1 = @host_client.consume_pool(@stacked_virt_pool1['id'])[0]
    ent2 = @host_client.consume_pool(@stacked_virt_pool2['id'])[0]
    ent3 = @host_client.consume_pool(@stacked_non_virt_pool['id'])[0]
    @host_client.list_entitlements.length.should == 3
    
    sub_pool = find_sub_pool(@guest_client, @guest['uuid'], @stack_id)
    sub_pool.should_not be_nil
    
    @guest_client.consume_pool(sub_pool['id'])
    @guest_client.list_entitlements.length.should == 1
    
    @host_client.unbind_entitlement(ent1['id'])
    @host_client.unbind_entitlement(ent2['id'])
    @host_client.unbind_entitlement(ent3['id'])
    @host_client.list_entitlements.length.should == 0
    
    # Guest entitlement should now be revoked.
    @guest_client.list_entitlements.length.should == 0
  end
  
  def find_product_attribute(pool, attribute_name)
    return pool['productAttributes'].detect { |a| a['name'] == attribute_name }
  end
  
  def check_product_attr_value(pool, attribute_name, expected_value)
    attribute = find_product_attribute(pool, attribute_name)
    attribute.should_not be_nil
    attribute['value'].should == expected_value
  end
  
  def find_sub_pool(guest_client, guest_uuid, stack_id)
    guest_pools = guest_client.list_pools(:consumer => guest_uuid)
    return guest_pools.detect { |i| i['sourceStackId'] == stack_id }
  end
  
end
