# -*- coding: utf-8 -*-
require 'candlepin_scenarios'

describe 'Consumer Resource' do

  include CandlepinMethods
  include CandlepinScenarios

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

    @consumer1.consume_pool(pool.id).size.should == 1
    @consumer1.get_consumer()['entitlementStatus'].should == "valid"
  end

  it 'should return a 410 for deleted consumers' do
    @cp.unregister(@consumer1.uuid)
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

  it 'allows super admins to see all consumers' do

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

  it 'lets an owner admin see only their consumers' do
    @user2.list_consumers({:owner => @owner2['key']}).length.should == 1
  end

  it 'lets a super admin filter consumers by owner' do
    @cp.list_consumers.size.should > 1
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
    end.should raise_exception(RestClient::Forbidden)
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
    end.should raise_exception(RestClient::Forbidden)
  end

  it "does not let an owner register with UUID of another owner's consumer" do
    linux_net = create_owner 'linux_net'
    greenfield = create_owner 'greenfield_consulting'

    linux_bill = user_client(linux_net, 'bill')
    green_ralph = user_client(greenfield, 'ralph')

    system1 = linux_bill.register('system1')

    lambda do
      green_ralph.register('system2', :system, system1.uuid)
    end.should raise_exception(RestClient::BadRequest)
  end

  it "does not let an owner reregister another owner's consumer" do
    linux_net = create_owner 'linux_net'
    greenfield = create_owner 'greenfield_consulting'

    linux_bill = user_client(linux_net, 'bill')
    green_ralph = user_client(greenfield, 'ralph')

    system1 = linux_bill.register('system1')

    lambda do
      green_ralph.regenerate_identity_certificate(system1.uuid)
    end.should raise_exception(RestClient::Forbidden)
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

    cp_client.consume_pool(pool.id).size.should == 1
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
    consumer_client.consume_pool(pool['id'])
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

    consumer = user.register('machine1', :system, 'custom-uuid')
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
    consumer = client.register('machine1', :system, nil, {}, nil,
      owner['key'], ["key1", "key2"])
    consumer.uuid.should_not be_nil

    # TODO: Verify activation keys did what we expect once they are functional
    @cp.get_pool(pool1.id).consumed.should == 3
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
      consumer = client.register('machine1', :system, nil, {}, nil,
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
    consumer = user.register('machine1', :system, nil, facts, nil, nil, [], installed)
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
        cp_client.consume_pool(pool.id)
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
    user.register('machine1', :system, 'ALF')

    # Second registration should be denied
    lambda do
      user.register('machine2', :system, 'ALF')
    end.should raise_exception(RestClient::BadRequest)
  end

  it 'should allow a consumer to unregister and free up the pool' do
    owner = create_owner('zowner')
    user = user_client(owner, 'cukebuster')
    # performs the register for us
    consumer = consumer_client(user, 'machine1')
    product = create_product()
    @cp.create_subscription(owner['key'], product.id, 2)
    @cp.refresh_pools(owner['key'])
    pool = consumer.list_pools(:consumer => consumer.uuid)[0]
    pool.consumed.should == 0
    consumer.consume_pool(pool.id)
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

end
