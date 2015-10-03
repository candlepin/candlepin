#!/usr/bin/env ruby

require_relative "../client/ruby/subservice_api"

require 'thread'

SMALL_SUB_QUANTITY = 5
LARGE_SUB_QUANTITY = 10

filenames=["../../bin/test_data.json"]
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

service = Subservice.new('/etc/candlepin/certs/candlepin-ca-pub.key', 'localhost', 8443, false)

puts ">>Importing content set data..."

def create_content(service, content)
  puts "content: #{content['name']}"

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

  service.create_content(
    content['name'],
    content['id'],
    content['label'],
    content['type'],
    content['vendor'],
    params
  )
end

thread_pool = Pool.new(5)

data['owners'].each do |owner|
    if owner.has_key?('content')
        owner['content'].each do |content|
          thread_pool.schedule do
              create_content(service, content)
          end
        end
    end
end
data['content'].each do |content|
    thread_pool.schedule do
        create_content(service, content)
    end
end
thread_pool.shutdown

puts ">>Import product data..."

def create_product(service, product)
  name = product['name']
  id = product['id']
  multiplier = product['multiplier'] || 1
  version = product['version'] || "1.0"
  variant = product['variant'] || "ALL"
  arch = product['arch'] || "ALL"
  type = product['type'] || "SVC"
  provided_products = product['provided_products'] || []
  attrs = product['attributes'] || {}
  content = product['content'] || []
  dependent_products = product['dependencies'] || []
  relies_on = product['relies_on'] || []
  derived_product_id = product['derived_product_id']
  derived_provided_products = product['derived_provided_products'] || []

  attrs['version'] = version
  attrs['variant'] = variant
  attrs['arch'] = arch
  attrs['type'] = type

  puts "product name: " + name + " version: " + version + \
       " arch: " + arch + " type: " + type

  product_ret = service.create_product(id, name, {:multiplier => multiplier,
                                             :attributes => attrs,
                                             :dependentProductIds => dependent_products,
                                             :content => content})
  return product_ret
end

def create_subscription(service, owner, product, product_ret)

  puts "owner: " + owner['name'] + " product: " + product['name']

  if product.has_key?('skip_subs')
    return
  end

  small_quantity = SMALL_SUB_QUANTITY
  large_quantity = LARGE_SUB_QUANTITY
  if product.has_key?('quantity')
    small_quantity = large_quantity = product['quantity']
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

  subscription = service.create_subscription(
    owner['name'],
    product_ret['id'],
    small_quantity,
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
  subscription = service.create_subscription(
    owner['name'],
    product_ret['id'],
    large_quantity,
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
  subscription = service.create_subscription(
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
candidates_for_subscription =[]

data['owners'].each do |owner|
    if owner.has_key?('products')
        #collect all marketing and engineering products of the owner
        #marketing products are also candidates for subscriptions
        owner['products'].each do |product|
            if product['type'] == 'MKT'
                mkt_products << product
                candidates_for_subscription << [owner, product]
            else
                eng_products << product
            end
        end
    end

    #we need one subscription for each permutation of owner, marketing product
    data['products'].each do |product|
        if product['type'] == 'MKT'
            candidates_for_subscription << [owner, product]
        end
    end
end

data['products'].each do |product|
    if product['type'] == 'MKT'
        mkt_products << product
    else
        eng_products << product
    end
end

puts ">>Creating eng products"
thread_pool = Pool.new(6)
eng_products.each do |eng_product|
    thread_pool.schedule do
        create_product(service, eng_product)
    end
end
thread_pool.shutdown

mkt_products_created = {}
puts ">>Creating mkt products"
thread_pool = Pool.new(1)
mkt_products.each do |mkt_product|
    thread_pool.schedule do
        mkt_product_return = create_product(service, mkt_product)
        mkt_products_created[mkt_product['id']] = mkt_product_return
    end
end
thread_pool.shutdown

puts ">>Creating subscriptions"
thread_pool = Pool.new(6)
candidates_for_subscription.each do |candidate_for_subscription|
    thread_pool.schedule do
        create_subscription(service,
                            candidate_for_subscription[0],
                            candidate_for_subscription[1],
                            mkt_products_created[candidate_for_subscription[1]['id']])
    end
end
thread_pool.shutdown

