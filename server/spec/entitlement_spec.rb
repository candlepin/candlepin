require 'spec_helper'
require 'candlepin_scenarios'

describe 'Entitlements' do
  include CandlepinMethods
  include AttributeHelper

  before(:each) do
    @owner = create_owner random_string 'test_owner'
    @monitoring = create_product(nil, random_string('monitoring'))
    @virt = create_product(nil, random_string('virtualization_host'),
      {:attributes => {"multi-entitlement" => "yes"}})
    @super_awesome = create_product(nil, random_string('super_awesome'),
                                    :attributes => {
                                      'cpu.cpu_socket(s)' => 4,
                                      'variant' => "Satellite Starter Pack"
                                    })
    @virt_limit = create_product(nil, random_string('virt_limit'),
      {:attributes => {"virt_limit" => "10"}})

    @instance_based = create_product(nil, random_string('instance_based'),
                                    :attributes => { 'instance_multiplier' => 2,
                                        'multi-entitlement' => 'yes' })

    @ram_provided = create_product(nil, random_string("ram provided"), {})
    content = create_content({:metadata_expire => 6000,
                                  :required_tags => "TAG1,TAG2"})
    content2 = create_content({:metadata_expire => 6000,
                                      :required_tags => "TAG1,TAG2"})
    @cp.add_content_to_product(@owner['key'], @ram_provided.id, content.id)
    @cp.add_content_to_product(@owner['key'], @ram_provided.id, content2.id)

    @ram = @cp.create_product(@owner['key'], random_string("ram-pack"), random_string("RAM Limiting Package"), {
      :attributes => {"ram" => "4"}, :providedProducts => [@ram_provided.id] })

    #entitle owner for the virt and monitoring products.
    @cp.create_pool(@owner['key'], @virt.id, { :quantity => 20 })
    @cp.create_pool(@owner['key'], @monitoring.id, { :quantity => 4 })
    @cp.create_pool(@owner['key'], @super_awesome.id, { :quantity => 4 })
    @cp.create_pool(@owner['key'], @virt_limit.id, { :quantity => 5 })
    @cp.create_pool(@owner['key'], @instance_based.id, { :quantity => 10 })
    @cp.create_pool(@owner['key'], @ram.id, { :quantity => 4 })

    #create consumer
    @user = user_client(@owner, random_string('billy'))
    @system = consumer_client(@user, 'system6')
  end

  it 'should bypasses rules for "candlepin" consumers' do
    box = consumer_client(@user, 'random_box', :candlepin, nil, 'cpu.cpu_socket(s)' => 8)

    box.consume_product(@super_awesome.id)
    box.list_entitlements.length.should eq(1)
  end

  it 'should throw an error when filtering by a non-existant product ID' do
    lambda do
      @system.list_entitlements(:product => 'non_existant')
    end.should raise_exception(RestClient::BadRequest)
  end

  it 'should allow an entitlement to be consumed by product' do
    @system.consume_product @virt.id
    @system.list_entitlements.length.should eq(1)
  end

  it 'should allow an entitlement to be consumed by pool' do
    pool = find_pool_for_product @virt
    @system.consume_pool pool.id

    @system.list_entitlements.length.should eq(1)
  end

  it 'should allow consumption of quantity 10' do
    pool = find_pool_for_product @virt
    @system.consume_pool(pool.id, {:quantity => 10})

    @system.list_entitlements.first.quantity.should == 10
  end

  it 'should allow consumption of quantity zero' do
    pool = find_pool_for_product @virt
    pool['consumed'].should eq(0)
    ent = @system.consume_pool(pool.id, {:quantity => 0})
    @cp.get_pool(pool['id'])['consumed'].should eq(1)
    ent[0]['quantity'].should eq(1)
  end

  it 'should allow multiple products to be consumed' do
    @system.consume_product(@virt.id)
    @system.consume_product(@monitoring.id)

    @system.list_entitlements.length.should eq(2)
  end

  it 'should have the correct product ID when subscribing by product' do
    @system.consume_product @monitoring.id

    entitlements = @system.list_entitlements(:product => @monitoring.id)
    entitlements.length.should eq(1)
  end

  it 'should have the correct product ID when subscribing by pool' do
    @system.consume_pool find_pool_for_product(@monitoring).id

    entitlements = @system.list_entitlements(:product => @monitoring.id)
    entitlements.length.should eq(1)
  end

  it 'should filter entitlements by product attribute' do
    @system.consume_pool find_pool_for_product(@virt_limit).id
    @system.consume_pool find_pool_for_product(@super_awesome).id
    @system.list_entitlements().length.should eq(2)

    entitlements = @system.list_entitlements(:attr_filters => {:variant => "Satellite*"})
    entitlements.length.should eq(1)

    variant = get_attribute_value(entitlements[0].pool.productAttributes, "variant")
    expect(variant).to eq("Satellite Starter Pack")
  end

  it 'should filter consumer entitlements by matches parameter' do
    @system.consume_pool find_pool_for_product(@ram).id
    @system.consume_pool find_pool_for_product(@super_awesome).id
    @system.list_entitlements().length.should eq(2)

    entitlements = @system.list_entitlements(:matches => "*ram*")
    entitlements.length.should eq(1)

    found_attr = false
    entitlements[0].pool.productName.should == @ram.name
  end

  it 'should be removed after revoking all entitlements' do
    @system.consume_product @virt.id
    @system.revoke_all_entitlements

    @system.list_entitlements.should be_empty
  end

  it 'should remove multiple entitlements after revoking all entitlements' do
    @system.consume_product @virt.id
    @system.consume_product @monitoring.id
    @system.revoke_all_entitlements

    @system.list_entitlements.should be_empty
  end

  it 'should not allow consuming two entitlements for the same product' do
    @system.consume_product @super_awesome.id

    entitlements = @system.consume_product @super_awesome.id
    entitlements.should == []

  end

  it 'should not allow consuming two entitlements in same pool' do
    pool = find_pool_for_product @super_awesome
    @system.consume_pool pool.id
    lambda do
      @system.consume_pool pool.id
    end.should raise_exception(RestClient::Forbidden)
  end

  it 'should not allow consuming an odd quantity' do
    pool = find_pool_for_product @instance_based
    lambda do
      @system.consume_pool(pool.id, {:quantity => 3})
    end.should raise_exception(RestClient::Forbidden)
  end

  it 'should allow consuming an even quantity' do
    pool = find_pool_for_product @instance_based
    @system.consume_pool(pool.id, {:quantity => 2})
  end

  private

  def find_pool_for_product(product, consumer=nil)
    consumer ||= @system
    consumer.list_pools(:product => product.id, :consumer => consumer.uuid).first
  end

end

