require 'spec_helper'
require 'candlepin_scenarios'

describe 'Pool Resource' do

  include CandlepinMethods

  it 'lets consumers view pools' do
    owner1 = create_owner random_string('test_owner')
    owner1_client = user_client(owner1, random_string('testuser'))

    product = create_product
    @cp.create_subscription(owner1['key'], product.id, 10)
    @cp.refresh_pools(owner1['key'])

    pool = owner1_client.list_pools(:owner => owner1.id).first

    consumer_client = consumer_client(owner1_client, random_string('testsystem'))
    p = consumer_client.get_pool(pool.id)
    p.id.should == pool.id
    p['type'].should == 'NORMAL'
  end

  it 'does not let consumers view pool entitlements' do
    owner1 = create_owner random_string('test_owner')
    owner1_client = user_client(owner1, random_string('testuser'))

    product = create_product
    @cp.create_subscription(owner1['key'], product.id, 10)
    @cp.refresh_pools(owner1['key'])

    pool = owner1_client.list_pools(:owner => owner1.id).first

    consumer_client = consumer_client(owner1_client, random_string('testsystem'))

    # This should work fine:
    owner1_client.list_pool_entitlements(pool['id'])

    # This shouldn't:
    lambda do
      consumer_client.list_pool_entitlements(pool['id'])
    end.should raise_exception(RestClient::Forbidden)
  end

  it 'does not let consumers view another owners pool' do
    owner1 = create_owner random_string('test_owner')
    owner1_client = user_client(owner1, random_string('testuser'))
    owner2 = create_owner random_string('test_owner')
    owner2_client = user_client(owner2, random_string('testuser'))

    product = create_product
    @cp.create_subscription(owner2['key'], product.id, 10)
    @cp.refresh_pools(owner2['key'])

    pool = owner2_client.list_pools(:owner => owner2.id).first

    consumer_client = consumer_client(owner1_client, random_string('testsystem'))
    lambda {
      consumer_client.get_pool(pool.id)
    }.should raise_exception(RestClient::ResourceNotFound)
  end

  it 'does not let owner admins view another owners pool' do
    owner1 = create_owner random_string('test_owner')
    owner1_client = user_client(owner1, random_string('testuser'))
    owner2 = create_owner random_string('test_owner')
    owner2_client = user_client(owner2, random_string('testuser'))

    product = create_product(random_string 'buskers')
    @cp.create_subscription(owner2['key'], product.id, 10)
    @cp.refresh_pools(owner2['key'])

    pool = owner2_client.list_pools(:owner => owner2.id).first

    lambda {
      owner1_client.get_pool(pool.id)
    }.should raise_exception(RestClient::ResourceNotFound)
  end

  it 'should not return expired pools' do
    owner = create_owner random_string('donaldduck')
    client = user_client(owner, random_string('testusr'))
    product = create_product()
    @cp.create_subscription(owner['key'], product.id, 5,
      [], '', '', '', Date.today - 60, Date.today - 1)
    @cp.refresh_pools(owner['key'])
    (@cp.list_pools :owner => owner.id).size.should == 0
  end

  it 'should not list pools with errors for a consumer if listall is used' do
    owner = create_owner random_string('test_owner')
    admin_cp = user_client(owner, random_string('testuser'))

    product = create_product()
    # Pool with just one entitlement available:
    @cp.create_subscription(owner['key'], product.id, 1)
    @cp.refresh_pools(owner['key'])
    pool = admin_cp.list_pools({:owner => owner.id})[0]

    consumer1_cp = consumer_client(admin_cp, random_string('testsystem'))
    consumer2_cp = consumer_client(admin_cp, random_string('testsystem'))

    # Consume that one entitlement:
    consumer1_cp.consume_pool(pool.id, {:quantity => 1}).size.should == 1

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
    @cp.create_subscription(owner['key'], product.id, 1)
    @cp.refresh_pools(owner['key'])
    pool = admin_cp.list_pools({:owner => owner.id})[0]

    consumer1_cp = consumer_client(admin_cp, random_string('testsystem'),
      :system, nil, {"uname.machine" => "X86_64"})

    pools = consumer1_cp.list_pools({:consumer => consumer1_cp.uuid,
      :listall => true})

    # Should see the pool despite rules warning because we used listall:
    pools.size.should == 1
  end

  it 'can delete pools' do
    owner1 = create_owner random_string('test_owner')
    owner1_client = user_client(owner1, random_string('testuser'))

    product = create_product
    @cp.create_subscription(owner1['key'], product.id, 10)
    @cp.refresh_pools(owner1['key'])

    pool = owner1_client.list_pools(:owner => owner1.id).first

    consumer1_cp = consumer_client(owner1_client, random_string('testsystem'))
    ent = consumer1_cp.consume_pool(pool['id'], {:quantity => 1}).first

    # Org admin should not be able to do this:
    lambda {
      owner1_client.delete_pool(pool['id'])
    }.should raise_exception(RestClient::Forbidden)

    # Super admin can:
    @cp.delete_pool(pool['id'])

    lambda {
      @cp.get_pool(pool['id'])
    }.should raise_exception(RestClient::ResourceNotFound)

    # Entitlement should be gone:
    lambda {
      consumer1_cp.get_entitlement(ent['id'])
    }.should raise_exception(RestClient::ResourceNotFound)

  end

  it 'should return calculated attributes' do
    owner = create_owner random_string('test_owner')
    product = create_product(nil, random_string('some_product'))

    @cp.create_subscription(owner['key'], product.id, 25)
    @cp.refresh_pools(owner['key'])
    pools = @cp.list_pools
    pool = pools.select { |p| p['owner']['key'] == owner['key'] }.first

    user = user_client(owner, random_string('billy'))
    system = consumer_client(user, 'system')

    pool = @cp.get_pool(pool.id, system.uuid)
    pool['calculatedAttributes']['suggested_quantity'].should == "1"
    pool['calculatedAttributes']['compliance_type'].should == "Standard"
  end

end
