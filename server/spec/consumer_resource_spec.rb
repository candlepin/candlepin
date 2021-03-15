# -*- coding: utf-8 -*-
require 'spec_helper'
require 'candlepin_scenarios'
require 'rexml/document'

describe 'Consumer Resource' do

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

  it 'should not allow setting entitlement count on register' do
     consumer = @user2.register(
        random_string('newConsumer'),
        :system,
        nil,
        {},
        nil,
        @owner2['key'],
        [],
        [],
        nil,
        [],
        nil,
        [],
        nil,
        nil,
        nil,
        nil,
        nil,
	3)
     consumer['entitlementCount'].should == 0
  end

  it 'should not allow copying id cert to other consumers' do
     consumer_old = @user2.register(random_string('consumer1'))
     id_cert = consumer_old['idCert']

     consumer = @user2.register(
        random_string('newConsumer'),
        :system,
        nil,
        {},
        nil,
        @owner2['key'],
        [],
        [],
        nil,
        [],
        nil,
        [],
        nil,
        nil,
        nil,
        nil,
        nil,
        0,
        id_cert)

     consumer['idCert'].should_not == id_cert
  end

  it "get consumer content access" do
    access = @cp.get_consumer_content_access(@consumer1.uuid)

    expect(access['contentAccessModeList']).to eq(%w[org_environment entitlement])
    expect(access['contentAccessMode']).to eq("entitlement")
  end

  it 'should receive paged data back when requested' do
    id_list = []
    (1..4).each do |i|
      prod = create_product(nil, nil, { :owner => @owner1['key'] })
      create_pool_and_subscription(@owner1['key'], prod.id, 6)
      id_list.push(prod.id)
    end
    id_list.each do |id|
      @consumer1.consume_product(id)
    end

    entitlements = @consumer1.list_entitlements({:page => 1, :per_page => 2, :sort_by => "id", :order => "asc"})
    entitlements.length.should == 2
    (entitlements[0].id <=> entitlements[1].id).should == -1
  end

  it 'does not allow a consumer to view entitlements from a different consumer' do
    # Given
    bad_owner = create_owner random_string 'baddie'
    bad_user = user_client(bad_owner, 'bad_dude')
    system = consumer_client(bad_user, 'wrong_system')

    # When
    lambda do
      system.list_entitlements(:uuid => @consumer1.uuid)

      # Then
    end.should raise_exception(RestClient::ResourceNotFound)
  end

  it 'should not re-calculate quantity attributes when fetching entitlements' do
    # should allow compliance type but not quantity
    prod = create_product(nil, nil, { :owner => @owner1['key'] })
    create_pool_and_subscription(@owner1['key'], prod.id, 6)
    @consumer1.consume_product(prod.id)

    entitlements = @consumer1.list_entitlements()
    entitlements.length.should == 1
    entitlements[0].pool.calculatedAttributes['suggested_quantity'].should be_nil
    entitlements[0].pool.calculatedAttributes['quantity_increment'].should be_nil
    entitlements[0].pool.calculatedAttributes['compliance_type'].should == 'Standard'
  end

  it "should block consumers from using other org's pools" do
    product_id = random_string('prod')
    product = create_product(product_id, product_id, {:owner => @owner1['key']})
    pool = create_pool_and_subscription(@owner1['key'], product.id)
    lambda {
      @consumer2.consume_pool(pool.id, {:quantity => 1}).size.should == 1
    }.should raise_exception(RestClient::ResourceNotFound)
  end

  it 'should receive paged consumers back when requested' do
    (1..4).each do |i|
      consumer_client(@user1, random_string('system'))
    end
    consumers = @cp.list_consumers({:owner => @owner1['key'], :page => 1, :per_page => 2, :sort_by => "id", :order => "asc"})
    consumers.length.should == 2
    (consumers[0].id <=> consumers[1].id).should == -1
  end

  it 'should set compliance status and update compliance status' do
    @consumer1.get_consumer()['entitlementStatus'].should == "valid"
    product1 = create_product(random_string('product'), random_string('product'),
      {:owner => @owner1['key']})
    installed = [
        {'productId' => product1.id, 'productName' => product1.name}
    ]
    @consumer1.update_consumer({:installedProducts => installed})
    @consumer1.get_consumer()['entitlementStatus'].should == "invalid"

    pool = create_pool_and_subscription(@owner1['key'], product1.id)

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

  it 'should not let consumer update environment with incorrect env name' do
    user_cp = user_client(@owner1, random_string('billy'))
    consumer = user_cp.register(random_string('system'), :system, nil, {}, nil, nil, [], [])
    consumer_client = Candlepin.new(nil, nil, consumer['idCert']['cert'], consumer['idCert']['key'])

    expect{
      consumer_client.update_consumer({:environment => {:name => "abc"}})
    }.to raise_error(RestClient::ResourceNotFound)
  end

  it 'should let consumer update environment with valid env name only' do
    user_cp = user_client(@owner1, random_string('billy'))
    consumer = user_cp.register(random_string('system'), :system, nil, {}, nil, nil, [], [])
    consumer_client = Candlepin.new(nil, nil, consumer['idCert']['cert'],
      consumer['idCert']['key'])
    env = @cp.create_environment(@owner1['key'], random_string("env_id"), random_string("env_name"))

    expect(consumer.environment).to be_nil

    consumer_client.update_consumer({:environment => {:name => env.name}})
    consumer = @cp.get_consumer(consumer['uuid'])

    expect(consumer.environment).to_not be_nil
  end

  it 'should let consumer update environment with valid env id only' do
    user_cp = user_client(@owner1, random_string('billy'))
    consumer = user_cp.register(random_string('system'), :system, nil, {}, nil, nil, [], [])
    consumer_client = Candlepin.new(nil, nil, consumer['idCert']['cert'], consumer['idCert']['key'])
    env = @cp.create_environment(@owner1['key'], random_string("env_id"), random_string("env_name"))

    expect(consumer.environment).to be_nil

    consumer_client.update_consumer({:environment => {:id => env.id}})
    consumer = @cp.get_consumer(consumer['uuid'])

    expect(consumer.environment).to_not be_nil
  end

  it 'should let not consumer update environment with incorrect env id' do
    user_cp = user_client(@owner1, random_string('billy'))
    consumer = user_cp.register(random_string('system'), :system, nil, {}, nil, nil, [], [])
    consumer_client = Candlepin.new(nil, nil, consumer['idCert']['cert'], consumer['idCert']['key'])

    expect(consumer.environment).to be_nil

    expect{
      consumer_client.update_consumer({:environment => {:id => 'incorrect_id'}})
    }.to raise_error(RestClient::ResourceNotFound)

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
    @cp.list_consumers({:type => 'system'}).each do |c|
      uuids << c['uuid']
      # Consumer lists should not have idCert or facts:
      c['facts'].should be_nil
      c['idCert'].should be_nil
    end
    uuids.include?(@consumer1.uuid).should be true
    uuids.include?(@consumer2.uuid).should be true
  end

  it 'allows super admins to query consumers by id' do
    # Create a consumer that should not be in the list of returned results
    consumer_client(@user2, random_string("consumer3"))
    returned_uuids = []
    @cp.list_consumers({:uuid => [@consumer1.uuid, @consumer2.uuid]}).each do |c|
      returned_uuids << c['uuid']
    end
    returned_uuids.include?(@consumer1.uuid).should be true
    returned_uuids.include?(@consumer2.uuid).should be true
    returned_uuids.length.should == 2
  end

  #TODO Get this working in parallel
  it 'lets a super admin filter consumers by owner', :serial => true do
    @cp.list_consumers({:type => 'system'}).size.should be > 1
    @cp.list_consumers({:owner => @owner1['key']}).size.should == 1
  end

  it 'lets a super admin see a peson consumer with a given username' do
    username = random_string("user1")
    user1 = user_client(@owner1, username)
    consumer_client(user1, random_string("consumer1"), 'person')

    @cp.list_consumers({:type => 'person', :username => username}).length.should == 1
  end

  it 'lets a super admin create person consumer for another user' do
    owner1 = create_owner random_string('test_owner1')
    username = random_string "user1"
    user_client(owner1, username)
    consumer_client(@cp, random_string("consumer1"), 'person',
                                username, {}, owner1['key'])

    @cp.list_consumers({:type => 'person', :username => username, :owner => owner1['key']}).length.should == 1
  end

  it 'does not let an owner admin create person consumer for another owner' do
    owner1 = create_owner random_string('test_owner1')
    username = random_string "user1"
    user_client(owner1, username)

    owner2 = create_owner random_string('test_owner2')
    user2 = user_client(owner2, random_string("user2"))

    lambda do
      consumer_client(user2, random_string("consumer1"), 'person', username)
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

  it 'returns a 404 for a non-existent consumer' do
    lambda do
      @cp.get_consumer('fake-uuid')
    end.should raise_exception(RestClient::ResourceNotFound)
  end

  it 'returns a 404 when checking if a non-existent consumer exists' do
    expect {
      @cp.consumer_exists('fake-uuid')
    }.to raise_error(RestClient::ResourceNotFound)
  end

  it 'returns a 204 when checking if a real consumer exists' do
    response = @cp.head("/consumers/#{@consumer1.uuid}/exists")
    expect(response.code).to eq(204)
  end

  it 'returns a 410 when checking if a deleted consumer exists' do
    @consumer1.unregister(@consumer1.uuid)

    expect {
      @cp.consumer_exists(@consumer1.uuid)
    }.to raise_error(RestClient::Gone)
  end

  it 'allows consumer to check for self-existence' do
    @consumer1.consumer_exists(@consumer1.uuid)
  end

  it 'does not allow consumer to check existence of other consumers' do
    # This test should expect a 404 rather than a 503, as we're explicitly minimizing the amount
    # of information provided in the no-permission case. Consumer 1 has no access to consumer 2,
    # and should not be able to determine whether or not consumer 2 even exists.

    expect {
      @consumer1.consumer_exists(@consumer2.uuid)
    }.to raise_error(RestClient::ResourceNotFound)
  end

  it 'allows an admin to check if any consumer exists' do
    @cp.consumer_exists(@consumer1.uuid)
    @cp.consumer_exists(@consumer2.uuid)
  end

  it 'does not allow to check existence when nil input is provided' do
    expect {
      @cp.consumer_exists_bulk(nil)
    }.to raise_error(RestClient::BadRequest)
  end

  it 'return empty body when all consumer uuid exists for bulk consumer existence check' do
    post_data = [@consumer1.uuid]
    response = @cp.consumer_exists_bulk(post_data)
    expect(response).to be(nil)
  end

  it 'should raise resource not found when consumer does not exists for bulk consumer existence check' do
    post_data = [@consumer1.uuid, "test_uuid", "more_test_uuid"]
    expect {
      @cp.consumer_exists_bulk(post_data)
    }.to raise_error(RestClient::ResourceNotFound)
  end

  it 'should return non existing ids for bulk consumer existence check' do
    post_data = [@consumer1.uuid, "test_uuid", "more_test_uuid"]
    begin
      @cp.consumer_exists_bulk(post_data)
    rescue RestClient::ResourceNotFound => e
      json = JSON.parse(e.http_body)
      expect(json).to include("test_uuid", "more_test_uuid")
    end
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

  it 'should let a consumer register with content tags' do
    some_owner = create_owner(random_string('someowner'))
    client = user_client(some_owner, random_string('bob'))
    tags = [ "awesomeos", "awesomeos-workstation", "otherproduct" ]
    consumer = client.register(random_string('system1'), :system, random_string("someuuid"), {}, random_string("uname"), some_owner['key'], [], [], nil, [], nil, tags)
    consumer['contentTags'].should =~ tags
  end

  it 'should let a consumer register with annotations' do
    some_owner = create_owner(random_string('someowner'))
    client = user_client(some_owner, random_string('bob'))
    annotations = "here is a piece of information, here is another piece."
    consumer = client.register(random_string('system1'), :system, random_string("someuuid"), {}, random_string("uname"), some_owner['key'], [], [], nil, [], nil, nil, nil, nil, annotations)
    consumer['annotations'].should == annotations
  end

  it 'should let a consumer register and set created and last checkin dates' do
    owner = create_owner(random_string('owner'))
    user_name = random_string('user')
    client = user_client(owner, user_name)
    created_date = '2015-05-09T13:23:55+0000'
    checkin_date = '2015-05-19T13:23:55+0000'
    consumer = client.register(random_string('system'), type=:system, nil, {}, user_name,
              owner['key'], [], [], nil, [], nil, [], created_date, checkin_date)
    consumer['lastCheckin'].should == checkin_date
    consumer['created'].should == created_date
    #reload to be sure it was persisted
    consumer = client.get_consumer(consumer['uuid'])
    consumer['lastCheckin'].should == checkin_date
    consumer['created'].should == created_date
  end

  it 'should let a consumer register and set service level' do
    owner = create_owner(random_string('owner'))
    user_name = random_string('user')
    client = user_client(owner, user_name)

    service_level = 'test_service_level'

    consumer = client.register(random_string('system'), type=:system, nil, {}, user_name,
              owner['key'], [], [], nil, [], nil, [], nil, nil, nil, nil, nil, 0, nil, service_level)

    expect(consumer['serviceLevel']).to eq(service_level)

    #reload to be sure it was persisted
    consumer = client.get_consumer(consumer['uuid'])
    expect(consumer['serviceLevel']).to eq(service_level)
  end

  it 'should let a consumer register and disable autoheal' do
    owner = create_owner(random_string('owner'))
    user_name = random_string('user')
    client = user_client(owner, user_name)

    consumer = client.register(random_string('system'), type=:system, nil, {}, user_name,
                               owner['key'], [], [], nil, [], nil, [], nil, nil, nil, nil, nil, 0, nil, nil,
                               nil, nil, nil, nil, false)

    expect(consumer['autoheal']).to eq(false)

    #reload to be sure it was persisted
    consumer = client.get_consumer(consumer['uuid'])
    expect(consumer['autoheal']).to eq(false)
  end

  it 'should let a consumer register and set system purpose role' do
    owner = create_owner(random_string('owner'))
    user_name = random_string('user')
    client = user_client(owner, user_name)

    role = 'test_role'

    consumer = client.register(random_string('system'), type=:system, nil, {}, user_name,
              owner['key'], [], [], nil, [], nil, [], nil, nil, nil, nil, nil, 0, nil, nil, role)

    expect(consumer['role']).to eq(role)

    #reload to be sure it was persisted
    consumer = client.get_consumer(consumer['uuid'])
    expect(consumer['role']).to eq(role)
  end

  it 'should let a consumer register and set system purpose usage' do
    owner = create_owner(random_string('owner'))
    user_name = random_string('user')
    client = user_client(owner, user_name)

    usage = 'test_usage'

    consumer = client.register(random_string('system'), type=:system, nil, {}, user_name,
              owner['key'], [], [], nil, [], nil, [], nil, nil, nil, nil, nil, 0, nil, nil, nil, usage)

    expect(consumer['usage']).to eq(usage)

    #reload to be sure it was persisted
    consumer = client.get_consumer(consumer['uuid'])
    expect(consumer['usage']).to eq(usage)
  end

  it 'should let a consumer register and set system purpose addons' do
    owner = create_owner(random_string('owner'))
    user_name = random_string('user')
    client = user_client(owner, user_name)

    addons = ['test_addon-1', 'test_addon-2', 'test_addon-3']

    consumer = client.register(random_string('system'), type=:system, nil, {}, user_name,
              owner['key'], [], [], nil, [], nil, [], nil, nil, nil, nil, nil, 0, nil, nil, nil, nil, addons)

    expect(consumer['addOns']).to_not be_nil
    expect(consumer['addOns'].size).to eq(addons.size)
    expect(consumer['addOns']).to include(*addons)

    #reload to be sure it was persisted
    consumer = client.get_consumer(consumer['uuid'])
    expect(consumer['addOns']).to_not be_nil
    expect(consumer['addOns'].size).to eq(addons.size)
    expect(consumer['addOns']).to include(*addons)
  end

  it 'should let a consumer register dates with milliseconds' do
    # to confirm the lack of a parse exception.
    owner = create_owner(random_string('owner'))
    user_name = random_string('user')
    client = user_client(owner, user_name)
    created_date = '2015-05-09T13:23:55.968+0000'
    checkin_date = '2015-05-19T13:25:55.222+0000'
    consumer = client.register(random_string('system'), type=:system, nil, {}, user_name,
              owner['key'], [], [], nil, [], nil, [], created_date, checkin_date)
    consumer = client.get_consumer(consumer['uuid'])
    consumer['lastCheckin'].should == '2015-05-19T13:25:55+0000'
    consumer['created'].should == '2015-05-09T13:23:55+0000'
  end

  it "should not let a consumer register with a used hypervisorId in same the org" do
    some_owner = create_owner(random_string('someowner'))
    client = user_client(some_owner, random_string('bob'))
    consumer = client.register(random_string('system1'), :system, random_string("someuuid"), {}, random_string("uname"), some_owner['key'], [], [], nil, [], "aBcD")
    # hypervisorId should always be set to lower case for the database constraint
    consumer['hypervisorId']['hypervisorId'].should == "abcd"

    lambda do
      client.register(random_string('system2'), :system, random_string("someuuid"), {}, random_string("uname"), some_owner['key'], [], [], nil, [], "abCd")
    end.should raise_exception(RestClient::BadRequest)
  end

  it "should let a consumer register with consumer type as a label" do
    # This is legacy functionality; this test should not be used to indicate expected or desired
    # behavior going forward, but to note that at the time of writing, this functionality is
    # required by older clients

    owner = create_owner(random_string('owner'))
    user_name = random_string('user')
    client = user_client(owner, user_name)

    path = client.get_path("consumers")

    consumer = {
      :uuid => random_string("someuuid"),
      :type => "system",
      :name => "test_consumer",
      :facts => {},
      :installedProducts => [],
      :contentTags => [],
    }

    params = {
      :owner => owner['key']
    }

    consumer = client.post(path, params, consumer)
    expect(consumer).to_not be_nil
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
    prod = create_product(random_string('product'),
      random_string('product-multiple-arch'),
          {:attributes => { :arch => 'i386, x86_64'}, :owner => owner['key']})
    pool = create_pool_and_subscription(owner['key'], prod.id)

    cp_client.consume_pool(pool.id, {:quantity => 1}).size.should == 1
  end

  it 'updates consumer updated timestamp on bind' do
    consumer = @user1.register(random_string("meow"))
    consumer_client = Candlepin.new(nil, nil, consumer['idCert']['cert'], consumer['idCert']['key'])
    prod = create_product(nil, nil, {:owner => @owner1['key']})
    pool = create_pool_and_subscription(@owner1['key'], prod.id)

    # Do a bind and make sure the updated timestamp changed:
    old_updated = @cp.get_consumer(consumer['uuid'])['updated']

    # MySQL before 5.6.4 doesn't store fractional seconds on timestamps.
    sleep 1

    consumer_client.consume_pool(pool['id'], {:quantity => 1})
    new_updated = @cp.get_consumer(consumer['uuid'])['updated']
    new_updated.should_not == old_updated
  end

  it 'consumer can async bind by product id' do
    product_id = random_string('prod')
    product = create_product(product_id, product_id, {:owner => @owner1['key']})
    pool = create_pool_and_subscription(@owner1['key'], product.id)

    status = @consumer1.consume_product(product.id, { :async => true })

    job_status = wait_for_job(status['id'], 15)
    job_status.should_not be_nil
    job_status['state'].should == 'FINISHED'

    entitlements = @consumer1.list_entitlements()
    entitlements.size.should == 1
    entitlements[0]['pool']['productId'].should == product.id
  end

  it 'should allow consumer to bind to products based on product socket quantity across pools' do
    owner = create_owner random_string('owner')
    owner_client = user_client(owner, random_string('testowner'))
    cp_client = consumer_client(owner_client, random_string('consumer123'), :system, nil, 'cpu.cpu_socket(s)' => '4')
    prod1 = create_product(random_string('product'), random_string('product-stackable'),
      {:attributes => { :sockets => '2', :'multi-entitlement' => 'yes', :stacking_id => '8888'}, :owner => owner['key']})
    prod2 = create_product(random_string('product'), random_string('product-stackable'),
      {:attributes => { :sockets => '2', :'multi-entitlement' => 'yes', :stacking_id => '8888'}, :owner => owner['key']})
    create_pool_and_subscription(owner['key'], prod1.id, 1, [], '', '', '', nil, nil, true)
    create_pool_and_subscription(owner['key'], prod1.id, 1, [], '', '', '', nil, nil, true)
    create_pool_and_subscription(owner['key'], prod2.id, 1)

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

  it 'should not allow the system uuid to be used to match a consumer across orgs' do
    owner1 = create_owner random_string('owner')
    user1 = user_client(owner1, random_string('user'))
    owner2 = create_owner random_string('owner')
    user2 = user_client(owner2, random_string('user'))

    host_name = random_string("hostname")
    system_id = "system_id"

    test1 = user1.register(host_name, :system, nil, {"dmi.system.uuid" => system_id, "virt.is_guest"=>"false"}, nil, owner1['key'])
    @cp.get_consumer(test1.uuid)['uuid'].should == test1['uuid']

    # different org should not use the same consumer record because of system uuid
    test2 = user2.register(host_name, :system, nil, {"dmi.system.uuid" => system_id, "virt.is_guest"=>"false"}, nil, owner2['key'])
    @cp.get_consumer(test2.uuid)['uuid'].should_not == test1['uuid']

    # same org should use the same consumer record because of system uuid
    test3 = user1.register(host_name, :system, nil, {"dmi.system.uuid" => system_id, "virt.is_guest"=>"false"}, nil, owner1['key'])
    @cp.get_consumer(test3.uuid)['uuid'].should == test1['uuid']
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

    consumer_client = Candlepin.new(nil, nil, consumer['idCert']['cert'], consumer['idCert']['key'])
    consumer_client.update_consumer({:installedProducts => installed})
    consumer = @cp.get_consumer(consumer['uuid'])
    verify_installed_pids(consumer, [pid1, pid3])

    # Make sure facts weren't clobbered:
    consumer['facts'].length.should == 1
  end

  it 'should not allow a RHSM client to register a manifest consumer' do
    user = user_client(@owner1, random_string('billy'))
    lambda do
      consumer = user.register(random_string('machine1'), :candlepin, nil,
                               {}, nil, nil, [], [], nil, [], nil, [], nil, nil,
                               nil, nil, "RHSM/1.0 (cmd=subscription-manager)")
    end.should raise_exception(RestClient::BadRequest)

  end

  it 'should allow the installed products to be enriched with product information' do
    owner = create_owner random_string('owner')
    user = user_client(owner, random_string("user"))
    cp_client = consumer_client(user, random_string('consumer'), :system,
                                nil, {:'arch' => 'test_arch1'}, owner['key'])

    product1 = create_product(random_string('product'), random_string('product'),
      {:attributes => { :'arch' => 'ALL',
      :'version' => '3.11',
      :'multi-entitlement' => 'yes'},
      :owner => owner['key']})
    installed = [
        {'productId' => product1.id, 'productName' => product1.name}
    ]
    cp_client.update_consumer({:installedProducts => installed})

    now = DateTime.now
    create_pool_and_subscription(owner['key'], product1.id, 1, [], '', '', '', now, now + 365)

    for pool in @cp.list_owner_pools(owner['key']) do
        cp_client.consume_pool(pool.id, {:quantity => 1})
    end

    consumer = @cp.get_consumer(cp_client.uuid)
    for installed_product in consumer['installedProducts'] do
        installed_product['arch'].should == 'ALL'
        installed_product['version'].should == '3.11'
        installed_product['status'].should == 'green'

        start_date = DateTime.strptime(installed_product['startDate']).new_offset(now.offset)
        start_date.year.should == now.year
        start_date.month.should == now.month
        start_date.day.should == now.day

        end_date = DateTime.strptime(installed_product['endDate']).new_offset(now.offset)
        end_date.year.should == (now + 365).year
        end_date.month.should == (now + 365).month
        end_date.day.should == (now + 365).day
    end
  end

  it 'should allow a consumer to update their autoheal flag' do
    user_cp = user_client(@owner1, random_string('billy'))
    consumer = user_cp.register(random_string('system'), :system, nil, {}, nil, nil, [], [])
    consumer_client = Candlepin.new(nil, nil, consumer['idCert']['cert'], consumer['idCert']['key'])
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
    consumer = user_cp.register(random_string('system'), :system, nil, {}, nil, nil, [], [])
    consumer_client = Candlepin.new(nil, nil, consumer['idCert']['cert'], consumer['idCert']['key'])

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
    consumer = user_cp.register(random_string('system'), :system, nil, {}, nil, nil, [], [])
    consumer_client = Candlepin.new(nil, nil, consumer['idCert']['cert'], consumer['idCert']['key'])
    consumer1 = user_cp.register(random_string('system'), :system, nil, {}, nil, nil, [], [], nil, [], "hYpervisor")
    consumer_client1 = Candlepin.new(nil, nil, consumer1['idCert']['cert'], consumer1['idCert']['key'])

    consumer = consumer_client.get_consumer()
    consumer['hypervisorId'].should == nil
    consumer1 =  consumer_client1.get_consumer()
    consumer1['hypervisorId']['hypervisorId'].should == "hypervisor"

    begin
      consumer_client.update_consumer({:hypervisorId => "hypervisor"})
      fail 'Should have failed!'
    rescue RestClient::BadRequest => e
      json = JSON.parse(e.http_body)
      json.displayMessage.end_with?('Hypervisor id: hypervisor is already used.').should == true
    end

    # should handle update to same consumer without issue
    consumer_client1.update_consumer({:hypervisorId => "hypervisor"})


  end

  it 'should allow a consumer to unset their hypervisorId' do
    user_cp = user_client(@owner1, random_string('billy'))
    consumer = user_cp.register(random_string('system'), :system, nil, {}, nil, nil, [], [])
    consumer_client = Candlepin.new(nil, nil, consumer['idCert']['cert'], consumer['idCert']['key'])

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
      {:attributes => {:support_level => 'VIP'},
      :owner => @owner1['key']})
    product2 = create_product(random_string('product'),
      random_string('product'),
      {:attributes => {:support_level => 'Layered',
      :support_level_exempt => 'true'},
      :owner => @owner1['key']})
    create_pool_and_subscription(@owner1['key'], product1.id, 1, [], '', '', '', nil, nil, true)
    create_pool_and_subscription(@owner1['key'], product2.id)

    user_cp = user_client(@owner1, random_string('billy'))
    consumer = user_cp.register(random_string('system'), :system, nil,
      {}, nil, nil, [], [])
    consumer_client = Candlepin.new(nil, nil, consumer['idCert']['cert'], consumer['idCert']['key'])
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

    # The service level should be case insensitive
    consumer_client.update_consumer({:serviceLevel => ''})
    consumer = @cp.get_consumer(consumer['uuid'])
    consumer['serviceLevel'].should == ''

    consumer_client.update_consumer({:serviceLevel => 'vip'})
    consumer = @cp.get_consumer(consumer['uuid'])
    consumer['serviceLevel'].should == 'vip'

  end

  it 'should not list service levels from expired pools' do
    product = create_product(random_string('product'), random_string('product'), {
      :owner => @owner1['key'],
      :attributes => {
        :support_level => 'Expired'
      }
    })

    now = DateTime.now
    create_pool_and_subscription(@owner1['key'], product.id, 1, [], '', '', '', now - 2, now - 1)

    user_cp = user_client(@owner1, random_string('billy'))
    consumer = user_cp.register(random_string('system'), :system, nil, {}, nil, nil, [], [])
    consumer_client = Candlepin.new(nil, nil, consumer['idCert']['cert'], consumer['idCert']['key'])
    consumer = @cp.get_consumer(consumer['uuid'])

    service_levels = consumer_client.list_owner_service_levels(@owner1['key'])
    service_levels.length.should eq(0)
  end

  it 'should allow a consumer without existing entitlements to dry run an autoattach based on SLA but not filter on it' do
    product1 = create_product(random_string('product'),
      random_string('product'),
      {:attributes => {:support_level => 'VIP'},
      :owner => @owner1['key']})
    product2 = create_product(random_string('product'),
      random_string('product'),
      {:attributes => {:support_level => 'Ultra-VIP'},
      :owner => @owner1['key']})
    pool1 = create_pool_and_subscription(@owner1['key'], product1.id)
    pool2 = create_pool_and_subscription(@owner1['key'], product2.id)

    user_cp = user_client(@owner1, random_string('billy'))
    consumer = user_cp.register(random_string('system'), :system, nil,
      {}, nil, nil, [], [])
    consumer_client = Candlepin.new(nil, nil, consumer['idCert']['cert'], consumer['idCert']['key'])
    installed = [
        {'productId' => product1.id, 'productName' => product1.name},
        {'productId' => product2.id, 'productName' => product2.name}]

    consumer_client.update_consumer({:serviceLevel => 'VIP',
                                     :installedProducts => installed})
    consumer = @cp.get_consumer(consumer['uuid'])
    consumer['serviceLevel'].should == 'VIP'

    # dry run against the set service level:
    # should return both pools because we no longer filter on consumer's sla
    # (unless the consumer has existing entitlements, and in this case we don't).
    pools = @cp.autobind_dryrun(consumer['uuid'])
    pools.length.should == 2
    pool1_id = pools[0].pool.id
    pool2_id = pools[1].pool.id
    pool = @cp.get_pool(pool1_id)
    pool.subscriptionId.should == pool1.subscriptionId
    pool = @cp.get_pool(pool2_id)
    pool.subscriptionId.should == pool2.subscriptionId

    # dry run against the override service level:
    # should return both pools because we no longer filter on the SLA override
    # (unless the consumer has existing entitlements, and in this case we don't).
    pools = @cp.autobind_dryrun(consumer['uuid'], 'Ultra-VIP')
    pools.length.should == 2
    pool1_id = pools[0].pool.id
    pool2_id = pools[1].pool.id
    pool = @cp.get_pool(pool1_id)
    pool.subscriptionId.should == pool1.subscriptionId
    pool = @cp.get_pool(pool2_id)
    pool.subscriptionId.should == pool2.subscriptionId

    # ensure the override use did not change the setting
    consumer = @cp.get_consumer(consumer['uuid'])
    consumer['serviceLevel'].should == 'VIP'

    # dry run against 1) no consumer SLA 2) no override SLA 3) with owner default SLA:
    # should return both pools because we no longer filter on the owner's default SLA.
    # (unless the consumer has existing entitlements, and in this case we don't).
    consumer_client.update_consumer({:serviceLevel => ''})
    consumer = @cp.get_consumer(consumer['uuid'])
    consumer['serviceLevel'].should == ''
    @cp.update_owner(@owner1['key'], {:defaultServiceLevel => 'VIP'})

    # dry run against the override service level should be case insensitive
    pools = @cp.autobind_dryrun(consumer['uuid'], 'Ultra-vip')
    pools.length.should == 2
    pool1_id = pools[0].pool.id
    pool2_id = pools[1].pool.id
    pool = @cp.get_pool(pool1_id)
    pool.subscriptionId.should == pool1.subscriptionId
    pool = @cp.get_pool(pool2_id)
    pool.subscriptionId.should == pool2.subscriptionId
  end

  it "should allow a consumer dry run an autoattach based on SLA and do not filter pool on their existing
    entitlement SLAs" do
    product1 = create_product(random_string('product'),
      random_string('product'),
      {:attributes => {:support_level => 'VIP'},
      :owner => @owner1['key']})
    product2 = create_product(random_string('product'),
      random_string('product'),
      {:attributes => {:support_level => 'Ultra-VIP'},
      :owner => @owner1['key']})
    product3 = create_product(random_string('product'),
      random_string('product'),
      {:attributes => {:support_level => 'Ultra-VIP'},
      :owner => @owner1['key']})

    pool1 = create_pool_and_subscription(@owner1['key'], product1.id)
    pool2 = create_pool_and_subscription(@owner1['key'], product2.id)
    pool3 = create_pool_and_subscription(@owner1['key'], product3.id)

    user_cp = user_client(@owner1, random_string('billy'))
    consumer = user_cp.register(random_string('system'), :system, nil,
      {}, nil, nil, [], [])
    consumer_client = Candlepin.new(nil, nil, consumer['idCert']['cert'], consumer['idCert']['key'])
    installed = [
        {'productId' => product1.id, 'productName' => product1.name},
        {'productId' => product2.id, 'productName' => product2.name},
        {'productId' => product3.id, 'productName' => product3.name}]

    # We set the consumer's SLA as VIP
    consumer_client.update_consumer({:serviceLevel => 'VIP',
                                     :installedProducts => installed})

    consumer = @cp.get_consumer(consumer['uuid'])
    consumer['serviceLevel'].should == 'VIP'

    # We explicitly attach to a pool with Ultra-VIP SLA
    consumer_client.consume_pool(pool2.id, {:quantity => 1})

    # dry run against the set service level:
    # NOTE : we do NOT filter on the consumer's existing entitlements' SLA's, and in this case
    # the consumer has an existing entitlement whose SLA is 'Ultra-VIP',
    # so 'Ultra-VIP' & 'VIP' both pools are considered and eligible during auto attach.
    returned_pools = @cp.autobind_dryrun(consumer['uuid'])
    returned_pools.length.should == 2
    returned_pool_id = returned_pools.first.pool.id
    returned_pool = @cp.get_pool(returned_pool_id)
    returned_pool.subscriptionId.should == pool1.subscriptionId
    expect(get_attribute_value(returned_pool['productAttributes'], 'support_level')).to eq('VIP')

    returned_pool_id = returned_pools[1].pool.id
    returned_pool = @cp.get_pool(returned_pool_id)
    returned_pool.subscriptionId.should == pool3.subscriptionId
    expect(get_attribute_value(returned_pool['productAttributes'], 'support_level')).to eq('Ultra-VIP')
  end

  it 'should recognize support level exempt attribute' do
    product1 = create_product(random_string('product'),
      random_string('product'),
      {:attributes => {:support_level => 'Layered',
      :support_level_exempt => 'true'},
      :owner => @owner1['key']})
    product2 = create_product(random_string('product'),
      random_string('product'),
      {:attributes => {:support_level => 'VIP'},
      :owner => @owner1['key']})
    product3 = create_product(random_string('product'),
      random_string('product'),
      {:attributes => {:support_level => 'Ultra-VIP'},
      :owner => @owner1['key']})
    product4 = create_product(random_string('product'),
      random_string('product'),
      {:attributes => {:support_level => 'LAYered'},
      :owner => @owner1['key']})
    pool1 = create_pool_and_subscription(@owner1['key'], product1.id)
    pool2 = create_pool_and_subscription(@owner1['key'], product2.id)
    pool3 = create_pool_and_subscription(@owner1['key'], product3.id)

    user_cp = user_client(@owner1, random_string('billy'))
    consumer = user_cp.register(random_string('system'), :system, nil,
      {}, nil, nil, [], [])
    consumer_client = Candlepin.new(nil, nil, consumer['idCert']['cert'], consumer['idCert']['key'])

    installed = [
        {'productId' => product1.id, 'productName' => product1.name},
        {'productId' => product2.id, 'productName' => product2.name},
        {'productId' => product4.id, 'productName' => product4.name}]

    consumer_client.update_consumer({:serviceLevel => 'Ultra-VIP',
                                     :installedProducts => installed})
    consumer = @cp.get_consumer(consumer['uuid'])
    consumer['serviceLevel'].should == 'Ultra-VIP'

    # Dry run against the set service level:
    # Should get pools of both the exempt product (product1) and the other installed product (product2),
    # because we no longer filter on the consumer's sla match (unless the consumer has existing entitlements),
    # and exempt sla pools are always returned.
    pools = @cp.autobind_dryrun(consumer['uuid'])
    pools.length.should == 2
    pool1_id = pools[0].pool.id
    pool2_id = pools[1].pool.id
    pool = @cp.get_pool(pool1_id)
    pool.subscriptionId.should == pool1.subscriptionId
    pool = @cp.get_pool(pool2_id)
    pool.subscriptionId.should == pool2.subscriptionId

    # this product should also get pulled, exempt overrides
    # based on name match
    create_pool_and_subscription(@owner1['key'], product4.id)
    pools = @cp.autobind_dryrun(consumer['uuid'])
    pools.length.should == 3

    # changing consumer's service level to one that matches installed
    # should have no effect because we are not filtering on it (unless the consumer has existing entitlements),
    # and exempt sla pools are always returned.
    consumer_client.update_consumer({:serviceLevel => 'VIP'})
    pools = @cp.autobind_dryrun(consumer['uuid'])
    pools.length.should == 3
  end

  it 'should return empty list for dry run where all pools are blocked because of consumer type' do
    product1 = create_product(random_string('product'),
      random_string('product'),
      {:attributes => {:requires_consumer_type => :person},
      :owner => @owner1['key']})
    product2 = create_product(random_string('product'),
      random_string('product'),
      {:attributes => {:requires_consumer_type => :person},
      :owner => @owner1['key']})
    create_pool_and_subscription(@owner1['key'], product1.id, 1, [], '', '', '', nil, nil, true)
    create_pool_and_subscription(@owner1['key'], product2.id)

    user_cp = user_client(@owner1, random_string('billy'))
    consumer = user_cp.register(random_string('system'), :system, nil,
      {}, nil, nil, [], [])
    consumer_client = Candlepin.new(nil, nil, consumer['idCert']['cert'], consumer['idCert']['key'])
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
    product = create_product(nil, nil, {:owner => owner['key']})
    create_pool_and_subscription(owner['key'], product.id, 2)
    pool = consumer.list_pools(:consumer => consumer.uuid)[0]
    pool.consumed.should == 0
    consumer.consume_pool(pool.id, {:quantity => 1})
    @cp.get_pool(pool.id).consumed.should == 1
    consumer.unregister(consumer.uuid)
    @cp.get_pool(pool.id).consumed.should == 0
  end

  it 'should allow a consumer to bind when the POST body is empty string' do
    owner = create_owner(random_string('zowner'))
    user = user_client(owner, random_string('cukebuster'))
    # performs the register for us
    consumer = consumer_client(user, random_string('machine1'))
    product = create_product(nil, nil, {:owner => owner['key']})
    create_pool_and_subscription(owner['key'], product.id, 2)
    pool = consumer.list_pools(:consumer => consumer.uuid)[0]
    pool.consumed.should == 0
    consumer.consume_pool_empty_body(pool.id)
    @cp.get_pool(pool.id).consumed.should == 1
  end

  it 'should allow a consumer to unregister and free up the pools consumed in a batch' do
    owner = create_owner(random_string('zowner'))
    user = user_client(owner, random_string('cukebuster'))
    # performs the register for us
    consumer = consumer_client(user, random_string('machine1'))
    product = create_product(nil, nil, {:owner => owner['key']})
    create_pool_and_subscription(owner['key'], product.id, 2)
    create_pool_and_subscription(owner['key'], product.id, 2)
    create_pool_and_subscription(owner['key'], product.id, 2)
    pools = consumer.list_pools(:consumer => consumer.uuid)
    pools.length.should == 3
    poolAndQuantities = []
    pools.each do |pool|
      consumer.consume_pool(pool['id'])
    end

    pools.each do |pool|
      @cp.get_pool(pool.id).consumed.should == 1
    end
    consumer.unregister(consumer.uuid)
    pools.each do |pool|
      @cp.get_pool(pool.id).consumed.should == 0
    end
  end

  it 'should ignore duplicate facts' do
    user = user_client(@owner1, random_string('billy'))
    facts = {
      'system.machine' => 'x86_64',
      'System.machine' => 'x86_64',
      'System.Machine' => 'x86_64',
      'SYSTEM.MACHINE' => 'x86_64',
    }
    consumer = user.register('machine1', :system, nil, facts, nil, nil, [], nil)
    consumer['facts'].size.should == 1
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

    consumer_client = Candlepin.new(nil, nil, consumer['idCert']['cert'], consumer['idCert']['key'])
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
      {:attributes => { :sockets => '1', :'multi-entitlement' => 'yes', :stacking_id => 'consumer-bind-test'}, :owner => @owner1['key']})
    pool = create_pool_and_subscription(@owner1['key'], product1.id, 10)
    installed = [
        {'productId' => product1.id, 'productName' => product1.name}
    ]
    @consumer1.update_consumer({:installedProducts => installed, :facts => facts})
    ent = @consumer1.consume_pool(pool.id)
    ent[0]["quantity"].should == 4
  end

  it "should bind quantity 1 when suggested is 0 and not specified" do
    facts = {
      'cpu.cpu_socket(s)' => '4',
    }
    product1 = create_product(random_string('product'), random_string('product-multiple-arch'),
      {:attributes => { :sockets => '2', :'multi-entitlement' => 'yes', :stacking_id => 'consumer-bind-test'}, :owner => @owner1['key']})
    pool = create_pool_and_subscription(@owner1['key'], product1.id, 10)
    installed = [
        {'productId' => product1.id, 'productName' => product1.name}
    ]
    @consumer1.update_consumer({:installedProducts => installed, :facts => facts})
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
      {:attributes => { :sockets => '2', :'multi-entitlement' => 'yes', :stacking_id => 'consumer-bind-test'}, :owner => @owner1['key']})
    current_pool = create_pool_and_subscription(@owner1['key'], product1.id, 10)
    start = DateTime.now + 400
    future_pool = create_pool_and_subscription(@owner1['key'], product1.id, 10, [], '', '', '', start)
    installed = [
        {'productId' => product1.id, 'productName' => product1.name}
    ]
    @consumer1.update_consumer({:installedProducts => installed, :facts => facts})
    # Fully cover the product1 for a year
    @consumer1.consume_pool(current_pool.id, {:quantity => 2})

    ent = @consumer1.consume_pool(future_pool.id)[0]
    ent["quantity"].should == 2
    ent.startDate[0..18].should == start.new_offset(0).strftime[0..18]
  end

  it 'should be able to add unused attributes' do
    guests = [{'guestId' => 'guest1', 'fooBar' => 'some value'}]

    user_cp = user_client(@owner1, random_string('test-user'))
    consumer = user_cp.register(random_string('host'), :system, nil,
      {}, nil, nil, [], [])

    consumer_client = Candlepin.new(nil, nil, consumer['idCert']['cert'], consumer['idCert']['key'])
    consumer_client.update_consumer({:guestIds => guests})

    guestIds = consumer_client.get_guestids()
    guestIds.length.should == 1
    guestIds[0]['guestId'].should == 'guest1'
  end

  it 'should return correct exception for contraint violations' do
    lambda {
      user_cp = user_client(@owner1, random_string('test-user'))
      user_cp.register("a" * 256, :system, nil,
      {}, nil, nil, [], [])
    }.should raise_exception(RestClient::BadRequest)
    lambda {
      user_cp = user_client(@owner1, random_string('test-user'))
      user_cp.register(random_string('test-consumer'), :system, "a" * 256,
      {}, nil, nil, [], [])
    }.should raise_exception(RestClient::BadRequest)
    lambda {
      user_cp = user_client(@owner1, random_string('test-user'))
      user_cp.register(nil, :system, nil,
      {}, nil, nil, [], [])
    }.should raise_exception(RestClient::BadRequest)
  end

  it 'updates consumer entitlement count on bind and revoke' do
    consumer = @user1.register(random_string("meow"))
    consumer_client = Candlepin.new(nil, nil, consumer['idCert']['cert'], consumer['idCert']['key'])
    prod = create_product(random_string("product"), random_string("product"), {:owner => @owner1['key']})
    prod1 = create_product(random_string("product"), random_string("product"), {:owner => @owner1['key']})
    prod2 = create_product(random_string("product"), random_string("product"), {:owner => @owner1['key']})

    pool = create_pool_and_subscription(@owner1['key'], prod.id)
    ent = consumer_client.consume_pool(pool['id'], {:quantity => 1}).first
    @cp.get_consumer(consumer['uuid'])['entitlementCount'].should == 1

    pool1 = create_pool_and_subscription(@owner1['key'], prod1.id)
    ent1 = consumer_client.consume_pool(pool1['id'], {:quantity => 1}).first
    @cp.get_consumer(consumer['uuid'])['entitlementCount'].should == 2

    pool2 = create_pool_and_subscription(@owner1['key'], prod2.id)
    ent2 = consumer_client.consume_pool(pool2['id'], {:quantity => 1}).first
    @cp.get_consumer(consumer['uuid'])['entitlementCount'].should == 3

    consumer_client.unbind_entitlement(ent1.id)
    @cp.get_consumer(consumer['uuid'])['entitlementCount'].should == 2
    consumer_client.unbind_entitlement(ent.id)
    @cp.get_consumer(consumer['uuid'])['entitlementCount'].should == 1
    consumer_client.unbind_entitlement(ent2.id)
    @cp.get_consumer(consumer['uuid'])['entitlementCount'].should == 0
  end

  it 'concurrent unregister should return 404 or 410 when consumer is deleted by another request' do
    consumer = @user1.register(random_string("a_test_consumer"))
    consumer_client = Candlepin.new(nil, nil, consumer['idCert']['cert'], consumer['idCert']['key'])

    total_threads = 50
    t_count = 0;
    unexpected_exceptions = []
    expected_exceptions = []
    threads = []
    total_threads.times do
      t = Thread.new do
        begin
          @user1.unregister(consumer['uuid'])
        rescue RestClient::ResourceNotFound => rnf
          # Expected and OK.
          expected_exceptions << rnf
        rescue RestClient::Gone => gone
          expected_exceptions << gone
        rescue Exception => e
          # Unexpected - report it.
          unexpected_exceptions << e
        end
        t_count = t_count + 1
      end
      threads << t
    end

    threads.each { |thread| thread.join}
    t_count.should == total_threads
    unexpected_exceptions.should be_empty
    expected_exceptions.should_not be_empty

    # Note: 404 can be returned in cases where a request was made after the initial deletion.
    #       With a large number of requests, we should expect 1 or more of each.
    expected_exceptions.each { |e| e.should be_an(RestClient::Gone) | be_an(RestClient::ResourceNotFound) }
  end

  it "should not allow to content access certificate body when not SCA mode" do
    expect {
      @consumer1.get_content_access_body()
    }.to raise_exception(RestClient::BadRequest)
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
    consumers = @cp.list_consumers({:fact => ['*key*:*val*']})
    # Length should be at least the three we have defined, however there could be other rows...
    consumers.length.should >= 3
  end

  it 'can filter by facts and uuids' do
    consumers = @cp.list_consumers({:fact => ['oth*key*:*val'], :uuid => [@consumer1['uuid'], @consumer2['uuid'], @consumer3['uuid']]})
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
    consumers = @cp.list_consumers({:owner => @owner['key'], :fact => ["trolol:'); DROP TABLE cp_consumer;"]})
    consumers.length.should == 1
    consumers[0]['uuid'].should == odd_consumer['uuid']
  end

end
