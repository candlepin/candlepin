require 'spec_helper'
require 'candlepin_scenarios'

# This spec tests virt limited products in a standalone Candlepin deployment.
# (which we assume to be testing against)
describe 'Unmapped Guest Pools' do
  include CandlepinMethods
  include VirtHelper
  include AttributeHelper

  before(:each) do
    @owner = create_owner random_string('owner')
    @user = user_client(@owner, random_string('user'))

    @virt_limit_product = create_product(nil, nil, {
      :attributes => {
        :virt_limit => 'unlimited',
        :host_limited => 'true',
        :physical_only => 'true',
        :'multi-entitlement' => 'yes'
      }
    })

    @cp.create_pool(@owner['key'], @virt_limit_product.id, {
      :quantity => 10,
      :subscription_id => random_string('source_sub'),
      :upstream_pool_id => random_string('upstream')
    })

    # should only be the base pool and the bonus pool for unmapped guests
    @pools = @user.list_pools :owner => @owner.id, :product => @virt_limit_product.id
    @pools.size.should == 2

    # we'll need a guest for this
    @uuid1 = random_string('system.uuid')
    @guest1 = @user.register(random_string('guest'), :system, nil,
      {'virt.uuid' => @uuid1, 'virt.is_guest' => 'true', 'uname.machine' => 'x86_64'}, nil, nil, [], [])
    @guest1_client = Candlepin.new(nil, nil, @guest1['idCert']['cert'], @guest1['idCert']['key'])
    installed = [
        {'productId' => @virt_limit_product.id, 'productName' => @virt_limit_product.name}
    ]
    @guest1_client.update_consumer({:installedProducts => installed})
    @host1 = @user.register(random_string('host'), :system)
    @host1_client = Candlepin.new(nil, nil, @host1['idCert']['cert'], @host1['idCert']['key'])
    @host2 = @user.register(random_string('host'), :system)
    @host2_client = Candlepin.new(nil, nil, @host2['idCert']['cert'], @host2['idCert']['key'])

  end

  it 'allows a new guest with no host to attach to an unmapped guest pool' do
    all_pools = @user.list_pools :owner => @owner.id, :product => @virt_limit_product.id
    all_pools.each do |pool|
      unmapped = get_attribute_value(pool['attributes'], 'unmapped_guests_only')
      if !unmapped.nil? && unmapped == 'true'
        @guest1_client.consume_pool(pool['id'], :quantity => 1)
      end
    end
    ents = @guest1_client.list_entitlements()
    ents.length.should eq(1)
  end

  it 'does not allow a new guest with a host to attach to an unmapped guest pool' do
    @host1_client.update_consumer({:guestIds => [{'guestId' => @uuid1}]});
    all_pools = @user.list_pools :owner => @owner.id, :product => @virt_limit_product.id
    all_pools.each do |pool|
      unmapped = get_attribute_value(pool['attributes'], 'unmapped_guests_only')
      if !unmapped.nil? && unmapped == 'true'
        lambda do
          @guest1_client.consume_pool(pool['id'], :quantity => 1)
        end.should raise_exception(RestClient::Forbidden)
      end
    end
  end

  it 'ensures unmapped guest will attach to unmapped guest pool on auto attach' do
    all_pools = @user.list_pools :owner => @owner.id, :product => @virt_limit_product.id
    all_pools.each do |pool|
      unmapped = get_attribute_value(pool['attributes'], 'unmapped_guests_only')
      if unmapped.nil? || unmapped != 'true'
        @host1_client.consume_pool(pool['id'], {:quantity => 1})
      end
    end

    # should be the base pool, the bonus pool for unmapped guests, plus a pool for the host's guests
    @pools = @user.list_pools :owner => @owner.id, :product => @virt_limit_product.id
    @pools.size.should == 3

    @guest1_client.autoheal_consumer
    ents = @guest1_client.list_entitlements()
    ents.length.should eq(1)

    bound_pool = ents[0].pool

    unmapped_guests_only = get_attribute_value(bound_pool['attributes'], "unmapped_guests_only")
    expect(unmapped_guests_only).not_to be_nil
  end

  it 'ensures unmapped guest will attach to unmapped guest pool on auto attach only once' do
    all_pools = @user.list_pools :owner => @owner.id, :product => @virt_limit_product.id
    all_pools.each do |pool|
      unmapped = get_attribute_value(pool['attributes'], 'unmapped_guests_only')
      if unmapped.nil? || unmapped != 'true'
        @host1_client.consume_pool(pool['id'], {:quantity => 1})
      end
    end

    # should be the base pool, the bonus pool for unmapped guests, plus a pool for the host's guests
    @pools = @user.list_pools :owner => @owner.id, :product => @virt_limit_product.id
    @pools.size.should == 3

    @guest1_client.autoheal_consumer
    ents = @guest1_client.list_entitlements()
    ents.length.should eq(1)

    @guest1_client.autoheal_consumer
    ents = @guest1_client.list_entitlements()
    ents.length.should eq(1)

    @guest1_client.autoheal_consumer
    ents = @guest1_client.list_entitlements()
    ents.length.should eq(1)
  end

  it 'unmapped guest entitlement does not have pool end date' do
    all_pools = @user.list_pools :owner => @owner.id, :product => @virt_limit_product.id
    unmapped_pool = nil
    all_pools.each do |pool|
      unmapped = get_attribute_value(pool['attributes'], 'unmapped_guests_only')
      if !unmapped.nil? && unmapped == 'true'
        unmapped_pool = pool
        @guest1_client.consume_pool(pool['id'], :quantity => 1)
      end
    end
    ents = @guest1_client.list_entitlements()
    ents.length.should eq(1)
    ents[0]['endDate'].should_not == unmapped_pool['endDate']
  end

  it 'revokes the unmapped guest pool once the guest is mapped' do
    all_pools = @user.list_pools :owner => @owner.id, :product => @virt_limit_product.id
    all_pools.each do |pool|
      unmapped = get_attribute_value(pool['attributes'], 'unmapped_guests_only')
      if !unmapped.nil? && unmapped == 'true'
        @guest1_client.consume_pool(pool['id'], :quantity => 1)
      end
    end
    ents = @guest1_client.list_entitlements()
    ents.length.should eq(1)
    original_ent_id = ents.first['id']

    @host1_client.update_consumer({:guestIds => [{'guestId' => @uuid1}]})
    ents = @guest1_client.list_entitlements()
    ents.length.should eq(1)
    autohealed_ent_id = ents.first['id']
    original_ent_id.should_not equal(autohealed_ent_id)
  end

  it 'hides unmapped guest pools from pool lists if instructed to' do
    all_pools = @user.list_owner_pools(@owner['displayName'], :product => @virt_limit_product.id)
    all_pools.each do |p|
      p['productId'].should eq(@virt_limit_product.id)
    end

    filtered_pools = @user.list_owner_pools(@owner['displayName'], {:product => @virt_limit_product.id},
      ["unmapped_guests_only:!true"])
    filtered_pools.length.should eq(all_pools.length - 1)

    filtered_pools.each do |p|
      p.should satisfy do |o|
        o['attributes'].select { |i| i['name'] == 'unmapped_guests_only' && i['value'] == 'true' }.empty?
      end
    end
  end

  it 'revokes entitlement from another host during an auto attach' do
    all_pools = @user.list_pools :owner => @owner.id, :product => @virt_limit_product.id
    @host1_client.update_consumer({:guestIds => [{'guestId' => @uuid1}]});

    all_pools.each do |pool|
      unmapped = get_attribute_value(pool['attributes'], 'unmapped_guests_only')
      if unmapped.nil? || unmapped != 'true'
        @host1_client.consume_pool(pool['id'], :quantity => 1)
        @host2_client.consume_pool(pool['id'], :quantity => 1)
      end
    end

    # should be the base pool, the bonus pool for unmapped guests, plus two pools for the hosts' guests
    @pools = @user.list_pools :owner => @owner.id, :product => @virt_limit_product.id
    @pools.size.should == 4

    @guest1_client.consume_product(@virt_limit_product.id)
    ents = @guest1_client.list_entitlements()
    ents.length.should eq(1)

    bound_pool = ents[0].pool
    requires_host = get_attribute_value(bound_pool['attributes'], "requires_host")
    expect(requires_host).to eq(@host1_client.uuid)

    # should not remove until attached to new host
    @host1_client.update_consumer({:guestIds => []});
    ents = @guest1_client.list_entitlements()
    ents.length.should eq(1)

    # now migrate the guest
    @host2_client.update_consumer({:guestIds => [{'guestId' => @uuid1}]});

    @guest1_client.consume_product(@virt_limit_product.id)
    ents = @guest1_client.list_entitlements()
    ents.length.should eq(1)

    bound_pool = ents[0].pool
    requires_host = get_attribute_value(bound_pool['attributes'], "requires_host")
    expect(requires_host).to eq(@host2_client.uuid)
  end

  it 'compliance status for entitled unmapped guest will be partial' do
    all_pools = @user.list_pools :owner => @owner.id, :product => @virt_limit_product.id
    all_pools.each do |pool|
      unmapped = get_attribute_value(pool['attributes'], 'unmapped_guests_only')
      if !unmapped.nil? && unmapped == 'true'
        @guest1_client.consume_pool(pool['id'], :quantity => 1)
      end
    end
    ents = @guest1_client.list_entitlements()
    ents.length.should eq(1)

    compliance_status = @guest1_client.get_compliance()
    compliance_status['status'].should == 'partial'
    compliance_status['compliant'].should == false
    compliance_status.should have_key('reasons')
    compliance_status['reasons'].size.should == 1
  end


  it 'compliance status for entitled unmapped guest will be partial without installed product' do
    @guest1_client.update_consumer({:installedProducts => []})
    all_pools = @user.list_pools :owner => @owner.id, :product => @virt_limit_product.id
    all_pools.each do |pool|
      unmapped = get_attribute_value(pool['attributes'], 'unmapped_guests_only')
      if !unmapped.nil? && unmapped == 'true'
        @guest1_client.consume_pool(pool['id'], :quantity => 1)
      end
    end
    ents = @guest1_client.list_entitlements()
    ents.length.should eq(1)

    compliance_status = @guest1_client.get_compliance()
    compliance_status['status'].should == 'partial'
    compliance_status['compliant'].should == false
    compliance_status.should have_key('reasons')
    compliance_status['reasons'].size.should == 1
  end
end
