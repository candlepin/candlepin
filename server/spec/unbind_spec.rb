require 'spec_helper'
require 'candlepin_scenarios'

describe 'Unbind' do
  include CandlepinMethods

  before(:each) do
    @owner = create_owner random_string('test_owner')
    @user = user_client(@owner, random_string('guy'))
    @monitoring = create_product(nil, random_string('monitoring'))

    create_pool_and_subscription(@owner['key'], @monitoring.id, 4)
  end

  it 'should remove a single entitlement' do
    consumer = consumer_client(@user, 'consumer')
    pool = consumer.list_pools(
      :product => @monitoring.id,
      :consumer => consumer.uuid).first

    ent = consumer.consume_pool(pool.id, {:quantity => 1}).first

    consumer.unbind_entitlement ent.id
    consumer.list_entitlements.should be_empty
  end

  it 'should remove entitlements by pool id without touching other entitlements' do
    consumer = consumer_client(@user, 'consumer')

    testing = create_product(nil, random_string('testing'))
    create_pool_and_subscription(@owner['key'], testing.id, 4)

    pool1 = consumer.list_pools(
      :product => @monitoring.id,
      :consumer => consumer.uuid).first
    pool2 = consumer.list_pools(
      :product => testing.id,
      :consumer => consumer.uuid).first

    ent1 = consumer.consume_pool(pool1.id, {:quantity => 1}).first
    ent2 = consumer.consume_pool(pool2.id, {:quantity => 1}).first

    consumer.unbind_entitlements_by_pool(consumer.uuid, pool1.id)
    expect(consumer.list_entitlements).to(match_array([ent2]))
  end

  it 'should add unbound entitlements back to the pool' do
    consumer = consumer_client(@user, 'consumer')
    pool = consumer.list_pools(
      :product => @monitoring.id,
      :consumer => consumer.uuid).first
    ent = consumer.consume_pool(pool.id, {:quantity => 1}).first

    consumer.unbind_entitlement ent.id
    consumer.get_pool(pool.id).quantity.should == 4
  end

  it 'should revoke an entitlement\'s certificate' do
    consumer = consumer_client(@user, 'dude')
    pool = consumer.list_pools(
      :product => @monitoring.id,
      :consumer => consumer.uuid).first
    ent = consumer.consume_pool(pool.id, {:quantity => 1}).first

    # Gather up the serials
    serials = consumer.list_entitlements(:product_id => @monitoring.id).collect do |ent|
      ent.certificates.collect { |cert| cert.serial.id }
    end.flatten

    consumer.unbind_entitlement ent.id

    # All the serials should be revoked
    serials.each { |serial| @cp.get_serial(serial).revoked.should be_true }
  end

  it 'should leave other entitlements in tact' do
    virt_host = create_product(nil, random_string('virt_host'))
    pool = create_pool_and_subscription(@owner['key'], virt_host.id, 5)

    consumer = consumer_client(@user, 'consumer')
    pool = consumer.list_pools(
      :product => @monitoring.id,
      :consumer => consumer.uuid).first
    monitoring_ent = consumer.consume_pool(pool.id, {:quantity => 1}).first

    pool = consumer.list_pools(
      :product => virt_host.id,
      :consumer => consumer.uuid).first
    virt_host_ent = consumer.consume_pool(pool.id, {:quantity => 1}).first

    # Gather up the serials
    serials = consumer.list_entitlements(:product_id => @monitoring.id).collect do |ent|
      ent.certificates.collect { |cert| cert.serial.id }
    end.flatten

    consumer.unbind_entitlement virt_host_ent.id

    # None of the serials should be revoked
    serials.each { |serial| @cp.get_serial(serial).revoked.should be_false }
  end
end
