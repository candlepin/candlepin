#!/usr/bin/env ruby

require  "../client/ruby/candlepin_api"
require  "../client/ruby/hostedtest_api"
require 'pp'

@cp = Candlepin.new('admin', 'admin')

include HostedTest

def random_string prefix=nil
  prefix ||= "rand"
  return "#{prefix}-#{rand(100000)}"
end

puts 'Creating Owner'
owner = @cp.create_owner(random_string('test_owner'))

puts 'Creating User'
user = @cp.create_user(random_string('test_user'), 'password')

puts 'Creating Product'
product = @cp.create_product(owner['key'],'new_product'.hash, 'new_product',
  {:attributes => {'multi-entitlement' => 'yes'}})

puts 'Creating Subscription'
create_pool_and_subscription(owner['key'], product['id'], 3000000)

cp_user = Candlepin.new('test_user', 'password')

puts 'Creating Consumer'
consumer = @cp.register('test-consumer', 'system', nil, {}, nil, owner['key'])
cp_consumer = Candlepin.new(nil, nil, consumer['idCert']['cert'], consumer['idCert']['key'])

puts 'Set up complete'
threads = []

50.times do |thread_id|
  threads << Thread.new(cp_consumer, thread_id) do |cp, thread_id|
    200.times do |loop_id|
      puts "Consuming new Entitlement:  #{thread_id}-#{loop_id}"
      entitlement = @cp.consume_product(product['id'], {:uuid => cp_consumer.uuid})

      ent_id = entitlement[0]['id']
      puts "Revoking entitlement:  #{ent_id}"
      @cp.unbind_entitlement(ent_id, {:uuid => cp_consumer.uuid})
    end
  end
end

threads.each { |thread| thread.join }
