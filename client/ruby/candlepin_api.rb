require 'base64'
require 'openssl'
require 'date'
require 'rubygems'
require 'rest_client'
require 'json'
require 'uri'

class Candlepin

  attr_accessor :consumer
  attr_reader :identity_certificate
  attr_accessor :uuid
  attr_reader :lang

  # Initialize a connection to candlepin. Can use username/password for 
  # basic authentication, or provide an identity certificate and key to
  # connect as a "consumer".
  def initialize(username=nil, password=nil, cert=nil, key=nil, 
                 host='localhost', port=8443, lang=nil)

    if not username.nil? and not cert.nil?
      raise "Cannot connect with both username and identity cert"
    end

    if username.nil? and cert.nil?
      raise "Need username/password or cert/key"
    end

    @base_url = "https://#{host}:#{port}/candlepin"
    
    @lang = lang

    if not cert.nil?
      create_ssl_client(cert, key)
    else
      create_basic_client(username, password)
    end

    # Store top level HATEOAS resource links so we know what we can do:
    results = get("/")
    @links = {}
    results.each do |link|
      @links[link['rel']] = link['href']
    end

  end

  def get_path(resource)
    return @links[resource]
  end

  def register(name, type=:system, uuid=nil, facts={}, username=nil)
    consumer = {
      :type => {:label => type},
      :name => name,
      :facts => facts
    }

    consumer[:uuid] = uuid if not uuid.nil?

    path = get_path("consumers")
    path += "?username=#{username}" if username
    @consumer = post(path, consumer)
    return @consumer
  end

  def update_facts(facts, uuid=nil)
    uuid = @uuid if uuid.nil?

    consumer = {
      :uuid => uuid,
      :facts => facts
    }

    path = get_path("consumers")
    put("#{path}/#{uuid}", consumer)
  end

  def list_owners(params = {})
    path = "/owners"
    results = get(path)
    return results
  end

  # Can pass an owner key, or an href to follow:
  def get_owner(owner)
    # Looks like a path to follow:
    if owner[0,1] == "/"
      return get(owner)
    end

    # Otherwise, assume owner key was given:
    get("/owners/#{owner}")
  end

  def create_owner(owner_name)
    owner = {
      'key' => owner_name,
      'displayName' => owner_name
    }

    post('/owners', owner)
  end

  def delete_owner(owner_key)
    delete("/owners/#{owner_key}")
  end

  def create_user(owner_key, login, password)
    user = {
      'username' => login,
      'password' => password
    }

    post("/owners/#{owner_key}/users", user)
  end

  def list_consumer_types
    get('/consumertypes')
  end

  def get_consumer_type(type_id)
    get("/consumertypes/#{type_id}")
  end

  def create_consumer_type(type_label)
    consumer_type =  {
      'label' => type_label
    }

    post('/consumertypes', consumer_type)
  end

  def delete_consumer_type(type_id)
    delete("/consumertypes/#{type_id}")
  end
  
  def get_pool(poolid)
    get("/pools/#{poolid}")
  end

  def list_pools(params = {})
    path = "/pools?"
    path << "consumer=#{params[:consumer]}&" if params[:consumer]
    path << "owner=#{params[:owner]}&" if params[:owner]
    path << "product=#{params[:product]}&" if params[:product]
    path << "listall=#{params[:listall]}&" if params[:listall]
    results = get(path)

    return results
  end
  
  def create_pool(product_id, owner_id, quantity, params={})
    subscription_id = params[:subscription_id] || nil
    start_date = params[:start_date] || Date.today
    end_date = params[:end_date] || Date.today + 365
    user_restricted = params[:user_restricted] || nil
    attributes = params[:attributes] || {}

    attribute_set = []
    attributes.each_pair do |name, value|
      attribute_set << { 'name' => name, 'value' => value }
    end

    pool = {
      'activeSubscription' => true,
      'subscriptionId' => subscription_id,
      'quantity' => quantity,
      'consumed' => 0,
      'startDate' => start_date,
      'endDate' => end_date,
      'productId' => product_id,
      'owner' => { 
        'id' => owner_id
      },
      'attributes' => attribute_set,
      'restrictedToUsername' => user_restricted
    }
    
    post('/pools', pool)
  end

  def delete_pool(pool_id)
    delete("/pools/#{pool_id}")
  end

  def refresh_pools(owner_key, immediate=false)
    return async_call(immediate) do
      put("/owners/#{owner_key}/subscriptions")
    end
  end
  
  def async_call(immediate, *args, &blk)
    status = blk.call(args)
    return status if immediate
    # otherwise poll the server to make this call synchronous
    while status['state'].downcase != 'finished'
      sleep 1
      # POSTing here will delete the job once it has finished
      status = post(status['statusPath'])
    end
    return status['result']
  end
      
  def export_consumer(dest_dir)
    path = "/consumers/#{@uuid}/export"
    begin
      get_file(path, dest_dir)
    rescue Exception => e
      puts e.response
    end
  end

  def get_entitlement(entitlement_id)
    get("/entitlements/#{entitlement_id}")
  end

  def unregister(uuid = nil)
    uuid = @uuid unless uuid    
    delete("/consumers/#{uuid}")
  end

  def revoke_all_entitlements()
    delete("/consumers/#{@uuid}/entitlements")
  end

  def list_products
    get("/products")
  end
  
  def create_content(name, id, label, type, vendor,
                     contentUrl, gpgUrl)
    content = {
      'name' => name,
      'id' => id,
      'label' => label,
      'type' => type,
      'vendor' => vendor,
      'contentUrl' => contentUrl,
      'gpgUrl' => gpgUrl
    }
    post("/content", content)
  end

  def list_content
    get("/content")
  end

  def get_content(content_id)
    get("/content/id/#{content_id}")
  end

  def add_content_to_product(product_uuid, content_id, enabled=true)
    post("/products/#{product_uuid}/content/#{content_id}?enabled=#{enabled}")
  end
    
  def create_product(id, name, params={})

    multiplier = params[:multiplier] || 1
    attributes = params[:attributes] || {}

    product = {
      'name' => name,
      'id' => id,
      'multiplier' => multiplier,
      'attributes' => attributes.collect {|k,v| {'name' => k, 'value' => v}}
    }

    post("/products", product)
  end

  def get_product(product_id)
    get("/products/#{product_id}")
  end

  def get_product_cert(product_id)
    get("/products/#{product_id}/certificate")
  end
  
  # TODO: Should we change these to bind to better match terminology?
  def consume_pool(pool)
    post("/consumers/#{@uuid}/entitlements?pool=#{pool}")
  end

  def consume_product(product, quantity = nil)
    path = "/consumers/#{@uuid}/entitlements?product=#{product}"
    path << "&quantity=#{quantity}" if quantity
    post(path)
  end

  def consume_token(token)
    post("/consumers/#{@uuid}/entitlements?token=#{token}")
  end

  # TODO: Could also fetch from /entitlements, a bit ambiguous:
  def list_entitlements(params={})
    uuid = params[:uuid] || @uuid

    path = "/consumers/#{uuid}/entitlements"
    path << "?product=#{params[:product_id]}" if params[:product_id]
    results = get(path)
    return results
  end

  def list_rules()
    get_text("/rules")
  end

  def upload_rules(rule_set)
    post_text("/rules/", rule_set)
  end

  def list_consumers(args={})
    query = "/consumers?"
    query << "username=#{args[:username]}&" if args[:username]
    query << "type=#{args[:type]}" if args[:type]
    get(query)
  end
  
  def get_consumer(consumer_id=nil)
    consumer_id ||= @uuid
    get("/consumers/#{consumer_id}")
  end

  def unbind_entitlement(eid)
    delete("/consumers/#{@uuid}/entitlements/#{eid}")
  end

  def list_subscriptions(owner_key, params={})
    results = get("/owners/#{owner_key}/subscriptions")
    return results
  end

  def get_subscription(sub_id)
    return get("/subscriptions/#{sub_id}")
  end

  def create_subscription(owner_key, product_id, quantity=1,
                          provided_products=[], contract_number='',
                          start_date=nil, end_date=nil)
    start_date ||= Date.today
    end_date ||= start_date + 365

    subscription = {
      'startDate' => start_date,
      'endDate'   => end_date,
      'quantity'  =>  quantity,
      'product' => { 'id' => product_id },
      'providedProducts' => provided_products.collect { |pid| {'id' => pid} },
      'contractNumber' => contract_number
    }

    return post("/owners/#{owner_key}/subscriptions", subscription)
  end

  def update_subscription(subscription)
    return put("/owners/subscriptions", subscription)
  end

  def delete_subscription(subscription_id)
    return delete("/subscriptions/#{subscription_id}")
  end

  def list_subscription_tokens
    return get("/subscriptiontokens")
  end

  def create_subscription_token(data)
    return post("/subscriptiontokens", data)
  end

  def delete_subscription_token(subscription)
    return delete("/subscriptiontokens/#{subscription}")
  end

  def list_certificates(serials = [])
    path = "/consumers/#{@uuid}/certificates"
    path += "?serials=" + serials.join(",") if serials.length > 0
    return get(path)
  end

  def regenerate_entitlement_certificates
    return put("/consumers/#{@uuid}/certificates")
  end
  
  def regenerate_entitlement_certificates_for_product(product_id, immediate=false)
    return async_call(immediate) do
      put("/entitlements/product/#{product_id}")
    end
  end

  def regenerate_identity_certificate(uuid=nil)
    uuid ||= @uuid

    new_consumer = post("/consumers/#{uuid}")
    create_ssl_client(new_consumer.idCert.cert, new_consumer.idCert.key)
  end

  def get_status
    return get("/status/")
  end

  def list_certificate_serials
    return get("/consumers/#{@uuid}/certificates/serials")
  end

  def get_serial(serial_id)
    get("/serials/#{serial_id}")
  end

  def list_consumer_events(consumer_id=nil)
    consumer_id ||= @uuid
    get_text("/consumers/#{consumer_id}/atom")
  end

  def get_crl
    OpenSSL::X509::CRL.new(get_text('/crl'))
  end

  def get(uri, accept_header = :json)
    response = @client[URI.escape(uri)].get :accept => accept_header

    return JSON.parse(response.body)
  end
  
  #assumes a zip archive atm
  def get_file(uri, dest_dir)
    response = @client[URI.escape(uri)].get    
    filename = response.headers[:content_disposition] == nil ? "tmp_#{rand}.zip" : response.headers[:content_disposition].split("filename=")[1]
    filename = File.join(dest_dir, filename)
    File.open(filename, 'w') { |f| f.write(response.body) }
    filename
  end

  def get_text(uri)
    response = @client[URI.escape(uri)].get :content_type => 'text/plain'
    return (response.body)
  end

  def post(uri, data=nil)
    data = data.to_json if not data.nil?
    response = @client[URI.escape(uri)].post(data, :content_type => :json, :accept => :json)
    return JSON.parse(response.body)
  end
  
  def post_text(uri, data=nil)
    response = @client[URI.escape(uri)].post(data, :content_type => 'text/plain', :accept => 'text/plain' )
    return response.body
  end

  def put(uri, data=nil)
    data = data.to_json if not data.nil?
    response = @client[uri].put(data, :content_type => :json, :accept => :json)

    return JSON.parse(response.body) unless response.body.empty?
  end

  def delete(uri)
    @client[URI.escape(uri)].delete
  end

  private

  def create_basic_client(username=nil, password=nil)
    @client = RestClient::Resource.new(@base_url, 
                                       :user => username, :password => password,
                                       :headers => {:accept_language => @lang})
  end

  def create_ssl_client(cert, key)
    @identity_certificate = OpenSSL::X509::Certificate.new(cert)
    @identity_key = OpenSSL::PKey::RSA.new(key)
    @uuid = @identity_certificate.subject.to_s.scan(/\/CN=([^\/=]+)/)[0][0]

    @client = RestClient::Resource.new(@base_url,
                                       :ssl_client_cert => @identity_certificate, 
                                       :ssl_client_key => @identity_key,
                                       :headers => {:accept_language => @lang})
  end

end


