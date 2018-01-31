require 'spec_helper'
require 'candlepin_scenarios'
require 'rest_client'

describe "Multi Org Shares" do
  include CandlepinMethods

  before(:each) do
    @owner1 = create_owner(random_string('orgA'))
    @username = random_string('orgA_user')
    @user_client = user_client(@owner1, @username)

    @owner2 = create_owner(random_string('orgB'))
    role = create_role(nil, @owner2['key'], 'ALL')
    @cp.add_role_user(role['id'], @username)
  end

  let(:share_consumer) do
     @user_client.register(
      random_string('orgBShare'),
      :share,
      nil,
      {},
      nil,
      @owner1['key'],
      [],
      [],
      nil,
      [],
      nil,
      [],
      nil,
      nil,
      nil,
      @owner2['key']
    )
  end

  it 'creates a share consumer type' do
    @user_client.register(
      random_string('orgBShare'),
      :share,
      nil,
      {},
      nil,
      @owner1['key'],
      [],
      [],
      nil,
      [],
      nil,
      [],
      nil,
      nil,
      nil,
      @owner2['key']
    )

    share = @cp.list_consumers(:owner => @owner1['key']).first
    expect(share['type']['label']).to eq('share')
    expect(share['idCert']).to be_nil
  end

  it 'ensures unique sharing consumer between two owners' do
    share_consumer = @user_client.register(
      random_string('orgBShare'),
      :share,
      nil,
      {},
      nil,
      @owner1['key'],
      [],
      [],
      nil,
      [],
      nil,
      [],
      nil,
      nil,
      nil,
      @owner2['key']
    )
    share_consumer2 = @user_client.register(
      random_string('orgBShare'),
      :share,
      nil,
      {},
      nil,
      @owner1['key'],
      [],
      [],
      nil,
      [],
      nil,
      [],
      nil,
      nil,
      nil,
      @owner2['key']
    )
    share_consumer.should == share_consumer2
    share_consumer2['type']['label'].should == "share"
    share_consumer2['owner']['key'].should == @owner1['key']
    share_consumer2['recipientOwnerKey'].should == @owner2['key']
  end

  it 'cannot create a share consumer with installed products' do
     p_product1 = create_product("p_product_1",
        "Provided Product 1",
        :owner => @owner1['key'])
     installed = [{'productId' => p_product1.id, 'productName' => p_product1.name}]

     expect do
       @user_client.register(
        random_string('orgBShare'),
        :share,
        nil,
        {},
        nil,
        @owner1['key'],
        [],
        installed,
        nil,
        [],
        nil,
        [],
        nil,
        nil,
        nil,
        @owner2['key'])

    end.to raise_error(RestClient::BadRequest)
  end

  it 'cannot create a share consumer that is a guest' do
     expect do
       @user_client.register(
        random_string('orgBShare'),
        :share,
        nil,
        {'virt.is_guest' => 'true'},
        nil,
        @owner1['key'],
        [],
        [],
        nil,
        [],
        nil,
        [],
        nil,
        nil,
        nil,
        @owner2['key'])
    end.to raise_error(RestClient::BadRequest)
  end

  it 'cannot create a share consumer without share recipient' do
     expect do
       @user_client.register(
        random_string('orgBShare'),
        :share,
        nil,
        {},
        nil,
        @owner1['key'])
    end.to raise_error(RestClient::BadRequest)
  end

  it 'cannot update recipient owner' do
     @user_client.register(
       random_string('orgBShare'),
       :share,
       nil,
       {},
       nil,
       @owner1['key'],
       [],
       [],
       nil,
       [],
       nil,
       [],
       nil,
       nil,
       nil,
       @owner2['key'])
     share = @cp.list_consumers(:owner => @owner1['key']).first
     owner3 = create_owner(random_string('owner'))
     expect do
       @cp.update_consumer({:uuid => share.uuid, :recipientOwnerKey => owner3['key']})
     end.to raise_error(RestClient::BadRequest)
  end

  it 'cannot create a share without permissions to the recipient org' do
    other_owner = create_owner(random_string('orgB'))
    expect do
      @user_client.register(
        random_string('orgBShare'),
        :share,
        nil,
        {},
        nil,
        @owner1['key'],
        [],
        [],
        nil,
        [],
        nil,
        [],
        nil,
        nil,
        nil,
        other_owner['key'])
    end.to raise_error(RestClient::ResourceNotFound)
  end

  it 'cannot share to a bogus org' do
    expect do
      @user_client.register(
        random_string('orgBShare'),
        :share,
        nil,
        {},
        nil,
        @owner1['key'],
        [],
        [],
        nil,
        [],
        nil,
        [],
        nil,
        nil,
        nil,
        'bogus_org')
    end.to raise_error(RestClient::ResourceNotFound)
  end

  it 'survives refresh on both orgs' do
    name = random_string('name')
    id = random_string('id')
    create_product(id, name, :owner => @owner1['key'])
    pp = create_product(nil, nil, :owner => @owner1['key'])
    dp = create_product(nil, nil, :owner => @owner1['key'])
    dpp = create_product(nil, nil, :owner => @owner1['key'])
    pool = create_pool_and_subscription(@owner1['key'], id, 10, [pp.id],
        'blah', 'blah', 'blah', nil, nil, false, {:derived_product_id => dp.id, :derived_provided_products => [dpp.id]})
    ent = @user_client.consume_pool(pool['id'], :uuid => share_consumer['uuid'])[0]
    pool = @cp.get_pool(pool['id'])
    owner1_prod = @user_client.get_product(@owner1['key'], id)
    recipient_pools = @cp.list_owner_pools(@owner2['key'])
    shared_pool = recipient_pools[0]
    @cp.refresh_pools(@owner1['key'])
    @cp.refresh_pools(@owner2['key'])
    @cp.get_pool(shared_pool['id'])
  end

  it 'binds a pool to a share consumer' do
    name = random_string('name')
    id = random_string('id')

    # Do not store the product immediately.  We need to instead fetch it after a refresh_pools has run.  In
    # hosted mode, the refresh pools may result in product changes (since the product we are creating is
    # marked as "locked" initially whereas all products from the hosted adaptors are marked as "locked" as
    # soon as they are received).
    create_product(id, name, :owner => @owner1['key'])
    pp = create_product(nil, nil, :owner => @owner1['key'])
    dp = create_product(nil, nil, :owner => @owner1['key'])
    dpp = create_product(nil, nil, :owner => @owner1['key'])

    pool = create_pool_and_subscription(@owner1['key'], id, 10, [pp.id],
        'blah', 'blah', 'blah', nil, nil, false, {:derived_product_id => dp.id, :derived_provided_products => [dpp.id]})
    ent = @user_client.consume_pool(pool['id'], :uuid => share_consumer['uuid'])[0]
    expect(ent.certificates).to be_empty
    expect(@user_client.list_pool_entitlements(pool['id'])).not_to be_empty
    pool = @cp.get_pool(pool['id'])
    expect(pool.consumed).to eq(1)
    expect(pool.shared).to eq(1)

    owner1_prod = @user_client.get_product(@owner1['key'], id)

    recipient_pools = @cp.list_owner_pools(@owner2['key'])
    expect(recipient_pools.length).to eq(1)
    shared_pool = recipient_pools[0]
    expect(shared_pool.createdByShare).to_s.eql? "true"
    #verify type is maintained
    expect(shared_pool.type).to eq('NORMAL')
    #verify all products are copied
    expect(shared_pool.productId).to eq(pool.productId)
    expect(shared_pool.derivedProductId).to eq(pool.derivedProductId)
    expect(shared_pool.providedProducts.length).to eq(1)
    expect(shared_pool.providedProducts[0]['productId']).to eq(pp.id)
    expect(shared_pool.derivedProvidedProducts.length).to eq(1)
    expect(shared_pool.derivedProvidedProducts[0]['productId']).to eq(dpp.id)


    attributes = recipient_pools.first['attributes']
    expect(attributes.select { |a| a['name'] == 'pool_derived' && a['value'] == 'true'}).not_to be_empty

    expect(shared_pool.subscriptionId).to eq(pool.subscriptionId)
    expect(shared_pool.subscriptionSubKey).to eq(ent.id)

    shared_pools = @cp.list_owner_pools(@owner1['key'])
    expect(shared_pools.first['shared']).to eq(1)

    owner2_prod = @user_client.get_product(@owner2['key'], id)

    # Owner 2 didn't have the shared product defined so it should be linked in
    expect(owner2_prod['uuid']).to eq(owner1_prod['uuid'])
  end

  it 'does not allow to over share a pool' do
    prod = create_product(nil, nil, :owner => @owner1['key'],
        :attributes => {"multi-entitlement" => "yes"})
    pool = create_pool_and_subscription(@owner1['key'], prod.id, 10)
    ent = @user_client.consume_pool(pool['id'], :uuid => share_consumer['uuid'])[0]

    recipient_pools = @cp.list_owner_pools(@owner2['key'])
    expect(recipient_pools.length).to eq(1)
    shared_pool = recipient_pools[0]
    expect(shared_pool.createdByShare).to_s.eql? "true"
    expect(shared_pool.type).to eq('NORMAL')
    consumer = consumer_client(@user_client, random_string("consumer"), :system, 'user',
        facts = {}, @owner2['key'])
    expect do
      consumer.consume_pool(shared_pool['id'], {:quantity => 2})
    end.to raise_error(RestClient::Forbidden)
  end

  it 'adjusts shared pool quantity when source entitlement is updated' do
    prod = create_product(nil, nil, :owner => @owner1['key'],
        :attributes => {"multi-entitlement" => "yes"})
    pool = create_pool_and_subscription(@owner1['key'], prod.id, 10)
    ent = @user_client.consume_pool(pool['id'], :uuid => share_consumer['uuid'], :quantity => 2)[0]

    recipient_pools = @cp.list_owner_pools(@owner2['key'])
    expect(recipient_pools.length).to eq(1)
    shared_pool = recipient_pools[0]
    expect(shared_pool.createdByShare).to_s.eql? "true"
    expect(shared_pool.type).to eq('NORMAL')
    expect(shared_pool.quantity).to eq(2)
    @cp.update_entitlement({:id => ent.id, :quantity => 1})
    expect(@cp.get_pool(shared_pool['id']).quantity).to eq(1)
  end

  it 'deletes a shared pool when source entitlement is revoked' do
    prod = create_product(nil, nil, :owner => @owner1['key'],
        :attributes => {"multi-entitlement" => "yes"})
    pool = create_pool_and_subscription(@owner1['key'], prod.id, 10)
    ent = @user_client.consume_pool(pool['id'], :uuid => share_consumer['uuid'])[0]

    recipient_pools = @cp.list_owner_pools(@owner2['key'])
    expect(recipient_pools.length).to eq(1)
    shared_pool = recipient_pools[0]
    expect(shared_pool.createdByShare).to_s.eql? "true"
    expect(shared_pool.type).to eq('NORMAL')
    @cp.revoke_all_entitlements(share_consumer['uuid'])
    expect do
      puts @cp.get_pool(shared_pool['id'])
    end.to raise_error(RestClient::ResourceNotFound)
  end

  it 'should allow multi share even without multi-entitlement' do
    prod = create_product(nil, nil, :owner => @owner1['key'])
    pool = create_pool_and_subscription(@owner1['key'], prod.id, 10)
    ent = @user_client.consume_pool(pool['id'], :uuid => share_consumer['uuid'], :quantity => 3)[0]
    recipient_pools = @cp.list_owner_pools(@owner2['key'])
    expect(recipient_pools.length).to eq(1)
    shared_pool = recipient_pools[0]
    expect(shared_pool.quantity).to eq(3)
  end

  it 'uses an existing product in a share recipient' do
    name = random_string('name')
    id = random_string('id')

    # Create products with the same product ID but different attributes
    owner1_prod = create_product(id, name, :owner => @owner1['key'], :multiplier => 1)
    create_product(id, name, :owner => @owner2['key'], :multiplier => 2)

    pool = create_pool_and_subscription(@owner1['key'], owner1_prod['id'], 10)
    @user_client.consume_pool(pool['id'], :uuid => share_consumer['uuid'])

    expect(@user_client.list_pool_entitlements(pool['id'])).not_to be_empty
    recipient_pools = @cp.list_owner_pools(@owner2['key'])
    expect(recipient_pools).not_to be_empty
    expect(recipient_pools.first.createdByShare).to_s.eql? "true"
    # Owner 2 should continue to use its own version of the product
    owner2_prod = @user_client.get_product(@owner2['key'], id)
    expect(owner2_prod['id']).to eq(owner1_prod['id'])
    expect(owner2_prod['uuid']).not_to eq(owner1_prod['uuid'])
  end

  it 'replaces one product share with a newer product share' do
    name = random_string('name')
    id = random_string('id')

    owner3 = create_owner(random_string('orgC'))
    role = create_role(nil, owner3['key'], 'ALL')
    @cp.add_role_user(role['id'], @username)
    share_consumer2 =
     @user_client.register(
      random_string('orgCShare'),
      :share,
      nil,
      {},
      nil,
      owner3['key'],
      [],
      [],
      nil,
      [],
      nil,
      [],
      nil,
      nil,
      nil,
      @owner2['key'])

    # Create products with the same product ID but different attributes
    # Do not store the product immediately.  We need to instead fetch it after a refresh_pools has run.  In
    # hosted mode, the refresh pools may result in product changes (since the product we are creating is
    # marked as "locked" initially whereas all products from the hosted adaptors are marked as "locked" as
    # soon as they are received).
    create_product(id, name, :owner => @owner1['key'], :multiplier => 1)
    create_product(id, name, :owner => owner3['key'], :multiplier => 2)

    pool = create_pool_and_subscription(@owner1['key'], id, 10)
    @user_client.consume_pool(pool['id'], :uuid => share_consumer['uuid'])

    owner1_prod = @user_client.get_product(@owner1['key'], id)
    owner2_prod = @user_client.get_product(@owner2['key'], id)
    expect(owner2_prod['uuid']).to eq(owner1_prod['uuid'])

    pool = create_pool_and_subscription(owner3['key'], id, 10)
    owner3_prod = @user_client.get_product(owner3['key'], id)
    @user_client.consume_pool(pool['id'], :uuid => share_consumer2['uuid'])

    # Owner 2 should now use Owner 3's product
    owner2_prod = @user_client.get_product(@owner2['key'], id)
    expect(owner2_prod['uuid']).to eq(owner3_prod['uuid'])
  end

  it 'prohibits sharing a share' do
    owner1_prod = create_product(nil, nil, :owner => @owner1['key'])
    pool = create_pool_and_subscription(@owner1['key'], owner1_prod['id'], 10)
    @user_client.consume_pool(pool['id'], :uuid => share_consumer['uuid'])
    expect(@user_client.list_pool_entitlements(pool['id'])).not_to be_empty
    recipient_pools = @cp.list_owner_pools(@owner2['key'])
    expect(recipient_pools).not_to be_empty

    owner3 = create_owner(random_string('orgC'))
    role = create_role(nil, owner3['key'], 'ALL')
    @cp.add_role_user(role['id'], @username)
    consumer2 =
     @user_client.register(
      random_string('orgCShare'),
      :share,
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
      owner3['key'])

    expect do
      @user_client.consume_pool(recipient_pools.first['id'], :uuid => consumer2['uuid'])
    end.to raise_error(RestClient::Forbidden)
  end

  it 'prohibits sharing a dev pool' do
    skip("candlepin running in standalone mode") if not is_hosted?
    dev_product = create_product("dev_product", "Dev Product",
        {:attributes => { :expires_after => "2"},
        :owner => @owner1['key']})
    p_product1 = create_product("p_product_1",
       "Provided Product 1",
       :owner => @owner1['key'])
    consumer = consumer_client(@user_client, random_string("dev_consumer"), :system, 'dev_user',
        facts = {:dev_sku => "dev_product"}, @owner1['key'])
    installed = [{'productId' => p_product1.id, 'productName' => p_product1.name}]
    consumer.update_consumer({:installedProducts => installed})
    # active subscription to allow this all to work
    active_prod = create_product(nil, nil, {:owner => @owner1['key']})
    @active_sub = create_pool_and_subscription(@owner1['key'], active_prod.id, 10)
    consumer.consume_product()
    dev_pool = consumer.list_entitlements()[0]['pool']
    expect do
      @user_client.consume_pool(dev_pool['id'], :uuid => share_consumer['uuid'])
    end.to raise_error(RestClient::Forbidden)
  end

  it 'prohibits sharing an unmapped guest pool' do
    product = create_product(nil, nil,
        {:attributes => {:virt_limit => "4",
                         :host_limited => 'true'},
        :owner => @owner1['key']})
    create_pool_and_subscription(@owner1['key'], product.id, 10, [])
    all_pools = @cp.list_owner_pools(@owner1['key'])
    all_pools.size.should == 2
    unmapped_pool = all_pools.find {|p| p.type == 'UNMAPPED_GUEST'}
    expect do
      @user_client.consume_pool(unmapped_pool['id'], :uuid => share_consumer['uuid'])
    end.to raise_error(RestClient::Forbidden)
  end

  it 'does not list unsharable pools for share consumers' do
    product = create_product(nil, nil,
        {:attributes => {:virt_limit => "4", :host_limited => 'true'}, :owner => @owner1['key']})
    # Will create an unmapped guest pool
    create_pool_and_subscription(@owner1['key'], product.id, 10, [])

    owner_pools = @user_client.list_owner_pools(@owner1['key'])
    owner_pools.size.should == 2

    owner_pools = @user_client.list_owner_pools(@owner1['key'], :consumer => share_consumer['uuid'])
    owner_pools.size.should == 1
  end

  it 'lists pools for share correctly based on quantity' do
    prod = create_product(nil, nil, :owner => @owner1['key'])
    pool = create_pool_and_subscription(@owner1['key'], prod.id, 10)
    pools_length = @user_client.list_owner_pools(@owner1['key'], :consumer => share_consumer['uuid']).length
    expect(pools_length).to eq 1

    @user_client.consume_pool(pool['id'], :uuid => share_consumer['uuid'], :quantity => 5)[0]
    pools_length =  @user_client.list_owner_pools(@owner1['key'], :consumer => share_consumer['uuid']).length
    expect(pools_length).to eq 1

    @user_client.consume_pool(pool['id'], :uuid => share_consumer['uuid'], :quantity => 5)[0]
    pools_length =  @user_client.list_owner_pools(@owner1['key'], :consumer => share_consumer['uuid']).length
    expect(pools_length).to eq 0
  end

  it 'share consumer gets removed when recipient org is removed' do
    @user_client.register(
      random_string('orgBShare'),
      :share,
      nil,
      {},
      nil,
      @owner1['key'],
      [],
      [],
      nil,
      [],
      nil,
      [],
      nil,
      nil,
      nil,
      @owner2['key']
    )

    share = @cp.list_consumers(:owner => @owner1['key']).first
    delete_owner(@owner2)
    @cp.list_consumers(:owner => @owner1['key']).size.should == 0
  end

  it 'cannot delete org when share consumer exists' do
    @user_client.register(
      random_string('orgBShare'),
      :share,
      nil,
      {},
      nil,
      @owner1['key'],
      [],
      [],
      nil,
      [],
      nil,
      [],
      nil,
      nil,
      nil,
      @owner2['key']
    )

    expect do
      delete_owner(@owner1)
    end.to raise_error(RestClient::BadRequest)
  end

  it 'can delete org when share consumer exists with force' do
    @user_client.register(
      random_string('orgBShare'),
      :share,
      nil,
      {},
      nil,
      @owner1['key'],
      [],
      [],
      nil,
      [],
      nil,
      [],
      nil,
      nil,
      nil,
      @owner2['key']
    )
    delete_owner(@owner1, true, true)
  end

  it 'reduces share pool quantity before revoking share entitlements' do
    name = random_string('name')
    id = random_string('id')
    create_product(id, name, :owner => @owner1['key'])

    pool = create_pool_and_subscription(@owner1['key'], id, 10)
    ent = @user_client.consume_pool(pool['id'], {:uuid => share_consumer['uuid'], :quantity => 5})[0]
    expect(ent.certificates).to be_empty
    expect(@user_client.list_pool_entitlements(pool['id'])).not_to be_empty
    pool = @cp.get_pool(pool['id'])
    expect(pool.consumed).to eq(5)
    expect(pool.shared).to eq(5)

    owner1_prod = @user_client.get_product(@owner1['key'], id)

    recipient_pools = @cp.list_owner_pools(@owner2['key'])
    expect(recipient_pools.length).to eq(1)
    shared_pool = recipient_pools[0]
    expect(shared_pool.quantity).to eq(5)
    expect(shared_pool.createdByShare).to_s.eql? "true"

    consumer1 = @user_client.register(
       random_string('consumer'), :system, nil, {}, nil, @owner2['key'])
    @user_client.consume_pool(shared_pool['id'], {:uuid => consumer1['uuid'], :quantity => 1})

    pool.quantity = 5
    @cp.update_pool(@owner1['key'], pool)
    pool = @cp.get_pool(pool['id'])
    expect(pool.quantity).to eq(5)
    expect(pool.shared).to eq(5)
    expect(pool.consumed).to eq(5)
    shared_pool = @cp.get_pool(shared_pool['id'])
    expect(shared_pool.quantity).to eq(5)
    pool.quantity = 3
    @cp.update_pool(@owner1['key'], pool)
    pool = @cp.get_pool(pool['id'])
    expect(pool.shared).to eq(3)
    expect(pool.consumed).to eq(3)
    shared_pool = @cp.get_pool(shared_pool['id'])
    expect(shared_pool.quantity).to eq(3)
    expect(@user_client.list_pool_entitlements(shared_pool['id'])).not_to be_empty
  end

  it 'reduces share pool quantity and revokes share entitlements' do
    name = random_string('name')
    id = random_string('id')
    create_product(id, name, :owner => @owner1['key'])

    pool = create_pool_and_subscription(@owner1['key'], id, 10)
    ent = @user_client.consume_pool(pool['id'], {:uuid => share_consumer['uuid'], :quantity => 5})[0]
    expect(ent.certificates).to be_empty
    expect(@user_client.list_pool_entitlements(pool['id'])).not_to be_empty
    pool = @cp.get_pool(pool['id'])
    expect(pool.consumed).to eq(5)
    expect(pool.shared).to eq(5)

    owner1_prod = @user_client.get_product(@owner1['key'], id)

    recipient_pools = @cp.list_owner_pools(@owner2['key'])
    expect(recipient_pools.length).to eq(1)
    shared_pool = recipient_pools[0]
    expect(shared_pool.quantity).to eq(5)
    expect(shared_pool.createdByShare).to_s.eql? "true"

    consumer1 = @user_client.register(
       random_string('consumer'), :system, nil, {}, nil, @owner2['key'])
    @user_client.consume_pool(shared_pool['id'], {:uuid => consumer1['uuid'], :quantity => 1})
    sleep 1
    consumer2 = @user_client.register(
       random_string('consumer'), :system, nil, {}, nil, @owner2['key'])
    @user_client.consume_pool(shared_pool['id'], {:uuid => consumer2['uuid'], :quantity => 1})

    pool.quantity = 1
    @cp.update_pool(@owner1['key'], pool)
    pool = @cp.get_pool(pool['id'])
    expect(pool.shared).to eq(1)
    expect(pool.consumed).to eq(1)
    shared_pool = @cp.get_pool(shared_pool['id'])
    expect(shared_pool.quantity).to eq(1)
    expect(@user_client.list_pool_entitlements(shared_pool['id'])).not_to be_empty
    expect(@user_client.list_pool_entitlements(shared_pool['id']).size).to eq(1)
    expect(@user_client.list_pool_entitlements(shared_pool['id'])[0].consumer['uuid']).to eq(consumer1['uuid'])
    expect(@cp.get_pool(pool['id']).quantity).to eq(1)
  end

  it 'revokes share entitlements by creation across master and shared pools' do
    name = random_string('name')
    id = random_string('id')
    create_product(id, name, :owner => @owner1['key'])

    pool = create_pool_and_subscription(@owner1['key'], id, 10)
    ent = @user_client.consume_pool(pool['id'], {:uuid => share_consumer['uuid'], :quantity => 5})[0]
    expect(ent.certificates).to be_empty
    expect(@user_client.list_pool_entitlements(pool['id'])).not_to be_empty
    pool = @cp.get_pool(pool['id'])
    expect(pool.consumed).to eq(5)
    expect(pool.shared).to eq(5)

    owner1_prod = @user_client.get_product(@owner1['key'], id)

    recipient_pools = @cp.list_owner_pools(@owner2['key'])
    expect(recipient_pools.length).to eq(1)
    shared_pool = recipient_pools[0]
    expect(shared_pool.quantity).to eq(5)
    expect(shared_pool.createdByShare).to_s.eql? "true"

    consumer1 = @user_client.register(
       random_string('consumer'), :system, nil, {}, nil, @owner2['key'])
    @user_client.consume_pool(shared_pool['id'], {:uuid => consumer1['uuid'], :quantity => 1})
    sleep 1
    consumer2 = @user_client.register(
       random_string('consumer'), :system, nil, {}, nil, @owner1['key'])
    @user_client.consume_pool(pool['id'], {:uuid => consumer2['uuid'], :quantity => 1})
    sleep 1
    consumer3 = @user_client.register(
       random_string('consumer'), :system, nil, {}, nil, @owner2['key'])
    @user_client.consume_pool(shared_pool['id'], {:uuid => consumer3['uuid'], :quantity => 1})

    pool.quantity = 1
    @cp.update_pool(@owner1['key'], pool)
    pool = @cp.get_pool(pool['id'])
    expect(pool.shared).to eq(1)
    expect(pool.consumed).to eq(1)
    shared_pool = @cp.get_pool(shared_pool['id'])
    expect(shared_pool.quantity).to eq(1)
    expect(@user_client.list_pool_entitlements(shared_pool['id'])).not_to be_empty
    expect(@user_client.list_pool_entitlements(shared_pool['id']).size).to eq(1)
    expect(@user_client.list_pool_entitlements(shared_pool['id'])[0].consumer['uuid']).to eq(consumer1['uuid'])
    expect(@cp.get_pool(pool['id']).quantity).to eq(1)
    expect(@user_client.list_pool_entitlements(pool['id']).size).to eq(1)
  end

  it 'uses a host-restricted pool from the sharing org' do
    skip("candlepin running in hosted mode") if is_hosted?
    owner_key = random_string('orgA')
    create_owner( owner_key)

    virt_product = create_product(nil, nil, {
      :owner => owner_key,
      :attributes => {:virt_limit => 3}
    })
    pool = create_pool_and_subscription(owner_key, virt_product['id'], 10)

    # create host consumer and consume the virt product
    host = random_string('host')
    orgAHost = @cp.register(
      host,
      :system,
      nil,
      {},
      nil,
      owner_key,
      [],
      [])

    @cp.consume_pool(pool['id'], :quantity => 1, :uuid => orgAHost['uuid'])
    guest_pool = @cp.list_owner_pools(owner_key).select do |p|
      p['type'] == "ENTITLEMENT_DERIVED"
    end.first

    # share guest pool with Org B
    orgB = random_string('orgB')
    create_owner(orgB)
    share_consumer = @cp.register(
      random_string('orgBShare'),
      :share,
      nil,
      {},
      nil,
      owner_key,
      [],
      [],
      nil,
      [],
      nil,
      [],
      nil,
      nil,
      nil,
      orgB
    )
    @cp.consume_pool(guest_pool['id'], :uuid => share_consumer['uuid'])

    virt_uuid = random_string('system.uuid')
    orgBconsumer = @cp.register(
      random_string('orgBConsumer'),
      :system,
      nil,
      {'virt.uuid' => virt_uuid, 'virt.is_guest' => 'true'},
      'admin',
      orgB,
      [],
      [{"productId" => virt_product['id'], "productName" => virt_product['name']}]
    )

    # Link the host and the guest
    @cp.update_consumer(:uuid => orgAHost['uuid'], :guestIds => [{'guestId' => virt_uuid}])

    ent = @cp.consume_product(virt_product['id'], :uuid => orgBconsumer['uuid'])
    expect(ent).to_not be_empty
  end

  it 'does not force a heal in the sharing organization' do
    skip("candlepin running in hosted mode") if is_hosted?
    owner_key = random_string('orgA')
    create_owner( owner_key)

    virt_product = create_product(nil, nil, {
      :owner => owner_key,
      :attributes => {:virt_limit => 3}
    })
    pool = create_pool_and_subscription(owner_key, virt_product['id'], 10)

    other_product = create_product(nil, nil, :owner => owner_key)
    other_pool = create_pool_and_subscription(owner_key, other_product['id'], 10)

    # create host consumer and consume the virt product
    host = random_string('host')
    orgAHost = @cp.register(
      host,
      :system,
      nil,
      {},
      nil,
      owner_key,
      [],
      [
        {"productId" => virt_product['id'], "productName" => virt_product['name']},
        {"productId" => other_product['id'], "productName" => other_product['name']}
      ])

    @cp.consume_product(virt_product['id'], :uuid => orgAHost['uuid'])

    expect(@cp.list_pool_entitlements(other_pool['id'])).to be_empty
    expect(@cp.list_pool_entitlements(pool['id'])).to_not be_empty

    # @cp.unbind_entitlements_by_pool(orgAHost['uuid'], other_pool['id'])
    # expect(@cp.list_pool_entitlements(other_pool['id'])).to be_empty

    guest_pool = @cp.list_owner_pools(owner_key).select do |p|
      p['type'] == "ENTITLEMENT_DERIVED"
    end.first

    # share guest pool with Org B
    orgB = random_string('orgB')
    create_owner(orgB)
    share_consumer = @cp.register(
      random_string('orgBShare'),
      :share,
      nil,
      {},
      nil,
      owner_key,
      [],
      [],
      nil,
      [],
      nil,
      [],
      nil,
      nil,
      nil,
      orgB
    )
    @cp.consume_pool(guest_pool['id'], :uuid => share_consumer['uuid'])

    virt_uuid = random_string('system.uuid')
    orgBconsumer = @cp.register(
      random_string('orgBConsumer'),
      :system,
      nil,
      {'virt.uuid' => virt_uuid, 'virt.is_guest' => 'true'},
      'admin',
      orgB,
      [],
      [{"productId" => virt_product['id'], "productName" => virt_product['name']}]
    )

    # Link the host and the guest
    @cp.update_consumer(:uuid => orgAHost['uuid'], :guestIds => [{'guestId' => virt_uuid}])

    ent = @cp.consume_product(nil, :uuid => orgBconsumer['uuid'])
    expect(ent).to_not be_empty
    expect(@cp.list_pool_entitlements(other_pool['id'])).to be_empty
  end
end
