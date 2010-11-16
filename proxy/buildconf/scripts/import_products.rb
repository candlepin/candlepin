#!/usr/bin/ruby

require  "../client/ruby/candlepin_api"

require 'rubygems'
require 'date'
require 'json'
require 'pp'

filenames=["import_products.json"]
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
end

cp = Candlepin.new(username='admin', password='admin', cert=nil, key=nil, host='localhost', post=8443)

# create some owners and users
data["owners"].each do |new_owner|
  owner_name = new_owner['name']
  users = new_owner['users']

  puts "owner: #{owner_name}"

  # Kind of a hack to allow users under
  # the default 'admin' owner
  if owner_name == 'admin'
    owner = { 'key' => 'admin' }
  else
    owner = cp.create_owner(owner_name)
  end

  users.each do |user|
    puts "   user: #{user['username']}"
    cp.create_user(owner['key'], user['username'], user['password'])
  end
end

owners = cp.list_owners({:fetch => true})
owner_key = owners[0]['key']

# import all the content sets
puts "importing content set data..."
data['content'].each do |content|
	puts content[0]
	cp.create_content(content[0], content[1], content[2], content[3],
			 content[4], content[5], content[6])
end


CERT_DIR='generated_certs'
if not File.directory? CERT_DIR
	Dir.mkdir(CERT_DIR)
end


puts "import product data..."
contract_number = 0
data['products'].each do |product|
	  # add arch as an attribute as wel


          # name, hash, multiplier, version, variant, arch, type, childProducts, attributes

          #FIXME: product data import file needs to move to dict's instead of lists
          name = product[1]
          id = product[2]
          multiplier = product[3]
          version = product[4]
          variant = product[5]
          arch = product[6]
          type = product[7]
          provided_products = product[8]
          attrs = product[9]
          product_content = product[10]


          attrs['version'] = version
          attrs['variant'] = variant
          attrs['arch'] = arch
          attrs['type'] = type
          product_ret = cp.create_product(id, name, {:multiplier => multiplier,
              :attributes => attrs})
          puts "product name: " + name + " version: " + version + " arch: " + \
            arch + " type: " + type
          startDate1 =  Date.today
	      endDate1 = startDate1 + 365
          startDate2 = endDate1 - 10
          endDate2 = startDate2 + 365
          startDate3 = endDate1 + 1
          endDate3 = startDate2 + 365

          if id.to_i.to_s != id:
              # subscription =  cp.create_subscription(owner_key, {'product' => { 'id' => product_ret['id'] },
              #                                        'providedProducts' => provided_products,
              #                                        'quantity' => 10,
              #                                          'startDate' => '2007-07-13',
              #                                          'contractNumber' => contract_number,
              #                                          'endDate' => '2012-07-13'})
              # Create a 5 and a 10 with the slightly similar begin/end dates.
              subscription = cp.create_subscription(owner_key, product_ret['id'], 5, provided_products,
                                                    contract_number, '12331131231', startDate1, endDate1)
              contract_number += 1
              subscription = cp.create_subscription(owner_key, product_ret['id'], 10, provided_products,
                                                    contract_number, '12331131231', startDate1, endDate1)
              # go ahead and create a token for each subscription, the token itself is just a random int
              token = cp.create_subscription_token({'token' => rand(10000000000),
                                                   'subscription' => {'id' => subscription['id']}})
              contract_number += 1

              # create a future dated model
              subscription = cp.create_subscription(owner_key, product_ret['id'], 15, provided_products,
                                                    contract_number, '12331131231', startDate2, endDate2)
              contract_number += 1
          end

          if id.to_i.to_s == id:
              product_cert = cp.get_product_cert(product_ret['id'])
              cert_file = File.new(CERT_DIR + '/' + product_ret['id'] + '.pem', 'w+')
              cert_file.puts(product_cert['cert'])
          end

	  product_content.each do |content|
		cp.add_content_to_product(product_ret['id'], content[0], content[1])
	  end
end

# tickle the subscriptions to get an entitlement pool
cp.refresh_pools(owner_key)

