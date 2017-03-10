require 'spec_helper'
require 'candlepin_scenarios'

describe 'Entitlement Resource' do

  include CandlepinMethods

  before do
    @owner = create_owner random_string 'test_owner'
    @monitoring_prod = create_product(nil, 'monitoring')
    @virt_prod= create_product(nil, 'virtualization')

    #entitle owner for the virt and monitoring products.
    @cp.create_subscription(@owner['key'], @monitoring_prod.id, 6)
    @cp.create_subscription(@owner['key'], @virt_prod.id, 6)

    @cp.refresh_pools(@owner['key'])

    #create consumer
    user = user_client(@owner, random_string('billy'))
    @system = consumer_client(user, 'system6')

    #separate for quantity adjust tests - here for cleanup
    @qowner = create_owner random_string 'test_owner'
  end

  it 'should receive paged data back when requested' do
    id_list = []
    (1..4).each do |i|
      prod = create_product()
      @cp.create_subscription(@owner['key'], prod.id, 6)
      id_list.push(prod.id)
    end
    @cp.refresh_pools(@owner['key'])

    id_list.each do |id|
      @system.consume_product(id)
    end

    entitlements = @system.list_entitlements({:page => 1, :per_page => 2, :sort_by => "id", :order => "asc"})
    entitlements.length.should == 2
    (entitlements[0].id <=> entitlements[1].id).should == -1
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

  it 'does not allow a consumer to view entitlements from a different consumer' do
    # Given
    bad_owner = create_owner random_string 'baddie'
    bad_user = user_client(bad_owner, 'bad_dude')
    system2 = consumer_client(bad_user, 'wrong_system')

    # When
    lambda do
      system2.list_entitlements(:uuid => @system.uuid)

      # Then
    end.should raise_exception(RestClient::ResourceNotFound)
  end

  it 'should allow consumer to change entitlement quantity' do
    owner_client = user_client(@qowner, random_string('owner'))
    cp_client = consumer_client(owner_client, random_string('consumer'), :system)
    prod = create_product(random_string('product'), random_string('product'),
                          :attributes => { :'multi-entitlement' => 'yes'})
    subs = @cp.create_subscription(@qowner['key'], prod.id, 10)
    @cp.refresh_pools(@qowner['key'])
    pool = cp_client.list_pools({:owner => @qowner['id']}).first
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
                          :attributes => { :'multi-entitlement' => 'yes'})
    subs = @cp.create_subscription(@qowner['key'], prod.id, 10)
    @cp.refresh_pools(@qowner['key'])
    pool = cp_client.list_pools({:owner => @qowner['id']}).first
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
    prod = create_product(random_string('product'), random_string('product'))
    subs = @cp.create_subscription(@qowner['key'], prod.id, 10)
    @cp.refresh_pools(@qowner['key'])
    pool = cp_client.list_pools({:owner => @qowner['id']}).first
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
    pool = @cp.create_subscription(@owner['key'], prod.id, 50)
    @cp.refresh_pools(@owner['key'])
    pool = owner_client.list_pools({:owner => @owner['id'], :product =>prod.id}).first

    t1 = Thread.new{register_and_consume(pool, "system", 5)}
    t2 = Thread.new{register_and_consume(pool, "candlepin", 7)}
    t3 = Thread.new{register_and_consume(pool, "system", 6)}
    t4 = Thread.new{register_and_consume(pool, "candlepin", 11)}
    t1.join
    t2.join
    t3.join
    t4.join

    consumed_pool = owner_client.get_pool(pool.id)
    consumed_pool['consumed'].should == 29
    consumed_pool['exported'].should == 18

  end

  it 'should not allow over consumption in pool' do
    owner_client = user_client(@owner, random_string('owner'))
    prod = create_product(random_string('product'), random_string('product'),
                          {:attributes => { :'multi-entitlement' => 'yes'},
                           :owner => @owner['key']})
    pool = @cp.create_subscription(@owner['key'], prod.id, 3)
    @cp.refresh_pools(@owner['key'])
    pool = owner_client.list_pools({:owner => @owner['id'], :product =>prod.id}).first

    t1 = Thread.new{register_and_consume(pool, "system", 1)}
    t2 = Thread.new{register_and_consume(pool, "candlepin", 1)}
    t3 = Thread.new{register_and_consume(pool, "system", 1)}
    t4 = Thread.new{register_and_consume(pool, "candlepin", 1)}
    t5 = Thread.new{register_and_consume(pool, "candlepin", 1)}
    t6 = Thread.new{register_and_consume(pool, "candlepin", 1)}
    t1.join
    t2.join
    t3.join
    t4.join
    t5.join
    t6.join

    consumed_pool = owner_client.get_pool(pool.id)
    consumed_pool['consumed'].should == 3

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

  it 'should end at zero quantity consumed when all consumers are unregistered' do
    owner_client = user_client(@owner, random_string('owner'))
    prod = create_product(random_string('product'), random_string('product'),
                          {:attributes => { :'multi-entitlement' => 'yes'},
                           :owner => @owner['key']})
    pool = @cp.create_subscription(@owner['key'], prod.id, 3)
    @cp.refresh_pools(@owner['key'])
    pool = owner_client.list_pools({:owner => @owner['id'], :product =>prod.id}).first

    t1 = Thread.new{register_consume_unregister(pool, "system", 1)}
    t2 = Thread.new{register_consume_unregister(pool, "candlepin", 1)}
    t3 = Thread.new{register_consume_unregister(pool, "system", 1)}
    t4 = Thread.new{register_consume_unregister(pool, "candlepin", 1)}
    t5 = Thread.new{register_consume_unregister(pool, "system", 1)}
    t6 = Thread.new{register_consume_unregister(pool, "candlepin", 1)}
    t1.join
    t2.join
    t3.join
    t4.join
    t5.join
    t6.join

    consumed_pool = owner_client.get_pool(pool.id)
    consumed_pool['consumed'].should == 0
    consumed_pool['exported'].should == 0

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


end
