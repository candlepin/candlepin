#!/usr/bin/env ruby

require_relative "../client/ruby/candlepin_api"
require_relative "./thread_pool"

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
@sourceSubId = 0

filenames.each do |filename|
  puts filename
  product_data_buf = File.read(filename)
  product_data = JSON(product_data_buf, {})
  data['products'] = data.fetch('products',[]) + product_data['products']
  data['content'] = data.fetch('content',[]) + product_data['content']
  data['owners'] = data.fetch('owners', []) + product_data['owners']
  data['users'] = data.fetch('users', []) + product_data['users']
  data['roles'] = data.fetch('roles', []) + product_data['roles']
end

cp = Candlepin.new('admin', 'admin', nil, nil, 'localhost', 8443)

def create_owner(cp, new_owner)
  owner_name =  new_owner['name']
  displayName = new_owner['displayName']

  puts "owner: #{owner_name}"
  puts "\t displayName: #{displayName}"

  owner = cp.create_owner(owner_name, new_owner)

  # Create one dummy activation key for the owner
  cp.create_activation_key(owner['key'], "default_key")
  cp.create_activation_key(owner['key'], "awesome_os_pool")
end

thread_pool = ThreadPool.new(5)
data['owners'].each do |new_owner|
    thread_pool.schedule(new_owner) do |new_owner|
        create_owner(cp, new_owner)
    end
end
thread_pool.shutdown


def create_user(cp, new_user)
  user_name = new_user['username']
  user_pass = new_user['password']
  user_super = new_user['superadmin'] || false

  puts "user: #{user_name}"
  puts "\t password: #{user_pass}"
  puts "\t super_user: #{user_super}"

  cp.create_user(user_name, user_pass, user_super)
end

puts "Create some users"
thread_pool = ThreadPool.new(5)
data['users'].each do |new_user|
    thread_pool.schedule(new_user) {|new_user| create_user(cp, new_user) }
end
thread_pool.shutdown

puts
# Create roles:
puts "Create some roles"
def create_role(cp, new_role)
  role_name = new_role['name']
  perms = new_role['permissions']
  users = new_role['users']

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

thread_pool = ThreadPool.new(5)
data['roles'].each do |new_role|
    thread_pool.schedule(new_role) {|new_role| create_role(cp, new_role) }
end
thread_pool.shutdown



# import all the content sets
puts "Importing content set data..."

def create_content(cp, owner, content)
  puts "#{owner['name']}/#{content['name']}"

  params = {}
  modified_products = content['modified_products'] || []
  if content.has_key?('metadata_expire')
    params[:metadata_expire] = content['metadata_expire']
  end

  if content.has_key?('required_tags')
    params[:required_tags] = content['required_tags']
  end

  params[:content_url] = content['content_url']
  params[:arches] = content['arches']
  params[:gpg_url] = content['gpg_url']
  params[:modified_products] = modified_products

  cp.create_content(
    owner['name'],
    content['name'],
    content['id'],
    content['label'],
    content['type'],
    content['vendor'],
    params
  )
end

thread_pool = ThreadPool.new(5)
data['owners'].each do |owner|
    if owner.has_key?('content')
        owner['content'].each do |content|
          thread_pool.schedule(owner, content) {|owner, content| create_content(cp, owner, content) }
        end
    end

    data['content'].each do |content|
        thread_pool.schedule(owner, content) {|owner, content| create_content(cp, owner, content) }
    end
end
thread_pool.shutdown


owners = cp.list_owners({:fetch => true})
owner_keys = owners.map{|owner| owner['key']}.compact
admin_owner_key = 'admin'

CERT_DIR='generated_certs'
if not File.directory? CERT_DIR
	Dir.mkdir(CERT_DIR)
end

puts
puts "Import product data..."

def create_product(cp, owner, product)
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
  derived_product_id = product['derived_product_id']
  derived_provided_products = product['derived_provided_products'] || []

  attrs['version'] = version
  attrs['variant'] = variant
  attrs['arch'] = arch
  attrs['type'] = type
  product_ret = cp.create_product(owner['name'], id, name, {:multiplier => multiplier,
                                             :attributes => attrs,
                                             :dependentProductIds => dependent_products,
                                             :relies_on => relies_on})
  puts "product name: " + name + " version: " + version + \
       " arch: " + arch + " type: " + type
  return product_ret
end

def create_eng_product(cp, owner, product)
  product_ret = create_product(cp, owner, product)
  product_content = product['content'] || []

  # Generate a product id cert in generated_certs for each engineering product
  product_cert = cp.get_product_cert(owner['name'], product_ret['id'])
  cert_file = File.new(CERT_DIR + '/' + product_ret['id'] + '.pem', 'w+')
  cert_file.puts(product_cert['cert'])

  if not product_content.empty?
    cp.add_all_content_to_product(owner['name'], product_ret['id'], product_content)
  end
end

def create_mkt_product_and_pools(cp, owner, product)
  product_ret = create_product(cp, owner, product)
  if product.has_key?('skip_pools')
    return
  end

  small_quantity = SMALL_SUB_QUANTITY
  large_quantity = LARGE_SUB_QUANTITY
  if product.has_key?('quantity')
    small_quantity = large_quantity = product['quantity']
  end

  params = {}
  params[:provided_products] = product['provided_products'] || []
  params[:derived_product_id] = product['derived_product_id']
  params[:derived_provided_products] = product['derived_provided_products'] || []

  params[:start_date] =  Date.today
  params[:end_date] =  params[:start_date] + 365

  # Create a SMALL and a LARGE with the slightly similar begin/end dates.

  params[:branding] = []
  if !params[:provided_products].empty? && product_ret['name'].include?('OS')
    params[:branding] = [
      {
        :productId => params[:provided_products][0],
        :type => 'OS',
        :name => 'Branded ' + product_ret['name']
      }
    ]
  end

  params[:contract_number] = 0
  params[:account_number] = '12331131231'
  params[:order_number] = 'order-8675309'

  params[:quantity] = small_quantity
  @sourceSubId += 1
  params[:source_subscription] = { 'id' => "#{@sourceSubId}" }

  pool = cp.create_pool(
    owner['name'],
    product_ret['id'],
    params
  )

  params[:contract_number] += 1
  params[:quantity] = large_quantity
  @sourceSubId += 1
  params[:source_subscription] = { 'id' => "#{@sourceSubId}" }

  pool = cp.create_pool(
    owner['name'],
    product_ret['id'],
    params
  )

  # Create a pool for the future:
  params[:quantity] = 15
  params[:start_date] =  params[:end_date] - 10
  params[:end_date] =  params[:start_date] + 365
  @sourceSubId += 1
  params[:source_subscription] = { 'id' => "#{@sourceSubId}" }

  pool = cp.create_pool(
    owner['name'],
    product_ret['id'],
    params
  )

end


eng_products = []
mkt_products = []

data['owners'].each do |owner|
    if owner.has_key?('products')
        owner['products'].each do |product|
            if product['type'] == 'MKT'
                mkt_products << [owner, product]
            else
                eng_products << [owner, product]
            end
        end
    end

    data['products'].each do |product|
        if product['type'] == 'MKT'
            mkt_products << [owner, product]
        else
            eng_products << [owner, product]
        end
    end
end

puts "creating eng products"
thread_pool = ThreadPool.new(6)
eng_products.each do |eng_product|
    thread_pool.schedule(eng_product[0], eng_product[1]) do |owner, product|
      create_eng_product(cp, owner, product)
    end
end
thread_pool.shutdown

puts "creating mkt products and pools"
thread_pool = ThreadPool.new(6)
mkt_products.each do |mkt_product|
    thread_pool.schedule(mkt_product[0], mkt_product[1]) do |owner, product|
      create_mkt_product_and_pools(cp, owner, product)
    end
end
thread_pool.shutdown

def create_activation_key_for_pool(cp, pool, owner_key)
    key_name = owner_key + '-' + pool['productId'] + '-' + pool['contractNumber'] + '-key'
    puts "creating activation_key " + key_name
    key = cp.create_activation_key(owner_key, key_name)
    cp.add_pool_to_key(key['id'], pool['id'])
end

exit

thread_pool = ThreadPool.new(6)
owner_keys.each do |owner_key|
    thread_pool.schedule(owner_key) do |owner_key|
        pools = cp.list_owner_pools(owner_key)
        pools.each do |pool|
            create_activation_key_for_pool(cp, pool, owner_key)
        end
    end
end
thread_pool.shutdown
