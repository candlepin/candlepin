#!/usr/bin/ruby

require  "../../../client/ruby/candlepin_api"
require 'pp'

cp = Candlepin.new('admin', 'admin')

puts 'Creating Owner'
owner = cp.create_owner('test_owner')

puts 'Creating User'
user = cp.create_user(owner['id'], 'test_user', 'password')

puts 'Creating Product'
product = cp.create_product('new_product', 
                            'new_product'.hash,
                            1,
                            1,
                            'ALL',
                            'ALL',
                            'SVC',
                            [],
                            {'multi-entitlement' => 'yes'})

puts 'Creating Subscription'
cp.create_subscription(owner['id'], product['id'], 3000000)
cp.refresh_pools(owner['key'])

cp_user = Candlepin.new('test_user', 'password')

puts 'Creating Consumer'
consumer = cp_user.register('test-consumer')
cp_consumer = Candlepin.new(nil, nil, consumer['idCert']['cert'], consumer['idCert']['key'])

threads = []

50.times do |thread_id|
  threads << Thread.new(cp_consumer, thread_id) do |cp, thread_id|
    200.times do |loop_id|
      puts "Consuming new Entitlement:  #{thread_id}-#{loop_id}"
      entitlement = cp.consume_product(product['id'])

      ent_id = entitlement[0]['id']
      puts "Revoking entitlement:  #{ent_id}"
      cp.unbind_entitlement(ent_id)
    end
  end
end

threads.each { |thread| thread.join }
