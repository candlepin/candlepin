# -*- coding: utf-8 -*-
require 'spec_helper'
require 'candlepin_scenarios'
require 'rexml/document'

describe 'Consumer Dev Resource' do

  include CandlepinMethods

  before(:each) do
    @owner = create_owner random_string('test_owner')
    @username = random_string("user")
    @consumername = random_string("dev_consumer")
    @user = user_client(@owner, @username)

    # active subscription to allow this all to work
    active_prod = create_product()
    @active_sub = create_pool_and_subscription(@owner['key'], active_prod.id, 10)
    pools = @cp.list_owner_pools(@owner['key'])
    pools.length.should eq(1)

    @p_product1 = create_product("p_product_1",
      "Provided Product 1")
    @p_product2 = create_product("p_product",
      "Provided Product 2")
    @dev_product = create_product("dev_product",
                                  "Dev Product",
                                  {:attributes => { :expires_after => "60"}, :providedProducts =>
                                    [@p_product1["id"], @p_product2["id"]]})
    @dev_product_2 = create_product("2nd_dev_product",
                                  "Dev Product",
                                  {:attributes => { :expires_after => "60"},  :providedProducts =>
                                    [@p_product1["id"], @p_product2["id"]]})

    @consumer = consumer_client(@user, @consumername, :system, 'dev_user', facts = {:dev_sku => "dev_product"})
    installed = [
        {'productId' => @p_product1.id, 'productName' => @p_product1.name},
        {'productId' => @p_product2.id, 'productName' => @p_product2.name}]
    @consumer.update_consumer({:installedProducts => installed})
  end

  it 'should create entitlement to newly created dev pool' do
    skip("candlepin running in standalone mode") if not is_hosted?
    auto_attach_and_verify_dev_product("dev_product")
  end

  it 'should create new entitlement when dev pool already exists' do
    skip("candlepin running in standalone mode") if not is_hosted?
    initial_ent = auto_attach_and_verify_dev_product("dev_product")
    auto_attach_and_verify_dev_product("dev_product", initial_ent.id)
  end

  it 'should create new entitlement when dev_sku attribute changes' do
    skip("candlepin running in standalone mode") if not is_hosted?
    ent = auto_attach_and_verify_dev_product("dev_product")
    @consumer.update_consumer({:facts => {:dev_sku => "2nd_dev_product"}})
    auto_attach_and_verify_dev_product("2nd_dev_product", ent.id)
  end

  def auto_attach_and_verify_dev_product(expected_product_id, old_ent_id=nil)
    @consumer.consume_product()
    entitlements = @consumer.list_entitlements()
    entitlements.length.should eq(1)
    unless old_ent_id.nil?
      entitlements[0].id.should_not eq(old_ent_id)
    end
    new_pool = entitlements[0].pool
    new_pool.type.should eq("DEVELOPMENT")
    new_pool.productId.should eq(expected_product_id)
    new_pool.providedProducts.length.should eq(2)
    return entitlements[0]
  end

  it 'should allow sub-man-gui process for auto-bind' do
    skip("candlepin running in standalone mode") if not is_hosted?

    pools = @consumer.autobind_dryrun()
    pools.length.should eq(1)
    ents = @consumer.consume_pool(pools[0].pool.id)
    ents.length.should eq(1)
    ent_pool = ents[0].pool
    ent_pool.type.should eq("DEVELOPMENT")
    ent_pool.productId.should eq("dev_product")
    ent_pool.providedProducts.length.should eq(2)
    ent_pool.id.should eq(pools[0].pool.id)
  end

it 'should not allow entitlement for consumer past expiration' do
    skip("candlepin running in standalone mode") if not is_hosted?
    created_date = '2015-05-09T13:23:55+0000'
    consumer = @user.register(random_string('system'), type=:system, nil, facts = {:dev_sku => "dev_product"}, @username,
              @owner['key'], [], [], nil, [], nil, [], created_date)
    consumer_client = Candlepin.new(nil, nil, consumer['idCert']['cert'], consumer['idCert']['key'])
    expected_error = "Unable to attach subscription for the product \"dev_product\": Subscriptions for dev_product expired on:"
    begin
        consumer_client.consume_product()
        fail("Expected Forbidden since consumer is older than product expiry")
    rescue RestClient::Forbidden => e
      message = JSON.parse(e.http_body)['displayMessage']
      message.should start_with(expected_error)
    end
  end

end
