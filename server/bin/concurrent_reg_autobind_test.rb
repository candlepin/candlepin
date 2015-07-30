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
prod2 = @cp.create_product(owner['key'], random_string('product2'), random_string('product2'), {})
subs1 = @cp.create_subscription(owner['key'], prod1["id"], 10)
subs2 = @cp.create_subscription(owner['key'], prod2["id"], 1)
subs3 = @cp.create_subscription(owner['key'], prod2["id"], 1)
@cp.refresh_pools(owner['key'])
pools = @cp.list_pools(:owner => owner['id'])

key1 = @cp.create_activation_key(owner['key'], 'key1')
@cp.update_activation_key({'id' => key1['id'], "autoAttach" => "true"})
@cp.add_pool_to_key(key1['id'], pools[0]['id'], 1)
@cp.add_pool_to_key(key1['id'], pools[1]['id'], 1)
@cp.add_pool_to_key(key1['id'], pools[2]['id'], 1)
@cp.add_prod_id_to_key(key1['id'], prod1["id"])
@cp.add_prod_id_to_key(key1['id'], prod2["id"])

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

  if this_pool["quantity"] > 5
    expected = 5
  else
    expected = this_pool["quantity"]
  end

  if (this_pool["consumed"] != expected)
    abort("Pool quantity consumed is not equal to the expected quantity consumed. Expected: #{expected}, Actual: #{this_pool["consumed"]}")
  end
end

# Verify entitlement counts
exp_entitlement_counts = {
  1 => 3,
  2 => 2
}

act_entitlement_counts = {}

consumers.each do |consumer|
  if (consumer["uuid"] == nil)
    abort("Consumer uuid is nil. Expected: not nil")
  end

  entitlements = @cp.list_entitlements(:uuid => consumer["uuid"])

  if act_entitlement_counts.has_key?(entitlements.length)
    act_entitlement_counts[entitlements.length] += 1
  else
    act_entitlement_counts[entitlements.length] = 1
  end
end

if act_entitlement_counts != exp_entitlement_counts
  abort("Expected consumer entitlement count mismatch. Expected: #{exp_entitlement_counts}, Actual: #{act_entitlement_counts}")
end

puts "Parallel auto-attaches completed successfully"
