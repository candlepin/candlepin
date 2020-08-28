#!/usr/bin/env ruby
# Upstream subscription loader
# Loads subscription information into the Candlepin HostedTest adapter for testing.
#
# - The provided json file is expected to be an array of subscription objects
# - Upstream subscription arrays can be found by enabling TRACE level logging for an owner
#   during refresh in hosted
# - Any owner defined on the subscriptions will be ignored and overwritten with one generated
#   for the owner key given at runtime

require 'optparse'
require 'json'

require_relative "../client/ruby/candlepin_api"



# Checks that Candlepin is running in standalone mode with the hosted test adapter
def check_candlepin_configuration(candlepin)
    status = candlepin.get_status()
    standalone = (status['standalone'].to_s.downcase == 'true')

    if standalone
        $stderr.puts "ERROR: Cannot inject upstream data; Candlepin is running in standalone mode"
        exit
    end

    hosted_adapter = false

    begin
         response = candlepin.get('/hostedtest/alive', {}, 'text/plain', true)
         hosted_adapter = (response.to_s.downcase == 'true')
    rescue RestClient::ResourceNotFound
        # CP running without the Hosted adapter
    end

    if !hosted_adapter
        $stderr.puts "ERROR: Cannot inject upstream data; Hosted adapter is absent or disabled"
        exit
    end
end

# Loads the given JSON-formatted file and loads the subscription information in the upstream data store
def load_upstream_subscriptions(candlepin, owner_key, subscription_data_file)
    json_file = File.open(subscription_data_file)
    subscriptions = JSON.load(json_file)
    json_file.close()

    subscriptions.each do |subscription|
        subscription['owner'] = { :key => owner_key }

        if subscription['id']
            puts "Loading subscription: #{subscription['id']}"
        else
            puts "Loading new subscription for product #{subscription['product']['name']}"
        end

        begin
            candlepin.post('hostedtest/subscriptions', {}, subscription)
        rescue RestClient::Conflict
            puts "  Subscription #{subscription['id']} already exists; updating existing entry"
            candlepin.put("hostedtest/subscriptions/#{subscription['id']}", {}, subscription)
        end
    end

    puts "Loaded #{subscriptions.length} upstream subscriptions for owner #{owner_key}"
end

# Set up the CLI options
@options = {}
optparse = OptionParser.new do |opts|
    file = File.basename(__FILE__)
    opts.banner = "Usage: #{file} [options] owner_key json_file\n\nOptions:"

    @options[:user] = 'admin'
    opts.on('--username [USER]', 'Username to connect with; defaults to "admin".') do |opt|
        @options[:user] = opt
    end

    @options[:password] = 'admin'
    opts.on('--password [PASSWORD]', 'Password to authenticate the user with; defaults to "admin".') do |opt|
        @options[:password] = opt
    end

    @options[:server] = 'localhost'
    opts.on('--server [SERVERNAME]', String, 'Server FQDN; defaults to "localhost"') do |opt|
        @options[:server] = opt
    end

    @options[:port] = 8443
    opts.on('--port [PORTNUM]', 'Port number for the Candlepin server; defaults to 8443') do |opt|
        @options[:port] = opt.to_i
    end

    @options[:context] = 'candlepin'
    opts.on('--context [CONTEXT]', 'Context to use for the Candlepin connection; defaults to "candlepin"') do |opt|
        @options[:context] = opt
    end

    @options[:uuid] = nil
    opts.on('--uuid [UUID]', 'UUID to use for the Candlepin connection; defaults to nil') do |opt|
        @options[:uuid] = opt
    end

    @options[:ssl] = true
    opts.on('--nossl', 'Do not use SSL; defaults to false') do
        @options[:ssl] = false
    end

    @options[:trusted_user] = false
    opts.on('--trusted', 'If the Candlepin user should be trusted; defaults to false') do
        @options[:trusted_user] = true
    end

    opts.on('-?', '--help', 'Displays command and option information') do
        puts opts
        exit
    end
end

optparse.parse!

candlepin = Candlepin.new(
    @options[:user], @options[:password], nil, nil, @options[:server], @options[:port], nil,
    @options[:uuid], @options[:trused_user], @options[:context], @options[:ssl]
)

check_candlepin_configuration(candlepin)

if ARGV.length < 2
    puts optparse
    exit
end

owner_key = ARGV[0]
json_file = ARGV[1]

load_upstream_subscriptions(candlepin, owner_key, json_file)
