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

    create_pool_and_subscription(@owner['key'], @product.id,
      10, [@prov_product.id], '222', '', '', nil, nil, false,
      {
        :derived_product_id => @derived_product.id,
        :derived_provided_products => [@derived_prov_product.id]
      })
  end

  it 'throws exception on write operations' do
    lambda do
      @cp.post("/products", {})
    end.should raise_exception(RestClient::BadRequest)

    lambda do
      @cp.put("/products/#{@product.id}", {})
    end.should raise_exception(RestClient::BadRequest)

    lambda do
      @cp.post("/products/#{@product.id}/batch_content", {})
    end.should raise_exception(RestClient::BadRequest)

    lambda do
      @cp.post("/products/#{@product.id}/content/contentid", {})
    end.should raise_exception(RestClient::BadRequest)

    lambda do
      @cp.delete("/products/#{@product.id}")
    end.should raise_exception(RestClient::BadRequest)

    # This may look like a read operation, but it generates the certificate if it doesn't exist;
    # making this a write operation at times.
    lambda do
      @cp.get("/products/#{@product.id}/certificate")
    end.should raise_exception(RestClient::BadRequest)
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

  it 'retrieves owners by product' do
    owners = setupOrgProductsAndPools()
    owner1 = owners[0]
    owner2 = owners[1]
    owner3 = owners[2]

    result = @cp.get_owners_with_product(["p4"])
    result.should_not be_nil
    result.length.should == 1
    result[0]['key'].should == owner1['key']

    result = @cp.get_owners_with_product(["p5d"])
    result.should_not be_nil
    result.length.should == 1
    result[0]['key'].should == owner2['key']

    result = @cp.get_owners_with_product(["p1"])
    result.should_not be_nil
    result.length.should == 3

    [owner1, owner2, owner3].each do |owner|
      found = false
      result.each do |recv|
        if recv['key'] == owner['key'] then
          found = true
          break
        end
      end

      found.should == true
    end

    result = @cp.get_owners_with_product(["p3"])
    result.should_not be_nil
    result.length.should == 2

    [owner2, owner3].each do |owner|
      found = false
      result.each do |recv|
        if recv['key'] == owner['key'] then
          found = true
          break
        end
      end

      found.should == true
    end

    result = @cp.get_owners_with_product(["p4", "p6"])
    result.should_not be_nil
    result.length.should == 2

    [owner1, owner3].each do |owner|
      found = false
      result.each do |recv|
        if recv['key'] == owner['key'] then
          found = true
          break
        end
      end

      found.should == true
    end

    result = @cp.get_owners_with_product(["nope"])
    result.should_not be_nil
    result.length.should == 0
  end

  it "refreshes pools for orgs owning products" do
    owners = setupOrgProductsAndPools()
    owner1 = owners[0]
    owner2 = owners[1]
    owner3 = owners[2]

    # Override enabled to true:
    jobs = @cp.refresh_pools_for_orgs_with_product(["p4"])
    jobs.length.should == 1
    jobs.each do |job|
      job['id'].should include("refresh_pools")
      wait_for_job(job['id'], 15)
    end

    jobs = @cp.refresh_pools_for_orgs_with_product(["p5d"])
    jobs.length.should == 1
    jobs.each do |job|
      job['id'].should include("refresh_pools")
      wait_for_job(job['id'], 15)
    end

    jobs = @cp.refresh_pools_for_orgs_with_product(["p1"])
    jobs.length.should == 3
    jobs.each do |job|
      job['id'].should include("refresh_pools")
      wait_for_job(job['id'], 15)
    end

    jobs = @cp.refresh_pools_for_orgs_with_product(["p3"])
    jobs.length.should == 2
    jobs.each do |job|
      job['id'].should include("refresh_pools")
      wait_for_job(job['id'], 15)
    end

    jobs = @cp.refresh_pools_for_orgs_with_product(["p4", "p6"])
    jobs.length.should == 2
    jobs.each do |job|
      job['id'].should include("refresh_pools")
      wait_for_job(job['id'], 15)
    end

    jobs = @cp.refresh_pools_for_orgs_with_product(["nope"])
    jobs.length.should == 0
  end

  it 'throws exception on get_owners with no products' do
    lambda do
      @cp.get("/products/owners")
    end.should raise_exception(RestClient::BadRequest)
  end

  it 'throws exception on refresh with no products' do
    lambda do
      @cp.put("/products/subscriptions", {})
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

