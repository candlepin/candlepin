require 'active_support/all'
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
      if JSONClient::CONTENT_TYPE_JSON_REGEX =~ content_type && !original_content.empty?
        json = JSON.parse(original_content)
        if json.is_a?(Array)
          json.map! do |i|
            i.deep_symbolize_keys!
          end
        else
          json.deep_symbolize_keys!
        end
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

  class AcceptTypeHeaderFilter
    def initialize(client, mime_type)
      @client = client
      @mime_type = mime_type
    end

    def filter_request(req)
      req.header['accept'] = @mime_type
    end

    def filter_response(req, res)
    end
  end

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
    @content_type_json = 'application/json'
    @fail_fast = keyword_argument(args, :fail_fast).first
  end

  # Takes in a uri, either a hash or individual arguments, and a block
  # If a hash is given then the body, query string, headers, etc. are just
  # passed through to HTTPClient's post.  The block is always passed through untouched
  #
  # If individual arguments are given they must be provided in the following order:
  #   body
  #   query
  #   header
  #   follow_redirect
  #
  # And any trailing nil arguments can be omitted.
  #
  # Examples:
  # json_body = {...}
  # query_args = {...}
  # post('/path', json_body)
  # post('/path', json_body, query_args)
  # post('/path', json_body, query_args, {'Accept' => 'text/html'})
  # post('/path', :query => query_args, :body => json_body)

  def post(uri, *args, &block)
    @header_filter.replace = true
    request(:post, uri, jsonify(argument_to_hash(args, :body, :query, :header, :follow_redirect)), &block)
  end

  # See documentation for post
  def put(uri, *args, &block)
    @header_filter.replace = true
    request(:put, uri, jsonify(argument_to_hash(args, :body, :query, :header)), &block)
  end

  def delete(uri, *args, &block)
    @header_filter.replace = true
    request(:delete,  uri, jsonify(argument_to_hash(args, :body, :query, :header)), &block)
  end

  def request(method, uri, *args, &block)
    # Hack to address https://github.com/nahi/httpclient/issues/285
    # We need to strip off any leading slash on a relative URL
    u = HTTPClient::Util.urify(uri)
    if @base_url && u.scheme.nil? && u.host.nil?
      uri = uri[1..-1] if uri[0] == "/"
    end
    res = super
    if !res.ok? && @fail_fast
      raise BadResponseError.new("Failed request: #{res.header.inspect}")
    end
    res
  end

  def request_async2(method, uri, *args, &block)
    # Hack to address https://github.com/nahi/httpclient/issues/285
    # We need to strip off any leading slash on a relative URL
    u = HTTPClient::Util.urify(uri)
    if @base_url && u.scheme.nil? && u.host.nil?
      uri = uri[1..-1] if uri[0] == "/"
    end
    super
  end

  # This method appears to be deprecated in HTTPClient but I am
  # overriding it here in case other HTTPClient internals use it.
  def request_async(method, uri, query = nil, body = nil, header = {})
    # Hack to address https://github.com/nahi/httpclient/issues/285
    # We need to strip off any leading slash on a relative URL
    u = HTTPClient::Util.urify(uri)
    if @base_url && u.scheme.nil? && u.host.nil?
      uri = uri[1..-1] if uri[0] == "/"
    end
    super
  end

private

  def jsonify(hash)
    if !hash.nil? && hash.key?(:body)
      hash[:body] = JSON.generate(hash[:body]) unless hash[:body].nil?
    end
    hash
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
    def camelize_hash(h, *args)
      h = h.slice(*args) unless args.nil? || args.empty?
      camelized = h.each.map do |entry|
        [camel_case(entry.first), entry.last]
      end
      Hash[camelized]
    end

    # Create a subset of key-value pairs.  This method yields the subset and the
    # original hash to a block so that developers can easily add or
    # tweak the resultant hash.  For example:
    #     h = {:hello => 'world', :goodbye => 'bye' }
    #     select_from(h, :hello) do |subset, original|
    #       h[:send_off] = original[:goodbye].upcase
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

    # Validate the value associated with a hash key.  By default, the method validates
    # the key is not nil, but if a block is passed then the block will be evaluated and
    # if the block returns a false value the value will be considered invalid.
    def validate_keys(hash, *check_keys, &block)
      if check_keys.empty?
        check_keys = hash.keys
      end

      invalid = []
      check_keys.each do |k|
        if block_given?
          invalid << k unless yield hash[k]
        else
          invalid << k if hash[k].nil?
        end
      end

      unless invalid.empty?
        raise RuntimeError.new("Hash #{hash} cannot have nil for keys #{invalid}")
      end
    end

    def verify_and_merge(opts, defaults)
      opts.assert_valid_keys(*defaults.keys)
      defaults.deep_merge(opts)
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
    #      - If request is a POST, method begins with create_, add_, or post_
    #      - If request is a PUT, method begins with update_ or put_
    #      - Aliases are acceptable, but use alias_method instead of just alias
    #  * URL construction should be performed with the Ruby URI class and/or the
    #    to_query methods added to Object, Array, and Hash.  No ad hoc string manipulations.

    # TODO At some point it might make more sense to set up some AOP advice at
    # the "before method call" joinpoint around defining, merging, and
    # validating the default options.  (The Aquarium gem seems to be a good fit)
    # E.g.
    #
    # req_defaults :username => nil, :password => nil
    # def create_user(opts)
    #   do stuff here
    # end
    #
    # Really what we need is a Python-type decorator.  There is an implementation at
    # https://github.com/wycats/ruby_decorators but it uses evals and a lot of tricky
    # meta-programming.
    #
    # Thor does method decorators but it's quite complex.

    def self.included(klass)
      # Mixin the Util module's methods into this module
      klass.class_eval do
        include Util
      end
      # Automatically include child Modules
      # This type of meta-programming is a little too complicated for my taste,
      # but the API has so many methods that it becomes difficult to navigate the
      # file if the methods aren't grouped into logical sections.
      klass.constants.each do |sym|
        klass.class_eval do
          include const_get(sym) if const_get(sym).kind_of?(Module)
        end
      end
    end

    attr_writer :uuid
    def uuid
      return @uuid || nil
    end

    attr_writer :key
    def key
      return @key || nil
    end

    def page_options
      return {
        :page => nil,
        :per_page => nil,
        :order => nil,
        :sort_by => nil,
      }
    end

    module GenericResource
      # There are so many GET /resource/:id methods that it
      # makes sense to provide a generic implementation.  The
      # opts hash that is passed in may have only one key and
      # that key's value will be used as the id.
      def get_by_id(resource, key, opts = {})
        # We don't reference the key by name since different
        # methods can use slightly different names.  E.g.
        # serial_id, uuid, id, etc.
        defaults = {
          key => nil,
        }
        opts = verify_and_merge(opts, defaults)
        validate_keys(opts)

        get("#{resource}/#{opts[key]}")
      end

      def delete_by_id(resource, key, opts = {})
        defaults = {
          key => nil,
        }
        opts = verify_and_merge(opts, defaults)
        validate_keys(opts)

        delete("#{resource}/#{opts[key]}")
      end
    end

    module CrlResource
      def get_crl
        res = get_text('/crl')
        OpenSSL::X509::CRL.new(res.content)
      end
    end

    module StatisticsResource
      def put_statistics
        put("/statistics/generate")
      end
    end

    module SerialsResource
      def get_serial(opts = {})
        get_by_id("/serials", :serial_id, opts)
      end
    end

    module EventsResource
      def get_events
        get("/events")
      end
    end

    module JobsResource
      def get_job(opts = {})
        get_by_id("/jobs", :job_id, opts)
      end

      def get_owner_jobs(opts = {})
        defaults = {
          :owner => key,
        }
        opts = verify_and_merge(opts, defaults)

        get("/jobs", opts)
      end

      def get_scheduler_status
        get("/jobs/scheduler")
      end

      def set_scheduler_status(opts = {})
        opts = {
          :status => false,
        }
        opts = verify_and_merge(opts, defaults)

        post("/jobs/scheduler", opts[:status])
      end

      def delete_job(opts = {})
        delete_by_id("/jobs", :job_id, opts)
      end
    end

    module ConsumerTypeResource
      def create_consumer_type(opts = {})
        defaults = {
          :label => nil,
          :manifest => false,
        }
        opts = verify_and_merge(opts, defaults)

        post("/consumertypes", opts)
      end

      def get_all_consumer_types
        get("/consumertypes")
      end

      def get_consumer_type(opts = {})
        get_by_id("/consumertypes", :type_id, opts)
      end

      def delete_consumer_type(opts = {})
        delete_by_id("/consumertypes", :type_id, opts)
      end
    end

    module StatusResource
      def get_status
        get('/status')
      end
    end

    module ConsumerResource
      def register_and_get_client(opts = {})
        res = register(opts)
        unless res.ok?
          raise HTTPClient::BadResponseError.new("Could not register: #{res.header.inspect}")
        end
        opts = @client_opts.dup
        opts.delete(:username)
        opts.delete(:password)
        x509_client = X509Client.from_consumer(res.content, opts)
        x509_client
      end

      def register(opts = {})
        defaults = {
          :name => nil,
          :type => :system,
          :uuid => uuid,
          :facts => {},
          :username => nil,
          :owner => key,
          :activation_keys => [],
          :installed_products => [],
          :environment => nil,
          :capabilities => [],
          :hypervisor_id => nil,
        }
        opts = verify_and_merge(opts, defaults)

        unless opts[:installed_products].kind_of?(Array)
          opts[:installed_products] = [opts[:installed_products]]
        end

        consumer_json = opts.slice(:name, :facts, :uuid)

        if opts[:hypervisor_id]
          consumer_json[:hypervisorId] = {
            :hypervisorId => opts[:hypervisor_id],
          }
        end

        unless opts[:capabilities].empty?
          consumer_json[:capabilities] = opts[:capabilities].map do |c|
            Hash[:name, c]
          end
        end

        consumer_json = {
          :type => { :label => opts[:type] },
        }.merge(consumer_json)

        consumer_json[:installed_products] = []
        opts[:installed_products].each do |ip|
          if ip.kind_of?(Hash)
            consumer_json[:installed_products] << { :id => ip[:id] }
          else
            consumer_json[:installed_products] << { :id => ip }
          end
        end

        if opts[:environment].nil?
          path = "/consumers"
        else
          path = "/environments/#{opts[:environment]}/consumers"
        end

        query_args = opts.slice(:username, :owner)
        keys = opts[:activation_keys].join(",")
        query_args[:activation_keys] = keys unless keys.empty?

        post(path, :query => query_args, :body => consumer_json)
      end

      def bind(opts = {})
        # Entitle dates are not allowed in bind by product.
        default_date = Date.today
        if opts.key?(:pool_id)
          default_date = nil
        end

        # Quantities are not allowed in auto-binds
        default_quantity = 1
        if opts.key?(:product)
          default_quantity = nil
        end

        defaults = {
          :uuid => uuid,
          :product => nil,
          :quantity => default_quantity,
          :async => false,
          :entitle_date => default_date,
          :pool_id => nil,
        }
        opts = verify_and_merge(opts, defaults)
        validate_keys(opts, :uuid)

        if !opts[:product].nil? && !opts[:pool_id].nil?
          raise ArgumentError.new("Bind by pool or by product but not by both")
        end

        query_args = opts.slice(
          :product,
          :quantity,
          :async,
          :entitle_date,
        )
        # Use the option :pool_id for consistency among all the other method calls
        # Just transparently transform it to match the API's requirements.
        query_args[:pool] = opts[:pool_id]
        query_args.compact!

        post("/consumers/#{opts[:uuid]}/entitlements", :query => query_args)
      end

      def delete_deletion_record(opts = {})
        defaults = {
          :deleted_uuid => nil,
        }
        opts = verify_and_merge(opts, defaults)
        validate_keys(opts, :deleted_uuid)

        path = "/consumers/#{opts[:deleted_uuid]}/deletionrecord"
        delete(path)
      end

      def delete_all_entitlements(opts = {})
        defaults = {
          :uuid => uuid,
        }
        opts = verify_and_merge(opts, defaults)
        validate_keys(opts, :uuid)

        delete("/consumers/#{opts[:uuid]}/entitlements")
      end

      def delete_entitlement(opts = {})
        defaults = {
          :uuid => uuid,
          :entitlement_id => nil,
        }
        opts = verify_and_merge(opts, defaults)
        validate_keys(opts, :uuid, :entitlement_id)

        get("/consumers/#{opts[:uuid]}/entitlements/#{opts[:entitlement_id]}")
      end

      def delete_consumer(opts = {})
        defaults = {
          :uuid => uuid,
        }
        opts = verify_and_merge(opts, defaults)
        validate_keys(opts, :uuid)

        delete("/consumers/#{opts[:uuid]}")
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
        validate_keys(opts, :uuid)

        body = opts.dup

        body[:capabilities].map! do |name|
          { :name => name }
        end

        body[:guest_ids].map! do |id|
          { :guestId => id }
        end

        body = camelize_hash(body)
        path = "/consumers/#{opts[:uuid]}"
        put(path, body)
      end

      def get_consumer(opts = {})
        # Can't use get_by_id here because of our usage of the
        # "sticky" uuid.
        defaults = {
          :uuid => uuid,
        }
        opts = verify_and_merge(opts, defaults)
        validate_keys(opts, :uuid)

        get("/consumers/#{opts[:uuid]}")
      end

      def get_consumer_events(opts = {})
        defaults = {
          :uuid => uuid,
        }
        opts = verify_and_merge(opts, defaults)
        validate_keys(opts, :uuid)

        get("/consumers/#{opts[:uuid]}/events")
      end

      def get_consumer_host(opts = {})
        defaults = {
          :uuid => uuid,
        }
        opts = verify_and_merge(opts, defaults)
        validate_keys(opts, :uuid)

        get("/consumers/#{opts[:uuid]}/host")
      end

      def get_consumer_guests(opts = {})
        defaults = {
          :uuid => uuid,
        }
        opts = verify_and_merge(opts, defaults)
        validate_keys(opts, :uuid)

        get("/consumers/#{opts[:uuid]}/guests")
      end

      def get_consumer_events_atom(opts = {})
        defaults = {
          :uuid => uuid,
        }
        opts = verify_and_merge(opts, defaults)
        validate_keys(opts, :uuid)

        get_text("/consumers/#{opts[:uuid]}/atom")
      end

      def get_consumer_cert_serials(opts = {})
        defaults = {
          :uuid => uuid,
        }
        opts = verify_and_merge(opts, defaults)
        validate_keys(opts, :uuid)

        get("/consumers/#{opts[:uuid]}/certificates/serials")
      end

      def get_consumer_certificates(opts = {})
        defaults = {
          :uuid => uuid,
          :serials => [],
        }
        opts = verify_and_merge(opts, defaults)
        validate_keys(opts, :uuid, :serials)

        unless opts[:serials].kind_of?(Array)
          opts[:serials] = [opts[:serials]]
        end

        get("/consumers/#{opts[:uuid]}/certificates", :query => {
          :serials => opts[:serials].join(",")
        })
      end

      def regen_certificates_by_consumer(opts = {})
        defaults = {
          :uuid => uuid,
          :entitlement_id => nil,
          :lazy_regen => true,
        }
        opts = verify_and_merge(opts, defaults)
        validate_keys(opts, :uuid)

        query = opts.slice(:lazy_regen)
        query[:entitlement] = opts[:entitlement_id] if opts[:entitlement_id]

        put("/consumers/#{opts[:uuid]}/certificates",
          :query => query)
      end

      def update_all_guest_ids(opts = {})
        defaults = {
          :uuid => uuid,
          :guest_ids => [],
        }
        opts = verify_and_merge(opts, defaults)

        path = "/consumers/#{opts[:uuid]}/guestids"
        body = opts[:guest_ids].map do |id|
            { :guestId => id }
        end
        put(path, body)
      end

      def update_guest_id(opts = {})
        defaults = {
          :uuid => uuid,
          :guest_id => nil,
        }
        opts = verify_and_merge(opts, defaults)
        validate_keys(opts, :uuid, :guest_id)

        path = "/consumers/#{opts[:uuid]}/guestids/#{opts[:guest_id]}"
        put(path, camelize_hash(opts, :guest_id))
      end

      def get_all_guest_ids(opts = {})
        defaults = {
          :uuid => uuid,
        }
        opts = verify_and_merge(opts, defaults)
        validate_keys(opts, :uuid)

        get("/consumers/#{opts[:uuid]}/guestids")
      end

      def get_guest_id(opts = {})
        defaults = {
          :uuid => uuid,
          :guest_id => nil,
        }
        opts = verify_and_merge(opts, defaults)

        get("/consumers/#{opts[:uuid]}/guestids/#{opts[:guest_id]}")
      end

      def delete_guest_id(opts = {})
        defaults = {
          :uuid => uuid,
          :guest_id => nil,
          :unregister => false,
        }
        opts = verify_and_merge(opts, defaults)
        validate_keys(opts, :uuid, :guest_id)

        path = "/consumers/#{opts[:uuid]}/guestids/#{opts[:guest_id]}"
        delete(path, opts.slice(:unregister))
      end
    end

    module EnvironmentResource
      def get_environments
        get("/environments")
      end

      def get_environment(opts = {})
        get_by_id("/environments", :id, opts)
      end

      def delete_environment(opts = {})
        delete_by_id("/environments", :id, opts)
      end

      # Note that the enabled flag passed in will be applied to
      # *all* content ids provided.
      def promote_content(opts = {})
        defaults = {
          :env_id => nil,
          :content_ids => nil,
          :enabled => true,
          :lazy_regen => true,
        }
        opts = verify_and_merge(opts, defaults)
        validate_keys(opts, :env_id, :content_ids)

        unless opts[:content_ids].kind_of?(Array)
          opts[:content_ids] = [opts[:content_ids]]
        end

        body = []
        opts[:content_ids].each do |c|
          body << {
            :contentId => c,
            :environmentId => opts[:env_id],
            :enabled => opts[:enabled]
          }
        end

        url = "/environments/#{opts[:env_id]}/content"
        post(url,
          :body => body,
          :query => opts.slice(:lazy_regen))
      end

      def demote_content(opts = {})
        defaults = {
          :env_id => nil,
          :content_ids => [],
          :lazy_regen => true,
        }
        opts = verify_and_merge(opts, defaults)
        validate_keys(opts, :env_id, :content_ids)

        unless opts[:content_ids].kind_of?(Array)
          opts[:content_ids] = [opts[:content_ids]]
        end
        url = "/environments/#{opts[:env_id]}/content"
        query = opts.slice(:lazy_regen)
        query[:content] = opts[:content_ids]
        delete(url, :query => query)
      end

      def create_consumer_in_environment(opts = {})
        defaults = {
          :env_id => nil,
          :username => nil,
          :owner => key,
          :activation_keys => [],
          :consumer => nil,
        }
        opts = verify_and_merge(opts, defaults)
        validate_keys(opts, :env_id, :owner, :consumer)

        opts.compact!
        query = opts.slice(:username, :owner, :activation_keys)

        url = "/environments/#{opts[:env_id]}/consumers"
        post(url, :body => opts[:consumer], :query => query)
      end
    end

    module ActivationKeyResource
      def get_activation_key(opts = {})
        get_by_id("/activation_keys", :id, opts)
      end

      def get_all_activation_keys(opts = {})
        get("/activation_keys")
      end

      def delete_activation_key(opts = {})
        delete_by_id("/activation_keys", :id, opts)
      end

      # Using this method is not recommended as the JSON structure
      # for an activation key is complex and no assistance is provided
      # for building that structure.
      def update_activation_key(opts = {})
        defaults = {
          :id => nil,
          :activation_key => nil,
        }
        opts = verify_and_merge(opts, defaults)
        validate_keys(opts, :id, :activation_key)

        put("/activation_keys/#{opts[:id]}", :body => opts[:activation_key])
      end

      def get_activation_key_pools(opts = {})
        defaults = {
          :id => nil,
        }
        opts = verify_and_merge(opts, defaults)
        validate_keys(opts, :id)

        get("/activation_keys/#{opts[:id]}/pools")
      end

      def add_pool_to_activation_key(opts = {})
        defaults = {
          :id => nil,
          :pool_id => nil,
          :quantity => 1,
        }
        opts = verify_and_merge(opts, defaults)
        validate_keys(opts, :id, :pool_id)

        post("/activation_keys/#{opts[:id]}/pools/#{opts[:pool_id]}", :query => opts.slice(:quantity))
      end

      def delete_pool_from_activation_key(opts = {})
        defaults = {
          :id => nil,
          :pool_id => nil,
        }
        opts = verify_and_merge(opts, defaults)
        validate_keys(opts, :id, :pool_id)
        delete("/activation_keys/#{opts[:id]}/pools/#{opts[:pool_id]}")
      end

      def get_activation_key_products(opts = {})
        defaults = {
          :id => nil,
        }
        opts = verify_and_merge(opts, defaults)
        validate_keys(opts, :id)

        get("/activation_keys/#{opts[:id]}/products")
      end

      def add_product_to_activation_key(opts = {})
        defaults = {
          :id => nil,
          :product_id => nil,
        }
        opts = verify_and_merge(opts, defaults)
        validate_keys(opts, :id, :product_id)

        post("/activation_keys/#{opts[:id]}/product/#{opts[:product_id]}")
      end

      def delete_product_from_activation_key(opts = {})
        defaults = {
          :id => nil,
          :product_id => nil,
        }
        opts = verify_and_merge(opts, defaults)
        validate_keys(opts, :id, :product_id)

        delete("/activation_keys/#{opts[:id]}/product/#{opts[:product_id]}")
      end

      def get_activation_key_overrides(opts = {})
        defaults = {
          :id => nil,
        }
        opts = verify_and_merge(opts, defaults)
        validate_keys(opts, :id)

        get("/activation_keys/#{opts[:id]}/content_overrides")
      end

      def add_overrides_to_activation_key(opts = {})
        defaults = {
          :id => nil,
          :overrides => [],
        }
        opts = verify_and_merge(opts, defaults)
        validate_keys(opts, :id)

        unless opts[:overrides].kind_of?(Array)
          opts[:overrides] = [opts[:overrides]]
        end

        override_defaults = {
          :content_label => nil,
          :name => nil,
          :value => nil,
        }
        body = []
        override_objects = opts[:overrides]
        override_objects.each do |override|
          override = verify_and_merge(override, override_defaults)
          body << camelize_hash(override)
        end

        put("/activation_keys/#{opts[:id]}/content_overrides", body)
      end

      def delete_overrides_from_activation_key(opts = {})
        defaults = {
          :id => nil,
          :overrides => [],
        }
        opts = verify_and_merge(opts, defaults)
        validate_keys(opts, :id)

        unless opts[:overrides].kind_of?(Array)
          opts[:overrides] = [opts[:overrides]]
        end

        override_defaults = {
          :content_label => nil,
          :name => nil,
          :value => nil,
        }
        body = []
        override_objects = opts[:overrides]
        override_objects.each do |override|
          override = verify_and_merge(override, override_defaults)
          body << camelize_hash(override)
        end

        delete("/activation_keys/#{opts[:id]}/content_overrides", body)
      end
    end

    module HypervisorResource
      def post_hypervisor_check_in(opts = {})
        defaults = {
           :owner => key,
           :host_guest_mapping => {},
           :create_missing => nil,
         }
         opts = verify_and_merge(opts, defaults)

         body = opts[:host_guest_mapping]
         post('/hypervisors', :query => opts.slice(:owner, :create_missing), :body => body)
      end
    end

    module DeletedConsumerResource
      def get_deleted_consumers(opts = {})
        defaults = {
          :date => nil,
        }
        opts = verify_and_merge(opts, defaults)

        get('/deleted_consumers')
      end
    end

    module EntitlementResource
      def get_entitlement(opts = {})
        get_by_id("/entitlements", :entitlement_id, opts)
      end

      def get_upstream_certificate(opts = {})
        defaults = {
          :id => nil,
        }
        opts = verify_and_merge(opts, defaults)

        get_text("/entitlements/#{opts[:id]}/upstream_cert")
      end

      def regen_certificates_by_product(opts = {})
        defaults = {
          :product_id => nil,
          :lazy_regen => true,
        }
        opts = verify_and_merge(opts, defaults)
        validate_keys(opts, :product_id)

        put("/entitlements/product/#{opts[:product_id]}",
          :query => opts.slice(:lazy_regen))
      end

      def update_entitlement(opts = {})
        defaults = {
          :id => nil,
          :quantity => 1,
        }
        opts = verify_and_merge(opts, defaults)

        path = "/entitlements/#{opts[:id]}"
        put(path, opts)
      end

      def update_entitlement_consumer(opts = {})
        defaults = {
          :id => nil,
          :to_consumer => nil,
          :quantity => 1,
        }
        opts = verify_and_merge(opts, defaults)

        path = "/entitlements/#{opts[:id]}"
        put(path, opts.slice(:to_consumer, :quantity))
      end
    end

    module UserResource
      def create_user(opts = {})
        defaults = {
          :username => nil,
          :password => nil,
          :super_admin => false,
        }
        opts = verify_and_merge(opts, defaults)
        post("/users", camelize_hash(opts))
      end

      def create_user_under_owner(opts = {})
        defaults = {
          :username => nil,
          :password => nil,
          :super_admin => false,
          :owner => key,
        }
        opts = verify_and_merge(opts, defaults)

        all_roles = ok_content(get_all_roles)

        role_name = "#{opts[:owner]}-ALL"

        role = all_roles.select { |r| r[:name] == role_name }.first
        if role.nil?
          if opts[:super_admin]
            perm = all_owner_permission(opts[:owner])
          else
            perm = ro_owner_permission(opts[:owner])
          end
          role = ok_content(create_role(:name => role_name, :permissions => perm))
        end

        user = ok_content(create_user(opts.slice(:username, :password, :super_admin)))
        ok_content(add_role_user(:role_id => role[:id], :username => opts[:username]))

        # Add password to returned user so it can be passed in to a new
        # BasicAuthClient
        user[:password] = opts[:password]
        user
      end

      def update_user(opts = {})
        defaults = {
          :username => nil,
          :password => nil,
          :super_admin => false,
        }
        opts = verify_and_merge(opts, defaults)
        put("/users/#{opts[:username]}", camelize_hash(opts))
      end

      def get_user(opts = {})
        get_by_id("/users", :username, opts)
      end

      def get_user_roles(opts = {})
        defaults = {
          :username => nil,
        }
        opts = verify_and_merge(opts, defaults)
        get("/users/#{opts[:username]}/roles")
      end

      def get_user_owners(opts = {})
        defaults = {
          :username => nil,
        }
        opts = verify_and_merge(opts, defaults)
        get("/users/#{opts[:username]}/owners")
      end

      def delete_user(opts = {})
        delete_by_id("/users", :username, opts)
      end

      def get_all_users
        get('/users')
      end
    end

    module RoleResource
      def create_role(opts = {})
        defaults = {
          :name => nil,
          :permissions => [],
        }
        opts = verify_and_merge(opts, defaults)

        unless opts[:permissions].kind_of?(Array)
          opts[:permissions] = [opts[:permissions]]
        end

        post("/roles", opts)
      end

      def all_owner_permission(owner_key)
        perm = {}
        perm[:access] = 'ALL'
        perm[:type] = 'OWNER'
        perm[:owner] = { :key => owner_key }
        perm
      end

      def ro_owner_permission(owner_key)
        perm = {}
        perm[:access] = 'READ_ONLY'
        perm[:type] = 'OWNER'
        perm[:owner] = { :key => owner_key }
        perm
      end

      def update_role(opts = {})
        defaults = {
          :role_id => nil,
          :users => [],
          :permissions => [],
          :name => nil,
        }
        opts = verify_and_merge(opts, defaults)

        put("/roles/#{opts[:role_id]}", opts)
      end

      def get_role(opts = {})
        get_by_id("/roles", :role_id, opts)
      end

      def get_all_roles
        get("/roles")
      end

      def delete_role(opts = {})
        delete_by_id("/roles", :role_id, opts)
      end

      def add_role_user(opts = {})
        defaults = {
          :role_id => nil,
          :username => nil,
        }
        opts = verify_and_merge(opts, defaults)

        post("/roles/#{opts[:role_id]}/users/#{opts[:username]}")
      end

      def delete_role_user(opts = {})
        defaults = {
          :role_id => nil,
          :username => nil,
        }
        opts = verify_and_merge(opts, defaults)

        delete("/roles/#{opts[:role_id]}/users/#{opts[:username]}")
      end

      def add_role_permission(opts = {})
        defaults = {
          :role_id => nil,
          :type => nil,
          :owner => key,
          :access => 'READ_ONLY',
        }
        opts = verify_and_merge(opts, defaults)

        permission = opts.slice(:owner, :access, :type)
        post("/roles/#{opts[:role_id]}/permissions/", permission)
      end

      def delete_role_permission(opts = {})
        defaults = {
          :role_id => nil,
          :permission_id => nil,
        }
        opts = verify_and_merge(opts, defaults)

        delete("/roles/#{opts[:role_id]}/permissions/#{opts[:permission_id]}")
      end
    end

    module OwnerResource
      def get_owner(opts = {})
        defaults = {
          :owner => key,
        }
        opts = verify_and_merge(opts, defaults)

        get("/owners/#{opts[:owner]}")
      end

      def get_owner_hypervisors(opts = {})
        defaults = {
          :owner => key,
          :hypervisor_ids => [],
        }
        opts = verify_and_merge(opts, defaults)

        get("/owners/#{opts[:owner]}/hypervisors", :hypervisor_id => opts[:hypervisor_ids])
      end

      def get_owner_subresource(subresource, opts = {})
        defaults = {
          :owner => key,
        }
        opts = verify_and_merge(opts, defaults)
        validate_keys(opts, :owner)

        get("/owners/#{opts[:owner]}/#{subresource}")
      end

      def get_owner_info(opts = {})
        get_owner_subresource("info", opts)
      end

      def get_owner_events(opts = {})
        get_owner_subresource("events", opts)
      end

      def get_owner_imports(opts = {})
        get_owner_subresource("imports", opts)
      end

      def get_owner_consumers(opts = {})
        defaults = {
          :owner => key,
          :types => [],
          :facts => [],
        }
        opts = verify_and_merge(opts, defaults)
        validate_keys(opts, :owner)

        unless opts[:types].kind_of?(Array)
          opts[:types] = [opts[:types]]
        end

        unless opts[:facts].kind_of?(Array)
          opts[:facts] = [opts[:facts]]
        end

        get("/owners/#{opts[:owner]}/consumers",
          :type => opts[:types],
          :facts => opts[:facts])
      end

      def get_owner_subscriptions(opts = {})
        get_owner_subresource("subscriptions", opts)
      end

      def get_owner_activation_keys(opts = {})
        get_owner_subresource("activation_keys", opts)
      end

      def get_owner_service_levels(opts = {})
        defaults = {
          :owner => key,
          :exempt => false,
        }
        opts = verify_and_merge(opts, defaults)

        get("/owners/#{opts[:owner]}/servicelevels", opts.slice(:exempt))
      end

      def get_owner_environment(opts = {})
        defaults = {
          :owner => key,
          :name => nil,
        }
        opts = verify_and_merge(opts, defaults)

        get("/owners/#{opts[:owner]}/environments", opts.slice(:name))
      end

      def get_all_owners
        get("/owners")
      end

      def get_owner_pools(opts = {})
        defaults = {
          :owner => key,
          :consumer => nil,
          :product => nil,
          :listall => nil,
          :attributes => [],
        }.merge(page_options)

        opts = verify_and_merge(opts, defaults)
        validate_keys(opts, :owner)
        opts.compact!

        params = opts.deep_dup.except!(:owner)
        params[:attributes] = opts[:attributes].map do |k, v|
          { :name => k, :value => v }
        end

        get("/owners/#{opts[:owner]}/pools", params)
      end

      def autoheal_owner(opts = {})
        defaults = {
          :owner => key,
        }
        opts = verify_and_merge(opts, defaults)

        post("/owners/#{opts[:owner]}/entitlements")
      end

      def create_owner(opts = {})
        defaults = {
          :owner => key,
          :display_name => nil,
          :parent_owner => nil,
        }
        opts = verify_and_merge(opts, defaults)
        # The model in actually expects "key", but I'm keeping
        # it as owner for consistency
        body = opts.except(:owner)
        body[:key] = opts[:owner]

        post("/owners", camelize_hash(body))
      end

      def create_owner_environment(opts = {})
        defaults = {
          :owner => key,
          :id => nil,
          :name => nil,
          :description => nil,
        }
        opts = verify_and_merge(opts, defaults)

        post("/owners/#{opts[:owner]}/environments", opts.slice(:id, :name, :description))
      end

      def create_ueber_cert(opts = {})
        defaults = {
          :owner => key,
        }
        opts = verify_and_merge(opts, defaults)

        post("/owners/#{opts[:owner]}/uebercert")
      end

      def refresh_pools_async(opts = {})
        defaults = {
          :owner => key,
          :auto_create_owner => false,
          :lazy_regen => false,
        }
        opts = verify_and_merge(opts, defaults)
        validate_keys(opts, :owner)

        put_async("/owners/#{opts[:owner]}/subscriptions",
            :query => opts.slice(:auto_create_owner, :lazy_regen))
      end

      # Same as refresh_pools_async but just block before returning
      def refresh_pools(opts = {})
        connection = refresh_pools_async(opts)
        connection.join
        connection.pop
      end

      def create_activation_key(opts = {})
        defaults = {
          :owner => key,
          :service_level => nil,
          :name => nil,
          :description => nil,
        }
        opts = verify_and_merge(opts, defaults)
        validate_keys(opts, :owner, :name)

        body = camelize_hash(
          opts.deep_dup.compact.except(:owner)
        )
        post("/owners/#{opts[:owner]}/activation_keys", body)
      end

      def create_subscription(opts = {})
        defaults = {
          :owner => key,
          :start_date => Date.today,
          :end_date => Date.today + 365,
          :quantity => 1,
          :account_number => '',
          :order_number => '',
          :contract_number => '',
          :product_id => nil,
          :provided_products => [],
          :derived_products => [],
          :derived_provided_products => [],
        }
        opts = verify_and_merge(opts, defaults)

        body = camelize_hash(
          opts.deep_dup.compact.except(
            :owner,
            :product_id,
            :provided_products,
            :derived_products,
            :derived_provided_products,
          )
        )

        body[:product] = { :id => opts[:product_id] }
        opts.slice(
          :provided_products,
          :derived_products,
          :derived_provided_products).each do |k, v|
            prods = []
            v = [v] unless v.kind_of?(Array)
            v.each do |p|
              prods << { :id => p }
            end
            body[camel_case(k)] = prods unless prods.empty?
        end

        post("/owners/#{opts[:owner]}/subscriptions", body)
      end

      def update_owner(opts = {})
        defaults = {
          :owner => key,
          :display_name => nil,
          :parent_owner => nil,
          :default_service_level => nil,
          :content_prefix => nil,
          :log_level => nil,
        }
        opts = verify_and_merge(opts, defaults)

        body = camelize_hash(
          opts.deep_dup.compact.except(:owner)
        )
        put("/owners/#{opts[:owner]}", body)
      end

      def set_owner_log_level(opts = {})
        defaults = {
          :owner => key,
          :level => nil,
        }
        opts = verify_and_merge(opts, defaults)

        put("/owners/#{opts[:owner]}/log", :query => opts.slice(:level))
      end

      def delete_owner_log_level(opts = {})
        defaults = {
          :owner => key,
        }
        opts = verify_and_merge(opts, defaults)

        delete("/owners/#{opts[:owner]}/log")
      end

      def delete_owner(opts = {})
        defaults = {
          :owner => key,
          :revoke => false,
        }
        opts = verify_and_merge(opts, defaults)
        delete("/owners/#{opts[:owner]}", opts.slice(:revoke))
      end
    end

    module PoolResource
      # This method is deprecated in Candlepin
      def get_all_pools(opts = {})
        defaults = {
          :owner => nil,
          :consumer => nil,
          :product => nil,
          :listall => false,
          :activeon => nil,
        }
        opts = verify_and_merge(opts, defaults)
        opts.compact!
        # Note that this method want the *owner ID* not the owner key
        validate_keys(opts, :owner)

        get("/pools", :query => opts)
      end

      def get_pool(opts = {})
        defaults = {
          :pool_id => nil,
          :uuid => uuid,
        }
        opts = verify_and_merge(opts, defaults)

        get("/pools", :consumer => opts[:uuid])
      end

      def get_pool_entitlements(opts = {})
        defaults = {
          :pool_id => nil,
        }
        opts = verify_and_merge(opts, defaults)

        get("/pools/#{opts[:pool_id]}/entitlements")
      end

      def get_per_pool_statistics(opts = {})
        defaults = {
          :pool_id => nil,
          :val_type => nil,
        }
        opts = verify_and_merge(opts, defaults)

        get("/pools/#{opts[:pool_id]}/statistics/#{opts[:val_type]}")
      end

      def delete_pool(opts = {})
        delete_by_id("/pools", :pool_id, opts)
      end
    end

    module OwnerContentResource
      def get_owner_content(opts = {})
        defaults = {
          :content_id => nil,
          :owner => key,
        }
        opts = verify_and_merge(opts, defaults)
        validate_keys(opts)

        get("/owners/#{opts[:owner]}/content/#{opts[:content_id]}")
      end

      def delete_owner_content(opts = {})
        defaults = {
          :content_id => nil,
          :owner => key,
        }
        opts = verify_and_merge(opts, defaults)
        validate_keys(opts)

        delete("/owners/#{opts[:owner]}/content/#{opts[:content_id]}")
      end

      def create_owner_content(opts = {})
        defaults = {
          :content_id => nil,
          :name => nil,
          :label => nil,
          :type => "yum",
          :vendor => "Red Hat",
          :content_url => "",
          :gpg_url => "",
          :modified_product_ids => [],
          :arches => nil,
          :required_tags => nil,
          :metadata_expire => nil,
          :owner => key,
        }
        opts = verify_and_merge(opts, defaults)
        validate_keys(opts, :owner)

        content = opts.dup
        content.delete(:content_id)
        content = camelize_hash(content)
        content[:id] = opts[:content_id]
        post("/owners/#{opts[:owner]}/content", content)
      end

      def create_batch_owner_content(opts = {})
        defaults = {
          :owner => key,
          :content => [],
        }
        opts = verify_and_merge(opts, defaults)
        validate_keys(opts, :owner)

        content_defaults = {
          :content_id => nil,
          :name => nil,
          :label => nil,
          :type => "yum",
          :vendor => "Red Hat",
          :content_url => "",
          :gpg_url => "",
          :modified_product_ids => [],
          :arches => nil,
          :required_tags => nil,
          :metadata_expire => nil,
        }
        content_objects = opts[:content]
        body = []
        content_objects.each do |content|
          content = verify_and_merge(content, content_defaults)
          content[:id] = content[:content_id]
          content.delete(:content_id)
          body << camelize_hash(content)
        end

        post("/owners/#{opts[:owner]}/content/batch", body)
      end

      def update_owner_content(opts = {})
        defaults = {
          :content_id => nil,
          :name => nil,
          :label => nil,
          :type => nil,
          :vendor => nil,
          :content_url => nil,
          :gpg_url => nil,
          :modified_product_ids => nil,
          :arches => nil,
          :required_tags => nil,
          :metadata_expire => nil,
          :owner => key,
        }
        opts = verify_and_merge(opts, defaults)
        validate_keys(opts, :owner)

        content = opts.dup
        content = camelize_hash(content)

        # For some reason the update method on owner
        # content requires a full content object and sending
        # a partial object results in errors from
        # Hibernate about null IDs and such.  We can
        # make life easier for users by doing the merging
        # here
        old_content = get_owner_content(
          :content_id => opts[:content_id],
          :owner => opts[:owner],
        ).content
        # The content id cannot change so don't let it win in
        # the merge
        content.delete(:content_id)
        content.compact!
        content.reverse_merge!(old_content)

        put("/owners/#{opts[:owner]}/content/#{opts[:content_id]}", content)
      end
    end

    module ContentResource
      def get_all_content
        get("/content")
      end

      # The name get_content is already in use by HTTPClient
      def get_content_by_id(opts = {})
        get_by_id("/content", :content_id, opts)
      end
    end

    module RuleResource
      def get_rules
        get_text("/rules")
      end

      def delete_rules
        delete("/rules")
      end
    end

    module OwnerProductResource
      def get_owner_product(opts = {})
        defaults = {
          :owner => key,
          :product_id => nil,
        }
        opts = verify_and_merge(opts, defaults)
        validate_keys(opts)
        get("/owners/#{opts[:owner]}/products/#{opts[:product_id]}")
      end

      def get_all_owner_products(opts = {})
        defaults = {
          :owner => key,
        }
        opts = verify_and_merge(opts, defaults)
        validate_keys(opts)
        get("/owners/#{opts[:owner]}/products/")
      end

      def create_product(opts = {})
        defaults = {
          :product_id => nil,
          :type => "SVC",
          :name => nil,
          :multiplier => 1,
          :attributes => {},
          :dependent_product_ids => [],
          :product_content => [],
          :relies_on => [],
          :owner => key,
          :legacy => false,
        }
        opts = verify_and_merge(opts, defaults)
        validate_keys(opts, :owner)
        validate_keys(opts, :attributes) do |x|
          x.kind_of?(Hash)
        end

        opts[:attributes][:type] = opts.extract!(:type)[:type]

        product = camelize_hash(opts,
          :name,
          :multiplier,
          :dependent_product_ids,
          :relies_on,
          :product_content,
        )
        product[:id] = opts[:product_id]
        product[:attributes] = opts[:attributes].map do |k, v|
          { :name => k, :value => v }
        end
        product.compact!

        if opts[:legacy]
          url = "/products"
        else
          url = "/owners/#{opts[:owner]}/products"
        end

        post(url, product)
      end

      def update_product(opts = {})
        defaults = {
          :product_id => nil,
          :name => nil,
          :multiplier => nil,
          :attributes => [],
          :dependent_product_ids => [],
          :relies_on => [],
          :owner => key,
        }
        opts = verify_and_merge(opts, defaults)
        validate_keys(opts, :owner)

        product = camelize_hash(opts, :name, :multiplier, :dependent_product_ids, :relies_on)
        product[:id] = opts[:product_id]
        product[:attributes] = opts[:attributes].map do |k, v|
          { :name => k, :value => v }
        end

        put("/owners/#{opts[:owner]}/products/#{opts[:product_id]}", product)
      end

      def delete_product(opts = {})
        defaults = {
          :owner => key,
          :product_id => nil,
        }
        opts = verify_and_merge(opts, defaults)
        validate_keys(opts)

        delete("/owners/#{opts[:owner]}/products/#{opts[:product_id]}")
      end

      def get_product_cert(opts = {})
        defaults = {
          :product_id => nil,
          :owner => key,
        }
        opts = verify_and_merge(opts, defaults)
        validate_keys(opts)

        get("owners/#{opts[:owner]}/products/#{opts[:product_id]}/certificate")
      end

      def update_product_content(opts = {})
        defaults = {
          :product_id => nil,
          :content_id => nil,
          :enabled => true,
          :owner => key,
        }
        opts = verify_and_merge(opts, defaults)
        validate_keys(opts)

        post("/owners/#{opts[:owner]}/products/#{opts[:product_id]}/content/#{opts[:content_id]}",
          :query => opts.slice(:enabled))
      end

      def delete_product_content(opts = {})
        defaults = {
          :product_id => nil,
          :content_id => nil,
          :owner => key,
        }
        opts = verify_and_merge(opts, defaults)
        validate_keys(opts)

        delete("/owners/#{opts[:owner]}/products/#{opts[:product_id]}/content/#{opts[:content_id]}")
      end
    end

    module ContentOverrideResource
      def create_content_overrides(opts = {})
        defaults = {
          :uuid => uuid,
          :overrides => [],
        }
        opts = verify_and_merge(opts, defaults)
        validate_keys(opts, :uuid)

        unless opts[:overrides].kind_of?(Array)
          opts[:overrides] = [opts[:overrides]]
        end

        override_defaults = {
          :content_label => nil,
          :name => nil,
          :value => nil,
        }
        body = []
        override_objects = opts[:overrides]
        override_objects.each do |override|
          override = verify_and_merge(override, override_defaults)
          body << camelize_hash(override)
        end

        put("/consumers/#{opts[:uuid]}/content_overrides", body)
      end

      def get_content_overrides(opts = {})
        defaults = {
          :uuid => uuid,
        }
        opts = verify_and_merge(opts, defaults)
        validate_keys(opts, :uuid)

        get("/consumers/#{opts[:uuid]}/content_overrides")
      end

      def delete_content_overrides(opts = {})
        defaults = {
          :uuid => uuid,
          :overrides => [],
        }
        opts = verify_and_merge(opts, defaults)
        validate_keys(opts, :uuid)

        unless opts[:overrides].kind_of?(Array)
          opts[:overrides] = [opts[:overrides]]
        end

        override_defaults = {
          :content_label => nil,
          :name => nil,
          :value => nil,
        }
        body = []
        override_objects = opts[:overrides]
        override_objects.each do |override|
          override = verify_and_merge(override, override_defaults)
          body << camelize_hash(override)
        end

        # It's unusual for a DELETE to have a body, but it is
        # allowed
        delete("/consumers/#{opts[:uuid]}/content_overrides", body)
      end
    end

    module ProductResource
      def get_product(opts = {})
        get_by_id("/products", :product_id, opts)
      end

      def get_owners_with_product(opts = {})
        defaults = {
          :product_ids => [],
        }
        opts = verify_and_merge(opts, defaults)
        validate_keys(opts, :product_ids) do |k|
          not k.empty?
        end
        get("/products/owners", :query => {'product' => opts[:product_ids]})
      end

      def get_product_certificate(opts = {})
        defaults = {
          :product_ids => [],
        }
        opts = verify_and_merge(opts, defaults)
        get("/products/#{opts[:product_id]}/certificate")
      end

      def get_per_product_statistics(opts = {})
        defaults = {
          :product_id => nil,
          :val_type => nil,
        }
        opts = verify_and_merge(opts, defaults)

        get("/products/#{opts[:product_id]}/statistics/#{opts[:val_type]}")
      end

      def refresh_pools_for_product_async(opts = {})
        defaults = {
          :product_ids => [],
          :lazy_regen => false,
        }
        opts = verify_and_merge(opts, defaults)
        validate_keys(opts, :product_ids)
        unless opts[:product_ids].kind_of?(Array)
          opts[:product_ids] = [opts[:product_ids]]
        end
        query = opts.slice(:lazy_regen)
        query[:product] = opts[:product_ids]

        # HTTPClient is a little quirky here.  It does not expect
        # a PUT without a body, so we need to provide a dummy body.
        put_async("/products/subscriptions",
          :query => query,
          :body => nil)
      end

      # Same as async call but just block before returning
      def refresh_pools_for_product(opts = {})
        connection = refresh_pools_for_product_async(opts)
        connection.join
        connection.pop
      end
    end

    module SubscriptionResource
      def get_subscription(opts = {})
        get_by_id("/subscriptions", :subscription_id, opts)
      end

      def get_subscription_certificate(opts = {})
        defaults = {
          :subscription_id => nil,
        }
        opts = verify_and_merge(opts, defaults)

        get_text("/subscriptions/#{opts[:subscription_id]}/cert")
      end

      def delete_subscription(opts = {})
        delete_by_id("/subscriptions", :subscription_id, opts)
      end
    end

    module DistributorVersionResource
      def create_distributor_version(opts = {})
        defaults = {
          :name => nil,
          :display_name => nil,
          :capabilities => [],
        }
        opts = verify_and_merge(opts, defaults)

        distributor = camelize_hash(opts, :name, :display_name)
        distributor[:capabilities] = opts[:capabilities].map do |v|
          { :name => v }
        end
        post("/distributor_versions", distributor)
      end

      def update_distributor_version(opts = {})
        defaults = {
          :id => nil,
          :name => nil,
          :display_name => nil,
          :capabilities => [],
        }
        opts = verify_and_merge(opts, defaults)

        distributor = camelize_hash(opts, :id, :name, :display_name)
        distributor[:capabilities] = opts[:capabilities].map do |v|
          { :name => v }
        end

        put("/distributor_versions/#{opts[:id]}", distributor)
      end

      def get_distributor_version(opts = {})
        defaults = {
          :name => nil,
          :capability => nil,
        }
        opts = verify_and_merge(opts, defaults)

        query = opts.slice(:capability)
        query['name_search'] = opts[:name]

        get("/distributor_versions", query)
      end

      def delete_distributor_version(opts = {})
        delete_by_id("/distributor_versions", :id, opts)
      end
    end

    module CdnResource
      def create_cdn(opts = {})
        defaults = {
          :label => nil,
          :name => nil,
          :url => nil,
          :certificate => nil
        }
        opts = verify_and_merge(opts, defaults)

        post("/cdn", opts)
      end

      def update_cdn(opts = {})
        defaults = {
          :label => nil,
          :name => nil,
          :url => nil,
          :certificate => nil
        }
        opts = verify_and_merge(opts, defaults)

        put("/cdn/#{opts[:label]}", opts.slice(:name, :url, :certificate))
      end

      def get_all_cdns
        get("/cdn")
      end

      def delete_cdn(opts = {})
        delete_by_id("/cdn", :label, opts)
      end
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
      :create_request,
      :debug_dev=,
      :delete,
      :delete_async,
      :follow_redirect,
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
      :request_async2,
      :success_content,
      :trace

    attr_accessor :use_ssl
    attr_accessor :host
    attr_accessor :port
    attr_accessor :insecure
    attr_accessor :ca_path
    attr_accessor :connection_timeout
    attr_accessor :client
    attr_reader :context
    attr_reader :fail_fast

    # Shorten the name of this useful method
    alias_method :ok_content, :success_content

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
    # * :fail_fast:: If an exception should be raised on a non-200 response. Redirects
    #     are considered failures.
    def initialize(opts = {})
      defaults = {
        :host => 'localhost',
        :port => 8443,
        :context => '/candlepin',
        :use_ssl => true,
        :insecure => true,
        :connection_timeout => 3,
        :fail_fast => false,
      }
      @client_opts = defaults.merge(opts)
      # Subclasses must provide an attr_writer or attr_accessor for every key
      # in the options hash.  The snippet below sends the values to the setter methods.
      @client_opts.each do |k, v|
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

    def fail_fast=(val)
      @fail_fast = val
      reload if @client
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
      # See https://github.com/nahi/httpclient/issues/285
      components[:path] += "/" unless components[:path][-1] == "/"
      if use_ssl
        uri = URI::HTTPS.build(components)
      else
        uri = URI::HTTP.build(components)
      end
      uri.to_s
    end


    def get_text(*args, &block)
      get_type('text/plain', *args, &block)
    end

    def get_file(*args, &block)
      get_type('application/zip', *args, &block)
    end

    def get_type(mime_type, uri, *args, &block)
      header_filter = JSONClient::AcceptTypeHeaderFilter.new(self, mime_type)
      client.request_filter << header_filter
      begin
        res = client.get(uri, *args, &block)
      ensure
        client.request_filter.delete(header_filter)
      end
      res
    end

    # This method provides the raw HTTPClient object that is being used
    # to communicate with the server.  In most circumstances, you should not
    # need to access it, but it is there if you need it.
    def raw_client
      client = JSONClient.new(:base_url => base_url, :fail_fast => fail_fast)
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
        consumer_json.deep_symbolize_keys!
        if opts.key?(:client_cert) || opts.key?(:client_key)
          raise ArgumentError.new("Cannot specify cert and key for this method")
        end
        client_cert = OpenSSL::X509::Certificate.new(consumer_json[:idCert][:cert])
        client_key = OpenSSL::PKey::RSA.new(consumer_json[:idCert][:key])
        opts = {
          :client_cert => client_cert,
          :client_key => client_key,
        }.merge(opts)
        client = X509Client.new(opts)
        client.uuid = consumer_json[:uuid]
        client.key = consumer_json[:owner][:key]
        client
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

  class TrustedAuthHeaderFilter
    def initialize(username)
      @username = username
    end

    def filter_request(req)
      req.header['cp-user'] = @username
    end

    def filter_response(req, res)
    end
  end

  class TrustedAuthClient < NoAuthClient
    attr_accessor :username

    def initialize(opts = {})
      defaults = {
        :username => 'admin',
      }
      opts = defaults.merge(opts)
      super(opts)
    end

    def raw_client
      client = super
      client.request_filter << TrustedAuthHeaderFilter.new(username)
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

    def switch_auth(*args)
      if args.length == 1 && args.first.kind_of?(Hash)
        @username = args.first[:username]
        @password = args.first[:password]
      else
        @username = args[0]
        @password = args[1]
      end
      reload
    end

    def raw_client
      client = super
      client.force_basic_auth = true
      client.set_auth(base_url, username, password)
      client
    end
  end
end
