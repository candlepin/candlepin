require 'cgi'
require 'forwardable'
require 'httpclient'
require 'json'
require 'date'
require 'openssl'
require 'uri'

# JSONClient stuff is courtesy of the jsonclient.rb example in the httpclient
# repo
module HTTP
  class Message
    # Returns JSON object of message body
    alias original_content content
    def content
      if JSONClient::CONTENT_TYPE_JSON_REGEX =~ content_type
        JSON.parse(original_content)
      else
        original_content
      end
    end
  end
end

# JSONClient provides JSON related methods in addition to HTTPClient.
class JSONClient < HTTPClient
  CONTENT_TYPE_JSON_REGEX = /(application|text)\/(x-)?json/i

  attr_accessor :content_type_json

  class JSONRequestHeaderFilter
    attr_accessor :replace

    def initialize(client)
      @client = client
      @replace = false
    end

    def filter_request(req)
      req.header['content-type'] = @client.content_type_json if @replace
    end

    def filter_response(req, res)
      @replace = false
    end
  end

  def initialize(*args)
    super
    @header_filter = JSONRequestHeaderFilter.new(self)
    @request_filter << @header_filter
    @content_type_json = 'application/json; charset=utf-8'
  end

  def post(uri, *args, &block)
    @header_filter.replace = true
    request(:post, uri, jsonify(argument_to_hash(args, :body, :header, :follow_redirect)), &block)
  end

  def put(uri, *args, &block)
    @header_filter.replace = true
    request(:put, uri, jsonify(argument_to_hash(args, :body, :header)), &block)
  end

private

  def jsonify(hash)
    if hash.key?(:body)
      hash[:body] = JSON.generate(hash[:body])
    end
    hash
  end
end

# These implementations taken from Rails.
class Array
  def to_param
    collect { |e| e.to_param }.join '/'
  end

  def to_query(key)
    if empty?
      nil.to_query(key)
    else
      collect { |value| value.to_query(key) }.join '&'
    end
  end
end

class Hash
  def to_param
    to_query
  end

  def to_query(namespace = nil)
    collect do |key, value|
      unless value.nil? ||
        (value.is_a?(Hash) || value.is_a?(Array)) && value.empty?
        value.to_query(namespace ? "#{namespace}[#{key}]" : key)
      end
    end.compact.sort! * '&'
  end
end

class Object
  def to_param
    to_s
  end

  def to_query(key)
    "#{CGI.escape(key.to_param)}=#{CGI.escape(to_param.to_s)}"
  end
end

module Candlepin
  module Util
    # Converts a string or symbol to a camel case string or symbol
    def camel_case(s)
      conversion = s.to_s.split('_').inject([]) do |buffer, e|
        buffer.push(buffer.empty? ? e : e.capitalize)
      end.join

      if s.is_a?(Symbol)
        conversion.to_sym
      else
        conversion
      end
    end

    # Convert all keys to camel case.
    def camelize_hash(h)
      camelized = h.each.map do |entry|
        [camel_case(entry.first), entry.last]
      end
      Hash[camelized]
    end

    def build_uri(path, query_hash = {})
      if query_hash.nil? || query_hash.to_query.empty?
        URI::Generic.build(:path => path).to_s
      else
        URI::Generic.build(:path => path, :query => query_hash.to_query).to_s
      end
    end

    # Create a subset of key-value pairs.  This method yields the subset and the
    # original hash to a block so that developers can easily add or
    # tweak key-values.  For example:
    #     h = {:hello => 'world', :goodbye => 'x' }
    #     select_from(h, :hello) do |subset, original|
    #       h[:good_bye] = original[:goodbye]
    #     end
    def select_from(hash, *args, &block)
      missing = args.flatten.reject { |key| hash.key?(key) }
      unless missing.empty?
        raise ArgumentError.new("Missing keys: #{missing}")
      end

      pairs = args.map do |key|
          hash.assoc(key)
      end
      h = Hash[pairs]
      yield h, hash if block_given?
      h
    end

    # Verify that a hash supplied as the first argument contains only keys specified
    # by subsequent arguments.  The purpose of this method is to help developers catch
    # mistakes and typos in the option hashes supplied to methods.  Suppose a method
    # expects to receive a hash with a ":name" key in it and the user sends in a hash with
    # the key ":nsme".  Ordinarily that would be accepted and the incorrect key would be
    # silently ignored.  Meanwhile, calling verify_keys(hash, :name) would raise an error
    # to alert the developer to the mistake.
    #
    # The valid_keys argument can either be a single array of valid keys or the valid keys
    # listed inline.  For example:
    #     verify_keys(hash, defaults.keys)
    #     verify_keys(hash, :name, :rank, :serial_number)
    def verify_keys(hash, *valid_keys)
      keys = Set.new(hash.keys)
      valid_keys = Set.new(valid_keys.flatten)
      unless keys == valid_keys || keys.proper_subset?(valid_keys)
        extra = keys.difference(valid_keys)
        msg = "Hash #{hash} contains invalid keys: #{extra.to_a}"
        raise RuntimeError.new(msg)
      end
    end

    def verify_and_merge(opts, defaults)
      verify_keys(opts, defaults.keys)
      defaults.merge(opts)
    end
  end

  module API
    # I am attempting to follow strict rules with this API module:
    #  * API calls with one parameter can use a normal Ruby method parameter.
    #  * API calls with more than one parameter MUST use an options hash.
    #  * Methods with options hashes should provide reasonable defaults and
    #    merge those defaults with the provided options in a manner similar to
    #      defaults = {:some_parameter => 'sensible default'}
    #      opts = defaults.merge(opts)
    #  * Do NOT use Ruby 2.0 style keyword arguments. This API should remain 1.9.3
    #    compatible.
    #  * All keys in options hashes MUST be symbols.
    #  * Methods SHOULD generally follow these conventions:
    #      - If request is a GET, method begins with get_
    #      - If request is a DELETE, method begins with delete_
    #      - If request is a POST, method begins with create_ or post_
    #      - If request is a PUT, method begins with update_ or put_
    #  * URL construction should be performed with the Ruby URI class and/or the
    #    to_query methods added to Object, Array, and Hash.  No ad hoc string manipulations.

    def self.included(klass)
      klass.class_eval do
        include Util
      end
    end

    attr_writer :uuid
    def uuid
      return @uuid || nil
    end

    def simple_req(verb, path, opts={}, *arg_names, &block)
      if arg_names.empty?
        query_args = nil
      else
        query_args = select_from(opts, *arg_names)
      end

      uri = build_uri(path, query_args)

      if block_given?
        post_body = yield
      else
        post_body = nil
      end

      send(verb, uri, post_body)
    end

    def simple_put(*args, &block)
      simple_req(:put, *args, &block)
    end

    def simple_post(*args, &block)
      simple_req(:post, *args, &block)
    end

    def simple_delete(*args, &block)
      simple_req(:delete, *args, &block)
    end

    def simple_get(*args)
      # Technically a GET is allowed to have a body, but the
      # server is just supposed to ignore it entirely.  I've elected
      # to prohibit a body to prevent likely programmer errors.
      # If a programmer simply must have a GET with a body, they
      # can call simple_req(:get, *args) { block_here }
      if block_given?
        raise ArgumentError.new("No body is allowed with GET")
      end

      simple_req(:get, *args)
    end

    def register(opts = {})
      defaults = {
        :name => nil,
        :type => :system,
        :uuid => uuid,
        :facts => {},
        :username => nil,
        :owner => nil,
        :activation_keys => [],
        :installed_products => [],
        :environment => nil,
        :capabilities => [],
        :hypervisor_id => nil,
      }
      opts = verify_and_merge(opts, defaults)

      consumer_json = select_from(opts, :name, :facts, :uuid) do |h|
        if opts[:hypervisor_id]
          h[:hypervisorId] = {
            :hypervisorId => opts[:hypervisor_id],
          }
        end

        unless opts[:capabilities].empty?
          h[:capabilities] = opts[:capabilities].map do |c|
            Hash[:name, c]
          end
        end
      end

      consumer_json = {
        :type => { :label => opts[:type] },
        :installedProducts => opts[:installed_products],
      }.merge(consumer_json)

      if opts[:environment].nil?
        path = "/consumers"
      else
        path = "/environments/#{opts[:environment]}/consumers"
      end

      query_args = select_from(opts, :username, :owner)
      keys = opts[:activation_keys].join(",")
      query_args[:activation_keys] = keys unless keys.empty?

      uri = build_uri(path, query_args)
      post(uri, consumer_json)
    end

    def post_hypervisor_check_in(opts = {})
      defaults = {
        :owner => nil,
        :host_guest_mapping => {},
        :create_missing => nil,
      }
      opts = verify_and_merge(opts, defaults)

      simple_post('/hypervisors', opts, :owner, :create_missing) do
        opts[:host_guest_mapping]
      end
    end

    def delete_deletion_record(opts = {})
      defaults = {
        :deleted_uuid => nil,
      }
      opts = verify_and_merge(opts, defaults)

      path = "/consumers/#{opts[:deleted_uuid]}/deletionrecord"
      delete(path)
    end

    def get_deleted_consumers(opts = {})
      defaults = {
        :date => nil,
      }
      opts = verify_and_merge(opts, defaults)

      simple_get('/deleted_consumers')
    end

    def update_consumer(opts = {})
      defaults = {
        :uuid => uuid,
        :facts => {},
        :installed_products => [],
        :hypervisor_id => nil,
        :guest_ids => [],
        :autoheal => true,
        :service_level => nil,
        :capabilities => [],
      }
      opts = verify_and_merge(opts, defaults)

      json_body = opts.dup

      json_body[:capabilities].map! do |name|
        { :name => name }
      end

      json_body[:guest_ids].map! do |id|
        { :guestId => id }
      end

      json_body = camelize_hash(json_body)
      path = "/consumers/#{opts[:uuid]}"
      put(path, json_body)
    end

    def update_all_guest_ids(opts = {})
      defaults = {
        :uuid => uuid,
        :guest_ids => [],
      }
      opts = verify_and_merge(opts, defaults)

      path = "/consumers/#{opts[:uuid]}/guestids"
      simple_put(path) do
        opts[:guest_ids].map do |id|
          { :guestId => id }
        end
      end
    end

    def update_guest_id(opts = {})
      defaults = {
        :uuid => uuid,
        :guest_id => nil,
      }
      opts = verify_and_merge(opts, defaults)

      path = "/consumers/#{opts[:uuid]}/guestids/#{guest_id}"
      simple_put(path) do
        { :guestId => opts[:guest_id] }
      end
    end

    def delete_guest_id(opts = {})
      defaults = {
        :uuid => uuid,
        :guest_id => nil,
        :unregister => false,
      }
      opts = verify_and_merge(opts, defaults)

      path = "/consumers/#{opts[:uuid]}/guestids/#{opts[:guest_id]}"
      simple_delete(path, opts, :unregister)
    end

    def get_owners
      get('/owners')
    end
  end

  class NoAuthClient
    include Candlepin::API

    extend Forwardable
    # By extending Forwardable we can simply take useful HTTPClient methods
    # and make them available and then for the implementation we just pass
    # everything through to @client
    #
    # HTTPClient has many methods, but the below seemed like the most useful.
    def_delegators :@client,
      :debug_dev=,
      :delete,
      :delete_async,
      :get,
      :get_async,
      :get_content,
      :post,
      :post_async,
      :post_content,
      :post_content_async,
      :put,
      :put_async,
      :head,
      :options,
      :request,
      :request_async,
      :trace

    attr_accessor :use_ssl
    attr_accessor :host
    attr_accessor :port
    attr_accessor :context
    attr_accessor :insecure
    attr_accessor :ca_path
    attr_accessor :connection_timeout
    attr_accessor :client

    # Build a connection without any authentication
    #
    # = Options
    # * :host:: The host to connect to. Defaults to localhost
    # * :port:: The port to connect to. Defaults to 8443.
    #     Should be provided as an integer.
    # * :context:: The servlet context to use. Defaults to 'candlepin'.
    #     If you do not provide a leading slash, one will be prepended.
    # * :use_ssl:: Whether to connect over SSL/TLS. Defaults to true.
    # * :insecure:: Whether to perform SSL hostname verification and whether to
    #     require a recognized CA. Defaults to <b>true</b> because in testing we
    #     are often dealing with self-signed certificates.
    # * :connection_timeout:: How long in seconds to wait before the connection times
    #     out. Defaults to <b>3 seconds</b>.
    def initialize(opts = {})
      defaults = {
        :host => 'localhost',
        :port => 8443,
        :context => '/candlepin',
        :use_ssl => true,
        :insecure => true,
        :connection_timeout => 3,
      }
      opts = defaults.merge(opts)
      # Subclasses must provide an attr_writer or attr_accessor for every key
      # in the options hash.  The snippet below sends the values to the setter methods.
      opts.each do |k, v|
        self.send(:"#{k}=", v)
      end
      reload
    end

    def context=(val)
      @context = val
      if !val.nil? && val[0] != '/'
        @context = "/#{val}"
      end
    end

    def debug
      self.debug=(true)
    end

    def debug=(value)
      if value
        client.debug_dev = $stdout
      else
        client.debug_dev = nil
      end
    end

    # Create a new HTTPClient. Useful after making configuration changes through the
    # accessors.
    def reload
      @client = raw_client
    end

    # Return the base URL that the Client is using.  Consists of protocol, host, port, and context.
    def base_url
      components = {
        :host => host,
        :port => port,
        :path => context,
      }
      if use_ssl
        uri = URI::HTTPS.build(components)
      else
        uri = URI::HTTP.build(components)
      end
      uri.to_s
    end

    # This method provides the raw HTTPClient object that is being used
    # to communicate with the server.  In most circumstances, you should not
    # need to access it, but it is there if you need it.
    def raw_client
      client = JSONClient.new(:base_url => base_url)
      # Three seconds is the default and that is pretty aggressive, but this code is mainly
      # meant for spec tests and we don't want to wait all day for connections to timeout
      # if something is wrong.
      client.connect_timeout = connection_timeout
      if use_ssl
        if insecure
          client.ssl_config.verify_mode = OpenSSL::SSL::VERIFY_NONE
        else
          client.ssl_config.add_trust_ca(ca_path) if ca_path
        end
      end
      client
    end
  end

  class X509Client < NoAuthClient
    attr_accessor :client_cert
    attr_accessor :client_key

    class << self
      def from_consumer(consumer_json, opts = {})
        if opts.key?(:client_cert) || opts.key?(:client_key)
          raise ArgumentError.new("Cannot specify cert and key for this method")
        end
        client_cert = OpenSSL::X509::Certificate.new(consumer_json['idCert']['cert'])
        client_key = OpenSSL::PKey::RSA.new(consumer_json['idCert']['key'])
        opts = {
          :client_cert => client_cert,
          :client_key => client_key,
        }.merge(opts)
        X509Client.new(opts)
      end

      def from_files(cert, key, opts = {})
        if opts.key?(:client_cert) || opts.key?(:client_key)
          raise ArgumentError.new("Cannot specify cert and key for this method")
        end
        client_cert = OpenSSL::X509::Certificate.new(File.read(cert))
        client_key = OpenSSL::PKey::RSA.new(File.read(key))
        opts = {
          :client_cert => client_cert,
          :client_key => client_key,
        }.merge(opts)
        X509Client.new(opts)
      end
    end

    # Build a connection using an X509 certificate provided as a client certificate
    #
    # = Options
    # * Same as those for NoAuthClient
    # * :client_cert:: An OpenSSL::X509::Certificate object. Defaults to nil.
    # * :client_key:: An OpenSSL::PKey::PKey object. Defaults to nil.
    def initialize(opts = {})
      defaults = {
        :client_cert => nil,
        :client_key => nil,
      }
      opts = defaults.merge(opts)
      super(opts)
    end

    def raw_client
      client = super
      client.ssl_config.client_cert = client_cert
      client.ssl_config.client_key = client_key
      client
    end
  end

  class OAuthClient < NoAuthClient
    def initialize(opts = {})
      defaults = {
        :oauth_key => nil,
        :oauth_secret => nil,
      }
      opts = defaults.merge(opts)
      super(opts)
    end

    def raw_client
      client = super
      # TODO OAuth stuff here
      client
    end
  end

  class BasicAuthClient < NoAuthClient
    attr_accessor :username
    attr_accessor :password

    # Build a connection using HTTP basic authentication.
    #
    # = Options
    # * Same as those for NoAuthClient
    # * :username:: The username to use. Defaults to 'admin'.
    # * :password:: The password to use. Defaults to 'admin'.
    def initialize(opts = {})
      defaults = {
        :username => 'admin',
        :password => 'admin',
      }
      opts = defaults.merge(opts)
      super(opts)
    end

    def raw_client
      client = super
      client.force_basic_auth = true
      client.set_auth(base_url, username, password)
      client
    end
  end
end
