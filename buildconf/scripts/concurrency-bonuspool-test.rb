# This script is used to create a given number of consumers in threads and
# have them all attempt to consume from the pools for a particular product.
#
# TO RUN:
# in terminal 1: touch logout
# in terminal 1: tail -f logout
# in terminal 2: ruby concurrency-test.rb 40 2> logout
# watch the progress bar in terminal 2 (green dot for consumed ent, red N for not)
# watch the details in terminal 1

require "../../client/ruby/candlepin_api"
require 'pp'
require 'optparse'

CP_SERVER = "localhost"
CP_PORT = 8443
CP_ADMIN_USER = "admin"
CP_ADMIN_PASS = "admin"
CP_OWNER_KEY = "admin"

def debug(msg)
    STDERR.write Thread.current[:name]
    STDERR.write " :: "
    STDERR.write msg
    STDERR.write "\n"
    STDERR.flush
end

def no_ent()
    return "\033[31mN\033[0m"
end

def an_ent()
    return "\033[32m.\033[0m"
end

def reg_and_consume(server, port, user, pass, pool_id, owner_key)
  cp = Candlepin.new(username=user, password=pass,
    cert=nil, key=nil,
    host=server, port=port)
  consumer = cp.register("test" << rand(10000).to_s, :candlepin, nil, {}, nil, owner_key)

  cp = Candlepin.new(nil, nil, consumer['idCert']['cert'],
                     consumer['idCert']['key'], server, port)
  ent = cp.consume_pool(pool_id)[0]
  pool = cp.get_pool(ent['pool']['id'])

  # Now unbind it:
  cp.unbind_entitlement(ent['id'], {:uuid => consumer['uuid']})
  debug "Got and returned entitlement: #{ent['id']}"
  return ent
end

# Create a product and pool to consume:
product_id = "concurproduct-#{rand(100000)}"
cp = Candlepin.new(username=CP_ADMIN_USER, password=CP_ADMIN_PASS,
  cert=nil, key=nil,
  host=CP_SERVER, port=CP_PORT)
test_owner = cp.create_owner("testowner-#{rand(100000)}")
attributes = {:virt_limit => '10'}
cp.create_product(product_id, product_id, {:attributes => attributes})
cp.create_subscription(test_owner['key'], product_id, 10)
cp.refresh_pools(test_owner['key'])

pools = cp.list_pools(:owner => test_owner['id'])

phys_pool = pools.find_all { |i| i['quantity'] == 10 }[0]
bonus_pool = pools.find_all { |i| i['quantity'] == 100 }[0]

num_threads = 10
if num_threads == 0
  num_threads = 1
end

threads = []
for i in 0..num_threads - 1
  threads[i] = Thread.new do
    Thread.current[:name] = "Thread #{i}"
      ent = reg_and_consume(CP_SERVER, CP_PORT, CP_ADMIN_USER, CP_ADMIN_PASS,
                            phys_pool['id'], test_owner['key'])
  end
end

collector = Thread.new do
  res_string = ""
  for i in 0..num_threads - 1
    STDOUT.flush
  end
  STDOUT.print "\n"
end

collector.join
threads.each { |thread| thread.join }

bonus_pool = cp.get_pool(bonus_pool['id'])
print "Bonus pool quantity should be 100: #{bonus_pool['quantity']}\n"

cp.delete_owner(test_owner['key'])
