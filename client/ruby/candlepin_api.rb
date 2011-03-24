require 'base64'
require 'openssl'
require 'date'
require 'rubygems'
require 'rest_client'
require 'json'
require 'uri'
require 'pp'
require 'oauth'

class Candlepin

  attr_accessor :consumer
  attr_reader :identity_certificate
  attr_accessor :uuid
  attr_reader :lang

  # Initialize a connection to candlepin. Can use username/password for
  # basic authentication, or provide an identity certificate and key to
  # connect as a "consumer".
  # TODO probably want to convert this to rails style kv
  def initialize(username=nil, password=nil, cert=nil, key=nil,
                 host='localhost', port=8443, lang=nil, uuid=nil,
                 trusted_user=false)

    if not username.nil? and not cert.nil?
      raise "Cannot connect with both username and identity cert"
    end

    if username.nil? and cert.nil? and uuid.nil?
      raise "Need username/password, cert/key, or uuid"
    end

    @base_url = "https://#{host}:#{port}/candlepin"
    @lang = lang

    if not uuid.nil?
      create_trusted_consumer_client(uuid)
    elsif trusted_user
      create_trusted_user_client(username)
    elsif not cert.nil?
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

  # expects an owner key
  def get_owner_info(owner)
    get("/owners/#{owner}/info")
  end

  def create_owner(owner_name, parent=nil)
    owner = {
      'key' => owner_name,
      'displayName' => owner_name
    }
    owner['parentOwner'] = parent if !parent.nil?
    post('/owners', owner)
  end

  def update_owner(owner_key, owner)
    put("/owners/#{owner_key}", owner)
  end

  def delete_owner(owner_key, revoke=true)
    uri = "/owners/#{owner_key}"
    uri << '?revoke=false' unless revoke

    delete uri
  end

  def migrate_owner(owner_key, uri, immediate=false)
    return async_call(immediate) do
      put("owners/migrate?id=#{owner_key}&uri=#{uri}")
    end
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
      params={})

    metadata_expire = params[:metadata_expire] || nil
    required_tags = params[:required_tags] || nil
    content_url = params[:content_url] || ""
    gpg_url = params[:gpg_url] || ""
    modified_product_ids = params[:modified_products] || []

    content = {
      'name' => name,
      'id' => id,
      'label' => label,
      'type' => type,
      'vendor' => vendor,
      'contentUrl' => content_url,
      'gpgUrl' => gpg_url,
      'modifiedProductIds' => modified_product_ids
    }
    content['metadataExpire'] = metadata_expire if not metadata_expire.nil?
    content['requiredTags'] = required_tags if not required_tags.nil?
    post("/content", content)
  end

  def list_content
    get("/content")
  end

  def get_content(content_id)
    get("/content/#{content_id}")
  end

  def add_content_to_product(product_id, content_id, enabled=true)
    post("/products/#{product_id}/content/#{content_id}?enabled=#{enabled}")
  end

  def create_product(id, name, params={}, dependentProductIds=[])

    multiplier = params[:multiplier] || 1
    attributes = params[:attributes] || {}
    #if product don't have type attributes, create_product will fail on server
    #side.
    attributes['type'] = 'SVC' if attributes['type'].nil?
    product = {
      'name' => name,
      'id' => id,
      'multiplier' => multiplier,
      'attributes' => attributes.collect {|k,v| {'name' => k, 'value' => v}},
      'dependentProductIds' => dependentProductIds
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
  def consume_pool(pool, quantity=nil)
    path = "/consumers/#{@uuid}/entitlements?pool=#{pool}"
    path << "&quantity=#{quantity}" if quantity

    post(path)
  end

  def consume_product(product, quantity=nil)
    path = "/consumers/#{@uuid}/entitlements?product=#{product}"
    path << "&quantity=#{quantity}" if quantity
    post(path)
  end

  def consume_token(token)
    post("/consumers/#{@uuid}/entitlements?token=#{token}")
  end

  def list_users_by_owner(owner_key)

    path = "/owners/#{owner_key}/users"
    results = get(path)
    return results
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
                          account_number='',start_date=nil,
                          end_date=nil)
    start_date ||= Date.today
    end_date ||= start_date + 365

    subscription = {
      'startDate' => start_date,
      'endDate'   => end_date,
      'quantity'  =>  quantity,
      'accountNumber' => account_number,
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

  def regenerate_entitlement_certificates_for_entitlement(entitlement_id)
    return put("/consumers/#{uuid}/certificates?entitlement=#{entitlement_id}")
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

  def list_events
    get '/events'
  end

  def list_imports(owner_key)
    get "/owners/#{owner_key}/imports"
  end

  def import(owner_key, filename)
    post_file "/owners/#{owner_key}/imports", File.new(filename)
  end

  def get_crl
    OpenSSL::X509::CRL.new(get_text('/crl'))
  end

  def get(uri, accept_header = :json)
    response = get_client(uri, Net::HTTP::Get, :get)[URI.escape(uri)].get \
      :accept => accept_header
    return JSON.parse(response.body)
  end

  # Assumes a zip archive currently. Returns filename (random#.zip) of the
  # temp file created.
  def get_file(uri, dest_dir)
    response = get_client(uri, Net::HTTP::Get, :get)[URI.escape(uri)].get
    filename = response.headers[:content_disposition] == nil ? "tmp_#{rand}.zip" : response.headers[:content_disposition].split("filename=")[1]
    filename = File.join(dest_dir, filename)
    File.open(filename, 'w') { |f| f.write(response.body) }
    filename
  end

  def get_text(uri)
    response = get_client(uri, Net::HTTP::Get, :get)[URI.escape(uri)].get :content_type => 'text/plain'
    return (response.body)
  end

  def post(uri, data=nil)
    data = data.to_json if not data.nil?
    response = get_client(uri, Net::HTTP::Post, :post)[URI.escape(uri)].post(
      data, :content_type => :json, :accept => :json)
    return JSON.parse(response.body)
  end

  def post_file(uri, file=nil)
    response = get_client(uri, Net::HTTP::Post, :post)[URI.escape(uri)].post(:import => file, :accept => :json)
    return JSON.parse(response.body) unless response.body.empty?
  end

  def post_text(uri, data=nil)
    response = get_client(uri, Net::HTTP::Post, :post)[URI.escape(uri)].post(data, :content_type => 'text/plain', :accept => 'text/plain' )
    return response.body
  end

  def put(uri, data=nil)
    data = data.to_json if not data.nil?
    response = get_client(uri, Net::HTTP::Put, :put)[uri].put(
      data, :content_type => :json, :accept => :json)

    return JSON.parse(response.body) unless response.body.empty?
  end

  def delete(uri)
    get_client(uri, Net::HTTP::Delete, :delete)[URI.escape(uri)].delete
  end

  protected

  # Overridden by sub-classes that need to do more advanced things:
  def get_client(uri, http_type, method)
    return @client
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

  def create_trusted_consumer_client(uuid)
    @uuid = uuid
    @client = RestClient::Resource.new(@base_url,
                                       :headers => {"cp-consumer" => uuid,
                                                    :accept_language => @lang}
                                        )
  end

  def create_trusted_user_client(username)
    @username = username
    @client = RestClient::Resource.new(@base_url,
                                       :headers => {"cp-user" => username,
                                                    :accept_language => @lang}
                                        )
  end

end

class OauthCandlepinApi < Candlepin

  def initialize(username, password, oauth_consumer_key, oauth_consumer_secret, params={})

    @oauth_consumer_key = oauth_consumer_key
    @oauth_consumer_secret = oauth_consumer_secret

    host = params[:host] || 'localhost'
    port = params[:port] || 8443
    lang = params[:lang] || nil
    super(username, password, nil, nil, host, port, lang, nil, false)
  end

  protected

  # OAuth implementation of this method creates a new REST resource for
  # each request to do the signing and add appropriate headers.
  def get_client(uri, http_type, method)
    final_url = @base_url + URI.escape(uri)
    params = {
      :site => @base_url,
      :http_method => method,
      :request_token_path => "",
      :authorize_path => "",
      :access_token_path => ""}
    #params[:ca_file] = self.ca_cert_file unless self.ca_cert_file.nil?

    consumer = OAuth::Consumer.new(@oauth_consumer_key,
      @oauth_consumer_secret, params)
    request = http_type.new(final_url)
    consumer.sign!(request)
    headers = {
      'Authorization' => request['Authorization'],
      'accept_language' => @lang,
      'cp-user' => 'admin'
    }

    # Creating a new client for every request:
    client = RestClient::Resource.new(@base_url,
      :headers => headers)
    return client
  end

end
