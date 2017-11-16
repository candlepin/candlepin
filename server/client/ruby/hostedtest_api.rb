module HostedTest

  @@hosted_mode = nil
  @@hostedtest_alive = nil

  def is_hostedtest_alive?
    if @@hostedtest_alive.nil?
      begin
        @@hostedtest_alive = @cp.get('/hostedtest/subscriptions/is_alive', {}, 'json', true)
      rescue RestClient::ResourceNotFound
        @@hostedttest_alive = false
      end
    end
    return @@hostedtest_alive
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
      raise "Could not find hostedtest rest API. Please run \'deploy -Ha\' or add the following to candlepin.conf:\n" \
          " module.config.hosted.configuration.module=org.candlepin.hostedtest.AdapterOverrideModule"
    end
  end

  def clear_upstream_data()
    @cp.delete('/hostedtest')
  end

  def create_upstream_subscription(subscription_id, owner_key, product_id, params = {})
    start_date = params.delete(:start_date) || Date.today
    end_date = params.delete(:end_date) || start_date + 365

    # Define subscription with defaults & specified params
    subscription = {
      :startDate => start_date,
      :endDate   => end_date,
      :product =>  { :id => product_id },
      :owner =>  { :key => owner_key },
      :quantity => 1
    }

    # Merge, but convert some snake-case keys to camel case
    keys = [:account_number, :contract_number, :order_number, :upstream_pool_id,
      :provided_products, :derived_product, :derived_provided_products,
      'account_number', 'contract_number', 'order_number', 'upstream_pool_id',
      'provided_products', 'derived_product', 'derived_provided_products']

    params.each do |key, value|
      if keys.include?(key)
        key = key.to_s.gsub!(/_(\w)/){$1.upcase}
      end

      subscription[key] = value
    end

    # Forcefully set identifier
    subscription[:id] = subscription_id

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

    # Merge, but convert some snake-case keys to camel case
    keys = [:account_number, :contract_number, :order_number, :upstream_pool_id, :start_date, :end_date,
      :provided_products, :derived_product, :derived_provided_products,
      'account_number', 'contract_number', 'order_number', 'upstream_pool_id', 'start_date', 'end_date',
      'provided_products', 'derived_product', 'derived_provided_products']

    params.each do |key, value|
      if keys.include?(key)
        key = key.to_s.gsub!(/_(\w)/){$1.upcase}
      end

      subscription[key] = value
    end

    # Forcefully set identifier
    subscription[:id] = subscription_id

    return @cp.put("/hostedtest/subscriptions/#{subscription_id}", {}, subscription)
  end

  def delete_upstream_subscription(subscription_id)
    return @cp.delete("/hostedtest/subscriptions/#{subscription_id}")
  end

  def create_upstream_product(product_id, params = {})
    # Create a product with some defaults for required fields
    product = {
      :multiplier => 1
    }

    # Merge provided params in
    product.merge!(params)

    # Forcefully set identifier and name (if absent)
    product[:id] = product_id
    product[:name] = product_id if !product[:name]

    return @cp.post('hostedtest/products', {}, product)
  end

  def list_upstream_products()
    return @cp.get('/hostedtest/products')
  end

  def get_upstream_product(product_id)
    return @cp.get("/hostedtest/products/#{product_id}")
  end

  def update_upstream_product(product_id, params = {})
    product = {}.merge(params)

    # Forcefully set identifier
    product[:id] = product_id

    return @cp.put("/hostedtest/products/#{product_id}", {}, product)
  end

  def delete_upstream_product(product_id)
    return @cp.delete("/hostedtest/products/#{product_id}")
  end

  def create_upstream_content(content_id, params = {})
    # Create a content with some defaults for required fields
    content = {
      :label => 'label',
      :type => 'yum',
      :vendor => 'vendor'
    }

    # Merge, but convert some snake-case keys to camel case
    keys = [:content_url, :gpg_url, :modified_product_ids, :metadata_expire, :required_tags,
      'content_url', 'gpg_url', 'modified_product_ids', 'metadata_expire', 'required_tags']

    params.each do |key, value|
      if keys.include?(key)
        key = key.to_s.gsub!(/_(\w)/){$1.upcase}
      end

      content[key] = value
    end

    # Forcefully assign the ID and name (if absent)
    content[:id] = content_id
    content[:name] = content_id if !content[:name]

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

    # Merge, but convert some snake-case keys to camel case
    keys = [:content_url, :gpg_url, :modified_product_ids, :metadata_expire, :required_tags,
      'content_url', 'gpg_url', 'modified_product_ids', 'metadata_expire', 'required_tags']

    params.each do |key, value|
      if keys.include?(key)
        key = key.to_s.gsub!(/_(\w)/){$1.upcase}
      end

      content[key] = value
    end

    # Forcefully set identifier
    content[:id] = content_id

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

  def cleanup_subscriptions
    if is_hosted?
      ensure_hostedtest_resource
      clear_upstream_data
    end
  end

end
