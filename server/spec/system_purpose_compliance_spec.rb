# -*- coding: utf-8 -*-
require 'spec_helper'
require 'candlepin_scenarios'

describe 'System purpose compliance' do

  include CandlepinMethods
  include AttributeHelper

  before(:each) do
    @owner2 = create_owner random_string('test_owner2')
    @username2 = random_string("user2")
    @user2 = user_client(@owner2, @username2)
    @consumer2 = consumer_client(@user2, random_string("consumer2"))
  end

  it 'should be valid for a consumer sans purpose preference' do
      consumer = @user2.register(
          random_string('systempurpose'), :system, nil, {}, nil, @owner2['key'], [], [], nil, [],
          nil, [], nil, nil, nil, nil, nil, 0, nil, nil, nil, nil, nil)
      status = @user2.get_purpose_compliance(consumer['uuid'])
      status['status'].should == 'valid'
  end

  it 'should be invalid for unsatisfied role' do
      consumer = @user2.register(
          random_string('systempurpose'), :system, nil, {}, nil, @owner2['key'], [], [], nil, [],
          nil, [], nil, nil, nil, nil, nil, 0, nil, nil, 'unsatisfied-role', nil, nil)
      status = @user2.get_purpose_compliance(consumer['uuid'])
      status['status'].should == 'invalid'
      status['nonCompliantRole'].should == 'unsatisfied-role'
      status.reasons.size.should == 1
      status.reasons.include?("unsatisfied role: unsatisfied-role").should == true
  end

  it 'should change to valid after satisfying role' do
      product = create_product(random_string('product'),
                              random_string('product'),
                              {:attributes => {:roles => 'myrole'},
                               :owner => @owner2['key']})
      p = create_pool_and_subscription(@owner2['key'], product.id)
      consumer = @user2.register(
          random_string('systempurpose'), :system, nil, {}, nil, @owner2['key'], [], [], nil, [],
          nil, [], nil, nil, nil, nil, nil, 0, nil, nil, 'myrole', nil, nil)
      status = @user2.get_purpose_compliance(consumer['uuid'])
      status['status'].should == 'invalid'
      status['nonCompliantRole'].include?('myrole').should == true
      @user2.consume_pool(p.id, params={:uuid=>consumer.uuid})
      status = @user2.get_purpose_compliance(consumer['uuid'])
      status['status'].should == 'valid'
      status['compliantRole']['myrole'][0]['pool']['id'].should == p.id
  end

  it 'should be invalid for unsatisfied usage' do
      consumer = @user2.register(
          random_string('systempurpose'), :system, nil, {}, nil, @owner2['key'], [], [], nil, [],
          nil, [], nil, nil, nil, nil, nil, 0, nil, nil, nil, 'taylor', nil)
      status = @user2.get_purpose_compliance(consumer['uuid'])
      status['status'].should == 'invalid'
      status['nonCompliantUsage'].should == 'taylor'
      status.reasons.size.should == 1
      status.reasons.include?("unsatisfied usage: taylor").should == true
  end

  it 'should change to valid after satisfying usage' do
      product = create_product(random_string('product'),
                              random_string('product'),
                              {:attributes => {:usage => 'myusage'},
                               :owner => @owner2['key']})
      p = create_pool_and_subscription(@owner2['key'], product.id)
      consumer = @user2.register(
          random_string('systempurpose'), :system, nil, {}, nil, @owner2['key'], [], [], nil, [],
          nil, [], nil, nil, nil, nil, nil, 0, nil, nil, nil, 'myusage', nil)
      status = @user2.get_purpose_compliance(consumer['uuid'])
      status['status'].should == 'invalid'
      status['nonCompliantUsage'].include?('myusage').should == true
      @user2.consume_pool(p.id, params={:uuid=>consumer.uuid})
      status = @user2.get_purpose_compliance(consumer['uuid'])
      status['status'].should == 'valid'
      status['compliantUsage']['myusage'][0]['pool']['id'].should == p.id
  end

  it 'should be invalid for unsatisfied add on' do
      consumer = @user2.register(
          random_string('systempurpose'), :system, nil, {}, nil, @owner2['key'], [], [], nil, [],
          nil, [], nil, nil, nil, nil, nil, 0, nil, nil, nil, nil, ['addon1', 'addon2'])
      status = @user2.get_purpose_compliance(consumer['uuid'])
      status['status'].should == 'invalid'
      status['nonCompliantAddOns'].include?('addon1').should == true
      status['nonCompliantAddOns'].include?('addon2').should == true
      status.reasons.size.should == 2
      status.reasons.include?("unsatisfied add on: addon1").should == true
      status.reasons.include?("unsatisfied add on: addon2").should == true
  end

  it 'should change to valid after satisfying add ons' do
      product1 = create_product(random_string('product'),
                              random_string('product'),
                              {:attributes => {:addons => "addon1"},
                               :owner => @owner2['key']})
      p1 = create_pool_and_subscription(@owner2['key'], product1.id)
      product2 = create_product(random_string('product'),
                              random_string('product'),
                              {:attributes => {:addons => "addon2"},
                               :owner => @owner2['key']})
      p2 = create_pool_and_subscription(@owner2['key'], product2.id)

      consumer = @user2.register(
          random_string('systempurpose'), :system, nil, {}, nil, @owner2['key'], [], [], nil, [],
          nil, [], nil, nil, nil, nil, nil, 0, nil, nil, nil, nil, ['addon1', 'addon2'])
      status = @user2.get_purpose_compliance(consumer['uuid'])
      status['status'].should == 'invalid'
      status['nonCompliantAddOns'].include?('addon1').should == true
      status['nonCompliantAddOns'].include?('addon2').should == true

      @user2.consume_pool(p1.id, params={:uuid=>consumer.uuid})
      status = @user2.get_purpose_compliance(consumer['uuid'])
      status['status'].should == 'partial'
      status['nonCompliantAddOns'].size.should == 1
      status['nonCompliantAddOns'].should == ['addon2']
      status['compliantAddOns']['addon1'][0]['pool']['id'].should == p1.id

      @user2.consume_pool(p2.id, params={:uuid=>consumer.uuid})
      status = @user2.get_purpose_compliance(consumer['uuid'])
      status['status'].should == 'valid'
      status['nonCompliantAddOns'].size.should == 0
      status['compliantAddOns']['addon1'][0]['pool']['id'].should == p1.id
      status['compliantAddOns']['addon2'][0]['pool']['id'].should == p2.id
  end

  it 'should be invalid for unsatisfied sla' do
      product = create_product(random_string('product'),
                              random_string('product'),
                              {:attributes => {:support_level => 'mysla'},
                               :owner => @owner2['key']})
      create_pool_and_subscription(@owner2['key'], product.id)
      consumer = @user2.register(
          random_string('systempurpose'), :system, nil, {}, nil, @owner2['key'], [], [], nil, [],
          nil, [], nil, nil, nil, nil, nil, 0, nil, 'mysla', nil, nil, nil)
      status = @user2.get_purpose_compliance(consumer['uuid'])
      status['status'].should == 'invalid'
      status['nonCompliantSLA'].include?('mysla').should == true
      status.reasons.size.should == 1
      status.reasons.include?("unsatisfied sla: mysla").should == true

      # should not change for another SLA
      product = create_product(random_string('product'),
                              random_string('product'),
                              {:attributes => {:support_level => 'anothersla'},
                               :owner => @owner2['key']})
      p = create_pool_and_subscription(@owner2['key'], product.id)
      @user2.consume_pool(p.id, params={:uuid=>consumer.uuid})
      status = @user2.get_purpose_compliance(consumer['uuid'])
      status['status'].should == 'invalid'
      status['nonCompliantSLA'].include?('mysla').should == true
      status.reasons.size.should == 2
      status['nonPreferredSLA']['anothersla'][0]['pool']['id'].should == p.id
      status['reasons'].include?('expected sla is mysla but pool '+p.id+' with product '+product.id+' provides SLA: anothersla').should == true
      status['reasons'].include?('unsatisfied sla: mysla').should == true
  end

  it 'should change to valid after satisfying sla' do
      product = create_product(random_string('product'),
                              random_string('product'),
                              {:attributes => {:support_level => 'mysla'},
                               :owner => @owner2['key']})
      p = create_pool_and_subscription(@owner2['key'], product.id)
      consumer = @user2.register(
          random_string('systempurpose'), :system, nil, {}, nil, @owner2['key'], [], [], nil, [],
          nil, [], nil, nil, nil, nil, nil, 0, nil, 'mysla', nil, nil, nil)
      status = @user2.get_purpose_compliance(consumer['uuid'])
      status['status'].should == 'invalid'
      status['nonCompliantSLA'].include?('mysla').should == true
      @user2.consume_pool(p.id, params={:uuid=>consumer.uuid})
      status = @user2.get_purpose_compliance(consumer['uuid'])
      status['status'].should == 'valid'
      status['compliantSLA']['mysla'][0]['pool']['id'].should == p.id
  end

  it 'should be partial for mixed SLAs' do
      product = create_product(random_string('product'),
                              random_string('product'),
                              {:attributes => {:support_level => 'mysla'},
                               :owner => @owner2['key']})
      p = create_pool_and_subscription(@owner2['key'], product.id)
      another_product = create_product(random_string('product'),
                              random_string('product'),
                              {:attributes => {:support_level => 'anothersla'},
                               :owner => @owner2['key']})
      another_p = create_pool_and_subscription(@owner2['key'], another_product.id)
      consumer = @user2.register(
          random_string('systempurpose'), :system, nil, {}, nil, @owner2['key'], [], [], nil, [],
          nil, [], nil, nil, nil, nil, nil, 0, nil, 'mysla', nil, nil, nil)
      @user2.consume_pool(p.id, params={:uuid=>consumer.uuid})
      @user2.consume_pool(another_p.id, params={:uuid=>consumer.uuid})
      status = @user2.get_purpose_compliance(consumer['uuid'])
      status['status'].should == 'partial'
      status['compliantSLA']['mysla'][0]['pool']['id'].should == p.id
      status['nonPreferredSLA']['anothersla'][0]['pool']['id'].should == another_p.id
      status['reasons'][0].should == 'expected sla is mysla but pool '+another_p.id+' with product '+another_product.id+' provides SLA: anothersla'
  end

  it 'should be partial for mixed usages' do
      product = create_product(random_string('product'),
                              random_string('product'),
                              {:attributes => {:usage => 'myusage'},
                               :owner => @owner2['key']})
      p = create_pool_and_subscription(@owner2['key'], product.id)
      another_product = create_product(random_string('product'),
                              random_string('product'),
                              {:attributes => {:usage => 'anotherusage'},
                               :owner => @owner2['key']})
      another_p = create_pool_and_subscription(@owner2['key'], another_product.id)
      consumer = @user2.register(
          random_string('systempurpose'), :system, nil, {}, nil, @owner2['key'], [], [], nil, [],
          nil, [], nil, nil, nil, nil, nil, 0, nil, nil, nil, 'myusage', nil)
      @user2.consume_pool(p.id, params={:uuid=>consumer.uuid})
      @user2.consume_pool(another_p.id, params={:uuid=>consumer.uuid})
      status = @user2.get_purpose_compliance(consumer['uuid'])
      status['status'].should == 'partial'
      status['compliantUsage']['myusage'][0]['pool']['id'].should == p.id
      status['nonPreferredUsage']['anotherusage'][0]['pool']['id'].should == another_p.id
      status['reasons'].size.should == 1
      status['reasons'][0].should == 'expected usage is myusage but pool '+another_p.id+' with product '+another_product.id+' provides Usage: anotherusage'
  end

  it 'should change to invalid after revoking pools' do
      product = create_product(random_string('product'),
                              random_string('product'),
                              {:attributes => {:usage => 'myusage'},
                               :owner => @owner2['key']})
      p = create_pool_and_subscription(@owner2['key'], product.id)
      consumer = @user2.register(
          random_string('systempurpose'), :system, nil, {}, nil, @owner2['key'], [], [], nil, [],
          nil, [], nil, nil, nil, nil, nil, 0, nil, nil, nil, 'myusage', nil)
      @user2.consume_pool(p.id, params={:uuid=>consumer.uuid})
      status = @user2.get_purpose_compliance(consumer['uuid'])
      status['status'].should == 'valid'
      status['compliantUsage']['myusage'][0]['pool']['id'].should == p.id

      @user2.revoke_all_entitlements(consumer.uuid)
      status = @user2.get_purpose_compliance(consumer['uuid'])
      status['status'].should == 'invalid'
      status['nonCompliantUsage'].include?('myusage').should == true
      status['compliantUsage'].size.should == 0
  end

  it 'should allow setting system purpose properties on registration' do
      # Create a product and pool so the service level is available.
      product = create_product(random_string('product'),
                              random_string('product'),
                              {:attributes => {:support_level => 'premium'},
                               :owner => @owner2['key']})
      create_pool_and_subscription(@owner2['key'], product.id)

      consumer = @user2.register(
          random_string('systempurpose'),
          :system,
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
          nil,
          nil,
          0,
          nil,
          'premium',
          'role',
          'u-sage',
          ['add-on 1', 'add-on 2'])
      consumer['serviceLevel'].should == 'premium'
      consumer['role'].should == 'role'
      consumer['usage'].should == 'u-sage'

      add_ons = consumer['addOns']
      add_ons.size.should == 2
      add_ons.should include('add-on 1')
      add_ons.should include('add-on 2')

      # Make sure that the newly fetched consumer also shows changes
      fetched = @user2.get_consumer(consumer['uuid'])
      fetched['role'].should == 'role'
      fetched['usage'].should == 'u-sage'

      add_ons = fetched['addOns']
      add_ons.size.should == 2
      add_ons.should include('add-on 1')
      add_ons.should include('add-on 2')

  end

  it 'should allow updating system purpose properties on a consumer' do
      # Create a product and pool so the service level is available.
      product1 = create_product(random_string('product'),
                             random_string('product'),
                             {:attributes => {:support_level => 'basic'},
                              :owner => @owner2['key']})
      product2 = create_product(random_string('product'),
                                random_string('product'),
                                {:attributes => {:support_level => 'premium'},
                                 :owner => @owner2['key']})
      create_pool_and_subscription(@owner2['key'], product1.id)
      create_pool_and_subscription(@owner2['key'], product2.id)
      consumer = @user2.register(
          random_string('systempurpose'),
          :system,
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
          nil,
          nil,
          0,
          nil,
          'basic',
          'role',
          'u-sage',
          ['add-on 1', 'add-on 2'])
        consumer['serviceLevel'] == 'basic'
        consumer['role'].should == 'role'
        consumer['usage'].should == 'u-sage'

        add_ons = consumer['addOns']
        add_ons.size.should == 2
        add_ons.should include('add-on 1')
        add_ons.should include('add-on 2')

        update_args = {
            :uuid => consumer['uuid'],
            :serviceLevel => 'premium',
            :role => 'updatedrole',
            :usage => 'updatedusage',
            :addOns => ['add-on 2', 'add-on 4']
        }

        @user2.update_consumer(update_args)
        consumer = @user2.get_consumer(consumer['uuid'])
        consumer['serviceLevel'].should == 'premium'
        consumer['usage'].should == 'updatedusage'
        consumer['role'].should == 'updatedrole'

        add_ons = consumer['addOns']
        add_ons.size.should == 2
        add_ons.should include('add-on 2')
        add_ons.should include('add-on 4')
  end

  it 'should allow clearing system purpose properties on a consumer' do
      # Create a product and pool so the service level is available.
      product = create_product(random_string('product'),
                                random_string('product'),
                                {:attributes => {:support_level => 'basic'},
                                 :owner => @owner2['key']})
      create_pool_and_subscription(@owner2['key'], product.id)

      consumer = @user2.register(
          random_string('systempurpose'),
          :system,
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
          nil,
          nil,
          0,
          nil,
          'basic',
          'role',
          'u-sage',
          ['add-on 1', 'add-on 2'])
      consumer['serviceLevel'] == 'basic'
      consumer['role'].should == 'role'
      consumer['usage'].should == 'u-sage'

      add_ons = consumer['addOns']
      add_ons.size.should == 2
      add_ons.should include('add-on 1')
      add_ons.should include('add-on 2')

      update_args = {
          :uuid => consumer['uuid'],
          :serviceLevel => '',
          :role => '',
          :usage => '',
          :addOns => []
      }

      @user2.update_consumer(update_args)
      consumer = @user2.get_consumer(consumer['uuid'])
      consumer['serviceLevel'].should == ''
      consumer['usage'].should be_nil
      consumer['role'].should be_nil

      add_ons = consumer['addOns']
      add_ons.size.should == 0
  end

end
