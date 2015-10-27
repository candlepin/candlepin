# -*- coding: utf-8 -*-
require 'spec_helper'
require 'candlepin_scenarios'
require 'rexml/document'

describe 'GuestId Resource' do

  include CandlepinMethods

  before(:each) do
    @owner1 = create_owner random_string('test_owner1')
    @username1 = random_string("user1")
    @consumername1 = random_string("consumer1")
    @user1 = user_client(@owner1, @username1)
    @consumer1 = consumer_client(@user1, @consumername1)

    @owner2 = create_owner random_string('test_owner2')
    @username2 = random_string("user2")
    @user2 = user_client(@owner2, @username2)
    @consumer2 = consumer_client(@user2, random_string("consumer2"))
  end

  it 'should allow adding guest ids to host consumer' do
    guests = [{'guestId' => 'guest1'}]

    user_cp = user_client(@owner1, random_string('test-user'))
    consumer = user_cp.register(random_string('host'), :system, nil,
      {}, nil, nil, [], [])
    consumer.should_not be_nil
    consumer['guestIds'].should be_nil

    consumer_client = Candlepin.new(nil, nil, consumer['idCert']['cert'], consumer['idCert']['key'])
    consumer_client.update_guestids(guests)

    guest_ids = consumer_client.get_guestids()
    guest_ids.length.should == 1
    guest_ids[0]['guestId'].should == 'guest1'
  end

  it 'should allow updating guest ids from host consumer' do
    guests = [{'guestId' => 'guest1'},
              {'guestId' => 'guest2'}]

    user_cp = user_client(@owner1, random_string('test-user'))
    consumer = user_cp.register(random_string('host'), :system, nil,
      {}, nil, nil, [], [])

    consumer_client = Candlepin.new(nil, nil, consumer['idCert']['cert'], consumer['idCert']['key'])
    consumer_client.update_guestids(guests)

    guest_ids = consumer_client.get_guestids()
    guest_ids.length.should == 2

    consumer_client.update_guestids([guests[1]])
    guest_ids = consumer_client.get_guestids()
    guest_ids.length.should == 1
    guest_ids[0]['guestId'].should == 'guest2'
  end

  it 'should clear guest ids when empty list is provided' do
    guests = [{'guestId' => 'guest1'}]

    user_cp = user_client(@owner1, random_string('test-user'))
    consumer = user_cp.register(random_string('host'), :system, nil,
      {}, nil, nil, [], [])

    consumer_client = Candlepin.new(nil, nil, consumer['idCert']['cert'], consumer['idCert']['key'])
    consumer_client.update_guestids(guests)

    guest_ids = consumer_client.get_guestids()
    guest_ids.length.should == 1

    consumer_client.update_guestids([])
    guest_ids = consumer_client.get_guestids()
    guest_ids.length.should == 0
  end

  it 'should allow host to list guests' do
    uuid1 = random_string('system.uuid')
    uuid2 = random_string('system.uuid')
    guests = [{'guestId' => uuid1}, {'guestId' => uuid2}]

    user_cp = user_client(@owner1, random_string('test-user'))
    host_consumer = user_cp.register(random_string('host'), :system, nil,
      {}, nil, nil, [], [])
    user_cp.register(random_string('guest'), :system, nil,
      {'virt.uuid' => uuid1}, nil, nil, [], [])
    user_cp.register(random_string('guest'), :system, nil,
      {'virt.uuid' => uuid2}, nil, nil, [], [])

    consumer_client = Candlepin.new(nil, nil, host_consumer['idCert']['cert'], host_consumer['idCert']['key'])
    consumer_client.update_guestids(guests)

    @cp.get_consumer_guests(host_consumer['uuid']).length.should == 2
  end

  it 'should not allow host to list guests that another host has claimed' do
    uuid1 = random_string('system.uuid')
    uuid2 = random_string('system.uuid')
    guests1 = [{'guestId' => uuid1}, {'guestId' => uuid2}]
    guests2 = [{'guestId' => uuid2}]

    user_cp = user_client(@owner1, random_string('test-user'))
    host_consumer1 = user_cp.register(random_string('host'), :system, nil,
      {}, nil, nil, [], [])
    host_consumer2 = user_cp.register(random_string('host'), :system, nil,
      {}, nil, nil, [], [])
    guest_consumer1 = user_cp.register(random_string('guest'), :system, nil,
      {'virt.uuid' => uuid1}, nil, nil, [], [])
    user_cp.register(random_string('guest'), :system, nil,
      {'virt.uuid' => uuid2}, nil, nil, [], [])

    consumer_client1 = Candlepin.new(nil, nil, host_consumer1['idCert']['cert'], host_consumer1['idCert']['key'])
    consumer_client1.update_guestids(guests1)

    # MySQL before 5.6.4 doesn't store fractional seconds on timestamps
    # and getHost() method in ConsumerCurator (which is what tells us which
    # host a guest is associated with) sorts results by updated time.
    sleep 1

    consumer_client2 = Candlepin.new(nil, nil, host_consumer2['idCert']['cert'], host_consumer2['idCert']['key'])
    consumer_client2.update_guestids(guests2)

    guestList = @cp.get_consumer_guests(host_consumer1['uuid'])
    guestList.length.should == 1
    guestList[0]['uuid'].should == guest_consumer1['uuid']
  end

  it 'should allow a single guest to be deleted' do
    uuid1 = random_string('system.uuid')
    guests = [{'guestId' => uuid1}]
    user_cp = user_client(@owner1, random_string('test-user'))
    host_consumer = user_cp.register(random_string('host'), :system, nil,
      {}, nil, @owner1['key'], [], [])
    consumer_client = Candlepin.new(nil, nil, host_consumer['idCert']['cert'], host_consumer['idCert']['key'])
    consumer_client.update_guestids(guests)
    consumer_client.get_guestids().length.should == 1
    consumer_client.delete_guestid(guests[0]['guestId'])
    consumer_client.get_guestids().length.should == 0
  end

  it 'should allow a single guest to be updated and revokes host-limited ents' do
    uuid1 = random_string('system.uuid')
    guests = [{:guestId => uuid1}]

    user_cp = user_client(@owner1, random_string('test-user'))
    host_consumer = user_cp.register(random_string('host'), :system, nil,
      {}, nil, @owner1['key'], [], [])
    new_host_consumer = user_cp.register(random_string('host'), :system, nil,
      {}, nil, @owner1['key'], [], [])
    guest_consumer = user_cp.register(random_string('guest'), :system, nil,
      {'virt.uuid' => uuid1, 'virt.is_guest' => 'true'}, nil, @owner1['key'], [], [])
    # Create a product/subscription
    super_awesome = create_product(nil, random_string('super_awesome'),
                            :attributes => { "virt_limit" => "10", "host_limited" => "true" }, :owner => @owner1['key'])
    create_pool_and_subscription(@owner1['key'], super_awesome.id, 20)
    consumer_client = Candlepin.new(nil, nil, host_consumer['idCert']['cert'], host_consumer['idCert']['key'])
    new_consumer_client = Candlepin.new(nil, nil, new_host_consumer['idCert']['cert'], new_host_consumer['idCert']['key'])
    guest_client = Candlepin.new(nil, nil, guest_consumer['idCert']['cert'], guest_consumer['idCert']['key'])
    consumer_client.update_guestids(guests)
    pools = consumer_client.list_pools :consumer => host_consumer['uuid']
    pools.length.should == 1
    consumer_client.consume_pool(pools[0]['id'], {:quantity => 1})

    @cp.refresh_pools(@owner1['key'])
    pools = guest_client.list_pools :consumer => guest_consumer['uuid']

    # The original pool and the new host limited pool should be available
    pools.length.should == 2
    # Get the guest pool
    guest_pool = pools.find_all { |i| !i['sourceEntitlement'].nil? }[0]

    # Make sure the required host is actually the host
    requires_host = guest_pool['attributes'].find_all {
      |i| i['name'] == 'requires_host' }[0]
    requires_host['value'].should == host_consumer['uuid']

    # Consume the host limited pool
    guest_client.consume_pool(guest_pool['id'], {:quantity => 1})

    # Should have a host with 1 registered guest
    guestList = @cp.get_consumer_guests(host_consumer['uuid'])
    guestList.length.should == 1
    guestList[0]['uuid'].should == guest_consumer['uuid']

    guest_client.list_entitlements().length.should == 1
    # Updating to a new host should remove host specific entitlements
    new_consumer_client.update_guestid(guests[0])

    consumer_client.get_guestids().length.should == 0
    new_consumer_client.get_guestids().length.should == 1

    # The guests host limited entitlement should be gone
    guest_client.list_entitlements().length.should == 0
  end

  it 'should allow a single guest to be updated and not revoke host-limited ents' do
    uuid1 = random_string('system.uuid')
    guests = [{:guestId => uuid1}]

    user_cp = user_client(@owner1, random_string('test-user'))
    host_consumer = user_cp.register(random_string('host'), :system, nil,
      {}, nil, @owner1['key'], [], [])
    guest_consumer = user_cp.register(random_string('guest'), :system, nil,
      {'virt.uuid' => uuid1, 'virt.is_guest' => 'true'}, nil, @owner1['key'], [], [])
    # Create a product/subscription
    super_awesome = create_product(nil, random_string('super_awesome'),
                            :attributes => { "virt_limit" => "10", "host_limited" => "true" }, :owner => @owner1['key'])
    create_pool_and_subscription(@owner1['key'], super_awesome.id, 20)

    consumer_client = Candlepin.new(nil, nil, host_consumer['idCert']['cert'], host_consumer['idCert']['key'])
    guest_client = Candlepin.new(nil, nil, guest_consumer['idCert']['cert'], guest_consumer['idCert']['key'])
    consumer_client.update_guestids(guests)
    pools = consumer_client.list_pools :consumer => host_consumer['uuid']
    pools.length.should == 1
    consumer_client.consume_pool(pools[0]['id'], {:quantity => 1})

    @cp.refresh_pools(@owner1['key'])
    pools = guest_client.list_pools :consumer => guest_consumer['uuid']

    # The original pool and the new host limited pool should be available
    pools.length.should == 2

    # Get the guest pool
    guest_pool = pools.find_all { |i| !i['sourceEntitlement'].nil? }[0]

    # Make sure the required host is actually the host
    requires_host = guest_pool['attributes'].find_all {
      |i| i['name'] == 'requires_host' }[0]
    requires_host['value'].should == host_consumer['uuid']

    # Consume the host limited pool
    guest_client.consume_pool(guest_pool['id'], {:quantity => 1})

    # Should have a host with 1 registered guest
    guestList = @cp.get_consumer_guests(host_consumer['uuid'])
    guestList.length.should == 1
    guestList[0]['uuid'].should == guest_consumer['uuid']

    guest_client.list_entitlements().length.should == 1
    # Updating to the same host shouldn't revoke entitlements
    consumer_client.update_guestid({:guestId => uuid1,
      :attributes => {"some attr" => "crazy new value"}})

    consumer_client.get_guestids().length.should == 1
    guest_client.list_entitlements().length.should == 1
  end
  it 'should not allow a consumer to unregister another consumer durring guest deletion' do
    uuid1 = random_string('system.uuid')
    guests = [{'guestId' => uuid1}]

    user_cp = user_client(@owner1, random_string('test-user'))
    host_consumer = user_cp.register(random_string('host'), :system, nil,
      {}, nil, @owner1['key'], [], [])
    guest_consumer = user_cp.register(random_string('guest'), :system, nil,
      {'virt.uuid' => uuid1, 'virt.is_guest' => 'true'}, nil, @owner1['key'], [], [])

    consumer_client = Candlepin.new(nil, nil, host_consumer['idCert']['cert'], host_consumer['idCert']['key'])
    consumer_client.update_guestids(guests)

    # Should have a host with 1 registered guest
    guestList = @cp.get_consumer_guests(host_consumer['uuid'])
    guestList.length.should == 1
    guestList[0]['uuid'].should == guest_consumer['uuid']

    # Delete should fail to delete the consumer
    lambda do
      consumer_client.delete_guestid(guests[0]['guestId'], true)
    end.should raise_exception(RestClient::Forbidden)
  end

  it 'should allow updating a single guests attributes' do
    uuid1 = random_string('guestid')
    guests = [{:guestId => uuid1, :attributes => {'test' => 'hello'}}]
    user_cp = user_client(@owner1, random_string('test-user'))
    host_consumer = user_cp.register(random_string('host'), :system, nil,
      {}, nil, @owner1['key'], [], [])
    consumer_client = Candlepin.new(nil, nil, host_consumer['idCert']['cert'], host_consumer['idCert']['key'])
    consumer_client.update_guestids(guests)

    guest_to_update = {:guestId => uuid1, :attributes => {'some_attr' => 'some_value'}}
    consumer_client.update_guestid(guest_to_update)

    guest = consumer_client.get_guestid(uuid1)
    guest['attributes']['some_attr'].should == 'some_value'
  end

  it 'should allow creation of a new guest via update' do
    user_cp = user_client(@owner1, random_string('test-user'))
    host_consumer = user_cp.register(random_string('host'), :system, nil,
      {}, nil, @owner1['key'], [], [])
    consumer_client = Candlepin.new(nil, nil, host_consumer['idCert']['cert'], host_consumer['idCert']['key'])

    uuid1 = random_string('guestid')
    guest_to_update = {:guestId => uuid1, :attributes => {'some_attr' => 'some_value'}}
    consumer_client.update_guestid(guest_to_update)

    guest = consumer_client.get_guestid(uuid1)
    guest['attributes']['some_attr'].should == 'some_value'
  end

  it 'should not rewrite existing guest ids on host consumer' do
    guests = [{'guestId' => 'guest1'},
              {'guestId' => 'guest2'}]

    user_cp = user_client(@owner1, random_string('test-user'))
    consumer = user_cp.register(random_string('host'), :system, nil,
      {}, nil, nil, [], [])

    consumer_client = Candlepin.new(nil, nil, consumer['idCert']['cert'], consumer['idCert']['key'])
    consumer_client.update_guestids(guests)

    guest_ids = consumer_client.get_guestids()
    guest_ids.length.should == 2

    updated = nil
    guest_ids.each do |gi|
      if gi['guestId'] == 'guest2'
        updated = gi['updated']
      end
    end
    updated.should_not be_nil

    consumer_client.update_guestids([guests[1]])
    guest_ids = consumer_client.get_guestids()
    guest_ids.length.should == 1
    guest_ids[0]['guestId'].should == 'guest2'
    guest_ids[0]['updated'].should == updated
  end
end
