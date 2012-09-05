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
require 'benchmark'
require 'optparse'

ADMIN_USERNAME = "admin"
ADMIN_PASSWORD = "admin"
HOST = "localhost"
PORT = 8443

# Array of installed products each system will have. When we create pools,
# we'll have them provide some random combination of these. These are all
# from our test data, as the products must already exist, and we can more
# easily test our own clients against the org this script creates.
INSTALLED_PRODUCTS = [
  '37060',
  '37062',
  '37065',
  '37070',
  '37067',
  '37068',
  '37069',
  '37080',
  '27060',
  '100000000000002',
]

ARCH = "x86_64"

def random_string prefix=nil
  prefix ||= "rand"
  return "#{prefix}-#{rand(100000000)}"
end

def random_provided_products
  return INSTALLED_PRODUCTS.slice(0, rand(INSTALLED_PRODUCTS.size + 1))
end

def create_systems(cp, owner_key, count, facts)
  installed_products = []
  INSTALLED_PRODUCTS.each do |pid|
    installed_products << {'productId' => pid, 'productName' => pid}
  end
  (1..count).each do |i|
    sys = cp.register(random_string("sys-#{i}"), :system, nil, facts, nil, owner_key, [], installed_products)
    # Do an autobind:
    ents = nil
    time = Benchmark.realtime do
      ents = cp.consume_product(nil, {:uuid => sys['uuid']})
    end
    quantity = 0
    if ! ents.nil?
      ents.each do |e|
        quantity += e["quantity"]
      end
    end
    puts "#{i} - (#{sys['uuid']}) ents=#{(ents.nil? ? 0 : ents.size)} quantity=#{quantity} time=#{time}\n"
  end
end

def create_pools(cp, owner_key, count, attributes, quantity)
  end_date = Date.new(2025, 5, 29)
  time = Benchmark.realtime do
    (1..(count)).each do |i|
      mkt_prod_id = random_string('mkt-prod')
      mkt_prod = cp.create_product(mkt_prod_id, mkt_prod_id, {
        :attributes => attributes,
      })
      sub1 = cp.create_subscription(owner_key, mkt_prod['id'], quantity,
        random_provided_products(), rand(10000), rand(10000), nil, end_date)
    end
  end
  puts "   #{time} seconds"
end

options = {}
optparse = OptionParser.new do |opts|
  options[:org] = random_string("perf-owner")
  opts.on("-o KEY", "--org KEY", "Create or re-use a specific org.") do |org_key|
    options[:org] = org_key
  end

  # By default we won't create any new pools:
  options[:pool_count] = 0
  opts.on("-p POOL_COUNT", "--pool-count POOL_COUNT", "Number of pools/subscriptions to create.") do |pool_count|
    options[:pool_count] = pool_count.to_i
  end

  # By default we won't create any new systems:
  options[:system_count] = 0
  opts.on("-s SYSTEM_COUNT", "--system-count SYSTEM_COUNT", "Number of systems to register and autosubscribe.") do |system_count|
    options[:system_count] = system_count.to_i
  end

  opts.on('-h', '--help', 'Display this screen') do
    exit
  end
end

optparse.parse!

cp = Candlepin.new(ADMIN_USERNAME, ADMIN_PASSWORD, nil, nil, HOST, PORT)

# Create the owner we will load up with data:

begin
  owner = cp.get_owner options[:org]
  puts "Re-using owner: #{owner['key']}"
rescue RestClient::ResourceNotFound
  owner = cp.create_owner options[:org]
  puts "Created owner: #{owner['key']}"
end

if options[:pool_count] > 0
  puts "Creating #{options[:pool_count]} subscriptions/pools:"
  attributes = {
    :virt_limit => "4",
    :arch => ARCH,
    :requires_consumer_type => "system",
    :support_level => "Amazing",
    :support_type => "Everything",
    #:stacking_id => "sostacked",
    #:sockets => "2",
    #"multi-entitlement" => "yes",
  }
  # To enable stacking, uncomment the attributes above and adjust the quantity
  # here:
  create_pools(cp, owner['key'], options[:pool_count], attributes, 500)

  puts "Refreshing pools..."
  time = Benchmark.realtime do
    cp.refresh_pools(owner['key'])
  end
  puts "   #{time} seconds"
else
  puts "Skipping pool creation. (use --pool-count)"
end

if options[:system_count] > 0
  puts "Creating #{options[:system_count]} systems:"
  create_systems(cp, owner['key'], options[:system_count], {"cpu.cpu_socket(s)" => 8, "uname.machine" => ARCH})
else
  puts "Skipping system registration and autobinds. (use --system-count)"
end

