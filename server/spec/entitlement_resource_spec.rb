require 'spec_helper'
require 'candlepin_scenarios'

describe 'Entitlement Resource' do
  include CandlepinMethods
  include AttributeHelper

  before do
    @owner = create_owner random_string 'test_owner'
    @monitoring_prod = create_product(nil, 'monitoring',
      :attributes => { 'variant' => "Satellite Starter Pack" })
    @virt_prod= create_product(nil, 'virtualization')

    #entitle owner for the virt and monitoring products.
    create_pool_and_subscription(@owner['key'], @monitoring_prod.id, 6, [], '', '', '', nil, nil, true)
    create_pool_and_subscription(@owner['key'], @virt_prod.id, 6)

    #create consumer
    user = user_client(@owner, random_string('billy'))
    @system = consumer_client(user, 'system6')

    #separate for quantity adjust tests - here for cleanup
    @qowner = create_owner random_string 'test_owner'
  end

  it 'can filter all entitlements by using matches param' do
    @system.consume_product(@monitoring_prod.id)
    @system.consume_product(@virt_prod.id)

    ents = @cp.list_ents_via_entitlements_resource(:matches => "virtualization")
    ents.length.should eq(1)
  end

  it 'should not re-calculate attributes when fetching entitlements' do
    @system.consume_product(@virt_prod.id)
    ents = @cp.list_ents_via_entitlements_resource(:matches => "virtualization")
    ents.length.should eq(1)
    ents[0].pool.calculatedAttributes.should be_nil
  end

  it 'can filter all entitlements by product attribute' do
    @system.consume_product(@monitoring_prod.id)
    @system.consume_product(@virt_prod.id)

    ents = @cp.list_ents_via_entitlements_resource(
      :attr_filters => { "variant" => "Satellite Starter Pack" })
    ents.length.should eq(1)

    variant = get_attribute_value(ents[0].pool.productAttributes, "variant")
    expect(variant).to eq("Satellite Starter Pack")
  end

  it 'can filter consumer entitlements by product attribute' do
    @system.consume_product(@monitoring_prod.id)
    @system.consume_product(@virt_prod.id)
    @cp.list_entitlements(:uuid => @system.uuid).length.should eq(2)

    ents = @cp.list_entitlements(:uuid => @system.uuid,
      :attr_filters => { "variant" => "Satellite Starter Pack" })
    ents.length.should eq(1)

    variant = get_attribute_value(ents[0].pool.productAttributes, "variant")
    expect(variant).to eq("Satellite Starter Pack")
  end

  it 'can filter consumer entitlements by using matches param' do
    @system.consume_product(@monitoring_prod.id)
    @system.consume_product(@virt_prod.id)
    @cp.list_entitlements(:uuid => @system.uuid).length.should eq(2)

    ents = @cp.list_entitlements(:uuid => @system.uuid,
      :matches => "virtualization")
    ents.length.should eq(1)
  end

  it 'should allow entitlement certificate regeneration based on product id' do
    @system.consume_product(@monitoring_prod.id)
    old_ent = @system.list_certificate_serials()[0]
    @cp.regenerate_entitlement_certificates_for_product(@monitoring_prod.id)
    new_ent = @system.list_certificate_serials()[0]
    old_ent.serial.should_not == new_ent.serial
  end

  it 'allows filtering certificates by serial number' do
    @system.consume_product(@monitoring_prod.id)
    @system.consume_product(@virt_prod.id)

    entitlements = @system.list_entitlements()

    # filter out entitlements for different products
    entitlements = entitlements.select do |ent|
      @system.get_pool(ent['pool']['id'])['productId'] == @monitoring_prod.id
    end

    # Just grab the cert serial ids
    entitlements.collect! do |ent|
      ent['certificates'].collect do |cert|
        cert['serial']['id']
      end
    end

    serials = entitlements
    serials.flatten!

    @system.list_certificates(serials).length.should == 1
  end

  it 'allows listing certificates by serial numbers' do
    @system.consume_product(@monitoring_prod.id)
    @system.list_certificate_serials.length.should == 1
  end

  it 'should allow consumer to change entitlement quantity' do
    owner_client = user_client(@qowner, random_string('owner'))
    cp_client = consumer_client(owner_client, random_string('consumer'), :system)
    prod = create_product(random_string('product'), random_string('product'),
      {:attributes => { :'multi-entitlement' => 'yes'}, :owner => @qowner['key']})
    pool = create_pool_and_subscription(@qowner['key'], prod.id, 10)
    entitlement = cp_client.consume_pool(pool.id, {:quantity => 3}).first
    ent_cert_ser = entitlement['certificates'].first['serial']['id']
    entitlement2 = cp_client.consume_pool(pool.id, {:quantity => 2}).first
    entitlement.quantity.should == 3
    pool = cp_client.list_pools({:owner => @qowner['id']}).first
    pool.consumed.should == 5

    # change it higher
    cp_client.update_entitlement({:id => entitlement.id, :quantity => 5})
    adj_entitlement = cp_client.get_entitlement(entitlement.id)
    adj_cert_ser_1 = adj_entitlement['certificates'].first['serial']['id']
    adj_pool = cp_client.list_pools({:owner => @qowner['id']}).first
    adj_entitlement.quantity.should == 5
    adj_pool.consumed.should == 7
    adj_cert_ser_1.should_not == ent_cert_ser

    # change it lower
    cp_client.update_entitlement({:id => entitlement.id, :quantity => 2})
    adj_entitlement = cp_client.get_entitlement(entitlement.id)
    adj_cert_ser_2 = adj_entitlement['certificates'].first['serial']['id']
    adj_pool = cp_client.list_pools({:owner => @qowner['id']}).first
    adj_entitlement.quantity.should == 2
    adj_pool.consumed.should == 4
    adj_cert_ser_2.should_not == ent_cert_ser
    adj_cert_ser_2.should_not == adj_cert_ser_1
  end

  it 'should not allow consumer to change entitlement quantity out of bounds' do
    owner_client = user_client(@qowner, random_string('owner'))
    cp_client = consumer_client(owner_client, random_string('consumer'), :system)
    prod = create_product(random_string('product'), random_string('product'),
      {:attributes => { :'multi-entitlement' => 'yes'},
       :owner => @qowner['key']})
    pool = create_pool_and_subscription(@qowner['key'], prod.id, 10)
    entitlement = cp_client.consume_pool(pool.id, {:quantity => 3}).first
    entitlement2 = cp_client.consume_pool(pool.id, {:quantity => 2}).first
    entitlement.quantity.should == 3
    pool = cp_client.list_pools({:owner => @qowner['id']}).first
    pool.consumed.should == 5

    lambda do
      cp_client.update_entitlement({:id => entitlement.id, :quantity => 9})
    end.should raise_exception(RestClient::Forbidden)

    lambda do
      cp_client.update_entitlement({:id => entitlement.id, :quantity => -1})
    end.should raise_exception(RestClient::BadRequest)
  end

  it 'should not allow consumer to change entitlement quantity non-multi' do
    owner_client = user_client(@qowner, random_string('owner'))
    cp_client = consumer_client(owner_client, random_string('consumer'), :system)
    prod = create_product(random_string('product'), random_string('product'), {:owner => @qowner['key']})
    pool = create_pool_and_subscription(@qowner['key'], prod.id, 10)
    entitlement = cp_client.consume_pool(pool.id, {:quantity => 1}).first
    entitlement.quantity.should == 1
    pool = cp_client.list_pools({:owner => @qowner['id']}).first
    pool.consumed.should == 1

    lambda do
      cp_client.update_entitlement({:id => entitlement.id, :quantity => 2})
    end.should raise_exception(RestClient::Forbidden)
  end

  it 'should handle concurrent requests to pool and maintain quanities' do
    owner_client = user_client(@owner, random_string('owner'))
    prod = create_product(random_string('product'), random_string('product'),
      {:attributes => { :'multi-entitlement' => 'yes'},
       :owner => @owner['key']})
    pool = create_pool_and_subscription(@owner['key'], prod.id, 50)

    t1 = Thread.new{register_and_consume(pool, "system", 5)}
    t1a = Thread.new{register_unregister()}
    t2 = Thread.new{register_and_consume(pool, "candlepin", 7)}
    t2a = Thread.new{register_unregister()}
    t3 = Thread.new{register_and_consume(pool, "system", 6)}
    t3a = Thread.new{register_unregister()}
    t4 = Thread.new{register_and_consume(pool, "candlepin", 11)}
    t4a = Thread.new{register_unregister()}
    t1.join
    t1a.join
    t2.join
    t2a.join
    t3.join
    t3a.join
    t4.join
    t4a.join

    consumed_pool = owner_client.get_pool(pool.id)
    consumed_pool['consumed'].should == 29
    consumed_pool['exported'].should == 18

  end

  it 'should not allow over consumption in pool' do
    owner_client = user_client(@owner, random_string('owner'))
    prod = create_product(random_string('product'), random_string('product'),
      {:attributes => { :'multi-entitlement' => 'yes'},
       :owner => @owner['key']})
    pool = create_pool_and_subscription(@owner['key'], prod.id, 3)

    t1 = Thread.new{register_and_consume(pool, "system", 1)}
    t1a = Thread.new{register_unregister()}
    t2 = Thread.new{register_and_consume(pool, "candlepin", 1)}
    t2a = Thread.new{register_unregister()}
    t3 = Thread.new{register_and_consume(pool, "system", 1)}
    t3a = Thread.new{register_unregister()}
    t4 = Thread.new{register_and_consume(pool, "candlepin", 1)}
    t4a = Thread.new{register_unregister()}
    t5 = Thread.new{register_and_consume(pool, "candlepin", 1)}
    t5a = Thread.new{register_unregister()}
    t6 = Thread.new{register_and_consume(pool, "candlepin", 1)}
    t6a = Thread.new{register_unregister()}
    t1.join
    t1a.join
    t2.join
    t2a.join
    t3.join
    t3a.join
    t4.join
    t4a.join
    t5.join
    t5a.join
    t6.join
    t6a.join

    consumed_pool = owner_client.get_pool(pool.id)
    consumed_pool['consumed'].should == 3

  end

  it 'should end at zero quantity consumed when all consumers are unregistered' do
    owner_client = user_client(@owner, random_string('owner'))
    prod = create_product(random_string('product'), random_string('product'),
      {:attributes => { :'multi-entitlement' => 'yes'},
       :owner => @owner['key']})
    pool = create_pool_and_subscription(@owner['key'], prod.id, 3)

    t1 = Thread.new{register_consume_unregister(pool, "system", 1)}
    t1a = Thread.new{register_unregister()}
    t2 = Thread.new{register_consume_unregister(pool, "candlepin", 1)}
    t2a = Thread.new{register_unregister()}
    t3 = Thread.new{register_consume_unregister(pool, "system", 1)}
    t3a = Thread.new{register_unregister()}
    t4 = Thread.new{register_consume_unregister(pool, "candlepin", 1)}
    t4a = Thread.new{register_unregister()}
    t5 = Thread.new{register_consume_unregister(pool, "system", 1)}
    t5a = Thread.new{register_unregister()}
    t6 = Thread.new{register_consume_unregister(pool, "candlepin", 1)}
    t6a = Thread.new{register_unregister()}
    t1.join
    t1a.join
    t2.join
    t2a.join
    t3.join
    t3a.join
    t4.join
    t4a.join
    t5.join
    t5a.join
    t6.join
    t6a.join

    consumed_pool = owner_client.get_pool(pool.id)
    consumed_pool['consumed'].should == 0
    consumed_pool['exported'].should == 0

  end

  it 'should handle concurrent binds on manifest consumer and maintain quanities' do
    owner_client = user_client(@owner, random_string('owner'))
    prod = create_product(random_string('product'), random_string('product'),
                          {:attributes => { :'multi-entitlement' => 'yes'},
                           :owner => @owner['key']})
    pool1 = create_pool_and_subscription(@owner['key'], prod.id, 50)
    pool2 = create_pool_and_subscription(@owner['key'], prod.id, 50)
    pool3 = create_pool_and_subscription(@owner['key'], prod.id, 50)
    pool4 = create_pool_and_subscription(@owner['key'], prod.id, 50)
    pool5 = create_pool_and_subscription(@owner['key'], prod.id, 50)

    cp_client = consumer_client(owner_client, random_string('consumer'), "candlepin")

    t1 = Thread.new{cp_client.consume_pool(pool1.id, {:quantity => 40})}
    t1a = Thread.new{register_unregister()}
    t2 = Thread.new{cp_client.consume_pool(pool2.id, {:quantity => 30})}
    t2a = Thread.new{register_unregister()}
    t3 = Thread.new{cp_client.consume_pool(pool3.id, {:quantity => 20})}
    t3a = Thread.new{register_unregister()}
    t4 = Thread.new{cp_client.consume_pool(pool4.id, {:quantity => 25})}
    t4a = Thread.new{register_unregister()}
    t5 = Thread.new{cp_client.consume_pool(pool5.id, {:quantity => 10})}
    t5a = Thread.new{register_unregister()}
    t1.join
    t1a.join
    t2.join
    t2a.join
    t3.join
    t3a.join
    t4.join
    t4a.join
    t5.join
    t5a.join

    consumer = cp_client.get_consumer()
    consumer['entitlementCount'].should == 125

  end

  it 'should handle concurrent binds and unbinds and maintain quanities' do
    owner_client = user_client(@owner, random_string('owner'))
    prod = create_product(random_string('product'), random_string('product'),
                          {:attributes => { :'multi-entitlement' => 'yes'},
                           :owner => @owner['key']})
    pool1 = create_pool_and_subscription(@owner['key'], prod.id, 50)
    pool2 = create_pool_and_subscription(@owner['key'], prod.id, 50)
    pool3 = create_pool_and_subscription(@owner['key'], prod.id, 50)
    pool4 = create_pool_and_subscription(@owner['key'], prod.id, 50)
    pool5 = create_pool_and_subscription(@owner['key'], prod.id, 50)

    cp_client = consumer_client(owner_client, random_string('consumer'), "candlepin")

    t1 = Thread.new{consume_delete_ent(pool1, cp_client, 40)}
    t1a = Thread.new{register_unregister()}
    t2 = Thread.new{consume_delete_ent(pool2, cp_client, 30)}
    t2a = Thread.new{register_unregister()}
    t3 = Thread.new{consume_delete_ent(pool3, cp_client, 20)}
    t3a = Thread.new{register_unregister()}
    t4 = Thread.new{consume_delete_ent(pool4, cp_client, 25)}
    t4a = Thread.new{register_unregister()}
    t5 = Thread.new{consume_delete_ent(pool5, cp_client, 10)}
    t5a = Thread.new{register_unregister()}
    t1.join
    t1a.join
    t2.join
    t2a.join
    t3.join
    t3a.join
    t4.join
    t4a.join
    t5.join
    t5a.join

    consumer = cp_client.get_consumer()
    consumer['entitlementCount'].should == 0

  end

  it 'should handle concurrent binds and pool deletes and maintain quanities' do
    owner_client = user_client(@owner, random_string('owner'))
    prod = create_product(random_string('product'), random_string('product'),
                          {:attributes => { :'multi-entitlement' => 'yes'},
                           :owner => @owner['key']})
    pool1 = create_pool_and_subscription(@owner['key'], prod.id, 50)
    pool2 = create_pool_and_subscription(@owner['key'], prod.id, 50)
    pool3 = create_pool_and_subscription(@owner['key'], prod.id, 50)
    pool4 = create_pool_and_subscription(@owner['key'], prod.id, 50)
    pool5 = create_pool_and_subscription(@owner['key'], prod.id, 50)

    cp_client = consumer_client(owner_client, random_string('consumer'), "candlepin")

    t1 = Thread.new{consume_delete_pool(pool1, cp_client, 40)}
    t1a = Thread.new{register_unregister()}
    t2 = Thread.new{consume_delete_pool(pool2, cp_client, 30)}
    t2a = Thread.new{register_unregister()}
    t3 = Thread.new{consume_delete_pool(pool3, cp_client, 20)}
    t3a = Thread.new{register_unregister()}
    t4 = Thread.new{consume_delete_pool(pool4, cp_client, 25)}
    t4a = Thread.new{register_unregister()}
    t5 = Thread.new{consume_delete_pool(pool5, cp_client, 10)}
    t5a = Thread.new{register_unregister()}
    t1.join
    t1a.join
    t2.join
    t2a.join
    t3.join
    t3a.join
    t4.join
    t4a.join
    t5.join
    t5a.join

    consumer = cp_client.get_consumer()
    consumer['entitlementCount'].should == 0

  end

  def register_consume_unregister(pool, consumer_type, quantity)
    user = user_client(@owner, random_string('user'))
    cp_client = consumer_client(user, random_string('consumer'), consumer_type)
    begin
        cp_client.consume_pool(pool.id, {:quantity => quantity})

    rescue RestClient::Forbidden => e
        # tests will run that try to over consume, this is expected
    end
    cp_client.unregister
  end

  def register_unregister()
    user = user_client(@owner, random_string('user'))
    cp_client = consumer_client(user, random_string('consumer'), "system")
    cp_client.unregister
  end

  def consume_delete_ent(pool, consumer_client, quantity)
    user = user_client(@owner, random_string('user'))
    begin
      ent = consumer_client.consume_pool(pool.id, {:quantity => quantity}).first
      consumer_client.unbind_entitlement(ent.id)
    rescue RestClient::Forbidden => e
      # tests will run that try to over consume, this is expected
    end
  end

  def consume_delete_pool(pool, consumer_client, quantity)
    user = user_client(@owner, random_string('user'))
    begin
      ent = consumer_client.consume_pool(pool.id, {:quantity => quantity}).first
      @cp.delete_pool(pool.id)
    rescue RestClient::Forbidden => e
      # tests will run that try to over consume, this is expected
    end
  end

  def register_and_consume(pool, consumer_type, quantity)
    user = user_client(@owner, random_string('user'))
    cp_client = consumer_client(user, random_string('consumer'), consumer_type)
    begin
      cp_client.consume_pool(pool.id, {:quantity => quantity})
    rescue RestClient::Forbidden => e
      # tests will run that try to over consume, this is expected
    end
  end
end
