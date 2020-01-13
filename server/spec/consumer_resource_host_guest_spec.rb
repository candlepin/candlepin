# -*- coding: utf-8 -*-
require 'spec_helper'
require 'candlepin_scenarios'
require 'rexml/document'

describe 'Consumer Resource Host/Guest' do
  include CandlepinMethods
  include AttributeHelper

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

  it 'should allow adding guest ids to host consumer on update' do
    guests = [{'guestId' => 'guest1'}]

    user_cp = user_client(@owner1, random_string('test-user'))
    consumer = user_cp.register(random_string('host'), :system, nil,
      {}, nil, nil, [], [])
    consumer.should_not be_nil
    consumer['guestIds'].should == []

    consumer_client = Candlepin.new(nil, nil, consumer['idCert']['cert'], consumer['idCert']['key'])
    consumer_client.update_consumer({:guestIds => guests})

    guestIds = consumer_client.get_guestids()
    guestIds.length.should == 1
    guestIds[0]['guestId'].should == 'guest1'
  end

  it 'should allow updating guest ids from host consumer on update' do
    guests = [{'guestId' => 'guest1'},
              {'guestId' => 'guest2'}]

    user_cp = user_client(@owner1, random_string('test-user'))
    consumer = user_cp.register(random_string('host'), :system, nil,
      {}, nil, nil, [], [])

    consumer_client = Candlepin.new(nil, nil, consumer['idCert']['cert'], consumer['idCert']['key'])
    consumer_client.update_consumer({:guestIds => guests})

    consumer_client.get_guestids().length.should == 2

    consumer_client.update_consumer({:guestIds => [guests[1]]})
    guestIds = consumer_client.get_guestids()
    guestIds.length.should == 1
    guestIds[0]['guestId'].should == 'guest2'
  end

  it 'should not modify guest id list if guestIds list is null on update' do
    guests = [{'guestId' => 'guest1'}]

    user_cp = user_client(@owner1, random_string('test-user'))
    consumer = user_cp.register(random_string('host'), :system, nil,
      {}, nil, nil, [], [])

    consumer_client = Candlepin.new(nil, nil, consumer['idCert']['cert'], consumer['idCert']['key'])
    consumer_client.update_consumer({:guestIds => guests})
    consumer_client.get_guestids().length.should == 1

    consumer_client.update_consumer({:guestIds => nil})
    consumer_client.update_consumer({})
    guestIds = consumer_client.get_guestids()
    guestIds.length.should == 1
    guestIds[0]['guestId'].should == 'guest1'
  end

  it 'should clear guest ids when empty list is provided on update' do
    guests = [{'guestId' => 'guest1'}]

    user_cp = user_client(@owner1, random_string('test-user'))
    consumer = user_cp.register(random_string('host'), :system, nil,
      {}, nil, nil, [], [])

    consumer_client = Candlepin.new(nil, nil, consumer['idCert']['cert'], consumer['idCert']['key'])
    consumer_client.update_consumer({:guestIds => guests})
    consumer_client.get_guestids().length.should == 1

    consumer_client.update_consumer({:guestIds => []})
    consumer_client.get_guestids().length.should == 0
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
    consumer_client.update_consumer({:guestIds => guests})

    @cp.get_consumer_guests(host_consumer['uuid']).length.should == 2
  end

  it 'should allow host to list guests when guestids reported with reverse endianness' do
    uuid2 = random_string('system.uuid')
    guests = [{'guestId' => '78d7e200-b7d6-4cfe-b7a9-5700e8094df3'}, {'guestId' => uuid2}]

    user_cp = user_client(@owner1, random_string('test-user'))
    host_consumer = user_cp.register(random_string('host'), :system, nil,
      {}, nil, nil, [], [])
    # The virt-uuid has reversed-endianness in the first 3 sections
    guest_consumer1 = user_cp.register(random_string('guest'), :system, nil,
      {'virt.uuid' => '00e2d778-d6b7-fe4c-b7a9-5700e8094df3'}, nil, nil, [], [])
    guest_consumer2 = user_cp.register(random_string('guest'), :system, nil,
      {'virt.uuid' => uuid2}, nil, nil, [], [])

    consumer_client = Candlepin.new(nil, nil, host_consumer['idCert']['cert'], host_consumer['idCert']['key'])
    consumer_client.update_consumer({:guestIds => guests})

    @cp.get_consumer_guests(host_consumer['uuid']).length.should == 2
    # Verify the lookup works both ways
    @cp.get_consumer_host(guest_consumer1['uuid'])['uuid'].should == host_consumer['uuid']
    @cp.get_consumer_host(guest_consumer2['uuid'])['uuid'].should == host_consumer['uuid']
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
    consumer_client1.update_consumer({:guestIds => guests1})

    # MySQL before 5.6.4 doesn't store fractional seconds on timestamps
    # and getHost() method in ConsumerCurator (which is what tells us which
    # host a guest is associated with) sorts results by updated time.
    sleep 1

    consumer_client2 = Candlepin.new(nil, nil, host_consumer2['idCert']['cert'], host_consumer2['idCert']['key'])
    consumer_client2.update_consumer({:guestIds => guests2})

    guestList = @cp.get_consumer_guests(host_consumer1['uuid'])
    guestList.length.should == 1
    guestList[0]['uuid'].should == guest_consumer1['uuid']
  end

  it 'guest should list most current host' do
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
    guest_consumer2 = user_cp.register(random_string('guest'), :system, nil,
      {'virt.uuid' => uuid2}, nil, nil, [], [])

    consumer_client1 = Candlepin.new(nil, nil, host_consumer1['idCert']['cert'], host_consumer1['idCert']['key'])
    consumer_client1.update_consumer({:guestIds => guests1})

    # MySQL before 5.6.4 doesn't store fractional seconds on timestamps
    # and getHost() method in ConsumerCurator (which is what tells us which
    # host a guest is associated with) sorts results by updated time.
    sleep 1

    consumer_client2 = Candlepin.new(nil, nil, host_consumer2['idCert']['cert'], host_consumer2['idCert']['key'])
    consumer_client2.update_consumer({:guestIds => guests2})

    host1 = @cp.get_consumer_host(guest_consumer1['uuid'])
    host1['uuid'].should == host_consumer1['uuid']
    host2 = @cp.get_consumer_host(guest_consumer2['uuid'])
    host2['uuid'].should == host_consumer2['uuid']
  end

  it 'should ignore duplicate guest ids' do
    uuid1 = random_string('system.uuid')
    uuid2 = random_string('system.uuid')
    guests = [{'guestId' => uuid1}, {'guestId' => uuid1}, {'guestId' => uuid2},  {'guestId' => uuid2}]

    user_cp = user_client(@owner1, random_string('test-user'))
    host_consumer = user_cp.register(random_string('host'), :system, nil,
      {}, nil, nil, [], [])
    guest_consumer1 = user_cp.register(random_string('guest'), :system, nil,
      {'virt.uuid' => uuid1}, nil, nil, [], [])
    guest_consumer2 = user_cp.register(random_string('guest'), :system, nil,
      {'virt.uuid' => uuid2}, nil, nil, [], [])

    consumer_client = Candlepin.new(nil, nil, host_consumer['idCert']['cert'], host_consumer['idCert']['key'])
    consumer_client.update_consumer({:guestIds => guests})

    guest_consumers = @cp.get_consumer_guests(host_consumer['uuid'])
    expect(guest_consumers.length).to eq(2)

    guest_uuids = guest_consumers.map { |guest| guest['uuid'] }
    expect(guest_uuids).to contain_exactly(guest_consumer1['uuid'], guest_consumer2['uuid'])

  end

  it 'guest should not impose SLA on host auto-attach' do
    uuid1 = random_string('system.uuid')
    uuid2 = random_string('system.uuid')
    uuid3 = random_string('system.uuid')
    guests = [{'guestId' => uuid1}, {'guestId' => uuid2}, {'guestId' => uuid3}]

    provided_product = create_product(random_string('product'),
                                      random_string('product'),
                                      {:owner => @owner1['key']})

    vip_product = create_product(random_string('product'),
      random_string('product'),
      {:attributes => {:support_level => 'VIP',
                       :virt_limit => "5",
                       :host_limited => 'true'},
      :owner => @owner1['key'],
      :providedProducts => [provided_product.id]})

    std_product = create_product(random_string('product'),
      random_string('product'),
      {:attributes => {:support_level => 'Standard',
                       :virt_limit => "5",
                       :host_limited => 'true'},
      :owner => @owner1['key'],
      :providedProducts => [provided_product.id]})

    create_pool_and_subscription(@owner1['key'], vip_product.id, 10, [provided_product.id],
				'', '', '', nil, nil, true)
    create_pool_and_subscription(@owner1['key'], std_product.id, 10, [provided_product.id])

    installed = [
        {'productId' => provided_product.id, 'productName' => provided_product.name}]

    user_cp = user_client(@owner1, random_string('test-user'))
    host_consumer = user_cp.register(random_string('host'), :system, nil, {}, nil, nil, [], [])
    host_consumer['serviceLevel'].should == ''
    guest_consumer1 = user_cp.register(random_string('guest'), :system, nil,
      {'virt.uuid' => uuid1, 'virt.is_guest' => 'true'}, nil, nil, [], [])
    guest_consumer2 = user_cp.register(random_string('guest'), :system, nil,
      {'virt.uuid' => uuid2, 'virt.is_guest' => 'true'}, nil, nil, [], [])
    guest_consumer3 = user_cp.register(random_string('guest'), :system, nil,
      {'virt.uuid' => uuid3, 'virt.is_guest' => 'true'}, nil, nil, [], [])

    host_client = Candlepin.new(nil, nil, host_consumer['idCert']['cert'], host_consumer['idCert']['key'])
    host_client.update_consumer({:guestIds => guests})
    guest_client1 = Candlepin.new(nil, nil, guest_consumer1['idCert']['cert'], guest_consumer1['idCert']['key'])
    guest_client1.update_consumer({:serviceLevel => 'VIP', :installedProducts => installed})
    guest_client2 = Candlepin.new(nil, nil, guest_consumer2['idCert']['cert'], guest_consumer2['idCert']['key'])
    guest_client2.update_consumer({:serviceLevel => 'VIP', :installedProducts => installed})
    guest_client3 = Candlepin.new(nil, nil, guest_consumer3['idCert']['cert'], guest_consumer3['idCert']['key'])
    guest_client3.update_consumer({:serviceLevel => 'Standard', :installedProducts => installed})

    # first guest causes host to attach to pool
    # We no longer filter based on consumer/pool SLA match, but we highly prioritize, so the VIP SLA pool is attached.
    guest_client1.consume_product()

    guest_ents = guest_client1.list_entitlements()
    guest_ents.size.should == 1
    guest_ent = guest_ents[0]
    expect(get_attribute_value(guest_ent.pool['attributes'], 'requires_host')).to eq(host_consumer['uuid'])

    host_ents = host_client.list_entitlements()
    host_ents.size.should == 1
    host_ent = host_ents[0]
    expect(get_attribute_value(host_ent.pool['productAttributes'], 'support_level')).to eq('VIP')
    host_consumer = host_client.get_consumer()
    host_consumer['serviceLevel'].should == ''

    # second guest grabs the VIP pool because it is already available
    guest_client2.consume_product()

    guest_ents = guest_client2.list_entitlements()
    guest_ents.size.should == 1
    guest_ent = guest_ents[0]
    expect(get_attribute_value(guest_ent.pool['attributes'], 'requires_host')).to eq(host_consumer['uuid'])

    host_ents = host_client.list_entitlements()
    host_ents.size.should == 1
    host_ent = host_ents[0]
    expect(get_attribute_value(host_ent.pool['productAttributes'], 'support_level')).to eq('VIP')

    host_consumer = host_client.get_consumer()
    host_consumer['serviceLevel'].should == ''

    # third guest, even though has a Standard SLA, will not attach to the Standard pool
    # instead will attach to the available VIP pool, since we no longer match on SLA.
    guest_client3.consume_product()

    guest_ents = guest_client3.list_entitlements()
    guest_ents.size.should == 1
    guest_ent = guest_ents[0]

    expect(get_attribute_value(guest_ent.pool['attributes'], 'requires_host')).to eq(host_consumer['uuid'])
    expect(get_attribute_value(guest_ent.pool['productAttributes'], 'support_level')).to eq('VIP')

    host_consumer = host_client.get_consumer()
    host_consumer['serviceLevel'].should == ''
    host_ents = host_client.list_entitlements()
    host_ents.size.should == 1
    host_ent = host_ents[0]
    expect(get_attribute_value(host_ent.pool['productAttributes'], 'support_level')).to eq('VIP')
  end
end
