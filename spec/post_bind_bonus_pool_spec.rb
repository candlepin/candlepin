require 'spec_helper'
require 'candlepin_scenarios'
require 'time'

describe 'Post bind bonus pool updates' do

  include CandlepinMethods

  before(:each) do
  @owner_key = random_string('test_owner')
  owner = create_owner @owner_key

  limited_virt_limit_prod = create_product('taylor_limited', 'taylor swift limited',
    {:owner => @owner_key,
     :version => "6.1",
     :attributes => {
       "virt_limit" => "4",
       "multi-entitlement" => "yes"}})
  unlimited_virt_limit_prod = create_product('taylor_unlimited', 'taylor swift unlimited',
    {:owner => @owner_key,
     :version => "6.1",
     :attributes => {
       "virt_limit" => "unlimited",
       "multi-entitlement" => "yes"}})
  host_limited_prod = create_product('taylor_host_limited', 'taylor swift host limited',
    {:owner => @owner_key,
     :version => "6.1",
     :attributes => {
       "virt_limit" => "unlimited",
       "multi-entitlement" => "yes",
       "host_limited" => "true"}})
  limited_virt_limit_stacked_prod = create_product('taylor_limited_stack', 'taylor swift limited stack',
    {:owner => @owner_key,
     :version => "6.1",
     :attributes => {
       "virt_limit" => "4",
       "stacking_id" => "fearless",
       "multi-entitlement" => "yes"}})
  host_limited_stacked_prod = create_product('taylor_host_limited_stacked', 'taylor swift host limited stacked',
    {:owner => @owner_key,
     :version => "6.1",
     :attributes => {
       "virt_limit" => "9",
       "multi-entitlement" => "yes",
       "stacking_id" => "badBlood",
       "host_limited" => "true"}})

  @limited_master_pool = create_pool_and_subscription(owner['key'], limited_virt_limit_prod['id'], 10)
  pools = @cp.list_owner_pools(@owner_key)
  pools.length.should == 2
  sub_pools = pools.select do |pool|
    pool['subscriptionId'] == @limited_master_pool['subscriptionId'] &&
    pool['type'] != 'NORMAL'
  end
  sub_pools.length.should == 1
  @limited_bonus_pool = sub_pools[0]
  @limited_bonus_pool['quantity'].should == 40

  @unlimited_master_pool = create_pool_and_subscription(owner['key'], unlimited_virt_limit_prod['id'], 10)
  pools = @cp.list_owner_pools(@owner_key)
  pools.length.should == 4
  sub_pools = pools.select do |pool|
    pool['subscriptionId'] == @unlimited_master_pool['subscriptionId'] &&
    pool['type'] != 'NORMAL'
  end
  sub_pools.length.should == 1
  @unlimited_bonus_pool = sub_pools[0]
  @unlimited_bonus_pool['quantity'].should == -1

  @hostlimited_master_pool = create_pool_and_subscription(owner['key'], host_limited_prod['id'], 10)
  pools = @cp.list_owner_pools(@owner_key)
  pools.length.should == 6
  sub_pools = pools.select do |pool|
    pool['subscriptionId'] == @hostlimited_master_pool['subscriptionId'] &&
    pool['type'] != 'NORMAL'
  end
  sub_pools.length.should == 1
  @hostlimited_bonus_pool = sub_pools[0]
  @hostlimited_bonus_pool['quantity'].should == -1

  @limited_master_stacked_pool = create_pool_and_subscription(owner['key'], limited_virt_limit_stacked_prod['id'], 10)
  pools = @cp.list_owner_pools(@owner_key)
  pools.length.should == 8
  sub_pools = pools.select do |pool|
    pool['subscriptionId'] == @limited_master_stacked_pool['subscriptionId'] &&
    pool['type'] != 'NORMAL'
  end
  sub_pools.length.should == 1
  @limited_bonus_stacked_pool = sub_pools[0]
  @limited_bonus_stacked_pool['quantity'].should == 40

  @hostlimited_master_stacked_pool = create_pool_and_subscription(owner['key'], host_limited_stacked_prod['id'], 10)
  pools = @cp.list_owner_pools(@owner_key)
  pools.length.should == 10
  sub_pools = pools.select do |pool|
    pool['subscriptionId'] == @hostlimited_master_stacked_pool['subscriptionId'] &&
    pool['type'] != 'NORMAL'
  end
  sub_pools.length.should == 1
  @hostlimited_bonus_stacked_pool = sub_pools[0]
  @hostlimited_bonus_stacked_pool['quantity'].should == 90

  guest_uuid =  random_string('guest')
  guest_facts = {
    "virt.is_guest"=>"true",
    "virt.uuid"=>guest_uuid
  }
  @guest = @cp.register('guest.bind.com',:system, guest_uuid, guest_facts, 'admin',
    @owner_key, [], [])
  @guest_cp = Candlepin.new(nil, nil,
    @guest['idCert']['cert'], @guest['idCert']['key'])
  guest_uuid2 =  random_string('guest')
  guest_facts2 = {
    "virt.is_guest"=>"true",
    "virt.uuid"=>guest_uuid2
  }
  @guest2 = @cp.register('guest.bind.com',:system, guest_uuid2, guest_facts2, 'admin',
    @owner_key, [], [])
  @guest_cp2 = Candlepin.new(nil, nil,
    @guest2['idCert']['cert'], @guest2['idCert']['key'])

  satellite_uuid =  random_string('satellite')
  @satellite = @cp.register('sallite.bind.com',:candlepin, satellite_uuid, nil, 'admin',
    @owner_key, [], [])
  @satellite_cp = Candlepin.new(nil, nil,
    @satellite['idCert']['cert'], @satellite['idCert']['key'])
  hypervisor_uuid = random_string('hypervisor')
  @hypervisor = @cp.register('notguest.bind.com',:system, hypervisor_uuid, {}, 'admin',
    @owner_key, [], [])
  @hypervisor_cp = Candlepin.new(nil, nil,
    @hypervisor['idCert']['cert'], @hypervisor['idCert']['key'])

  @owner2_key = random_string('another_owner')
  create_owner @owner2_key
  end

  #verifies whether bind time pool ( stack or ent derived ) was created or not
  def verify_bind_time_pool_creation(consumer, pool, type, should_create)
    before_pools = @cp.list_owner_pools(@owner_key)
    before_ids = before_pools.collect { |p| p.id }

    consumer.consume_pool(pool['id'],{ :quantity => 1})
    after = @cp.list_owner_pools(@owner_key)
    if should_create
      after.length.should eq(before_pools.length + 1)
    else
      after.length.should eq(before_pools.length)
    end

    bind_time_created_pools = after.select { |d_pool| before_ids.include?(d_pool.id) == false }
    if should_create
      bind_time_created_pools.length.should == 1
    else
      bind_time_created_pools.length.should == 0
    end
  end

  it 'should create entitlement derived pool in hosted mode' do
    skip("candlepin running in standalone mode") unless is_hosted?

    #does not create ent derived if host_limited <> true
    verify_bind_time_pool_creation(@hypervisor_cp, @limited_master_pool, 'ENTITLEMENT_DERIVED', false)

    #does create ent derived if host_limited == true
    verify_bind_time_pool_creation(@hypervisor_cp, @hostlimited_master_pool, 'ENTITLEMENT_DERIVED', true)
  end

  it 'should create entitlement derived pool in standalone mode' do
    skip("candlepin not running in standalone mode") if is_hosted?

    #does create ent derived pool irrespective of host_limited attribute
    verify_bind_time_pool_creation(@hypervisor_cp, @limited_master_pool, 'ENTITLEMENT_DERIVED', true)

    #does create ent derived pool irrespective of host_limited attribute
    verify_bind_time_pool_creation(@hypervisor_cp, @hostlimited_master_pool, 'ENTITLEMENT_DERIVED', true)
  end


  it 'should create stack derived pool in hosted mode' do
    skip("candlepin running in standalone mode") unless is_hosted?

    #does not create stack derived if host_limited <> true
    verify_bind_time_pool_creation(@hypervisor_cp, @limited_master_stacked_pool, 'STACK_DERIVED', false)

    #does create stack derived if host_limited == true
    verify_bind_time_pool_creation(@hypervisor_cp, @hostlimited_master_stacked_pool, 'STACK_DERIVED', true)
  end

  it 'should create stack derived pool in standalone mode' do
    skip("candlepin not running in standalone mode") if is_hosted?

    #does create stack derived pool irrespective of host_limited attribute
    verify_bind_time_pool_creation(@hypervisor_cp, @limited_master_stacked_pool, 'STACK_DERIVED', true)

    #does create stack derived pool irrespective of host_limited attribute
    verify_bind_time_pool_creation(@hypervisor_cp, @hostlimited_master_stacked_pool, 'STACK_DERIVED', true)
  end

  it 'should create entitlement derived pool for every bind' do
    before_length = @cp.list_owner_pools(@owner_key).length
    @hypervisor_cp.consume_pool(@hostlimited_master_pool['id'],{ :quantity => 1} )
    @hypervisor_cp.consume_pool(@hostlimited_master_pool['id'],{ :quantity => 1} )
    @hypervisor_cp.consume_pool(@hostlimited_master_pool['id'],{ :quantity => 1} )
    after = @cp.list_owner_pools(@owner_key)
    after.length.should eq(before_length + 3)
    ent_derived_pools = after.select { |pool| pool.type == 'ENTITLEMENT_DERIVED' }
    ent_derived_pools.length.should == 3
  end

  it 'should decrement bonus pool quantity when finite virt limited master pool is partially exported' do
    skip("candlepin running in standalone mode") unless is_hosted?
    # reduce by quantity * virt_limit
    @satellite_cp.consume_pool(@limited_master_pool['id'], {:quantity => 2})
    @cp.get_pool(@limited_bonus_pool['id'])['quantity'].should == 32

    # now set to 0 when fully exported
    @satellite_cp.consume_pool(@limited_master_pool['id'], {:quantity => 8})
    @cp.get_pool(@limited_bonus_pool['id'])['quantity'].should == 0
  end

  it 'should update bonus pool quantity when export entitlement quantity is updated' do
    skip("candlepin running in standalone mode") unless is_hosted?
    # bonus pool quantity adjusted by quantity * virt_limit
    ent = @satellite_cp.consume_pool(@limited_master_pool['id'], {:quantity => 2})[0]
    @cp.get_pool(@limited_bonus_pool['id'])['quantity'].should == 32

    @cp.update_entitlement(:id => ent['id'], :quantity => 1)
    @cp.get_pool(@limited_bonus_pool['id'])['quantity'].should == 36

    @cp.update_entitlement(:id => ent['id'], :quantity => 3)
    @cp.get_pool(@limited_bonus_pool['id'])['quantity'].should == 28
  end

  it 'should not change bonus pool quantity when unlimited virt limited master pool is partially exported' do
    skip("candlepin running in standalone mode") unless is_hosted?
    @satellite_cp.consume_pool(@unlimited_master_pool['id'], {:quantity => 9})
    @cp.get_pool(@unlimited_bonus_pool['id'])['quantity'].should == -1

    # once it is fully consumed, set to 0
    @satellite_cp.consume_pool(@unlimited_master_pool['id'], {:quantity => 1})
    @cp.get_pool(@unlimited_bonus_pool['id'])['quantity'].should == 0
  end

  it 'should not change bonus pool quantity when unlimited virt limited master pool is consumed by non manifest consumer' do
    @guest_cp.consume_pool(@unlimited_master_pool['id'], {:quantity => 1})
    @cp.get_pool(@unlimited_bonus_pool['id'])['quantity'].should == -1
    @unlimited_master_pool = @cp.get_pool(@unlimited_master_pool['id'])
    @unlimited_master_pool['consumed'].should == 1
    @unlimited_master_pool['exported'].should == 0

    #even if one quantity was consumed but not exported, do not update quantity of the bonus pool
    @satellite_cp.consume_pool(@unlimited_master_pool['id'], {:quantity => 9})
    @unlimited_master_pool = @cp.get_pool(@unlimited_master_pool['id'])
    @unlimited_master_pool['consumed'].should == @unlimited_master_pool['quantity']
    @unlimited_master_pool['exported'].should_not == @unlimited_master_pool['consumed']
    @cp.get_pool(@unlimited_bonus_pool['id'])['quantity'].should == -1
  end

  #verifies quantity of entitlement derived pool does not changes even
  #when quantity of source entitlement is reduced to 1
  def ent_derived_test(pool, expected_quantity)
    init_length = @cp.list_owner_pools(@owner_key).length
    ent = @hypervisor_cp.consume_pool(pool['id'], {:quantity => 4})[0]
    pools = @cp.list_owner_pools(@owner_key)
    pools.length.should == init_length + 1
    ent_derived_pool = pools.select{ |pool| pool['type'] == 'ENTITLEMENT_DERIVED' }[0]
    ent_derived_pool['quantity'].should == expected_quantity
    ent_derived_pool['sourceEntitlement']['id'].should == ent['id']
    req_host_attr = ent_derived_pool['attributes'].select{ |attr| attr['name'] == 'requires_host' }[0]
    req_host_attr['value'].should == @hypervisor['uuid']

    @cp.update_entitlement({:id => ent['id'], :quantity => 1})
    #test will fail if following is uncommented
    #@cp.unregister(@hypervisor['uuid'])
    @cp.get_pool(ent_derived_pool['id'])['quantity'].should == expected_quantity
  end

  it 'should not change hostlimited entitlement derived pool quantity when source entitlement\'s quantity is reduced' do
    ent_derived_pool = ent_derived_test(@hostlimited_master_pool, -1)
  end

  it 'should change unlimited virtlimited entitlement derived pool quantity when source entitlement\'s quantity is reduced' do
    skip("candlepin running in hosted mode") if is_hosted?
    ent_derived_pool = ent_derived_test(@unlimited_master_pool, -1)
  end

  it 'should change limited virtlimited entitlement derived pool quantity when source entitlement\'s quantity is reduced' do
    skip("candlepin running in hosted mode") if is_hosted?
    ent_derived_pool = ent_derived_test(@limited_master_pool, 4)
  end

  it 'should revoke excess entitlements when finite virt limited master pool is exported' do
    skip("candlepin running in standalone mode") unless is_hosted?
    ent = @guest_cp.consume_pool(@limited_bonus_pool['id'], {:quantity => 1})[0]
    ent.quantity.should == 1

    # reduce by quantity * virt_limit
    @satellite_cp.consume_pool(@limited_master_pool['id'], {:quantity => 2})
    @cp.get_pool(@limited_bonus_pool['id'])['quantity'].should == 32

    #if fully exported, bonus pool is set to 0 quantity
    @satellite_cp.consume_pool(@limited_master_pool['id'], {:quantity => 8})
    @cp.get_pool(@limited_bonus_pool['id'])['quantity'].should == 0

    #and it's entitlement is revoked
    lambda do
      @cp.get_entitlement(ent['id'])
    end.should raise_exception(RestClient::ResourceNotFound)
  end

  it 'should revoke only sufficient entitlements when finite virt limited master pool is exported' do
    skip("candlepin running in standalone mode") unless is_hosted?
    @cp.get_pool(@limited_bonus_pool['id'])['quantity'].should == 40
    @cp.get_pool(@limited_bonus_pool['id'])['consumed'].should == 0

    #create two ents of qty 4 each
    ent1 = @guest_cp.consume_pool(@limited_bonus_pool['id'], {:quantity => 4})[0]
    ent1.quantity.should == 4

    ent2 = @guest_cp2.consume_pool(@limited_bonus_pool['id'], {:quantity => 4})[0]
    ent2.quantity.should == 4

    @cp.get_pool(@limited_bonus_pool['id'])['consumed'].should == 8

    # reduce by quantity * virt_limit
    @satellite_cp.consume_pool(@limited_master_pool['id'], {:quantity => 9})
    @cp.get_pool(@limited_bonus_pool['id'])['quantity'].should == 4

    #verify only one of the ents was revoked, was quantity is still 4
    ent1_revoked = true
    lambda do
      ent1 = @cp.get_entitlement(ent1['id'])
      ent1_revoked = false
      @cp.get_entitlement(ent2['id'])
    end.should raise_exception(RestClient::ResourceNotFound)

    if ent1_revoked
      ent2 = @cp.get_entitlement(ent2['id'])
      ent2['quantity'].should == 4
    else
      ent1['quantity'].should == 4
    end
  end
  
  it 'should revoke sufficient entitlements when entitlement quantity is updated' do
    skip("candlepin running in standalone mode") unless is_hosted?
    @cp.get_pool(@limited_bonus_pool['id'])['quantity'].should == 40
    @cp.get_pool(@limited_bonus_pool['id'])['consumed'].should == 0

    #create a bonus pool ent
    bonus_ent = @guest_cp.consume_pool(@limited_bonus_pool['id'], {:quantity => 4})[0]
    bonus_ent.quantity.should == 4

    @cp.get_pool(@limited_bonus_pool['id'])['consumed'].should == 4

    # reduce by quantity * virt_limit
    master_ent = @satellite_cp.consume_pool(@limited_master_pool['id'], {:quantity => 9})[0]
    @cp.get_pool(@limited_bonus_pool['id'])['quantity'].should == 4

    #verify ent still exists
    @cp.get_entitlement(bonus_ent['id'])

    @cp.update_entitlement(:id => master_ent['id'], :quantity => 10)
    #verify bonus pool quantity was updated
    @cp.get_pool(@limited_bonus_pool['id'])['quantity'].should == 0

    #verify the ent was revoked
    lambda do
      @cp.get_entitlement(bonus_ent['id'])
    end.should raise_exception(RestClient::ResourceNotFound)
  end

  it 'should not revoke excess entitlements when unlimited virt limited master pool is partially exported' do
    skip("candlepin running in standalone mode") unless is_hosted?
    ent = @guest_cp.consume_pool(@unlimited_bonus_pool['id'], {:quantity => 10})[0]
    ent.quantity.should == 10
    @cp.get_pool(@unlimited_bonus_pool['id'])['quantity'].should == -1

    # not revoked untill master is completely consumed
    @satellite_cp.consume_pool(@unlimited_master_pool['id'], {:quantity => 9})
    @cp.get_pool(@unlimited_bonus_pool['id'])['quantity'].should == -1
    ent = @cp.get_entitlement(ent['id'])

    @satellite_cp.consume_pool(@unlimited_master_pool['id'], {:quantity => 1})
    @cp.get_pool(@unlimited_bonus_pool['id'])['quantity'].should == 0

    lambda do
      @cp.get_entitlement(ent['id'])
    end.should raise_exception(RestClient::ResourceNotFound)
  end

  it 'should not revoke excess entitlements when unlimited virt limited master pool is consumed by non manifest consumer' do
    skip("candlepin running in standalone mode") unless is_hosted?
    ent = @guest_cp.consume_pool(@unlimited_bonus_pool['id'], {:quantity => 10})[0]
    ent.quantity.should == 10
    @cp.get_pool(@unlimited_bonus_pool['id'])['quantity'].should == -1

    # consumer master pool from non satellite consumer
    @guest_cp2.consume_pool(@unlimited_master_pool['id'], {:quantity => 1})

    @cp.get_pool(@unlimited_bonus_pool['id'])['quantity'].should == -1
    @unlimited_master_pool = @cp.get_pool(@unlimited_master_pool['id'])
    @unlimited_master_pool['consumed'].should == 1
    @unlimited_master_pool['exported'].should == 0

    #even if 1 qty is consumed but not exported, do not set bonus pool to qty 0
    @satellite_cp.consume_pool(@unlimited_master_pool['id'], {:quantity => 9})
    @cp.get_pool(@unlimited_bonus_pool['id'])['quantity'].should == -1
    @cp.get_entitlement(ent.id)['quantity'].should == 10
  end

end
