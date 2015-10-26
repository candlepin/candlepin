require 'candlepin_api'

module HostedTest

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

end
