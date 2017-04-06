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
      {'share.recipient' => @owner2['key']},
      nil,
      @owner1['key']
    )
  end

  it 'creates a share consumer type' do
    @user_client.register(
      random_string('orgBShare'),
      :share,
      nil,
      {'share.recipient' => @owner2['key']},
      nil,
      @owner1['key']
    )

    share = @cp.list_consumers(:owner => @owner1['key']).first
    expect(share['type']['label']).to eq('share')
    expect(share['idCert']).to be_nil
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
        {'share.recipient' => @owner2['key']},
        nil,
        @owner1['key'],
        [],
        installed
      )
    end.to raise_error(RestClient::BadRequest)
  end

  it 'cannot create a share consumer that is a guest' do
     expect do
       @user_client.register(
        random_string('orgBShare'),
        :share,
        nil,
	{'share.recipient' => @owner2['key'], 'virt.is_guest' => 'true'},
        nil,
        @owner1['key'],
        []
      )
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
        @owner1['key'],
        []
      )
    end.to raise_error(RestClient::BadRequest)
  end

  it 'cannot update recipient owner' do
     @user_client.register(
      random_string('orgBShare'),
      :share,
      nil,
      {'share.recipient' => @owner2['key']},
      nil,
      @owner1['key']
     )
     share = @cp.list_consumers(:owner => @owner1['key']).first
     owner3 = create_owner(random_string('owner'))
     facts = {'share.recipient' => owner3['key']}
     @cp.update_consumer({:uuid => share.uuid, :facts => facts})
  end

  it 'cannot create a share without permissions to the recipient org' do
    other_owner = create_owner(random_string('orgB'))
    expect do
      @user_client.register(
        random_string('orgBShare'),
        :share,
        nil,
        {'share.recipient' => other_owner['key']},
        nil,
        @owner1['key']
      )
    end.to raise_error(RestClient::ResourceNotFound)
  end

  it 'cannot share to a bogus org' do
    expect do
      @user_client.register(
        random_string('orgBShare'),
        :share,
        nil,
        {'share.recipient' => 'bogus_org'},
        nil,
        @owner1['key']
      )
    end.to raise_error(RestClient::ResourceNotFound)
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
    expect(shared_pool.type).to eq('SHARE_DERIVED')
    #verify all products are copied
    expect(shared_pool.productId).to eq(pool.productId)
    expect(shared_pool.derivedProductId).to eq(pool.derivedProductId)
    expect(shared_pool.providedProducts.length).to eq(1)
    expect(shared_pool.providedProducts[0]['productId']).to eq(pp.id)
    expect(shared_pool.derivedProvidedProducts.length).to eq(1)
    expect(shared_pool.derivedProvidedProducts[0]['productId']).to eq(dpp.id)


    attributes = recipient_pools.first['attributes']
    expect(attributes.select { |a| a['name'] == 'share_derived' && a['value'] == 'true'}).not_to be_empty
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
    expect(shared_pool.type).to eq('SHARE_DERIVED')
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
    expect(shared_pool.type).to eq('SHARE_DERIVED')
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
    expect(shared_pool.type).to eq('SHARE_DERIVED')
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
    attributes = recipient_pools.first['attributes']
    expect(attributes.select { |a| a['name'] == 'share_derived' && a['value'] == 'true'}).not_to be_empty

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
      {'share.recipient' => @owner2['key']},
      nil,
      owner3['key']
    )

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
      {'share.recipient' => owner3['key']},
      nil,
      @owner2['key']
    )

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

end
