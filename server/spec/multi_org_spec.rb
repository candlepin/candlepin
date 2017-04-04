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
    pool = create_pool_and_subscription(@owner1['key'], id, 10)
    @user_client.consume_pool(pool['id'], :uuid => share_consumer['uuid'])
    expect(@user_client.list_pool_entitlements(pool['id'])).not_to be_empty

    owner1_prod = @user_client.get_product(@owner1['key'], id)

    recipient_pools = @cp.list_owner_pools(@owner2['key'])
    expect(recipient_pools).not_to be_empty
    attributes = recipient_pools.first['attributes']
    expect(attributes.select { |a| a['name'] == 'share_derived' && a['value'] == 'true'}).not_to be_empty

    shared_pools = @cp.list_owner_pools(@owner1['key'])
    expect(shared_pools.first['shared']).to eq(1)

    owner2_prod = @user_client.get_product(@owner2['key'], id)

    # Owner 2 didn't have the shared product defined so it should be linked in
    expect(owner2_prod['uuid']).to eq(owner1_prod['uuid'])
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
end
