require 'base64'
require 'cgi'
require 'date'
require 'json'
require 'oauth'
require 'openssl'
require 'pp'
require 'rest_client'
require 'rubygems'
require 'uri'

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
                 use_ssl = true, verify_ssl=nil)

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
    if not verify_ssl.nil?
      @verify_ssl = verify_ssl
    elsif host == 'localhost'
      @verify_ssl = OpenSSL::SSL::VERIFY_NONE
    else
      @verify_ssl = OpenSSL::SSL::SSLContext::DEFAULT_PARAMS[:verify_mode]
    end

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
              environment=nil, capabilities=[], hypervisor_id=nil,
              content_tags=[], created_date=nil, last_checkin_date=nil,
              annotations=nil, recipient_owner_key=nil, user_agent=nil,
              entitlement_count=0, id_cert=nil, serviceLevel=nil, role=nil, usage=nil,
              addOns=nil, reporter_id=nil, autoheal=nil)

    consumer = {
      :type => {:label => type},
      :name => name,
      :facts => facts,
      :installedProducts => installedProducts,
      :contentTags => content_tags,
    }

    consumer[:capabilities] = capabilities.collect { |name| {'name' => name} } if capabilities
    consumer[:uuid] = uuid if not uuid.nil?
    consumer[:hypervisorId] = {:hypervisorId => hypervisor_id} if hypervisor_id
    consumer[:hypervisorId][:reporterId] = reporter_id if reporter_id
    consumer[:created] = created_date if created_date
    consumer[:lastCheckin] = last_checkin_date if last_checkin_date
    consumer[:annotations] = annotations if annotations

    #note: ent count and id_cert are added to demonstrate erroneous input
    consumer[:entitlementCount] = entitlement_count if entitlement_count
    consumer[:idCert] = id_cert if id_cert

    consumer[:serviceLevel] = serviceLevel if serviceLevel
    consumer[:autoheal] = autoheal if not autoheal.nil?
    consumer[:role] = role if role
    consumer[:usage] = usage if usage
    consumer[:addOns] = addOns if addOns

    params = {}

    if environment.nil?
      path = get_path("consumers")
      params[:owner] = owner_key if not owner_key.nil?
    else
      path = "/environments/#{environment}/consumers"
    end

    params[:username] = username if username
    params[:activation_keys] = activation_keys.join(",") if activation_keys.length > 1
    params[:activation_keys] = activation_keys[0] if activation_keys.length == 1
    params[:user_agent] = user_agent if user_agent

    @consumer = post(path, params, consumer)
    return @consumer
  end

  def hypervisor_check_in(owner, host_guest_mapping={}, create_missing=nil)
    path = get_path("hypervisors")
    params = {
        :owner => owner
    }

    params[:create_missing] = create_missing unless create_missing.nil?

    return post(path, params, host_guest_mapping)
  end

  def hypervisor_update(owner, json_data, create_missing=nil, reporter_id=nil)
    path = get_path("hypervisors") + "/#{owner}"
    params = {}

    params[:create_missing] = create_missing unless create_missing.nil?
    params[:reporter_id] = reporter_id unless reporter_id.nil?

    return post_text(path, params, json_data, 'json')
  end

  def hypervisor_heartbeat_update(owner, reporter_id=nil)
    path = get_path("hypervisors") + "/#{owner}/heartbeat"
    params = {}
    params[:reporter_id] = reporter_id unless reporter_id.nil?

    return put_empty(path, params, accept='application/json')
  end

  def hypervisor_update_file(owner, json_file, create_missing=nil, reporter_id=nil)
    path = get_path("hypervisors") + "/#{owner}"
    params = {}

    params[:create_missing] = create_missing unless create_missing.nil?
    params[:reporter_id] = reporter_id unless reporter_id.nil?

    json_data = ''
    File.open(json_file) do |f|
      f.each_line do |line|
        json_data << "\n" << line
      end
    end

    return post_text(path, params, json_data, 'json')
  end

  def remove_deletion_record(deleted_uuid)
    path = get_path("consumers") + "/#{deleted_uuid}/deletionrecord"
    return delete(path)
  end

  def get_deleted_consumers(date = nil)
    path = get_path("deleted_consumers")
    params = {}

    if !date.nil?
      params[:date] = date
    end

    return get(path, params)
  end

  def update_consumer(params)
    uuid = params[:uuid] || @uuid

    consumer = {
      :uuid => uuid
    }

    consumer[:facts] = params[:facts] if params[:facts]
    consumer[:installedProducts] = params[:installedProducts] if params[:installedProducts]
    consumer[:guestIds] = params[:guestIds] if params[:guestIds]
    consumer[:autoheal] = params[:autoheal] if params.has_key?(:autoheal)
    consumer[:serviceLevel] = params[:serviceLevel] if params.has_key?(:serviceLevel)
    consumer[:role] = params[:role] if params.has_key?(:role)
    consumer[:usage] = params[:usage] if params.has_key?(:usage)
    consumer[:addOns] = params[:addOns] if params.has_key?(:addOns)
    consumer[:capabilities] = params[:capabilities].collect { |name| {'name' => name} } if params[:capabilities]
    consumer[:hypervisorId] = {:hypervisorId => params[:hypervisorId]} if params[:hypervisorId]
    consumer['contentAccessMode'] = params['contentAccessMode'] if params.key?('contentAccessMode')
    consumer[:environment] = params[:environment] if params.key?(:environment)

    path = get_path("consumers")
    put("#{path}/#{uuid}", {}, consumer)
  end

  def update_guestids(guestIds)
    path = "/consumers/#{@uuid}/guestids"
    put(path, {}, guestIds)
  end

  def update_guestid(guest)
    path = "/consumers/#{@uuid}/guestids/#{guest[:guestId]}"
    put(path, {}, guest)
  end

  def delete_guestid(guestuuid, unregister = false)
    path = "/consumers/#{@uuid}/guestids/#{guestuuid}"
    params = {}

    if unregister
        params[:unregister] = true
    end

    delete(path, params)
  end

  def get_guestids(uuid = nil)
    uuid = uuid || @uuid
    path = "/consumers/#{uuid}/guestids"
    return get(path)
  end

  def get_guestid(guestuuid)
    path = "/consumers/#{@uuid}/guestids/#{guestuuid}"
    return get(path)
  end

  def update_entitlement(params)
    entitlement = {
      :id => params[:id],
      :quantity => params[:quantity]
    }

    put("/entitlements/#{params[:id]}", {}, entitlement)
  end

  def migrate_entitlement(params)
    path = "/entitlements/#{params[:id]}/migrate"
    qparams = {
      :to_consumer => params[:dest]
    }

    if params[:quantity]
      qparams[:quantity] = params[:quantity]
    end

    put(path, qparams, params)
  end

  def get_user_info(user)
    get("/users/#{user}")
  end

  def get_user_roles(user)
    get("/users/#{user}/roles")
  end

  def list_owners(params = {})
    path = "/owners"
    return get(path)
  end

  def list_users_owners(username, params = {})
    path = "/users/#{username}/owners"
    return get(path)
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

  def get_owner_syspurpose(owner)
    get("/owners/#{owner}/system_purpose")
  end

  def get_owner_consumers_syspurpose(owner)
    get("/owners/#{owner}/consumers_system_purpose")
  end

  def get_owner_hypervisors(owner, hypervisor_ids = [])
    url = "/owners/#{owner}/hypervisors"
    params = {
      :hypervisor_id => hypervisor_ids
    }

    get(url, params)
  end

  def get_owners_with_product(product_ids = [])
    path = "/products/owners"
    params = {
      :product => product_ids
    }

    return get(path, params)
  end

  def create_owner(key, params={})
    parent = params[:parent] || nil
    name = params['name'] || key
    displayName = params['displayName'] || name
    contentAccessModeList = params['contentAccessModeList'] || nil
    contentAccessMode = params['contentAccessMode'] || nil

    owner = {
      'key' => key,
      'displayName' => displayName
    }

    owner['contentAccessModeList'] = contentAccessModeList unless contentAccessModeList.nil?
    owner['contentAccessMode'] = contentAccessMode unless contentAccessMode.nil?
    owner['parentOwner'] = parent if !parent.nil?

    post('/owners', {}, owner)
  end

  def update_owner(owner_key, owner)
    put("/owners/#{owner_key}",{},  owner)
  end

  def set_owner_log_level(owner_key, log_level=nil)
    uri = "/owners/#{owner_key}/log"

    params = {}
    params[:level] = log_level if log_level

    put(uri, params)
  end

  def delete_owner_log_level(owner_key)
    delete "/owners/#{owner_key}/log"
  end

  def generate_ueber_cert(owner_key, filename="")
    uri = "/owners/#{owner_key}/uebercert"
    result_json = post uri
    if !filename.empty?
      File.open(filename, 'w') { |f| f.write("#{result_json['key']}\n\n#{result_json['cert']}") }
      return filename
    end
    result_json
  end

  def delete_owner(owner_key, revoke=true, force=false)
    uri = "/owners/#{owner_key}"

    params = {}
    params[:revoke] = false if !revoke
    params[:force] = force if force

    delete(uri, params)
  end

  def migrate_owner(owner_key, uri, immediate=false)
    params = {
      :id => owner_key,
      :uri => uri
    }

    return async_call(immediate) do
      put("owners/migrate", params)
    end
  end

  def heal_owner(owner_key)
    post "owners/#{owner_key}/entitlements"
  end

  def create_user(login, password, superadmin=false)
    user = {
      'username' => login,
      'password' => password,
      'superAdmin' => superadmin
    }

    post("/users", {}, user)
  end

  def update_user(user, username = nil)
    username ||= user[:username]
    put("/users/#{username}", {}, user)
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

    post("/roles", {}, role)
  end

  def update_role(role_name, role)
    put("/roles/#{role_name}", {}, role)
  end

  def delete_role(role_name)
    delete("/roles/#{role_name}")
  end

  def add_role_user(role_name, username)
    post("/roles/#{role_name}/users/#{username}")
  end

  def delete_role_user(role_name, username)
    delete("/roles/#{role_name}/users/#{username}")
  end

  def add_role_permission(role_name, permission)
    post("/roles/#{role_name}/permissions", {}, permission)
  end

  def delete_role_permission(role_name, permission_id)
    delete("/roles/#{role_name}/permissions/#{permission_id}")
  end

  def list_roles
    get("/roles")
  end

  def get_role(role_name)
    get("/roles/#{role_name}")
  end

  def delete_user(username)
    delete("/users/#{username}")
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
    post("/jobs/scheduler", { "running" => status })
  end

  def trigger_job(job, async=false)
    return async_call(!async) do
      post("/jobs/schedule/#{job}")
    end
  end

  def create_consumer_type(type_label, manifest=false)
    consumer_type =  {
      'label' => type_label,
      'manifest' => manifest
    }

    post('/consumertypes', {}, consumer_type)
  end

  def delete_consumer_type(type_id)
    delete("/consumertypes/#{type_id}")
  end

  def get_pool(poolid, uuid=nil)
    path = "/pools/#{poolid}"

    params = {}
    params[:consumer] = uuid if uuid

    get(path, params)
  end

  def delete_pool(pool_id)
    delete("/pools/#{pool_id}")
  end

  # Deprecated, unless you're a super admin actually looking to list all pools:
  def list_pools(params = {})
    return get("/pools", params)
  end

  def list_owner_pools(owner_key, params = {}, attribute_filters=[], dont_parse= false)
    path = "/owners/#{owner_key}/pools"

    params[:attribute] = attribute_filters if !attribute_filters.empty?

    return get(path, params, :json, dont_parse)
  end

  def list_owner_service_levels(owner_key, exempt=false)
    path = "/owners/#{owner_key}/servicelevels"

    params = {}
    params[:exempt] = exempt if exempt

    return get(path, params)
  end

  def refresh_pools(owner_key, immediate=false, create_owner=false, lazy_regen=true)
    return async_call(immediate) do
      url = "/owners/#{owner_key}/subscriptions"

      params = {}
      params[:auto_create_owner] = true if create_owner
      params[:lazy_regen] = false if !lazy_regen

      put(url, params)
    end
  end

  def async_call(immediate, auto_cleanup=true, *args, &blk)
    status = blk.call(args)

    # Hack to limit test churn due to switchover to refresh pools being hosted only:
    # TODO: can be removed if we remove all refresh_pools calls in spec tests.
    if status.nil?
      return status
    end

    return status if immediate

    # otherwise poll the server to make this call synchronous
    finished_states = ['ABORTED', 'FINISHED', 'CANCELED', 'FAILED']

    # We toss this into a new array since async_call is sometimes used with endpoints which return multiple
    # job details
    if !status.kind_of?(Array)
      status = [status]
    end

    status.each_index do |index|
      while !finished_states.include? status[index]['state'].upcase
        sleep 1
        status[index] = get(status[index]['statusPath'])
      end

      if auto_cleanup
        delete('/jobs', {:id => [status[index]['id']]}, nil, true)
      end
    end

    # If we only have one job detail, return the status directly; otherwise return a collection of job results
    return (status.length == 1 ? status[0]['resultData'] : status.map {|detail| detail['resultData']})
  end

  def export_consumer(dest_dir, params={}, uuid=nil)
    uuid = @uuid unless uuid
    path = "/consumers/#{uuid}/export"
    do_consumer_export(path, dest_dir, params)
  end

  def export_consumer_async(params={}, uuid=nil)
    uuid = @uuid unless uuid
    path = "/consumers/#{uuid}/export/async"
    do_consumer_export(path, nil, params)
  end

  def download_consumer_export(uuid, export_id, dest_dir)
    path = "/consumers/#{uuid}/export/#{export_id}"
    get_file(path, {}, dest_dir)
  end

  def get_entitlement(entitlement_id)
    get("/entitlements/#{entitlement_id}")
  end

  def unregister(uuid = nil)
    uuid = @uuid unless uuid
    delete("/consumers/#{uuid}")
  end

  def revoke_all_entitlements(uuid = nil)
    uuid = @uuid unless uuid
    delete("/consumers/#{uuid}/entitlements")
  end

  def autoheal_org(owner_key)
    # Note that this method returns an array of JobDetails and
    # not just a single JobDetail.
    post("/owners/#{owner_key}/entitlements")
  end

  def autoheal_consumer(uuid = nil)
    uuid = @uuid unless uuid
    post("/consumers/#{uuid}/entitlements")
  end

  def list_products(product_ids=nil, params={})
    path = "/products"
    params[:product] = product_ids if product_ids

    get(path, params)
  end

  def list_products_by_owner(owner_key, product_ids=nil, params={})
    path = "/owners/#{owner_key}/products"
    params[:product] = product_ids if product_ids

    get(path, params)
  end

  def create_content(owner_key, name, id, label, type, vendor, params={}, post=true)
    metadata_expire = params[:metadata_expire] || nil
    required_tags = params[:required_tags] || nil
    content_url = params[:content_url] || nil
    arches = params[:arches] || nil
    gpg_url = params[:gpg_url] || nil
    modified_product_ids = params[:modified_products] || nil

    content = {
      'id' => id,
      'type' => type,
      'label' => label,
      'name' => name,
      'vendor' => vendor
    }

    content['contentUrl'] = content_url if not content_url.nil?
    content['arches'] = arches if not arches.nil?
    content['gpgUrl'] = gpg_url if not gpg_url.nil?
    content['modifiedProductIds'] = modified_product_ids if not modified_product_ids.nil?
    content['metadataExpire'] = metadata_expire if not metadata_expire.nil?
    content['requiredTags'] = required_tags if not required_tags.nil?

    if post
      post("/owners/#{owner_key}/content", {}, content)
    else
      return content
    end
  end

  def create_batch_content(owner_key, contents=[])
    post("/owners/#{owner_key}/content/batch", {}, contents)
  end

  def list_content(owner_key, params={})
    path = "/owners/#{owner_key}/content"
    get(path, params)
  end

  def get_content(owner_key, content_id)
    get("/owners/#{owner_key}/content/#{content_id}")
  end

  def get_content_by_uuid(content_uuid)
    get("/content/#{content_uuid}")
  end

  def delete_content(owner_key, content_id)
    delete("/owners/#{owner_key}/content/#{content_id}")
  end

  def update_content(owner_key, content_id, updates={})
    current_content = get_content(owner_key, content_id)
    updates.each do |key, value|
      current_content[key] = value
    end

    put("/owners/#{owner_key}/content/#{content_id}", {}, current_content)
  end

  def add_content_to_product(owner_key, product_id, content_id, enabled=true)
    post("/owners/#{owner_key}/products/#{product_id}/content/#{content_id}", {:enabled => enabled})
  end

  def add_batch_content_to_product(owner_key, product_id, content_ids, enabled=true)
    data = {}
    content_ids.each do |id|
      data[id] = enabled
    end
    post("/owners/#{owner_key}/products/#{product_id}/batch_content", {}, data)
  end

  def add_all_content_to_product(owner_key, product_id, content)
    data = {}
    content.each do |id, enabled|
      data[id] = enabled
    end
    post("/owners/#{owner_key}/products/#{product_id}/batch_content", {}, data)
  end

  def remove_content_from_product(owner_key, product_id, content_id)
    delete("/owners/#{owner_key}/products/#{product_id}/content/#{content_id}")
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
    return post(url, {}, content_promotions)
  end

  # Demomote content from a particular environment.
  #
  # Pass the actual content IDs here, rather than the ID assigned to the
  # EnvironmentContent object.
  def demote_content(env_id, content_ids)
    url = "/environments/#{env_id}/content"

    params = {}
    params[:content] = content_ids if content_ids

    return delete(url, params)
  end

  def get_product_owners(product_ids)
    url = "/products/owners"

    params = {}
    params[:product] = product_ids if product_ids

    return get(url, params)
  end

  def refresh_pools_for_orgs_with_product(product_ids, immediate=true, lazy_regen=true)
    return async_call(immediate) do
      url = "/products/subscriptions"

      params = {}
      params[:product] = product_ids if product_ids
      params[:lazy_regen] = false if !lazy_regen

      put(url, params)
    end
  end

  def refresh_pools_for_product(owner_key, product_id, immediate=false, lazy_regen=true)
    return async_call(immediate) do
      url = "/owners/#{owner_key}/products/#{product_id}/subscriptions"

      params = {}
      params[:lazy_regen] = false if !lazy_regen

      return put(url, params)
    end
  end

  def create_product(owner_key, id, name, params={})
    multiplier = params[:multiplier] || 1
    attributes = params[:attributes] || {}
    dependentProductIds = params[:dependentProductIds] || []
    branding = params[:branding] || []
    relies_on = params[:relies_on] || []
    providedProducts = params[:providedProducts] || []

    #if product don't have type attributes, create_product will fail on server
    #side.
    attributes['type'] = 'SVC' if attributes['type'].nil?
    product = {
      'name' => name,
      'id' => id,
      'multiplier' => multiplier,
      'attributes' => attributes,
      'dependentProductIds' => dependentProductIds,
      'branding' => branding,
      'reliesOn' => relies_on,
      'providedProducts' => providedProducts.collect { |pid| {'id' => pid} || pid},
    }

    post("/owners/#{owner_key}/products", {}, product)
  end

  def update_product(owner_key, product_id, params={})
    product = {
      :id => product_id
    }

    product[:name] = params[:name] if params[:name]
    product[:multiplier] = params[:multiplier] if params[:multiplier]
    product[:attributes] = params[:attributes] if params[:attributes]
    product[:dependentProductIds] = params[:dependentProductIds] if params[:dependentProductIds]
    product[:branding] = params[:branding] if params[:branding]
    product[:relies_on] = params[:relies_on] if params[:relies_on]

    if params[:providedProducts]
      product['providedProducts'] = params[:providedProducts]
        .collect { |pid| {'id' => pid} }
    end

    put("/owners/#{owner_key}/products/#{product_id}", {}, product)
  end

  def get_product(owner_key, product_id)
    get("/owners/#{owner_key}/products/#{product_id}")
  end

  def get_product_by_uuid(product_uuid)
    get("/products/#{product_uuid}")
  end

  def delete_product(owner_key, product_id)
    delete("/owners/#{owner_key}/products/#{product_id}")
  end

  def get_product_cert(owner_key, product_id)
    get("/owners/#{owner_key}/products/#{product_id}/certificate")
  end

  # TODO: Should we change these to bind to better match terminology?
  def consume_pool(pool_id, params={})
    uuid = params[:uuid] || @uuid
    path = "/consumers/#{uuid}/entitlements"

    qparams = {
      :pool => pool_id
    }

    qparams[:quantity] = params[:quantity] if params[:quantity]
    qparams[:async] = params[:async] if params[:async]

    return post(path, qparams)
  end

  def consume_pool_empty_body(pool_id, params={})
    uuid = params[:uuid] || @uuid
    path = "/consumers/#{uuid}/entitlements?pool=#{pool_id}&quantity=1"
    return post(path, {}," ")
  end

  def consume_product(product=nil, params={})
    uuid = params[:uuid] || @uuid
    path = "/consumers/#{uuid}/entitlements"
    params[:product] = product if product

    post(path, params)
  end

  def fix_entitlement_list_params(params={})
    params.delete(:uuid)

    params[:regen] = false if params.delete(:regen)

    attr_filters = params.delete(:attr_filters) || []
    if !attr_filters.empty?
      params[:attribute] = []

      attr_filters.each do | attr_name, attr_value |
        params[:attribute] << "#{attr_name}:#{attr_value}"
      end
    end

    return params
  end

  # TODO: Could also fetch from /entitlements, a bit ambiguous:
  def list_entitlements(params={})
    uuid = params[:uuid] || @uuid
    path = "/consumers/#{uuid}/entitlements"

    return get(path, fix_entitlement_list_params(params))
  end

  # NOTE: Purely for testing via the entitlement resource.
  #       Very similar to list_entitlements above (consumer resource)
  def list_ents_via_entitlements_resource(params={})
    get("/entitlements", fix_entitlement_list_params(params))
  end

  # NOTE: Purely for testing via the owner resource.
  #       expects an owner_key param
  def list_ents_via_owners_resource(owner_key, params={})
    path = "/owners/#{owner_key}/entitlements"
    return get(path, fix_entitlement_list_params(params))
  end

  def list_pool_entitlements(pool_id)
    path = "/pools/#{pool_id}/entitlements"
    return get(path)
  end

  def list_rules()
    get_text("/rules")
  end

  def upload_rules_file(rule_file)
    lines = []
    File.open(rule_file) do |f|
      f.each_line do |line|
        lines << line
      end
    end

    post_data = Base64.encode64(lines.join(""))
    post_text("/rules/", {}, post_data)
  end

  def upload_rules(encoded_rules)
    post_text("/rules/", {}, encoded_rules)
  end

  def delete_rules()
    delete("/rules")
  end

  def list_consumers(params={})
    path = "/consumers"
    return get(path, params)
  end

  def list_owner_consumers(owner_key, consumer_types=[], facts=[])
    path = "/owners/#{owner_key}/consumers"
    params = {}

    params[:type] = consumer_types if !consumer_types.empty?
    params[:fact] = facts if !facts.empty?

    return get(path, params)
  end

  def count_owner_consumers(owner_key, consumer_types=[], skus=[], subscription_ids=[], contracts=[])
    path = "/owners/#{owner_key}/consumers/count"

    params = {}
    params[:type] = consumer_types if !consumer_types.empty?
    params[:sku] = skus if !skus.empty?
    params[:subscription_id] = subscription_ids if !subscription_ids.empty?
    params[:contract] = contracts if !contracts.empty?

    return get(path, params, accept_header=:json, dont_parse=true)
  end

  def get_consumer(consumer_id=nil)
    consumer_id ||= @uuid
    get("/consumers/#{consumer_id}")
  end

  def consumer_exists(consumer_uuid=nil)
    consumer_uuid ||= @uuid
    head("/consumers/#{consumer_uuid}/exists")
  end

  def consumer_exists_bulk(data)
    post("/consumers/exists", {}, data)
  end

  def get_consumer_release(consumer_id=nil)
    consumer_id ||= @uuid
    get("/consumers/#{consumer_id}/release")
  end

  def get_compliance(consumer_id=nil, on_date=nil)
    consumer_id ||= @uuid
    query = "/consumers/#{consumer_id}/compliance"

    params = {}
    params[:on_date] = on_date if on_date

    get(query, params)
  end

  def get_compliance_list(consumer_ids=nil)
    consumer_ids ||= [@uuid]
    query = "/consumers/compliance"
    params = {}
    params[:uuid] = consumer_ids if !consumer_ids.empty?

    get(query, params)
  end

  def get_purpose_compliance(consumer_id=nil, on_date=nil)
    consumer_id ||= @uuid
    query = "/consumers/#{consumer_id}/purpose_compliance"

    params = {}
    params[:on_date] = on_date if on_date

    get(query, params)
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

  def unbind_entitlements_by_pool(consumer_id, pool_id)
    consumer_id ||= @uuid
    delete("/consumers/#{consumer_id}/entitlements/pool/#{pool_id}")
  end

  def autobind_dryrun(consumer_id=nil, service_level=nil)
    consumer_id ||= @uuid
    query = "/consumers/#{consumer_id}/entitlements/dry-run"
    params = {}
    params[:service_level] = service_level if service_level
    return get(query, params)
  end

  def list_subscriptions(owner_key, params={})
    return get("/owners/#{owner_key}/subscriptions")
  end

  def get_subscription(sub_id)
    return get("/subscriptions/#{sub_id}")
  end

  def get_pools_for_subscription(owner_key, sub_id)
    return get("/owners/#{owner_key}/pools", {:subscription => sub_id})
  end

  def get_subscription_cert(sub_id)
    return get_text("/subscriptions/#{sub_id}/cert", {}, 'text/plain')
  end

  def get_pool_cert(id)
    return get_text("/pools/#{id}/cert", {}, 'text/plain')
  end

  def get_subscription_cert_by_ent_id(ent_id)
    return get_text("/entitlements/#{ent_id}/upstream_cert", {}, 'text/plain')
  end

  def create_subscription(owner_key, product_id, quantity=1,
                          provided_products=[], contract_number='',
                          account_number='', order_number='',
                          start_date=nil, end_date=nil, params={})
      raise "Deprecated API. Please use create_pool or HostedTest resources"
  end

  def update_subscription(subscription)
      raise "Deprecated API. Please use update_pool or HostedTest resources"
  end

  def delete_subscription(subscription_id)
      raise "Deprecated API. Please use delete_pool or HostedTest resources"
  end

  def create_pool(owner_key, product_id, params={})
    quantity = params[:quantity] || 1
    provided_products = params[:provided_products] || []

    start_date = params[:start_date] || DateTime.now
    end_date = params[:end_date] || start_date + 365

    pool = {
      'startDate' => start_date,
      'endDate'   => end_date,
      'quantity'  =>  quantity,
      'productId' => product_id,
      'providedProducts' => provided_products.collect { |pid| {'productId' => pid} }
    }

    if params[:branding]
      pool['branding'] = params[:branding]
    end

    if params[:contract_number]
      pool['contractNumber'] = params[:contract_number]
    end

    if params[:account_number]
      pool['accountNumber'] = params[:account_number]
    end

    if params[:order_number]
      pool['orderNumber'] = params[:order_number]
    end

    if params[:derived_product_id]
      pool['derivedProductId'] = params[:derived_product_id]
    end

    if params[:source_subscription]
      pool['subscriptionId'] = params[:source_subscription]['id'] || "sub_id-#{rand(9)}#{rand(9)}#{rand(9)}"
      pool['subscriptionSubKey'] = params[:source_subscription]['subkey'] || 'master'
    elsif params[:subscription_id] || params[:subscription_subkey]
      pool['subscriptionId'] = params[:subscription_id] || "sub_id-#{rand(9)}#{rand(9)}#{rand(9)}"
      pool['subscriptionSubKey'] = params[:subscription_subkey] || 'master'
    elsif params[:subscriptionId] || params[:subscriptionSubKey]
      pool['subscriptionId'] = params[:subscriptionId] || "sub_id-#{rand(9)}#{rand(9)}#{rand(9)}"
      pool['subscriptionSubKey'] = params[:subscriptionSubKey] || 'master'
    end

    if params[:derived_provided_products]
      pool['derivedProvidedProducts'] = params[:derived_provided_products].collect { |pid| {'productId' => pid} }
    end

    if params[:upstream_pool_id]
      pool['upstreamPoolId'] = params[:upstream_pool_id]
    end

    return post("/owners/#{owner_key}/pools", {}, pool)
  end

  def update_pool(owner_key, pool)
    put("/owners/#{owner_key}/pools", {}, pool)
  end

  def list_activation_keys(owner_key=nil, key_name=nil)
    return get("/activation_keys") if owner_key.nil?

    path = "/owners/#{owner_key}/activation_keys"

    params = {}
    params[:name] = key_name if key_name

    return get(path, params)
  end

  def create_activation_key(owner_key, name, service_level=nil, autobind=nil, usage=nil, role=nil, addons=nil)
    key = {
      :name => name,
    }

    if service_level
      key['serviceLevel'] = service_level
    end

    if usage
      key['usage'] = usage
    end

    if role
      key['role'] = role
    end

    if addons
      key['addOns'] = addons
    end

    key['autoAttach'] = autobind
    return new_activation_key(owner_key, key)
  end

  def new_activation_key(owner_key, data)
    return post("/owners/#{owner_key}/activation_keys", {}, data)
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
    return post("/owners/#{owner_key}/environments", {}, env)
  end

  def list_environments(owner_key, env_name=nil)
    path = "/owners/#{owner_key}/environments"
    params = {}
    params[:name] = env_name if env_name

    return get(path, params)
  end

  def get_environment(env_id)
    return get("/environments/#{env_id}")
  end

  def delete_environment(env_id)
    return delete("/environments/#{env_id}")
  end

  def update_activation_key(key)
    return put("/activation_keys/#{key['id']}", {}, key)
  end

  def delete_activation_key(key_id)
    return delete("/activation_keys/#{key_id}")
  end

  def activation_key_pools(key_id)
    return get("/activation_keys/#{key_id}/pools")
  end

  def add_pool_to_key(key_id, pool_id, quantity=nil)
    if(quantity)
      return post("/activation_keys/#{key_id}/pools/#{pool_id}", {:quantity => quantity})
    else
      return post("/activation_keys/#{key_id}/pools/#{pool_id}")
    end
  end

  def remove_pool_from_key(key_id, pool_id)
    return delete("/activation_keys/#{key_id}/pools/#{pool_id}")
  end

  def add_prod_id_to_key(key_id, prod_id)
    return post("/activation_keys/#{key_id}/product/#{prod_id}")
  end

  def remove_prod_id_from_key(key_id, prod_id)
    return delete("/activation_keys/#{key_id}/product/#{prod_id}")
  end

  def add_content_overrides_to_key(key_id, overrides)
    return put("/activation_keys/#{key_id}/content_overrides", {}, overrides)
  end

  def remove_activation_key_overrides(key_id, overrides)
    return delete("/activation_keys/#{key_id}/content_overrides", {}, overrides)
  end

  def get_content_overrides_for_key(key_id)
    return get("/activation_keys/#{key_id}/content_overrides")
  end

  def list_certificates(serials = [], params = {})
    if params[:uuid]
      uuid = params[:uuid]
    else
      uuid = @uuid
    end

    path = "/consumers/#{uuid}/certificates"
    qparams = {}
    qparams[:serials] = serials.join(",") if serials.length > 0

    return get(path, qparams)
  end

  def get_content_access_body(params = {})
      uuid = if params[:uuid]
      else
        uuid = @uuid
      end
      since = params[:since] if params[:since]
      path = "/consumers/#{uuid}/accessible_content"
      response = get_client(path, Net::HTTP::Get, :get)[URI.escape(path)].get \
        ({:accept => :json, :if_modified_since => since})
      return JSON.parse(response.body)
  end

  def export_certificates(dest_dir, serials = [])
    path = "/consumers/#{@uuid}/certificates"
    params = {}
    params[:serials] = serials.join(",") if serials.length > 0

    begin
      get_file(path, params, dest_dir)
    rescue Exception => e
      puts e.response
    end
  end

  def regenerate_entitlement_certificates(lazy_regen=true, consumer_uuid=nil)
    consumer_uuid ||= @uuid
    url = "/consumers/#{consumer_uuid}/certificates"
    params = {}
    params[:lazy_regen] = false if !lazy_regen

    return put(url, params)
  end

  def regenerate_entitlement_certificates_for_product(product_id, immediate=false, lazy_regen=true)
    return async_call(immediate) do
      url = "/entitlements/product/#{product_id}"
      params = {}
      params[:lazy_regen] = false if !lazy_regen

      put(url, params)
    end
  end

  def regenerate_entitlement_certificates_for_entitlement(entitlement_id, consumer_uuid=nil)
    consumer_uuid ||= @uuid
    return put("/consumers/#{consumer_uuid}/certificates", {:entitlement => entitlement_id})
  end

  def regenerate_identity_certificate(uuid=nil)
    uuid ||= @uuid

    new_consumer = post("/consumers/#{uuid}")
    create_ssl_client(new_consumer.idCert.cert, new_consumer.idCert.key)
  end

  def get_status
    return get("/status/")
  end

  def list_certificate_serials(consumer_uuid=nil)
    consumer_uuid ||= @uuid
    return get("/consumers/#{consumer_uuid}/certificates/serials")
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

  def list_owner_events(owner_key)
    get("/owners/#{owner_key}/events")
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

  def get_job(job_id, result_data=false)
    url = "/jobs/#{job_id}"
    params = {}
    params[:result_data] = true if result_data

    return get(url, params)
  end

  def cancel_job(job_id)
    delete "/jobs/#{job_id}"
  end

  def cleanup_jobs(params = {})
    # TODO: validation/sanitiation as necessary

    # query = {
    #   :id =>
    #   :key =>
    #   :state =>
    #   :owner =>
    #   :origin =>
    #   :executor =>
    #   :after =>
    #   :before =>
    #   :force =>
    # }

    return delete('/jobs', params, nil, true)
  end

  def import(owner_key, filename, params = {}, headers = {})
    do_import("/owners/#{owner_key}/imports", filename, params, headers)
  end

  def import_async(owner_key, filename, params = {}, headers = {})
    do_import("/owners/#{owner_key}/imports/async", filename, params, headers)
  end

  def undo_import(owner_key)
    path = "/owners/#{owner_key}/imports"
    delete(path)
  end

  def get_crl
    crl = get_text('/crl')
    puts ("Received CRL:\n#{crl}") if @verbose

    OpenSSL::X509::CRL.new(crl)
  end

  def get_guests(consumer_id)
    get("/consumers/#{consumer_id}/guests")
  end

  def create_distributor_version(name, display_name, capabilities=[])
    version =  {
      'name' => name,
      'displayName' => display_name,
      'capabilities' => capabilities.collect { |name| {'name' => name} }
    }

    post('/distributor_versions', {}, version)
  end

  def update_distributor_version(id, name, display_name, capabilities=[])
    version =  {
      'name' => name,
      'displayName' => display_name,
      'capabilities' => capabilities.collect { |name| {'name' => name} }
    }

    put("/distributor_versions/#{id}", {}, version)
  end

  def delete_distributor_version(id)
    delete("/distributor_versions/#{id}")
  end

  def get_distributor_versions(name_search = nil, capability = nil)
    path = "/distributor_versions"

    params = {}
    params[:name_search] = name_search if name_search
    params[:capability] = capability if capability

    return get(path, params)
  end

  def create_cdn(label, name, url, cert=nil)
    cdn =  {
      'label' => label,
      'name' => name,
      'url' => url,
      'certificate' => cert
    }

    return post('/cdn', {}, cdn)
  end

  def update_cdn(label, name, url, cert=nil)
    cdn =  {
      'name' => name,
      'url' => url,
      'certificate' => cert
    }

    return put("/cdn/#{label}", {}, cdn)
  end

  def delete_cdn(label)
    delete("/cdn/#{label}")
  end

  def get_cdn_from_pool(id)
    get("/pools/#{id}/cdn")
  end

  def get_cdns()
    get("/cdn")
  end

  def add_content_overrides(uuid, overrides=[])
    put("consumers/#{uuid}/content_overrides", {}, overrides)
  end

  def delete_content_overrides(uuid, overrides=[])
    delete("consumers/#{uuid}/content_overrides", {}, overrides)
  end

  def get_content_overrides(uuid)
    get("consumers/#{uuid}/content_overrides")
  end

  def get(uri, params={}, accept_header=:json, dont_parse=false)
    # escape and build uri
    euri = URI.escape(uri)
    if !params.empty?
      euri << '?'
      euri << URI.encode_www_form(params)
    end

    # execute
    puts ("GET #{euri}") if @verbose
    response = get_client(uri, Net::HTTP::Get, :get)[euri].get :accept => accept_header

    if dont_parse
      return response
    end

    return JSON.parse(response.body)
  end

  # Assumes a zip archive currently. Returns filename (random#.zip) of the
  # temp file created.
  def get_file(uri, params={}, dest_dir)
    # escape and build uri
    euri = URI.escape(uri)
    if !params.empty?
      euri << '?'
      euri << URI.encode_www_form(params)
    end

    # execute
    puts ("GET #{euri}") if @verbose
    response = get_client(euri, Net::HTTP::Get, :get)[euri].get :accept => "application/zip"
    filename = response.headers[:content_disposition] == nil ? "tmp_#{rand}.zip" : response.headers[:content_disposition].split("filename=")[1]
    filename = File.join(dest_dir, filename)
    File.open(filename, 'w') { |f| f.write(response.body) }
    filename
  end

  def get_text(uri, params={}, accept_header=nil)
    # escape and build uri
    euri = URI.escape(uri)
    if !params.empty?
      euri << '?'
      euri << URI.encode_www_form(params)
    end

    # execute
    puts ("GET #{euri}") if @verbose
    if accept_header.nil?
      response = get_client(euri, Net::HTTP::Get, :get)[euri].get :content_type => 'text/plain'
    else
      response = get_client(euri, Net::HTTP::Get, :get)[euri].get :content_type => 'text/plain', :accept => accept_header
    end

    return (response.body)
  end

  def head(uri, params={})
    # escape and build uri
    euri = URI.escape(uri)
    if !params.empty?
      euri << '?'
      euri << URI.encode_www_form(params)
    end

    # execute
    puts ("HEAD #{euri}") if @verbose
    response = get_client(uri, Net::HTTP::Head, :head)[euri].head

    return response
  end

  def post(uri, params={}, data=nil)
    # escape and build uri
    euri = URI.escape(uri)
    if !params.empty?
      euri << '?'
      euri << URI.encode_www_form(params)
    end

    # encode data
    data = data.to_json if not data.nil?

    # execute
    puts ("POST #{euri} #{data}") if @verbose
    if params[:user_agent].nil?
      response = get_client(uri, Net::HTTP::Post, :post)[euri].post data, :content_type => :json, :accept => :json
    else
      response = get_client(uri, Net::HTTP::Post, :post)[euri].post data, :content_type => :json, :accept => :json, "user-agent" => params[:user_agent]
    end

    return JSON.parse(response.body) unless response.body.empty?
  end

  def post_file(uri, params={}, file=nil, headers={})
    # escape and build uri
    euri = URI.escape(uri)
    if !params.empty?
      euri << '?'
      euri << URI.encode_www_form(params)
    end

    # execute
    puts ("POST #{euri} #{file}") if @verbose
    response = get_client(uri, Net::HTTP::Post, :post)[euri].post({:import => file}, {'x-correlation-id' => headers[:correlation_id]})

    return JSON.parse(response.body) unless response.body.empty?
  end

  def post_text(uri, params={}, data=nil, accept='text/plain')
    # escape and build uri
    euri = URI.escape(uri)
    if !params.empty?
      euri << '?'
      euri << URI.encode_www_form(params)
    end

    # execute
    puts ("POST #{euri} #{data} #{accept}") if @verbose
    response = get_client(euri, Net::HTTP::Post, :post)[euri].post(data, :content_type => 'text/plain', :accept => accept)

    return response.body
  end

  def put(uri, params={}, data=nil)
    # escape and build uri
    euri = URI.escape(uri)
    if !params.empty?
      euri << '?'
      euri << URI.encode_www_form(params)
    end

    # encode data
    data = data.to_json if not data.nil?

    # execute
    puts ("PUT #{euri} #{data}") if @verbose
    response = get_client(uri, Net::HTTP::Put, :put)[euri].put(data, :content_type => :json, :accept => :json)

    return JSON.parse(response.body) unless response.body.empty?
  end

  def put_empty(uri, params={}, data=nil, accept='application/json')
    # escape and build uri
    euri = URI.escape(uri)
    if !params.empty?
      euri << '?'
      euri << URI.encode_www_form(params)
    end

    # execute
    puts ("PUT #{euri} #{data} #{accept}") if @verbose
    response = get_client(euri, Net::HTTP::Put, :put)[euri].put(data, :accept => accept, :content_length => 0)

    return JSON.parse(response.body) unless response.body.empty?
  end

  def delete(uri, params={}, data=nil, dont_parse=false)
    # escape and build uri
    euri = URI.escape(uri)
    if !params.empty?
      euri << '?'
      euri << URI.encode_www_form(params)
    end

    # execute
    puts ("DELETE #{euri}") if @verbose
    client = get_client(euri, Net::HTTP::Delete, :delete)
    client.options[:payload] = data.to_json if not data.nil?
    response = client[euri].delete(:content_type => :json, :accepts => :json)

    if dont_parse
      return response
    end

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
                                       :headers => {:accept_language => @lang},
                                       :verify_ssl => @verify_ssl)
  end

  def create_ssl_client(cert, key)
    @identity_certificate = OpenSSL::X509::Certificate.new(cert)
    @identity_key = OpenSSL::PKey::RSA.new(key)
    @uuid = @identity_certificate.subject.to_s.scan(/\/CN=([^\/=]+)/)[0][0]

    @client = RestClient::Resource.new(@base_url,
                                       :ssl_client_cert => @identity_certificate,
                                       :ssl_client_key => @identity_key,
                                       :headers => {:accept_language => @lang},
                                       :verify_ssl => @verify_ssl)
  end

  def create_trusted_consumer_client(uuid)
    @uuid = uuid
    @client = RestClient::Resource.new(@base_url,
                                       :headers => {"cp-consumer" => uuid, :accept_language => @lang},
                                       :verify_ssl => @verify_ssl)
  end

  def create_trusted_user_client(username)
    @username = username
    @client = RestClient::Resource.new(@base_url,
                                       :headers => {"cp-user" => username, :accept_language => @lang},
                                       :verify_ssl => @verify_ssl)
  end

  private

  def do_import(path, filename, params = {}, headers = {})
    return post_file(path, params, File.new(filename), headers)
  end

  def do_consumer_export(path, dest_dir, params)
    begin
      if dest_dir.nil?
        # support async call
        get(path, params)
      else
        get_file(path, params, dest_dir)
      end
    rescue Exception => e
      puts e.response
    end
  end

end

class OauthCandlepinApi < Candlepin

  def initialize(username, password, oauth_consumer_key, oauth_consumer_secret, params={})
    @oauth_consumer_key = oauth_consumer_key
    @oauth_consumer_secret = oauth_consumer_secret

    host = params[:host] || 'localhost'
    port = params[:port] || 8443
    lang = params[:lang] || nil
    verify_ssl = params[:verify_ssl] || nil
    super(username, password, nil, nil, host, port, lang, nil, false, verify_ssl)
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
      :access_token_path => ""
    }
    #params[:ca_file] = self.ca_cert_file unless self.ca_cert_file.nil?

    consumer = OAuth::Consumer.new(@oauth_consumer_key, @oauth_consumer_secret, params)
    request = http_type.new(final_url)
    consumer.sign!(request)
    headers = {
      'Authorization' => request['Authorization'],
      'accept_language' => @lang,
      'cp-user' => 'admin'
    }

    # Creating a new client for every request:
    client = RestClient::Resource.new(@base_url, :headers => headers)
    return client
  end
end
