#!/usr/bin/env ruby

require_relative "../client/ruby/candlepin_api"
require_relative "../client/ruby/hostedtest_api"
require_relative "./thread_pool"

require 'rubygems'
require 'date'
require 'json'
require 'pp'

# Impl note:
# We use print instead of puts throughout this script, as a majority of the operations are
# performed in parallel threads. Internally, puts prints the message and an automatic line break as
# two separate, non-atomic operations. This allows time for the threads to print their messages to
# the same line before the line break is written. We work around this here by using print with a
# line break in the message itself, reducing/eliminating the possibility for two+ threads to write
# a message to the same line.

include HostedTest
SMALL_SUB_QUANTITY = 5
LARGE_SUB_QUANTITY = 10

script_home = File.dirname(File.expand_path($0))
filenames=["#{script_home}/test_data.json"]
if not ARGV.empty?
  filenames.clear
  ARGV.each do |filename|
    filenames.push(filename)
  end
end

$data = {'products'=> [], 'content'=> [], 'owners'=> [], 'users'=> [], 'roles'=> []}
@sourceSubId = 0

filenames.each do |filename|
  # puts filename
  product_data_buf = File.read(filename)
  product_data = JSON(product_data_buf, {})
  $data['products'] = $data.fetch('products') + product_data['products'] unless product_data['products'].nil?
  $data['content'] = $data.fetch('content') + product_data['content'] unless product_data['content'].nil?
  $data['owners'] = $data.fetch('owners') + product_data['owners'] unless product_data['owners'].nil?
  $data['users'] = $data.fetch('users') + product_data['users'] unless product_data['users'].nil?
  $data['roles'] = $data.fetch('roles') + product_data['roles'] unless product_data['roles'].nil?
end

cp = Candlepin.new('admin', 'admin', nil, nil, 'localhost', 8443)
@cp = cp

print "\nCreating owners\n"
def create_owner(cp, new_owner)
  owner_name =  new_owner['name']
  displayName = new_owner['displayName']

  print "owner: #{owner_name}\n"
  print "    displayName: #{displayName}\n"

  owner = cp.create_owner(owner_name, new_owner)

  # Create one dummy activation key for the owner
  cp.create_activation_key(owner['key'], "default_key")
  cp.create_activation_key(owner['key'], "awesome_os_pool")
end

thread_pool = ThreadPool.new(5)
$data['owners'].each do |new_owner|
    thread_pool.schedule(new_owner) do |new_owner|
        create_owner(cp, new_owner)
    end
end
thread_pool.shutdown


def create_user(cp, new_user)
  user_name = new_user['username']
  user_pass = new_user['password']
  user_super = new_user['superadmin'] || false

  print "user: #{user_name}\n"
  print "    password: #{user_pass}\n"
  print "    super_user: #{user_super}\n"

  cp.create_user(user_name, user_pass, user_super)
end

print "\nCreate some users\n"
thread_pool = ThreadPool.new(5)
$data['users'].each do |new_user|
    thread_pool.schedule(new_user) {|new_user| create_user(cp, new_user) }
end
thread_pool.shutdown

# Create roles:
print "\nCreate some roles\n"

def create_role(cp, new_role)
  role_name = new_role['name']
  perms = new_role['permissions']
  users = new_role['users']

  print "role_name: #{role_name}\n"
  perms.each do |perm|
    # convert owner key to owner objects
    if (perm['owner'].is_a? String)
      perm['owner'] = { :key => perm['owner'] }
    end

    print "    owner: #{perm['owner']['key']}\n"
    print "    access: #{perm['access']}\n"
  end

  role = cp.create_role(role_name, perms)

  users.each do |user|
    print "    user: #{user['username']}\n"
    cp.add_role_user(role['name'], user['username'])
  end
end

thread_pool = ThreadPool.new(5)
$data['roles'].each do |new_role|
    thread_pool.schedule(new_role) {|new_role| create_role(cp, new_role) }
end
thread_pool.shutdown


def create_content(cp, owner, product, content)
  print "    content: #{owner['name']}/#{content['name']}/#{product['id']}\n"

  params = {}
  modified_products = content['modified_products'] || []
  if content.has_key?('metadata_expire')
    params[:metadata_expire] = content['metadata_expire']
  end

  if content.has_key?('required_tags')
    params[:required_tags] = content['required_tags']
  end

  params[:content_url] = content['content_url'] + '/' + product['id'].to_s
  params[:arches] = content['arches']
  params[:gpg_url] = content['gpg_url']
  params[:modified_products] = modified_products

  content_name = product['id'].to_s + '-' + content['name']
  content_id = product['id'].to_s + content['id'].to_s
  content_label = product['id'].to_s + '-' + content['label']

  cp.create_content(
    owner['name'],
    content_name,
    content_id,
    content_label,
    content['type'],
    content['vendor'],
    params
  )
end


owners = cp.list_owners({:fetch => true})
owner_keys = owners.map{|owner| owner['key']}.compact
admin_owner_key = 'admin'

CERT_DIR='generated_certs'
if not File.directory? CERT_DIR
  Dir.mkdir(CERT_DIR)
end

print "\nImport product data...\n"

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
  derived_product = { :id => product['derived_product_id'] } if product['derived_product_id']

  # To create branding information for marketing product
  if !provided_products.empty? && product['name'].include?('OS') && type == 'MKT'
    branding = [
        {
            :productId => product['provided_products'][0],
            :type => 'OS',
            :name => 'Branded ' + product['name']
        }
    ]
  else
    branding = []
  end

  attrs['version'] = version
  attrs['variant'] = variant
  attrs['arch'] = arch
  attrs['type'] = type

  product_ret = cp.create_product(owner['name'], id, name, {
    :multiplier => multiplier,
    :attributes => attrs,
    :dependentProductIds => dependent_products,
    :relies_on => relies_on,
    :branding => branding,
    :providedProducts => provided_products,
    :derivedProduct => derived_product
  })

  print "product name: #{name} version: #{version} arch: #{arch} type: #{type}; owner: #{owner['name']}\n"
  return product_ret
end


def find_content(content_id)
  $data['owners'].each do |owner|
    if owner.has_key?('content')
      owner['content'].each do |content|
        if content['id'] == content_id
          return content
        end
      end
    end

    $data['content'].each do |content|
      if content['id'] == content_id
        return content
      end
    end
  end
  return nil
end


def create_eng_product(cp, owner, product)
  product_ret = create_product(cp, owner, product)
  product_content = product['content'] || []

  # Generate a product id cert in generated_certs for each engineering product
  attempts = 3
  product_cert = nil

  (0..attempts).each do |i|
    begin
      # This can fail with an ISE, which is bad but we should just retry a couple times
      product_cert = cp.get_product_cert(owner['name'], product_ret['id'])
      break
    rescue RestClient::InternalServerError => e
      sleep(0.5)
    end
  end

  if product_cert
    cert_file = File.new(CERT_DIR + '/' + product_ret['id'] + '.pem', 'w+')
    cert_file.puts(product_cert['cert'])
  else
    raise "Unable to fetch product certificate for product: #{product['id']}"
  end

  if not product_content.empty?
    product_content.each do |content_id, enabled|
      content = find_content(content_id)
      if content != nil
        create_content(cp, owner, product, content)
      end
    end

    # Modify IDs of content in product_content to match ids used in create_content
    prod_id = product['id'].to_s
    mod_prod_content = product_content.map {|content_id, enabled| [prod_id + content_id.to_s, enabled]}
    cp.add_all_content_to_product(owner['name'], product_ret['id'], mod_prod_content)
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

  # Create a SMALL and a LARGE with the slightly similar begin/end dates.
  contract_number = 0
  account_number = '12331131231'
  order_number = 'order-8675309'
  start_date =  Date.today
  end_date =  start_date + 365

  @sourceSubId += 1

  pool = cp.create_pool(owner['name'], product_ret['id'], {
    :quantity => small_quantity,
    :contract_number => contract_number,
    :account_number => account_number,
    :order_number => order_number,
    :start_date => start_date,
    :end_date => end_date,
    :subscription_id => "#{@sourceSubId}"
  })

  contract_number += 1
  @sourceSubId += 1

  pool = cp.create_pool(owner['name'], product_ret['id'], {
    :quantity => large_quantity,
    :contract_number => contract_number,
    :account_number => account_number,
    :order_number => order_number,
    :start_date => start_date,
    :end_date => end_date,
    :subscription_id => "#{@sourceSubId}"
  })

  # Create a pool for the future:
  @sourceSubId += 1

  pool = cp.create_pool(owner['name'], product_ret['id'], {
    :quantity => 15,
    :contract_number => contract_number,
    :account_number => account_number,
    :order_number => order_number,
    :start_date => start_date + 365,
    :end_date => end_date - 10,
    :subscription_id => "#{@sourceSubId}"
  })

end



owner_products = {}
owner_product_refs = {}

$data['owners'].each do |owner|
    product_map = {}
    product_refs = {}

    owner_products[owner] = product_map
    owner_product_refs[owner] = product_refs

    def catalog_product(product_map, product_refs, product)
        product_map[product['id']] = product

        refs = []
        product_refs[product['id']] = refs

        if product.has_key?('provided_products')
            refs.concat(product['provided_products'])
        end

        if product.has_key?('derived_product_id')
            refs << product['derived_product_id']
        end
    end

    $data['products'].each do |product|
        catalog_product(product_map, product_refs, product)
    end

    if owner.has_key?('products')
        owner['products'].each do |product|
            catalog_product(product_map, product_refs, product)
        end
    end
end

print "\nCreating products...\n"
thread_pool = ThreadPool.new(5)

owner_products.each do |owner, product_map|
    product_refs = owner_product_refs[owner]

    def build_product(cp, owner, product_refs, product_map, created_pids, product)
        pid = product['id']

        # Check if we've already created this product
        if created_pids.include?(pid)
            return
        end

        # Create any references first
        if product_refs.has_key?(pid) && !product_refs[pid].empty?
            product_refs[pid].each do |child_pid|
                build_product(cp, owner, product_refs, product_map, created_pids, product_map[child_pid])
            end
        end

        # Create the product
        if product['type'] == 'MKT'
            create_mkt_product_and_pools(cp, owner, product)
        else
            create_eng_product(cp, owner, product)
        end

        # Add its ID to the list of created PIDs
        created_pids << pid
    end

    # Actual per-thread block
    thread_pool.schedule(owner, product_map, product_refs) do |owner, product_map, product_refs|
        created_pids = []

        product_map.each do |pid, product|
            build_product(cp, owner, product_refs, product_map, created_pids, product)
        end
    end
end
thread_pool.shutdown

print "\nrefreshing owners...\n"
thread_pool = ThreadPool.new(6)
owner_keys.each do |owner_key|
    thread_pool.schedule(owner_key) do |owner_key|
        cp.refresh_pools(owner_key)
    end
end
thread_pool.shutdown

print "\nCreating activation keys...\n"
def create_activation_key_for_pool(cp, pool, owner_key)
    key_name = owner_key + '-' + pool['productId'] + '-key-' + pool['id']
    print "creating activation_key " + key_name + "\n"
    key = cp.create_activation_key(owner_key, key_name)
    cp.add_pool_to_key(key['id'], pool['id'])
end

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
