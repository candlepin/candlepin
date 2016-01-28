module HostedTest

  @@hosted_mode = nil
  @@hostedtest_alive = nil

  def is_hostedtest_alive?
    if @@hostedtest_alive.nil?
      begin
        @@hostedtest_alive = @cp.get('/hostedtest/subscriptions/is_alive','json', true)
      rescue RestClient::ResourceNotFound
        @@hostedttest_alive = false
      end
    end
    return @@hostedtest_alive
  end

  def create_hostedtest_subscription(owner_key, product_id, quantity=1,
                          params={})

    provided_products = params[:provided_products] || []
    start_date = params[:start_date] || Date.today
    end_date = params[:end_date] || start_date + 365

    subscription = {
      'startDate' => start_date,
      'endDate'   => end_date,
      'quantity'  =>  quantity,
      'product' =>  { 'id' => product_id },
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
      subscription['providedProducts'] = params[:provided_products].collect { |pid| {'id' => pid} }
    end

    if params[:derived_provided_products]
      subscription['derivedProvidedProducts'] = params[:derived_provided_products].collect { |pid| {'id' => pid} }
    end
    return @cp.post("/hostedtest/subscriptions", subscription)
  end

  def update_hostedtest_subscription(subscription)
    return @cp.put("/hostedtest/subscriptions", subscription)
  end

  def get_all_hostedtest_subscriptions()
    return @cp.get('/hostedtest/subscriptions/')
  end

  def get_hostedtest_subscription(id)
    return @cp.get("/hostedtest/subscriptions/#{id}")
  end

  def delete_hostedtest_subscription(id)
    return @cp.delete("/hostedtest/subscriptions/#{id}", nil, true)
  end

  def delete_all_hostedtest_subscriptions()
    @cp.delete('/hostedtest/subscriptions/', nil, true)
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

  # Lets users be agnostic of what mode we are in, standalone or hosted.
  # Always returns the main pool that was created ( unless running in hosted mode and refresh is skipped )
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
      params[:source_subscription] = { 'id' => random_str('source_sub_') }
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
      @cp.refresh_pools(pool['owner']['key'], true)
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
  def update_pool_or_subscription(subOrPool)
    if is_hosted?
      ensure_hostedtest_resource
      update_hostedtest_subscription(subOrPool)
      active_on = case subOrPool.startDate
        when String then Date.strptime(subOrPool.startDate, "%Y-%m-%d")+1
        when Date then subOrPool.startDate+1
        else raise "invalid date format"
      end
      @cp.refresh_pools(subOrPool['owner']['key'], true)
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

end
