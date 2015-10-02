require 'openssl'
require 'rest_client'
require 'json'
require 'date'


class Subservice

  # Initialize a connection to Subservice.
  # TODO Fix and clean this once auth is implemented in the service
  def initialize(cacert, host='localhost', port=8443, verbose=true, use_ssl=true)

    if cacert.nil?
      raise "Only cert auth is supported right now"
    end

    @verbose = verbose
    base_url = "https://#{host}:#{port}/subservice"
    @client = RestClient::Resource.new(base_url,
                            :ssl_ca_file => cacert,
                            :ssl_ciphers => 'AESGCM:!aNULL')
  end

  def get_client
    return @client
  end

  def is_alive
    begin
      return get_status()['alive']
    rescue Exception => e
      puts "Error calling subservice: #{e.message}"
      return false
    end
  end

  def get_status
    return get("/status")
  end

  def get_all_subscriptions
    return get("/subscriptions")
  end

  def get_subscription(uuid)
    return get("/subscriptions/#{uuid}")
  end

  def create_subscription(owner_key, product_id, quantity=1,
                          provided_products=[], contract_number='',
                          account_number='', order_number='',
                          start_date=nil, end_date=nil, params={})
    start_date ||= Date.today
    end_date ||= start_date + 365

    subscription = {
      'owner' => owner_key,
      'startDate' => start_date,
      'endDate'   => end_date,
      'quantity'  =>  quantity,
      'accountNumber' => account_number,
      'orderNumber' => order_number,
      'product' => { 'id' => product_id },
      'providedProducts' => provided_products.collect { |pid| {'id' => pid} },
      'contractNumber' => contract_number
    }

    if params[:branding]
      subscription['branding'] = params[:branding]
    end

    if params['derived_product_id']
      subscription['derivedProduct'] = { 'id' => params['derived_product_id'] }
    end

    if params['derived_provided_products']
      subscription['derivedProvidedProducts'] = params['derived_provided_products'].collect { |pid| {'id' => pid} }
    end

    return post("/subscriptions/", subscription)
  end

  def update_subscription(uuid,subscription)
    return put("/subscriptions/#{uuid}",subscription)
  end

  def delete_subscription(uuid)
    return delete("/subscriptions/#{uuid}")
  end

  def get_all_products
    return get("/products")
  end

  def get_product(uuid)
    return get("/products/#{uuid}")
  end

  def create_product(product)
    return post("/products",product)
  end

  def update_product(uuid,product)
    return put("/products/#{uuid}",product)
  end

  def delete_product(uuid)
    return delete("/products/#{uuid}")
  end

  def get(uri, accept_header = :json)
    puts ("GET #{uri}") if @verbose
    response = get_client()[URI.escape(uri)].get \
      :accept => accept_header

    return JSON.parse(response.body)
  end

  def post(uri, data=nil)
    puts ("POST #{uri} #{data}") if @verbose
    data = data.to_json if not data.nil?
    response = get_client()[URI.escape(uri)].post(
      data, :content_type => :json, :accept => :json)

    return JSON.parse(response.body) unless response.body.empty?
  end

  def put(uri, data=nil)
    puts ("PUT #{uri} #{data}") if @verbose
    data = data.to_json if not data.nil?
    response = get_client()[uri].put(
      data, :content_type => :json, :accept => :json)

    return JSON.parse(response.body) unless response.body.empty?
  end

  def delete(uri, data=nil)
    puts ("DELETE #{uri}") if @verbose
    client = get_client()
    client.options[:payload] = data.to_json if not data.nil?
    response = client[URI.escape(uri)].delete(:content_type => :json, :accepts => :json)

    return response
  end

end

