require 'spec_helper'
require 'candlepin_scenarios'

describe 'Entitlements' do

  include CandlepinMethods

  before(:each) do
    @owner = create_owner random_string 'test_owner'
    @monitoring = create_product(nil, random_string('monitoring'))
    @virt = create_product(nil, random_string('virtualization_host'),
      {:attributes => {"multi-entitlement" => "yes"}})
    @super_awesome = create_product(nil, random_string('super_awesome'),
                                    :attributes => { 'cpu.cpu_socket(s)' => 4 })
    @virt_limit = create_product(nil, random_string('virt_limit'),
      {:attributes => {"virt_limit" => "10"}})

    @instance_based = create_product(nil, random_string('instance_based'),
                                    :attributes => { 'instance_multiplier' => 2,
                                        'multi-entitlement' => 'yes' })

    #entitle owner for the virt and monitoring products.
    @cp.create_subscription(@owner['key'], @virt.id, 20)
    @cp.create_subscription(@owner['key'], @monitoring.id, 4)
    @cp.create_subscription(@owner['key'], @super_awesome.id, 4)
    @cp.create_subscription(@owner['key'], @virt_limit.id, 5)
    @cp.create_subscription(@owner['key'], @instance_based.id, 10)

    @cp.refresh_pools(@owner['key'])

    #create consumer
    @user = user_client(@owner, random_string('billy'))
    @system = consumer_client(@user, 'system6')
  end

  it 'should bypasses rules for "candlepin" consumers' do
    box = consumer_client(@user, 'random_box', :candlepin, nil, 'cpu.cpu_socket(s)' => 8)

    box.consume_product(@super_awesome.id)
    box.list_entitlements.should have(1).things
  end

  it 'should throw an error when filtering by a non-existant product ID' do
    lambda do
      @system.list_entitlements(:product_id => 'non_existant')
    end.should raise_exception(RestClient::BadRequest)
  end

  it 'should allow an entitlement to be consumed by product' do
    @system.consume_product @virt.id
    @system.list_entitlements.should have(1).things
  end

  it 'should allow an entitlement to be consumed by pool' do
    pool = find_pool @virt
    @system.consume_pool pool.id

    @system.list_entitlements.should have(1).things
  end

  it 'should allow consumption of quantity 10' do
    pool = find_pool @virt
    @system.consume_pool(pool.id, {:quantity => 10})

    @system.list_entitlements.first.quantity.should == 10
  end

  it 'should allow multiple products to be consumed' do
    @system.consume_product(@virt.id)
    @system.consume_product(@monitoring.id)

    @system.list_entitlements.should have(2).things
  end

  it 'should have the correct product ID when subscribing by product' do
    @system.consume_product @monitoring.id

    entitlements = @system.list_entitlements(:product_id => @monitoring.id)
    entitlements.should have(1).things
  end

  it 'should have the correct product ID when subscribing by pool' do
    @system.consume_pool find_pool(@monitoring).id

    entitlements = @system.list_entitlements(:product_id => @monitoring.id)
    entitlements.should have(1).things
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
    entitlements.should be_nil
  end

  it 'should not allow consuming two entitlements in same pool' do
    pool = find_pool @super_awesome
    @system.consume_pool pool.id
    lambda do
      @system.consume_pool pool.id
    end.should raise_exception(RestClient::Forbidden)
  end

  it 'should not allow consuming an odd quantity' do
    pool = find_pool @instance_based
    lambda do
      @system.consume_pool(pool.id, {:quantity => 3})
    end.should raise_exception(RestClient::Forbidden)
  end

  it 'should allow consuming an even quantity' do
    pool = find_pool @instance_based
    @system.consume_pool(pool.id, {:quantity => 2})
  end

  it 'should have pool type calculated attribute' do
    box = consumer_client(@user, 'random_box', :candlepin, nil, 'cpu.cpu_socket(s)' => 8)
    box.consume_product(@super_awesome.id)
    ents = box.list_entitlements()
    ents.should have(1).things
    ents[0]['pool']['calculatedAttributes']['compliance_type'].should == 'Standard'
  end
  private

  def find_pool(product, consumer=nil)
    consumer ||= @system
    consumer.list_pools(:product => product.id, :consumer => consumer.uuid).first
  end

end

