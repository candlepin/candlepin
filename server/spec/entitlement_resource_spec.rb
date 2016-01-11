require 'spec_helper'
require 'candlepin_scenarios'

describe 'Entitlement Resource' do

  include CandlepinMethods

  before do
    @owner = create_owner random_string 'test_owner'
    @monitoring_prod = create_product(nil, 'monitoring',
      :attributes => { 'variant' => "Satellite Starter Pack" })
    @virt_prod= create_product(nil, 'virtualization')

    #entitle owner for the virt and monitoring products.
    create_pool_and_subscription(@owner['key'], @monitoring_prod.id, 6,
				 [], '', '', '', nil, nil, true)
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
    ents.should have(1).things
  end

  it 'should not re-calculate attributes when fetching entitlements' do
    @system.consume_product(@virt_prod.id)
    ents = @cp.list_ents_via_entitlements_resource(:matches => "virtualization")
    ents.should have(1).things
    ents[0].pool.calculatedAttributes.should be_nil
  end

  it 'can filter all entitlements by product attribute' do
    @system.consume_product(@monitoring_prod.id)
    @system.consume_product(@virt_prod.id)

    ents = @cp.list_ents_via_entitlements_resource(
      :attr_filters => { "variant" => "Satellite Starter Pack" })
    ents.should have(1).things

    found_attr = false
    ents[0].pool.productAttributes.each do |attr|
      if attr["name"] == 'variant' && attr["value"] == "Satellite Starter Pack"
        found_attr = true
        break
      end
    end
    found_attr.should == true
  end

  it 'can filter consumer entitlements by product attribute' do
    @system.consume_product(@monitoring_prod.id)
    @system.consume_product(@virt_prod.id)
    @cp.list_ents_via_entitlements_resource(:consumer_uuid => @system.uuid).should have(2).things

    ents = @cp.list_ents_via_entitlements_resource(:consumer_uuid => @system.uuid,
      :attr_filters => { "variant" => "Satellite Starter Pack" })
    ents.should have(1).things

    found_attr = false
    ents[0].pool.productAttributes.each do |attr|
      if attr["name"] == 'variant' && attr["value"] == "Satellite Starter Pack"
        found_attr = true
        break
      end
    end
    found_attr.should == true
  end

  it 'can filter consumer entitlements by using matches param' do
    @system.consume_product(@monitoring_prod.id)
    @system.consume_product(@virt_prod.id)
    @cp.list_ents_via_entitlements_resource(:consumer_uuid => @system.uuid).should have(2).things

    ents = @cp.list_ents_via_entitlements_resource(:consumer_uuid => @system.uuid,
      :matches => "virtualization")
    ents.should have(1).things
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

end
