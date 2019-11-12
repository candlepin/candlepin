require 'spec_helper'
require 'candlepin_scenarios'
require 'time'

describe 'Autobind Disabled On Owner' do

  include CandlepinMethods

  before(:each) do
    owner = create_owner random_string('test_owner1')
    owner['autobindDisabled'] = true
    @cp.update_owner(owner['key'], owner)
    @owner = @cp.get_owner(owner['key'])
    @owner.should_not be_nil
    @activation_key = @cp.create_activation_key(@owner['key'], random_string('test_token'), nil, true)
    @user_cp = user_client(@owner, random_string("test-user"))
    @consumer = @user_cp.register("foofy", :system, nil, {'cpu.cpu_socket(s)' => '8'}, nil, @owner['key'], [], [])
    @consumer_cp = Candlepin.new(nil, nil, @consumer.idCert.cert, @consumer.idCert['key'])
  end

  it 'autobind fails when autobind disabled on owner' do
    exception_thrown = false
    begin
      @consumer_cp.consume_product()
    rescue RestClient::BadRequest => e
      exception_thrown = true
      ex_message = "Ignoring request to auto-attach. It is disabled for org \"#{@owner['key']}\" because of the content access mode setting."
      data = JSON.parse(e.response)
      data['displayMessage'].should == ex_message
    end
    exception_thrown.should be true
  end

  it 'autobind fails when hypervisor autobind disabled on owner' do
    exception_thrown = false
    @owner['autobindDisabled'] = false
    @owner['autobindHypervisorDisabled'] = true
    @cp.update_owner(@owner['key'], @owner)
    @owner = @cp.get_owner(@owner['key'])
    puts (@owner.inspect())
    begin
      consumer = @user_cp.register("foofy", :hypervisor, nil, {}, nil, @owner['key'], [], [])
      consumer_cp = Candlepin.new(nil, nil, consumer.idCert.cert, consumer.idCert['key'])
      consumer_cp.consume_product()
    rescue RestClient::BadRequest => e
      exception_thrown = true
      ex_message = "Ignoring request to auto-attach. It is disabled for org \"#{@owner['key']}\" because of the hypervisor autobind setting."
      data = JSON.parse(e.response)
      data['displayMessage'].should == ex_message
    end
    exception_thrown.should be true
  end

  it 'still allows dev consumer to autobind when disabled on owner' do
    skip("candlepin running in standalone mode") if not is_hosted?

    # active subscription to allow this all to work
    active_prod = create_product()
    active_sub = create_pool_and_subscription(@owner['key'], active_prod.id, 10)
    @cp.refresh_pools(@owner['key'])

    dev_product = create_product("dev_product", "Dev Product", {:attributes => { :expires_after => "60"}})
    dev_product_2 = create_product("2nd_dev_product", "Dev Product", {:attributes => { :expires_after => "60"}})
    p_product1 = create_product("p_product_1", "Provided Product 1")
    p_product2 = create_product("p_product", "Provided Product 2")

    installed = [
        {'productId' => p_product1.id, 'productName' => p_product1.name},
        {'productId' => p_product2.id, 'productName' => p_product2.name}
    ]
    consumer = @user_cp.register("foofy_dev", :system, nil, {'dev_sku'=> "dev_product"}, nil, @owner['key'], [], installed)
    consumer_cp = Candlepin.new(nil, nil, consumer.idCert.cert, consumer.idCert['key'])

    consumer_cp.consume_product()
    entitlements = consumer_cp.list_entitlements()
    entitlements.length.should == 1
    new_pool = entitlements[0].pool
    new_pool.type.should == "DEVELOPMENT"
    new_pool.productId.should == dev_product['id']
    new_pool.providedProducts.length.should == 2
  end

  it 'will still register when activation key has autobind enabled' do
    @user_cp.register("foofy", :system, nil, {'cpu.cpu_socket(s)' => '8'}, nil, @owner['key'], [@activation_key['name']], [])
  end

  it 'will still register when content access setting enabled and autobind enabled on activation key' do
    owner = create_owner(random_string("test_owner"), nil, {
        'contentAccessModeList' => 'org_environment,test_access_mode,entitlement',
        'contentAccessMode' => "org_environment"
    })
    activation_key = @cp.create_activation_key(owner['key'], random_string('test_token'), nil, true)
    user_cp = user_client(owner, random_string("test-user"))
    user_cp.register(random_string("consumer"), :system, nil, {}, nil, owner['key'], [activation_key['name']])
  end

  it 'fails to heal entire org' do
    job = @cp.heal_owner(@owner['key'])
    wait_for_job(job['id'], 3)
    job = @cp.get_job(job['id'])
    job['state'].should == "FAILED"
    job['result'].should == "Auto-attach is disabled for owner #{@owner['key']}."
  end

  it 'fails to heal entire org if content access is org_environment' do
    owner = create_owner(random_string("test_owner"), nil, {
        'contentAccessModeList' => 'org_environment,test_access_mode,entitlement',
        'contentAccessMode' => "org_environment"
    })
    user_cp = user_client(owner, random_string("test-user"))
    job = @cp.heal_owner(owner['key'])
    wait_for_job(job['id'], 3)
    job = @cp.get_job(job['id'])
    job['state'].should == "FAILED"
    job['result'].should == "Auto-attach is disabled for owner #{owner['key']} because of the content access mode setting."
  end
end
