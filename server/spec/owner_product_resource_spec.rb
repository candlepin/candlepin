require 'spec_helper'
require 'candlepin_scenarios'

describe 'Owner Product Resource' do

  include CandlepinMethods

  before do
    @owner = create_owner random_string('test_owner')
    @product = create_product random_string('product')
    @prov_product = create_product random_string('provided_product')
    @derived_product = create_product random_string('derived_product')
    @derived_prov_product = create_product random_string('derived_provided_product')

    create_pool_and_subscription(@owner['key'], @product.id,
      10, [@prov_product.id], '222', '', '', nil, nil, false,
      {
        :derived_product_id => @derived_product.id,
        :derived_provided_products => [@derived_prov_product.id]
      })
  end

  it 'should fail when fetching non-existing products' do
    lambda do
      @cp.get_product(@owner['key'], "some bad product id")
    end.should raise_exception(RestClient::ResourceNotFound)
  end

  it 'updates individual product fields' do
    prod = create_product(nil, 'tacos', {:multiplier => 2, :dependentProductIds => [2, 4]})
    #Ensure the dates are at least one second different
    sleep 2
    prod2 = create_product(nil, 'enchiladas', {:multiplier => 4})

    prod.name.should_not == prod2.name
    prod.multiplier.should_not == prod2.multiplier
    prod.dependentProductIds.should_not == prod2.dependentProductIds

    prod = @cp.update_product(@owner['key'], prod.id, {:name => 'enchiladas'})

    prod.name.should == prod2.name
    prod.multiplier.should_not == prod2.multiplier
    prod.dependentProductIds.should_not == prod2.dependentProductIds

    #the idea here is attributes should not change if set equal to nil
    #then updated, so store it as a temp variable to compare to after
    #update_product is called.
    temp_attributes = prod.attributes

    # Delay here a moment to ensure an in-place update on the product doesn't trigger
    # attributes to be cloned/updated (as we're not updating attributes here)
    sleep 1

    prod = @cp.update_product(@owner['key'], prod.id, {:multiplier => prod2.multiplier, :attributes => nil })

    prod.multiplier.should == prod2.multiplier
    prod.attributes.size.should == 1
    temp_attributes.size.should == 1
    prod.attributes[0]['name'].should == temp_attributes[0]['name']
    prod.attributes[0]['value'].should == temp_attributes[0]['value']
    prod = @cp.update_product(@owner['key'], prod.id, {:dependentProductIds => prod2.dependentProductIds})

    prod.dependentProductIds.should == prod2.dependentProductIds
  end

  it 'does not update product name' do
    prod = create_product(nil, 'iron maiden')
    prod2 = create_product(nil, nil)

    prod.name.should == 'iron maiden'

    prod = @cp.update_product(@owner['key'], prod.id, prod2)

    prod.name.should == 'iron maiden'
  end

  it 'removes content from products' do
    prod = create_product
    content = create_content
    @cp.add_content_to_product(@owner['key'], prod['id'], content['id'])
    prod = @cp.get_product(@owner['key'], prod['id'])
    prod['productContent'].size.should == 1

    @cp.remove_content_from_product(@owner['key'], prod['id'], content['id'])
    prod = @cp.get_product(@owner['key'], prod['id'])
    prod['productContent'].should be_empty
  end

  it 'allows regular users to view products' do
    owner = create_owner random_string('test')
    user_cp = user_client(owner, random_string('testuser'), true)
    prod = create_product(nil, nil, {:owner => owner['key']})
    user_cp.get_product(owner['key'], prod['id'])
  end

  it 'create two products with the same name' do
    product1 = create_product(nil, 'doppelganger')
    product2 = create_product(nil, 'doppelganger')
    product1.id.should_not  == product2.id
    product1.name.should == product2.name
  end

  it 'retrieves the owners of an active product' do
    owner = create_owner(random_string('owner'), nil)
    product = create_product(random_string("test_id"), random_string("test_name"), {:owner => owner['key']})
    provided_product = create_product(nil, nil, {:owner => owner['key']})
    create_pool_and_subscription(owner['key'], product.id, 10, [provided_product.id])
    user = user_client(owner, random_string('billy'))
    system = consumer_client(user, 'system6')
    system.consume_product(product.id)
    product_owners = @cp.get_product_owners([provided_product.id])
    product_owners.length.should eq(1)
    product_owners[0]['key'].should == owner['key']
  end

  it 'refreshes pools for owner on subscription creation' do
    owner = create_owner(random_string('owner'))
    owner2 = create_owner(random_string('owner2'))

    owner_client = user_client(owner, random_string('testuser'))
    owner2_client = user_client(owner2, random_string('testuser'))

    product = create_product(
      random_string("test_id"),
      random_string("test_name"),
      {:owner => owner['key']}
    )

    provided_product = create_product(nil, nil, {:owner => owner['key']})

    create_pool_and_subscription(owner['key'], product.id, 10, [provided_product.id])

    pool = owner_client.list_pools(:owner => owner.id)
    pool.length.should eq(1)

    pool = owner_client.list_pools(:owner => owner.id)
    pool.length.should eq(1)
    pool[0]['owner']['key'].should == owner['key']

    pool = owner2_client.list_pools(:owner => owner2.id)
    pool.length.should eq(0)
  end

  def setupOrgProductsAndPools()
    owner1 = create_owner(random_string("owner"))
    owner2 = create_owner(random_string("owner"))
    owner3 = create_owner(random_string("owner"))

    prod1o1 = create_product("p1", "p1", { :owner => owner1['key'] })
    prod1o2 = create_product("p1", "p1", { :owner => owner2['key'] })
    prod1o3 = create_product("p1", "p1", { :owner => owner3['key'] })

    prod2o1 = create_product("p2", "p2", { :owner => owner1['key'] })
    prod2o2 = create_product("p2", "p2", { :owner => owner2['key'] })

    prod3o2 = create_product("p3", "p3", { :owner => owner2['key'] })
    prod3o3 = create_product("p3", "p3", { :owner => owner3['key'] })

    prod4 = create_product("p4", "p4", { :owner => owner1['key'] })
    prod4d = create_product("p4d", "p4d", { :owner => owner1['key'] })
    prod5 = create_product("p5", "p5", { :owner => owner2['key'] })
    prod5d = create_product("p5d", "p5d", { :owner => owner2['key'] })
    prod6 = create_product("p6", "p6", { :owner => owner3['key'] })
    prod6d = create_product("p6d", "p6d", { :owner => owner3['key'] })

    @cp.create_pool(owner1['key'], "p4", {
      :derived_product_id         => "p4d",
      :provided_products          => ["p1"],
      :derived_provided_products  => ["p2"]
    });

    @cp.create_pool(owner2['key'], "p5", {
      :derived_product_id         => "p5d",
      :provided_products          => ["p1", "p2"],
      :derived_provided_products  => ["p3"]
    });

    @cp.create_pool(owner3['key'], "p6", {
      :derived_product_id         => "p6d",
      :provided_products          => ["p1"],
      :derived_provided_products  => ["p3"]
    });

    return [owner1, owner2, owner3]
  end

  it "refreshes pools for orgs owning products" do
    skip("candlepin running in standalone mode") if not is_hosted?

    owners = setupOrgProductsAndPools()
    owner1 = owners[0]
    owner2 = owners[1]
    owner3 = owners[2]

    # Override enabled to true:
    job = @cp.refresh_pools_for_product(owner1['key'], "p4")
    job.should_not be_nil
    job['key'].should include("RefreshPools")
    wait_for_job(job['id'], 15)

    job = @cp.refresh_pools_for_product(owner2['key'], "p5d")
    job.should_not be_nil
    job['key'].should include("RefreshPools")
    wait_for_job(job['id'], 15)

    job = @cp.refresh_pools_for_product(owner3['key'], "p1")
    job.should_not be_nil
    job['key'].should include("RefreshPools")
    wait_for_job(job['id'], 15)

    job = @cp.refresh_pools_for_product(owner3['key'], "p3")
    job.should_not be_nil
    job['key'].should include("RefreshPools")
    wait_for_job(job['id'], 15)

    lambda do
      @cp.refresh_pools_for_product(owner1['key'], "nope")
    end.should raise_exception(RestClient::ResourceNotFound)
  end

  it 'lists all products in bulk fetch' do
    prod1_id = random_string("test_id")
    prod2_id = random_string("test_id")
    prod3_id = random_string("test_id")
    prod1 = create_product(prod1_id, random_string("test_name"))
    sleep 1
    prod2 = create_product(prod2_id, random_string("test_name"))
    sleep 1
    prod3 = create_product(prod3_id, random_string("test_name"))
    all_products = @cp.list_products_by_owner(@owner['key'])
    all_products.size.should >= 3

    # Pick two products to use in a bulk get
    prod_ids_to_get = [prod1_id, prod3_id]

    # Get 2 products
    bulk_get_products = @cp.list_products_by_owner(@owner['key'], prod_ids_to_get)
    bulk_get_products.size.should == 2

    # Make sure it got the correct ones
    if (bulk_get_products[0]['id'] == prod1_id)
      bulk_get_products[1]['id'].should == prod3_id
    else
      bulk_get_products[0]['id'].should == prod3_id
      bulk_get_products[1]['id'].should == prod1_id
    end
  end

  it 'lists products in pages' do
    @owner = create_owner random_string('test_owner')

    prod1_id = "test_product-1"
    prod2_id = "test_product-2"
    prod3_id = "test_product-3"

    # The creation order here is important. By default, Candlepin sorts in descending order of the
    # entity's creation time, so we need to create them backward to let the default sorting order
    # let us page through them in ascending order.
    prod3 = create_product(prod3_id, random_string("test_name"))
    sleep 1
    prod2 = create_product(prod2_id, random_string("test_name"))
    sleep 1
    prod1 = create_product(prod1_id, random_string("test_name"))

    p1set = @cp.list_products_by_owner(@owner['key'], nil, {:page=>1, :per_page=>1})
    expect(p1set.size).to eq(1)
    expect(p1set[0]['id']).to eq(prod1_id)

    p2set = @cp.list_products_by_owner(@owner['key'], nil, {:page=>2, :per_page=>1})
    expect(p2set.size).to eq(1)
    expect(p2set[0]['id']).to eq(prod2_id)

    p3set = @cp.list_products_by_owner(@owner['key'], nil, {:page=>3, :per_page=>1})
    expect(p3set.size).to eq(1)
    expect(p3set[0]['id']).to eq(prod3_id)

    p4set = @cp.list_products_by_owner(@owner['key'], nil, {:page=>4, :per_page=>1})
    expect(p4set.size).to eq(0)
  end

  it 'lists products in sorted pages' do
    @owner = create_owner random_string('test_owner')

    prod1_id = "test_product-1"
    prod2_id = "test_product-2"
    prod3_id = "test_product-3"

    # The creation order here is important so we don't accidentally setup the correct ordering by
    # default.
    prod2 = create_product(prod2_id, random_string("test_name"))
    prod1 = create_product(prod1_id, random_string("test_name"))
    prod3 = create_product(prod3_id, random_string("test_name"))

    p1set = @cp.list_products_by_owner(@owner['key'], nil, {:page=>1, :per_page=>1, :sort_by=>"id"})
    expect(p1set.size).to eq(1)
    expect(p1set[0]['id']).to eq(prod3_id)

    p2set = @cp.list_products_by_owner(@owner['key'], nil, {:page=>2, :per_page=>1, :sort_by=>"id"})
    expect(p2set.size).to eq(1)
    expect(p2set[0]['id']).to eq(prod2_id)

    p3set = @cp.list_products_by_owner(@owner['key'], nil, {:page=>3, :per_page=>1, :sort_by=>"id"})
    expect(p3set.size).to eq(1)
    expect(p3set[0]['id']).to eq(prod1_id)

    p4set = @cp.list_products_by_owner(@owner['key'], nil, {:page=>4, :per_page=>1, :sort_by=>"id"})
    expect(p4set.size).to eq(0)
  end

  it 'should return correct exception for contraint violations' do
    lambda {
      prod = create_product(random_string("test_id"), random_string("test_name"), {
        :attributes => {
          :support_level => ("a" * 255) + ("b" * 10)
        }
      })
    }.should raise_exception(RestClient::BadRequest)
  end

  it 'bad request on attempt to delete product attached to sub' do
    if is_hosted? then
      lambda do
        @cp.delete_product(@owner['key'], @product.id)
      end.should raise_exception(RestClient::Forbidden)
    else
      lambda do
        @cp.delete_product(@owner['key'], @product.id)
      end.should raise_exception(RestClient::BadRequest)
    end
  end

  it 'bad request on attempt to delete provided product attached to sub' do
    if is_hosted? then
      lambda do
        @cp.delete_product(@owner['key'], @prov_product.id)
      end.should raise_exception(RestClient::Forbidden)
    else
      lambda do
        @cp.delete_product(@owner['key'], @prov_product.id)
      end.should raise_exception(RestClient::BadRequest)
    end
  end

  it 'bad request on attempt to delete derived product attached to sub' do
    if is_hosted? then
      lambda do
        @cp.delete_product(@owner['key'], @derived_product.id)
      end.should raise_exception(RestClient::Forbidden)
    else
      lambda do
        @cp.delete_product(@owner['key'], @derived_product.id)
      end.should raise_exception(RestClient::BadRequest)
    end
  end

  it 'bad request on attempt to delete derived provided product attached to sub' do
    if is_hosted? then
      lambda do
        @cp.delete_product(@owner['key'], @derived_prov_product.id)
      end.should raise_exception(RestClient::Forbidden)
    else
      lambda do
        @cp.delete_product(@owner['key'], @derived_prov_product.id)
      end.should raise_exception(RestClient::BadRequest)
    end
  end

  it 'should create and delete products with branding correctly' do
    b1 = {:productId => 'prodid1',
      :type => 'type1', :name => 'branding1'}
    b2 = {:productId => 'prodid2',
      :type => 'type2', :name => 'branding2'}

    owner = create_owner random_string('some-owner')
    name = random_string("product-")

    prod = create_product(name, name, :owner => owner['key'], :branding => [b1, b2])
    prod.branding.size.should == 2

    prod = @cp.get_product(owner['key'], prod['id'])
    prod.branding.size.should == 2

    prod = @cp.update_product(owner['key'], prod['id'], { :name => "new_product_name" })
    expect(prod.name).to eq('new_product_name')
    prod.branding.size.should == 2

    @cp.delete_product(owner['key'], prod['id'])
    lambda do
      @cp.get_product(owner['key'], prod['id'])
    end.should raise_exception(RestClient::ResourceNotFound)

    # The shared product data should not get removed until the OrphanCleanupJob runs.
    product = @cp.get_product_by_uuid(prod['uuid'])
    product.branding.size.should == 2
  end

  it 'should create new product version when updating branding' do
    b1 = {:productId => 'prodid1',
      :type => 'type1', :name => 'branding1'}

    owner = create_owner random_string('some-owner')
    name = random_string("product-")

    prod = create_product(name, name, :owner => owner['key'], :branding => [ b1 ])
    prod.branding.size.should == 1

    prod = @cp.get_product(owner['key'], prod['id'])
    prod.branding.size.should == 1
    original_prod_uuid = prod.uuid

    b1.name = 'new_branding_name1'
    updated_prod = @cp.update_product(owner['key'], prod['id'], :branding => [ b1 ])
    updated_prod.branding.size.should == 1
    updated_prod_uuid = updated_prod.uuid
    expect(updated_prod.branding[0].name).to eq('new_branding_name1')

    # A new product version should have been created during an update of branding
    expect(updated_prod_uuid).not_to eq(original_prod_uuid)
  end

  it "should able to create product with provided product" do
    owner = create_owner random_string('some-owner')
    provided_product_id1 = "provided_product_id_1"
    provided_product_id2 = "provided_product_id_2"
    product_name = "test_product"

    p1 = create_product(provided_product_id1, random_string("provided1"),  :owner => owner['key'])
    p2 = create_product(provided_product_id2, random_string("provided2"),  :owner => owner['key'])

    prod = create_product(product_name, product_name, :owner => owner['key'],
      :providedProducts => [ "provided_product_id_1", "provided_product_id_2" ])

    product = @cp.get_product(owner['key'], prod['id'])

    expect(product.providedProducts.find { |item| item['id'] == 'provided_product_id_2' })
      .to_not be_nil
    expect(product.providedProducts.find { |item| item['id'] == 'provided_product_id_1' })
      .to_not be_nil
  end

  it "should able to update product with provided products" do
    owner = create_owner random_string('some-owner')
    provided_product_id = "provided_product_id"
    product_name = "test_product"

    create_product(provided_product_id, random_string("provided"),  :owner => owner['key'])
    create_product(product_name, product_name, :owner => owner['key'])

    prod = @cp.update_product(owner['key'], product_name, :providedProducts => [ "provided_product_id" ])

    expect(prod.providedProducts[0].id).to eq('provided_product_id')
  end

  it 'should create new product version when updating provided product' do
    owner = create_owner random_string('some-owner')
    name = random_string("product-")
    provided_product_id = "provided_product_id"
    new_provided_product_id = "new_provided_product_id"

    create_product(provided_product_id, random_string("provided"),  :owner => owner['key'])
    prod = create_product(name, name, :owner => owner['key'], :providedProducts => [ "provided_product_id" ])

    prod.providedProducts.size.should == 1

    prod = @cp.get_product(owner['key'], prod['id'])
    prod.providedProducts.size.should == 1
    original_prod_uuid = prod.uuid

    create_product(new_provided_product_id, random_string("provided"),  :owner => owner['key'])
    updated_prod = @cp.update_product(owner['key'], prod['id'],
      :providedProducts => ["new_provided_product_id" ])

    updated_prod.providedProducts.size.should == 1
    updated_prod_uuid = updated_prod.uuid

    expect(updated_prod.providedProducts[0].id).to eq('new_provided_product_id')

    # A new product version should have been created during an update of provided product
    expect(updated_prod_uuid).not_to eq(original_prod_uuid)
  end

end

