require 'spec_helper'
require 'candlepin_scenarios'

describe 'vCPU Limiting' do
  include CandlepinMethods

  before(:each) do
    @owner = create_owner random_string('test_owner')

    # Create a product limiting by core only.
    @vcpu_product = create_product(nil, random_string("Product1"), :attributes =>
                {:version => '6.4',
                 :vcpu => 8,
                 :cores => 2,
                 :sockets => 1,
                 :warning_period => 15,
                 :management_enabled => true,
                 :support_level => 'standard',
                 :support_type => 'excellent',})
    @vcpu_sub = @cp.create_subscription(@owner['key'], @vcpu_product.id, 10, [], '1888', '1234')

    @vcpu_stackable_prod = create_product(nil, random_string("Product2"), :attributes =>
                {:version => '6.4',
                 :vcpu => 8,
                 :cores => 2,
                 :sockets => 1,
                 :warning_period => 15,
                 :management_enabled => true,
                 :support_level => 'standard',
                 :support_type => 'excellent',
                 :'multi-entitlement' => 'yes',
                 :stacking_id => '12344321'})
    @vcpu_stackable_sub = @cp.create_subscription(@owner['key'], @vcpu_stackable_prod.id, 10, [], '1888', '1234')

    # Refresh pools so that the subscription pools will be available to the test systems.
    @cp.refresh_pools(@owner['key'])

    @user = user_client(@owner, random_string('test-user'))
  end

  it 'consumer status should be valid when consumer vCPUs are covered' do
    system = consumer_client(@user, random_string('system1'), :system, nil,
                {'system.certificate_version' => '3.2',
                 # Simulate 8 cores as would be returned from system fact
                 'cpu.core(s)_per_socket' => '8',
                 'virt.is_guest' => 'true'})
    installed = [
        {'productId' => @vcpu_product.id, 'productName' => @vcpu_product.name}
    ]
    system.update_consumer({:installedProducts => installed})

    entitlement = system.consume_product(@vcpu_product.id)[0]
    entitlement.should_not == nil

    compliance_status = system.get_compliance(consumer_id=system.uuid)
    compliance_status['status'].should == 'valid'
    compliance_status['compliant'].should == true
    compliant_products = compliance_status['compliantProducts']
    compliant_products.should have_key(@vcpu_product.id)
  end

  it 'consumer status should be partial when consumer vCPUs are not covered' do
    system = consumer_client(@user, random_string('system1'), :system, nil,
                {'system.certificate_version' => '3.2',
                 # Simulate 16 cores as would be returned from system fact
                 'cpu.core(s)_per_socket' => '16',
                 'virt.is_guest' => 'true'})
    installed = [
        {'productId' => @vcpu_product.id, 'productName' => @vcpu_product.name}
    ]
    system.update_consumer({:installedProducts => installed})

    pool = find_pool(@owner.id, @vcpu_sub.id)
    pool.should_not == nil

    entitlement = system.consume_pool(pool.id, {:quantity => 1})
    entitlement.should_not == nil

    compliance_status = system.get_compliance(consumer_id=system.uuid)
    compliance_status['status'].should == 'partial'
    compliance_status['compliant'].should == false
    partially_compliant_products = compliance_status['partiallyCompliantProducts']
    partially_compliant_products.should have_key(@vcpu_product.id)
    compliance_status.reasons.length.should == 1
    compliance_status.reasons[0]['key'].should == 'VCPU'
  end

  it 'can heal single entitlement when vcpu limited' do
    system = consumer_client(@user, random_string('system1'), :system, nil,
                {'system.certificate_version' => '3.2',
                 # Simulate 8 cores as would be returned from system fact
                 'cpu.core(s)_per_socket' => '8',
                 'virt.is_guest' => 'true'})
    installed = [
        {'productId' => @vcpu_product.id, 'productName' => @vcpu_product.name}
    ]
    system.update_consumer({:installedProducts => installed})

    # Perform healing
    ents = system.consume_product()
    ents.size.should == 1
  end

  it 'can heal correct quantity when vcpu limited' do
    system = consumer_client(@user, random_string('system1'), :system, nil,
                {'system.certificate_version' => '3.2',
                 # Simulate 32 cores as would be returned from system fact
                 'cpu.core(s)_per_socket' => '32',
                 'virt.is_guest' => 'true'})
    installed = [
        {'productId' => @vcpu_stackable_prod.id, 'productName' => @vcpu_stackable_prod.name}
    ]
    system.update_consumer({:installedProducts => installed})

    # Perform healing
    ents = system.consume_product()
    ents.size.should == 1
    ents[0].quantity.should == 4
  end

  it 'will not heal when system vcpu is not covered by any entitlements' do
    system = consumer_client(@user, random_string('system1'), :system, nil,
                {'system.certificate_version' => '3.2',
                 # Simulate 12 corse as would be returned from system fact
                 'cpu.core(s)_per_socket' => '12',
                 'virt.is_guest' => 'true'})
    installed = [
        {'productId' => @vcpu_product.id, 'productName' => @vcpu_product.name}
    ]
    system.update_consumer({:installedProducts => installed})

    # Perform healing
    ents = system.consume_product()
    ents.should == nil
  end

end

