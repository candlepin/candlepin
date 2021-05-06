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

  it 'should be not specified for a consumer sans purpose preference' do
      consumer = @user2.register(
          random_string('systempurpose'), :system, nil, {}, nil, @owner2['key'], [], [], nil, [],
          nil, [], nil, nil, nil, nil, nil, 0, nil, nil, nil, nil, nil)
      status = @user2.get_purpose_compliance(consumer['uuid'])
      status['status'].should == 'not specified'
  end

  it 'should be mismatched for unsatisfied role' do
      consumer = @user2.register(
          random_string('systempurpose'), :system, nil, {}, nil, @owner2['key'], [], [], nil, [],
          nil, [], nil, nil, nil, nil, nil, 0, nil, nil, 'unsatisfied-role', nil, nil)
      status = @user2.get_purpose_compliance(consumer['uuid'])
      status['status'].should == 'mismatched'
      status['nonCompliantRole'].should == 'unsatisfied-role'
      status.reasons.size.should == 1
      status.reasons.include?(
          'The requested role "unsatisfied-role" is not provided by a currently consumed subscription.'
          ).should == true
  end

  it 'should be matched on a future date for a future entitlement' do
      product = create_product(random_string('product'),
                              random_string('product'),
                              {:attributes => {:roles => 'myrole'},
                               :owner => @owner2['key']})
      start_date = Date.new(2037, 1, 1)
      end_date = Date.new(2037, 12, 31)
      p = create_pool_and_subscription(@owner2['key'], product.id, nil, nil, nil, nil, nil, start_date, end_date)

      consumer = @user2.register(
          random_string('systempurpose'), :system, nil, {}, nil, @owner2['key'], [], [], nil, [],
          nil, [], nil, nil, nil, nil, nil, 0, nil, nil, 'myrole', nil, nil)

      status = @user2.get_purpose_compliance(consumer['uuid'], on_date=Date.new(2037, 5, 5))
      status['status'].should == 'mismatched'
      status['nonCompliantRole'].include?('myrole').should == true

      @user2.consume_pool(p.id, params={:uuid=>consumer.uuid})

      # Check that the status is only matched during the period that the entitlement is valid
      status = @user2.get_purpose_compliance(consumer['uuid'])
      status['status'].should == 'mismatched'
      status['nonCompliantRole'].include?('myrole').should == true

      status = @user2.get_purpose_compliance(consumer['uuid'], on_date=Date.new(2037, 5, 5))
      status['status'].should == 'matched'
      status['compliantRole']['myrole'][0]['pool']['id'].should == p.id

      status = @user2.get_purpose_compliance(consumer['uuid'], on_date=Date.new(2036, 12, 31))
      status['status'].should == 'mismatched'
      status['nonCompliantRole'].include?('myrole').should == true

      status = @user2.get_purpose_compliance(consumer['uuid'], on_date=Date.new(2038, 1, 1))
      status['status'].should == 'mismatched'
      status['nonCompliantRole'].include?('myrole').should == true
  end

  it 'should not recalculate consumer status during status call with specified date' do
      product = create_product(random_string('product'),
                              random_string('product'),
                              {:attributes => {:roles => 'myrole'},
                               :owner => @owner2['key']})
      start_date = Date.new(2037, 1, 1)
      end_date = Date.new(2037, 12, 31)
      p = create_pool_and_subscription(@owner2['key'], product.id, nil, nil, nil, nil, nil, start_date, end_date)

      # check that the status has been calculated during consumer registration
      consumer = @user2.register(
          random_string('systempurpose'), :system, nil, {}, nil, @owner2['key'], [], [], nil, [],
          nil, [], nil, nil, nil, nil, nil, 0, nil, nil, 'myrole', nil, nil)
      consumer = @user2.get_consumer(consumer['uuid'])
      consumer.systemPurposeStatus.should == 'mismatched'

      # check that the status has not been miscalculated during a bind operation
      @user2.consume_pool(p.id, params={:uuid=>consumer.uuid})
      consumer = @user2.get_consumer(consumer['uuid'])
      consumer.systemPurposeStatus.should == 'mismatched'

      status = @user2.get_purpose_compliance(consumer['uuid'])
      status['status'].should == 'mismatched'
      status['nonCompliantRole'].include?('myrole').should == true
      consumer = @user2.get_consumer(consumer['uuid'])
      consumer.systemPurposeStatus.should == 'mismatched'

      # check that the status has not been recalculated & persisted during a call using on_date
      status = @user2.get_purpose_compliance(consumer['uuid'], on_date=Date.new(2037, 5, 5))
      status['status'].should == 'matched'
      status['compliantRole']['myrole'][0]['pool']['id'].should == p.id
      consumer = @user2.get_consumer(consumer['uuid'])
      consumer.systemPurposeStatus.should == 'mismatched'
  end

  it 'should change to matched after satisfying role' do
      product = create_product(random_string('product'),
                              random_string('product'),
                              {:attributes => {:roles => 'myrole'},
                               :owner => @owner2['key']})
      p = create_pool_and_subscription(@owner2['key'], product.id)
      consumer = @user2.register(
          random_string('systempurpose'), :system, nil, {}, nil, @owner2['key'], [], [], nil, [],
          nil, [], nil, nil, nil, nil, nil, 0, nil, nil, 'myrole', nil, nil)
      status = @user2.get_purpose_compliance(consumer['uuid'])
      status['status'].should == 'mismatched'
      status['nonCompliantRole'].include?('myrole').should == true

      @user2.consume_pool(p.id, params={:uuid=>consumer.uuid})
      status = @user2.get_purpose_compliance(consumer['uuid'])
      status['status'].should == 'matched'
      status['compliantRole']['myrole'][0]['pool']['id'].should == p.id
  end

  it 'should be not specified for any SLA when consumer has null SLA' do
      product = create_product(random_string('product'),
                              random_string('product'),
                              {:attributes => {:support_level => 'mysla'},
                               :owner => @owner2['key']})
      p = create_pool_and_subscription(@owner2['key'], product.id)
      consumer = @user2.register(
          random_string('systempurpose'), :system, nil, {}, nil, @owner2['key'], [], [], nil, [],
          nil, [], nil, nil, nil, nil, nil, 0, nil, nil, nil, nil, nil)
      status = @user2.get_purpose_compliance(consumer['uuid'])
      @user2.consume_pool(p.id, params={:uuid=>consumer.uuid})
      status = @user2.get_purpose_compliance(consumer['uuid'])
      expect(status['status']).to eq('not specified')
      expect(status['compliantSLA']).to be_empty
  end

  it 'should be not specified for any usage when consumer has null usage' do
      product = create_product(random_string('product'),
                              random_string('product'),
                              {:attributes => {:usage => 'myusage'},
                               :owner => @owner2['key']})
      p = create_pool_and_subscription(@owner2['key'], product.id)
      consumer = @user2.register(
          random_string('systempurpose'), :system, nil, {}, nil, @owner2['key'], [], [], nil, [],
          nil, [], nil, nil, nil, nil, nil, 0, nil, nil, nil, nil, nil)
      status = @user2.get_purpose_compliance(consumer['uuid'])
      @user2.consume_pool(p.id, params={:uuid=>consumer.uuid})
      status = @user2.get_purpose_compliance(consumer['uuid'])
      expect(status['status']).to eq('not specified')
      expect(status['compliantUsage']).to be_empty
  end

  it 'should be not specified for any role when consumer has null role' do
      product = create_product(random_string('product'),
                              random_string('product'),
                              {:attributes => {:roles => 'myrole'},
                               :owner => @owner2['key']})
      p = create_pool_and_subscription(@owner2['key'], product.id)
      consumer = @user2.register(
          random_string('systempurpose'), :system, nil, {}, nil, @owner2['key'], [], [], nil, [],
          nil, [], nil, nil, nil, nil, nil, 0, nil, nil, nil, nil, nil)
      status = @user2.get_purpose_compliance(consumer['uuid'])
      @user2.consume_pool(p.id, params={:uuid=>consumer.uuid})
      status = @user2.get_purpose_compliance(consumer['uuid'])
      expect(status['status']).to eq('not specified')
      expect(status['compliantRole']).to be_empty
  end

  it 'should be not specified for any addons when consumer has null addons' do
      product = create_product(random_string('product'),
                              random_string('product'),
                              {:attributes => {:addons => 'myaddon,myotheraddon'},
                               :owner => @owner2['key']})
      p = create_pool_and_subscription(@owner2['key'], product.id)
      consumer = @user2.register(
          random_string('systempurpose'), :system, nil, {}, nil, @owner2['key'], [], [], nil, [],
          nil, [], nil, nil, nil, nil, nil, 0, nil, nil, nil, nil, nil)
      status = @user2.get_purpose_compliance(consumer['uuid'])
      @user2.consume_pool(p.id, params={:uuid=>consumer.uuid})
      status = @user2.get_purpose_compliance(consumer['uuid'])
      expect(status['status']).to eq('not specified')
      expect(status['compliantAddOns']).to be_empty
  end

  it 'should be mismatched for unsatisfied usage' do
      consumer = @user2.register(
          random_string('systempurpose'), :system, nil, {}, nil, @owner2['key'], [], [], nil, [],
          nil, [], nil, nil, nil, nil, nil, 0, nil, nil, nil, 'taylor', nil)
      status = @user2.get_purpose_compliance(consumer['uuid'])
      status['status'].should == 'mismatched'
      status['nonCompliantUsage'].should == 'taylor'
      status.reasons.size.should == 1
      status.reasons.include?(
          'The requested usage preference "taylor" is not provided by a currently consumed subscription.'
          ).should == true
  end

  it 'should change to matched after satisfying usage' do
      product = create_product(random_string('product'),
                              random_string('product'),
                              {:attributes => {:usage => 'myusage'},
                               :owner => @owner2['key']})
      p = create_pool_and_subscription(@owner2['key'], product.id)
      consumer = @user2.register(
          random_string('systempurpose'), :system, nil, {}, nil, @owner2['key'], [], [], nil, [],
          nil, [], nil, nil, nil, nil, nil, 0, nil, nil, nil, 'myusage', nil)
      status = @user2.get_purpose_compliance(consumer['uuid'])
      status['status'].should == 'mismatched'
      status['nonCompliantUsage'].include?('myusage').should == true

      @user2.consume_pool(p.id, params={:uuid=>consumer.uuid})
      status = @user2.get_purpose_compliance(consumer['uuid'])
      status['status'].should == 'matched'
      status['compliantUsage']['myusage'][0]['pool']['id'].should == p.id
  end

  it 'should be mismatched for unsatisfied addon' do
      consumer = @user2.register(
          random_string('systempurpose'), :system, nil, {}, nil, @owner2['key'], [], [], nil, [],
          nil, [], nil, nil, nil, nil, nil, 0, nil, nil, nil, nil, ['addon1', 'addon2'])
      status = @user2.get_purpose_compliance(consumer['uuid'])
      status['status'].should == 'mismatched'
      status['nonCompliantAddOns'].include?('addon1').should == true
      status['nonCompliantAddOns'].include?('addon2').should == true
      status.reasons.size.should == 2
      status.reasons.include?(
          'The requested add-on "addon1" is not provided by a currently consumed subscription.'
          ).should == true
      status.reasons.include?(
          'The requested add-on "addon2" is not provided by a currently consumed subscription.'
          ).should == true
  end

  it 'should change to matched after satisfying all addons' do
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
      status['status'].should == 'mismatched'
      status['nonCompliantAddOns'].include?('addon1').should == true
      status['nonCompliantAddOns'].include?('addon2').should == true

      @user2.consume_pool(p1.id, params={:uuid=>consumer.uuid})
      status = @user2.get_purpose_compliance(consumer['uuid'])
      status['status'].should == 'mismatched'
      status['nonCompliantAddOns'].size.should == 1
      status['nonCompliantAddOns'].should == ['addon2']
      status['compliantAddOns']['addon1'][0]['pool']['id'].should == p1.id

      @user2.consume_pool(p2.id, params={:uuid=>consumer.uuid})
      status = @user2.get_purpose_compliance(consumer['uuid'])
      status['status'].should == 'matched'
      status['nonCompliantAddOns'].size.should == 0
      status['compliantAddOns']['addon1'][0]['pool']['id'].should == p1.id
      status['compliantAddOns']['addon2'][0]['pool']['id'].should == p2.id
  end

  it 'should be mismatched for unsatisfied sla' do
      product = create_product(random_string('product'),
                              random_string('product'),
                              {:attributes => {:support_level => 'mysla'},
                               :owner => @owner2['key']})
      create_pool_and_subscription(@owner2['key'], product.id)
      consumer = @user2.register(
          random_string('systempurpose'), :system, nil, {}, nil, @owner2['key'], [], [], nil, [],
          nil, [], nil, nil, nil, nil, nil, 0, nil, 'mysla', nil, nil, nil)
      status = @user2.get_purpose_compliance(consumer['uuid'])
      status['status'].should == 'mismatched'
      status['nonCompliantSLA'].include?('mysla').should == true
      status.reasons.size.should == 1
      status.reasons.include?(
          'The service level preference "mysla" is not provided by a currently consumed subscription.'
          ).should == true

      # should not change for another SLA
      product = create_product(random_string('product'),
                              random_string('product'),
                              {:attributes => {:support_level => 'anothersla'},
                               :owner => @owner2['key']})
      p = create_pool_and_subscription(@owner2['key'], product.id)
      @user2.consume_pool(p.id, params={:uuid=>consumer.uuid})
      status = @user2.get_purpose_compliance(consumer['uuid'])
      status['status'].should == 'mismatched'
      status['nonCompliantSLA'].include?('mysla').should == true
      puts status['nonCompliantSLA']
      status.reasons.size.should == 1
      status['reasons'].include?(
          'The service level preference "mysla" is not provided by a currently consumed subscription.'
          ).should == true
  end

  it 'should change to matched after satisfying sla' do
      product = create_product(random_string('product'),
                              random_string('product'),
                              {:attributes => {:support_level => 'mysla'},
                               :owner => @owner2['key']})
      p = create_pool_and_subscription(@owner2['key'], product.id)
      consumer = @user2.register(
          random_string('systempurpose'), :system, nil, {}, nil, @owner2['key'], [], [], nil, [],
          nil, [], nil, nil, nil, nil, nil, 0, nil, 'mysla', nil, nil, nil)
      status = @user2.get_purpose_compliance(consumer['uuid'])
      status['status'].should == 'mismatched'
      status['nonCompliantSLA'].include?('mysla').should == true

      @user2.consume_pool(p.id, params={:uuid=>consumer.uuid})
      status = @user2.get_purpose_compliance(consumer['uuid'])
      status['status'].should == 'matched'
      status['compliantSLA']['mysla'][0]['pool']['id'].should == p.id
  end

  it 'should be matched for mixed SLAs' do
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
      status['status'].should == 'matched'
      status['compliantSLA']['mysla'][0]['pool']['id'].should == p.id
      status['reasons'].size.should == 0
  end

  it 'should be matched for mixed usages' do
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
      status['status'].should == 'matched'
      status['compliantUsage']['myusage'][0]['pool']['id'].should == p.id
      status['reasons'].size.should == 0
  end

  it 'should change to mismatched after revoking pools' do
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
      status['status'].should == 'matched'
      status['compliantUsage']['myusage'][0]['pool']['id'].should == p.id

      @user2.revoke_all_entitlements(consumer.uuid)
      status = @user2.get_purpose_compliance(consumer['uuid'])
      status['status'].should == 'mismatched'
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
                             {:attributes => {:support_level => 'basic', :support_type => 'basic_support'},
                              :owner => @owner2['key']})
      product2 = create_product(random_string('product'),
                                random_string('product'),
                                {:attributes => {:support_level => 'premium', :support_type => 'premium_support'},
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
          ['add-on 1', 'add-on 2'],
          nil,
          nil,
          nil,
          nil,
          'basic_support')

        consumer['serviceLevel'] == 'basic'
        expect(consumer['role']).to eq('role')
        expect(consumer['usage']).to eq('u-sage')
        expect(consumer['serviceType']).to eq('basic_support')

        add_ons = consumer['addOns']
        expect(add_ons.size).to eq(2)
        add_ons.should include('add-on 1')
        add_ons.should include('add-on 2')

        update_args = {
            :uuid => consumer['uuid'],
            :serviceLevel => 'premium',
            :role => 'updatedrole',
            :usage => 'updatedusage',
            :addOns => ['add-on 2', 'add-on 4'],
            :serviceType => 'updatedsupport'
        }

        @user2.update_consumer(update_args)
        consumer = @user2.get_consumer(consumer['uuid'])
      expect(consumer['serviceLevel']).to eq('premium')
      expect(consumer['usage']).to eq('updatedusage')
      expect(consumer['role']).to eq('updatedrole')
      expect(consumer['serviceType']).to eq('updatedsupport')

      add_ons = consumer['addOns']
      expect(add_ons.size).to eq(2)
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
          ['add-on 1', 'add-on 2'],
          nil,
          nil,
          nil,
          nil,
          'basic_support')

      consumer['serviceLevel'] == 'basic'
      expect(consumer['role']).to eq('role')
      expect(consumer['usage']).to eq('u-sage')
      expect(consumer['serviceType']).to eq('basic_support')

      add_ons = consumer['addOns']
      add_ons.size.should == 2
      add_ons.should include('add-on 1')
      add_ons.should include('add-on 2')

      update_args = {
          :uuid => consumer['uuid'],
          :serviceLevel => '',
          :role => '',
          :usage => '',
          :addOns => [],
          :serviceType => ''
      }

      @user2.update_consumer(update_args)
      consumer = @user2.get_consumer(consumer['uuid'])
      consumer['serviceLevel'].should == ''
      expect(consumer['usage']).to eq('')
      expect(consumer['role']).to eq('')
      expect(consumer['serviceType']).to eq('')

      add_ons = consumer['addOns']
      add_ons.size.should == 0
  end

  it 'should be not specified for any service type when consumer has null service type' do
    product = create_product(random_string('product'),
                             random_string('product'),
                             {:attributes => {:support_type => 'test_support'},
                              :owner => @owner2['key']})

    p = create_pool_and_subscription(@owner2['key'], product.id)

    consumer = @user2.register(
        random_string('systempurpose'), :system, nil, {}, nil, @owner2['key'], [], [], nil, [],
        nil, [], nil, nil, nil, nil, nil, 0, nil, nil, nil, nil, nil, nil, nil, nil, nil, nil)

    @user2.consume_pool(p.id, params={:uuid=>consumer.uuid})

    status = @user2.get_purpose_compliance(consumer['uuid'])
    expect(status['status']).to eq('not specified')
    expect(status['compliantServiceType']).to be_empty
  end

  it 'should be mismatched for unsatisfied service type' do
    consumer = @user2.register(
        random_string('systempurpose'), :system, nil, {}, nil, @owner2['key'], [], [], nil, [],
        nil, [], nil, nil, nil, nil, nil, 0, nil, nil, nil, nil, nil, nil, nil, nil, nil, "test_service_type")
    status = @user2.get_purpose_compliance(consumer['uuid'])
    expect(status['status']).to eq('mismatched')
    expect(status['nonCompliantServiceType']).to eq('test_service_type')
    expect(status.reasons.size).to eq(1)
    expect(status.reasons.include?(
        'The requested service type preference "test_service_type" is not provided by a currently consumed subscription.'
    )).to eq(true)
  end

  it 'should change to matched after satisfying service type' do
    product = create_product(random_string('product'),
                             random_string('product'),
                             {:attributes => {:support_type => 'test_service_type'},
                              :owner => @owner2['key']})
    p = create_pool_and_subscription(@owner2['key'], product.id)
    consumer = @user2.register(
        random_string('systempurpose'), :system, nil, {}, nil, @owner2['key'], [], [], nil, [],
        nil, [], nil, nil, nil, nil, nil, 0, nil, nil, nil, nil, nil, nil, nil, nil, nil, 'test_service_type')

    status = @user2.get_purpose_compliance(consumer['uuid'])
    expect( status['status']).to eq('mismatched')
    expect( status['nonCompliantServiceType'].include?('test_service_type')).to eq(true)

    @user2.consume_pool(p.id, params={:uuid=>consumer.uuid})
    status = @user2.get_purpose_compliance(consumer['uuid'])
    expect( status['status']).to eq('matched')
    expect( status['compliantServiceType']['test_service_type'][0]['pool']['id']).to eq(p.id)
  end

  it 'should be matched for mixed service type' do
    product = create_product(random_string('product'),
                             random_string('product'),
                             {:attributes => {:support_type => 'L1'},
                              :owner => @owner2['key']})
    p = create_pool_and_subscription(@owner2['key'], product.id)
    another_product = create_product(random_string('product'),
                                     random_string('product'),
                                     {:attributes => {:support_type => 'L2'},
                                      :owner => @owner2['key']})
    another_p = create_pool_and_subscription(@owner2['key'], another_product.id)

    consumer = @user2.register(
        random_string('systempurpose'), :system, nil, {}, nil, @owner2['key'], [], [], nil, [],
        nil, [], nil, nil, nil, nil, nil, 0, nil, nil, nil, nil, nil, nil, nil, nil, nil, "L1")

    @user2.consume_pool(p.id, params={:uuid=>consumer.uuid})
    @user2.consume_pool(another_p.id, params={:uuid=>consumer.uuid})

    status = @user2.get_purpose_compliance(consumer['uuid'])

    expect(status['status']).to eq('matched')
    expect(status['compliantServiceType']['L1'][0]['pool']['id']).to eq(p.id)
    expect(status['reasons'].size).to eq(0)
  end

end
