# -*- coding: utf-8 -*-
require 'spec_helper'
require 'candlepin_scenarios'
require 'rexml/document'

describe 'Consumer Resource Activation Key' do

  include CandlepinMethods

  before(:each) do
    @owner = create_owner random_string('owner')
    @user = user_client(@owner, random_string("user"))
    # Connect without any credentials:
    @client = Candlepin.new

  end

  it 'should allow a consumer to register with activation keys' do
    prod1 = create_product(random_string('product1'), random_string('product1'),
                           :attributes => { :'multi-entitlement' => 'yes'})
    create_pool_and_subscription(@owner['key'], prod1.id, 10)
    pool1 = @cp.list_pools({:owner => @owner['id']}).first

    key1 = @cp.create_activation_key(@owner['key'], 'key1')
    @cp.add_pool_to_key(key1['id'], pool1['id'], 3)
    @cp.create_activation_key(@owner['key'], 'key2')
    consumer = @client.register(random_string('machine1'), :system, nil, {}, nil,
      @owner['key'], ["key1", "key2"])
    consumer.uuid.should_not be_nil
    @cp.get_pool(pool1.id).consumed.should == 3
  end

  it 'should allow a consumer to register with an activation key with an auto-attach' do
    # create extra product/pool to show selectivity
    prod1 = create_product(random_string('product1'), random_string('product1'))
    prod2 = create_product(random_string('product2'), random_string('product2'))
    create_pool_and_subscription(@owner['key'], prod1.id, 10, [], '', '', '', nil, nil, true)
    create_pool_and_subscription(@owner['key'], prod2.id, 10)
    pool1 = @cp.list_pools(:owner => @owner['id'], :product => prod1.id).first

    key1 = @cp.create_activation_key(@owner['key'], 'key1')
    @cp.update_activation_key({'id' => key1['id'], "autoAttach" => "true"})
    @cp.add_pool_to_key(key1['id'], pool1['id'])
    @cp.add_prod_id_to_key(key1['id'], prod1.id)
    consumer = @client.register(random_string('machine1'), :system, nil, {}, nil,
      @owner['key'], ["key1"])
    consumer.uuid.should_not be_nil
    @cp.get_pool(pool1.id).consumed.should == 1
  end

 it 'should allow a consumer to register with activation keys with content overrides' do
    key1 = @cp.create_activation_key(@owner['key'], 'key1')

    override = {"name" => "somename", "value" => "someval", "contentLabel" => "somelabel"}
    @cp.add_content_overrides_to_key(key1['id'], [override])

    consumer = @client.register(random_string('machine1'), :system, nil, {}, nil,
      @owner['key'], ["key1"])
    consumer.uuid.should_not be_nil

    consumer_overrides = @cp.get_content_overrides(consumer['uuid'])
    consumer_overrides.length.should == 1
    consumer_overrides[0]['name'].should == "somename"
    consumer_overrides[0]['value'].should == "someval"
    consumer_overrides[0]['contentLabel'].should == "somelabel"
  end

  it 'should allow a consumer to register with activation keys with release' do
    key1 = @cp.create_activation_key(@owner['key'], 'key1')

    @cp.update_activation_key({'id' => key1['id'], 'releaseVer' => "Registration Release"})

    consumer = @client.register(random_string('machine1'), :system, nil, {}, nil,
      @owner['key'], ["key1"])
    consumer.uuid.should_not be_nil

    consumer.releaseVer.releaseVer.should == "Registration Release"
  end

  it 'should allow a consumer to register with activation keys with service level' do
    prod1 = create_product(random_string('product1'), random_string('product1'),
                           :attributes => { :'support_level' => 'VIP'})
    create_pool_and_subscription(@owner['key'], prod1.id, 10)

    key1 = @cp.create_activation_key(@owner['key'], 'key1', 'VIP')
    key2 = @cp.create_activation_key(@owner['key'], 'key2')

    @cp.update_activation_key({'id' => key1['id'], 'releaseVer' => "Registration Release"})

    consumer = @client.register(random_string('machine1'), :system, nil, {}, nil,
      @owner['key'], ["key1", "key2"])
    consumer.uuid.should_not be_nil

    consumer.releaseVer.releaseVer.should == "Registration Release"
    consumer.serviceLevel.should == "VIP"
  end

  it 'should allow a consumer to register with multiple activation keys with same content override names' do
    key1 = @cp.create_activation_key(@owner['key'], 'key1')
    key2 = @cp.create_activation_key(@owner['key'], 'key2')

    override1 = {"name" => "somename", "value" => "someval", "contentLabel" => "somelabel"}
    override2 = {"name" => "somename", "value" => "otherval", "contentLabel" => "somelabel"}
    @cp.add_content_overrides_to_key(key1['id'], [override1, override2])

    consumer = @client.register(random_string('machine1'), :system, nil, {}, nil,
      @owner['key'], ["key2", "key1"])
    consumer.uuid.should_not be_nil

    consumer_overrides = @cp.get_content_overrides(consumer['uuid'])
    consumer_overrides.length.should == 1
    # The order doesn't appear to be preserved, so we dont know which override will be applied
    # We really just don't want an exception
  end

  it 'should allow a consumer to register with activation keys with null quantity' do
    prod1 = create_product(random_string('product1'), random_string('product1'),
                           :attributes => { :'multi-entitlement' => 'yes',
                                            :'stacking_id' => random_string('stacking_id'),
                                            :'sockets' => '1'})
    create_pool_and_subscription(@owner['key'], prod1.id, 10)
    pool1 = @cp.list_pools({:owner => @owner['id']}).first

    act_key1 = @cp.create_activation_key(@owner['key'], 'act_key1')

    # null quantity
    @cp.add_pool_to_key(act_key1['id'], pool1['id'])
    @cp.create_activation_key(@owner['key'], 'act_key2')

    consumer = @client.register(random_string('machine1'), :system, nil, {'cpu.cpu_socket(s)' => '4'}, nil,
      @owner['key'], ["act_key1", "act_key2"])

    consumer.uuid.should_not be_nil
    @cp.get_pool(pool1.id).consumed.should == 4
  end

  it 'should allow a consumer to register with an activation key with an auto-attach across pools' do
    # create extra product/pool to show selectivity
    prod1 = create_product(random_string('product1'), random_string('product1'))
    create_pool_and_subscription(@owner['key'], prod1.id, 1, [], '', '', '', nil, nil, true)
    create_pool_and_subscription(@owner['key'], prod1.id, 1, [], '', '', '', nil, nil, true)
    create_pool_and_subscription(@owner['key'], prod1.id, 2)
    pools = @cp.list_pools(:owner => @owner['id'], :product => prod1.id)

    key1 = @cp.create_activation_key(@owner['key'], 'key1')
    @cp.update_activation_key({'id' => key1['id'], "autoAttach" => "true"})
    @cp.add_pool_to_key(key1['id'], pools[0]['id'], 1)
    @cp.add_pool_to_key(key1['id'], pools[1]['id'], 1)
    @cp.add_pool_to_key(key1['id'], pools[2]['id'], 1)
    @cp.add_prod_id_to_key(key1['id'], prod1.id)
    consumer1 = @client.register(random_string('machine'), :system, nil, {}, nil,
      @owner['key'], ["key1"])
    consumer1.uuid.should_not be_nil
    @cp.list_entitlements(:uuid => consumer1.uuid).size.should == 1

    consumer2 = @client.register(random_string('machine'), :system, nil, {}, nil,
      @owner['key'], ["key1"])
    consumer2.uuid.should_not be_nil
    @cp.list_entitlements(:uuid => consumer2.uuid).size.should == 1

    consumer3 = @client.register(random_string('machine'), :system, nil, {}, nil,
      @owner['key'], ["key1"])
    consumer3.uuid.should_not be_nil
    @cp.list_entitlements(:uuid => consumer3.uuid).size.should == 1

    consumer4 = @client.register(random_string('machine'), :system, nil, {}, nil,
      @owner['key'], ["key1"])
    consumer4.uuid.should_not be_nil
    @cp.list_entitlements(:uuid => consumer4.uuid).size.should == 1

    pools.each do |p|
      this_pool = @cp.get_pool(p.id)
      this_pool.consumed.should == this_pool.quantity
    end

    consumer5 = @client.register(random_string('machine'), :system, nil, {}, nil,
      @owner['key'], ["key1"])
    consumer5.uuid.should_not be_nil
    @cp.list_entitlements(:uuid => consumer5.uuid).size.should == 0
  end

end
