# -*- coding: utf-8 -*-
require 'spec_helper'
require 'candlepin_scenarios'
require 'rexml/document'

describe 'Consumer Resource' do

  include CandlepinMethods
  include AttributeHelper

  before(:each) do
    @owner1 = create_owner random_string('test_owner1')
    @username1 = random_string("user1")
    @consumername1 = random_string("consumer1")
    @user1 = user_client(@owner1, @username1)
    @consumer1 = consumer_client(@user1, @consumername1)

    @owner2 = create_owner random_string('test_owner2')
    @username2 = random_string("user2")
    @user2 = user_client(@owner2, @username2)
    @consumer2 = consumer_client(@user2, random_string("consumer2"))
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
