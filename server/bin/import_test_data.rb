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
  print "\t displayName: #{displayName}\n"

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
  print "\t password: #{user_pass}\n"
  print "\t super_user: #{user_super}\n"

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

    print "\t owner: #{perm['owner']['key']}\n"
    print "\t access: #{perm['access']}\n"
  end

  role = cp.create_role(role_name, perms)

  users.each do |user|
    print "\t user: #{user['username']}\n"
    cp.add_role_user(role['name'], user['username'])
  end
end

thread_pool = ThreadPool.new(5)
$data['roles'].each do |new_role|
    thread_pool.schedule(new_role) {|new_role| create_role(cp, new_role) }
end
thread_pool.shutdown


def create_content(cp, owner, product, content)
  print "\t content: #{owner['name']}/#{content['name']}/#{product['id']}\n"

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
  derived_product_id = product['derived_product_id']
  derived_provided_products = product['derived_provided_products'] || []

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
    :branding => branding
  })

  print "product name: #{name} version: #{version} arch: #{arch} type: #{type}\n"
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
  product_cert = cp.get_product_cert(owner['name'], product_ret['id'])
  cert_file = File.new(CERT_DIR + '/' + product_ret['id'] + '.pem', 'w+')
  cert_file.puts(product_cert['cert'])

  if not product_content.empty?
    product_content.each do |content_id, enabled|
      content = find_content(content_id)
      if content != nil
        create_content(cp, owner, product, content)
      end
    end
    # Modify IDs of content in product_content to mach ids used in create_content
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

  params = {}
  params[:provided_products] = product['provided_products'] || []
  params[:derived_product_id] = product['derived_product_id']
  params[:derived_provided_products] = product['derived_provided_products'] || []

  params[:start_date] =  Date.today
  params[:end_date] =  params[:start_date] + 365

  # Create a SMALL and a LARGE with the slightly similar begin/end dates.

  params[:branding] = []

  contract_number = 0
  account_number = '12331131231'
  order_number = 'order-8675309'
  start_date =  Date.today
  end_date =  start_date + 365

  @sourceSubId += 1
  params[:subscription_id] = "#{@sourceSubId}"

  pool = create_pool_and_subscription(
    owner['name'],
    product_ret['id'],
    small_quantity,
    product['provided_products'] || [],
    contract_number,
    account_number,
    order_number,
    start_date,
    end_date,
    true,
    params
  )

  contract_number += 1
  @sourceSubId += 1
  params[:subscription_id] = "#{@sourceSubId}"

  pool = create_pool_and_subscription(
    owner['name'],
    product_ret['id'],
    large_quantity,
    product['provided_products'] || [],
    contract_number,
    account_number,
    order_number,
    start_date,
    end_date,
    true,
    params
  )

  @sourceSubId += 1
  params[:subscription_id] = "#{@sourceSubId}"

  # Create a pool for the future:
  pool = create_pool_and_subscription(
    owner['name'],
    product_ret['id'],
    15,
    product['provided_products'] || [],
    contract_number,
    account_number,
    order_number,
    end_date - 10,
    start_date + 365,
    true,
    params
  )

end


eng_products = []
mkt_products = []

$data['owners'].each do |owner|
    if owner.has_key?('products')
        owner['products'].each do |product|
            if product['type'] == 'MKT'
                mkt_products << [owner, product]
            else
                eng_products << [owner, product]
            end
        end
    end

    $data['products'].each do |product|
        if product['type'] == 'MKT'
            mkt_products << [owner, product]
        else
            eng_products << [owner, product]
        end
    end
end

print "\ncreating eng products\n"
thread_pool = ThreadPool.new(6)
eng_products.each do |eng_product|
    thread_pool.schedule(eng_product[0], eng_product[1]) do |owner, product|
      create_eng_product(cp, owner, product)
    end
end
thread_pool.shutdown

print "\ncreating mkt products and pools\n"
thread_pool = ThreadPool.new(6)
mkt_products.each do |mkt_product|
    thread_pool.schedule(mkt_product[0], mkt_product[1]) do |owner, product|
      create_mkt_product_and_pools(cp, owner, product)
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
