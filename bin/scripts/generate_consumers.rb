#!/usr/bin/env ruby
# Candlepin consumer generator
# Generates consumers and entitlements for a Candlepin database

require 'optparse'
require 'set'
require 'thread'

require_relative "../../client/ruby/candlepin_api"



class ThreadPool
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

@options = {}

def log(level, message)
  puts "#{level}: #{message}" unless @options[:silent]
end

# Fetches the quantity to consume for this pool. If the pool cannot be consumed, this function
# returns 0.
def get_quantity_to_consume(pool)
  # Normalize the pool's attributes...
  attribs = {}
  pool['productAttributes'].each do |attribute|
    attribs[attribute['name']] = attribute['value']
  end

  # Step 0: Check for attributes which may screw us
  danger_attribs = %w[virt_only virt_limit]

  # We're just going to assume the presence of any of these attribute means this pool is off-limits for now.
  if danger_attribs.any? { |attrib| attribs.has_key?(attrib) }
    return 0;
  end

  # Check if we're the required consumer type...
  if attribs.has_key?('requires_consumer_type') and attribs['requires_consumer_type'] != 'system'
    return 0;
  end

  # Step 1: identify whether or not the pool has a multiplier
  multiplier = 1
  if attribs.has_key?('instance_multiplier')
    multiplier = Integer(attribs['instance_multiplier']) # if this blows up, we're sending bad values
  end

  # Step 2: Determine whether or not we have enough quantity remaining to consume it
  remaining = Integer(pool['quantity']) - Integer(pool['consumed']) - Integer(pool['exported'])
  return remaining > multiplier ? multiplier : 0
end


# Fetches the pools to consume for a given owner. Output is an array of arrays containing the pool id
# and quantity
def get_pools_to_consume(rand, owner, pools)
  use_random_pools = Integer(@options[:pools]) != nil rescue false

  output = []

  if use_random_pools
    pool_count = Integer(@options[:pools])
    log("INFO", "Using #{pool_count} random pools")
    selected_pool_ids = []

    p = 0
    while p < pool_count
      if pools.empty? || selected_pool_ids.length >= pools.length
        log("ERROR", "Out of pools to consume")
        exit
      end

      keys = pools.keys - selected_pool_ids
      pool_id = keys.sample(random: rand)
      pool = pools[pool_id]
      selected_pool_ids << pool_id

      quantity = get_quantity_to_consume(pool)

      if quantity > 0
        output << [pool_id, quantity]

        # Simulate the consumed count going up so we don't over-consume this pool
        pool['consumed'] = Integer(pool['consumed']) + quantity

        p = p + 1
      else
        log("WARN", "Skipping unconsumable pool: #{pool['productName']}")

        # Remove the pool from our list so we don't randomly select it again
        pools.delete(pool_id)
      end
    end
  else
    user_pools = @options[:pools].split(',')
    log("INFO", "Using pool list: #{@options[:pools]}")

    # Validate and deduplicate user's pool selection...
    pool_ids = Set.new
    bad_pool_ids = Set.new
    user_pools.each do |pool_id|
      if pools.has_key?(pool_id)
        pool_ids << pool_id
      else
        bad_pool_ids << pool_id
      end
    end

    if !bad_pool_ids.empty?
      log("ERROR: Owner \"#{owner}\" does not contain the following pools", "#{bad_pool_ids.inspect()}\n" +
        "When using --pools, be sure to specify an owner that contains the given pools")
    end

    pool_ids.each do |pool_id|
      pool = pools[pool_id]
      quantity = get_quantity_to_consume(pool)

      # Since the user specified this pool explicitly, this is kind of a big deal
      if quantity < 1
        log("ERROR: Unable to consume pool", "#{pool['productName']}")
        exit
      end

      output << [pool_id, quantity]
    end
  end

  return output
end


# Set up the options
optparse = OptionParser.new do |opts|
    file = File.basename(__FILE__)
    opts.banner = "Usage: #{file} [options] [org1 [, org2, [, org3...]]]\n\nOptions:"

    @options[:user] = 'admin'
    opts.on('--username [USER]', 'Username to connect as; defaults to "admin".') do |opt|
        @options[:user] = opt
    end

    @options[:password] = 'admin'
    opts.on('--password [PASSWORD]', 'Password to authenticate the user as; defaults to "admin".') do |opt|
        @options[:password] = opt
    end

    @options[:server] = 'localhost'
    opts.on('--server [SERVERNAME]', String, 'Server name FQDN; defaults to "localhost"') do |opt|
        @options[:server] = opt
    end

    @options[:port] = 8443
    opts.on('--port [PORTNUM]', 'Port number for the Candlepin server; defaults to 8443') do |opt|
        @options[:port] = opt.to_i
    end

    @options[:context] = 'candlepin'
    opts.on('--context [CONTEXT]', 'Context to use; defaults to "candlepin"') do |opt|
        @options[:context] = opt
    end

    @options[:uuid] = nil
    opts.on('--uuid [UUID]', 'UUID to use; defaults to nil') do |opt|
        @options[:uuid] = opt
    end

    @options[:ssl] = true
    opts.on('--nossl', 'Do not use SSL; defaults to false') do
        @options[:ssl] = false
    end

    @options[:trusted_user] = false
    opts.on('--trusted', 'User should be trusted; defaults to false') do
        @options[:trusted_user] = true
    end

    @options[:consumers] = 3
    opts.on('--consumers [CONSUMERS]', 'The number of consumers to generate per org; defaults to 3') do |opt|
        @options[:consumers] = opt.to_i
    end

    @options[:pools] = 3
    opts.on('--pools [POOLS|POOLIDS]', 'The pools to consume per consumer. If this value is numeric, that many ' +
        'pools will be randomly selected to be consumed, otherwise the list is processed as a ' +
        'comma-delimited list of pool IDs; defaults to 3') do |opt|
        @options[:pools] = opt.to_i
    end

    @options[:rng_seed] = 79135
    opts.on('--seed [SEED]', 'Seed to use for any random selection; defaults to 79135') do |opt|
        @options[:rng_seed] = opt.to_i
    end

    @options[:silent] = false
    opts.on( '--silent', 'Disable output while generating consumers' ) do
        @options[:silent] = true
    end

    opts.on('-?', '--help', 'Displays command and option information') do
        puts opts
        exit
    end
end

optparse.parse!

####################################################################################################

# At present we do not have any required parameters

candlepin = Candlepin.new(
    @options[:user], @options[:password], nil, nil, @options[:server], @options[:port], nil,
    @options[:uuid], @options[:trused_user], @options[:context], @options[:ssl]
)

# Fetch known orgs...
owner_pools = {}
candlepin.list_owners().each do |owner|
  owner_pools[owner['key']] = {}
end

if owner_pools.empty?
  log("ERROR", "Candlepin deployment has no owners")
  exit
end

# Ensure the user specified orgs that exist (also deduplicate while we're here)
if !ARGV.empty?
  owners = Set.new
  bad_owners = Set.new

  ARGV.each do |owner|
    if owner_pools.has_key?(owner)
      owners << owner
    else
      bad_owners << owner
    end
  end

  if !bad_owners.empty?
    log("ERROR", "Candlepin deployment does not contain one or more owners: #{bad_owners.inspect()}")
    exit
  end
else
  owners = owner_pools.keys
end

if owners.empty?
  # This shouldn't happen
  log("ERROR", "No owners specified for which to generate consumers")
  exit
end

# Fetch pools for each org...
owners.each do |owner|
  puts
  log("INFO", "Fetching pools for owner: #{owner}")
  pools = candlepin.list_owner_pools(owner)

  if pools.empty?
    log("WARN", "Owner \"#{owner}\" does not have any pools")
    next
  end

  # We'll store filtered pool objects in the event we want to add more pool info later
  pool_keys = %w[id productName productAttributes consumed exported quantity]
  pools.each do |pool|
    owner_pools[owner][pool['id']] = pool.delete_if {|key, val| !pool_keys.include?(key)}
  end

  # Determine which pools we're going to be using...
  seed = @options[:rng_seed] || Random.new_seed
  rand = Random.new(seed)


  # Generate consumers and consume pools
  i = 0
  while i < @options[:consumers] do
    i = i + 1
    consumer_name = "test_consumer-#{owner}.#{seed}.#{i}"

    puts
    log("INFO", "Creating consumer: #{consumer_name}")
    consumer = candlepin.register(consumer_name, "system", nil, {}, nil, owner)

    # Create consumer connection
    log("INFO", "Opening consumer connection")
    consumer_cp = Candlepin.new(nil, nil, consumer['idCert']['cert'], consumer['idCert']['key'],
      @options[:server], @options[:port], nil, @options[:uuid], @options[:trused_user], @options[:context],
      @options[:ssl])

    p2c = get_pools_to_consume(rand, owner, owner_pools[owner])
    p2c.each do |pool_id, quantity|
      pool = owner_pools[owner][pool_id]

      log("INFO", "Consuming pool: #{pool['productName']}")
      consumer_cp.consume_pool(pool_id, {:quantity => quantity})
    end
  end

end

