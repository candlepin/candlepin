#!/usr/bin/ruby
#
# Script to create a valid candlepin export.zip which can then be imported
# for an owner. Creates everything necessary, starting with a fresh owner,
# admin user, candlepin consumer, products, subs, pools, and entitlements.
#
# None of the above will be cleaned up.

require  "../../client/ruby/candlepin_api"
require 'pp'

ADMIN_USERNAME = "admin"
ADMIN_PASSWORD = "admin"
HOST = "localhost"
PORT = 8443

def random_string prefix=nil
  prefix ||= "rand"
  return "#{prefix}-#{rand(100000)}"
end

if ARGV.length != 1 then
  abort("USAGE: generate-all-export.rb <OWNERKEY>")
end


cp = Candlepin.new(ADMIN_USERNAME, ADMIN_PASSWORD, nil, nil, HOST, PORT)
owner = cp.get_owner(ARGV[0])

capabilities = ["cores", "instance_multiplier", "ram"]
consumer = cp.register(random_string('dummyconsumer'), "candlepin",
  nil, {"system.certificate_version" => "3.9"}, nil, owner['key'],
  [], [], nil, capabilities)

consumer_cp = Candlepin.new(nil, nil, consumer['idCert']['cert'],
  consumer['idCert']['key'], HOST, PORT)

pools = cp.list_pools(:owner => owner['id'])
for p in pools
  puts "consuming pool: " + p['productName']
  consumer_cp.consume_pool(p['id'], {:quantity => p['quantity']})
end

# Make a temporary directory where we can safely extract our archive:
tmp_dir = File.join(Dir.tmpdir, random_string('candlepin-export'))
export_dir = File.join(tmp_dir, "export")
Dir.mkdir(tmp_dir)

export_filename = consumer_cp.export_consumer(tmp_dir)
puts "Export: #{export_filename}"
