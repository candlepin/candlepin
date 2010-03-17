require 'base64'
require 'openssl'
require 'rest_client'
require 'json'

class Candlepin

    attr_reader :identity_certificate, :consumer

    def initialize(host='localhost', port=8080)
        @base_url = "http://#{host}:#{port}/candlepin"
    end

    def register(consumer, username=nil, password=nil)
        auth = RestClient::Resource.new("#{@base_url}", username, password)
        result = auth['/consumers'].post(consumer.to_json, 
                 :content_type => :json, :accept => :json)

        @consumer = JSON.parse(result.body)['consumer']

        identity_pem = Base64.decode64(@consumer['idCert']['pem'])
        @identity_certificate = OpenSSL::X509::Certificate.new(identity_pem)
    end

    def revoke_all_entitlements()
        delete("/consumers/#{@consumer['uuid']}/entitlements")
    end

    def consume_product(product)
        post("/consumers/#{@consumer['uuid']}/entitlements?product=#{product}")['entitlement']
    end

    def list_entitlements()
        get("/entitlements?consumer=#{@consumer['uuid']}")
    end

    private

    def get(uri)
        result = RestClient.get("#{@base_url}#{uri}", :accept => :json)
        JSON.parse(result.body)
    end

    def post(uri, data=nil)
        result = RestClient.post("#{@base_url}#{uri}", data.to_json, 
                 :content_type => :json, :accept => :json)
        return JSON.parse(result.body)
    end

    def delete(uri)
        RestClient.delete("#{@base_url}#{uri}")
    end
end

