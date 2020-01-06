require 'spec_helper'
require 'candlepin_scenarios'

describe 'Derived Products' do
  include CandlepinMethods
  include SpecUtils
  include CertificateMethods
  include AttributeHelper

  before(:each) do
    @owner = create_owner random_string('instance_owner')
    @owner_key = @owner['key']
    @user = user_client(@owner, random_string('virt_user'))

    #create_product() creates products with numeric IDs by default
    @eng_product = create_product()
    @eng_product_2 = create_product(nil, "eng_product_2")
    @modified_product = create_product()

    @eng_product_content = create_content({:gpg_url => 'gpg_url',
                                         :content_url => '/content/dist/rhel/$releasever/$basearch/os',
                                         :metadata_expire => 6400})


    @product_modifier_content = create_content({:gpg_url => 'gpg_url',
                                         :content_url => '/this/modifies/product',
                                         :metadata_expire => 6400,
                                         :modified_products => [@modified_product["id"]]})

    @cp.add_content_to_product(@owner['key'], @eng_product.id, @eng_product_content.id, true)
    @cp.add_content_to_product(@owner['key'], @eng_product_2.id, @product_modifier_content.id, true)

    installed_prods = [{'productId' => @eng_product['id'], 'productName' => @eng_product['name']}]

    # For linking the host and the guest:
    @uuid = random_string('system.uuid')

    @physical_sys = @user.register(random_string('host'), :system, nil,
      {"cpu.cpu_socket(s)" => 8}, nil, nil, [], [], nil)
    @physical_client = Candlepin.new(nil, nil, @physical_sys['idCert']['cert'], @physical_sys['idCert']['key'])
    @physical_client.update_consumer({:facts => {"system.certificate_version" => "3.2"},
                                           :guestIds => [{'guestId' => @uuid}]})

    @guest1 = @user.register(random_string('guest'), :system, nil,
      {'virt.uuid' => @uuid, 'virt.is_guest' => 'true'}, nil, nil,
      [], installed_prods)
    @guest_client = Candlepin.new(nil, nil, @guest1['idCert']['cert'], @guest1['idCert']['key'])
    # create subscription with sub-pool data:
    @datacenter_product = create_product(nil, "datacenter product", {
      :attributes => {
        :virt_limit => "unlimited",
        :stacking_id => "stackme",
        :sockets => "2",
        'host_limited' => "true",
        'multi-entitlement' => "yes"
      }
    })

    @datacenter_product_2 = create_product(nil, "datacenter product 2", {
      :attributes => {
        :virt_limit => "unlimited",
        'host_limited' => "true"
      }
    })

    @derived_product = create_product(nil, "derived product 1", {
      :attributes => {
          :cores => 2,
          :sockets => 4
      },
      :providedProducts => [@eng_product['id']]
    })

    @derived_product_2 = create_product(nil, "derived product 2", {
      :attributes => {
          :cores => 2
      }
    })

    @main_pool = create_pool_and_subscription(@owner['key'], @datacenter_product.id,
      10, [], '', '', '', nil, nil, false,
      {
        :derived_product_id => @derived_product['id'],
        :derived_provided_products => [@eng_product['id']]
      })

    create_pool_and_subscription(@owner['key'], @datacenter_product_2.id,
      10, [], '', '', '', nil, nil, false,
      {
        :derived_product_id => @derived_product_2['id'],
        :derived_provided_products => [@eng_product_2['id'], @modified_product['id']]
      })

    @pools = @cp.list_pools :owner => @owner.id, \
      :product => @datacenter_product.id
    @pools.size.should == 1

    @distributor = @user.register(random_string('host'), :candlepin, nil,
      {}, nil, nil, [], [], nil)
    @distributor_client = Candlepin.new(nil, nil, @distributor['idCert']['cert'], @distributor['idCert']['key'])
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
    create_pool_and_subscription(@owner['key'], instance_product.id, 10, [@eng_product['id']])

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
    @main_pool['derivedProvidedProducts'][0]['productId'].should == @eng_product['id']

    @physical_client.consume_pool @main_pool['id']
    ents = @physical_client.list_entitlements
    ents.size.should == 1

    # Guest should now see additional sub-pool:
    @guest_client.list_pools({:consumer => @guest_client.uuid}).size.should == 3
    guest_pools = @guest_client.list_pools({:consumer => @guest_client.uuid, :product => @derived_product['id']})
    guest_pools.size.should == 1
    derived_prod_pool = guest_pools[0]
    derived_prod_pool['quantity'].should == -1 # unlimited

    derived_prod_pool['sourceStackId'].should == "stackme"

    pool_attrs = derived_prod_pool['attributes']
    expect(get_attribute_value(pool_attrs, "requires_consumer_type")).to eq('system')
    expect(get_attribute_value(pool_attrs, "requires_host")).to eq(@physical_sys.uuid)
    expect(get_attribute_value(pool_attrs, "virt_only")).to eq("true")
    expect(get_attribute_value(pool_attrs, "pool_derived")).to eq("true")

    product_attrs = derived_prod_pool['productAttributes']
    expect(get_attribute_value(product_attrs, "sockets")).to eq("4")
    expect(get_attribute_value(product_attrs, "cores")).to eq("2")
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

  it 'should not be visible by distributor that does not have capability after basic search' do
    pools = @distributor_client.list_pools :consumer => @distributor.uuid
    pools.size.should == 0
  end

  it 'should be visible by distributor that does not have capability after list all' do
    pools = @distributor_client.list_pools :consumer => @distributor.uuid, :listall => "true"
    pools.size.should == 2

    pool = pools[0]
    pool = pools[1] unless pool.id == @main_pool.id
    pool['derivedProductId'].should == @derived_product['id']
    pool['derivedProvidedProducts'].size.should == 1
    pool['derivedProvidedProducts'][0]['productId'].should == @eng_product['id']
  end

  it 'prevents distributor from attaching without necessisary capabilities' do
    expected_error = "Unit does not support derived products data required by pool \"%s\"" % @main_pool['id']
    begin
        @distributor_client.consume_pool @main_pool['id']
        fail("Expected Forbidden since distributor does not have capability")
    rescue RestClient::Forbidden => e
      message = JSON.parse(e.http_body)['displayMessage']
      message.should == expected_error
    end
  end

  it 'distributor entitlement cert includes derived content' do
    dist_name = random_string("CP Distributor")
    create_distributor_version(dist_name, "Subscription Asset Manager", ["cert_v3", "derived_product"])

    @distributor_client.update_consumer({:facts => {'distributor_version' => dist_name}})

    entitlement = @distributor_client.consume_pool @main_pool['id']
    entitlement.should_not be_nil

    json_body = extract_payload(@distributor_client.list_certificates[0]['cert'])
    products = json_body['products']
    products.size.should == 3

    found = false;
    products.each do |cert_product|
      if cert_product['id'] == @eng_product.id
         found = true;
         content_sets = cert_product['content']
         content_sets.size.should == 1
         content_sets[0].id.should == @eng_product_content.id
      end
    end

    found.should == true
  end

  it 'distributor entitlement cert includes derived modifier content' do
    # Content for modified product is only included if it provides the modified product
    # through derived provided products, provided products, or through another entitlement.
    dist_name = random_string("CP Distributor")
    create_distributor_version(dist_name,
      "Subscription Asset Manager",
      ["cert_v3", "derived_product"])

    @distributor_client.update_consumer({:facts => {'distributor_version' => dist_name}})


    pools = @cp.list_pools :owner => @owner.id, \
      :product => @datacenter_product_2.id
    pools.size.should == 1
    target_pool = pools[0]

    entitlement = @distributor_client.consume_pool target_pool['id']
    entitlement.should_not be_nil

    json_body = extract_payload(@distributor_client.list_certificates[0]['cert'])
    products = json_body['products']
    products.size.should == 4

    found = false;
    products.each do |cert_product|
      if cert_product['id'] == @eng_product_2.id
         found = true;
         content_sets = cert_product['content']
         content_sets.size.should == 1
         content_sets[0]['id'].should == @product_modifier_content.id
      end
    end

    found.should == true
  end

  it 'distributor entitlement cert does not include modifier content when base entitlement is deleted' do

    setup_data = setup_modifier_test()
    vdc_pool_ent = setup_data[:vdc_pool_ent]

    # Unbind the base entitlement. This should trigger the removal of the modifier content since the
    # distributor no longer has an entitlement that provides @modified_product.
    @distributor_client.unbind_entitlement(vdc_pool_ent[0]["id"])
    ents = @distributor_client.list_entitlements
    ents.size.should == 1
    verify_modifier_content(ents[0], false)
  end

  it 'distributor entitlement cert does not include modifier content when base pool is deleted' do

    setup_data = setup_modifier_test()
    vdc_pool = setup_data[:vdc_pool]

    # Delete the base pool. This should trigger the removal of the modifier content since the
    # distributor no longer has an entitlement that provides @modified_product.
    @cp.delete_pool(vdc_pool['id'])
    ents = @distributor_client.list_entitlements
    ents.size.should == 1
    verify_modifier_content(ents[0], false)
  end

  it 'host entitlement cert does not include derived content' do
    entitlement = @physical_client.consume_pool @main_pool['id']
    entitlement.should_not be_nil

    json_body = extract_payload(@physical_client.list_certificates[0]['cert'])
    products = json_body['products']
    products.size.should == 1
    product = products[0]
    product['id'].should == @datacenter_product.id
    product['content'].size.should == 0
  end

  it 'regenerates entitlements when updating derived content' do
    skip("candlepin running in standalone mode") if not is_hosted?

    # Create a subscription with an upstream product with a derived product that provides content
    datacenter_product = create_upstream_product(random_string('dc_prod'), {
        :attributes => {
            :virt_limit => "unlimited",
            :stacking_id => "stackme",
            :sockets => "2",
            'multi-entitlement' => "yes"
        }
    })
    derived_product = create_upstream_product(random_string('derived_prod'), {
        :attributes => {
            :cores => 2,
            :sockets=>4
        }
    })

    derived_eng_product = create_upstream_product(random_string(nil, true))
    derived_content = create_upstream_content("twentyTwo", {
        :type => "yum",
        :label => "teardropsOnMyGuitar",
        :name => "swiftrocks",
        :vendor => "fifteen",
        :releaseVer => nil
    })
    add_content_to_product_upstream(derived_eng_product.id, derived_content.id)

    sub = create_upstream_subscription(random_string('dc_sub'), @owner_key, datacenter_product.id, {
        :quantity => 10,
        :derived_product => derived_product,
        :derived_provided_products => [derived_eng_product]
    })

    @cp.refresh_pools(@owner_key)
    @cp.get_product(@owner_key, derived_eng_product.id)['productContent'].size.should == 1
    main_pools = @cp.list_pools({:owner => @owner.id, :product => datacenter_product.id})
    expect(main_pools.length).to eq(1) # We're expecting the base pool

    dist_name = random_string("CP Distributor")
    create_distributor_version(dist_name, "Subscription Asset Manager", ["cert_v3", "derived_product"])
    @distributor_client.update_consumer({:facts => {'distributor_version' => dist_name}})
    entitlement = @distributor_client.consume_pool main_pools.first['id']
    json_body = extract_payload(@distributor_client.list_certificates[0]['cert'])
    products = json_body['products']
    products.size.should == 1

    found = false;
    products.each do |cert_product|
      if cert_product['id'] == derived_eng_product.id
        found = true;
        content_sets = cert_product['content']
        content_sets.size.should == 1
        content_sets[0].id.should == derived_content.id
      end
    end
    found.should == true

    # Add content to the derived product upstream
    new_derived_content = create_upstream_content("twentyFour", {
        :type => "yum",
        :label => "teardropsOnMyUkelele",
        :name => "slowrocks",
        :vendor => "fifteen",
        :releaseVer => nil
    })
    add_content_to_product_upstream(derived_eng_product.id, new_derived_content.id)

    # Refresh the account
    @cp.refresh_pools(@owner_key)
    @cp.get_product(@owner_key, derived_eng_product.id)['productContent'].size.should == 2
    json_body = extract_payload(@distributor_client.list_certificates[0]['cert'])
    products = json_body['products']
    products.size.should == 1

    found = false;
    products.each do |cert_product|
      if cert_product['id'] == derived_eng_product.id
        found = true;
        content_sets = cert_product['content']
        content_sets.size.should == 2
        expect(content_sets[0].id).to eq(derived_content.id).or eq(new_derived_content.id)
        expect(content_sets[1].id).to eq(derived_content.id).or eq(new_derived_content.id)
      end
    end
    found.should == true

    @distributor_client.unbind_entitlement(entitlement[0].id)
    entitlement = @distributor_client.consume_pool main_pools.first['id']
    json_body = extract_payload(@distributor_client.list_certificates[0]['cert'])
    products = json_body['products']

    found = false;
    products.each do |cert_product|
      if cert_product['id'] == derived_eng_product.id
        found = true;
        content_sets = cert_product['content']
        content_sets.size.should == 2
        expect(content_sets[0].id).to eq(derived_content.id).or eq(new_derived_content.id)
        expect(content_sets[1].id).to eq(derived_content.id).or eq(new_derived_content.id)
      end
    end
    found.should == true
  end

  def setup_modifier_test
    # Content for modified product is only included if it provides the modified product
    # through derived provided products, provided products, or through another entitlement.
    dist_name = random_string("CP Distributor")
    create_distributor_version(dist_name,
                               "Subscription Asset Manager",
                               ["cert_v3", "derived_product"])

    @distributor_client.update_consumer({:facts => {'distributor_version' => dist_name}})

    # Create a VDC style subscription that has a derived provided product matching @eng_product_2's
    # modifying content set requirement (@modified_product).
    vdc_product = create_product(nil, "base")
    vdc_pool = create_pool_and_subscription(@owner['key'], vdc_product.id,
                                            10, [], '', '', '', nil, nil, false,
                                            {
                                                :derived_product_id => @derived_product_2['id'],
                                                :derived_provided_products => [@modified_product['id']]
                                            })

    # Create a subscription that has a product that has content that has modifier definitions (@eng_product_2)
    modifier_ent_product = create_product(nil, "modifier")
    modifier_pool = create_pool_and_subscription(@owner['key'], modifier_ent_product.id, 10, [@eng_product_2['id']])

    # Grab an entitlement from the VDC style subscription.
    vdc_pool_ent = @distributor_client.consume_pool vdc_pool['id']
    vdc_pool_ent.should_not be_nil

    # Grab an entitlement from the modifier pool. Already having an entitlement that provides @modified_product
    # permits the addition of the additional content set from the modifier product.
    modifier_pool_ent = @distributor_client.consume_pool modifier_pool['id']
    modifier_pool_ent.should_not be_nil
    verify_modifier_content(modifier_pool_ent[0], true)

    setup_data = {}
    setup_data[:vdc_pool] = vdc_pool
    setup_data[:vdc_pool_ent] = vdc_pool_ent
    setup_data[:modifier_pool] = modifier_pool
    setup_data[:modifier_pool_ent] = modifier_pool_ent
    return setup_data
  end

  # Verifies that the specified entitlement contains/does not contain the modifier
  # content as per the test setup.
  def verify_modifier_content(entitlement, content_should_exist)
    json_body = extract_payload(entitlement['certificates'][0]['cert'])
    products = json_body['products']
    products.size.should == 2

    found = false;
    products.each do |cert_product|
      if cert_product['id'] == @eng_product_2.id
        content_sets = cert_product['content']
        if content_should_exist
          found = true;
          content_sets.size.should == 1
          content_sets[0]['id'].should == @product_modifier_content.id
        else
          content_sets.size.should == 0
        end
      end
    end
    found.should == content_should_exist
  end

end
