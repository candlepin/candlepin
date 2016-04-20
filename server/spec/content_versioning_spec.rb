require 'date'
require 'spec_helper'
require 'candlepin_scenarios'



describe 'Content Versioning' do

  include CandlepinMethods

  it "creates one content instance when shared by multiple orgs" do
    owner1 = create_owner random_string('test_owner')
    owner2 = create_owner random_string('test_owner')

    id = random_string("content")
    name = "shared_content"
    label = "shared content"
    type = "shared_content_type"
    vendor = "generous vendor"
    updated_upstream = Date.today

    content1 = @cp.create_content(owner1["key"], name, id, label, type, vendor)
    content2 = @cp.create_content(owner2["key"], name, id, label, type, vendor)

    content1["uuid"].should eq(content2["uuid"])
  end

  it "creates two distinct content instances when details differ" do
    owner1 = create_owner random_string('test_owner')
    owner2 = create_owner random_string('test_owner')

    id = random_string("content")
    name = "shared_content"
    label = "shared content"
    type = "shared_content_type"
    vendor = "generous vendor"
    updated_upstream = Date.today

    content1 = @cp.create_content(owner1["key"], name, id, label, type, vendor)
    content2 = @cp.create_content(owner2["key"], name + "-2", id, label, type, vendor)

    content1["uuid"].should_not eq(content2["uuid"])
  end

  it "creates a new content instance when an org updates a shared instance" do
    owner1 = create_owner random_string('test_owner')
    owner2 = create_owner random_string('test_owner')
    owner3 = create_owner random_string('test_owner')

    id = random_string("content")
    name = "shared_content"
    label = "shared content"
    type = "shared_content_type"
    vendor = "generous vendor"
    updated_upstream = Date.today

    content1 = @cp.create_content(owner1["key"], name, id, label, type, vendor)
    content2 = @cp.create_content(owner2["key"], name, id, label, type, vendor)
    content3 = @cp.create_content(owner3["key"], name, id, label, type, vendor)

    content1["uuid"].should eq(content2["uuid"])
    content1["uuid"].should eq(content3["uuid"])

    content4 = @cp.update_content(owner2["key"], id, { :name => "new content name" })
    content4["uuid"].should_not eq(content1["uuid"])

    content = @cp.list_content(owner2["key"])
    content.size.should eq(1)
    content[0]["uuid"].should eq(content4["uuid"])
    content[0]["uuid"].should_not eq(content2["uuid"])

    content = @cp.list_content(owner1["key"])
    content.size.should eq(1)
    content[0]["uuid"].should eq(content1["uuid"])

    content = @cp.list_content(owner3["key"])
    content.size.should eq(1)
    content[0]["uuid"].should eq(content3["uuid"])
  end

  it "converges content when a given version already exists" do
    owner1 = create_owner random_string('test_owner1')
    owner2 = create_owner random_string('test_owner2')
    owner3 = create_owner random_string('test_owner3')

    id = random_string("content")
    name = "shared_content"
    label = "shared content"
    type = "shared_content_type"
    vendor = "generous vendor"
    updated_upstream = Date.today

    content1 = @cp.create_content(owner1["key"], name, id, label, type, vendor)
    content2 = @cp.create_content(owner2["key"], name, id, label, type, vendor)
    content3 = @cp.create_content(owner3["key"], name + "-2", id, label, type, vendor)
    content1["uuid"].should eq(content2["uuid"])
    content2["uuid"].should_not eq(content3["uuid"])

    content4 = @cp.update_content(owner3["key"], id, { :name => name })

    content4["uuid"].should eq(content1["uuid"])

    content = @cp.list_content(owner1["key"])
    content.size.should eq(1)
    content[0]["uuid"].should eq(content1["uuid"])

    content = @cp.list_content(owner2["key"])
    content.size.should eq(1)
    content[0]["uuid"].should eq(content2["uuid"])

    content = @cp.list_content(owner3["key"])
    content.size.should eq(1)
    content[0]["uuid"].should eq(content1["uuid"])
    content[0]["uuid"].should_not eq(content3["uuid"])
  end

  it "deletes content without affecting other orgs" do
    owner1 = create_owner random_string('test_owner')
    owner2 = create_owner random_string('test_owner')

    id = random_string("content")
    name = "shared_content"
    label = "shared content"
    type = "shared_content_type"
    vendor = "generous vendor"
    updated_upstream = Date.today

    content1 = @cp.create_content(owner1["key"], name, id, label, type, vendor)
    content2 = @cp.create_content(owner2["key"], name, id, label, type, vendor)

    content1["uuid"].should eq(content2["uuid"])

    @cp.delete_content(owner1["key"], id)

    content = @cp.list_content(owner1["key"])
    content.size.should eq(0)

    lambda do
      @cp.get_content(owner1["key"], id)
    end.should raise_exception(RestClient::ResourceNotFound)

    content = @cp.list_content(owner2["key"])
    content.size.should eq(1)
    content[0]["uuid"].should eq(content2["uuid"])
  end

end
