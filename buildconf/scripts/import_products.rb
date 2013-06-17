#!/usr/bin/ruby

require  "./client/ruby/candlepin_api"

require 'rubygems'
require 'date'
require 'json'
require 'pp'

SMALL_SUB_QUANTITY = 5
LARGE_SUB_QUANTITY = 10

filenames=["test_products.json"]
if not ARGV.empty?
  filenames.clear
  ARGV.each do |filename|
    filenames.push(filename)
  end
end

data = {}

filenames.each do |filename|
  puts filename
  product_data_buf = File.read(filename)
  product_data = JSON product_data_buf
  data['products'] = data.fetch('products',[]) + product_data['products']
  data['content'] = data.fetch('content',[]) + product_data['content']
  data['owners'] = data.fetch('owners', []) + product_data['owners']
  data['users'] = data.fetch('users', []) + product_data['users']
  data['roles'] = data.fetch('roles', []) + product_data['roles']
end

cp = Candlepin.new(username='admin', password='admin', cert=nil, key=nil, host='localhost', post=8443)

# create some owners
puts "Create some owners"
data["owners"].each do |new_owner|
  owner_name = new_owner['name']
  displayName = new_owner['displayName']

  puts "owner: #{owner_name}"
  puts "\t displayName: #{displayName}"

  owner = cp.create_owner(owner_name, new_owner)

  # Create one dummy activation key for the owner
  cp.create_activation_key(owner['key'], "default_key")
end

puts

# create some users
puts "Create some users"
data["users"].each do |new_user|
  user_name = new_user['username']
  user_pass = new_user['password']
  user_super = new_user['superadmin'] || false

  puts "user: #{user_name}"
  puts "\t password: #{user_pass}"
  puts "\t super_user: #{user_super}"

  owner = cp.create_user(user_name, user_pass, user_super)

end

puts
# Create roles:
puts "Create some roles"
data['roles'].each do |new_role|
  role_name = new_role['name']
  perms = new_role['permissions']
  users = new_role['users']

#  puts " permissions: \t #{pp perms}"
  puts "role_name: #{role_name}"
  perms.each do |perm|
    puts "\t owner: #{perm['owner']}"
    puts "\t access: #{perm['access']}"
  end

  role = cp.create_role(role_name, perms)

  users.each do |user|
    puts "\t user: #{user['username']}"
    cp.add_role_user(role['id'], user['username'])
  end

end

puts

# import all the content sets
puts "Importing content set data..."
data['content'].each do |c|

  puts c['name']

  params = {}
  modified_products = c['modified_products'] || []
  if c.has_key?('metadata_expire')
    params[:metadata_expire] = c['metadata_expire']
  end

  if c.has_key?('required_tags')
    params[:required_tags] = c['required_tags']
  end

  params[:content_url] = c['content_url']
  params[:arches] = c['arches']
  params[:gpg_url] = c['gpg_url']
  params[:modified_products] = modified_products

  cp.create_content(c['name'], c['id'], c['label'], c['type'],
                    c['vendor'], params)
end


owners = cp.list_owners({:fetch => true})
owner_keys = owners.map{|owner| owner['key']}.compact
owner_key = 'admin'

CERT_DIR='generated_certs'
if not File.directory? CERT_DIR
	Dir.mkdir(CERT_DIR)
end

puts
puts "Import product data..."
contract_number = 0
data['products'].each do |product|

  name = product['name']
  id = product['id']
  multiplier = product['multiplier'] || 1
  version = product['version'] || "1.0"
  variant = product['variant'] || "ALL"
  arch = product['arch'] || "ALL"
  type = product['type'] || "SVC"
  provided_products = product['provided_products'] || []
  attrs = product['attributes'] || {}
  product_content = product['content'] || []
  dependent_products = product['dependencies'] || []
  relies_on = product['relies_on'] || []
  sub_product_id = product['sub_product_id']
  sub_provided_products = product['sub_provided_products'] || []

  attrs['version'] = version
  attrs['variant'] = variant
  attrs['arch'] = arch
  attrs['type'] = type
  product_ret = cp.create_product(id, name, {:multiplier => multiplier,
                                             :attributes => attrs,
                                             :dependentProductIds => dependent_products,
                                             :relies_on => relies_on})
  puts "product name: " + name + " version: " + version + \
       " arch: " + arch + " type: " + type
  startDate1 =  Date.today
  endDate1 = startDate1 + 365
  startDate2 = endDate1 - 10
  endDate2 = startDate2 + 365
  startDate3 = endDate1 + 1
  endDate3 = startDate2 + 365

  # If product ID is non-numeric, we assume it's a marketing product
  # and create subscriptions for it:
  if id.to_i.to_s != id
    # Create a SMALL and a LARGE with the slightly similar begin/end dates.
    owner_keys.each do |owner_key|
      subscription = cp.create_subscription(owner_key,
                                            product_ret['id'],
                                            SMALL_SUB_QUANTITY,
                                            provided_products,
                                            contract_number, '12331131231',
                                            'order-8675309',
                                            startDate1, endDate1,
                                            {
                                              'sub_product_id' => sub_product_id,
                                              'sub_provided_products' => sub_provided_products 
                                            })
      contract_number += 1
      subscription = cp.create_subscription(owner_key,
                                            product_ret['id'],
                                            LARGE_SUB_QUANTITY,
                                            provided_products,
                                            contract_number, '12331131231',
                                            'order-8675309',
                                            startDate1, endDate1,
                                            {
                                              'sub_product_id' => sub_product_id,
                                              'sub_provided_products' => sub_provided_products 
                                            })

      # Create a subscription for the future:
      subscription = cp.create_subscription(owner_key, product_ret['id'],
                                            15, provided_products,
                                            contract_number, '12331131231',
                                            'order-8675309',
                                            startDate2, endDate2,
                                            {
                                              'sub_product_id' => sub_product_id,
                                              'sub_provided_products' => sub_provided_products 
                                            })
      contract_number += 1
    end
  end

  # TODO: not sure what's going on here?
  if id.to_i.to_s == id
    product_cert = cp.get_product_cert(product_ret['id'])
    cert_file = File.new(CERT_DIR + '/' + product_ret['id'] + '.pem', 'w+')
    cert_file.puts(product_cert['cert'])
  end

  product_content.each do |content|
    cp.add_content_to_product(product_ret['id'], content[0], content[1])
  end

end

# Refresh to create pools for all subscriptions just created:
owner_keys.each do |owner_key|
    puts "refreshing pools for " + owner_key
    cp.refresh_pools(owner_key)
end
