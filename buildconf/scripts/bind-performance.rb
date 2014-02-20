#!/usr/bin/env ruby
#
# Script to create an account with a large number of pools, and bind a
# separate entitlement for each to a specific consumer. The time required
# for each bind as well as some additional steps in the process is printed
# to stdout.
#
# Can be run and re-run against a typical dev deployment without any arguments.

require  "../../client/ruby/candlepin_api"
require 'pp'

require 'benchmark'

ADMIN_USERNAME = "admin"
ADMIN_PASSWORD = "admin"
HOST = "localhost"
PORT = 8443

def random_string prefix=nil
  prefix ||= "rand"
  return "#{prefix}-#{rand(100000)}"
end

def time_rand from = 0.0, to = Time.now
  Time.at(from + rand * (to.to_f - from.to_f))
end

cp = Candlepin.new(ADMIN_USERNAME, ADMIN_PASSWORD, nil, nil, HOST, PORT)

owner = cp.create_owner random_string("consumertest")
puts "Created owner: #{owner['key']}"

prod_attrs = {}
(0..20).each do |i|
  prod_attrs[random_string("attr")] = random_string("val")
end
prod_attrs['multi-entitlement'] = "yes"
product1 = cp.create_product(random_string(), random_string(),
  {:attributes => prod_attrs})

ent_count = 50

all_provided_products = [random_string(),
  random_string(),
  random_string(),
  random_string(),
  random_string(),
  random_string(),
  random_string(),
  random_string(),
  random_string(),
  random_string(),
  random_string(),
  random_string(),
  random_string(),
  random_string(),
  random_string(),
  random_string(),
  random_string(),
  random_string()]
all_provided_products.each do |pid|
  cp.create_product(pid, pid, {})
end

Benchmark.bm (10) do |x|
  x.report("Creating #{ent_count} pools:") {
    (0..ent_count).each do |i|
      start_date = Date.parse(time_rand(Time.local(2000, 1, 1), Time.now).to_s)
      end_date = Date.parse(time_rand(Time.now, Time.local(2050, 1, 1)).to_s)
      sub1 = cp.create_subscription(owner['key'], product1['id'], 1, all_provided_products.sample(8), '', '12345', nil, start_date, end_date)
    end
  }
end

Benchmark.bm (10) do |x|
  x.report("Refresh:") {
    cp.refresh_pools(owner['key'])
  }
end

org_admin_username = random_string("orgadmin")
org_admin_password = 'password'
cp.create_user(org_admin_username, org_admin_password, true)
org_admin_cp = Candlepin.new(org_admin_username, org_admin_password)
facts = {
    "distributor_version" => "sat-6.0",
    "satellite_version" => "6.0",
    "system.certificate_version" => "3.0"
}
consumer = org_admin_cp.register(random_string('dummyconsumer'), "system",
  nil, facts, nil, owner['key'])
puts "Created consumer: id = #{consumer['id']}, uuid = #{consumer['uuid']}"
consumer_cp = Candlepin.new(nil, nil, consumer['idCert']['cert'], consumer['idCert']['key'],
  HOST, PORT)

pools = cp.list_owner_pools(owner['key'])
puts "Grabbing #{ent_count} entitlements"

Benchmark.bm (10) do |x|
  i = 1
  pools.each do |pool|
    x.report("Binding #{i}:") {
      consumer_cp.consume_pool(pool['id'])
    }
    x.report("GET:") {
      consumer_cp.get_consumer(consumer['uuid'])
    }
    i = i + 1
  end
end

Benchmark.bm(10) do |d|
  i = 1
  d.report("Unbinding all: ") {
      consumer_cp.revoke_all_entitlements()
  }
end
