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
      ex_message = "Ignoring request to auto-attach. It is disabled for org \"#{@owner['key']}\"."
      data = JSON.parse(e.response)
      data['displayMessage'].should == ex_message
    end
    exception_thrown.should be true
  end

  it 'autobind should fail when owner is in SCA mode' do
    skip("candlepin running in standalone mode") if not is_hosted?

    exception_thrown = false
    owner = create_owner(random_string("test_owner"), nil, {
      'contentAccessModeList' => 'org_environment,entitlement',
      'contentAccessMode' => "org_environment"
    })
    owner = @cp.get_owner(owner['key'])

    expect(owner).to_not be_nil

    cp_user = user_client(owner, random_string("testing-user"))
    consumer = cp_user.register("foofy_test", :system, nil,
      {'cpu.cpu_socket(s)' => '8'}, nil, owner['key'], [], [])
    consumer_cp = Candlepin.new(nil, nil, consumer.idCert.cert, consumer.idCert['key'])

    begin
      consumer_cp.consume_product()
    rescue RestClient::BadRequest => e
      exception_thrown = true
      ex_message = "Ignoring request to auto-attach. " +
        "It is disabled for org \"#{owner['key']}\" because of the content access mode setting."
      data = JSON.parse(e.response)
      expect(data['displayMessage']).to eq(ex_message)
    end

    expect(exception_thrown).to eq(true)
  end

  it 'autobind fails when hypervisor autobind disabled on owner' do
    exception_thrown = false
    @owner['autobindDisabled'] = false
    @owner['autobindHypervisorDisabled'] = true
    @cp.update_owner(@owner['key'], @owner)
    @owner = @cp.get_owner(@owner['key'])

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
    active_sub = @cp.create_pool(@owner['key'], active_prod.id, {
      :quantity => 10,
      :subscription_id => random_str('source_sub'),
      :upstream_pool_id => random_str('upstream')
    })

    provided_product_1 = create_upstream_product("prov_product_1", { :name => "provided product 1" })
    provided_product_2 = create_upstream_product("prov_product_2", { :name => "provided product 2" })
    dev_product = create_upstream_product("dev_product", {
        :name => "Dev Product",
        :attributes => {
            :expires_after => "60"
        },
        :providedProducts => [provided_product_1, provided_product_2]
    })

    installed = [
        {'productId' => provided_product_1.id, 'productName' => provided_product_1.name},
        {'productId' => provided_product_2.id, 'productName' => provided_product_2.name}
    ]

    consumer = @user_cp.register("foofy_dev", :system, nil, { 'dev_sku' => dev_product.id }, nil, @owner['key'], [], installed)
    consumer_cp = Candlepin.new(nil, nil, consumer.idCert.cert, consumer.idCert['key'])
    consumer_cp.consume_product()

    entitlements = consumer_cp.list_entitlements()
    expect(entitlements.length).to eq(1)

    new_pool = entitlements[0].pool
    expect(new_pool.type).to eq("DEVELOPMENT")
    expect(new_pool.productId).to eq(dev_product.id)
    expect(new_pool.providedProducts.length).to eq(2)
  end

  it 'will still register when activation key has autobind enabled' do
    @user_cp.register("foofy", :system, nil, {'cpu.cpu_socket(s)' => '8'}, nil, @owner['key'], [@activation_key['name']], [])
  end

  it 'will still register when content access setting enabled and autobind enabled on activation key' do
    # org_environment mode cannot be set via API in standalone mode
    skip("candlepin running in standalone mode") if not is_hosted?

    owner = create_owner(random_string("test_owner"), nil, {
        'contentAccessModeList' => 'org_environment,entitlement',
        'contentAccessMode' => "org_environment"
    })

    activation_key = @cp.create_activation_key(owner['key'], random_string('test_token'), nil, true)
    user_cp = user_client(owner, random_string("test-user"))
    user_cp.register(random_string("consumer"), :system, nil, {}, nil, owner['key'], [activation_key['name']])
  end

  it 'fails to heal entire org' do
    job = @cp.heal_owner(@owner['key'])
    wait_for_job(job['id'], 15)
    job = @cp.get_job(job['id'], true)
    job['state'].should == "FAILED"
    job['resultData'].should == "org.candlepin.async.JobExecutionException: Auto-attach is disabled for owner #{@owner['key']}."
  end

  it 'fails to heal entire org if content access is org_environment' do
    # org_environment mode cannot be set via API in standalone mode
    skip("candlepin running in standalone mode") if not is_hosted?

    owner = create_owner(random_string("test_owner"), nil, {
      'contentAccessModeList' => 'org_environment,entitlement',
      'contentAccessMode' => "org_environment"
    })
    user_cp = user_client(owner, random_string("test-user"))
    job = @cp.heal_owner(owner['key'])
    wait_for_job(job['id'], 15)
    job = @cp.get_job(job['id'], true)
    job['state'].should == "FAILED"
    job['resultData'].should == "org.candlepin.async.JobExecutionException: Auto-attach is disabled for owner #{owner['key']} because of the content access mode setting."
  end
end
