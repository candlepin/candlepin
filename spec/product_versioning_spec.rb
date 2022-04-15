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

    prod1 = @cp.create_product(owner1["key"], id, name)
    prod2 = @cp.create_product(owner2["key"], id, name)

    prod1["uuid"].should eq(prod2["uuid"])
  end

  it "creates two distinct product instances when details differ" do
    owner1 = create_owner random_string('test_owner')
    owner2 = create_owner random_string('test_owner')

    id = random_string("product")
    name = "shared_product"

    prod1 = @cp.create_product(owner1["key"], id, name)
    prod2 = @cp.create_product(owner2["key"], id, name + "2")

    prod1["uuid"].should_not eq(prod2["uuid"])
  end

  it "creates a new instance when making changes to an existing instance" do
    # If we enable clustered caching and re-enable the in-place content update branch, this
    # test will need to be updated or removed.

    owner1 = create_owner random_string('test_owner1')

    id = random_string("product")
    name = "shared_product"


    prod1 = @cp.create_product(owner1["key"], id, name)
    expect(prod1).to_not be_nil

    prod2 = @cp.update_product(owner1["key"], id, { :name => "new product name" })
    expect(prod2).to_not be_nil

    expect(prod1["uuid"]).to_not eq(prod2["uuid"])

    # content should now be different from both content1 and 2, and content2 should no longer exist
    prods = @cp.list_products_by_owner(owner1["key"])
    prods.size.should eq(1)
    expect(prods[0]["uuid"]).to_not eq(prod1["uuid"])
    expect(prods[0]["uuid"]).to eq(prod2["uuid"])
  end

  it "creates a new product instance when an org updates a shared instance" do
    owner1 = create_owner random_string('test_owner')
    owner2 = create_owner random_string('test_owner')
    owner3 = create_owner random_string('test_owner')

    id = random_string("product")
    name = "shared_product"

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
    # NOTE: The above will only be true when in-place updates are enabled. When they are disabled
    # (as they are now), the UUIDs will not match, since we're always forking on any change.
    prod4["uuid"].should_not eq(prod1["uuid"]) # ^
    prod4["uuid"].should_not eq(prod2["uuid"]) # ^
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

  it 'should cleanup orphans without interfering with normal actions' do
    # NOTE:
    # This test takes advantage of the immutable nature of products with the in-place update branch
    # disabled. If in-place updates are ever reenabled, we'll need a way to generate large numbers
    # of orphaned products for this test.

    owner1 = create_owner(random_string('test_owner-1'))
    owner2 = create_owner(random_string('test_owner-2'))

    prefix = "test-product-"
    offset = 10000
    length = 100

    # Repeat this test a few(ish) times to hopefully catch any synchronization error
    (1..10).each do
      o1_uuids = []
      o2_uuids = []

      # Create a bunch of dummy products
      (offset..(offset + length - 1)).each do |i|
        id = "#{prefix}#{i}"

        # create product and immediately update it to generate an orphaned product
        @cp.create_product(owner1["key"], id, id)
      end

      # Attempt to update and create new products to get into some funky race conditions with
      # convergence and orphanization
      updater = Thread.new do
        (offset..(offset + length - 1)).each do |i|
          id = "#{prefix}#{i}"
          product = @cp.update_product(owner1["key"], id, { :name => "#{id}-update" })
          o1_uuids << product['uuid']
        end
      end

      generator = Thread.new do
        (offset..(offset + length - 1)).each do |i|
          id = "#{prefix}#{i}"
          product = @cp.create_product(owner2["key"], id, id)
          o2_uuids << product['uuid']
        end
      end

      sleep 1
      job = @cp.trigger_job("OrphanCleanupJob")

      updater.join
      generator.join
      job_status = wait_for_job(job['id'])

      # Verify the orphan cleanup job completed successfully
      expect(job_status).to_not be_nil
      expect(job_status['state'].upcase).to eq('FINISHED')

      # Verify the products created/updated still exist
      o1_uuids.each do |uuid|
        product = @cp.get_product_by_uuid(uuid)
        expect(product).to_not be_nil
      end

      o2_uuids.each do |uuid|
        product = @cp.get_product_by_uuid(uuid)
        expect(product).to_not be_nil
      end

      offset += length
    end
  end

  def ntier_versioning_test(&updater)
    owner = create_owner("test_owner")

    parent_product = nil
    prev_uuids = {}
    uuids = {}

    (1..10).each do |i|
      pid = "tier#{i}_prod-#{rand(100000)}"
      product = @cp.create_product(owner['key'], pid, pid)
      uuids[product['id']] = product['uuid']

      if parent_product != nil
        parent_product = updater.call(owner, parent_product, product)

        prev_uuids.each do |pid, prev_uuid|
          updated_product = @cp.get_product(owner['key'], pid)
          uuids[updated_product['id']] = updated_product['uuid']
        end
      end

      # We expect the size of the uuids collection to grow by 1 every iteration
      expect(uuids.length).to eq(prev_uuids.length + 1)

      # Verify that the uuids are changing every iteration (indicating the version has been recalculated)
      prev_uuids.each do |pid, prev_uuid|
        uuid = uuids[pid]

        expect(uuid).to_not be_nil
        expect(uuid).to_not eq(prev_uuid)
      end

      parent_product = product
      prev_uuids = uuids
      uuids = {}
    end
  end

  it "add and remove content without causing db write issues" do
    owner1 = create_owner random_string('test_owner')
    contents = []
    (1..10).each do |i|
      contents[i] = @cp.create_content(
        owner1["key"],
        "content_name#{i}",
        "content_id#{i}",
        "content_label#{i}",
        "content_type#{i}",
        "content_vendor#{i}")
    end

    # Add and remove content to see if it causes concurrent update issues on the product
    prod1 = @cp.create_product(owner1["key"], random_string("product"), random_string("name"))
    (1..25).each do |w|
      threads = []
      (1..10).each do |i|
        threads[i] = Thread.new do
          @cp.add_content_to_product(owner1["key"], prod1.id, contents[i].id)
        end
      end
      (1..10).each do |i|
        threads[i].join
      end
      prod1 = @cp.get_product(owner1["key"], prod1.id)
      expect(prod1['productContent'].size).to eq(10)

      threads = []
      (1..10).each do |i|
        threads[i] = Thread.new do
          @cp.remove_content_from_product(owner1["key"], prod1.id, contents[i].id)
        end
      end
      (1..10).each do |i|
        threads[i].join
      end
      prod1 = @cp.get_product(owner1["key"], prod1.id)
      expect(prod1['productContent'].size).to eq(0)
    end
  end

  it 'should support n-tier product versioning for provided products' do
    ntier_versioning_test do |owner, parent_product, product|
      @cp.update_product(owner['key'], parent_product['id'], {
        :providedProducts => [product['id']]
      })
    end
  end

  it 'should support n-tier product versioning for derived products' do
    ntier_versioning_test do |owner, parent_product, product|
      @cp.update_product(owner['key'], parent_product['id'], {
        :derivedProduct => product
      })
    end
  end

end
