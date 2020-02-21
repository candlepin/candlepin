module HostedTest

  @@hosted_mode = nil
  @@hostedtest_alive = nil

  # FIXME: This is broken. There's no distiction between up and downstream data sources, which creates
  # a lot of problems when it comes to product and content mapping. Hosted test resources should be
  # entirely upstream, and not rely on, nor require, anything downstream for proper functionality.

  def is_hostedtest_alive?
    if @@hostedtest_alive.nil?
      begin
        @@hostedtest_alive = @cp.get('/hostedtest/alive', {}, 'text/plain', true)
      rescue RestClient::ResourceNotFound
        @@hostedttest_alive = false
      end
    end
    return @@hostedtest_alive
  end

  def create_hostedtest_subscription(owner_key, product_id, quantity=1, params={})
    provided_products = params[:provided_products] || []
    start_date = params[:start_date] || DateTime.now
    end_date = params[:end_date] || start_date + 365

    subscription = {
      'startDate' => start_date,
      'endDate'   => end_date,
      'product' =>  { 'id' => product_id },
      'quantity'  =>  quantity,
      'owner' =>  { 'key' => owner_key }
    }

    if params[:upstream_pool_id]
      subscription['upstreamPoolId'] = params[:upstream_pool_id]
    end

    if params[:contract_number]
      subscription['contractNumber'] = params[:contract_number]
    end

    if params[:account_number]
      subscription['accountNumber'] = params[:account_number]
    end

    if params[:order_number]
      subscription['orderNumber'] = params[:order_number]
    end

    if params[:branding]
      subscription['branding'] = params[:branding]
    end

    if params[:derived_product_id]
      subscription['derivedProduct'] = { 'id' => params[:derived_product_id] }
    end

    if params[:provided_products]
      subscription['product']['providedProducts'] = params[:provided_products].collect { |pid| {'id' => pid} }
    end

    if params[:derived_provided_products]
      subscription['derivedProduct']['providedProducts'] = params[:derived_provided_products].collect { |pid| {'id' => pid} }
    end
    return @cp.post("/hostedtest/subscriptions", {}, subscription)
  end

  def update_hostedtest_subscription(subscription)
    id = subscription.id
    return @cp.put("/hostedtest/subscriptions/#{id}", {}, subscription)
  end

  def get_all_hostedtest_subscriptions()
    return @cp.get('/hostedtest/subscriptions/')
  end

  def get_hostedtest_subscription(id)
    return @cp.get("/hostedtest/subscriptions/#{id}")
  end

  def delete_hostedtest_subscription(id)
    return @cp.delete("/hostedtest/subscriptions/#{id}", {}, nil, true)
  end

  def clear_upstream_data()
    @cp.delete('/hostedtest', {}, nil, true)
  end

  def is_hosted?
    if @@hosted_mode.nil?
      @@hosted_mode = ! @cp.get_status()['standalone']
    end
    return @@hosted_mode
  end

  def is_standalone?
    if @@hosted_mode.nil?
      @@hosted_mode = ! @cp.get_status()['standalone']
    end
    return !@@hosted_mode
  end

  def ensure_hostedtest_resource()
    if is_hosted? && !is_hostedtest_alive?
      raise "Could not find hostedtest rest API. Please run \'deploy -ha\' or add the following to candlepin.conf:\n" \
          " module.config.hosted.configuration.module=org.candlepin.hostedtest.AdapterOverrideModule"
    end
  end

  def add_batch_content_to_product(owner_key, product_id, content_ids, enabled=true)
    if is_hosted?
      data = {}
      content_ids.each do |id|
        data[id] = enabled
      end
      @cp.post("/hostedtest/products/#{product_id}/batch_content", {}, data)
    else
      @cp.add_batch_content_to_product(owner_key, product_id, content_ids, true)
    end
  end

  def add_content_to_product(owner_key, product_id, content_id, enabled=true)
    if is_hosted?
      @cp.post("/hostedtest/products/#{product_id}/content/#{content_id}", {:enabled => enabled})
    else
      @cp.add_content_to_product(owner_key, product_id, content_id, true)
    end
  end

  def update_product(owner_key, product_id, params={})
    if is_hosted?
      # FIXME: This is broken

      product = {
        :id => product_id
      }

      product[:name] = params[:name] if params[:name]
      product[:multiplier] = params[:multiplier] if params[:multiplier]
      product[:attributes] = params[:attributes] if params[:attributes]
      product[:dependentProductIds] = params[:dependentProductIds] if params[:dependentProductIds]
      product[:relies_on] = params[:relies_on] if params[:relies_on]
      product[:providedProducts] = params[:providedProducts] if params[:providedProducts]

      @cp.put("/hostedtest/products/#{product_id}", {}, product)
    else
      @cp.update_product(owner_key, product_id, params)
    end
  end

  # Lets users be agnostic of what mode we are in, standalone or hosted.
  # Always returns the main pool that was created ( unless running in hosted mode and refresh is skipped )
  # not to be used to create custom pool
  # DEPRECATED, Create your pools directly using create_pool from candlepin_api.rb
  def create_pool_and_subscription(owner_key, product_id, quantity=1,
                          provided_products=[], contract_number='',
                          account_number='', order_number='',
                          start_date=nil, end_date=nil, skip_refresh=false, params={})

    params[:start_date] = start_date
    params[:end_date] = end_date
    params[:contract_number] = contract_number
    params[:account_number] = account_number
    params[:order_number] = order_number
    params[:quantity] = quantity
    params[:provided_products] = provided_products

    pool = nil
    if is_hosted?
      ensure_hostedtest_resource
      sub = create_hostedtest_subscription(owner_key, product_id, quantity, params)
      if not skip_refresh
        active_on = Date.strptime(sub['startDate'], "%Y-%m-%d")+1
        @cp.refresh_pools(owner_key)
        pool = find_main_pool(owner_key, sub['id'], activeon=active_on, true)
      end
    else
      params[:subscription_id] = random_str('source_sub')
      params[:upstream_pool_id] = random_str('upstream')
      pool = @cp.create_pool(owner_key, product_id, params)
    end
    return pool
  end

  # Lets users be agnostic of what mode we are in, standalone or hosted.
  # if we are running in hosted mode, delete the upstream subscription and refresh pools.
  # else, simply delete the pool
  def delete_pool_and_subscription(pool)
    if is_hosted?
      ensure_hostedtest_resource
      delete_hostedtest_subscription(pool.subscriptionId)
      @cp.refresh_pools(pool['owner']['key'])
    else
      @cp.delete_pool(pool.id)
    end
  end

  # Lets users be agnostic of what mode we are in, standalone or hosted.
  # This method is used when we need to update the upstream subscription's details.
  # first we fetch the upstrean pool ( if standalone mode ) or subscription ( if hosted mode )
  # using get_pool_or_subscription(pool) and then use update_pool_or_subscription
  # to update the upstream entity.
  #
  # input is always a pool, but the out may be either a subscription or a pool
  def get_pool_or_subscription(pool)
    if is_hosted?
      ensure_hostedtest_resource
      return get_hostedtest_subscription(pool.subscriptionId)
    else
      return pool
    end
  end

  # Lets users be agnostic of what mode we are in, standalone or hosted.
  # This method is used when we need to update the upstream subscription's details.
  # first we fetch the upstrean pool ( if standalone mode ) or subscription ( if hosted mode )
  # using get_pool_or_subscription(pool) and then use update_pool_or_subscription
  # to update the upstream entity.
  #
  # input may be either a subscription or a pool, and there is no output
  def update_pool_or_subscription(subOrPool, refresh=true)
    if is_hosted?
      ensure_hostedtest_resource
      update_hostedtest_subscription(subOrPool)
      active_on = case subOrPool.startDate
        when String then Date.strptime(subOrPool.startDate, "%Y-%m-%d")+1
        when Date then subOrPool.startDate+1
        else raise "invalid date format"
      end
      @cp.refresh_pools(subOrPool['owner']['key'], true) if refresh
      sleep 1
    else
      @cp.update_pool(subOrPool['owner']['key'], subOrPool)
    end
  end

  # Lets users be agnostic of what mode we are in, standalone or hosted.
  # This method is used when we need to update the dependent entities of a
  # upstream subscription or pool. simply fetching and updating the subscription forces
  # a re-resolve of products, owners, etc.
  #
  # input is alwasy a pool, and there is no output
  def refresh_upstream_subscription(pool)
    if is_hosted?
      ensure_hostedtest_resource
      sub = get_hostedtest_subscription(pool.subscriptionId)
      update_hostedtest_subscription(sub)
      @cp.refresh_pools(pool['owner']['key'])
    end
  end

  # List all the pools for the given owner, and find one that matches
  # a specific subscription ID. (we often want to verify what pool was used,
  # but the pools are created indirectly after a refresh so it's hard to
  # locate a specific reference without this)
  def find_main_pool(owner_key, sub_id, activeon=nil, return_normal)
    pools = @cp.list_owner_pools(owner_key, {:activeon => activeon})
    pools.each do |pool|
      if pool['subscriptionId'] == sub_id && (!return_normal || pool['type'] == 'NORMAL')
        return pool
      end
    end
    return nil
  end

  def random_str(prefix=nil, numeric_only=false)
    if prefix
      prefix = "#{prefix}-"
    end

    if numeric_only
      suffix = rand(9999999)
    else
      # This is actually a bit faster than using SecureRandom.  Go figure.
      o = [('a'..'z'), ('A'..'Z'), ('0'..'9')].map { |i| i.to_a }.flatten
      suffix = (0..7).map { o[rand(o.length)] }.join
    end
    "#{prefix}#{suffix}"
  end

  def cleanup_subscriptions
    if is_hosted?
      ensure_hostedtest_resource
      delete_all_hostedtest_subscriptions
    end
  end


  # TODO: Delete everything above this


  def create_upstream_subscription(subscription_id, owner_key, params = {})
    start_date = params.delete(:start_date) || Date.today
    end_date = params.delete(:end_date) || start_date + 365

    # Define subscription with defaults & specified params
    subscription = {
      'startDate' => start_date,
      'endDate'   => end_date,
      'quantity'  => 1
    }

    # Do not copy these with the rest of the merged keys
    filter = ['id', 'owner', 'ownerId']

    params.each do |key, value|
      # Convert the key to snake case so we can support whatever is thrown at us
      key = key.to_s.gsub(/_(\w)/){$1.upcase}

      if !filter.include?(key)
        subscription[key] = value
      end
    end

    # Forcefully set critical identifiers
    subscription['id'] = subscription_id
    subscription['owner'] = { :key => owner_key }

    if !subscription['product']
      raise "Attempting to create a subscription without a product"
    end

    return @cp.post('hostedtest/subscriptions', {}, subscription)
  end

  def list_upstream_subscriptions()
    return @cp.get('/hostedtest/subscriptions')
  end

  def get_upstream_subscription(subscription_id)
    return @cp.get("/hostedtest/subscriptions/#{subscription_id}")
  end

  def update_upstream_subscription(subscription_id, params = {})
    subscription = {}

    # Do not copy these with the rest of the merged keys
    filter = ['id', 'ownerId']

    params.each do |key, value|
      # Convert the key to snake case so we can support whatever is thrown at us
      key = key.to_s.gsub(/_(\w)/){$1.upcase}

      if !filter.include?(key)
        subscription[key] = value
      end
    end

    # Forcefully set critical identifiers
    subscription['id'] = subscription_id

    return @cp.put("/hostedtest/subscriptions/#{subscription_id}", {}, subscription)
  end

  def delete_upstream_subscription(subscription_id)
    return @cp.delete("/hostedtest/subscriptions/#{subscription_id}")
  end

  def create_upstream_product(product_id = nil, params = {})
    # Generate an ID if one was not provided
    if product_id.nil?
      product_id = random_str('product')
    end

    # Create a product with some defaults for required fields
    product = {
      'multiplier' => 1
    }

    # Do not copy these with the rest of the merged keys
    filter = ['id']

    params.each do |key, value|
      # Convert the key to snake case so we can support whatever is thrown at us
      key = key.to_s.gsub(/_(\w)/){$1.upcase}

      if !filter.include?(key)
        product[key] = value
      end
    end

    # Forcefully set identifier and name (if absent)
    product['id'] = product_id
    product['name'] = product_id if !product['name']

    return @cp.post('hostedtest/products', {}, product)
  end

  def list_upstream_products()
    return @cp.get('/hostedtest/products')
  end

  def get_upstream_product(product_id)
    return @cp.get("/hostedtest/products/#{product_id}")
  end

  def update_upstream_product(product_id, params = {})
    product = {}

    # Do not copy these with the rest of the merged keys
    filter = ['id']

    params.each do |key, value|
      # Convert the key to snake case so we can support whatever is thrown at us
      key = key.to_s.gsub(/_(\w)/){$1.upcase}

      if !filter.include?(key)
        product[key] = value
      end
    end

    # Forcefully set identifier
    product['id'] = product_id

    return @cp.put("/hostedtest/products/#{product_id}", {}, product)
  end

  def delete_upstream_product(product_id)
    return @cp.delete("/hostedtest/products/#{product_id}")
  end

  def create_upstream_content(content_id = nil, params = {})
    # Generate an ID if one was not provided
    if content_id.nil?
      content_id = random_str('content')
    end

    # Create a content with some defaults for required fields
    content = {
      'label' => 'label',
      'type' => 'yum',
      'vendor' => 'vendor'
    }

    # Do not copy these with the rest of the merged keys
    filter = ['id']

    params.each do |key, value|
      # Convert the key to snake case so we can support whatever is thrown at us
      key = key.to_s.gsub(/_(\w)/){$1.upcase}

      if !filter.include?(key)
        content[key] = value
      end
    end

    # Forcefully set identifier and name (if absent)
    content['id'] = content_id
    content['name'] = content_id if !content['name']

    return @cp.post('hostedtest/content', {}, content)
  end

  def list_upstream_contents()
    return @cp.get('/hostedtest/content')
  end

  def get_upstream_content(content_id)
    return @cp.get("/hostedtest/content/#{content_id}")
  end

  def update_upstream_content(content_id, params = {})
    content = {}

    # Do not copy these with the rest of the merged keys
    filter = ['id']

    params.each do |key, value|
      # Convert the key to snake case so we can support whatever is thrown at us
      key = key.to_s.gsub(/_(\w)/){$1.upcase}

      if !filter.include?(key)
        content[key] = value
      end
    end

    # Forcefully set identifier
    content['id'] = content_id

    return @cp.put("/hostedtest/content/#{content_id}", {}, content)
  end

  def delete_upstream_content(content_id)
    return @cp.delete("/hostedtest/content/#{content_id}")
  end

  def add_batch_content_to_product_upstream(product_id, content_ids, enabled=true)
    data = {}
    content_ids.each do |id|
      data[id] = enabled
    end
    @cp.post("/hostedtest/products/#{product_id}/content", {}, data)
  end

  def add_content_to_product_upstream(product_id, content_id, enabled = true)
    @cp.post("/hostedtest/products/#{product_id}/content/#{content_id}", { :enabled => enabled })
  end

  def remove_batch_content_from_product_upstream(product_id, content_ids)
    @cp.delete("/hostedtest/products/#{product_id}/content", {}, content_ids)
  end

  def remove_content_from_product_upstream(product_id, content_id)
    @cp.delete("/hostedtest/products/#{product_id}/content/#{content_id}")
  end



end
