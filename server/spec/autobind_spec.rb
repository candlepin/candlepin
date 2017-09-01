require 'spec_helper'
require 'candlepin_scenarios'

describe 'Autobind On Owner' do
  include CandlepinMethods

  let!(:owner_key) do
    random_string('test_owner')
  end

  let!(:owner) do
    create_owner( owner_key)
  end

  it 'succeeds when requesting bind of multiple pools with same stack id' do
    # create 4 products with the same stack id and sockets.
    prod = create_product('taylorid', 'taylor swift', {
      :owner => owner_key,
      :version => "6.1",
      :attributes => {
        :stacking_id => "ouch",
        "sockets" => "2",
        "vcpu" => "4",
        "warning_period" => "30",
        "brand_type" => "OS",
      }
    })

    prod1 = create_product(nil, nil, {
      :owner => owner_key,
      :attributes => {
        :stacking_id => "ouch",
        "virt_limit" => 1,
        "sockets" => 1,
        "instance_multiplier" => 1,
        "multi-entitlement" => "yes",
        "host_limited" => "true"
      }
    })

    prod2 = create_product(nil, nil, {
      :owner => owner_key,
      :attributes => {
        :stacking_id => "ouch",
        "virt_limit" => 1,
        "sockets" => 1,
        "instance_multiplier" => 1,
        "multi-entitlement" => "yes",
        "host_limited" => "true"
      }
    })

    prod3 = create_product(nil, nil, {
      :owner => owner_key,
      :attributes => {
        :stacking_id => "ouch",
        "virt_limit" => 1,
        "sockets" => 1,
        "instance_multiplier" => 1,
        "multi-entitlement" => "yes",
        "host_limited" => "true"
      }
    })

    # create 4 pools, all must provide product "prod" . none of them
    # should provide enough sockets to heal the host on it's own
    create_pool_and_subscription(owner['key'], prod['id'], 10)['id']
    create_pool_and_subscription(owner['key'], prod1['id'], 30, [prod['id']])['id']
    create_pool_and_subscription(owner['key'], prod2['id'], 30, [prod['id']])['id']
    create_pool_and_subscription(owner['key'], prod3['id'], 30, [prod['id']])['id']

    # create a guest with "prod" as an installed product
    guest_uuid =  random_string('guest')
    guest_facts = {
      "virt.is_guest"=>"true",
      "virt.uuid"=>"myGuestId",
      "cpu.cpu_socket(s)"=>"1",
      "virt.host_type"=>"kvm",
      "system.certificate_version"=>"3.2"
    }

    guest = @cp.register('guest.bind.com',:system, guest_uuid, guest_facts, 'admin',
      owner_key, [], [{"productId" => prod.id, "productName" => "taylor swift"}])

    # create a hypervisor that needs 40 sockets and report the guest with it
    hypervisor_facts = {
      "virt.is_guest"=>"false",
      "cpu.cpu(s)"=>"4",
      "cpu.cpu_socket(s)"=>"40"
    }

    hypervisor_guests = [{"guestId"=>"myGuestId"}]
    hypervisor_uuid = random_string("hypervisor")
    hypervisor = @cp.register('hypervisor.bind.com',:system, hypervisor_uuid, hypervisor_facts, 'admin',
      owner_key, [], [], nil, [], random_string('hypervisorid'))
    hypervisor = @cp.update_consumer({:uuid => hypervisor.uuid, :guestIds => hypervisor_guests})

    @cp.list_owner_pools(owner_key).length.should == 7

    @cp.consume_product(nil, {:uuid => guest_uuid})

    @cp.list_owner_pools(owner_key).length.should == 8

    # heal should succeed, and hypervisor should consume 2 pools of 30 sockets each
    @cp.list_entitlements({:uuid => hypervisor_uuid}).length.should == 2
    @cp.list_entitlements({:uuid => guest_uuid}).length.should == 1

    @cp.revoke_all_entitlements(hypervisor_uuid)
    @cp.revoke_all_entitlements(guest_uuid)

    # change the hypervisor to 70 sockets
    hypervisor_facts = {
      "virt.is_guest"=>"false",
      "cpu.cpu(s)"=>"4",
      "cpu.cpu_socket(s)"=>"70"
    }

    # heal should succeed, and hypervisor should consume 3 pools of 30 sockets each
    @cp.update_consumer({:uuid => hypervisor_uuid, :facts => hypervisor_facts})

    @cp.consume_product(nil, {:uuid => guest_uuid})

    @cp.list_entitlements({:uuid => hypervisor_uuid}).length.should == 3
    @cp.list_entitlements({:uuid => guest_uuid}).length.should == 1
    @cp.list_owner_pools(owner_key).length.should == 8
  end

  it 'favors non-shared pools' do
    skip("sharing is disabled in this branch")
    prod = create_product(random_string('prod'), random_string('prod'), {
      :owner => owner_key,
      :attributes => {}
    })

    orgB = random_string('orgB')
    create_owner(orgB)

    shared_pool = create_pool_and_subscription(owner_key, prod['id'])
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

    # Create the shared pool in Org B first since all else being equal Candlepin
    # will pick an earlier expiring pool in autobind.
    @cp.consume_pool(shared_pool['id'], :uuid => share_consumer['uuid'])

    orgBconsumer = @cp.register(
      random_string('orgBConsumer'),
      :system,
      nil,
      {},
      'admin',
      orgB
    )

    # OrgB now has a shared pool and an unshared pool for prod
    pool = create_pool_and_subscription(orgB, prod['id'])
    ent = @cp.consume_product(prod['id'], :uuid => orgBconsumer['uuid'])

    expect(ent.first['pool']['id']).to eq(pool['id'])
  end
end
