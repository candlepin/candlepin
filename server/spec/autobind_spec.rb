require 'spec_helper'
require 'candlepin_scenarios'

describe 'Autobind On Owner' do
  include CandlepinMethods

  let!(:owner_key) do
    random_string('test_owner')
  end

  let!(:owner) do
    create_owner(owner_key)
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

  it 'should attach to addon pool when product is not installed' do
    product1 = create_product(random_string('product'),
                              random_string('product'),
                              {:attributes => {:addons => "addon1"},
                               :owner => owner_key})
    p1 = create_pool_and_subscription(owner_key, product1.id)
    product2 = create_product(random_string('product'),
                              random_string('product'),
                              {:owner => owner_key})
    p2 = create_pool_and_subscription(owner_key, product2.id)

    installed = [
        {'productId' => product2.id, 'productName' => product2['name']}]

    consumer = @cp.register(
        random_string('systempurpose'), :system, nil, {}, nil, owner_key, [], installed, nil, [],
        nil, [], nil, nil, nil, nil, nil, 0, nil, nil, nil, nil, ['addon1'])
    status = @cp.get_purpose_compliance(consumer['uuid'])
    status['status'].should == 'mismatched'
    status['nonCompliantAddOns'].include?('addon1').should == true

    @cp.consume_product(nil, {:uuid => consumer.uuid})
    entitlements = @cp.list_entitlements(:uuid => consumer.uuid)
    entitlements.size.should == 2
    # print ("entitlements: " + entitlements.inspect())
    status = @cp.get_purpose_compliance(consumer.uuid)
    status['status'].should == 'matched'
    status['nonCompliantAddOns'].size.should == 0
    status['compliantAddOns']['addon1'][0]['pool']['id'].should == p1.id
  end

  it 'should attach to role pool when product is not installed' do
    product1 = create_product(random_string('product'),
                              random_string('product'),
                              {:attributes => {:roles => "role1"},
                               :owner => owner_key})
    p1 = create_pool_and_subscription(owner_key, product1.id)
    product2 = create_product(random_string('product'),
                              random_string('product'),
                              {:owner => owner_key})
    p2 = create_pool_and_subscription(owner_key, product2.id)

    installed = [
        {'productId' => product2.id, 'productName' => product2['name']}]

    consumer = @cp.register(
        random_string('systempurpose'), :system, nil, {}, nil, owner_key, [], installed, nil, [],
        nil, [], nil, nil, nil, nil, nil, 0, nil, nil, 'role1', nil, [])
    status = @cp.get_purpose_compliance(consumer['uuid'])
    status['status'].should == 'mismatched'
    status['nonCompliantRole'].include?('role1').should == true

    @cp.consume_product(nil, {:uuid => consumer.uuid})
    entitlements = @cp.list_entitlements(:uuid => consumer.uuid)
    entitlements.size.should == 2
    # print ("entitlements: " + entitlements.inspect())
    status = @cp.get_purpose_compliance(consumer.uuid)
    status['status'].should == 'matched'
    status['nonCompliantRole'].should be_nil
    status['compliantRole']['role1'][0]['pool']['id'].should == p1.id
  end

  it 'pool with role should have priority over pool without' do
    mkt_product1 = create_product(random_string('product'),
                              random_string('product'),
                              {:attributes => {:roles => "role1"},
                               :owner => owner_key})
    mkt_product2 = create_product(random_string('product'),
                              random_string('product'),
                              {:owner => owner_key})
    eng_product = create_product(random_string('product'),
                                 random_string('product'),
                                 {:owner => owner_key})
    p1 = create_pool_and_subscription(owner_key, mkt_product1.id, 10, [eng_product.id])
    p2 = create_pool_and_subscription(owner_key, mkt_product2.id, 10, [eng_product.id])

    installed = [
        {'productId' => eng_product.id, 'productName' => eng_product['name']}]

    consumer = @cp.register(
        random_string('systempurpose'), :system, nil, {}, nil, owner_key, [], installed, nil, [],
        nil, [], nil, nil, nil, nil, nil, 0, nil, nil, 'role1', nil, [])
    status = @cp.get_purpose_compliance(consumer['uuid'])
    status['status'].should == 'mismatched'
    status['nonCompliantRole'].include?('role1').should == true

    @cp.consume_product(nil, {:uuid => consumer.uuid})
    entitlements = @cp.list_entitlements(:uuid => consumer.uuid)
    entitlements.size.should == 1
    # print ("entitlements: " + entitlements.inspect())
    status = @cp.get_purpose_compliance(consumer.uuid)
    status['status'].should == 'matched'
    status['nonCompliantRole'].should be_nil
    status['compliantRole']['role1'][0]['pool']['id'].should == p1.id
  end

  it 'pool with addon should have priority over pool without' do
    mkt_product1 = create_product(random_string('product'),
                                  random_string('product'),
                                  {:attributes => {:addons => "addon1"},
                                   :owner => owner_key})
    mkt_product2 = create_product(random_string('product'),
                                  random_string('product'),
                                  {:owner => owner_key})
    eng_product = create_product(random_string('product'),
                                 random_string('product'),
                                 {:owner => owner_key})
    p1 = create_pool_and_subscription(owner_key, mkt_product1.id, 10, [eng_product.id])
    p2 = create_pool_and_subscription(owner_key, mkt_product2.id, 10, [eng_product.id])

    installed = [
        {'productId' => eng_product.id, 'productName' => eng_product['name']}]

    consumer = @cp.register(
        random_string('systempurpose'), :system, nil, {}, nil, owner_key, [], installed, nil, [],
        nil, [], nil, nil, nil, nil, nil, 0, nil, nil, nil, nil, ['addon1'])
    status = @cp.get_purpose_compliance(consumer['uuid'])
    status['status'].should == 'mismatched'
    status['nonCompliantAddOns'].include?('addon1').should == true

    @cp.consume_product(nil, {:uuid => consumer.uuid})
    entitlements = @cp.list_entitlements(:uuid => consumer.uuid)
    entitlements.size.should == 1
    # print ("entitlements: " + entitlements.inspect())
    status = @cp.get_purpose_compliance(consumer.uuid)
    status['status'].should == 'matched'
    status['nonCompliantAddOns'].size.should == 0
    status['compliantAddOns']['addon1'][0]['pool']['id'].should == p1.id
  end

  it 'should attach more than on addon pool when needed' do
    product1 = create_product(random_string('product'),
                              random_string('product'),
                              {:attributes => {:addons => "addon1"},
                               :owner => owner_key})
    p1 = create_pool_and_subscription(owner_key, product1.id)
    product2 = create_product(random_string('product'),
                              random_string('product'),
                              {:attributes => {:addons => "addon2"},
                              :owner => owner_key})
    p2 = create_pool_and_subscription(owner_key, product2.id)

    installed = [
        {'productId' => product2.id, 'productName' => product2['name']}]

    consumer = @cp.register(
        random_string('systempurpose'), :system, nil, {}, nil, owner_key, [], installed, nil, [],
        nil, [], nil, nil, nil, nil, nil, 0, nil, nil, nil, nil, ['addon1','addon2'])
    status = @cp.get_purpose_compliance(consumer['uuid'])
    status['status'].should == 'mismatched'
    status['nonCompliantAddOns'].include?('addon1').should == true

    @cp.consume_product(nil, {:uuid => consumer.uuid})
    entitlements = @cp.list_entitlements(:uuid => consumer.uuid)
    entitlements.size.should == 2
    # print ("entitlements: " + entitlements.inspect())
    status = @cp.get_purpose_compliance(consumer.uuid)
    status['status'].should == 'matched'
    status['nonCompliantAddOns'].size.should == 0
    status['compliantAddOns']['addon1'][0]['pool']['id'].should == p1.id
    status['compliantAddOns']['addon2'][0]['pool']['id'].should == p2.id
  end

  it 'pool with usage should have priority over pool without' do
    mkt_product1 = create_product(random_string('product'),
                              random_string('product'),
                              {:attributes => {:usage => "my_usage"},
                               :owner => owner_key})
    mkt_product2 = create_product(random_string('product'),
                              random_string('product'),
                              {:owner => owner_key})
    eng_product = create_product(random_string('product'),
                                 random_string('product'),
                                 {:owner => owner_key})
    p1 = create_pool_and_subscription(owner_key, mkt_product1.id, 10, [eng_product.id])
    p2 = create_pool_and_subscription(owner_key, mkt_product2.id, 10, [eng_product.id])

    installed = [
        {'productId' => eng_product.id, 'productName' => eng_product['name']}]

    consumer = @cp.register(
        random_string('systempurpose'), :system, nil, {}, nil, owner_key, [], installed, nil, [],
        nil, [], nil, nil, nil, nil, nil, 0, nil, nil, nil, "my_usage", [])
    status = @cp.get_purpose_compliance(consumer['uuid'])
    status['status'].should == 'mismatched'
    status['nonCompliantUsage'].include?('my_usage').should == true

    @cp.consume_product(nil, {:uuid => consumer.uuid})
    entitlements = @cp.list_entitlements(:uuid => consumer.uuid)
    entitlements.size.should == 1
    entitlements[0].pool.id.should == p1.id

    status = @cp.get_purpose_compliance(consumer.uuid)
    status['status'].should == 'matched'
    status['nonCompliantUsage'].should be_nil
    status['compliantUsage']['my_usage'][0]['pool']['id'].should == p1.id
  end

  it 'pool with matching role should be attached even if it covers no products' do
    mkt_product1 = create_product(random_string('product'),
                              random_string('product'),
                              {:attributes => {:roles => "provided_role,another_role"},
                               :owner => owner_key})
    p1 = create_pool_and_subscription(owner_key, mkt_product1.id, 10, [])

    eng_product = create_product(random_string('product'),
                                 random_string('product'),
                                 {:owner => owner_key})

    installed = [
        {'productId' => eng_product.id, 'productName' => eng_product['name']}]

    consumer = @cp.register(
        random_string('systempurpose'), :system, nil, {}, nil, owner_key, [], installed, nil, [],
        nil, [], nil, nil, nil, nil, nil, 0, nil, nil, "provided_role", nil, [])

    status = @cp.get_purpose_compliance(consumer['uuid'])
    status['status'].should == 'mismatched'
    status['nonCompliantRole'].include?('provided_role').should == true

    @cp.consume_product(nil, {:uuid => consumer.uuid})
    entitlements = @cp.list_entitlements(:uuid => consumer.uuid)
    entitlements.size.should == 1
    entitlements[0].pool.id.should == p1.id

    status = @cp.get_purpose_compliance(consumer.uuid)
    status['status'].should == 'matched'
    status['nonCompliantRole'].should be_nil
    status['compliantRole']['provided_role'][0]['pool']['id'].should == p1.id
  end

  it 'pool with matching addon should be attached even if it covers no products' do
    mkt_product1 = create_product(random_string('product'),
                              random_string('product'),
                              {:attributes => {:addons => "provided_addon,another_addon"},
                               :owner => owner_key})
    p1 = create_pool_and_subscription(owner_key, mkt_product1.id, 10, [])

    eng_product = create_product(random_string('product'),
                                 random_string('product'),
                                 {:owner => owner_key})

    installed = [
        {'productId' => eng_product.id, 'productName' => eng_product['name']}]

    consumer = @cp.register(
        random_string('systempurpose'), :system, nil, {}, nil, owner_key, [], installed, nil, [],
        nil, [], nil, nil, nil, nil, nil, 0, nil, nil, nil, nil, ["provided_addon", "random_addon"])

    status = @cp.get_purpose_compliance(consumer['uuid'])
    status['status'].should == 'mismatched'
    status['nonCompliantAddOns'].include?('provided_addon').should == true
    status['nonCompliantAddOns'].include?('random_addon').should == true

    @cp.consume_product(nil, {:uuid => consumer.uuid})
    entitlements = @cp.list_entitlements(:uuid => consumer.uuid)
    entitlements.size.should == 1
    entitlements[0].pool.id.should == p1.id

    status = @cp.get_purpose_compliance(consumer.uuid)
    status['status'].should == 'mismatched'
    status['nonCompliantAddOns'].include?('random_addon').should == true
    status['compliantAddOns']['provided_addon'][0]['pool']['id'].should == p1.id
  end

  it 'pool with matching usage should not be attached when it covers no products' do
    mkt_product1 = create_product(random_string('product'),
                              random_string('product'),
                              {:attributes => {:usage => "provided_usage"},
                               :owner => owner_key})
    p1 = create_pool_and_subscription(owner_key, mkt_product1.id, 10, [])

    eng_product = create_product(random_string('product'),
                                 random_string('product'),
                                 {:owner => owner_key})

    installed = [
        {'productId' => eng_product.id, 'productName' => eng_product['name']}]

    consumer = @cp.register(
        random_string('systempurpose'), :system, nil, {}, nil, owner_key, [], installed, nil, [],
        nil, [], nil, nil, nil, nil, nil, 0, nil, nil, nil, "provided_usage", [])

    status = @cp.get_purpose_compliance(consumer['uuid'])
    status['status'].should == 'mismatched'
    status['nonCompliantUsage'].include?('provided_usage').should == true

    @cp.consume_product(nil, {:uuid => consumer.uuid})
    entitlements = @cp.list_entitlements(:uuid => consumer.uuid)
    entitlements.size.should == 0

    status = @cp.get_purpose_compliance(consumer.uuid)
    status['status'].should == 'mismatched'
    status['nonCompliantUsage'].include?('provided_usage').should == true
  end

  it 'pool with matching SLA should not be attached when it covers no products' do
    mkt_product1 = create_product(random_string('product'),
                              random_string('product'),
                              {:attributes => {:support_level => "provided_sla"},
                               :owner => owner_key})
    p1 = create_pool_and_subscription(owner_key, mkt_product1.id, 10, [])

    eng_product = create_product(random_string('product'),
                                 random_string('product'),
                                 {:owner => owner_key})

    installed = [
        {'productId' => eng_product.id, 'productName' => eng_product['name']}]

    consumer = @cp.register(
        random_string('systempurpose'), :system, nil, {}, nil, owner_key, [], installed, nil, [],
        nil, [], nil, nil, nil, nil, nil, 0, nil, "provided_sla", nil, nil, [])

    status = @cp.get_purpose_compliance(consumer['uuid'])
    status['status'].should == 'mismatched'
    status['nonCompliantSLA'].include?('provided_sla').should == true

    @cp.consume_product(nil, {:uuid => consumer.uuid})
    entitlements = @cp.list_entitlements(:uuid => consumer.uuid)
    entitlements.size.should == 0

    status = @cp.get_purpose_compliance(consumer.uuid)
    status['status'].should == 'mismatched'
    status['nonCompliantSLA'].include?('provided_sla').should == true
  end

  it 'all pools with matching either role, addon or installed product should be attached, but not if they match usage or SLA only' do
    eng_product = create_product(random_string('product'),
                             random_string('product'),
                             {:owner => owner_key})

    mkt_prod_with_eng_product_only = create_product(random_string('product'),
                              random_string('product'),
                              {
                               :owner => owner_key})
    pool_with_eng_product_only = create_pool_and_subscription(owner_key, mkt_prod_with_eng_product_only.id,
                              10, [eng_product.id])

    mkt_prod_with_role_only = create_product(random_string('product'),
                              random_string('product'),
                              {:attributes => {:roles => "provided_role,non_provided_role"},
                               :owner => owner_key})
    pool_with_role_only = create_pool_and_subscription(owner_key, mkt_prod_with_role_only.id, 10, [])

    mkt_prod_with_addon_only = create_product(random_string('product'),
                              random_string('product'),
                              {:attributes => {:addons => "provided_addon,non_provided_addon"},
                               :owner => owner_key})
    pool_with_addon_only = create_pool_and_subscription(owner_key, mkt_prod_with_addon_only.id, 10, [])

    mkt_prod_with_usage_only = create_product(random_string('product'),
                              random_string('product'),
                              {:attributes => {:usage => "provided_usage"},
                               :owner => owner_key})
    pool_with_usage_only = create_pool_and_subscription(owner_key, mkt_prod_with_usage_only.id, 10, [])

    mkt_prod_with_sla_only = create_product(random_string('product'),
                              random_string('product'),
                              {:attributes => {:support_level => "provided_sla"},
                               :owner => owner_key})
    pool_with_sla_only = create_pool_and_subscription(owner_key, mkt_prod_with_sla_only.id, 10, [])

    installed = [
        {'productId' => eng_product.id, 'productName' => eng_product['name']}]

    consumer = @cp.register(
            random_string('systempurpose'), :system, nil, {}, nil, owner_key, [], installed, nil, [],
            nil, [], nil, nil, nil, nil, nil, 0, nil,
            "provided_sla", "provided_role", "provided_usage", ["provided_addon", "another_addon"])

    status = @cp.get_purpose_compliance(consumer['uuid'])
    status['status'].should == 'mismatched'
    status['nonCompliantSLA'].include?('provided_sla').should == true
    status['nonCompliantUsage'].include?('provided_usage').should == true
    status['nonCompliantRole'].include?('provided_role').should == true
    status['nonCompliantAddOns'].include?('provided_addon').should == true
    status['nonCompliantAddOns'].include?('another_addon').should == true

    @cp.consume_product(nil, {:uuid => consumer.uuid})
    entitlements = @cp.list_entitlements(:uuid => consumer.uuid)
    entitlements.size.should == 3

    # Check which pools are attached:
    # The pools attached should be: pool_with_addon_only, pool_with_role_only and pool_with_eng_product_only
    # The pools NOT attached should be: pool_with_usage_only and pool_with_sla_only
    entitlements.each do |entitlement|
      if entitlement.pool.id != pool_with_addon_only.id \
        and entitlement.pool.id != pool_with_role_only.id \
        and entitlement.pool.id != pool_with_eng_product_only.id
          if entitlement.pool.id == pool_with_usage_only.id
            fail("pool #{entitlement.pool.id} should not have been attached because
                  it only provides a matching usage!")
          end
          if entitlement.pool.id == pool_with_sla_only.id
            fail("pool #{entitlement.pool.id} should not have been attached because
                  it only provides a matching SLA!")
          end
      end
    end

    status = @cp.get_purpose_compliance(consumer.uuid)
    status['status'].should == 'mismatched'
    status['nonCompliantSLA'].include?('provided_sla').should == true
    status['nonCompliantUsage'].include?('provided_usage').should == true
    status['nonCompliantAddOns'].include?('another_addon').should == true

    status['compliantAddOns']['provided_addon'][0]['pool']['id'].should == pool_with_addon_only.id
    status['compliantRole']['provided_role'][0]['pool']['id'].should == pool_with_role_only.id
  end
end
