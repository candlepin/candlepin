require 'date'
require 'spec_helper'
require 'candlepin_scenarios'



describe 'Product Versioning' do

  include CandlepinMethods

  it "creates one product instance when shared by multiple orgs" do
    owner1 = create_owner random_string('test_owner')
    owner2 = create_owner random_string('test_owner')

    id = random_string("product")
    name = "shared_product"

    updated_upstream = Date.today

    prod1 = @cp.create_product(owner1["key"], id, name)
    prod2 = @cp.create_product(owner2["key"], id, name)

    prod1["uuid"].should == prod2["uuid"]
  end

  it "creates two distinct product instances when details differ" do
    owner1 = create_owner random_string('test_owner')
    owner2 = create_owner random_string('test_owner')

    id = random_string("product")
    name = "shared_product"

    updated_upstream = Date.today

    prod1 = @cp.create_product(owner1["key"], id, name)
    prod2 = @cp.create_product(owner2["key"], id, name + "2")

    prod1["uuid"].should_not == prod2["uuid"]
  end

  it "creates a new product instance when an org updates a shared instance" do
    owner1 = create_owner random_string('test_owner')
    owner2 = create_owner random_string('test_owner')
    owner3 = create_owner random_string('test_owner')

    id = random_string("product")
    name = "shared_product"

    updated_upstream = Date.today

    prod1 = @cp.create_product(owner1["key"], id, name)
    prod2 = @cp.create_product(owner2["key"], id, name)
    prod3 = @cp.create_product(owner3["key"], id, name)

    prod1["uuid"].should == prod2["uuid"]
    prod1["uuid"].should == prod3["uuid"]

    prod4 = @cp.update_product(owner2["key"], id, { :name => "new product name" })
    prod4["uuid"].should_not == prod1["uuid"]

    prods = @cp.list_products_by_owner(owner2["key"])
    prods.size.should == 1
    prods[0]["uuid"].should == prod4["uuid"]
    prods[0]["uuid"].should_not == prod2["uuid"]

    prods = @cp.list_products_by_owner(owner1["key"])
    prods.size.should == 1
    prods[0]["uuid"].should == prod1["uuid"]

    prods = @cp.list_products_by_owner(owner3["key"])
    prods.size.should == 1
    prods[0]["uuid"].should == prod3["uuid"]
  end

  it "converges products when a given version already exists" do
    owner1 = create_owner random_string('test_owner1')
    owner2 = create_owner random_string('test_owner2')
    owner3 = create_owner random_string('test_owner3')

    id = random_string("product")
    name = "shared_product"

    updated_upstream = Date.today

    prod1 = @cp.create_product(owner1["key"], id, name)
    prod2 = @cp.create_product(owner2["key"], id, name)
    prod3 = @cp.create_product(owner3["key"], id, "differing product name")

    prod1["uuid"].should == prod2["uuid"]
    prod2["uuid"].should_not == prod3["uuid"]

    prod4 = @cp.update_product(owner3["key"], id, { :name => name })

    prod4["uuid"].should == prod1["uuid"]

    prods = @cp.list_products_by_owner(owner1["key"])
    prods.size.should == 1
    prods[0]["uuid"].should == prod1["uuid"]

    prods = @cp.list_products_by_owner(owner2["key"])
    prods.size.should == 1
    prods[0]["uuid"].should == prod2["uuid"]

    prods = @cp.list_products_by_owner(owner3["key"])
    prods.size.should == 1
    prods[0]["uuid"].should == prod1["uuid"]
    prods[0]["uuid"].should_not == prod3["uuid"]
  end

  it "deletes products without affecting other orgs" do
    owner1 = create_owner random_string('test_owner')
    owner2 = create_owner random_string('test_owner')

    id = random_string("product")
    name = "shared_product"

    updated_upstream = Date.today

    prod1 = @cp.create_product(owner1["key"], id, name)
    prod2 = @cp.create_product(owner2["key"], id, name)

    prod1["uuid"].should == prod2["uuid"]

    @cp.delete_product(owner1["key"], id)

    prods = @cp.list_products_by_owner(owner1["key"])
    prods.size.should == 0

    lambda do
      @cp.get_product(owner1["key"], id)
    end.should raise_exception(RestClient::ResourceNotFound)

    prods = @cp.list_products_by_owner(owner2["key"])
    prods.size.should == 1
    prods[0]["uuid"].should == prod2["uuid"]
  end

  it "creates new products when adding content to shared products" do
    owner1 = create_owner random_string('test_owner')
    owner2 = create_owner random_string('test_owner')

    id = random_string("product")
    name = "shared_product"

    updated_upstream = Date.today

    prod1 = @cp.create_product(owner1["key"], id, name)
    prod2 = @cp.create_product(owner2["key"], id, name)

    prod1["uuid"].should == prod2["uuid"]


    content_id = random_string("content")
    content_name = "shared_content"
    content_label = "shared content"
    content_type = "shared_content_type"
    content_vendor = "generous vendor"

    content1 = @cp.create_content(
        owner1["key"], content_name, content_id, content_label, content_type, content_vendor
    )

    prod3 = @cp.add_content_to_product(owner1["key"], id, content_id)
    prod3["uuid"].should_not == prod1["uuid"]
    prod3["uuid"].should_not == prod2["uuid"]

    prods = @cp.list_products_by_owner(owner1["key"])
    prods.size.should == 1
    prods[0]["uuid"].should == prod3["uuid"]
    prods[0]["uuid"].should_not == prod1["uuid"]
    prods[0]["uuid"].should_not == prod2["uuid"]

    prods = @cp.list_products_by_owner(owner2["key"])
    prods.size.should == 1
    prods[0]["uuid"].should == prod2["uuid"]
    prods[0]["uuid"].should_not == prod3["uuid"]
  end

  it "creates new products when updating content used by shared products" do
    owner1 = create_owner random_string('test_owner')
    owner2 = create_owner random_string('test_owner')

    id = random_string("product")
    name = "shared_product"

    updated_upstream = Date.today

    prod1 = @cp.create_product(owner1["key"], id, name)
    prod2 = @cp.create_product(owner2["key"], id, name)

    prod1["uuid"].should == prod2["uuid"]


    content_id = random_string("content")
    content_name = "shared_content"
    content_label = "shared content"
    content_type = "shared_content_type"
    content_vendor = "generous vendor"

    content1 = @cp.create_content(
        owner1["key"], content_name, content_id, content_label, content_type, content_vendor
    )
    content2 = @cp.create_content(
        owner2["key"], content_name, content_id, content_label, content_type, content_vendor
    )

    prod3 = @cp.add_content_to_product(owner1["key"], id, content_id)
    prod4 = @cp.add_content_to_product(owner2["key"], id, content_id)
    prod3["uuid"].should_not == prod1["uuid"]
    prod3["uuid"].should_not == prod2["uuid"]
    prod4["uuid"].should_not == prod1["uuid"]
    prod4["uuid"].should_not == prod2["uuid"]
    prod3["uuid"].should == prod4["uuid"]

    prods = @cp.list_products_by_owner(owner1["key"])
    prods.size.should == 1
    prods[0]["uuid"].should == prod3["uuid"]
    prods[0]["uuid"].should_not == prod1["uuid"]
    prods[0]["uuid"].should_not == prod2["uuid"]

    prods = @cp.list_products_by_owner(owner2["key"])
    prods.size.should == 1
    prods[0]["uuid"].should == prod4["uuid"]
    prods[0]["uuid"].should_not == prod1["uuid"]
    prods[0]["uuid"].should_not == prod2["uuid"]

    # Actual test starts here
    content3 = @cp.update_content(owner1["key"], content_id, {:label => "new label"})

    prods = @cp.list_products_by_owner(owner1["key"])
    prods.size.should == 1
    prods[0]["uuid"].should_not == prod1["uuid"]
    prods[0]["uuid"].should_not == prod2["uuid"]
    prods[0]["uuid"].should_not == prod3["uuid"]
    prods[0]["uuid"].should_not == prod4["uuid"]

    prods = @cp.list_products_by_owner(owner2["key"])
    prods.size.should == 1
    prods[0]["uuid"].should == prod4["uuid"]
    prods[0]["uuid"].should_not == prod1["uuid"]
    prods[0]["uuid"].should_not == prod2["uuid"]
  end

  # TODO ? :
  # - Updating a shared product used by another resource properly links the new product

end

