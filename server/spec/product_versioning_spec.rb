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

    prod1["uuid"].should eq(prod2["uuid"])
  end

  it "creates two distinct product instances when details differ" do
    owner1 = create_owner random_string('test_owner')
    owner2 = create_owner random_string('test_owner')

    id = random_string("product")
    name = "shared_product"

    updated_upstream = Date.today

    prod1 = @cp.create_product(owner1["key"], id, name)
    prod2 = @cp.create_product(owner2["key"], id, name + "2")

    prod1["uuid"].should_not eq(prod2["uuid"])
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

    prod1["uuid"].should eq(prod2["uuid"])
    prod1["uuid"].should eq(prod3["uuid"])

    prod4 = @cp.update_product(owner2["key"], id, { :name => "new product name" })
    prod4["uuid"].should_not eq(prod1["uuid"])

    prods = @cp.list_products_by_owner(owner2["key"])
    prods.size.should eq(1)
    prods[0]["uuid"].should eq(prod4["uuid"])
    prods[0]["uuid"].should_not eq(prod2["uuid"])

    prods = @cp.list_products_by_owner(owner1["key"])
    prods.size.should eq(1)
    prods[0]["uuid"].should eq(prod1["uuid"])

    prods = @cp.list_products_by_owner(owner3["key"])
    prods.size.should eq(1)
    prods[0]["uuid"].should eq(prod3["uuid"])
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

    prod1["uuid"].should eq(prod2["uuid"])
    prod2["uuid"].should_not eq(prod3["uuid"])

    prod4 = @cp.update_product(owner3["key"], id, { :name => name })

    prod4["uuid"].should eq(prod1["uuid"])

    prods = @cp.list_products_by_owner(owner1["key"])
    prods.size.should eq(1)
    prods[0]["uuid"].should eq(prod1["uuid"])

    prods = @cp.list_products_by_owner(owner2["key"])
    prods.size.should eq(1)
    prods[0]["uuid"].should eq(prod2["uuid"])

    prods = @cp.list_products_by_owner(owner3["key"])
    prods.size.should eq(1)
    prods[0]["uuid"].should eq(prod1["uuid"])
    prods[0]["uuid"].should_not eq(prod3["uuid"])
  end

  it "deletes products without affecting other orgs" do
    owner1 = create_owner random_string('test_owner')
    owner2 = create_owner random_string('test_owner')

    id = random_string("product")
    name = "shared_product"

    updated_upstream = Date.today

    prod1 = @cp.create_product(owner1["key"], id, name)
    prod2 = @cp.create_product(owner2["key"], id, name)

    prod1["uuid"].should eq(prod2["uuid"])

    @cp.delete_product(owner1["key"], id)

    prods = @cp.list_products_by_owner(owner1["key"])
    prods.size.should eq(0)

    lambda do
      @cp.get_product(owner1["key"], id)
    end.should raise_exception(RestClient::ResourceNotFound)

    prods = @cp.list_products_by_owner(owner2["key"])
    prods.size.should eq(1)
    prods[0]["uuid"].should eq(prod2["uuid"])
  end

  it "creates new products when adding content to shared products" do
    owner1 = create_owner random_string('test_owner')
    owner2 = create_owner random_string('test_owner')

    id = random_string("product")
    name = "shared_product"

    updated_upstream = Date.today

    prod1 = @cp.create_product(owner1["key"], id, name)
    prod2 = @cp.create_product(owner2["key"], id, name)

    prod1["uuid"].should eq(prod2["uuid"])


    content_id = random_string("content")
    content_name = "shared_content"
    content_label = "shared content"
    content_type = "shared_content_type"
    content_vendor = "generous vendor"

    content1 = @cp.create_content(
        owner1["key"], content_name, content_id, content_label, content_type, content_vendor
    )

    prod3 = @cp.add_content_to_product(owner1["key"], id, content_id)
    prod3["uuid"].should_not eq(prod1["uuid"])
    prod3["uuid"].should_not eq(prod2["uuid"])

    prods = @cp.list_products_by_owner(owner1["key"])
    prods.size.should eq(1)
    prods[0]["uuid"].should eq(prod3["uuid"])
    prods[0]["uuid"].should_not eq(prod1["uuid"])
    prods[0]["uuid"].should_not eq(prod2["uuid"])

    prods = @cp.list_products_by_owner(owner2["key"])
    prods.size.should eq(1)
    prods[0]["uuid"].should eq(prod2["uuid"])
    prods[0]["uuid"].should_not eq(prod3["uuid"])
  end

  it "creates new products when updating content used by shared products" do
    owner1 = create_owner random_string('test_owner')
    owner2 = create_owner random_string('test_owner')

    id = random_string("product")
    name = "shared_product"

    updated_upstream = Date.today

    prod1 = @cp.create_product(owner1["key"], id, name)
    prod2 = @cp.create_product(owner2["key"], id, name)

    prod1["uuid"].should eq(prod2["uuid"])


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

    content1["uuid"].should eq(content2["uuid"])

    prod3 = @cp.add_content_to_product(owner1["key"], id, content_id)
    prod4 = @cp.add_content_to_product(owner2["key"], id, content_id)
    prod3["uuid"].should_not eq(prod1["uuid"])
    prod3["uuid"].should_not eq(prod2["uuid"])
    prod4["uuid"].should_not eq(prod1["uuid"])
    prod4["uuid"].should_not eq(prod2["uuid"])
    prod3["uuid"].should eq(prod4["uuid"])

    prods = @cp.list_products_by_owner(owner1["key"])
    prods.size.should eq(1)
    prods[0]["uuid"].should eq(prod3["uuid"])
    prods[0]["uuid"].should_not eq(prod1["uuid"])
    prods[0]["uuid"].should_not eq(prod2["uuid"])

    prods = @cp.list_products_by_owner(owner2["key"])
    prods.size.should eq(1)
    prods[0]["uuid"].should eq(prod4["uuid"])
    prods[0]["uuid"].should_not eq(prod1["uuid"])
    prods[0]["uuid"].should_not eq(prod2["uuid"])

    # Actual test starts here
    content3 = @cp.update_content(owner1["key"], content_id, {:label => "new label"})
    content3["uuid"].should_not eq(content1["uuid"])
    content3["uuid"].should_not eq(content2["uuid"])

    prods = @cp.list_products_by_owner(owner1["key"])
    prods.size.should eq(1)
    prods[0]["uuid"].should_not eq(prod1["uuid"])
    prods[0]["uuid"].should_not eq(prod2["uuid"])
    prods[0]["uuid"].should_not eq(prod3["uuid"])
    prods[0]["uuid"].should_not eq(prod4["uuid"])

    prods = @cp.list_products_by_owner(owner2["key"])
    prods.size.should eq(1)
    prods[0]["uuid"].should eq(prod4["uuid"])
    prods[0]["uuid"].should_not eq(prod1["uuid"])
    prods[0]["uuid"].should_not eq(prod2["uuid"])
  end

  it "creates new products when removing content used by shared products" do
    owner1 = create_owner random_string('test_owner')
    owner2 = create_owner random_string('test_owner')

    id = random_string("product")
    name = "shared_product"

    updated_upstream = Date.today

    prod1 = @cp.create_product(owner1["key"], id, name)
    prod2 = @cp.create_product(owner2["key"], id, name)

    prod1["uuid"].should eq(prod2["uuid"])


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
    prod3["uuid"].should_not eq(prod1["uuid"])
    prod3["uuid"].should_not eq(prod2["uuid"])
    prod4["uuid"].should_not eq(prod1["uuid"])
    prod4["uuid"].should_not eq(prod2["uuid"])
    prod4["uuid"].should eq(prod3["uuid"])

    prods = @cp.list_products_by_owner(owner1["key"])
    prods.size.should eq(1)
    prods[0]["uuid"].should eq(prod3["uuid"])
    prods[0]["uuid"].should_not eq(prod1["uuid"])
    prods[0]["uuid"].should_not eq(prod2["uuid"])

    prods = @cp.list_products_by_owner(owner2["key"])
    prods.size.should eq(1)
    prods[0]["uuid"].should_not eq(prod1["uuid"])
    prods[0]["uuid"].should_not eq(prod2["uuid"])
    prods[0]["uuid"].should eq(prod4["uuid"])

    # Actual test starts here
    content3 = @cp.delete_content(owner1["key"], content_id)

    prods = @cp.list_products_by_owner(owner1["key"])
    prods.size.should eq(1)

    # This is an interesting case. Due to our current implementation, these will be equal, but it's
    # by coincedence, not intent. At the time of writing, it is not a requirement for a given state
    # to have a defined version/hash, but it does. As a result, we can't test that they shouldn't
    # be equal (because they will be), but testing that they are equal is wrong. We'll just comment
    # these out and leave this message here for future maintaners.
    # prods[0]["uuid"].should_not eq(prod1["uuid"])
    # prods[0]["uuid"].should_not eq(prod2["uuid"])
    prods[0]["uuid"].should_not eq(prod3["uuid"])
    prods[0]["uuid"].should_not eq(prod4["uuid"])

    prods = @cp.list_products_by_owner(owner2["key"])
    prods.size.should eq(1)
    prods[0]["uuid"].should_not eq(prod1["uuid"])
    prods[0]["uuid"].should_not eq(prod2["uuid"])
    prods[0]["uuid"].should eq(prod3["uuid"])
    prods[0]["uuid"].should eq(prod4["uuid"])
  end

  it "converges identical products when converging content" do
    owner1 = create_owner random_string('test_owner-1')
    owner2 = create_owner random_string('test_owner-2')

    id = random_string("product")
    name = "shared_product"

    updated_upstream = Date.today

    prod1 = @cp.create_product(owner1["key"], id, name)
    prod2 = @cp.create_product(owner2["key"], id, name)

    prod1["uuid"].should eq(prod2["uuid"])

    content_id = random_string("content")
    content_name = "shared_content"
    content_label = "shared content"
    content_type = "shared_content_type"
    content_vendor = "generous vendor"

    content1 = @cp.create_content(
        owner1["key"], content_name, content_id, content_label, content_type, content_vendor
    )
    content2 = @cp.create_content(
        owner2["key"], content_name, content_id, "different label", content_type, content_vendor
    )

    content1["uuid"].should_not eq(content2["uuid"])

    prod3 = @cp.add_content_to_product(owner1["key"], id, content_id)
    prod4 = @cp.add_content_to_product(owner2["key"], id, content_id)
    prod3["uuid"].should_not eq(prod1["uuid"])
    prod3["uuid"].should_not eq(prod2["uuid"])

    # Another interesting case here. While it may appear that these should be different, the
    # addition of the content to prod1 triggered the generation of a new product for org1 (prod3),
    # leaving org2 as the only owner for prod2. That being the case, prod2 was updated in-place,
    # allowing it to retain its UUID, and make this test look strange
    prod4["uuid"].should eq(prod1["uuid"])
    prod4["uuid"].should eq(prod2["uuid"])
    prod4["uuid"].should_not eq(prod3["uuid"])

    # Actual test starts here
    content3 = @cp.update_content(owner2["key"], content_id, {:label => content_label})
    content3["uuid"].should eq(content1["uuid"])
    content3["uuid"].should_not eq(content2["uuid"])

    prods = @cp.list_products_by_owner(owner1["key"])
    prods.size.should eq(1)
    prod5 = prods[0]
    prod5["uuid"].should_not eq(prod1["uuid"])
    prod5["uuid"].should_not eq(prod2["uuid"])
    prod5["uuid"].should eq(prod3["uuid"])
    prod5["uuid"].should_not eq(prod4["uuid"])

    prods = @cp.list_products_by_owner(owner2["key"])
    prods.size.should eq(1)
    prod6 = prods[0]
    prod6["uuid"].should_not eq(prod1["uuid"])
    prod6["uuid"].should_not eq(prod2["uuid"])
    prod6["uuid"].should eq(prod3["uuid"])
    prod6["uuid"].should_not eq(prod4["uuid"])
    prod6["uuid"].should eq(prod5["uuid"])
  end

end
