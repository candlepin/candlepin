#!/usr/bin/ruby


require  "../client/ruby/candlepin_api"
#CP_RUBY = $LOAD_PATH.unshift'/client/ruby'
#CP_RUBY="/home/adrian/src/candlepin/client/ruby"
#$LOAD_PATH << CP_RUBY


require 'rubygems'
require 'date'
require 'json'
#require 'candlepin_api'
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
	  # add arch as an attribute as well
	  arch = product[5]
	  attrs = product[8]
          attrs['version'] = product[3]
          attrs['variant'] = product[4]
	  attrs['arch'] = product[5]
          attrs['type'] = product[6]


  
          pp attrs
	  product_ret = cp.create_product(product[1], product[2],product[3],
					product[4], product[5], product[6], 
					attrs)
          pp product_ret

	  #FIXME: only create subscriptions for MKT/config sku's
          provided_products = Array.new()
          product[7].each do |pid|
              provided_products << {'id' => pid}
          end

          if attrs['type'] == 'MKT':
              subscription =  cp.create_subscription(owner_id, {'product' => { 'id' => product_ret['id'] }, 
                                                     'providedProducts' => provided_products,
                                                     'quantity' => 10,
                                                       'startDate' => '2007-07-13',
                                                       'contractNumber' => contract_number,
                                                       'endDate' => '2012-07-13'})
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
	  product[9].each do |content|
		cp.add_content_to_product(product_ret['id'], content[0], content[1])
	  end
end

# tickle the subscriptions to get an entitlement pool
cp.refresh_pools(owner_key)

