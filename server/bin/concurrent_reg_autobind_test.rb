#!/usr/bin/env ruby
#
# Verifies that multiple consumers can register with an activation key with an auto-attach across
# pools in parallel
#
# This script creates several CP objects: owners, products, subscriptions, pools and activation
# keys; none of which are cleaned up.
#

require 'thread'

require File.expand_path('candlepin_api', File.dirname(__FILE__) + '/../client/ruby')


# Stolen from import_products
# Originally from http://burgestrand.se/articles/quick-and-simple-ruby-thread-pool.html
class Pool
  def initialize(size)
    @size = size
    @jobs = Queue.new
    @pool = Array.new(@size) do |i|
      Thread.new do
        Thread.current[:id] = i
        catch(:exit) do
          loop do
            job, args = @jobs.pop
            job.call(*args)
          end
        end
      end
    end
  end

  def schedule(*args, &block)
    @jobs << [block, args]
  end

  def shutdown
    @size.times do
      schedule { throw :exit }
    end

    @pool.map(&:join)
  end
end

def random_string(prefix=nil, numeric_only=false)
if prefix
  prefix = "#{prefix}-"
end

if numeric_only
  suffix = rand(9999999)
else
  # This is actually a bit faster than using SecureRandom.  Go figure.
  o = [('a'..'z'), ('A'..'Z'), ('0'..'9')].map { |i| i.to_a }.flatten
  suffix = (0..7).map { o[rand(o.length)] }.join
end
"#{prefix}#{suffix}"
end


@cp = Candlepin.new('admin', 'admin')
@client = Candlepin.new

# create extra product/pool to show selectivity
owner = @cp.create_owner(random_string('owner'))
prod1 = @cp.create_product(owner['key'], random_string('product1'), random_string('product1'), {})
subs1 = @cp.create_subscription(owner['key'], prod1["id"], 1)
subs2 = @cp.create_subscription(owner['key'], prod1["id"], 1)
subs3 = @cp.create_subscription(owner['key'], prod1["id"], 2)
@cp.refresh_pools(owner['key'])
pools = @cp.list_pools(:owner => owner['id'], :product => prod1["id"])

key1 = @cp.create_activation_key(owner['key'], 'key1')
@cp.update_activation_key({'id' => key1['id'], "autoAttach" => "true"})
@cp.add_pool_to_key(key1['id'], pools[0]['id'], 1)
@cp.add_pool_to_key(key1['id'], pools[1]['id'], 1)
@cp.add_pool_to_key(key1['id'], pools[2]['id'], 1)
@cp.add_prod_id_to_key(key1['id'], prod1["id"])

count = 5
consumers = []
thread_pool = Pool.new(count)

for i in 0..(count - 1) do
  thread_pool.schedule do
    consumer = @client.register(
      random_string('machine'), :system, nil, {}, nil, owner['key'], ["key1"]
    )

    consumers.push(consumer)
  end
end

# Shutdown & join on the thread pool
thread_pool.shutdown

pools.each do |p|
  this_pool = @cp.get_pool(p["id"])
  if (this_pool["consumed"] != this_pool["quantity"])
    abort("Pool quantity consumed is not equal to its provided quantity. Expected: #{this_pool["quantity"]}, Actual: #{this_pool["consumed"]}")
  end
end

# Count the number of consumers which registered, but could not consume an entitlement
fail_count = 0
consumers.each do |consumer|
  if (consumer["uuid"] == nil)
    abort("Consumer uuid is nil. Expected: not nil")
  end

  certs = @cp.list_entitlements(:uuid => consumer["uuid"])
  fail_count += 1 if (certs.length == 0)
end

if (fail_count != 1)
  abort("Expected number of consumers without entitlements differs from actual number. Expected: 1, Actual: #{fail_count}")
end

puts "Parallel auto-attaches completed successfully"
