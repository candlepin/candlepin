require 'candlepin_scenarios'

describe 'Pool Resource' do

  include CandlepinMethods
  it_should_behave_like 'Candlepin Scenarios'

  it 'lets consumers view pools' do
    owner1 = create_owner random_string('test_owner')
    owner1_client = user_client(owner1, random_string('testuser'))

    product = create_product()
    pool = @cp.create_pool(product.id, product.name, owner1.id, 10)

    consumer_client = consumer_client(owner1_client, random_string('testsystem'))
    p = consumer_client.get_pool(pool.id)
    p.id.should == pool.id
  end

  it 'does not let consumers view another owners pool' do
    owner1 = create_owner random_string('test_owner')
    owner1_client = user_client(owner1, random_string('testuser'))
    owner2 = create_owner random_string('test_owner')
    owner2_client = user_client(owner2, random_string('testuser'))

    product = create_product()
    pool = @cp.create_pool(product.id, product.name, owner2.id, 10)

    consumer_client = consumer_client(owner1_client, random_string('testsystem'))
    lambda {
      consumer_client.get_pool(pool.id)
    }.should raise_exception(RestClient::Forbidden)
  end

  it 'does not let owner admins view another owners pool' do
    owner1 = create_owner random_string('test_owner')
    owner1_client = user_client(owner1, random_string('testuser'))
    owner2 = create_owner random_string('test_owner')
    owner2_client = user_client(owner2, random_string('testuser'))

    product = create_product()
    pool = @cp.create_pool(product.id, product.name, owner2.id, 10)

    lambda {
      owner1_client.get_pool(pool.id)
    }.should raise_exception(RestClient::Forbidden)
  end

  it 'supports pool deletion' do
    owner1 = create_owner random_string('test_owner')
    owner1_client = user_client(owner1, random_string('testuser'))
    product = create_product()
    pool = @cp.create_pool(product.id, product.name, owner1.id, 10)
    @cp.delete_pool(pool['id'])
    lambda { @cp.get_pool(pool['id']) }.should \
      raise_exception(RestClient::ResourceNotFound)
  end

  it 'prevents pool deletion if backed by subscription' do
    owner1 = create_owner random_string('test_owner')
    owner1_client = user_client(owner1, random_string('testuser'))
    product = create_product()
    @cp.create_subscription(owner1.key, product.id, 2)
    @cp.refresh_pools(owner1.key)
    pool = @cp.list_pools(:owner => owner1.id)[0]
    lambda { @cp.delete_pool(pool['id']) }.should raise_exception(RestClient::Forbidden)
  end

  it 'should not return expired pools' do
    owner = create_owner random_string('donaldduck')
    client = user_client(owner, random_string('testusr'))
    product = create_product()
    @cp.create_subscription(owner.key, product.id, 5,
      [], '', '', Date.today - 60, Date.today - 1)
    @cp.refresh_pools(owner.key)
    (@cp.list_pools :owner => owner.id).size.should == 0
  end

  it 'should not list pools with errors for a consumer if listall is used' do
    owner = create_owner random_string('test_owner')
    admin_cp = user_client(owner, random_string('testuser'))

    product = create_product()
    # Pool with just one entitlement available:
    @cp.create_subscription(owner.key, product.id, 1)
    @cp.refresh_pools(owner.key)
    pool = admin_cp.list_pools({:owner => owner.id})[0]

    consumer1_cp = consumer_client(admin_cp, random_string('testsystem'))
    consumer2_cp = consumer_client(admin_cp, random_string('testsystem'))

    # Consume that one entitlement:
    consumer1_cp.consume_pool(pool.id).size.should == 1

    pools = consumer2_cp.list_pools({:consumer => consumer2_cp.uuid,
      :listall => true})

    # Pool should be omitted if we query for a consumer and use listall:
    pools.size.should == 0
  end

  it 'should list pools with warnings for a consumer if listall is used' do
    owner = create_owner random_string('test_owner')
    admin_cp = user_client(owner, random_string('testuser'))

    # Create a product for an arch our consumer will not match:
    product = create_product(nil, nil, {:attributes => {:arch => "X86"}})

    # Pool with just one entitlement available:
    @cp.create_subscription(owner.key, product.id, 1)
    @cp.refresh_pools(owner.key)
    pool = admin_cp.list_pools({:owner => owner.id})[0]

    consumer1_cp = consumer_client(admin_cp, random_string('testsystem'),
      :system, nil, {"cpu.architecture" => "X86_64"})

    pools = consumer1_cp.list_pools({:consumer => consumer1_cp.uuid,
      :listall => true})

    # Should see the pool despite rules warning because we used listall:
    pools.size.should == 1
  end
end
