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

  let(:consumer1) do
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
    owner1_prod = create_product(nil, nil, :owner => @owner1['key'])
    pool = create_pool_and_subscription(@owner1['key'], owner1_prod['id'], 10)
    @user_client.consume_pool(pool['id'], :uuid => consumer1['uuid'])
    expect(@user_client.list_pool_entitlements(pool['id'])).not_to be_empty
    recipient_pools = @cp.list_owner_pools(@owner2['key'])
    expect(recipient_pools).not_to be_empty
    attributes = recipient_pools.first['attributes']
    expect(attributes.select { |a| a['name'] == 'share' && a['value'] == 'true'}).not_to be_empty
  end
end
