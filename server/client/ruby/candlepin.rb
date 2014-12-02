require 'httpclient'
require 'json'
require 'date'
require 'openssl'

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
    if hash[:body] && hash[:body].is_a?(Hash)
      hash[:body] = JSON.generate(hash[:body])
    end
    hash
  end
end

module Candlepin
  class SimpleClient
    include Candlepin

    attr_accessor :use_ssl
    attr_accessor :host
    attr_accessor :port
    attr_accessor :context
    attr_accessor :insecure
    attr_accessor :ca_path

    def initialize(opts = {})
      defaults = {
        :host => 'localhost',
        :port => 8443,
        :context => 'candlepin',
        :use_ssl => true,
        :insecure => true,
      }
      opts = defaults.merge(opts)
      opts.each do |k, v|
        self.send(:"#{k}=", v)
      end
    end

    def base_url
      protocol = (@use_ssl) ? 'https' : 'http'
      "#{protocol}://#{host}:#{port}/#{context}"
    end

    def client
      client = JSONClient.new(:base_url => base_url)
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

  class UserClient < SimpleClient
    attr_accessor :username
    attr_accessor :password

    def initialize(opts = {})
      defaults = {
        :username => 'admin',
        :password => 'admin',
      }
      opts = defaults.merge(opts)
      super(opts)
    end

    def client
      client = super
      client.set_auth(base_url, username, password)
      client
    end
  end
end
