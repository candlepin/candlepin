# This script is used to create a given number of consumers in threads and
# have them all attempt to consume from the pools for a particular product.
#
# TO RUN:
# in terminal 1: touch logout
# in terminal 1: tail -f logout
# in terminal 2: ruby concurrency-test.rb 40 2> logout
# watch the progress bar in terminal 2 (green dot for consumed ent, red N for not)
# watch the details in terminal 1

require "../client/ruby/candlepin_api"
require 'pp'
require 'optparse'

CP_SERVER = "localhost"
CP_PORT = 8443
CP_ADMIN_USER = "admin"
CP_ADMIN_PASS = "admin"

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

def consume(consumer_cp, pool_id)
  ent = consumer_cp.consume_pool(pool_id)[0]
  pool = consumer_cp.get_pool(ent['pool']['id'])
  debug "Got entitlement #{ent['id']} from pool #{ent['pool']['id']} (#{pool['consumed']} of #{pool['quantity']})"
  return ent, pool
end

# Create a product and pool to consume:
product_id = "concurproduct-#{rand(100000)}"
cp = Candlepin.new(username=CP_ADMIN_USER, password=CP_ADMIN_PASS,
  cert=nil, key=nil,
  host=CP_SERVER, port=CP_PORT)
test_owner = cp.create_owner("testowner-#{rand(100000)}")
attributes = {'multi-entitlement' => "yes"}
cp.create_product(product_id, product_id, {:attributes => attributes})
cp.create_subscription(test_owner['key'], product_id, 10)
cp.refresh_pools(test_owner['key'])
pools = cp.list_pools(:owner => test_owner['id'])
pool = pools[0]

# Create a consumer to bind entitlements to. We'll just use one combined
# with a pool that supports multi-entitlement:
consumer = cp.register("test" << rand(10000).to_s, :candlepin,
  nil, {}, nil, test_owner['key'])
consumer_cp = Candlepin.new(nil, nil, consumer['idCert']['cert'],
  consumer['idCert']['key'], CP_SERVER, CP_PORT)

# Launch threads to try to bind at same time:
num_threads = ARGV[0].to_i
if num_threads == 0
  num_threads = 1
end

queue = Queue.new

threads = []
for i in 0..num_threads - 1
  threads[i] = Thread.new do
    Thread.current[:name] = "Thread"
    begin
      ent = consume(consumer_cp, pool['id'])
      queue << (ent.nil? ? no_ent : an_ent)
    rescue
      debug "Exception caught / no entitlement"
      queue << no_ent
    end
  end
end

collector = Thread.new do
  res_string = ""
  for i in 0..num_threads - 1
    res_string << queue.pop
    STDOUT.print "\r" + res_string
    STDOUT.flush
  end
  STDOUT.print "\n"
end

collector.join
threads.each { |thread| thread.join }
