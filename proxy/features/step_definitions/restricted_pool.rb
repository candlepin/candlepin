require 'spec/expectations'
require 'candlepin_api'

def create_client(consumer)
  idCert = consumer['idCert']
  Candlepin.new(nil, nil, cert=idCert['cert'], key=idCert['key'])
end

def get_client(consumer_id)
  @cp_clients = {} if @cp_clients.nil?
  if @cp_clients[consumer_id].nil?
    idCert = @consumers[consumer_id]['idCert']
    @cp_clients[consumer_id] = create_client(@consumers[consumer_id])
  end
  @cp_clients[consumer_id]
end


Given /^I am a user "([^\"]*)"$/ do |arg1|
  @user = @candlepin.create_user(@test_owner['id'], arg1, 'password')
#  @clients = {} if @clients.nil?
  @client  = Candlepin.new(username=arg1, 'password')
end

When /^I create a consumer of type person$/ do
  @consumer = @client.register(nil, :person)
end

#Given /^I create a consumer "([^\"]*)" of type system$/ do |consumer_id|
#  @consumers = {} if @consumers.nil?
#  @consumers[consumer_id] = @client.register(consumer_id, :system)
#end

Given /^I create a consumer "([^\"]*)" of type "([^\"]*)"$/ do |consumer_id, consumer_type|
  @consumers = {} if @consumers.nil?
  @consumers[consumer_id] = @client.register(consumer_id, consumer_type)
end

When /^I create a pool of unlimited license and consumer type person$/ do
  @product = create_product('product1')
  @subscription = create_subscription('product1', 1)
  @pool = @candlepin.create_pool(product_id=@product['id'],
                                             owner_id=@test_owner['id'], @subscription['id'],
                                             attributes={
                                               'user_license' => 'unlimited',
                                               'requires_consumer_type' => 'person'
                                            })
end

Then /^pool should be of unlimited license and consumer type person$/ do
  map = pool_attr_to_map(@pool)
  map['user_license'].should == "unlimited"
  map['requires_consumer_type'].should == "person"
end

When /^I consume entitlement from pool for consumer of type person$/ do
  #puts @consumer.inspect
  @person_cp = create_client(@consumer)
  @entitlements = @person_cp.consume_product(@product['id'], 1)

#  pending # express the regexp above with the code you wish you had
end

Then /^a new pool should have been created$/ do
   pools = @person_cp.get_pools({ :consumer => @uuid})
   pools.size.should == 2
   @new_pool = pools.find { |pool| pool['id'] != @pool['id'] }
end

Then /^source entitlement of pool should match the entitlement just created$/ do
  @new_pool['sourceEntitlement']['id'].should == @entitlements[0]['id']
end

Then /^pool should be of unlimited quantity and restricted to "([^\"]*)"$/ do |arg1|
  @new_pool['quantity'].should == -1
  @new_pool['restrictedToUsername'].should == arg1
end


def pool_attr_to_map(pool)
  map = {}
  pool['attributes'].each { |attr| map[attr["name"]] = attr["value"] }
  #puts map.inspect
  return map
end

Then /^I should be able to consume entitlement for "([^\"]*)" system from this pool$/ do |arg1|
  get_client(arg1).consume_pool(@new_pool['id'])
end

#Then /^I should not be able to consume entitlement for a system "([^\"]*)" does not own$/ do |arg1|
#  pending # express the regexp above with the code you wish you had
#end

Then /^one of "([^\"]*)" pools should be unlimited pool$/ do |arg1|
  any_unlimited_present(get_client(arg1).get_pools({ :consumer => @uuid})).should == true
end

def any_unlimited_present(pools)
  pools.any? { |pool| pool['quantity'] == -1 }
end

Then /^pools from "([^\"]*)" pools should not be unlimited pool$/ do |arg1|
  any_unlimited_present(get_client(arg1).get_pools({ :consumer => @uuid})).should == false
end

Then /^another consumer cannot see user-restricted pool$/ do
   alice_user = @candlepin.create_user(@test_owner['id'], 'alice', 'password')
   tmp_client = Candlepin.new('alice', 'password')
   alice_consumer = tmp_client.register(nil, :person)
   alice_client = create_client(alice_consumer)
   any_unlimited_present(alice_client.get_pools()).should == false
end

Then /^the consumer's username should be "([^\"]*)"$/ do |arg1|
  @consumer['username'].should == arg1
end
