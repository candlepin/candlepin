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

  it 'updates individual product fields' do
    prod = create_product(nil, 'tacos', {:multiplier => 2, :dependentProductIds => [2, 4]})
    #Ensure the dates are at least one second different
    sleep 1
    prod2 = create_product(nil, 'enchiladas', {:multiplier => 4})

    prod.name.should_not == prod2.name
    prod.multiplier.should_not == prod2.multiplier
    prod.attributes.should_not == prod2.attributes
    prod.dependentProductIds.should_not == prod2.dependentProductIds

    prod = @cp.update_product(@owner['key'], prod.id, {:name => 'enchiladas'})

    prod.name.should == prod2.name
    prod.multiplier.should_not == prod2.multiplier
    prod.attributes.should_not == prod2.attributes
    prod.dependentProductIds.should_not == prod2.dependentProductIds

    #the idea here is attributes should not change if set equal to nil
    #then updated, so store it as a temp variable to compare to after
    #update_product is called.
    temp_attributes = prod.attributes

    prod = @cp.update_product(@owner['key'], prod.id, {:multiplier => prod2.multiplier, :attributes => nil})

    prod.multiplier.should == prod2.multiplier
    prod.attributes.should == temp_attributes

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
    product = create_product(random_string("test_id"), random_string("test_name"),
        {:owner => owner['key']})
    provided_product = create_product(nil, nil, {:owner => owner['key']})
    create_pool_and_subscription(owner['key'], product.id, 10, [provided_product.id])
    user = user_client(owner, random_string('billy'))
    system = consumer_client(user, 'system6')
    system.consume_product(product.id)
    product_owners = @cp.get_product_owners([provided_product.id])
    product_owners.should have(1).things
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
    pool.should have(1).things

    pool = owner_client.list_pools(:owner => owner.id)
    pool.should have(1).things
    pool[0]['owner']['key'].should == owner['key']

    pool = owner2_client.list_pools(:owner => owner2.id)
    pool.should have(0).things
  end

  it 'lists all products in bulk fetch' do
    prod1_id = random_string("test_id")
    prod2_id = random_string("test_id")
    prod3_id = random_string("test_id")
    prod1 = create_product(prod1_id, random_string("test_name"))
    prod2 = create_product(prod2_id, random_string("test_name"))
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

  it 'should return correct exception for contraint violations' do
    lambda {
      prod = create_product(random_string("test_id"),
                          random_string("test_name"),
                          {:attributes => {:support_level => "a" * 256}})
    }.should raise_exception(RestClient::BadRequest)
  end

  it 'bad request on attempt to delete product attached to sub' do
    lambda do
      @cp.delete_product(@owner['key'], @product.id)
    end.should raise_exception(RestClient::BadRequest)
  end

  it 'bad request on attempt to delete provided product attached to sub' do
    lambda do
      @cp.delete_product(@owner['key'], @prov_product.id)
    end.should raise_exception(RestClient::BadRequest)
  end

  it 'bad request on attempt to delete derived product attached to sub' do
    lambda do
      @cp.delete_product(@owner['key'], @derived_product.id)
    end.should raise_exception(RestClient::BadRequest)
  end

  it 'bad request on attempt to delete derived provided product attached to sub' do
    lambda do
      @cp.delete_product(@owner['key'], @derived_prov_product.id)
    end.should raise_exception(RestClient::BadRequest)
  end


end

