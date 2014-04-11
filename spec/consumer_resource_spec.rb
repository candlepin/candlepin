# -*- coding: utf-8 -*-
require 'spec_helper'
require 'candlepin_scenarios'
require 'rexml/document'

describe 'Consumer Resource' do

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

  it "should block consumers from using other org's pools" do
    product1 = create_product
    sub = @cp.create_subscription(@owner1['key'], product1.id)
    @cp.refresh_pools(@owner1['key'])
    pool = @consumer1.list_pools({:owner => @owner1['id']}).first
    lambda {
      @consumer2.consume_pool(pool.id, {:quantity => 1}).size.should == 1
    }.should raise_exception(RestClient::ResourceNotFound)
  end

  it "should expose a consumer's event atom feed" do
    atom = @consumer1.list_consumer_events_atom(@consumer1.uuid)
    doc = REXML::Document.new(atom)
    events = REXML::XPath.match(doc, "//*[local-name()='event'][type = 'CREATED' and target ='CONSUMER']")
    events.should have(1).things

    # Consumer 2 should not be able to see consumer 1's feed:
    lambda {
      @consumer2.list_consumer_events_atom(@consumer1.uuid)
    }.should raise_exception(RestClient::ResourceNotFound)
  end

  it "should expose a consumer's events" do
    events = @consumer1.list_consumer_events(@consumer1.uuid)
    events.size.should be > 0

    # Events are sorted in order of descending timestamp, so the first
    # event should be consumer created:
    events[-1]['target'].should == 'CONSUMER'
    events[-1]['type'].should == 'CREATED'
    events[-1]['principal']['name'].should == @username1

    # Consumer 2 should not be able to see consumer 1's feed:
    lambda {
      @consumer2.list_consumer_events(@consumer1.uuid)
    }.should raise_exception(RestClient::ResourceNotFound)
  end

  it 'should receive paged data back when requested' do
    (1..4).each do |i|
      consumer_client(@user1, random_string('system'))
    end
    consumers = @cp.list_consumers({:page => 1, :per_page => 2, :sort_by => "id", :order => "asc"})
    consumers.length.should == 2
    (consumers[0].id <=> consumers[1].id).should == -1
  end

  it 'should set compliance status and update compliance status' do
    @consumer1.get_consumer()['entitlementStatus'].should == "valid"
    product1 = create_product(random_string('product'), random_string('product'))
    installed = [
        {'productId' => product1.id, 'productName' => product1.name}
    ]
    @consumer1.update_consumer({:installedProducts => installed})
    @consumer1.get_consumer()['entitlementStatus'].should == "invalid"

    subs = @cp.create_subscription(@owner1['key'], product1.id)
    @cp.refresh_pools(@owner1['key'])
    pool = @consumer1.list_pools({:owner => @owner1['id']}).first

    @consumer1.consume_pool(pool.id, {:quantity => 1}).size.should == 1
    @consumer1.get_consumer()['entitlementStatus'].should == "valid"
  end

  it 'should list compliances' do
    additionalConsumer = consumer_client(@user1, random_string("additionalConsumer"))
    results = @user1.get_compliance_list([@consumer1.uuid, additionalConsumer.uuid])
    results.length.should == 2
    results.has_key?(additionalConsumer.uuid).should == true
    results.has_key?(@consumer1.uuid).should == true
  end

  it 'should filter compliances the user does not own' do
    results = @user1.get_compliance_list([@consumer1.uuid, @consumer2.uuid])
    results.size.should == 1
    results[@consumer1.uuid].should_not be_nil
  end

  it 'should return a 410 for deleted consumers' do
    @consumer1.unregister(@consumer1.uuid)
    lambda do
      @cp.get_consumer(@consumer1.uuid)
    end.should raise_exception(RestClient::Gone)
  end

  it 'should return a 410 for a consumer with an invalid identity cert' do
    @consumer1.unregister
    lambda do
      @consumer1.unregister
    end.should raise_exception(RestClient::Gone)
  end

  #TODO Get this working in parallel
  it 'allows super admins to see all consumers', :serial => true do
    uuids = []
    @cp.list_consumers.each do |c|
      uuids << c['uuid']
      # Consumer lists should not have idCert or facts:
      c['facts'].should be_nil
      c['idCert'].should be_nil
    end
    uuids.include?(@consumer1.uuid).should be_true
    uuids.include?(@consumer2.uuid).should be_true
  end

  it 'allows super admins to query consumers by id' do
    # Create a consumer that should not be in the list of returned results
    consumer3 = consumer_client(@user2, random_string("consumer3"))
    returned_uuids = []
    @cp.list_consumers({:uuids => [@consumer1.uuid, @consumer2.uuid]}).each do |c|
      returned_uuids << c['uuid']
    end
    returned_uuids.include?(@consumer1.uuid).should be_true
    returned_uuids.include?(@consumer2.uuid).should be_true
    returned_uuids.length.should == 2
  end

  it 'lets an owner admin see only their consumers' do
    @user2.list_consumers({:owner => @owner2['key']}).length.should == 1
  end

  #TODO Get this working in parallel
  it 'lets a super admin filter consumers by owner', :serial => true do
    @cp.list_consumers.size.should be > 1
    @cp.list_consumers({:owner => @owner1['key']}).size.should == 1
  end


  it 'lets an owner see only their system consumer types' do
    @user1.list_consumers({:type => 'system', :owner => @owner1['key']}).length.should == 1
  end

  it 'lets a super admin see a peson consumer with a given username' do

    username = random_string("user1")
    user1 = user_client(@owner1, username)
    consumer1 = consumer_client(user1, random_string("consumer1"), 'person')

    @cp.list_consumers({:type => 'person',
                       :username => username}).length.should == 1
  end

  it 'lets a super admin create person consumer for another user' do
    owner1 = create_owner random_string('test_owner1')
    username = random_string "user1"
    user1 = user_client(owner1, username)
    consumer1 = consumer_client(@cp, random_string("consumer1"), 'person',
                                username, {}, owner1['key'])

    @cp.list_consumers({:type => 'person',
        :username => username, :owner => owner1['key']}).length.should == 1
  end

  it 'does not let an owner admin create person consumer for another owner' do
    owner1 = create_owner random_string('test_owner1')
    username = random_string "user1"
    user1 = user_client(owner1, username)

    owner2 = create_owner random_string('test_owner2')
    user2 = user_client(owner2, random_string("user2"))

    lambda do
      consumer_client(user2, random_string("consumer1"), 'person',
                      username)
    end.should raise_exception(RestClient::ResourceNotFound)
  end


  # note all of this consumer names fail with the current defauly
  # consumer_name_pattern, but should be okay with a more open one
  it 'does not let a super admin create consumer with invalid name' do
    owner1 = create_owner random_string('test_owner1')
    username = random_string "user1"
    user1 = user_client(owner1, username)

    lambda do
      consumer_client(user1, "")
    end.should raise_exception(RestClient::BadRequest)

    lambda do
      consumer_client(user1, "#something")
    end.should raise_exception(RestClient::BadRequest)

    lambda do
      consumer_client(user1, "bar$%camp")
    end.should raise_exception(RestClient::BadRequest)

    lambda do
      consumer_client(user1, "문자열이 아님 ")
    end.should raise_exception(RestClient::BadRequest)
  end

  it 'returns a 404 for a non-existant consumer' do
    lambda do
      @cp.get_consumer('fake-uuid')
    end.should raise_exception(RestClient::ResourceNotFound)
  end

  it 'lets a consumer view their own information' do
    owner1 = create_owner random_string('test_owner1')
    user1 = user_client(owner1, random_string("user1"))
    consumer1 = consumer_client(user1, random_string("consumer1"))

    consumer = consumer1.get_consumer(consumer1.uuid)
    consumer['uuid'].should == consumer1.uuid
  end

  it "does not let a consumer view another consumer's information" do
    owner1 = create_owner random_string('test_owner1')
    user1 = user_client(owner1, random_string("user1"))
    consumer1 = consumer_client(user1, random_string("consumer1"))
    consumer2 = consumer_client(user1, random_string("consumer2"))

    lambda do
      consumer1.get_consumer(consumer2.uuid)
    end.should raise_exception(RestClient::ResourceNotFound)
  end

  it "does not let an owner register with UUID of another owner's consumer" do
    linux_net = create_owner(random_string('linux_net'))
    greenfield = create_owner(random_string('greenfield_consulting'))

    linux_bill = user_client(linux_net, random_string('bill'))
    green_ralph = user_client(greenfield, random_string('ralph'))

    system1 = linux_bill.register(random_string('system1'))

    lambda do
      green_ralph.register(random_string('system2'), :system, system1.uuid)
    end.should raise_exception(RestClient::BadRequest)
  end

  it "should let a consumer register with a hypervisorId" do
    some_owner = create_owner(random_string('someowner'))
    client = user_client(some_owner, random_string('bob'))
    consumer = client.register(random_string('system1'), :system, random_string("someuuid"), {}, random_string("uname"), some_owner['key'], [], [], nil, [], "aBcD")
    # hypervisorId should always be set to lower case for the database constraint
    consumer['hypervisorId']['hypervisorId'].should == "abcd"
  end

  it "should not let a consumer register with a used hypervisorId in same the org" do
    some_owner = create_owner(random_string('someowner'))
    client = user_client(some_owner, random_string('bob'))
    consumer = client.register(random_string('system1'), :system, random_string("someuuid"), {}, random_string("uname"), some_owner['key'], [], [], nil, [], "aBcD")
    # hypervisorId should always be set to lower case for the database constraint
    consumer['hypervisorId']['hypervisorId'].should == "abcd"

    lambda do
      consumer2 = client.register(random_string('system2'), :system, random_string("someuuid"), {}, random_string("uname"), some_owner['key'], [], [], nil, [], "abCd")
    end.should raise_exception(RestClient::BadRequest)
  end

  it "does not let an owner reregister another owner's consumer" do
    linux_net = create_owner(random_string('linux_net'))
    greenfield = create_owner(random_string('greenfield_consulting'))

    linux_bill = user_client(linux_net, random_string('bill'))
    green_ralph = user_client(greenfield, 'ralph')

    system1 = linux_bill.register(random_string('system1'))

    lambda do
      green_ralph.regenerate_identity_certificate(system1.uuid)
    end.should raise_exception(RestClient::ResourceNotFound)
  end

  it 'should allow consumer to bind to products supporting multiple architectures' do
    owner = create_owner random_string('owner')
    owner_client = user_client(owner, random_string('testowner'))
    cp_client = consumer_client(owner_client, random_string('consumer123'), :system,
                                nil, 'uname.machine' => 'x86_64')
    prod = create_product(random_string('product'), random_string('product-multiple-arch'),
                          :attributes => { :arch => 'i386, x86_64'})
    subs = @cp.create_subscription(owner['key'], prod.id)
    @cp.refresh_pools(owner['key'])
    pool = cp_client.list_pools({:owner => owner['id']}).first

    cp_client.consume_pool(pool.id, {:quantity => 1}).size.should == 1
  end

  it 'updates consumer updated timestamp on bind' do
    consumer = @user1.register(random_string("meow"))
    consumer_client = Candlepin.new(username=nil, password=nil,
        cert=consumer['idCert']['cert'],
        key=consumer['idCert']['key'])

    prod = create_product()
    subs = @cp.create_subscription(@owner1['key'], prod['id'])
    @cp.refresh_pools(@owner1['key'])
    pool = consumer_client.list_pools({:owner => @owner1['id']}).first

    # Do a bind and make sure the updated timestamp changed:
    old_updated = @cp.get_consumer(consumer['uuid'])['updated']

    # MySQL before 5.6.4 doesn't store fractional seconds on timestamps.
    sleep 1

    consumer_client.consume_pool(pool['id'], {:quantity => 1})
    @cp.get_consumer(consumer['uuid'])['updated'].should_not == old_updated
  end

  it 'should allow consumer to bind to products based on product socket quantity across pools' do
    owner = create_owner random_string('owner')
    owner_client = user_client(owner, random_string('testowner'))
    cp_client = consumer_client(owner_client, random_string('consumer123'), :system,
                                nil, 'cpu.cpu_socket(s)' => '4')
    prod1 = create_product(random_string('product'), random_string('product-stackable'),
                          :attributes => { :sockets => '2', :'multi-entitlement' => 'yes', :stacking_id => '8888'})
    prod2 = create_product(random_string('product'), random_string('product-stackable'),
                          :attributes => { :sockets => '2', :'multi-entitlement' => 'yes', :stacking_id => '8888'})
    @cp.create_subscription(owner['key'], prod1.id, 1)
    @cp.create_subscription(owner['key'], prod1.id, 1)
    @cp.create_subscription(owner['key'], prod2.id, 1)

    @cp.refresh_pools(owner['key'])

    total = 0
    cp_client.consume_product(prod1.id).each {|ent|  total += ent.quantity}
    total.should == 2

  end

  it 'should allow a consumer to specify their own UUID' do
    owner = create_owner random_string('owner')
    user = user_client(owner, random_string('billy'))

    consumer = user.register(random_string('machine1'), :system, 'custom-uuid')
    consumer.uuid.should == 'custom-uuid'
  end

  it 'should allow a consumer to register with activation keys' do
    owner = create_owner random_string('owner')

    user1 = user_client(owner, random_string("user1"))
    prod1 = create_product(random_string('product1'), random_string('product1'),
                           :attributes => { :'multi-entitlement' => 'yes'})
    subs1 = @cp.create_subscription(owner['key'], prod1.id, 10)
    @cp.refresh_pools(owner['key'])
    pool1 = @cp.list_pools({:owner => owner['id']}).first

    # Connect without any credentials:
    client = Candlepin.new

    key1 = @cp.create_activation_key(owner['key'], 'key1')
    @cp.add_pool_to_key(key1['id'], pool1['id'], 3)
    @cp.create_activation_key(owner['key'], 'key2')
    consumer = client.register(random_string('machine1'), :system, nil, {}, nil,
      owner['key'], ["key1", "key2"])
    consumer.uuid.should_not be_nil

    # TODO: Verify activation keys did what we expect once they are functional
    @cp.get_pool(pool1.id).consumed.should == 3
  end

  it 'should allow a consumer to register with activation keys with content overrides' do
    owner = create_owner random_string('owner')

    user1 = user_client(owner, random_string("user1"))

    # Connect without any credentials:
    client = Candlepin.new

    key1 = @cp.create_activation_key(owner['key'], 'key1')

    override = {"name" => "somename", "value" => "someval", "contentLabel" => "somelabel"}
    @cp.add_content_overrides_to_key(key1['id'], [override])

    consumer = client.register(random_string('machine1'), :system, nil, {}, nil,
      owner['key'], ["key1"])
    consumer.uuid.should_not be_nil

    consumer_overrides = @cp.get_content_overrides(consumer['uuid'])
    consumer_overrides.length.should == 1
    consumer_overrides[0]['name'].should == "somename"
    consumer_overrides[0]['value'].should == "someval"
    consumer_overrides[0]['contentLabel'].should == "somelabel"
  end

  it 'should allow a consumer to register with activation keys with release' do
    owner = create_owner random_string('owner')

    user1 = user_client(owner, random_string("user1"))

    # Connect without any credentials:
    client = Candlepin.new

    key1 = @cp.create_activation_key(owner['key'], 'key1')

    @cp.update_activation_key({'id' => key1['id'], 'releaseVer' => "Registration Release"})

    consumer = client.register(random_string('machine1'), :system, nil, {}, nil,
      owner['key'], ["key1"])
    consumer.uuid.should_not be_nil

    consumer.releaseVer.releaseVer.should == "Registration Release"
  end

  it 'should allow a consumer to register with activation keys with service level' do
    owner = create_owner random_string('owner')

    user1 = user_client(owner, random_string("user1"))
    prod1 = create_product(random_string('product1'), random_string('product1'),
                           :attributes => { :'support_level' => 'VIP'})
    subs1 = @cp.create_subscription(owner['key'], prod1.id, 10)
    @cp.refresh_pools(owner['key'])

    # Connect without any credentials:
    client = Candlepin.new

    key1 = @cp.create_activation_key(owner['key'], 'key1', 'VIP')
    key2 = @cp.create_activation_key(owner['key'], 'key2')

    @cp.update_activation_key({'id' => key1['id'], 'releaseVer' => "Registration Release"})

    consumer = client.register(random_string('machine1'), :system, nil, {}, nil,
      owner['key'], ["key1", "key2"])
    consumer.uuid.should_not be_nil

    consumer.releaseVer.releaseVer.should == "Registration Release"
    consumer.serviceLevel.should == "VIP"
  end

  it 'should fail a consumer registration for activation keys with missing service level' do
    owner = create_owner random_string('owner')

    user1 = user_client(owner, random_string("user1"))
    prod1 = create_product(random_string('product1'), random_string('product1'),
                           :attributes => { :'support_level' => 'VIP'})
    subs1 = @cp.create_subscription(owner['key'], prod1.id, 10)
    @cp.refresh_pools(owner['key'])
    # Connect without any credentials:
    client = Candlepin.new

    key1 = @cp.create_activation_key(owner['key'], 'key1', 'VIP')
    key2 = @cp.create_activation_key(owner['key'], 'key2')

    @cp.update_activation_key({'id' => key1['id'], 'releaseVer' => "Registration Release"})

    @cp.delete_subscription(subs1.id)
    @cp.refresh_pools(owner['key'])

    lambda do
      consumer = client.register(random_string('machine1'), :system, nil, {}, nil,
        owner['key'], ["key1", "key2"])
    end.should raise_exception(RestClient::BadRequest)
  end

  it 'should allow a consumer to register with multiple activation keys with same content override names' do
    owner = create_owner random_string('owner')

    user1 = user_client(owner, random_string("user1"))

    # Connect without any credentials:
    client = Candlepin.new

    key1 = @cp.create_activation_key(owner['key'], 'key1')
    key2 = @cp.create_activation_key(owner['key'], 'key2')

    override1 = {"name" => "somename", "value" => "someval", "contentLabel" => "somelabel"}
    override2 = {"name" => "somename", "value" => "otherval", "contentLabel" => "somelabel"}
    @cp.add_content_overrides_to_key(key1['id'], [override1, override2])

    consumer = client.register(random_string('machine1'), :system, nil, {}, nil,
      owner['key'], ["key2", "key1"])
    consumer.uuid.should_not be_nil

    consumer_overrides = @cp.get_content_overrides(consumer['uuid'])
    consumer_overrides.length.should == 1
    # The order doesn't appear to be preserved, so we dont know which override will be applied
    # We really just don't want an exception
  end

  it 'should allow a consumer to register with activation keys with null quantity' do
    owner = create_owner random_string('owner')

    user1 = user_client(owner, random_string("user1"))
    prod1 = create_product(random_string('product1'), random_string('product1'),
                           :attributes => { :'multi-entitlement' => 'yes',
                                            :'stacking_id' => random_string('stacking_id'),
                                            :'sockets' => '1'})
    subs1 = @cp.create_subscription(owner['key'], prod1.id, 10)
    @cp.refresh_pools(owner['key'])
    pool1 = @cp.list_pools({:owner => owner['id']}).first

    # Connect without any credentials:
    client = Candlepin.new

    act_key1 = @cp.create_activation_key(owner['key'], 'act_key1')

    # null quantity
    @cp.add_pool_to_key(act_key1['id'], pool1['id'])
    @cp.create_activation_key(owner['key'], 'act_key2')

    consumer = client.register(random_string('machine1'), :system, nil, {'cpu.cpu_socket(s)' => '4'}, nil,
      owner['key'], ["act_key1", "act_key2"])

    consumer.uuid.should_not be_nil

    # TODO: Verify activation keys did what we expect once they are functional
    @cp.get_pool(pool1.id).consumed.should == 4
  end

  it 'handles failed activation key registration' do
    owner = create_owner random_string('owner')

    user1 = user_client(owner, random_string("user1"))
    consumer1 = consumer_client(user1, random_string("consumer1"))
    prod1 = create_product(random_string('product1'), random_string('product1'),
                           :attributes => { :'multi-entitlement' => 'yes'})
    subs1 = @cp.create_subscription(owner['key'], prod1.id, 10)
    @cp.refresh_pools(owner['key'])
    pool1 = consumer1.list_pools({:owner => owner['id']}).first

    # Connect without any credentials:
    client = Candlepin.new

    # Now we register asking for 9 when only 8 are available
    key1 = @cp.create_activation_key(owner['key'], 'key1')
    @cp.add_pool_to_key(key1['id'], pool1['id'], 9)

    # As another consumer, use 2 of the 10 available, we will then request
    # registration with the key wanting 9. (the server won't let us create
    # an activation key with a quantity greater than the pool)
    consumer1.consume_pool(pool1['id'], {:quantity => 2})

    @cp.list_consumers({:owner => owner['key']}).size.should == 1

    lambda do
      consumer = client.register(random_string('machine1'), :system, nil, {}, nil,
        owner['key'], ["key1"])
    end.should raise_exception(RestClient::Forbidden)

    # No new consumer should have been created:
    @cp.list_consumers({:owner => owner['key']}).size.should == 1
  end

  def verify_installed_pids(consumer, product_ids)
    installed_ids = consumer['installedProducts'].collect { |p| p['productId'] }
    installed_ids.length.should == product_ids.length
    product_ids.each do |pid|
      installed_ids.should include(pid)
    end
  end

  it 'should allow a consumer to register with and update installed products' do
    user = user_client(@owner1, random_string('billy'))
    pid1 = '918237'
    pid2 = '871234'
    pid3 = '712717'
    installed = [
        {'productId' => pid1, 'productName' => 'My Installed Product'},
        {'productId' => pid2, 'productName' => 'Another Installed Product'}]

    # Set a single fact, so we can make sure it doesn't get clobbered:
    facts = {
      'system.machine' => 'x86_64',
    }
    consumer = user.register(random_string('machine1'), :system, nil, facts, nil, nil, [], installed)
    verify_installed_pids(consumer, [pid1, pid2])

    # Now update the installed packages:
    installed = [
        {'productId' => pid1, 'productName' => 'My Installed Product'},
        {'productId' => pid3, 'productName' => 'Third Installed Product'}]

    consumer_client = Candlepin.new(username=nil, password=nil,
        cert=consumer['idCert']['cert'],
        key=consumer['idCert']['key'])
    consumer_client.update_consumer({:installedProducts => installed})
    consumer = @cp.get_consumer(consumer['uuid'])
    verify_installed_pids(consumer, [pid1, pid3])

    # Make sure facts weren't clobbered:
    consumer['facts'].length.should == 1
  end

  it 'should allow the installed products to be enriched with product information' do
    owner = create_owner random_string('owner')
    user = user_client(owner, random_string("user"))
    cp_client = consumer_client(user, random_string('consumer'), :system,
                                nil, {:'arch' => 'test_arch1'}, owner['key'])

    product1 = create_product(random_string('product'), random_string('product'),
                           :attributes => { :'arch' => 'ALL',
                                            :'version' => '3.11',
                                            :'multi-entitlement' => 'yes'})
    installed = [
        {'productId' => product1.id, 'productName' => product1.name}
    ]
    cp_client.update_consumer({:installedProducts => installed})

    subs1 = @cp.create_subscription(owner['key'], product1.id, 1, [], '', '', '', Date.today, Date.today + 365)
    @cp.refresh_pools(owner['key'])

    for pool in @cp.list_owner_pools(owner['key']) do
        cp_client.consume_pool(pool.id, {:quantity => 1})
    end

    consumer = @cp.get_consumer(cp_client.uuid)
    for installed_product in consumer['installedProducts'] do
         installed_product['arch'].should == 'ALL'
         installed_product['version'].should == '3.11'
         installed_product['status'].should == 'green'
         start_date = Date.strptime(installed_product['startDate'])
         start_date.year.should == Date.today.year
         start_date.month.should == Date.today.month
         start_date.day.should == Date.today.day
         end_date = Date.strptime(installed_product['endDate'])
         end_date.year.should == (Date.today + 365).year
         end_date.month.should == (Date.today + 365).month
         end_date.day.should == (Date.today + 365).day
    end
  end

  it 'should allow a consumer to update their autoheal flag' do
    user_cp = user_client(@owner1, random_string('billy'))
    consumer = user_cp.register(random_string('system'), :system, nil,
      {}, nil, nil, [], [])
    consumer_client = Candlepin.new(username=nil, password=nil,
        cert=consumer['idCert']['cert'],
        key=consumer['idCert']['key'])

    consumer = @cp.get_consumer(consumer['uuid'])
    consumer['autoheal'].should == true

    consumer_client.update_consumer({:autoheal => false})
    consumer = @cp.get_consumer(consumer['uuid'])
    consumer['autoheal'].should == false

    # Empty update shouldn't modify the setting:
    consumer_client.update_consumer({})
    consumer = @cp.get_consumer(consumer['uuid'])
    consumer['autoheal'].should == false
  end

  it 'should allow a consumer to update their hypervisorId' do
    user_cp = user_client(@owner1, random_string('billy'))
    consumer = user_cp.register(random_string('system'), :system, nil,
      {}, nil, nil, [], [])
    consumer_client = Candlepin.new(username=nil, password=nil,
        cert=consumer['idCert']['cert'],
        key=consumer['idCert']['key'])

    consumer = @cp.get_consumer(consumer['uuid'])
    consumer['hypervisorId'].should == nil

    consumer_client.update_consumer({:hypervisorId => "123abC"})
    consumer = @cp.get_consumer(consumer['uuid'])
    consumer['hypervisorId']['hypervisorId'].should == "123abc"

    # Empty update shouldn't modify the setting:
    consumer_client.update_consumer({})
    consumer = @cp.get_consumer(consumer['uuid'])
    consumer['hypervisorId']['hypervisorId'].should == "123abc"
  end

  it 'should not allow a consumer to update their hypervisorId to one in use by owner' do
    user_cp = user_client(@owner1, random_string('billy'))
    consumer = user_cp.register(random_string('system'), :system, nil,
      {}, nil, nil, [], [])
    consumer1 = user_cp.register(random_string('system'), :system, nil,
      {}, nil, nil, [], [], nil, [], "hYpervisor")
    consumer_client = Candlepin.new(username=nil, password=nil,
        cert=consumer['idCert']['cert'],
        key=consumer['idCert']['key'])

    consumer1 =  @cp.get_consumer(consumer1['uuid'])
    consumer1['hypervisorId']['hypervisorId'].should == "hypervisor"

    consumer = @cp.get_consumer(consumer['uuid'])
    consumer['hypervisorId'].should == nil

    lambda do
      consumer_client.update_consumer({:hypervisorId => "hypervisor"})
    end.should raise_exception(RestClient::BadRequest)
  end

  it 'should allow a consumer to unset their hypervisorId' do
    user_cp = user_client(@owner1, random_string('billy'))
    consumer = user_cp.register(random_string('system'), :system, nil,
      {}, nil, nil, [], [])
    consumer_client = Candlepin.new(username=nil, password=nil,
        cert=consumer['idCert']['cert'],
        key=consumer['idCert']['key'])

    consumer = @cp.get_consumer(consumer['uuid'])
    consumer['hypervisorId'].should == nil

    consumer_client.update_consumer({:hypervisorId => "123abC"})
    consumer = @cp.get_consumer(consumer['uuid'])
    consumer['hypervisorId']['hypervisorId'].should == "123abc"

    # Empty update shouldn't modify the setting:
    consumer_client.update_consumer({:hypervisorId => ""})
    consumer = @cp.get_consumer(consumer['uuid'])
    consumer['hypervisorId'].should == nil
  end

  it 'should allow a consumer to update their service level' do
    product1 = create_product(random_string('product'),
                              random_string('product'),
                              {:attributes => {:support_level => 'VIP'}})
    product2 = create_product(random_string('product'),
                              random_string('product'),
                              {:attributes => {:support_level => 'Layered',
                                               :support_level_exempt => 'true'}})
    subs1 = @cp.create_subscription(@owner1['key'], product1.id)
    subs2 = @cp.create_subscription(@owner1['key'], product2.id)
    @cp.refresh_pools(@owner1['key'])

    user_cp = user_client(@owner1, random_string('billy'))
    consumer = user_cp.register(random_string('system'), :system, nil,
      {}, nil, nil, [], [])
    consumer_client = Candlepin.new(username=nil, password=nil,
        cert=consumer['idCert']['cert'],
        key=consumer['idCert']['key'])

    consumer = @cp.get_consumer(consumer['uuid'])
    consumer['serviceLevel'].should == ''

    consumer_client.update_consumer({:serviceLevel => 'VIP'})
    consumer = @cp.get_consumer(consumer['uuid'])
    consumer['serviceLevel'].should == 'VIP'

    # Make sure we can reset to empty for service level
    consumer_client.update_consumer({:serviceLevel => ''})
    consumer = @cp.get_consumer(consumer['uuid'])
    consumer['serviceLevel'].should == ''

    # Empty update shouldn't modify the setting [after reset to VIP]:
    consumer_client.update_consumer({:serviceLevel => 'VIP'})
    consumer = @cp.get_consumer(consumer['uuid'])
    consumer['serviceLevel'].should == 'VIP'

    consumer_client.update_consumer({})
    consumer = @cp.get_consumer(consumer['uuid'])
    consumer['serviceLevel'].should == 'VIP'

    # Should not be able to set service level to one not available by org
    lambda do
        consumer_client.update_consumer({:serviceLevel => 'Ultra-VIP'})
    end.should raise_exception(RestClient::BadRequest)

    # The service level should be case insensitive
    consumer_client.update_consumer({:serviceLevel => ''})
    consumer = @cp.get_consumer(consumer['uuid'])
    consumer['serviceLevel'].should == ''

    consumer_client.update_consumer({:serviceLevel => 'vip'})
    consumer = @cp.get_consumer(consumer['uuid'])
    consumer['serviceLevel'].should == 'vip'

   # Cannot assign exempt level to consumer
    lambda do
        consumer_client.update_consumer({:serviceLevel => 'Layered'})
    end.should raise_exception(RestClient::BadRequest)

  end

  it 'should allow a consumer dry run an autosubscribe based on service level' do
    product1 = create_product(random_string('product'),
                              random_string('product'),
                              {:attributes => {:support_level => 'VIP'}})
    product2 = create_product(random_string('product'),
                              random_string('product'),
                              {:attributes => {:support_level => 'Ultra-VIP'}})
    subs1 = @cp.create_subscription(@owner1['key'], product1.id)
    subs2 = @cp.create_subscription(@owner1['key'], product2.id)
    @cp.refresh_pools(@owner1['key'])

    user_cp = user_client(@owner1, random_string('billy'))
    consumer = user_cp.register(random_string('system'), :system, nil,
      {}, nil, nil, [], [])
    consumer_client = Candlepin.new(username=nil, password=nil,
        cert=consumer['idCert']['cert'],
        key=consumer['idCert']['key'])

    installed = [
        {'productId' => product1.id, 'productName' => product1.name},
        {'productId' => product2.id, 'productName' => product2.name}]

    consumer_client.update_consumer({:serviceLevel => 'VIP',
                                     :installedProducts => installed})
    consumer = @cp.get_consumer(consumer['uuid'])
    consumer['serviceLevel'].should == 'VIP'

    # dry run against the set service level
    pools = @cp.autobind_dryrun(consumer['uuid'])
    pools.length.should == 1
    pool_id = pools.first.pool.id
    pool = @cp.get_pool(pool_id)
    pool.subscriptionId.should == subs1.id

    # dry run against the override service level
    pools = @cp.autobind_dryrun(consumer['uuid'], 'Ultra-VIP')
    pools.length.should == 1
    pool_id = pools.first.pool.id
    pool = @cp.get_pool(pool_id)
    pool.subscriptionId.should == subs2.id

    # ensure the override use did not change the setting
    consumer = @cp.get_consumer(consumer['uuid'])
    consumer['serviceLevel'].should == 'VIP'

    # dry run with unknown level should return badrequest
    lambda do
        @cp.autobind_dryrun(consumer['uuid'], 'Standard').length.should == 0
    end.should raise_exception(RestClient::BadRequest)

    # dry run against the override service level should be case insensitive
    pools = @cp.autobind_dryrun(consumer['uuid'], 'Ultra-vip')
    pools.length.should == 1
    pool_id = pools.first.pool.id
    pool = @cp.get_pool(pool_id)
    pool.subscriptionId.should == subs2.id
  end

  it 'should recognize support level exempt attribute' do
    product1 = create_product(random_string('product'),
                              random_string('product'),
                              {:attributes => {:support_level => 'Layered',
                                               :support_level_exempt => 'true'}})
    product2 = create_product(random_string('product'),
                              random_string('product'),
                              {:attributes => {:support_level => 'VIP'}})
    product3 = create_product(random_string('product'),
                              random_string('product'),
                              {:attributes => {:support_level => 'Ultra-VIP'}})
    product4 = create_product(random_string('product'),
                              random_string('product'),
                              {:attributes => {:support_level => 'LAYered'}})
    subs1 = @cp.create_subscription(@owner1['key'], product1.id)
    subs2 = @cp.create_subscription(@owner1['key'], product2.id)
    subs3 = @cp.create_subscription(@owner1['key'], product3.id)
    @cp.refresh_pools(@owner1['key'])

    user_cp = user_client(@owner1, random_string('billy'))
    consumer = user_cp.register(random_string('system'), :system, nil,
      {}, nil, nil, [], [])
    consumer_client = Candlepin.new(username=nil, password=nil,
        cert=consumer['idCert']['cert'],
        key=consumer['idCert']['key'])

    installed = [
        {'productId' => product1.id, 'productName' => product1.name},
        {'productId' => product2.id, 'productName' => product2.name},
        {'productId' => product4.id, 'productName' => product4.name}]

    consumer_client.update_consumer({:serviceLevel => 'Ultra-VIP',
                                     :installedProducts => installed})
    consumer = @cp.get_consumer(consumer['uuid'])
    consumer['serviceLevel'].should == 'Ultra-VIP'

    # dry run against the set service level
    # should get only the exempt product that has subscription
    pools = @cp.autobind_dryrun(consumer['uuid'])
    pools.length.should == 1
    pool_id = pools.first.pool.id
    pool = @cp.get_pool(pool_id)
    pool.subscriptionId.should == subs1.id

    # this product should also get pulled, exempt overrides
    # based on name match
    subs4 = @cp.create_subscription(@owner1['key'], product4.id)
    @cp.refresh_pools(@owner1['key'])
    pools = @cp.autobind_dryrun(consumer['uuid'])
    pools.length.should == 2

    # change service level to one that matches installed
    # should get 3 pools
    consumer_client.update_consumer({:serviceLevel => 'VIP'})
    pools = @cp.autobind_dryrun(consumer['uuid'])
    pools.length.should == 3
  end

  it 'should return empty list for dry run where all pools are blocked because of consumer type' do
    product1 = create_product(random_string('product'),
                              random_string('product'),
                              {:attributes => {:requires_consumer_type => :person}})
    product2 = create_product(random_string('product'),
                              random_string('product'),
                              {:attributes => {:requires_consumer_type => :person}})
    subs1 = @cp.create_subscription(@owner1['key'], product1.id)
    subs2 = @cp.create_subscription(@owner1['key'], product2.id)
    @cp.refresh_pools(@owner1['key'])

    user_cp = user_client(@owner1, random_string('billy'))
    consumer = user_cp.register(random_string('system'), :system, nil,
      {}, nil, nil, [], [])
    consumer_client = Candlepin.new(username=nil, password=nil,
        cert=consumer['idCert']['cert'],
        key=consumer['idCert']['key'])

    installed = [
        {'productId' => product1.id, 'productName' => product1.name},
        {'productId' => product2.id, 'productName' => product2.name}]

    consumer_client.update_consumer({:installedProducts => installed})

    # dry run
    pools = @cp.autobind_dryrun(consumer['uuid'])
    pools.length.should == 0
  end

  it 'should not allow the same UUID to be registered twice' do
    owner = create_owner random_string('owner')
    user = user_client(owner, random_string('willy'))

    # Register the UUID initially
    user.register(random_string('machine1'), :system, 'ALF')

    # Second registration should be denied
    lambda do
      user.register(random_string('machine2'), :system, 'ALF')
    end.should raise_exception(RestClient::BadRequest)
  end

  it 'should allow a consumer to unregister and free up the pool' do
    owner = create_owner(random_string('zowner'))
    user = user_client(owner, random_string('cukebuster'))
    # performs the register for us
    consumer = consumer_client(user, random_string('machine1'))
    product = create_product()
    @cp.create_subscription(owner['key'], product.id, 2)
    @cp.refresh_pools(owner['key'])
    pool = consumer.list_pools(:consumer => consumer.uuid)[0]
    pool.consumed.should == 0
    consumer.consume_pool(pool.id, {:quantity => 1})
    @cp.get_pool(pool.id).consumed.should == 1
    consumer.unregister(consumer.uuid)
    @cp.get_pool(pool.id).consumed.should == 0
  end

  it 'should allow adding guest ids to host consumer on update' do
    guests = [{'guestId' => 'guest1'}]

    user_cp = user_client(@owner1, random_string('test-user'))
    consumer = user_cp.register(random_string('host'), :system, nil,
      {}, nil, nil, [], [])
    consumer.should_not be_nil
    consumer['guestIds'].should be_nil

    consumer_client = Candlepin.new(username=nil, password=nil,
        cert=consumer['idCert']['cert'],
        key=consumer['idCert']['key'])
    consumer_client.update_consumer({:guestIds => guests})

    consumer = @cp.get_consumer(consumer['uuid'])
    consumer['guestIds'].length.should == 1
    consumer['guestIds'][0]['guestId'].should == 'guest1'
  end

  it 'should allow updating guest ids from host consumer on update' do
    guests = [{'guestId' => 'guest1'},
              {'guestId' => 'guest2'}]

    user_cp = user_client(@owner1, random_string('test-user'))
    consumer = user_cp.register(random_string('host'), :system, nil,
      {}, nil, nil, [], [])

    consumer_client = Candlepin.new(username=nil, password=nil,
        cert=consumer['idCert']['cert'],
        key=consumer['idCert']['key'])
    consumer_client.update_consumer({:guestIds => guests})

    consumer = @cp.get_consumer(consumer['uuid'])
    consumer['guestIds'].length.should == 2

    consumer_client.update_consumer({:guestIds => [guests[1]]})
    consumer = @cp.get_consumer(consumer['uuid'])
    consumer['guestIds'].length.should == 1
    consumer['guestIds'][0]['guestId'].should == 'guest2'
  end

  it 'should not modify guest id list if guestIds list is null on update' do
    guests = [{'guestId' => 'guest1'}]

    user_cp = user_client(@owner1, random_string('test-user'))
    consumer = user_cp.register(random_string('host'), :system, nil,
      {}, nil, nil, [], [])

    consumer_client = Candlepin.new(username=nil, password=nil,
        cert=consumer['idCert']['cert'],
        key=consumer['idCert']['key'])
    consumer_client.update_consumer({:guestIds => guests})

    consumer = @cp.get_consumer(consumer['uuid'])
    consumer['guestIds'].length.should == 1

    consumer_client.update_consumer({:guestIds => nil})
    consumer_client.update_consumer({})
    consumer = @cp.get_consumer(consumer['uuid'])
    consumer['guestIds'].length.should == 1
    consumer['guestIds'][0]['guestId'].should == 'guest1'
  end

  it 'should clear guest ids when empty list is provided on update' do
    guests = [{'guestId' => 'guest1'}]

    user_cp = user_client(@owner1, random_string('test-user'))
    consumer = user_cp.register(random_string('host'), :system, nil,
      {}, nil, nil, [], [])

    consumer_client = Candlepin.new(username=nil, password=nil,
        cert=consumer['idCert']['cert'],
        key=consumer['idCert']['key'])
    consumer_client.update_consumer({:guestIds => guests})

    consumer = @cp.get_consumer(consumer['uuid'])
    consumer['guestIds'].length.should == 1

    consumer_client.update_consumer({:guestIds => []})
    consumer = @cp.get_consumer(consumer['uuid'])
    consumer['guestIds'].length.should == 0
  end

  it 'should allow host to list guests' do
    uuid1 = random_string('system.uuid')
    uuid2 = random_string('system.uuid')
    guests = [{'guestId' => uuid1}, {'guestId' => uuid2}]

    user_cp = user_client(@owner1, random_string('test-user'))
    host_consumer = user_cp.register(random_string('host'), :system, nil,
      {}, nil, nil, [], [])
    guest_consumer1 = user_cp.register(random_string('guest'), :system, nil,
      {'virt.uuid' => uuid1}, nil, nil, [], [])
    guest_consumer2 = user_cp.register(random_string('guest'), :system, nil,
      {'virt.uuid' => uuid2}, nil, nil, [], [])

    consumer_client = Candlepin.new(username=nil, password=nil,
        cert=host_consumer['idCert']['cert'],
        key=host_consumer['idCert']['key'])
    consumer_client.update_consumer({:guestIds => guests})

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
    guest_consumer2 = user_cp.register(random_string('guest'), :system, nil,
      {'virt.uuid' => uuid2}, nil, nil, [], [])

    consumer_client1 = Candlepin.new(username=nil, password=nil,
        cert=host_consumer1['idCert']['cert'],
        key=host_consumer1['idCert']['key'])
    consumer_client1.update_consumer({:guestIds => guests1})

    # MySQL before 5.6.4 doesn't store fractional seconds on timestamps
    # and getHost() method in ConsumerCurator (which is what tells us which
    # host a guest is associated with) sorts results by updated time.
    sleep 1

    consumer_client2 = Candlepin.new(username=nil, password=nil,
        cert=host_consumer2['idCert']['cert'],
        key=host_consumer2['idCert']['key'])
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

    consumer_client1 = Candlepin.new(username=nil, password=nil,
        cert=host_consumer1['idCert']['cert'],
        key=host_consumer1['idCert']['key'])
    consumer_client1.update_consumer({:guestIds => guests1})

    # MySQL before 5.6.4 doesn't store fractional seconds on timestamps
    # and getHost() method in ConsumerCurator (which is what tells us which
    # host a guest is associated with) sorts results by updated time.
    sleep 1

    consumer_client2 = Candlepin.new(username=nil, password=nil,
        cert=host_consumer2['idCert']['cert'],
        key=host_consumer2['idCert']['key'])
    consumer_client2.update_consumer({:guestIds => guests2})

    host1 = @cp.get_consumer_host(guest_consumer1['uuid'])
    host1['uuid'].should == host_consumer1['uuid']
    host2 = @cp.get_consumer_host(guest_consumer2['uuid'])
    host2['uuid'].should == host_consumer2['uuid']
  end

  it 'should allow a consumer fact to be removed when updated badly' do
    # typing for certain facts. violation means value for fact is entirely removed
    user = user_client(@owner1, random_string('billy'))
    # Set a single fact, so we can make sure it doesn't get clobbered:
    facts = {
      'system.machine' => 'x86_64',
      'lscpu.socket(s)' => '4',
      'cpu.cpu(s)' => '12',
    }
    consumer = user.register('machine1', :system, nil, facts, nil, nil, [], nil)

    # Now update the facts, must send all:
    facts = {
      'system.machine' => 'x86_64',
      'lscpu.socket(s)' => 'four',
      'cpu.cpu(s)' => '8',
       # these facts dont need to be an int, they are ranges
      'lscpu.numa_node0_cpu(s)' => '0-3',
      'lscpu.on-line_cpu(s)_list' => '0-3'
   }

    consumer_client = Candlepin.new(username=nil, password=nil,
        cert=consumer['idCert']['cert'],
        key=consumer['idCert']['key'])
    consumer_client.update_consumer({:facts => facts})
    consumer = @cp.get_consumer(consumer['uuid'])

    # Make sure facts weren't clobbered:
    consumer['facts']['system.machine'].should == 'x86_64'
    consumer['facts']['lscpu.socket(s)'].should be_nil
    consumer['facts']['cpu.cpu(s)'].should == '8'
    # range facts should be left alone, rhbz #950462 shows
    # them being ignored
    consumer['facts']['lscpu.on-line_cpu(s)_list'].should == '0-3'
    consumer['facts']['lscpu.numa_node0_cpu(s)'].should == '0-3'
  end

  # When no quantity is sent to the server, the suggested quantity should be attached
  it "should bind correct quantity when not specified" do
    facts = {
        'cpu.cpu_socket(s)' => '4',
    }
    product1 = create_product(random_string('product'), random_string('product-multiple-arch'),
        :attributes => { :sockets => '1', :'multi-entitlement' => 'yes', :stacking_id => 'consumer-bind-test'})
    sub = @cp.create_subscription(@owner1['key'], product1.id, 10)
    installed = [
        {'productId' => product1.id, 'productName' => product1.name}
    ]
    @consumer1.update_consumer({:installedProducts => installed, :facts => facts})
    @cp.refresh_pools(@owner1['key'])
    pool = @consumer1.list_pools({:owner => @owner1['id']}).first
    ent = @consumer1.consume_pool(pool.id)
    ent[0]["quantity"].should == 4
  end

  it "should bind quantity 1 when suggested is 0 and not specified" do
    facts = {
      'cpu.cpu_socket(s)' => '4',
    }
    product1 = create_product(random_string('product'), random_string('product-multiple-arch'),
        :attributes => { :sockets => '2', :'multi-entitlement' => 'yes', :stacking_id => 'consumer-bind-test'})
    sub = @cp.create_subscription(@owner1['key'], product1.id, 10)
    installed = [
        {'productId' => product1.id, 'productName' => product1.name}
    ]
    @consumer1.update_consumer({:installedProducts => installed, :facts => facts})
    @cp.refresh_pools(@owner1['key'])
    pool = @consumer1.list_pools({:owner => @owner1['id']}).first
    # Cover product with 2 2 socket ents, then suggested will be 0
    ent = @consumer1.consume_pool(pool.id, {:quantity => 2})
    ent = @consumer1.consume_pool(pool.id)
    ent[0]["quantity"].should == 1
  end

  it "should bind correct future quantity when fully subscribed today" do
    facts = {
      'cpu.cpu_socket(s)' => '4',
    }
    product1 = create_product(random_string('product'), random_string('product-multiple-arch'),
        :attributes => { :sockets => '2', :'multi-entitlement' => 'yes', :stacking_id => 'consumer-bind-test'})
    sub = @cp.create_subscription(@owner1['key'], product1.id, 10)
    start = Date.today + 400
    future_sub = @cp.create_subscription(@owner1['key'], product1.id, 10, [], '', '', '', start)
    installed = [
        {'productId' => product1.id, 'productName' => product1.name}
    ]
    @consumer1.update_consumer({:installedProducts => installed, :facts => facts})
    @cp.refresh_pools(@owner1['key'])
    pool = @consumer1.list_pools({:owner => @owner1['id']}).first
    # Fully cover the product1 for a year
    @consumer1.consume_pool(pool.id, {:quantity => 2})

    future_pool = @consumer1.list_pools({:owner => @owner1['id'], :activeon => Date.today + 450}).first
    ent = @consumer1.consume_pool(future_pool.id)[0]
    ent["quantity"].should == 2
  end

  it 'should be able to add unused attributes' do
    guests = [{'guestId' => 'guest1', 'fooBar' => 'some value'}]

    user_cp = user_client(@owner1, random_string('test-user'))
    consumer = user_cp.register(random_string('host'), :system, nil,
      {}, nil, nil, [], [])

    consumer_client = Candlepin.new(username=nil, password=nil,
        cert=consumer['idCert']['cert'],
        key=consumer['idCert']['key'])
    consumer_client.update_consumer({:guestIds => guests})

    consumer = @cp.get_consumer(consumer['uuid'])
    consumer['guestIds'].length.should == 1
    consumer['guestIds'][0]['guestId'].should == 'guest1'
  end

  it 'should return correct exception for contraint violations' do
    lambda {
      user_cp = user_client(@owner1, random_string('test-user'))
      consumer = user_cp.register("a" * 256, :system, nil,
      {}, nil, nil, [], [])
    }.should raise_exception(RestClient::BadRequest)
    lambda {
      user_cp = user_client(@owner1, random_string('test-user'))
      consumer = user_cp.register(random_string('test-consumer'), :system, "a" * 256,
      {}, nil, nil, [], [])
    }.should raise_exception(RestClient::BadRequest)
    lambda {
      user_cp = user_client(@owner1, random_string('test-user'))
      consumer = user_cp.register(nil, :system, nil,
      {}, nil, nil, [], [])
    }.should raise_exception(RestClient::BadRequest)
  end
end

describe 'Consumer Resource Consumer Fact Filter Tests' do

  include CandlepinMethods

  before(:each) do
    @owner = create_owner(random_string("test_owner"))
    @owner_client = user_client(@owner, random_string('bill'))
    @consumer1 = @owner_client.register('c1', :system, nil, {'key' => 'value', 'otherkey' => 'otherval'})
    @consumer2 = @owner_client.register('c2', :system, nil, {'key' => 'value', 'otherkey' => 'someval'})
    @consumer3 = @owner_client.register('c3', :system, nil, {'newkey' => 'somevalue'})
  end

  it 'can filter by facts and nothing else' do
    consumers = @cp.list_consumers({:facts => ['*key*:*val*']})
    # Length should be at least the three we have defined, however there could be other rows...
    consumers.length.should >= 3
  end

  it 'can filter by facts and uuids' do
    consumers = @cp.list_consumers({:facts => ['oth*key*:*val'], :uuids => [@consumer1['uuid'], @consumer2['uuid'], @consumer3['uuid']]})
    consumers.length.should == 2
    expected_uuids = [@consumer1['uuid'], @consumer2['uuid']]
    consumers.each do |consumer|
      expected_uuids.delete(consumer['uuid'])
    end
    # We should have found and removed every item in this list
    expected_uuids.length.should == 0
  end

  it 'should properly escape values to avoid sql injection' do
    odd_consumer =  @owner_client.register('c4', :system, nil, {'trolol' => "'); DROP TABLE cp_consumer;"})
    consumers = @cp.list_consumers({:owner => @owner['key'], :facts => ["trolol:'); DROP TABLE cp_consumer;"]})
    consumers.length.should == 1
    consumers[0]['uuid'].should == odd_consumer['uuid']
  end
end
