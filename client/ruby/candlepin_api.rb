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
                 trusted_user=false, context='candlepin',
                 use_ssl = true)

    if not username.nil? and not cert.nil?
      raise "Cannot connect with both username and identity cert"
    end

    @verbose = false

    if use_ssl
        @base_url = "https://#{host}:#{port}/#{context}"
    else
        @base_url = "http://#{host}:#{port}/#{context}"
    end

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

  def verbose=(verbose)
    @verbose = verbose
  end

  def get_path(resource)
    return @links[resource]
  end

  # TODO: need to switch to a params hash, getting to be too many arguments.
  def register(name, type=:system, uuid=nil, facts={}, username=nil,
              owner_key=nil, activation_keys=[], installedProducts=[],
              environment=nil)
    consumer = {
      :type => {:label => type},
      :name => name,
      :facts => facts,
      :installedProducts => installedProducts
    }

    consumer[:uuid] = uuid if not uuid.nil?

    if environment.nil?
      path = get_path("consumers") + "?"
      path = path + "owner=#{owner_key}&" if not owner_key.nil?
    else
      path = "/environments/#{environment}/consumers?"
    end
    path += "username=#{username}&" if username
    path += "activation_keys=" + activation_keys.join(",") if activation_keys.length > 0
    @consumer = post(path, consumer)
    return @consumer
  end

  def hypervisor_check_in(owner, host_guest_mapping={})
    path = get_path("hypervisors") + "?owner=#{owner}"
    consumers = post(path, host_guest_mapping)
    return consumers
  end

  def remove_deletion_record(deleted_uuid)
    path = get_path("consumers") + "/#{deleted_uuid}/deletionrecord"
    result = delete(path)
    return result
  end

  def update_consumer(params)
    uuid = params[:uuid] || @uuid

    consumer = {
      :uuid => uuid
    }
    consumer[:facts] = params[:facts] if params[:facts]
    consumer[:installedProducts] = \
        params[:installedProducts] if params[:installedProducts]
    consumer[:guestIds] = \
        params[:guestIds] if params[:guestIds]
    consumer[:autoheal] = params[:autoheal] if params.has_key?(:autoheal)
    consumer[:serviceLevel] = params[:serviceLevel] if params.has_key?(:serviceLevel)

    path = get_path("consumers")
    put("#{path}/#{uuid}", consumer)
  end

  def update_entitlement(params)
    entitlement = {
        :id => params[:id]
    }
    entitlement[:quantity] = params[:quantity]
    put("/entitlements/#{params[:id]}", entitlement)
  end

  def get_user_info(user)
    get("/users/#{user}")
  end

  def get_user_roles(user)
    get("/users/#{user}/roles")
  end

  def list_owners(params = {})
    path = "/owners"
    results = get(path)
    return results
  end

  def list_users_owners(username, params = {})
    path = "/users/#{username}/owners"
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

  def create_owner(key, params={})
    parent = params[:parent] || nil
    name = params['name'] || key
    displayName = params['displayName'] || name
    owner = {
      'key' => key,
      'displayName' => displayName
    }
    owner['parentOwner'] = parent if !parent.nil?
    post('/owners', owner)
  end

  def update_owner(owner_key, owner)
    put("/owners/#{owner_key}", owner)
  end

  def generate_ueber_cert(owner_key)
    uri = "/owners/#{owner_key}/uebercert"
    post uri
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

  def create_user(login, password, superadmin=false)
    user = {
      'username' => login,
      'password' => password,
      'superAdmin' => superadmin
    }

    post("/users", user)
  end

  def update_user(user, username = nil)
    username ||= user[:username]
    put("/users/#{username}", user)
  end

  def list_users()
    get("/users")
  end

  # TODO: drop perms here too?
  def create_role(name, perms=nil)
    perms ||= []
    role = {
      :name => name,
      :permissions => perms,
    }
    post("/roles", role)
  end

  def update_role(role)
    put("/roles/#{role['id']}", role)
  end

  def delete_role(roleid)
    delete("/roles/#{roleid}")
  end

  def add_role_user(role_id, username)
    post("/roles/#{role_id}/users/#{username}")
  end

  def delete_role_user(role_id, username)
    delete("/roles/#{role_id}/users/#{username}")
  end

  def add_role_permission(role_id, permission)
    post("/roles/#{role_id}/permissions", permission)
  end

  def delete_role_permission(role_id, permission_id)
    delete("/roles/#{role_id}/permissions/#{permission_id}")
  end

  def list_roles
    get("/roles")
  end

  def get_role(role_id)
    get("/roles/#{role_id}")
  end

  def delete_user(username)
    uri = "/users/#{username}"
    delete uri
  end

  def list_consumer_types
    get('/consumertypes')
  end

  def get_consumer_type(type_id)
    get("/consumertypes/#{type_id}")
  end

  def get_scheduler_status()
    get("/jobs/scheduler")
  end

  def set_scheduler_status(status)
    post("/jobs/scheduler", status)
  end

  def create_consumer_type(type_label, manifest=false)
    consumer_type =  {
      'label' => type_label,
      'manifest' => manifest
    }

    post('/consumertypes', consumer_type)
  end

  def delete_consumer_type(type_id)
    delete("/consumertypes/#{type_id}")
  end

  def get_pool(poolid)
    get("/pools/#{poolid}")
  end

  def delete_pool(pool_id)
    delete("/pools/#{pool_id}")
  end

  # Deprecated, unless you're a super admin actually looking to list all pools:
  def list_pools(params = {})
    path = "/pools?"
    path << "consumer=#{params[:consumer]}&" if params[:consumer]
    path << "owner=#{params[:owner]}&" if params[:owner]
    path << "product=#{params[:product]}&" if params[:product]
    path << "listall=#{params[:listall]}&" if params[:listall]
    path << "activeon=#{params[:activeon]}&" if params[:activeon]
    results = get(path)

    return results
  end

  def list_owner_pools(owner_key, params = {})
    path = "/owners/#{owner_key}/pools?"
    path << "consumer=#{params[:consumer]}&" if params[:consumer]
    path << "product=#{params[:product]}&" if params[:product]
    path << "listall=#{params[:listall]}&" if params[:listall]
    results = get(path)

    return results
  end

  def list_owner_service_levels(owner_key)
    path = "/owners/#{owner_key}/servicelevels"
    results = get(path)

    return results
  end

  def refresh_pools(owner_key, immediate=false, create_owner=false,
    lazy_regen=true)
    return async_call(immediate) do
      url = "/owners/#{owner_key}/subscriptions?"
      url += "auto_create_owner=true&" if create_owner
      url += "lazy_regen=false&" if !lazy_regen
      put(url)
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

  def delete_content(content_id)
    delete("/content/#{content_id}")
  end

  def add_content_to_product(product_id, content_id, enabled=true)
    post("/products/#{product_id}/content/#{content_id}?enabled=#{enabled}")
  end

  def remove_content_from_product(product_id, content_id)
    delete("/products/#{product_id}/content/#{content_id}")
  end

  def add_product_reliance(product_id, rely_id)
    post("/products/#{product_id}/reliance/#{rely_id}")
  end

  def remove_product_reliance(product_id, rely_id)
    delete("/products/#{product_id}/reliance/#{rely_id}")
  end

  # Promote content to a particular environment.
  #
  # The promotions list should contain hashes like:
  # {
  #   :contentId => contentId,
  #   :enabled => enabled,
  # }
  #
  # Skip the enabled field entirely if you would prefer to just use the default
  # enabled flag from the content.
  def promote_content(env_id, content_promotions)
    url = "/environments/#{env_id}/content"
    post(url, content_promotions)
  end

  # Demomote content from a particular environment.
  #
  # Pass the actual content IDs here, rather than the ID assigned to the
  # EnvironmentContent object.
  def demote_content(env_id, content_ids)
    url = "/environments/#{env_id}/content?"
    content_ids.each do |cid|
      url << "content=#{cid}&"
    end
    delete(url)
  end

  def get_product_owners(product_ids)
    url = "/products/owners?"
    product_ids.each do |pid|
      url << "product=#{pid}&"
    end
    get(url)
  end

  def refresh_pools_for_product(product_id, immediate=false, lazy_regen=true)
    return async_call(immediate) do
      url="/products/#{product_id}/subscriptions?"
      url += "lazy_regen=false&" if !lazy_regen
      put(url)
    end
  end

  def create_product(id, name, params={})

    multiplier = params[:multiplier] || 1
    attributes = params[:attributes] || {}
    dependentProductIds = params[:dependentProductIds] || []
    relies_on = params[:relies_on] || []
    #if product don't have type attributes, create_product will fail on server
    #side.
    attributes['type'] = 'SVC' if attributes['type'].nil?
    product = {
      'name' => name,
      'id' => id,
      'multiplier' => multiplier,
      'attributes' => attributes.collect {|k,v| {'name' => k, 'value' => v}},
      'dependentProductIds' => dependentProductIds,
      'reliesOn' => relies_on
    }

    post("/products", product)
  end

  def get_product(product_id)
    get("/products/#{product_id}")
  end

  def delete_product(product_id)
    delete("/products/#{product_id}")
  end

  def get_product_cert(product_id)
    get("/products/#{product_id}/certificate")
  end

  # TODO: Should we change these to bind to better match terminology?
  def consume_pool(pool, params={})
    quantity = params[:quantity] || nil
    uuid = params[:uuid] || @uuid
    async = params[:async] || nil
    path = "/consumers/#{uuid}/entitlements?pool=#{pool}"
    path << "&quantity=#{quantity}" if quantity
    path << "&async=#{async}" if async

    post(path)
  end

  def consume_product(product=nil, params={})
    quantity = params[:quantity] || nil
    uuid = params[:uuid] || @uuid
    async = params[:async] || nil
    entitle_date = params[:entitle_date] || nil
    path = "/consumers/#{uuid}/entitlements?"
    path << "product=#{product}&" if product
    path << "quantity=#{quantity}&" if quantity
    path << "async=#{async}&" if async
    path << "entitle_date=#{entitle_date}" if entitle_date

    post(path)
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

  def delete_rules()
    delete("/rules")
  end

  def list_consumers(args={})
    query = "/consumers?"

    # Ideally, call this method with an owner parameter. Only
    # super admins will be able to call without and list all
    # consumers.
    query = "/owners/#{args[:owner]}/consumers?" if args[:owner]

    query << "username=#{args[:username]}&" if args[:username]
    query << "type=#{args[:type]}&" if args[:type]
    get(query)
  end

  def list_owner_consumers(owner_key)
    query = "/owners/#{owner_key}/consumers"
    get(query)
  end

  def get_consumer(consumer_id=nil)
    consumer_id ||= @uuid
    get("/consumers/#{consumer_id}")
  end

  def get_compliance(consumer_id=nil)
    consumer_id ||= @uuid
    get("/consumers/#{consumer_id}/compliance")
  end

  def get_consumer_host(consumer_id=nil)
    consumer_id ||= @uuid
    get("/consumers/#{consumer_id}/host")
  end

  def get_consumer_guests(consumer_id=nil)
    consumer_id ||= @uuid
    get("/consumers/#{consumer_id}/guests")
  end

  def unbind_entitlement(eid, params={})
    uuid = params[:uuid] || @uuid
    delete("/consumers/#{uuid}/entitlements/#{eid}")
  end

  def autobind_dryrun(consumer_id, service_level=nil)
    consumer_id ||= @uuid
    query = "/consumers/#{consumer_id}/entitlements/dry-run"
    query << "?service_level=#{service_level}" if service_level
    get(query)
  end

  def list_subscriptions(owner_key, params={})
    results = get("/owners/#{owner_key}/subscriptions")
    return results
  end

  def get_subscription(sub_id)
    return get("/subscriptions/#{sub_id}")
  end

  def get_subscription_cert(sub_id)
    return get_text("/subscriptions/#{sub_id}/cert", 'text/plain')
  end

  def get_subscription_cert_by_ent_id(ent_id)
    return get_text("/entitlements/#{ent_id}/upstream_cert", 'text/plain')
  end

  def create_subscription(owner_key, product_id, quantity=1,
                          provided_products=[], contract_number='',
                          account_number='', order_number='', 
                          start_date=nil, end_date=nil)
    start_date ||= Date.today
    end_date ||= start_date + 365

    subscription = {
      'startDate' => start_date,
      'endDate'   => end_date,
      'quantity'  =>  quantity,
      'accountNumber' => account_number,
      'orderNumber' => order_number,
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

  def list_activation_keys(owner_key=nil)
    if owner_key.nil?
      return get("/activation_keys")
    end
    return get("/owner/#{owner_key}/activation_keys")
  end

  def create_activation_key(owner_key, name)
    key = {
      :name => name,
    }
    return post("/owners/#{owner_key}/activation_keys", key)
  end

  def get_activation_key(key_id)
    return get("/activation_keys/#{key_id}")
  end

  def create_environment(owner_key, env_id, env_name, description=nil)
    env = {
      :id => env_id,
      :name => env_name,
      :description => description,
    }
    return post("/owners/#{owner_key}/environments", env)
  end

  def list_environments(owner_key, env_name=nil)
    path = "/owners/#{owner_key}/environments"
    path << "?name=#{env_name}&" if env_name
    return get(path)
  end

  def get_environment(env_id)
    return get("/environments/#{env_id}")
  end

  def delete_environment(env_id)
    return delete("/environments/#{env_id}")
  end

  def update_activation_key(key)
    return put("/activation_keys/#{key['id']}", key)
  end

  def delete_activation_key(key_id)
    return delete("/activation_keys/#{key_id}")
  end

  def activation_key_pools(key_id)
    return get("/activation_keys/#{key_id}/pools")
  end

  def add_pool_to_key(key_id, pool_id, quantity=nil)
    if(quantity)
      return post("/activation_keys/#{key_id}/pools/#{pool_id}?quantity=#{quantity}")
    else
      return post("/activation_keys/#{key_id}/pools/#{pool_id}")
    end
  end

  def remove_pool_from_key(key_id, pool_id)
    return delete("/activation_keys/#{key_id}/pools/#{pool_id}")
  end

  def list_certificates(serials = [])
    path = "/consumers/#{@uuid}/certificates"
    path += "?serials=" + serials.join(",") if serials.length > 0
    return get(path)
  end

  def export_certificates(dest_dir, serials = [])
    path = "/consumers/#{@uuid}/certificates"
    path += "?serials=" + serials.join(",") if serials.length > 0
    begin
      get_file(path, dest_dir)
    rescue Exception => e
      puts e.response
    end
  end

  def regenerate_entitlement_certificates(lazy_regen=true)
    url = "/consumers/#{@uuid}/certificates?"
    url += "?lazy_regen=false" if !lazy_regen
    return put(url)
  end

  def regenerate_entitlement_certificates_for_product(product_id, immediate=false, lazy_regen=true)
    return async_call(immediate) do
      url = "/entitlements/product/#{product_id}"
      url += "?lazy_regen=false" if !lazy_regen
      put(url)
    end
  end

  def regenerate_entitlement_certificates_for_entitlement(entitlement_id, consumer_uuid=nil)
    consumer_uuid ||= @uuid
    return put("/consumers/#{consumer_uuid}/certificates?entitlement=#{entitlement_id}")
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

  def list_consumer_events(owner_key, consumer_id)
    get_text("/owners/#{owner_key}/consumers/#{consumer_id}/atom")
  end

  def list_events
    get '/events'
  end

  def list_imports(owner_key)
    get "/owners/#{owner_key}/imports"
  end

  def list_jobs(owner_key)
    get "/jobs?owner=#{owner_key}"
  end

  def get_job(job_id)
    get "/jobs/#{job_id}"
  end

  def cancel_job(job_id)
    delete "/jobs/#{job_id}"
  end

  def import(owner_key, filename, params = {})
    path = "/owners/#{owner_key}/imports?"
    if params.has_key? :force
      if params[:force].kind_of? Array
        # New style, array of conflict keys to force:
        params[:force].each do |f|
          path += "force=#{f}&"
        end
      else
        # Old style, force=true/false:
        path += "force=#{force}"
      end
    end
    post_file path, File.new(filename)
  end

  def undo_import(owner_key)
    path = "/owners/#{owner_key}/imports"
    delete(path)
  end

  def generate_statistics()
    put "/statistics/generate"
  end

  def get_owner_perpool(owner_id, pool_id, val_type)
    return get "/owners/#{owner_id}/statistics/PERPOOL/#{val_type}?reference=#{pool_id}"
  end

  def get_owner_perproduct(owner_id, prod_id, val_type)
    return get "/owners/#{owner_id}/statistics/PERPRODUCT/#{val_type}?reference=#{prod_id}"
  end

  def get_consumer_count(owner_id)
    return get "/owners/#{owner_id}/statistics/TOTALCONSUMERS"
  end

  def get_subscription_count_raw(owner_id)
    return get "/owners/#{owner_id}/statistics/TOTALSUBSCRIPTIONCOUNT/RAW"
  end

  def get_subscription_consumed_count_raw(owner_id)
    return get "/owners/#{owner_id}/statistics/TOTALSUBSCRIPTIONCONSUMED/RAW"
  end

  def get_subscription_consumed_count_percentage(owner_id)
    return get "/owners/#{owner_id}/statistics/TOTALSUBSCRIPTIONCONSUMED/PERCENTAGECONSUMED"
  end

  def get_system_physical_count(owner_id)
    return get "/owners/#{owner_id}/statistics/SYSTEM/PHYSICAL"
  end

  def get_system_virtual_count(owner_id)
    return get "/owners/#{owner_id}/statistics/SYSTEM/VIRTUAL"
  end

  def get_perpool(pool_id, val_type)
    return get "/pools/#{pool_id}/statistics/#{val_type}"
  end

  def get_perproduct(prod_id, val_type)
    return get "/products/#{prod_id}/statistics/#{val_type}"
  end

  def get_crl
    OpenSSL::X509::CRL.new(get_text('/crl'))
  end

  def get_guests(consumer_id)
    get("/consumers/#{consumer_id}/guests")
  end

  def get(uri, accept_header = :json)
    puts ("GET #{uri}") if @verbose
    response = get_client(uri, Net::HTTP::Get, :get)[URI.escape(uri)].get \
      :accept => accept_header
    return JSON.parse(response.body)
  end

  # Assumes a zip archive currently. Returns filename (random#.zip) of the
  # temp file created.
  def get_file(uri, dest_dir)
    response = get_client(uri, Net::HTTP::Get, :get)[URI.escape(uri)].get :accept => "application/zip"
    filename = response.headers[:content_disposition] == nil ? "tmp_#{rand}.zip" : response.headers[:content_disposition].split("filename=")[1]
    filename = File.join(dest_dir, filename)
    File.open(filename, 'w') { |f| f.write(response.body) }
    filename
  end

  def get_text(uri, accept_header = nil)
    if accept_header.nil?
      response = get_client(uri, Net::HTTP::Get, :get)[URI.escape(uri)].get :content_type => 'text/plain'
    else
      response = get_client(uri, Net::HTTP::Get, :get)[URI.escape(uri)].get :content_type => 'text/plain', :accept => accept_header
    end
    return (response.body)
  end

  def post(uri, data=nil)
    puts ("POST #{uri} #{data}") if @verbose
    data = data.to_json if not data.nil?
    response = get_client(uri, Net::HTTP::Post, :post)[URI.escape(uri)].post(
      data, :content_type => :json, :accept => :json)
    return JSON.parse(response.body) unless response.body.empty?
  end

  def post_file(uri, file=nil)
    response = get_client(uri, Net::HTTP::Post, :post)[URI.escape(uri)].post(:import => file)
    return JSON.parse(response.body) unless response.body.empty?
  end

  def post_text(uri, data=nil)
    response = get_client(uri, Net::HTTP::Post, :post)[URI.escape(uri)].post(data, :content_type => 'text/plain', :accept => 'text/plain' )
    return response.body
  end

  def put(uri, data=nil)
    puts ("PUT #{uri} #{data}") if @verbose
    data = data.to_json if not data.nil?
    response = get_client(uri, Net::HTTP::Put, :put)[uri].put(
      data, :content_type => :json, :accept => :json)

    return JSON.parse(response.body) unless response.body.empty?
  end

  def delete(uri)
    puts ("DELETE #{uri}") if @verbose
    response = get_client(uri, Net::HTTP::Delete, :delete)[URI.escape(uri)].delete
    return JSON.parse(response.body) unless response.body.empty?
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
