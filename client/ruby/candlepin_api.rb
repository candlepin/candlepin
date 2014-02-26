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
              environment=nil, capabilities=[], hypervisor_id=nil)
    consumer = {
      :type => {:label => type},
      :name => name,
      :facts => facts,
      :installedProducts => installedProducts
    }
    consumer[:capabilities] = capabilities.collect { |name| {'name' => name} } if capabilities

    consumer[:uuid] = uuid if not uuid.nil?

    consumer[:hypervisorId] = {:hypervisorId => hypervisor_id} if hypervisor_id

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

  def hypervisor_check_in(owner, host_guest_mapping={}, create_missing=nil)
    path = get_path("hypervisors") + "?owner=#{owner}"
    unless create_missing.nil?
      path << "&create_missing=#{create_missing}"
    end
    consumers = post(path, host_guest_mapping)
    return consumers
  end

  def remove_deletion_record(deleted_uuid)
    path = get_path("consumers") + "/#{deleted_uuid}/deletionrecord"
    result = delete(path)
    return result
  end

  def get_deleted_consumers(date = nil)
    path = get_path("deleted_consumers")
    if !date.nil?
        path += "?date=#{date}"
    end
    result = get(path)
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
    consumer[:capabilities] = params[:capabilities].collect { |name| {'name' => name} } if params[:capabilities]
    consumer[:hypervisorId] = {:hypervisorId => params[:hypervisorId]} if params[:hypervisorId]

    path = get_path("consumers")
    put("#{path}/#{uuid}", consumer)
  end

  def update_guestids(guestIds)
    path = "/consumers/#{@uuid}/guestids"
    put(path, guestIds)
  end

  def update_guestid(guest)
    path = "/consumers/#{@uuid}/guestids/#{guest[:guestId]}"
    put(path, guest)
  end

  def delete_guestid(guestuuid, unregister = false)
    path = "/consumers/#{@uuid}/guestids/#{guestuuid}"
    if unregister
        path << "?unregister=true"
    end
    delete(path)
  end

  def get_guestids()
    path = "/consumers/#{@uuid}/guestids"
    results = get(path)
    return results
  end

  def get_guestid(guestuuid)
    path = "/consumers/#{@uuid}/guestids/#{guestuuid}"
    result = get(path)
    return result
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
    # Convert owner to a string in case someone gave us a numeric key
    if owner.to_s[0] == "/"
      return get(owner)
    end

    # Otherwise, assume owner key was given:
    get("/owners/#{owner}")
  end

  # expects an owner key
  def get_owner_info(owner)
    get("/owners/#{owner}/info")
  end

  def get_owner_hypervisors(owner, hypervisor_ids = [])
    url = "/owners/#{owner}/hypervisors?"
    hypervisor_ids.each do |hid|
      url << "hypervisor_id=#{hid}&"
    end
    get(url)
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

  def set_owner_log_level(owner_key, log_level=nil)
    uri = "/owners/#{owner_key}/log"
    uri << "?level=#{log_level}" if log_level
    put uri
  end

  def delete_owner_log_level(owner_key)
    delete "/owners/#{owner_key}/log"
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

  def get_pool(poolid, uuid=nil)
    path = "/pools/#{poolid}?"
    path += "consumer=#{uuid}" if uuid
    get(path)
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

  def list_owner_pools(owner_key, params = {}, attribute_filters=[])
    path = "/owners/#{owner_key}/pools?"
    path << "consumer=#{params[:consumer]}&" if params[:consumer]
    path << "product=#{params[:product]}&" if params[:product]
    path << "listall=#{params[:listall]}&" if params[:listall]
    path << "page=#{params[:page]}&" if params[:page]
    path << "per_page=#{params[:per_page]}&" if params[:per_page]
    path << "order=#{params[:order]}&" if params[:order]
    path << "sort_by=#{params[:sort_by]}&" if params[:sort_by]
    
    # Attribute filters are specified in the following format:
    #    {attributeName}:{attributeValue}
    attribute_filters.each do |filter|
        path << "attribute=%s&" % [filter]
    end
    
    results = get(path)
    return results
  end

  def list_owner_service_levels(owner_key, exempt=false)
    path = "/owners/#{owner_key}/servicelevels"
    path << "?exempt=#{exempt}" if exempt
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

  def export_consumer(dest_dir, params={})
    path = "/consumers/#{@uuid}/export"
    path += "?" if params
    path += "cdn_label=#{params[:cdn_label]}&" if params[:cdn_label]
    path += "webapp_prefix=#{params[:webapp_prefix]}&" if params[:webapp_prefix]
    path += "api_url=#{params[:api_url]}&" if params[:api_url]

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

  def autoheal_org(owner_key)
    # Note that this method returns an array of JobDetails and
    # not just a single JobDetail.
    post("/owners/#{owner_key}/entitlements")
  end

  def list_products(product_uuids=nil)
    method = "/products?"
    if product_uuids
      product_uuids.each { |uuid|
        method << "&product=" << uuid
      }
    end
    get(method)
  end

  def create_content(name, id, label, type, vendor,
      params={}, post=true)

    metadata_expire = params[:metadata_expire] || nil
    required_tags = params[:required_tags] || nil
    content_url = params[:content_url] || ""
    arches = params[:arches] || ""
    gpg_url = params[:gpg_url] || ""
    modified_product_ids = params[:modified_products] || []

    content = {
      'name' => name,
      'id' => id,
      'label' => label,
      'type' => type,
      'vendor' => vendor,
      'contentUrl' => content_url,
      'arches' => arches,
      'gpgUrl' => gpg_url,
      'modifiedProductIds' => modified_product_ids
    }
    content['metadataExpire'] = metadata_expire if not metadata_expire.nil?
    content['requiredTags'] = required_tags if not required_tags.nil?
    if post
      post("/content", content)
    else
      return content
    end
  end

  def create_batch_content(contents=[])
    post("/content/batch", contents)
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

  def update_content(content_id, updates={})
    current_content = get_content(content_id)
    updates.each do |key, value|
      current_content[key] = value
    end
    put("/content/#{content_id}", current_content)
  end

  def add_content_to_product(product_id, content_id, enabled=true)
    post("/products/#{product_id}/content/#{content_id}?enabled=#{enabled}")
  end

  def add_batch_content_to_product(product_id, content_ids, enabled=true)
    data = {}
    content_ids.each do |id|
      data[id] = enabled
    end
    post("/products/#{product_id}/batch_content", data)
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

  def update_product(product_id, params={})

    product = {
      :id => product_id
    }

    product[:name] = params[:name] if params[:name]
    product[:multiplier] = params[:multiplier] if params[:multiplier]
    product[:attributes] = params[:attributes] if params[:attributes]
    product[:dependentProductIds] = params[:dependentProductIds] if params[:dependentProductIds]
    product[:relies_on] = params[:relies_on] if params[:relies_on]

    put("/products/#{product_id}", product)
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

    path = "/consumers/#{uuid}/entitlements?"
    path << "product=#{params[:product_id]}&" if params[:product_id]
    path << "page=#{params[:page]}&" if params[:page]
    path << "per_page=#{params[:per_page]}&" if params[:per_page]
    path << "order=#{params[:order]}&" if params[:order]
    path << "sort_by=#{params[:sort_by]}&" if params[:sort_by]
    results = get(path)
    return results
  end

  def list_pool_entitlements(pool_id, params={})
    path = "/pools/#{pool_id}/entitlements"
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
    query << "page=#{args[:page]}&" if args[:page]
    query << "per_page=#{args[:per_page]}&" if args[:per_page]
    query << "order=#{args[:order]}&" if args[:order]
    query << "sort_by=#{args[:sort_by]}&" if args[:sort_by]
    # We could join("&") but that would not leave a trailing ampersand
    # which is nice for the next person who needs to add an argument to
    # the query.
    query << args[:uuids].map {|uuid| "uuid=#{uuid}&"}.join("") if args[:uuids]
    get(query)
  end

  def list_owner_consumers(owner_key, consumer_types=[])
    query = "/owners/#{owner_key}/consumers"
    if !consumer_types.empty?
        query += "?type=" + consumer_types.join("&type=")
    end
    get(query)
  end

  def get_consumer(consumer_id=nil)
    consumer_id ||= @uuid
    get("/consumers/#{consumer_id}")
  end

  def get_compliance(consumer_id=nil, on_date=nil)
    consumer_id ||= @uuid
    query = "/consumers/#{consumer_id}/compliance"
    if on_date
        query << "?on_date=#{on_date}"
    end
    get(query)
  end

  def get_compliance_list(consumer_ids=nil)
    consumer_ids ||= [@uuid]
    query = "/consumers/compliance?"
    query << consumer_ids.map {|uuid| "uuid=#{uuid}"}.join("&")
    get(query)
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
                          start_date=nil, end_date=nil, params={})
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

    if params['derived_product_id']
      subscription['derivedProduct'] = { 'id' => params['derived_product_id'] }
    end

    if params['derived_provided_products']
      subscription['derivedProvidedProducts'] = params['derived_provided_products'].collect { |pid| {'id' => pid} }
    end

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

  def add_content_overrides_to_key(key_id, overrides)
      return put("/activation_keys/#{key_id}/content_overrides", overrides)
  end

  def remove_activation_key_overrides(key_id, overrides)
      return delete("/activation_keys/#{key_id}/content_overrides", overrides)
  end

  def get_content_overrides_for_key(key_id)
      return get("/activation_keys/#{key_id}/content_overrides")
  end

  def set_activation_key_release(key_id, release)
      return post("/activation_keys/#{key_id}/release", release)
  end

  def get_activation_key_release(key_id)
      return get("/activation_keys/#{key_id}/release")
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

  def list_consumer_events(consumer_id)
    get("/consumers/#{consumer_id}/events")
  end

  def list_consumer_events_atom(consumer_id)
    get_text("/consumers/#{consumer_id}/atom")
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

  def create_distributor_version(name, display_name, capabilities=[])
    version =  {
      'name' => name,
      'displayName' => display_name,
      'capabilities' => capabilities.collect { |name| {'name' => name} }
    }
    post('/distributor_versions', version)
  end

  def update_distributor_version(id, name, display_name, capabilities=[])
    version =  {
      'name' => name,
      'displayName' => display_name,
      'capabilities' => capabilities.collect { |name| {'name' => name} }
    }
    put("/distributor_versions/#{id}", version)
  end

  def delete_distributor_version(id)
    delete("/distributor_versions/#{id}")
  end

  def get_distributor_versions(name_search = nil, capability = nil)
    query = "/distributor_versions"
    query << "?" if name_search or capability
    query << "name_search=#{name_search}" if name_search
    query << "&" if name_search and capability
    query << "capability=#{capability}" if capability
    get(query)
  end

  def create_cdn(label, name, url, cert=nil)
    cdn =  {
      'label' => label,
      'name' => name,
      'url' => url,
      'certificate' => cert
    }
    post('/cdn', cdn)
  end

  def update_cdn(label, name, url, cert=nil)
    cdn =  {
      'name' => name,
      'url' => url,
      'certificate' => cert
    }
    put("/cdn/#{label}", cdn)
  end

  def delete_cdn(label)
    delete("/cdn/#{label}")
  end

  def get_cdns()
    get("/cdn")
  end

  def add_content_overrides(uuid, overrides=[])
    put("consumers/#{uuid}/content_overrides", overrides)
  end

  def delete_content_overrides(uuid, overrides=[])
    delete("consumers/#{uuid}/content_overrides", overrides)
  end

  def get_content_overrides(uuid)
    get("consumers/#{uuid}/content_overrides")
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

  def delete(uri, data=nil)
    puts ("DELETE #{uri}") if @verbose
    client = get_client(uri, Net::HTTP::Delete, :delete)
    client.options[:payload] = data.to_json if not data.nil?
    response = client[URI.escape(uri)].delete(:content_type => :json, :accepts => :json)
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
