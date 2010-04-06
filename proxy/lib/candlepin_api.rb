require 'base64'
require 'openssl'
require 'rest_client'
require 'json'

class Candlepin

    attr_reader :identity_certificate, :consumer

    def initialize(host='localhost', port=8443)
        @base_url = "https://#{host}:#{port}/candlepin"
    end

    def register(consumer, username=nil, password=nil)
      
        # TODO:  Maybe this should be created earlier?
        create_basic_client(username, password)

        @consumer = post('/consumers', consumer)['consumer']

        identity_cert = @consumer['idCert']['cert']
        identity_key = @consumer['idCert']['key']
        @identity_certificate = OpenSSL::X509::Certificate.new(identity_cert)
        @identity_key = OpenSSL::PKey::RSA.new(identity_key)

        create_ssl_client
    end

    def get_pool(poolid)
      get("/pools/#{poolid}'")
    end

    def get_pools(params = {})
      path = "/pools?"
      path << "consumer=#{params[:consumer]}&" if params[:consumer]
      path << "owner=#{params[:owner]}&" if params[:owner]
      path << "product=#{params[:product]}&" if params[:product]
      return get(path)
    end

    def get_certificates()
        path = "/consumers/#{@consumer['uuid']}/certificates"
        return get(path)
    end

    def unregister()
        delete("/consumers/#{@consumer['uuid']}")
    end


    def revoke_all_entitlements()
        delete("/consumers/#{@consumer['uuid']}/entitlements")
    end

    def consume_product(product)
        post("/consumers/#{@consumer['uuid']}/entitlements?product=#{product}")
    end
    
    def consume_pool(pool)
        post("/consumers/#{@consumer['uuid']}/entitlements?pool=#{pool}")
    end

    def list_entitlements(product_id = nil)
        path = "/consumers/#{@consumer['uuid']}/entitlements"
        path << "?product=#{product_id}" if product_id
        get(path)
    end

    def list_rules()
	get_text("/rules")
    end

    def upload_rules(rule_set)
        post_text("/rules/", rule_set)
    end

    def get_consumer(cid)
        create_basic_client("foo", "bar")
        get("/consumers/#{cid}")
    end

    private

    def create_basic_client(username=nil, password=nil)
        @client = RestClient::Resource.new(@base_url, 
            username, password)
    end

    def create_ssl_client
        @client = RestClient::Resource.new(@base_url,
            :ssl_client_cert => @identity_certificate, 
            :ssl_client_key => @identity_key)
    end

    def get(uri)
        response = @client[uri].get :accept => :json

        return JSON.parse(response.body)
    end

    def get_text(uri)
      response = @client[uri].get
      return (response.body)
    end

    def post(uri, data=nil)
        data = data.to_json if not data.nil?
        response = @client[uri].post(data, :content_type => :json, :accept => :json)

        return JSON.parse(response.body)
    end

    
    def post_text(uri, data=nil)
        response = @client[uri].post(data, :content_type => 'text/plain', :accept => 'text/plain' )
        return response.body
    end

    def delete(uri)
        @client[uri].delete
    end
end

