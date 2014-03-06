require 'spec_helper'
require 'candlepin_scenarios'

describe 'Derived Products' do
  include CandlepinMethods
  include SpecUtils

  before(:each) do
    @owner = create_owner random_string('instance_owner')
    @user = user_client(@owner, random_string('virt_user'))

    #create_product() creates products with numeric IDs by default
    @eng_product = create_product()
    installed_prods = [{'productId' => @eng_product['id'],
      'productName' => @eng_product['name']}]

    # For linking the host and the guest:
    @uuid = random_string('system.uuid')

    @physical_sys = @user.register(random_string('host'), :system, nil,
      {"cpu.cpu_socket(s)" => 8}, nil, nil, [], [], nil)
    @physical_client = Candlepin.new(username=nil, password=nil,
        cert=@physical_sys['idCert']['cert'],
        key=@physical_sys['idCert']['key'])
    @physical_client.update_consumer({:guestIds => [{'guestId' => @uuid}]})

    @guest1 = @user.register(random_string('guest'), :system, nil,
      {'virt.uuid' => @uuid, 'virt.is_guest' => 'true'}, nil, nil,
      [], installed_prods)
    @guest_client = Candlepin.new(username=nil, password=nil,
        cert=@guest1['idCert']['cert'], key=@guest1['idCert']['key'])

    # create subscription with sub-pool data:
    @datacenter_product = create_product(nil, nil, {
      :attributes => {
        :virt_limit => "unlimited",
        :stacking_id => "stackme",
        :sockets => "2",
        'multi-entitlement' => "yes"
      }
    })
    @derived_product = create_product(nil, nil, {
      :attributes => {
          :cores => 2,
          :sockets=>4
      }
    })

    @sub1 = @cp.create_subscription(@owner['key'], @datacenter_product.id,
      10, [], '', '', '', nil, nil,
      {
        'derived_product_id' => @derived_product['id'],
        'derived_provided_products' => [@eng_product['id']]
      })
    @cp.refresh_pools(@owner['key'])
    @pools = @cp.list_pools :owner => @owner.id, \
      :product => @datacenter_product.id
    @pools.size.should == 1
    @main_pool = @pools[0]

    @distributor = @user.register(random_string('host'), :candlepin, nil,
      {}, nil, nil, [], [], nil)
    @distributor_client = Candlepin.new(username=nil, password=nil,
        cert=@distributor['idCert']['cert'],
        key=@distributor['idCert']['key'])
    #@distributor_client.update_consumer(
    #    {:facts => {'@distributor_version' => 'sam-1.3'}})
  end

  # Complicated scenario, but we wanted to verify that if a derived SKU and an instance based
  # SKU are available, the guest autobind will have it's host autobind to the derived and
  # prefer it's virt_only sub-pool to instance based.
  it 'prefers a host-autobind virt-only sub-pool to instance based pool during guest autobind' do
    # create instance based subscription:
    instance_product = create_product(nil, nil, {
      :attributes => {
        :instance_multiplier => "2",
        :stacking_id => "stackme",
        :sockets => "2",
        :host_limited => "true",
        'multi-entitlement' => "yes"
      }
    })
    @sub1 = @cp.create_subscription(@owner['key'], instance_product.id,
      10, [@eng_product['id']])
    @cp.refresh_pools(@owner['key'])
    @guest_client.consume_product

    # Now the host should have an entitlement to the virt pool, and the guest
    # to it's derived pool.
    host_ents = @physical_client.list_entitlements
    host_ents.length.should == 1
    host_ents[0]['pool']['productId'].should == @datacenter_product['id']
    guest_ents = @guest_client.list_entitlements
    guest_ents.length.should == 1
    guest_ents[0]['pool']['productId'].should == @derived_product['id']


  end

  it 'transfers sub-product data to main pool' do
    @main_pool['derivedProductId'].should == @derived_product['id']
    @main_pool['derivedProvidedProducts'].size.should == 1
    @main_pool['derivedProvidedProducts'][0]['productId'].should ==
      @eng_product['id']

    @physical_client.consume_pool @main_pool['id']
    ents = @physical_client.list_entitlements
    ents.size.should == 1

    # Guest should now see additional sub-pool:
    @guest_client.list_pools({:consumer => @guest_client.uuid}).size.should == 2
    guest_pools = @guest_client.list_pools({:consumer => @guest_client.uuid,
        :product => @derived_product['id']})
    guest_pools.size.should == 1
    derived_prod_pool = guest_pools[0]
    derived_prod_pool['quantity'].should == -1 # unlimited

    derived_prod_pool['sourceConsumer']['uuid'].should == @physical_sys.uuid
    derived_prod_pool['sourceStackId'].should == "stackme"

    pool_attrs = flatten_attributes(derived_prod_pool['attributes'])
    verify_attribute(pool_attrs, "requires_consumer_type", 'system')
    verify_attribute(pool_attrs, "requires_host", @physical_sys.uuid)
    verify_attribute(pool_attrs, "virt_only", "true")
    verify_attribute(pool_attrs, "pool_derived", "true")

    product_attrs = flatten_attributes(derived_prod_pool['productAttributes'])
    verify_attribute(product_attrs, "sockets", "4")
    verify_attribute(product_attrs, "cores", "2")
  end

  it 'allows guest to consume sub product pool' do
    @physical_client.consume_pool @main_pool['id']
    ents = @physical_client.list_entitlements
    ents.size.should == 1

    guest_pools = @guest_client.list_pools({:consumer => @guest_client.uuid,
      :product => @derived_product['id']})
    guest_pools.size.should == 1
    @guest_client.consume_pool guest_pools[0]['id']
    ents = @guest_client.list_entitlements
    ents.size.should == 1
  end

  it 'not be visible by distributor that does not have capability after basic search' do
    pools = @distributor_client.list_pools :consumer => @distributor.uuid
    pools.size.should == 0
  end

  it 'be visible by distributor that does not have capability after list all' do
    pools = @distributor_client.list_pools :consumer => @distributor.uuid, :listall => "true"
    pools.size.should == 1

    pool = pools.first
    pool['derivedProductId'].should == @derived_product['id']
    pool['derivedProvidedProducts'].size.should == 1
    pool['derivedProvidedProducts'][0]['productId'].should ==
      @eng_product['id']
  end

  it 'prevents distributor from attaching without necessisary capabilities' do
    expected_error = "Unit does not support derived products data required by pool '%s'" % @main_pool['id']
    begin
        @distributor_client.consume_pool @main_pool['id']
        fail("Expected Forbidden since distributor does not have capability")
    rescue RestClient::Forbidden => e
      message = JSON.parse(e.http_body)['displayMessage']
      message.should == expected_error
    end
  end

  def verify_attribute(attrs, attr_name, attr_value)
    attrs.should have_key(attr_name)
    attrs[attr_name].should == attr_value
  end
end
