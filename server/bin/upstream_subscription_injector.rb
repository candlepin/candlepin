#!/usr/bin/env ruby
# Candlepin consumer generator
# Generates consumers and entitlements for a Candlepin database

require 'optparse'
require 'set'
require 'thread'

require_relative "../client/ruby/candlepin_api"



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

def terminate(message)
  puts "ERROR: #{message}"
  exit
end

def is_hosted(candlepin)
  return !candlepin.get_status()['standalone']
end

def clear_upstream_data(candlepin)
  candlepin.delete('/hostedtest', {}, nil, true)
end

def create_upstream_subscription(candlepin, subscription_id, owner_key, params = {})
  start_date = params.delete(:start_date) || Date.today
  end_date = params.delete(:end_date) || start_date + 365

  # Define subscription with defaults & specified params
  subscription = {
    'startDate' => start_date,
    'endDate'   => end_date,
    'quantity'  => 1
  }

  # Do not copy these with the rest of the merged keys
  filter = ['id', 'owner', 'ownerId']

  params.each do |key, value|
    # Convert the key to snake case so we can support whatever is thrown at us
    key = key.to_s.gsub(/_(\w)/){$1.upcase}

    if !filter.include?(key)
      subscription[key] = value
    end
  end

  # Forcefully set critical identifiers
  subscription['id'] = subscription_id
  subscription['owner'] = { :key => owner_key }

  return candlepin.post('hostedtest/subscriptions', {}, subscription)
end


# Set up the options
optparse = OptionParser.new do |opts|
  file = File.basename(__FILE__)
  opts.banner = "Usage: #{file} [options] org subscription_file\n\nOptions:"

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

  @options[:clean] = false
  opts.on('--clean', 'Cleans the upstream subscription data before injecting the new data') do
    @options[:clean] = true
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

# Check that the targeted CP is running in hosted mode
terminate("Targeted Candlepin deployment is not running in hosted mode") if !is_hosted(candlepin)

# Fetch known orgs...
owners = []
candlepin.list_owners().each do |owner|
  owners << owner['key']
end

# Ensure an owner was specified and exists
terminate("An owner to receive the subscription data must be specified") if ARGV.empty?

owner_key = ARGV.shift

if owners.empty? || !owners.include?(owner_key)
  log("INFO", "Owner \"#{owner_key}\" does not exist; creating it")
  candlepin.create_owner(owner_key)
end

# Ensure that the subscription data file was specified and exists
terminate("A JSON-formatted subscription data file must be specified") if ARGV.empty?

subscription_file = ARGV.shift
subscriptions = []

begin
  subscriptions = JSON.parse(File.read(subscription_file))
rescue
  terminate("Unable to parse provided subscription file: #{subscription_file}\n"\
    "Ensure the file is readable and is a JSON-formatted subscription or array of subscriptions")
end

# If we only get a single subscription object out of this, convert it to an array for uniform processing
if !subscriptions.is_a?(Array)
  subscriptions = [subscriptions]
end

# If we need to clean the upstream subscription first
if @options[:clean]
  log("INFO", "Cleaning existing upstream subscription data")
  clear_upstream_data(candlepin)
end

count = 0
total = subscriptions.length

subscriptions.each do |subscription|
  sid = subscription['id']
  count = count + 1

  log("INFO", "Persisting data for subscription: #{sid}  (#{count} of #{total})")

  create_upstream_subscription(candlepin, sid, owner_key, subscription)
end

log("INFO", "Finished. Persisted #{count} subscriptions for owner \"#{owner_key}\"")
