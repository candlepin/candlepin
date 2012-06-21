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

cp = Candlepin.new(ADMIN_USERNAME, ADMIN_PASSWORD, nil, nil, HOST, PORT)

owner = cp.create_owner random_string("export_me")

product1 = cp.create_product(random_string(), random_string())
product2 = cp.create_product(random_string(), random_string())

end_date = Date.new(2025, 5, 29)
sub1 = cp.create_subscription(owner['key'], product1['id'], 20, [], '', '12345', nil, end_date)
sub2 = cp.create_subscription(owner['key'], product2['id'], 30, [], '', '76534', nil, end_date)
cp.refresh_pools(owner['key'])

pool1 = cp.list_pools(:owner => owner['id'], :product => product1['id'])[0]
pool2 = cp.list_pools(:owner => owner['id'], :product => product2['id'])[0]

org_admin_username = random_string("orgadmin")
org_admin_password = 'password'
cp.create_user(org_admin_username, org_admin_password, true)
org_admin_cp = Candlepin.new(org_admin_username, org_admin_password)
consumer = org_admin_cp.register(random_string('dummyconsumer'), "candlepin",
  nil, {}, nil, owner['key'])
consumer_cp = Candlepin.new(nil, nil, consumer['idCert']['cert'], consumer['idCert']['key'],
  HOST, PORT)
consumer_cp.consume_pool(pool1['id'], {:quantity => 20})
consumer_cp.consume_pool(pool2['id'], {:quantity => 30})

# Make a temporary directory where we can safely extract our archive:
tmp_dir = File.join(Dir.tmpdir, random_string('candlepin-export'))
export_dir = File.join(tmp_dir, "export")
Dir.mkdir(tmp_dir)

export_filename = consumer_cp.export_consumer(tmp_dir)
puts "Export: #{export_filename}"
