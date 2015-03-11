#!/usr/bin/env ruby

require_relative "../client/ruby/candlepin_api"

require 'rubygems'
require 'date'
require 'json'
require 'pp'
require 'thread'

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
  product_data = JSON(product_data_buf, {})
  data['products'] = data.fetch('products',[]) + product_data['products']
  data['content'] = data.fetch('content',[]) + product_data['content']
  data['owners'] = data.fetch('owners', []) + product_data['owners']
  data['users'] = data.fetch('users', []) + product_data['users']
  data['roles'] = data.fetch('roles', []) + product_data['roles']
end

# from http://burgestrand.se/articles/quick-and-simple-ruby-thread-pool.html
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

thread_pool = Pool.new(4)
data['owners'].each do |new_owner|
    thread_pool.schedule do
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
thread_pool = Pool.new(4)
data['users'].each do |new_user|
    thread_pool.schedule do
        create_user(cp, new_user)
    end
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

thread_pool = Pool.new(4)
data['roles'].each do |new_role|
    thread_pool.schedule do
        create_role(cp, new_role)
    end
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

thread_pool = Pool.new(4)
data['owners'].each do |owner|
    if owner.has_key?('content')
        owner['content'].each do |content|
          thread_pool.schedule do
              create_content(cp, owner, content)
          end
        end
    end

    data['content'].each do |content|
        thread_pool.schedule do
            create_content(cp, owner, content)
        end
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

def create_eng_product(cp, thread_pool, owner, product)
  product_ret = create_product(cp, owner, product)
  product_content = product['content'] || []

  # Generate a product id cert in generated_certs for each engineering product
  product_cert = cp.get_product_cert(product_ret['id'])
  cert_file = File.new(CERT_DIR + '/' + product_ret['id'] + '.pem', 'w+')
  cert_file.puts(product_cert['cert'])

  product_content.each do |content|
    cp.add_content_to_product(owner['name'], product_ret['id'], content[0], content[1])
  end
end

def create_mkt_product(cp, owner, product)
  product_ret = create_product(cp, owner, product)

  if product.has_key?('skip_subs')
    return
  end

  provided_products = product['provided_products'] || []
  derived_product_id = product['derived_product_id']
  derived_provided_products = product['derived_provided_products'] || []

  startDate1 =  Date.today
  endDate1 = startDate1 + 365
  startDate2 = endDate1 - 10
  endDate2 = startDate2 + 365
  startDate3 = endDate1 + 1
  endDate3 = startDate2 + 365

  contract_number = 0
  # Create a SMALL and a LARGE with the slightly similar begin/end dates.

  brandings = []
  if !provided_products.empty? && product_ret['name'].include?('OS')
    brandings = [
      {
        :productId => provided_products[0],
        :type => 'OS',
        :name => 'Branded ' + product_ret['name']
      }
    ]
  end

  subscription = cp.create_subscription(
    owner['name'],
    product_ret['id'],
    SMALL_SUB_QUANTITY,
    provided_products,
    contract_number,
    '12331131231',
    'order-8675309',
    startDate1,
    endDate1,
    {
      'derived_product_id' => derived_product_id,
      'derived_provided_products' => derived_provided_products,
      :branding => brandings
    }
  )

  contract_number += 1
  subscription = cp.create_subscription(
    owner['name'],
    product_ret['id'],
    LARGE_SUB_QUANTITY,
    provided_products,
    contract_number,
    '12331131231',
    'order-8675309',
    startDate1,
    endDate1,
    {
      'derived_product_id' => derived_product_id,
      'derived_provided_products' => derived_provided_products,
      :branding => brandings
    }
  )

  # Create a subscription for the future:
  subscription = cp.create_subscription(
    owner['name'],
    product_ret['id'],
    15,
    provided_products,
    contract_number,
    '12331131231',
    'order-8675309',
    startDate2,
    endDate2,
    {
      'derived_product_id' => derived_product_id,
      'derived_provided_products' => derived_provided_products,
      :branding => brandings
    }
  )

  contract_number += 1
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
thread_pool = Pool.new(6)
eng_products.each do |eng_product|
    thread_pool.schedule do
        create_eng_product(cp, thread_pool, eng_product[0], eng_product[1])
    end
end
thread_pool.shutdown

puts "creating mkt products"
thread_pool = Pool.new(6)
mkt_products.each do |mkt_product|
    thread_pool.schedule do
        create_mkt_product(cp, mkt_product[0], mkt_product[1])
    end
end
thread_pool.shutdown

# Refresh to create pools for all subscriptions just created:
thread_pool = Pool.new(4)
owner_keys.each do |owner_key|
    thread_pool.schedule do
        puts "refreshing pools for " + owner_key
        cp.refresh_pools(owner_key)
    end
end
thread_pool.shutdown

def create_activation_key_for_pool(cp, pool, owner_key)
    #pp pool contractNumber
    key_name = owner_key + '-' + pool['productId'] + '-' + pool['contractNumber'] + '-key'
    puts "creating activation_key " + key_name
    key = cp.create_activation_key(owner_key, key_name)
    cp.add_pool_to_key(key['id'], pool['id'])
end

exit

thread_pool = Pool.new(6)
owner_keys.each do |owner_key|
    thread_pool.schedule do
        pools = cp.list_owner_pools(owner_key)
        pools.each do |pool|
            create_activation_key_for_pool(cp, pool, owner_key)
        end
    end
end
thread_pool.shutdown
