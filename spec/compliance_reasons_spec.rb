require 'spec_helper'

require 'candlepin_scenarios'
require 'time'

def assert_reason(reason, expected_key, expected_message, expected_attributes)
  reason["key"].should == expected_key
  reason.message.should == expected_message

  # Check for expected attributes
  reason.attributes.size.should == expected_attributes.size
  for attribute in expected_attributes.keys()
    reason.attributes.should have_key(attribute)
    reason.attributes[attribute].should == expected_attributes[attribute]
  end
end

describe 'Single Entitlement Compliance Reasons' do
  include CandlepinMethods

  before(:each) do
    @owner = create_owner random_string('test_owner')

    # Create a product limiting by all of our attributes.
    @product1 = create_product(nil, random_string("Product1"), :attributes =>
                {:version => '6.4',
                 :ram => 8,
                 :sockets => 2,
                 :cores => 22,
                 :arch => 'x86_64',
                 :warning_period => 15,
                 :management_enabled => true,
                 :support_level => 'standard',
                 :support_type => 'excellent',})
    @product1_sub = @cp.create_subscription(@owner['key'], @product1.id, 100, [], '1888', '1234')
    
    # Refresh pools so that the subscription pools will be available to the test systems.
    @cp.refresh_pools(@owner['key'])
    
    @user = user_client(@owner, random_string('test-user'))
  end
  
  it 'reports products not covered' do
    system = consumer_client(@user, random_string('system1'), :system, nil,
                {'system.certificate_version' => '3.2',
                 'uname.machine' => 'x86_64',
                 'memory.memtotal' => '4194304'})
    installed = [
        {'productId' => @product1.id, 'productName' => @product1.name}
    ]
    system.update_consumer({:installedProducts => installed})

    compliance_status = system.get_compliance(consumer_id=system.uuid)
    compliance_status['status'].should == 'invalid'
    compliance_status['compliant'].should == false
    compliance_status.should have_key('reasons')
    reasons = compliance_status['reasons']
    reasons.size.should == 1
    
    expected_message = "Not supported by a valid subscription."
    assert_reason(reasons[0], "NOTCOVERED", expected_message, {"product_id" => @product1.id,
                                                                "name" => @product1.name})
  end
  
  it 'reports ram not covered but no installed product' do
    system = consumer_client(@user, random_string('system1'), :system, nil,
                {'system.certificate_version' => '3.2',
                 'uname.machine' => 'x86_64',
                 # Bump RAM so that it is not covered.
                 'memory.memtotal' => '16777216'})
    installed = []
    system.update_consumer({:installedProducts => installed})
    
    pool = find_pool(@owner.id, @product1_sub.id)
    pool.should_not == nil

    entitlements = system.consume_pool(pool.id, {:quantity => 1})
    entitlements.should_not == nil
    entitlements.size.should == 1
    entitlement = entitlements[0]

    compliance_status = system.get_compliance(consumer_id=system.uuid)
    compliance_status['status'].should == 'partial'
    compliance_status['compliant'].should == false
    compliance_status.should have_key('reasons')

    expected_has = "16"
    expected_covered = "8"
    expected_message = "Only supports %sGB of %sGB of RAM." % [expected_covered,
                                                           expected_has]

    reasons = compliance_status['reasons']
    reasons.size.should == 1
    assert_reason(reasons[0], 'RAM', expected_message, {'entitlement_id' => entitlement.id,
                                                        'covered' => expected_covered,
                                                        'has' => expected_has,
                                                        'name' => @product1.name})
  end

  it 'reports ram not covered' do
    system = consumer_client(@user, random_string('system1'), :system, nil,
                {'system.certificate_version' => '3.2',
                 'uname.machine' => 'x86_64',
                 # Bump RAM so that it is not covered.
                 'memory.memtotal' => '16777216'})
    installed = [
        {'productId' => @product1.id, 'productName' => @product1.name}
    ]
    system.update_consumer({:installedProducts => installed})
    
    pool = find_pool(@owner.id, @product1_sub.id)
    pool.should_not == nil

    entitlements = system.consume_pool(pool.id, {:quantity => 1})
    entitlements.should_not == nil
    entitlements.size.should == 1
    entitlement = entitlements[0]

    compliance_status = system.get_compliance(consumer_id=system.uuid)
    compliance_status['status'].should == 'partial'
    compliance_status['compliant'].should == false
    compliance_status.should have_key('reasons')

    expected_has = "16"
    expected_covered = "8"
    expected_message = "Only supports %sGB of %sGB of RAM." % [expected_covered,
                                                           expected_has]

    reasons = compliance_status['reasons']
    reasons.size.should == 1
    assert_reason(reasons[0], 'RAM', expected_message, {'entitlement_id' => entitlement.id,
                                                        'covered' => expected_covered,
                                                        'has' => expected_has,
                                                        'name' => @product1.name})
  end

  it 'future reasons system invalid' do
    system = consumer_client(@user, random_string('system1'), :system, nil,
                {'system.certificate_version' => '3.2',
                 'uname.machine' => 'x86_64',
                 'memory.memtotal' => '4194304',
                 'cpu.cpu_socket(s)' => '2'})
    installed = [
        {'productId' => @product1.id, 'productName' => @product1.name}
    ]
    system.update_consumer({:installedProducts => installed})

    pool = find_pool(@owner.id, @product1_sub.id)
    pool.should_not == nil

    entitlements = system.consume_pool(pool.id, {:quantity => 1})
    entitlements.should_not == nil
    entitlements.size.should == 1
    entitlement = entitlements[0]
    # One day after endDate
    after_stop_date = (Time.parse(entitlement.endDate) + 24 * 60 * 60).utc.iso8601

    now_compliance_status = system.get_compliance(consumer_id=system.uuid)
    compliance_status = system.get_compliance(consumer_id=system.uuid, on_date=after_stop_date)
    compliance_status['status'].should == 'invalid'
    compliance_status['compliant'].should == false
    compliance_status.should have_key('reasons')

    expected_message = "Not supported by a valid subscription."

    reasons = compliance_status['reasons']
    reasons.size.should == 1
    assert_reason(reasons[0], 'NOTCOVERED', expected_message, {'product_id' => @product1.id,
                                                        'name' => @product1.name})
    @cp.get_consumer(system.uuid)['entitlementStatus'].should_not == compliance_status['status']

  end

  it 'reports sockets not covered' do
    system = consumer_client(@user, random_string('system1'), :system, nil,
                {'system.certificate_version' => '3.2',
                 'uname.machine' => 'x86_64',
                 'memory.memtotal' => '4194304',
                 # Bump sockets so that it is not covered.
                 'cpu.cpu_socket(s)' => '12'})
    installed = [
        {'productId' => @product1.id, 'productName' => @product1.name}
    ]
    system.update_consumer({:installedProducts => installed})
    
    pool = find_pool(@owner.id, @product1_sub.id)
    pool.should_not == nil

    entitlements = system.consume_pool(pool.id, {:quantity => 1})
    entitlements.should_not == nil
    entitlements.size.should == 1
    entitlement = entitlements[0]

    compliance_status = system.get_compliance(consumer_id=system.uuid)
    compliance_status['status'].should == 'partial'
    compliance_status['compliant'].should == false
    compliance_status.should have_key('reasons')

    expected_has = "12"
    expected_covered = "2"
    expected_message = "Only supports %s of %s sockets." % [expected_covered,
                                                           expected_has]

    reasons = compliance_status['reasons']
    reasons.size.should == 1
    assert_reason(reasons[0], 'SOCKETS', expected_message, {'entitlement_id' => entitlement.id,
                                                        'covered' => expected_covered,
                                                        'has' => expected_has,
                                                        'name' => @product1.name})
  end
  
  it 'reports cores not covered' do
    system = consumer_client(@user, random_string('system1'), :system, nil,
                {'system.certificate_version' => '3.2',
                 'uname.machine' => 'x86_64',
                 'memory.memtotal' => '4194304',
                 # Bump cores so that it is not covered.
                 'cpu.core(s)_per_socket' => 12,
                 'cpu.cpu_socket(s)' => '2'})
    installed = [
        {'productId' => @product1.id, 'productName' => @product1.name}
    ]
    system.update_consumer({:installedProducts => installed})
    
    pool = find_pool(@owner.id, @product1_sub.id)
    pool.should_not == nil

    entitlements = system.consume_pool(pool.id, {:quantity => 1})
    entitlements.should_not == nil
    entitlements.size.should == 1
    entitlement = entitlements[0]

    compliance_status = system.get_compliance(consumer_id=system.uuid)
    compliance_status['status'].should == 'partial'
    compliance_status['compliant'].should == false
    compliance_status.should have_key('reasons')

    expected_has = "24"
    expected_covered = "22"
    expected_message = "Only supports %s of %s cores." % [expected_covered,
                                                           expected_has]

    reasons = compliance_status['reasons']
    reasons.size.should == 1
    assert_reason(reasons[0], 'CORES', expected_message, {'entitlement_id' => entitlement.id,
                                                        'covered' => expected_covered,
                                                        'has' => expected_has,
                                                        'name' => @product1.name})
  end
  
  it 'reports arch not covered' do
    system = consumer_client(@user, random_string('system1'), :system, nil,
                {'system.certificate_version' => '3.2',
                 # Set the arch so it doesn't match.
                 'uname.machine' => 'ppc64',
                 'cpu.cpu_socket(s)' => '2'})
    installed = [
        {'productId' => @product1.id, 'productName' => @product1.name}
    ]
    system.update_consumer({:installedProducts => installed})
    
    pool = find_pool(@owner.id, @product1_sub.id)
    pool.should_not == nil

    entitlements = system.consume_pool(pool.id, {:quantity => 1})
    entitlements.should_not == nil
    entitlements.size.should == 1
    entitlement = entitlements[0]

    compliance_status = system.get_compliance(consumer_id=system.uuid)
    compliance_status['status'].should == 'partial'
    compliance_status['compliant'].should == false
    compliance_status.should have_key('reasons')

    expected_has = "ppc64"
    expected_covered = "x86_64"
    expected_message = "Supports architecture %s but the system is %s." % [expected_covered,
                                                                           expected_has]

    reasons = compliance_status['reasons']
    reasons.size.should == 1
    assert_reason(reasons[0], 'ARCH', expected_message, {'entitlement_id' => entitlement.id,
                                                        'covered' => expected_covered,
                                                        'has' => expected_has,
                                                        'name' => @product1.name})
  end
  
  it 'reports multiple reasons' do
    system = consumer_client(@user, random_string('system1'), :system, nil,
                {'system.certificate_version' => '3.2',
                 # Non of the following attributes will be covered.
                 'uname.machine' => 'ppc64',
                 'memory.memtotal' => '16777216',
                 'cpu.core(s)_per_socket' => 12,
                 'cpu.cpu_socket(s)' => '20'})
    installed = [
        {'productId' => @product1.id, 'productName' => @product1.name}
    ]
    system.update_consumer({:installedProducts => installed})
    
    pool = find_pool(@owner.id, @product1_sub.id)
    pool.should_not == nil

    entitlements = system.consume_pool(pool.id, {:quantity => 1})
    entitlements.should_not == nil
    entitlements.size.should == 1
    entitlement = entitlements[0]

    compliance_status = system.get_compliance(consumer_id=system.uuid)
    compliance_status['status'].should == 'partial'
    compliance_status['compliant'].should == false
    compliance_status.should have_key('reasons')

    reasons = compliance_status['reasons']
    reasons.size.should == 4
    
    # Build up an expectation map to reason key because we
    # can't determine the order of the resons that are returned
    # from the server.
    reason_expectations = {}

    expected_has = "ppc64"
    expected_covered = "x86_64"
    expected_message = "Supports architecture %s but the system is %s." % [expected_covered,
                                                                           expected_has]
    reason_expectations["ARCH"] = {
            "key" => "ARCH",
            "attributes" => {
                              "entitlement_id" => entitlement.id,
                              "has" => expected_has,
                              "covered" => expected_covered,
                              "name" => @product1.name
            },
            "message" => expected_message
                         
                         
    }
    
    expected_has = "16"
    expected_covered = "8"
    expected_message = "Only supports %sGB of %sGB of RAM." % [expected_covered,
                                                              expected_has]
    reason_expectations["RAM"] = {
            "key" => "RAM",
            "attributes" => {
                              "entitlement_id" => entitlement.id,
                              "has" => expected_has,
                              "covered" => expected_covered,
                              "name" => @product1.name
            },
            "message" => expected_message
    }

    expected_has = "20"
    expected_covered = "2"
    expected_message = "Only supports %s of %s sockets." % [expected_covered,
                                                              expected_has]
    reason_expectations["SOCKETS"] = {
            "key" => "SOCKETS",
            "attributes" => {
                              "entitlement_id" => entitlement.id,
                              "has" => expected_has,
                              "covered" => expected_covered,
                              "name" => @product1.name
            },
            "message" => expected_message
    }
    
    expected_has = "240"
    expected_covered = "22"
    expected_message = "Only supports %s of %s cores." % [expected_covered,
                                                              expected_has]
    reason_expectations["CORES"] = {
            "key" => "CORES",
            "attributes" => {
                              "entitlement_id" => entitlement.id,
                              "has" => expected_has,
                              "covered" => expected_covered,
                              "name" => @product1.name
            },
            "message" => expected_message
    }
    for reason in reasons
        reason_expectations.should have_key(reason["key"])
        expectation = reason_expectations[reason["key"]]
        assert_reason(reason, expectation["key"], expectation.message, expectation.attributes)
    end
    
  end
end

describe 'Stacking Compliance Reasons' do
  include CandlepinMethods

  before(:each) do
    @owner = create_owner random_string('test_owner')

    @stack_id = random_string('test-stack-id')
    # Create a stackable RAM product.
    @stackable_product_1 = create_product(nil, random_string("Stackable"), :attributes =>
                {:version => '1.2',
                 :ram => 4,
                 :sockets => 2,
                 :cores => 10,
                 :arch => 'x86_64',
                 :vcpu => 8,
                 :support_level => 'standard',
                 :support_type => 'excellent',
                 :'multi-entitlement' => 'yes',
                 :stacking_id => @stack_id})
    @stackable_sub_1 = @cp.create_subscription(@owner['key'], @stackable_product_1.id, 10)

    @not_covered_product = create_product(nil, random_string("Not Covered Product"), :attributes =>
                {:version => '6.4',
                 :sockets => 2,
                 :warning_period => 15,
                 :management_enabled => true,
                 :support_level => 'standard',
                 :support_type => 'excellent',})
    @not_covered_product_sub = @cp.create_subscription(@owner['key'], @not_covered_product.id, 100, [],
                                                              '1999', '3332')
    
    # Refresh pools so that the subscription pools will be available to the test systems.
    @cp.refresh_pools(@owner['key'])
    
    @user = user_client(@owner, random_string('test-user'))
  end
  
  it 'report stack does not cover ram' do
    system = consumer_client(@user, random_string('system1'), :system, nil,
                {'system.certificate_version' => '3.2',
                 'uname.machine' => 'x86_64',
                 'memory.memtotal' => '16777216',
                 'cpu.cpu_socket(s)' => '4'})
    installed = [
        {'productId' => @stackable_product_1.id, 'productName' => @stackable_product_1.name}
    ]
    system.update_consumer({:installedProducts => installed})
    
    pool = find_pool(@owner.id, @stackable_sub_1.id)
    pool.should_not == nil

    entitlements = system.consume_pool(pool.id, {:quantity => 2})
    entitlements.should_not == nil
    entitlements.size.should == 1
    entitlement = entitlements[0]
    entitlement.quantity.should == 2

    compliance_status = system.get_compliance(consumer_id=system.uuid)
    compliance_status['status'].should == 'partial'
    compliance_status['compliant'].should == false
    compliance_status.should have_key('reasons')
    
    expected_has = "16"
    expected_covered = "8"
    expected_message = "Only supports %sGB of %sGB of RAM." % [expected_covered,
                                                                expected_has]

    reasons = compliance_status['reasons']
    reasons.size.should == 1
    assert_reason(reasons[0], 'RAM', expected_message, {'stack_id' => @stack_id,
                                                        'covered' => expected_covered,
                                                        'has' => expected_has,
                                                        'name' => @stackable_product_1.name})
  end
  
  it 'report partial for stack that does not cover ram and has no installed products' do
    system = consumer_client(@user, random_string('system1'), :system, nil,
                {'system.certificate_version' => '3.2',
                 'uname.machine' => 'x86_64',
                 'memory.memtotal' => '16777216',
                 'cpu.cpu_socket(s)' => '4'})
    installed = []
    system.update_consumer({:installedProducts => installed})
    
    pool = find_pool(@owner.id, @stackable_sub_1.id)
    pool.should_not == nil

    entitlements = system.consume_pool(pool.id, {:quantity => 2})
    entitlements.should_not == nil
    entitlements.size.should == 1
    entitlement = entitlements[0]
    entitlement.quantity.should == 2

    compliance_status = system.get_compliance(consumer_id=system.uuid)
    compliance_status['status'].should == 'partial'
    compliance_status['compliant'].should == false
    compliance_status.should have_key('reasons')
    
    expected_has = "16"
    expected_covered = "8"
    expected_message = "Only supports %sGB of %sGB of RAM." % [expected_covered,
                                                                expected_has]

    reasons = compliance_status['reasons']
    reasons.size.should == 1
    assert_reason(reasons[0], 'RAM', expected_message, {'stack_id' => @stack_id,
                                                        'covered' => expected_covered,
                                                        'has' => expected_has,
                                                        'name' => @stackable_product_1.name})
  end
  
  it 'report stack does not cover sockets' do
    system = consumer_client(@user, random_string('system1'), :system, nil,
                {'system.certificate_version' => '3.2',
                 'uname.machine' => 'x86_64',
                 'memory.memtotal' => '4194304',
                 'cpu.cpu_socket(s)' => '6'})
    installed = [
        {'productId' => @stackable_product_1.id, 'productName' => @stackable_product_1.name}
    ]
    system.update_consumer({:installedProducts => installed})
    
    pool = find_pool(@owner.id, @stackable_sub_1.id)
    pool.should_not == nil

    entitlements = system.consume_pool(pool.id, {:quantity => 2})
    entitlements.should_not == nil
    entitlements.size.should == 1
    entitlement = entitlements[0]
    entitlement.quantity.should == 2

    compliance_status = system.get_compliance(consumer_id=system.uuid)
    compliance_status['status'].should == 'partial'
    compliance_status['compliant'].should == false
    compliance_status.should have_key('reasons')
    
    expected_has = "6"
    expected_covered = "4"
    expected_message = "Only supports %s of %s sockets." % [expected_covered,
                                                             expected_has]

    reasons = compliance_status['reasons']
    reasons.size.should == 1
    assert_reason(reasons[0], 'SOCKETS', expected_message, {'stack_id' => @stack_id,
                                                        'covered' => expected_covered,
                                                        'has' => expected_has,
                                                        'name' => @stackable_product_1.name})
  end
  
  it 'report stack does not cover cores' do
    system = consumer_client(@user, random_string('system1'), :system, nil,
                {'system.certificate_version' => '3.2',
                 'uname.machine' => 'x86_64',
                 'memory.memtotal' => '4194304',
                 'cpu.cpu_socket(s)' => '1',
                 'cpu.core(s)_per_socket' => '30'
                 })
    installed = [
        {'productId' => @stackable_product_1.id, 'productName' => @stackable_product_1.name}
    ]
    system.update_consumer({:installedProducts => installed})
    
    pool = find_pool(@owner.id, @stackable_sub_1.id)
    pool.should_not == nil

    entitlements = system.consume_pool(pool.id, {:quantity => 2})
    entitlements.should_not == nil
    entitlements.size.should == 1
    entitlement = entitlements[0]
    entitlement.quantity.should == 2

    compliance_status = system.get_compliance(consumer_id=system.uuid)
    compliance_status['status'].should == 'partial'
    compliance_status['compliant'].should == false
    compliance_status.should have_key('reasons')
    
    expected_has = "30"
    expected_covered = "20"
    expected_message = "Only supports %s of %s cores." % [expected_covered,
                                                           expected_has]

    reasons = compliance_status['reasons']
    reasons.size.should == 1
    assert_reason(reasons[0], 'CORES', expected_message, {'stack_id' => @stack_id,
                                                        'covered' => expected_covered,
                                                        'has' => expected_has,
                                                        'name' => @stackable_product_1.name})
  end

  it 'report stack does not cover vCPUs' do
    system = consumer_client(@user, random_string('system1'), :system, nil,
                {'system.certificate_version' => '3.2',
                 'virt.is_guest' => 'true',
                 'uname.machine' => 'x86_64',
                 'memory.memtotal' => '4194304',
                 'cpu.cpu_socket(s)' => '1',
                 'cpu.core(s)_per_socket' => '30'
                 })
    installed = [
        {'productId' => @stackable_product_1.id, 'productName' => @stackable_product_1.name}
    ]
    system.update_consumer({:installedProducts => installed})
    
    pool = find_pool(@owner.id, @stackable_sub_1.id)
    pool.should_not == nil

    entitlements = system.consume_pool(pool.id, {:quantity => 2})
    entitlements.should_not == nil
    entitlements.size.should == 1
    entitlement = entitlements[0]
    entitlement.quantity.should == 2

    compliance_status = system.get_compliance(consumer_id=system.uuid)
    compliance_status['status'].should == 'partial'
    compliance_status['compliant'].should == false
    compliance_status.should have_key('reasons')
    
    expected_has = "30"
    expected_covered = "16"
    expected_message = "Only supports %s of %s vCPUs." % [expected_covered,
                                                           expected_has]

    reasons = compliance_status['reasons']
    reasons.size.should == 1
    assert_reason(reasons[0], 'VCPU', expected_message, {'stack_id' => @stack_id,
                                                        'covered' => expected_covered,
                                                        'has' => expected_has,
                                                        'name' => @stackable_product_1.name})
  end
  
  it 'report stack does not cover arch' do
    system = consumer_client(@user, random_string('system1'), :system, nil,
                {'system.certificate_version' => '3.2',
                 'uname.machine' => 'ppc64',
                 'memory.memtotal' => '4194304',
                 'cpu.cpu_socket(s)' => '1',
                 'cpu.core(s)_per_socket' => '10'
                 })
    installed = [
        {'productId' => @stackable_product_1.id, 'productName' => @stackable_product_1.name}
    ]
    system.update_consumer({:installedProducts => installed})
    
    pool = find_pool(@owner.id, @stackable_sub_1.id)
    pool.should_not == nil

    entitlements = system.consume_pool(pool.id, {:quantity => 2})
    entitlements.should_not == nil
    entitlements.size.should == 1
    entitlement = entitlements[0]
    entitlement.quantity.should == 2

    compliance_status = system.get_compliance(consumer_id=system.uuid)
    compliance_status['status'].should == 'partial'
    compliance_status['compliant'].should == false
    compliance_status.should have_key('reasons')
    
    expected_has = "ppc64"
    expected_covered = "x86_64"
    expected_message = "Supports architecture %s but the system is %s." % [expected_covered,
                                                                           expected_has]

    reasons = compliance_status['reasons']
    reasons.size.should == 1
    assert_reason(reasons[0], 'ARCH', expected_message, {'stack_id' => @stack_id,
                                                        'covered' => expected_covered,
                                                        'has' => expected_has,
                                                        'name' => @stackable_product_1.name})
  end
  
  it 'report stack does not cover all installed products' do
    system = consumer_client(@user, random_string('system1'), :system, nil,
                {'system.certificate_version' => '3.2',
                 'uname.machine' => 'x86_64',
                 'memory.memtotal' => '4194304',
                 'cpu.cpu_socket(s)' => '4'})
    installed = [
        {'productId' => @stackable_product_1.id, 'productName' => @stackable_product_1.name},
        {'productId' => @not_covered_product.id, 'productName' => @not_covered_product.name}
    ]
    system.update_consumer({:installedProducts => installed})
    
    pool = find_pool(@owner.id, @stackable_sub_1.id)
    pool.should_not == nil

    entitlements = system.consume_pool(pool.id, {:quantity => 2})
    entitlements.should_not == nil
    entitlements.size.should == 1
    entitlement = entitlements[0]
    entitlement.quantity.should == 2

    compliance_status = system.get_compliance(consumer_id=system.uuid)
    compliance_status['status'].should == 'invalid'
    compliance_status['compliant'].should == false
    compliance_status.should have_key('reasons')
    
    expected_message = "Not supported by a valid subscription."

    reasons = compliance_status['reasons']
    reasons.size.should == 1
    assert_reason(reasons[0], 'NOTCOVERED', expected_message, {'product_id' => @not_covered_product.id,
                                                                'name' => @not_covered_product.name})
  end
  
  it 'report stack does not cover multiple attributes' do
    system = consumer_client(@user, random_string('system1'), :system, nil,
                {'system.certificate_version' => '3.2',
                 # Non of the following attributes will be covered.
                 'uname.machine' => 'ppc64',
                 'memory.memtotal' => '16777216',
                 'cpu.core(s)_per_socket' => 12,
                 'cpu.cpu_socket(s)' => '20'})
    installed = [
        {'productId' => @stackable_product_1.id, 'productName' => @stackable_product_1.name}
    ]
    system.update_consumer({:installedProducts => installed})
    
    pool = find_pool(@owner.id, @stackable_sub_1.id)
    pool.should_not == nil

    entitlements = system.consume_pool(pool.id, {:quantity => 2})
    entitlements.should_not == nil
    entitlements.size.should == 1
    entitlement = entitlements[0]

    compliance_status = system.get_compliance(consumer_id=system.uuid)
    compliance_status['status'].should == 'partial'
    compliance_status['compliant'].should == false
    compliance_status.should have_key('reasons')
    
    # Build up an expectation map to reason key because we
    # can't determine the order of the resons that are returned
    # from the server.
    reason_expectations = {}
    
    expected_has = "ppc64"
    expected_covered = "x86_64"
    expected_message = "Supports architecture %s but the system is %s." % [expected_covered,
                                                                            expected_has]

    reason_expectations["ARCH"] = {
            "key" => "ARCH",
            "attributes" => {
                              'stack_id' => @stack_id,
                              "has" => expected_has,
                              "covered" => expected_covered,
                              "name" => @stackable_product_1.name
            },
            "message" => expected_message
    }
    
    expected_has = "16"
    expected_covered = "8"
    expected_message = "Only supports %sGB of %sGB of RAM." % [expected_covered,
                                                                expected_has]
    reason_expectations["RAM"] = {
            "key" => "RAM",
            "attributes" => {
                              'stack_id' => @stack_id,
                              "has" => expected_has,
                              "covered" => expected_covered,
                              "name" => @stackable_product_1.name
            },
            "message" => expected_message
    }
    
    expected_has = "20"
    expected_covered = "4"
    expected_message = "Only supports %s of %s sockets." % [expected_covered,
                                                           expected_has]
    reason_expectations["SOCKETS"] = {
            "key" => "SOCKETS",
            "attributes" => {
                              'stack_id' => @stack_id,
                              "has" => expected_has,
                              "covered" => expected_covered,
                              "name" => @stackable_product_1.name
            },
            "message" => expected_message
    }
    
    expected_has = "240"
    expected_covered = "20"
    expected_message = "Only supports %s of %s cores." % [expected_covered,
                                                           expected_has]
    reason_expectations["CORES"] = {
            "key" => "CORES",
            "attributes" => {
                              'stack_id' => @stack_id,
                              "has" => expected_has,
                              "covered" => expected_covered,
                              "name" => @stackable_product_1.name
            },
            "message" => expected_message
    }

    reasons = compliance_status['reasons']
    reasons.size.should == 4
    for reason in reasons
        reason_expectations.should have_key(reason["key"])
        expectation = reason_expectations[reason["key"]]
        assert_reason(reason, expectation["key"], expectation.message, expectation.attributes)
    end
  end
end
