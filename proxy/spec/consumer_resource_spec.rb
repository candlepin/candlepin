require 'candlepin_scenarios'

describe 'Consumer Resource' do

  include CandlepinMethods
  it_should_behave_like 'Candlepin Scenarios'

  it 'allows super admins to see all consumers' do
    owner1 = create_owner random_string('test_owner1')
    user1 = user_client(owner1, random_string("user1"))
    consumer1 = consumer_client(user1, random_string("consumer1"))

    owner2 = create_owner random_string('test_owner2')
    user2 = user_client(owner2, random_string("user2"))
    consumer2 = consumer_client(user2, random_string("consumer2"))

    uuids = []
    @cp.list_consumers.each do |c|
      # These are HATEOAS serialized consumers, the ID is the numeric DB
      # ID, so pull the UUID off the URL.
      # TODO: Find a better way once client is more HATEOASy.
      uuids << c['href'].split('/')[-1]
    end
    uuids.include?(consumer1.uuid).should be_true
    uuids.include?(consumer2.uuid).should be_true
  end

  it 'lets an owner admin see only their consumers' do
    owner1 = create_owner random_string('test_owner1')
    user1 = user_client(owner1, random_string("user1"))
    consumer1 = consumer_client(user1, random_string("consumer1"))

    owner2 = create_owner random_string('test_owner2')
    user2 = user_client(owner2, random_string("user2"))
    consumer2 = consumer_client(user2, random_string("consumer2"))

    user2.list_consumers.length.should == 1
  end

  it 'lets a super admin filter consumers by owner' do
    owner1 = create_owner random_string('test_owner1')
    user1 = user_client(owner1, random_string("user1"))
    consumer1 = consumer_client(user1, random_string("consumer1"))

    owner2 = create_owner random_string('test_owner2')
    user2 = user_client(owner2, random_string("user2"))
    consumer2 = consumer_client(user2, random_string("consumer2"))

    @cp.list_consumers.size.should > 1
    @cp.list_consumers({:owner => owner1.key}).size.should == 1
  end


  it 'lets an owner see only their system consumer types' do
    owner1 = create_owner random_string('test_owner1')
    user1 = user_client(owner1, random_string("user1"))
    consumer1 = consumer_client(user1, random_string("consumer1"))
    consumer2 = consumer_client(user1, random_string("consumer2"), 'candlepin')

    user1.list_consumers({:type => 'system'}).length.should == 1
  end

  it 'lets a super admin see a peson consumer with a given username' do
    owner1 = create_owner random_string('test_owner1')
    username = random_string "user1"
    user1 = user_client(owner1, username)
    consumer1 = consumer_client(user1, random_string("consumer1"), 'person')

    @cp.list_consumers({:type => 'person',
                       :username => username}).length.should == 1
  end

  it 'lets a super admin create person consumer for another user' do
    owner1 = create_owner random_string('test_owner1')
    username = random_string "user1"
    user1 = user_client(owner1, username)
    consumer1 = consumer_client(@cp, random_string("consumer1"), 'person',
                                username)

    @cp.list_consumers({:type => 'person',
                       :username => username}).length.should == 1
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
                                nil, 'cpu.architecture' => 'x86_64')
    prod = create_product('product', random_string('product-multiple-arch'),
                          :attribute => { :arch => 'i386, x86_64'})
    subs = @cp.create_subscription(owner.key, prod.id)
    @cp.refresh_pools(owner.key)
    pool = cp_client.list_pools.first

    cp_client.consume_pool(pool.id).size.should == 1
  end

  it 'should allow a consumer to specify their own UUID' do
    owner = create_owner random_string('owner')
    user = user_client(owner, random_string('billy'))

    consumer = user.register('machine1', :system, 'custom-uuid')
    consumer.uuid.should == 'custom-uuid'
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
    @cp.create_subscription(owner.key, product.id, 2)
    @cp.refresh_pools(owner.key)
    pool = consumer.list_pools(:consumer => consumer.uuid)[0]
    pool.consumed.should == 0
    consumer.consume_pool(pool.id)
    @cp.get_pool(pool.id).consumed.should == 1
    consumer.unregister(consumer.uuid)
    @cp.get_pool(pool.id).consumed.should == 0
  end

end
