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

  it "censors owner information on owner-agnostic retrieval" do
    prod_id = "test_prod"

    owner1 = create_owner(random_string("test_owner_1"))
    owner2 = create_owner(random_string("test_owner_2"))
    owner3 = create_owner(random_string("test_owner_3"))

    prod1 = create_product(prod_id, "test product", {:owner => owner1['key']})
    prod2 = create_product(prod_id, "test product", {:owner => owner2['key']})
    prod3 = create_product(prod_id, "test product", {:owner => owner3['key']})

    result = @cp.get("/products/#{prod_id}")
    result.should_not be_nil

    result["id"].should == prod_id
    result["owner"].should be_nil
  end

  it "censors owner information for owner-agnostic statistics" do
    @cp.generate_statistics()

    result = @cp.get("/products/#{@product.id}/statistics")
    result.should_not be_nil
    result.length.should == 3

    result.each do |stats|
      stats['ownerId'].should be_nil
    end
  end

end

