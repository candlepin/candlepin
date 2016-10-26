#!/usr/bin/env ruby

require  "../client/ruby/candlepin_api"
require 'pp'

ADMIN_USERNAME = "admin"
ADMIN_PASSWORD = "admin"
HOST = "localhost"
PORT = 8443
OWNER_KEY = "owner"

# ===  Methods ===
def random_string prefix=nil
  prefix ||= "rand"
  return "#{prefix}-#{rand(100000)}"
end

def createBranding(productId, type=nil, name=nil)
    b = Hash.new
    b[:productId] = productId
    b[:type] = type || random_string("BrandingType")
    b[:name] = name || random_string("BrandingName")
    return b
end

def createBrandings(productId, countOfBrandings)
  brandings=Array.new
  i = 0
  begin
    brandings[i] = createBranding(productId)
    i += 1
  end while i < countOfBrandings
  return brandings
end

def create_products_and_subs_with_brandings(cp, owner_key, prods_and_subs, brandings_count)
  results = Hash.new
  subs = Array.new
  products = Array.new
  i = 0
  begin
    product = cp.create_product(random_string(), random_string())
    params = {:branding => createBrandings(product['id'], brandings_count)}
    subs[i] = cp.create_subscription(owner_key, product['id'], 100,
                                     [], '', '12345', nil, nil, nil,params)
    products[i] = product
    i += 1
  end while i < prods_and_subs
  results[:products] = products
  results[:subs] = subs
  return results
end

# Make a temporary directory where we can safely extract our archive:
def makeTempDir
  tmp_dir = File.join(Dir.tmpdir, random_string('candlepin-export'))
  export_dir = File.join(tmp_dir, "export")
  Dir.mkdir(tmp_dir)
  return tmp_dir
end

def delete_consumers(cp)
  consumers = cp.list_consumers
	consumers.each do |consumer|
	  cp.unregister(consumer['uuid']) #cp_id_cert
	  puts "unregister consumer '#{consumer['uuid']}'"

	  cp.remove_deletion_record(consumer['uuid']) #cp_deleted_consumer
		puts "deletion record for consumer '#{consumer['uuid']}' removed"
	end
end

def delete_consumers_quiet(cp)
 consumers = cp.list_consumers
	consumers.each do |consumer|
	  cp.unregister(consumer['uuid']) #cp_id_cert
	  cp.remove_deletion_record(consumer['uuid']) #cp_deleted_consumer
	end
end

def delete_roles(cp)
  roles = cp.list_roles
	roles.each do |role|
		cp.delete_role(role['id'])
		puts "role '#{role['id']}' deleted"
	end
end

def delete_roles_quiet(cp)
  roles = cp.list_roles
	roles.each do |role|
	  cp.delete_role(role['id'])
	end
end

def delete_users(cp)
  users = cp.list_users
	users.each do |user|
	  if user['username'] != 'admin' then # admin is default user in DB
		  cp.delete_user(user['username'])
		  puts "user '#{user['username']}' deleted"
	  end
	end
end

def delete_users_quiet(cp)
  users = cp.list_users
	users.each do |user|
	  if user['username'] != 'admin' then # admin is default user in DB
		  cp.delete_user(user['username'])
	  end
	end
end

def delete_owners(cp)
  owners = cp.list_owners
	owners.each do |owner|
	  cp.delete_owner(owner['key'],true)
    puts "owner '#{owner['key']}' deleted"
	end
end

def delete_owners_quiet(cp)
  owners = cp.list_owners
	owners.each do |owner|
	  cp.delete_owner(owner['key'],true)
	end
end

def delete_products(cp)
	products = cp.list_products
	products.each do |product|
		cp.delete_product(product['id'])
		puts "product '#{product['id']}' deleted"
	end
end

def delete_products_quiet(cp)
	products = cp.list_products
	products.each do |product|
	  cp.delete_product(product['id'])
	end
end

def delete_created_data(cp)
	delete_consumers(cp)
  delete_roles(cp)
	delete_users(cp)
  delete_owners(cp)
	delete_products(cp)
	#cp_cert_serial -> don't have method to remove this in candlepin_api.rb
  #omited data in these tables: cp_event, qrtz_fired_triggers
end

def delete_created_data_quiet(cp)
	delete_consumers_quiet(cp)
  delete_roles_quiet(cp)
	delete_users_quiet(cp)
  delete_owners_quiet(cp)
	delete_products_quiet(cp)
	#cp_cert_serial -> don't have method to remove this in candlepin_api.rb
  #omited data in these tables: cp_event, qrtz_fired_triggers
end

def concurrent_import_and_undo_manifest(cp, export1_filename, export2_filename)
    delete_created_data_quiet(cp)
		owner = cp.create_owner(OWNER_KEY)
		cp.import(owner['key'], export1_filename)

		t1 = Thread.new do
			cp.undo_import(owner['key']) #doesn't undo everything (e.g. products)
		end
		cp.import(owner['key'], export2_filename)

		thr = t1.join
		return thr
end

def time_limit_on_thread_expired(thr)
 return (thr == nil)
end
# -----------------------------------------------------

#create hosted CP: cp
cp = Candlepin.new(ADMIN_USERNAME, ADMIN_PASSWORD, nil, nil, HOST, PORT)
delete_created_data(cp)
owner = cp.create_owner(OWNER_KEY)

#create downstream CP: owner_client
user = cp.create_user("orgadmin", 'password', true)
role = cp.create_role(random_string("role"),[{:type => "OWNER", :owner => {:key => owner['key']}, :access => 'ALL'}])
cp.add_role_user(role['id'], user['username'])
owner_client = Candlepin.new(user['username'], 'password')

#create consumer client:
consumer = owner_client.register(random_string('consumer'), "candlepin", user['username'], {}, nil, owner['key'])
candlepin_client = Candlepin.new(nil, nil, consumer['idCert']['cert'], consumer['idCert']['key'], HOST, PORT)

#create and consume all pool of created product/sub
results = create_products_and_subs_with_brandings(cp, owner['key'], 20, 1)
cp.refresh_pools(owner['key'])

products = results[:products]
products.each do |product|
   pools = cp.list_pools(:owner => owner['id'], :product => product['id'])

   pools.each do |pool|
    candlepin_client.consume_pool(pool['id'], {:quantity => 1})
    puts "consuming pool '#{pool['id']}'"
   end
end

export1_filename = candlepin_client.export_consumer(makeTempDir())
puts "1st export : '#{export1_filename}'"

#change: update branding on subscriptions and create export again
subs = results[:subs]
subs.each do |sub|
    b = createBranding(sub['product']['id'],
                       "UPDATE: BrandingType", "UPDATE: UpdateName")
    sub['branding'] = [b]
    cp.update_subscription(sub)
end
cp.refresh_pools(owner['key'])

export2_filename = candlepin_client.export_consumer(makeTempDir())
puts "2nd (updated) export : '#{export2_filename}'"

i = 0
count = 100
fails = 0
begin
	begin
		thr = concurrent_import_and_undo_manifest(cp, export1_filename, export2_filename)

		if time_limit_on_thread_expired(thr)
		 puts "Replication No. #{i} - fail (time limit on thread expired)"
		 fails += 1
	   i += 1
	   next
		end
	rescue
	  puts "Replication No. #{i} - fail"
	  fails += 1
	  i += 1
	  next
	end
	puts "Replication No. #{i} - ok"
	i += 1
end while i < count

puts "+---------------------------+"
puts "#{count} replication done!"
puts "Total fails: #{fails}"
puts "+---------------------------+"
