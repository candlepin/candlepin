require 'candlepin_scenarios'

describe 'Pool Resource' do

  include CandlepinMethods
  it_should_behave_like 'Candlepin Scenarios'

  it 'lets consumers view pools' do
    owner1 = create_owner random_string('test_owner')
    owner1_client = user_client(owner1, random_string('testuser'))

    product = create_product()
    pool = @cp.create_pool(product.id, owner1.id, 10)

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
    pool = @cp.create_pool(product.id, owner2.id, 10)

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
    pool = @cp.create_pool(product.id, owner2.id, 10)

    lambda {
      owner1_client.get_pool(pool.id)
    }.should raise_exception(RestClient::Forbidden)
  end

  it 'supports pool deletion' do
    owner1 = create_owner random_string('test_owner')
    owner1_client = user_client(owner1, random_string('testuser'))
    product = create_product()
    pool = @cp.create_pool(product.id, owner1.id, 10)
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


end
