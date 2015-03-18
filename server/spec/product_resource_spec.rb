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

  it 'throws exception on write operation' do
    lambda do
      @cp.post("/products", {})
    end.should raise_exception(RestClient::BadRequest)

    lambda do
      @cp.put("/products/dummyid", {})
    end.should raise_exception(RestClient::BadRequest)

    lambda do
      @cp.post("/products/dummyid/batch_content", {})
    end.should raise_exception(RestClient::BadRequest)

    lambda do
      @cp.post("/products/dummyid/content/contentid", {})
    end.should raise_exception(RestClient::BadRequest)

    lambda do
      @cp.delete("/products/dummyid")
    end.should raise_exception(RestClient::BadRequest)

    lambda do
      @cp.put("/products/dummyid/subscriptions", {})
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
    prod_ids_to_get = [prod1_id, prod2_id]

    # Get 2 products
    bulk_get_products = @cp.list_products(prod_ids_to_get)
    bulk_get_products.size.should == 2

    # Make sure it got the correct ones
    if (bulk_get_products[0]['id'] == prod1_id)
      bulk_get_products[1]['id'].should == prod2_id
    else
      bulk_get_products[0]['id'].should == prod2_id
      bulk_get_products[1]['id'].should == prod1_id
    end
  end

end

