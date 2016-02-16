#!/usr/bin/env ruby

require 'optparse'
require 'securerandom'
require 'thread'

require_relative "./name_generator"
require_relative "./thread_pool"
require_relative "../client/ruby/candlepin_api"



####################################################################################################
####################################################################################################
# Pool Generators
####################################################################################################
####################################################################################################

class PoolGenerator
  @@initialized = false

  @@cp = nil
  @@rand = nil

  @@content_id_pool = nil
  @@content_name_generator = nil

  @@product_id_pool = nil
  @@product_name_generator = nil

  def self.init_generators(cp, prng)
    if @@initialized then
      raise "Illegal State"
    end

    @@cp = cp
    @@prng = prng

    @@content_id_pool = 1000000
    @@content_name_generator = NameGenerator.new(%w(content), prng)

    @@product_id_pool = 5000000
    @@product_name_generator = NameGenerator.new(%w(
      add-on        appliance     application   architecture  atomic        balancer
      business      capsule       client        cloud         cloudforms    cluster
      desktop       developer     enterprise    java          jboss         kernel
      linux         mainframe     network       node          openshift     openstack
      OS            platform      premium       preview       product       proxy
      RHEL          satellite     server        software      storage       suite
      system        toolset       vswitch       workstation
    ), prng)

    @@initialized = true
  end

  def generate_content_id()
    if not @@initialized then
      raise "Illegal State"
    end

    result = @@content_id_pool
    @@content_id_pool += 1

    return result
  end

  def generate_content_name
    if not @@initialized then
      raise "Illegal State"
    end

    return @@content_name_generator.generate_name
  end

  def generate_product_id()
    if not @@initialized then
      raise "Illegal State"
    end

    result = @@product_id_pool
    @@product_id_pool += 1

    return result
  end

  def generate_product_name()
    if not @@initialized then
      raise "Illegal State"
    end

    return @@product_name_generator.generate_name
  end

  def generate_pools(owner, pools, products, content)
    raise "Pool generator \"#{self.class.name}\" did not override generate_pools\n"
  end
end


# Unmapped product and content
####################################################################################################
class UnmappedProductContentGenerator < PoolGenerator
  def generate_pools(owner, pools, products, content)
    content << {
      :name => self.generate_content_name,
      :id => self.generate_content_id,
      :content_url => "/foo/path/always",
      :gpg_url => "/foo/path/always/gpg",
    }

    products << {
      :name => self.generate_product_name,
      :id => self.generate_product_id,
      :attributes => {
        :sockets => 2,
        :vcpu => 4
      }
    }
  end
end


# Simple pool generation
####################################################################################################
class SimplePoolGenerator < PoolGenerator
  def generate_pools(owner, pools, products, content)
    eng_content = {
      :name => self.generate_content_name,
      :id => self.generate_content_id,
      :content_url => "/foo/path/always",
      :gpg_url => "/foo/path/always/gpg",
    }

    eng_product = {
      :name => self.generate_product_name,
      :id => self.generate_product_id,
      :version => "6.1",
      :attributes => {
        :sockets => 2,
        :vcpu => 4,
        :warning_period => 30,
        :brand_type => "OS"
      },
      :content => [
        [eng_content[:id], false]
      ]
    }

    mkt_product_name = self.generate_product_name
    mkt_product = {
      :name => mkt_product_name + "(Basic)",
      :id => mkt_product_name,
      :attributes => {
        :sockets => 2,
        :vcpu => 4,
        :warning_period => 30,
        :management_enabled => 0,
        :support_level => "None",
        :support_type => "Self-Support",
        "multi-entitlement" => "no"
      }
    }

    content << eng_content
    products.concat([eng_product, mkt_product])

    pools << {
      :product_id => mkt_product[:id],
      :provided_products => [
        eng_product[:id]
      ]
    }
  end
end


# Shared content and pool generation
####################################################################################################
class SharedObjectPoolGenerator < PoolGenerator

  def initialize()
    super()

    @owner_products = {}
    @owner_content = {}
    @lock = Mutex.new
  end

  def generate_pools(owner, pools, products, content)
    owner_content = nil
    owner_products = nil

    @lock.synchronize do
      owner_content = @owner_content[owner]
      owner_products = @owner_products[owner]

      if owner_content == nil || owner_products == nil
        owner_content = []
        owner_products = []

        # Generate new content and engineering products so the pools and marketing products can
        # reference them safely
        owner_content << {
          :name => self.generate_content_name + " (shared)",
          :id => self.generate_content_id,
          :content_url => "/foo/path/always",
          :gpg_url => "/foo/path/always/gpg",
        }

        content_name = self.generate_content_name + " (shared, tagged)"
        owner_content << {
          :name => content_name,
          :id => self.generate_content_id,
          :label => content_name,
          :type => "yum",
          :vendor => "test-vendor",
          :content_url => "/foo/path/always",
          :gpg_url => "/foo/path/always/gpg",
          :required_tags => "TAG1,TAG2"
        }

        eng_product = {
          :name => self.generate_product_name + " (shared)",
          :id => self.generate_product_id,
          :content => []
        }

        owner_content.each do |content|
          eng_product[:content] << [content[:id], false]
        end

        owner_products << eng_product

        # Note
        # We may need to explicitly create these with CP here to ensure we don't run into a race
        # condition where one thread references the shared objects before they're actually created.
        # That means we'll need to pass in the client instance so we can do it.

        content.concat(owner_content)
        products.concat(owner_products)
      end
    end

    # Generate new marketing product and pool using the above
    mkt_product = {
      :name => self.generate_product_name + " (shared)",
      :id => self.generate_product_id
    }

    products << mkt_product
    pool = {
      :product_id => mkt_product[:id],
      :provided_products => []
    }

    owner_products.each do |product|
      pool[:provided_products] << product[:id]
    end

    pools << pool
  end
end


# Product-modifying Content Sets
####################################################################################################
class ModifiedPoolGenerator < PoolGenerator
  def generate_pools(owner, pools, products, content)
    provided_product_1 = {
      :name => self.generate_product_name,
      :id => self.generate_product_id,
    }

    provided_product_2 = {
      :name => self.generate_product_name,
      :id => self.generate_product_id,
    }

    provided_product_3 = {
      :name => self.generate_product_name,
      :id => self.generate_product_id,
    }

    modifier_content_1 = {
      :name => self.generate_content_name,
      :id => self.generate_content_id,
      :modified_products => [provided_product_1[:id]]
    }

    modifier_content_2 = {
      :name => self.generate_content_name,
      :id => self.generate_content_id,
      :modified_products => [provided_product_2[:id]]
    }

    modifier_content_3 = {
      :name => self.generate_content_name,
      :id => self.generate_content_id,
      :modified_products => [provided_product_3[:id]]
    }

    modifier_provided_product = {
      :name => self.generate_product_name,
      :id => self.generate_product_id,
      :content => [
        [modifier_content_1[:id], true],
        [modifier_content_2[:id], true],
        [modifier_content_3[:id], true]
      ]
    }

    mkt_product = {
      :name => self.generate_product_name + " (Modified)",
      :id => self.generate_product_id,
    }

    content.concat([modifier_content_1, modifier_content_2, modifier_content_3])
    products.concat([provided_product_1, provided_product_2, provided_product_3, modifier_provided_product, mkt_product])

    pools << {
      :product_id => mkt_product[:id],
      :provided_products => [
        modifier_provided_product[:id]
      ]
    }
  end
end


# VDC pool generation
####################################################################################################
class VDCPoolGenerator < PoolGenerator
  def generate_pools(owner, pools, products, content)
      base_product_name = self.generate_product_name
      base_product_id = self.generate_product_id

      base_content_name = self.generate_content_name
      base_content = {
        :name => base_content_name,
        :id => self.generate_content_id,
        :label => base_content_name,
        :type => "yum",
        :vendor => "test-vendor",
        :content_url => "/foo/path",
        :gpg_url => "/foo/path/gpg/",
        :metadata_expire => 0
      }

      modifier_content_name = self.generate_content_name + " (modifier)"
      modifier_content = {
        :name => modifier_content_name,
        :id => self.generate_content_id,
        :label => self.generate_content_name + " (modifier)",
        :type => "yum",
        :vendor => "test-vendor",
        :content_url => "http://example.com/awesomeos-modifier",
        :gpg_url => "http://example.com/awesomeos-modifier/gpg",
        :modified_products => [base_product_id]
      }

      base_product = {
        :name => base_product_name,
        :id => base_product_name,
        :version => "6.1",
        :attributes => {
          "sockets" => 2,
          "vcpu" => 4,
          "warning_period" => 30,
          "brand_type" => "OS"
        },
        :content => [
          [base_content[:id], false]
        ]
      }

      derived_product = {
        :name => self.generate_product_name,
        :id => self.generate_product_id,
        :version => "6.1",
        :content => [[modifier_content[:id], false]]
      }

      vdc_product = {
        :name => base_product_name + " (VDC)",
        :id => self.generate_product_id,
        :attributes => {
          :sockets => 4,
          :vcpu => 8,
          :warning_period => 30,
          :management_enabled => 0,
          :support_level => "None",
          :support_type => "Self-Support",
          'multi-entitlement' => "no",
          :virt_limit => "unlimited",
          :host_limited => "true"
        }
      }

      vdc_pool = {
        :product_id => vdc_product[:id],
        :provided_products => [],
        :derived_product_id => derived_product[:id],
        :derived_provided_products => [base_product[:id]]
      }

      content.concat([base_content, modifier_content])
      products.concat([base_product, derived_product, vdc_product])
      pools << vdc_pool
  end
end



####################################################################################################
####################################################################################################
# Core Data Generator
####################################################################################################
####################################################################################################

class CandlepinDataGenerator
  def initialize(seed = nil, thread_count = 5)
    @seed = seed
    @rand = Random.new(@seed || Random.new_seed)

    @thread_count = thread_count

    @cp = Candlepin.new('admin', 'admin', nil, nil, 'localhost', 8443)

    # This has potential to be a problem if we're generating tons of data. But we need to generate
    # data that is actually connected... For now we'll just store the keys/names, but we may need
    # more in the future
    @owners = Array.new
    @users = Array.new
    @products = Array.new
    @content = Array.new
    @pools = Array.new
  end

  def generate_owners(count)
    create_owner = lambda {|name|
      # TODO: Replace this call with updated CP API
      print "  Creating owner: #{name}\n"
      owner = @cp.create_owner(name.downcase, {:name => name})

      @owners << owner['key']
    }

    name_generator = NameGenerator.new(%w(owner org), @rand)
    thread_pool = ThreadPool.new(@thread_count)

    count.times do
      thread_pool.schedule(name_generator.generate_name, &create_owner)
    end

    thread_pool.shutdown
  end

  def generate_users(count)
    create_user = lambda {|username, password, params|
      # TODO: Replace this call with updated CP API
      print "  Creating user: #{username}\n"
      user = @cp.create_user(username, password, params[:superuser] || false)

      @users << username
    }

    name_generator = NameGenerator.new(%w(user), @rand)
    thread_pool = ThreadPool.new(@thread_count)

    user_requirements = []
    user_requirements << [1, { :superuser => true }]
    user_requirements << [1, { :superuser => false }]

    # Add our required users...
    user_requirements.each do |rcount, params|
      rcount.times do
        thread_pool.schedule(name_generator.generate_name, SecureRandom.hex(32), params, &create_user)
      end

      count -= rcount
    end

    # Fill in the rest of our count with random elements generated from our requirements
    count.times do
      params = user_requirements.sample(random: @rand)[1]
      thread_pool.schedule(name_generator.generate_name, SecureRandom.hex(32), params, &create_user)
    end

    thread_pool.shutdown
  end

  def generate_roles(count)
    create_role = lambda {|role|
      # TODO: Replace this with updated CP API calls
      rid = @cp.create_role(role[:name], role[:permissions])['id']

      role[:users].each do |user|
        @cp.add_role_user(rid, user[:username])
      end
    }

    def build_role(role_name, owner_access, users)
      permissions = []
      users = []

      owner_access.each do |owner, access|
        permissions << {
          :type => "OWNER",
          :owner => owner,
          :access => access
        }
      end

      users.each do |username|
        users << { :username => username }
      end

      return {
        :name => role_name,
        :permissions => permissions,
        :users => users
      }
    end

    # We need to link owners and users to roles through some magic.
    # Things we need:
    #  ALL vs READ_ONLY access
    #  Single-owner roles
    #  Multi-owner roles
    #  Roles linked to a single user
    #  Roles linked to multiple users
    #
    # Actual list will be every iteration of the above (yuck)
    #  Single-owner, ALL access for single user
    #  Single-owner, ALL access for multiple users
    #  Single-owner, READ_ONLY access for single user
    #  Single-owner, READ_ONLY access for multiple users
    #  Multi-owner, ALL access for single user
    #  Multi-owner, ALL access for multiple users
    #  Multi-owner, READ_ONLY access for single user
    #  Multi-owner, READ_ONLY access for multiple users
    #  Multi-owner, mixed access for single user
    #  Multi-owner, mixed access for multiple users

    role_requirements = Array.new
    role_requirements << [1, { :owners => [{:ALL => 1}], :users => 1 }]
    role_requirements << [1, { :owners => [{:ALL => 1}], :users => 2 }]
    role_requirements << [1, { :owners => [{:READ_ONLY => 1}], :users => 1 }]
    role_requirements << [1, { :owners => [{:READ_ONLY => 1}], :users => 2 }]
    role_requirements << [1, { :owners => [{:ALL => 2}], :users => 1 }]
    role_requirements << [1, { :owners => [{:ALL => 2}], :users => 2 }]
    role_requirements << [1, { :owners => [{:READ_ONLY => 2}], :users => 1 }]
    role_requirements << [1, { :owners => [{:READ_ONLY => 2}], :users => 2 }]
    role_requirements << [1, { :owners => [{:ALL => 1}, {:READ_ONLY => 1}], :users => 1 }]
    role_requirements << [1, { :owners => [{:ALL => 1}, {:READ_ONLY => 1}], :users => 2 }]

    thread_pool = ThreadPool.new(@thread_count)
    role_count = 0

    # Add our required users...
    role_requirements.each do |rcount, details|
      rcount.times do
        role_count += 1
        owners_access = []

        details[:owners].each do |acl|
          acl.each do |access, count|
            if @owners.length < count then
              raise "Not enough owners created to meet role requirements"
            end

            owners_access = @owners.sample(count, random: @rand)
            owners_access.each_index do |i|
              owners_access[i] = [owners_access[i], access]
            end
          end
        end

        if @users.length < details[:users] then
          raise "Not enough users created to meet role requirements"
        end

        users = @users.sample(details[:users], random: @rand)

        thread_pool.schedule(build_role("role-#{role_count}", owners_access, users), &create_role)
      end

      count -= rcount
    end

    # Fill in the rest of our count with random elements generated from our requirements
    count.times do
      role_count += 1

      details = role_requirements.sample(random: @rand)[1]

      details[:owners].each do |acl|
        acl.each do |access, count|
          if owners.length < count then
            raise "Not enough owners created to meet role requirements"
          end

          owners = @owners.sample(count, random: @rand)
          owners.each_index do |i|
            owners[i] = [owners[i], access]
          end
        end
      end

      if @users.length < details[:users] then
        raise "Not enough users created to meet role requirements"
      end

      users = @users.sample(details[:users], random: @rand)

      thread_pool.schedule(build_role("role-#{role_count}", owners, users), &create_role)
    end

    thread_pool.shutdown
  end

  def generate_pools(perorg_count)
    source_sub_id_pool = 0
    source_sub_id_pool_lock = Mutex.new

    create_pools = lambda{|owner, pools, products, content|
      content.each do |content|
        label = content[:label] || content[:name]
        type = content[:type] || "yum"
        vendor = content[:vendor] || "test-vendor"

        params = {}
        modified_products = content[:modified_products] || []
        if content.has_key?(:metadata_expire)
          params[:metadata_expire] = content[:metadata_expire]
        end

        if content.has_key?(:required_tags)
          params[:required_tags] = content[:required_tags]
        end

        print "  Creating content: #{content[:id]}\n"
        cp_content = @cp.create_content(owner, content[:name], content[:id], label, type, vendor, params)
      end

      products.each do |product|
        multiplier = product[:multiplier] || 1
        attributes = product[:attributes] || {}
        dependent_products = product[:dependencies] || []
        relies_on = product[:relies_on] || []
        product_content = product[:content] || []

        attributes[:version] = attributes[:version] || "1.0"
        attributes[:varient] = attributes[:varient] || "ALL"
        attributes[:arch] = attributes[:arch] || "ALL"
        attributes[:type] = attributes[:type] || "SVC"

        params = {
          :multiplier => multiplier,
          :attributes => attributes,
          :dependentProductIds => dependent_products,
          :relies_on => relies_on
        }

        if (Integer(product[:id]) != nil rescue false) then
          print "  Creating product ##{product[:id]}: #{product[:name]} \n"
        else
          print "  Creating product: #{product[:id]} \n"
        end

        cp_product = @cp.create_product(owner, product[:id], product[:name], params)

        if not product_content.empty?
          print "    Adding content to product: #{product[:name]}\n"
          @cp.add_all_content_to_product(owner, cp_product['id'], product_content)
        end

        # TODO: Do we need to retrieve the certs for these products...?
      end

      pools.each do |pool|
        params = {}
        params[:provided_products] = pool[:provided_products] || []
        params[:derived_product_id] = pool[:derived_product_id]
        params[:derived_provided_products] = pool[:derived_provided_products] || []
        params[:start_date] =  Date.today
        params[:end_date] =  params[:start_date] + 365

        params[:branding] = pool[:branding]
        # TODO: Do we need to validate or correct branding objects?

        params[:contract_number] = pool[:contract_number]
        params[:account_number] = pool[:account_number]
        params[:order_number] = pool[:order_number]

        params[:quantity] = pool[:quantity] || 1

        source_sub_id_pool_lock.synchronize do
          params[:source_subscription] = { 'id' => (source_sub_id_pool += 1) }
        end

        print "  Creating pool for product: #{pool[:product_id]}\n"
        cp_pool = @cp.create_pool(owner, pool[:product_id], params)
      end
    }

    # Pool generation is a bit strange and difficult to generalize, as we have many examples of
    # intertwined products and content. As such, instead of some cumbersome attempts to describe
    # what's needed, we'll just use a bunch of lambdas that explicitly generate the products and
    # content as they need. Unfortunately, this breaks our count slightly with some generators
    # creating more than one product, but that's a much better solution than what I was initially
    # doing.

    # pool_generators is an array of arrays. The inner array should consist of the following:
    #  required_count (integer), rand_weight (integer), generator class
    #
    # Blocks with a weight of zero will never be selected randomly
    pool_generators = []
    total_weight = 0

    PoolGenerator.init_generators(@cp, @rand)
    pool_generators << [1, 1, UnmappedProductContentGenerator.new]
    pool_generators << [1, 10, SimplePoolGenerator.new]
    pool_generators << [3, 10, SharedObjectPoolGenerator.new]
    pool_generators << [1, 5, ModifiedPoolGenerator.new]
    pool_generators << [1, 10, VDCPoolGenerator.new]

    thread_pool = ThreadPool.new(@thread_count)

    pool_generators.each do |rcount, weight, generator|
      total_weight += weight
    end

    @owners.each do |owner|
      # Generate some per-org products and content
      pool_generators.each do |rcount, weight, generator|
        rcount.times do
          pools = []
          products = []
          content = []

          generator.generate_pools(owner, pools, products, content)
          thread_pool.schedule(owner, pools, products, content, &create_pools)
        end
      end

      perorg_count.times do
        products = []
        content = []
        pools = []

        wrand = @rand.rand(total_weight)
        cweight = 0
        generator = nil

        pool_generators.each_index do |i|
          cweight += pool_generators[i][1]

          if (wrand < cweight)
            generator = pool_generators[i][2]
            break
          end
        end

        generator.generate_pools(owner, pools, products, content)
        thread_pool.schedule(owner, pools, products, content, &create_pools)
      end
    end

    thread_pool.shutdown
  end

  def generate_activation_keys(count)
    # TODO: May not be necessary, as our current import_products doesn't do this.
  end
end



####################################################################################################
####################################################################################################
# Begin CLI Handling
####################################################################################################
####################################################################################################

options = {
  :seed => 8675309,
  :thread_count => 5,

  :owner_count => 10,
  :user_count => 10,
  :role_count => 10,
  :pool_count => 100,

  :safe_generation => false
}

def safeParseInt(value, err_msg)
  begin
    value = Integer(value)

    if value < 1 then
      raise ArgumentError
    end
  rescue ArgumentError
    $stderr.puts err_msg
    exit
  end
end

optparse = OptionParser.new do |opts|
    opts.banner = "Usage: data_generator [options]"

    opts.on('-s', '--seed', '=SEED', "The seed to use for initializing the PRNG.") do |opt|
        options[:seed] = safeParseInt(opt, "Invalid value specified for seed: #{opt}")
    end

    opts.on('-t', '--threads', '=COUNT', "The number of threads to use for generating objects. Defaults to #{options[:thread_count]}.") do |opt|
      options[:thread_count] = safeParseInt(opt, "Invalid value specified for thread count: #{opt}")
    end

    opts.on('-o', '--owners', '=COUNT', "The number of owner/org objects to generate. Defaults to #{options[:owner_count]}.") do |opt|
        options[:owner_count] = safeParseInt(opt, "Invalid value specified for owner count: #{opt}")
    end

    opts.on('-u', '--users', '=COUNT', "The number of user accounts to generate. Defaults to #{options[:user_count]}.") do |opt|
        options[:user_count] = safeParseInt(opt, "Invalid value specified for user count: #{opt}")
    end

    opts.on('-r', '--roles', '=COUNT', "The number of account roles to generate. Defaults to #{options[:role_count]}.") do |opt|
        options[:role_count] = safeParseInt(opt, "Invalid value specified for role count: #{opt}")
    end

    opt_help = "The number of pool objects to generate per owner. Defaults to #{options[:pool_count]}."
    opts.on('-p', '--pools', '=COUNT', opt_help) do |opt|
        options[:pool_count] = safeParseInt(opt, "Invalid value specified for pool count: #{opt}")
    end

    opt_help = 'Whether or not generated names and IDs should be validated with the destination ' +
      "Candlepin server before attempting to use them. Defaults to #{options[:safe_generation]}."
    opts.on('-S', '--safe_generation', opt_help) do |opt|
      options[:safe_generation] = opt
    end

    opts.on( '--help', 'Display help and exit' ) do
        puts opts
        exit
    end
end

optparse.parse!

data_generator = CandlepinDataGenerator.new(options[:seed], options[:thread_count])

puts "Generating owners..."
data_generator.generate_owners(options[:owner_count])

puts "Generating users..."
data_generator.generate_users(options[:user_count])

puts "Generating roles..."
data_generator.generate_roles(options[:role_count])

puts "Generating pools..."
data_generator.generate_pools(options[:pool_count])
