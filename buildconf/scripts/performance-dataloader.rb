#!/usr/bin/ruby
#
# Script to load up a candlepin server for performance testing.
#
# Requires our test data to be loaded, as some of the eng products
# are referenced below so we can test some things easily with our
# own systems.
#
# Creates a number of subscriptions (some virt-limit to create
# virt bonus pools once consumed, and some using stacking.
# A number of fake systems are then registered with installed
# products, and then an autobind is performed for each.

require  "../../client/ruby/candlepin_api"
require 'pp'

ADMIN_USERNAME = "admin"
ADMIN_PASSWORD = "admin"
HOST = "localhost"
PORT = 8443

# This is low, we need more but not until there's a list pools fix, the performance is just too slow:
SYSTEM_COUNT = 100
POOL_COUNT = 100

# Using eng products from our test data so we can test clients manually as
# well. (easier as we have product certs laying around for these)
ENG_PROD = '37060'
STACKED_ENG_PROD = '100000000000002'

def random_string prefix=nil
  prefix ||= "rand"
  return "#{prefix}-#{rand(100000000)}"
end

def create_systems(cp, owner_key, count, installed_pids, facts)
  installed_products = []
  installed_pids.each do |pid|
    installed_products << {'productId' => pid, 'productName' => pid}
  end
  (1..count).each do |i|
    sys = cp.register(random_string("sys-#{i}"), :system, nil, facts, nil, owner_key, [], installed_products)
    # Do an autobind:
    ents = cp.consume_product(nil, {:uuid => sys['uuid']})
    puts "   #{i} - #{sys['name']} (#{sys['uuid']})"
  end
end
cp = Candlepin.new(ADMIN_USERNAME, ADMIN_PASSWORD, nil, nil, HOST, PORT)

# Create the owner we will load up with data:
owner = cp.create_owner random_string("perf-owner")

puts "Created test owner: #{owner['key']}"

# Create subscriptions, each of which is for a different marketing product,
# but all of which provide the same provided products.
end_date = Date.new(2025, 5, 29)
puts "Creating #{POOL_COUNT / 2} virt-limit subscriptions/pools:"
(1..(POOL_COUNT / 2)).each do |i|
  mkt_prod_id = random_string('mkt-prod')
  mkt_prod = cp.create_product(mkt_prod_id, mkt_prod_id, {
    :attributes => {
      :virt_limit => "4",
    }
  })
  sub1 = cp.create_subscription(owner['key'], mkt_prod['id'], 50,
    [ENG_PROD], rand(10000), rand(10000), nil, end_date)
end

puts "Creating #{POOL_COUNT / 2} stacked subscriptions/pools:"
(1..(POOL_COUNT / 2)).each do |i|
  mkt_prod_id = random_string('mkt-prod')
  mkt_prod = cp.create_product(mkt_prod_id, mkt_prod_id, {
    :attributes => {
      :stacking_id => "sostacked",
      :sockets => "2",
      "multi-entitlement" => "yes",
    }
  })
  # Quantity 2 to force stacking to kick in:
  sub1 = cp.create_subscription(owner['key'], mkt_prod['id'], 2,
    [STACKED_ENG_PROD], rand(10000), rand(10000), nil, end_date)
end
puts "Refreshing pools..."
cp.refresh_pools(owner['key'])

puts "Creating #{SYSTEM_COUNT / 2} regular systems:"
create_systems(cp, owner['key'], SYSTEM_COUNT / 2, [ENG_PROD], {})
puts "Creating #{SYSTEM_COUNT / 2} stacked systems:"
create_systems(cp, owner['key'], SYSTEM_COUNT / 2, [STACKED_ENG_PROD], {"cpu.cpu_socket(s)" => 8})

