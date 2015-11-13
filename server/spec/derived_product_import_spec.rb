require 'spec_helper'
require 'candlepin_scenarios'
require 'json'

describe 'Import', :serial => true do

  include CandlepinMethods
  include VirtHelper

  before(:all) do
    @cp = Candlepin.new('admin', 'admin')
    pending("candlepin running in hosted mode") if is_hosted?
    @owners = []
    @owner = @cp.create_owner(random_string('owner'))
    @user = user_client(@owner, random_string('user'))
    @dist_owner = create_owner(random_string('owner'))
    @dist_user = user_client(@dist_owner, random_string('user'))
    @owners = [@owner, @dist_owner]
    @exporter = Export.new
    @exporters = [@exporter]
  end

  after(:all) do
    @owners.each do |o|
      @cp.delete_owner(o['key'])
    end
    @exporters.each do |e|
      e.cleanup()
    end
  end

  it 'can retrieve subscripton certificate from derived pool entitlement' do
    stacked_datacenter_product = create_product(nil, nil, {
      :attributes => {
        :virt_limit => "unlimited",
        :stacking_id => 'mixed-stack',
        :sockets => "2",
        'multi-entitlement' => "yes"
      }
    })
    derived_product = create_product(nil, nil, {
      :attributes => {
          :cores => '6',
          :sockets=>'8'
      }
    })
    datacenter_pool = create_pool_and_subscription(@owner['key'], stacked_datacenter_product.id,
      10, [], '222', '', '', nil, nil, false,
      {
        :derived_product_id => derived_product.id
      })
    @cp.refresh_pools(@owner['key'])
    pool = @cp.list_owner_pools(@owner['key'], {:product => stacked_datacenter_product.id})[0]

    # create the distributor consumer
    consumer = @user.register(random_string("consumer"), :candlepin, nil, {
      'distributor_version' => 'sam-1.3'
    })
    consumer_client = Candlepin.new(nil, nil, consumer['idCert']['cert'], consumer['idCert']['key'])
    # entitlements from data center pool
    consumer_client.consume_pool(pool['id'], {:quantity => 5})[0]

    # make manifest
    @exporter.export_filename = consumer_client.export_consumer(@exporter.tmp_dir)
    @exporter.extract()

    # remove client at 'host'
    consumer_client.unregister consumer_client.uuid
    delete_pool_and_subscription(datacenter_pool)

    # import to make org at 'distributor'
    import_user_client = user_client(@dist_owner, random_string("user"))
    import_user_client.import(@dist_owner['key'], @exporter.export_filename)
    @cp.refresh_pools(@dist_owner['key'])
    pools = @cp.list_owner_pools(@dist_owner['key'], {:product => stacked_datacenter_product.id})
    pools.size.should >= 1

    # need to ignore the unmapped guest pool
    filter_unmapped_guest_pools(pools)
    dist_pool = pools[0]

    # make host client to get entitlement from distributor
    dist_consumer_1 = @dist_user.register(random_string("consumer"))
    dist_consumer_client_1 = Candlepin.new(nil, nil, dist_consumer_1['idCert']['cert'], dist_consumer_1['idCert']['key'])
    virt_uuid = random_string('system.uuid')
    guests = [{'guestId' => virt_uuid}]
    dist_consumer_client_1.update_consumer({:guestIds => guests})

    # spawn pool for derived product
    dist_consumer_client_1.consume_pool(dist_pool['id'])[0]

    # make guest client
    dist_consumer_2 = @dist_user.register(random_string("consumer"), :system, nil,
       {'virt.uuid' => virt_uuid, 'virt.is_guest' => 'true'})
    dist_consumer_client_2 = Candlepin.new(nil, nil, dist_consumer_2['idCert']['cert'], dist_consumer_2['idCert']['key'])

    # entitle from derived pool
    dist_ent = nil
    pools = @cp.list_owner_pools(@dist_owner['key'])
    # need to ignore the unmapped guest pool
    filter_unmapped_guest_pools(pools)

    pools.each do |p|
      p.attributes.each do |a|
        if a.name == 'pool_derived' and a.value == 'true'
            dist_ent = dist_consumer_client_2.consume_pool(p['id'])[0]
        end
      end
    end

    # use entitlement to get subsription cert back at master pool
    upstream_cert = @cp.get_subscription_cert_by_ent_id(dist_ent.id)
    upstream_cert[0..26].should == "-----BEGIN CERTIFICATE-----"

    # remove created subs
    @cp.list_subscriptions(@dist_owner['key']).each do |s|
        @cp.delete_subscription(s.id)
    end
  end
end
