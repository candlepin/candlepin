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

  it 'adds content to products' do
    prod = create_product
    content = create_content
    @cp.add_content_to_product(@owner['key'], prod['id'], content['id'])
    prod = @cp.get_product(@owner['key'], prod['id'])
    prod['productContent'].size.should == 1
  end

  it 'concurrently adds content to products' do
    product = create_product
    content1 = create_content
    content2 = create_content
    content3 = create_content
    content4 = create_content
    content5 = create_content
    content6 = create_content
    content7 = create_content
    content8 = create_content
    content9 = create_content
    content10 = create_content
    t1 = Thread.new{@cp.add_content_to_product(@owner['key'], product['id'], content1['id'])}
    t2 = Thread.new{@cp.add_content_to_product(@owner['key'], product['id'], content2['id'])}
    t3 = Thread.new{@cp.add_content_to_product(@owner['key'], product['id'], content3['id'])}
    t4 = Thread.new{@cp.add_content_to_product(@owner['key'], product['id'], content4['id'])}
    t5 = Thread.new{@cp.add_content_to_product(@owner['key'], product['id'], content5['id'])}
    t6 = Thread.new{@cp.add_content_to_product(@owner['key'], product['id'], content6['id'])}
    t7 = Thread.new{@cp.add_content_to_product(@owner['key'], product['id'], content7['id'])}
    t8 = Thread.new{@cp.add_content_to_product(@owner['key'], product['id'], content8['id'])}
    t9 = Thread.new{@cp.add_content_to_product(@owner['key'], product['id'], content9['id'])}
    t10 = Thread.new{@cp.add_content_to_product(@owner['key'], product['id'], content10['id'])}
    t1.join
    t2.join
    t3.join
    t4.join
    t5.join
    t6.join
    t7.join
    t8.join
    t9.join
    t10.join
    prod = @cp.get_product(@owner['key'], product['id'])
    prod['productContent'].size.should == 10
  end

  it 'concurrently removes content from products' do
    product = create_product
    content1 = create_content
    content2 = create_content
    content3 = create_content
    content4 = create_content
    content5 = create_content
    content6 = create_content
    content7 = create_content
    content8 = create_content
    content9 = create_content
    content10 = create_content
    @cp.add_content_to_product(@owner['key'], product['id'], content1['id'])
    @cp.add_content_to_product(@owner['key'], product['id'], content2['id'])
    @cp.add_content_to_product(@owner['key'], product['id'], content3['id'])
    @cp.add_content_to_product(@owner['key'], product['id'], content4['id'])
    @cp.add_content_to_product(@owner['key'], product['id'], content5['id'])
    @cp.add_content_to_product(@owner['key'], product['id'], content6['id'])
    @cp.add_content_to_product(@owner['key'], product['id'], content7['id'])
    @cp.add_content_to_product(@owner['key'], product['id'], content8['id'])
    @cp.add_content_to_product(@owner['key'], product['id'], content9['id'])
    @cp.add_content_to_product(@owner['key'], product['id'], content10['id'])
    prod = @cp.get_product(@owner['key'], product['id'])
    prod['productContent'].size.should == 10
    t1 = Thread.new{@cp.remove_content_from_product(@owner['key'], product['id'], content1['id'])}
    t2 = Thread.new{@cp.remove_content_from_product(@owner['key'], product['id'], content2['id'])}
    t3 = Thread.new{@cp.remove_content_from_product(@owner['key'], product['id'], content3['id'])}
    t4 = Thread.new{@cp.remove_content_from_product(@owner['key'], product['id'], content4['id'])}
    t5 = Thread.new{@cp.remove_content_from_product(@owner['key'], product['id'], content5['id'])}
    t6 = Thread.new{@cp.remove_content_from_product(@owner['key'], product['id'], content6['id'])}
    t7 = Thread.new{@cp.remove_content_from_product(@owner['key'], product['id'], content7['id'])}
    t8 = Thread.new{@cp.remove_content_from_product(@owner['key'], product['id'], content8['id'])}
    t9 = Thread.new{@cp.remove_content_from_product(@owner['key'], product['id'], content9['id'])}
    t10 = Thread.new{@cp.remove_content_from_product(@owner['key'], product['id'], content10['id'])}
    t1.join
    t2.join
    t3.join
    t4.join
    t5.join
    t6.join
    t7.join
    t8.join
    t9.join
    t10.join
    prod = @cp.get_product(@owner['key'], product['id'])
    prod['productContent'].size.should == 0
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
    pending("candlepin running in standalone mode") if not is_hosted?

    owners = setupOrgProductsAndPools()
    owner1 = owners[0]
    owner2 = owners[1]
    owner3 = owners[2]

    # Override enabled to true:
    job = @cp.refresh_pools_for_product(owner1['key'], "p4")
    job.should_not be_nil
    job['id'].should include("refresh_pools")
    wait_for_job(job['id'], 15)

    job = @cp.refresh_pools_for_product(owner2['key'], "p5d")
    job.should_not be_nil
    job['id'].should include("refresh_pools")
    wait_for_job(job['id'], 15)

    job = @cp.refresh_pools_for_product(owner3['key'], "p1")
    job.should_not be_nil
    job['id'].should include("refresh_pools")
    wait_for_job(job['id'], 15)

    job = @cp.refresh_pools_for_product(owner3['key'], "p3")
    job.should_not be_nil
    job['id'].should include("refresh_pools")
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
end

