require 'spec_helper'
require 'candlepin_scenarios'

describe 'Product Resource' do

  include CandlepinMethods

  before do
    @owner = create_owner random_string('test_owner')
    @product = create_product random_string('product')
    @prov_product = create_product random_string('provided_product')
    @derived_product = create_product random_string('derived_product')
    @derived_prov_product = create_product random_string('derived_provided_product')
    
    @cp.create_subscription(@owner['key'], @product.id,
      10, [@prov_product.id], '222', '', '', nil, nil,
      {
        'derived_product_id' => @derived_product.id,
        'derived_provided_products' => [@derived_prov_product.id]
      })

  end

  it 'updates individual product fields' do
    prod = create_product(nil, 'tacos', {:multiplier => 2, :dependentProductIds => [2, 4]})
    prod2 = create_product(nil, 'enchiladas', {:multiplier => 4})

    prod.name.should_not == prod2.name
    prod.multiplier.should_not == prod2.multiplier
    prod.attributes.should_not == prod2.attributes
    prod.dependentProductIds.should_not == prod2.dependentProductIds

    prod = @cp.update_product(prod.id, {:name => 'enchiladas'})

    prod.name.should == prod2.name
    prod.multiplier.should_not == prod2.multiplier
    prod.attributes.should_not == prod2.attributes
    prod.dependentProductIds.should_not == prod2.dependentProductIds

    #the idea here is attributes should not change if set equal to nil
    #then updated, so store it as a temp variable to compare to after
    #update_product is called.
    temp_attributes = prod.attributes

    prod = @cp.update_product(prod.id, {:multiplier => prod2.multiplier, :attributes => nil})

    prod.multiplier.should == prod2.multiplier
    prod.attributes.should == temp_attributes

    prod = @cp.update_product(prod.id, {:dependentProductIds => prod2.dependentProductIds})

    prod.dependentProductIds.should == prod2.dependentProductIds
  end

  it 'does not update product name' do
    prod = create_product(nil, 'iron maiden')
    prod2 = create_product(nil, nil)

    prod.name.should == 'iron maiden'

    prod = @cp.update_product(prod.id, prod2)

    prod.name.should == 'iron maiden'
  end

  it 'removes content from products.' do
    prod = create_product
    content = create_content
    @cp.add_content_to_product(prod['id'], content['id'])
    prod = @cp.get_product(prod['id'])
    prod['productContent'].size.should == 1

    @cp.remove_content_from_product(prod['id'], content['id'])
    prod = @cp.get_product(prod['id'])
    prod['productContent'].should be_empty
  end

  it 'allows regular users to view products' do
    owner = create_owner random_string('test')
    user_cp = user_client(owner, random_string('testuser'), true)
    prod = create_product
    user_cp.get_product(prod['id'])
  end

  it 'create two products with the same name' do
    product1 = create_product(nil, 'doppelganger')
    product2 = create_product(nil, 'doppelganger')
    product1.id.should_not  == product2.id
    product1.name.should == product2.name
  end

  it 'retrieves the owners of an active product' do
    owner = create_owner(random_string('owner'))
    product = create_product(random_string("test_id"),
      random_string("test_name"))
    provided_product = create_product()
    @cp.create_subscription(owner['key'], product.id, 10, [provided_product.id])
    @cp.refresh_pools(owner['key'])
    user = user_client(owner, random_string('billy'))
    system = consumer_client(user, 'system6')
    system.consume_product(product.id)
    product_owners = @cp.get_product_owners([provided_product.id])
    product_owners.should have(1).things
    product_owners[0]['key'].should == owner['key']
  end

  it 'refreshes pools for specific products' do
    owner = create_owner(random_string('owner'))
    owner_client = user_client(owner, random_string('testuser'))
    product = create_product(random_string("test_id"),
      random_string("test_name"))
    provided_product = create_product()
    @cp.create_subscription(owner['key'], product.id, 10, [provided_product.id])
    pool = owner_client.list_pools(:owner => owner.id)
    pool.should have(0).things
    @cp.refresh_pools_for_product(product.id)
    pool = owner_client.list_pools(:owner => owner.id)
    pool.should have(1).things
    pool[0]['owner']['key'].should == owner['key']
  end

  it 'does not refresh pools without a given product' do
    owner = create_owner(random_string('owner'))
    owner_client = user_client(owner, random_string('testuser'))
    product = create_product(random_string("test_id"),
      random_string("test_name"))
    product2 = create_product()
    provided_product = create_product()
    @cp.create_subscription(owner['key'], product.id, 10, [provided_product.id])
    pool = owner_client.list_pools(:owner => owner.id)
    pool.should have(0).things
    @cp.refresh_pools_for_product(product2.id)
    pool = owner_client.list_pools(:owner => owner.id)
    pool.should have(0).things
  end

  it 'refreshes pools for specific provided products' do
    owner = create_owner(random_string('owner'))
    owner_client = user_client(owner, random_string('testuser'))
    product = create_product(random_string("test_id"),
      random_string("test_name"))
    provided_product = create_product()
    @cp.create_subscription(owner['key'], product.id, 10, [provided_product.id])
    pool = owner_client.list_pools(:owner => owner.id)
    pool.should have(0).things
    @cp.refresh_pools_for_product(provided_product.id)
    pool = owner_client.list_pools(:owner => owner.id)
    pool.should have(1).things
    pool[0]['owner']['key'].should == owner['key']
  end

  it 'creates products with relies on relationships' do
    relies_on = ['ProductA','ProductB','ProductC']
    prod = create_product(random_string("test_id"),
                          random_string("test_name"),
                          {:relies_on => relies_on})
    prod = @cp.get_product(prod['id'])
    prod['reliesOn'].size.should == 3
  end

  it 'adds and removes relies on relationships' do
    relies_on = ['ProductA','ProductB','ProductC']
    prod = create_product(random_string("test_id"),
                          random_string("test_name"),
                          {:relies_on => relies_on})
    @cp.remove_product_reliance(prod['id'], 'ProductB')
    @cp.remove_product_reliance(prod['id'], 'ProductC')
    prod = @cp.get_product(prod['id'])
    prod['reliesOn'].first.should == 'ProductA'

    @cp.add_product_reliance(prod['id'], 'ProductD')
    prod = @cp.get_product(prod['id'])
    prod['reliesOn'].size.should == 2
  end

  it 'rejects product creation with circular relies on relationships' do
    prod1_id = random_string("test_id")
    prod2_id = random_string("test_id")
    prod3_id = random_string("test_id")
    prod1 = create_product(prod1_id,
                          random_string("test_name"),
                          {:relies_on => [prod2_id]})
    prod2 = create_product(prod2_id,
                          random_string("test_name"),
                          {:relies_on => [prod3_id]})
    lambda do
        prod3 = create_product(prod3_id,
                          random_string("test_name"),
                          {:relies_on => [prod1_id]})
    end.should raise_exception(RestClient::BadRequest)
  end

  it 'lists all products in bulk fetch' do
    prod1_id = random_string("test_id")
    prod2_id = random_string("test_id")
    prod3_id = random_string("test_id")
    prod1 = create_product(prod1_id, random_string("test_name"))
    prod2 = create_product(prod2_id, random_string("test_name"))
    prod3 = create_product(prod3_id, random_string("test_name"))
    all_products = @cp.list_products()
    all_products.size.should > 2

    # Pick two products to use in a bulk get
    first_prod_id = all_products[0]['id']
    second_prod_id = all_products[1]['id']
    prod_ids_to_get = [first_prod_id, second_prod_id]

    # Get 2 products
    bulk_get_products = @cp.list_products(prod_ids_to_get)
    bulk_get_products.size.should == 2

    # Make sure it got the correct ones
    prod_ids_to_get.index(bulk_get_products[0]['id']).should_not == nil
    prod_ids_to_get.index(bulk_get_products[1]['id']).should_not == nil
  end

  it 'should return correct exception for contraint violations' do
    lambda {
      prod = create_product(random_string("test_id"),
                          random_string("test_name"),
                          {:attributes => {:support_level => "a" * 256}})
    }.should raise_exception(RestClient::BadRequest)
  end

  it 'bad request on attempt to delete product attached to sub' do
    lambda do
      @cp.delete_product(@product.id)
    end.should raise_exception(RestClient::BadRequest)
  end

  it 'bad request on attempt to delete provided product attached to sub' do
    lambda do
      @cp.delete_product(@prov_product.id)
    end.should raise_exception(RestClient::BadRequest)
  end

  it 'bad request on attempt to delete derived product attached to sub' do
    lambda do
      @cp.delete_product(@derived_product.id)
    end.should raise_exception(RestClient::BadRequest)
  end

  it 'bad request on attempt to delete derived provided product attached to sub' do
    lambda do
      @cp.delete_product(@derived_prov_product.id)
    end.should raise_exception(RestClient::BadRequest)
  end


end

