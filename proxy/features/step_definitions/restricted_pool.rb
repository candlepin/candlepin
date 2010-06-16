require 'spec/expectations'
require 'candlepin_api'

def create_client(consumer)
  idCert = consumer['idCert']
  Candlepin.new(username=nil, password=nil, cert=idCert['cert'], key=idCert['key'])
end

def get_client(consumer_id)
  @cp_clients = {} if @cp_clients.nil?
  if @cp_clients[consumer_id].nil?
    idCert = @consumers[consumer_id]['idCert']
    @cp_clients[consumer_id] = create_client(@consumers[consumer_id])
  end
  @cp_clients[consumer_id]
end


Given /^I am an user "([^\"]*)"$/ do |arg1|
  @user = @candlepin.create_user(@test_owner['id'], arg1, 'password')
#  @clients = {} if @clients.nil?
  @client  = Candlepin.new(username=arg1, 'password')
end

When /^I create a consumer of type person$/ do
  @consumer = @client.register(nil, :person)
end

Given /^I create a consumer "([^\"]*)" of type system$/ do |consumer_id|
  @consumers = {} if @consumers.nil?
  @consumers[consumer_id] = @client.register(consumer_id, :system)
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
  map = pool_attr_to_map(@new_pool)
  pool_attr_to_map(@new_pool['sourceEntitlement']['pool'])
#  puts "pool id within entitlement: #{@new_pool['sourceEntitlement']['pool']['id']}"
  @new_pool['quantity'].should == -1
  map['user_restricted'].should == arg1
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

Then /^pools without filtering consumers should not include unlimited pool$/ do
  user =  @candlepin.create_user(@test_owner['id'], "annie", 'password1')
  cp = Candlepin.new(username="annie", 'password1')
  any_unlimited_present(cp.get_pools()).should == false
#  pending #any_unlimited_prsent(
end

Then /^the consumer's username should be "([^\"]*)"$/ do |arg1|
  @consumer['username'].should == arg1
end
