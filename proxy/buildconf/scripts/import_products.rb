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
end

cp = Candlepin.new(username='admin', password='admin', cert=nil, key=nil, host='localhost', post=8443)

owners = cp.get_owners()
owner_id = owners[0]['id']
owner_key = owners[0]['key']

# import all the content sets
data['content'].each do |content|
	pp content
	cp.create_content(content[0], content[1], content[2], content[3],
			 content[4], content[5], content[6])
end


CERT_DIR='generated_certs'
if not File.directory? CERT_DIR
	Dir.mkdir(CERT_DIR)
end


contract_number = 0
data['products'].each do |product|
	  # add arch as an attribute as wel

          pp product
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

          pp version
          pp attrs
          attrs['version'] = version
          attrs['variant'] = variant
	  attrs['arch'] = arch
          attrs['type'] = type
	  product_ret = cp.create_product(name, id, multiplier,
					version, variant, arch, type,
					attrs)
          pp product_ret

          if attrs['type'] == 'MKT':
              # subscription =  cp.create_subscription(owner_id, {'product' => { 'id' => product_ret['id'] }, 
              #                                        'providedProducts' => provided_products,
              #                                        'quantity' => 10,
              #                                          'startDate' => '2007-07-13',
              #                                          'contractNumber' => contract_number,
              #                                          'endDate' => '2012-07-13'})
              subscription = cp.create_subscription(owner_id, product_ret['id'], 10, provided_products,
                                                    contract_number)
            # go ahead and create a token for each subscription, the token itself is just a random int
            token = cp.create_subscription_token({'token' => rand(10000000000), 
                                                   'subscription' => {'id' => subscription['id']}})
            contract_number += 1
          end

          start =  Date.new
	  enddate = Date.new + 365

          if attrs['type'] != 'MKT':
              product_cert = cp.get_product_cert(product_ret['id'])
              cert_file = File.new(CERT_DIR + '/' + product_ret['id'] + '.pem', 'w+')
              cert_file.puts(product_cert['cert'])
          end

	  pp product
	  product_content.each do |content|
		cp.add_content_to_product(product_ret['id'], content[0], content[1])
	  end
end

# tickle the subscriptions to get an entitlement pool
cp.refresh_pools(owner_key)

