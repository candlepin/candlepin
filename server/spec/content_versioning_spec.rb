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
    updated_upstream = DateTime.now

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
    updated_upstream = DateTime.now

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
    updated_upstream = DateTime.now

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

  it "creates a new instance when making changes to an existing instance" do
    # If we enable clustered caching and re-enable the in-place content update branch, this
    # test will need to be updated or removed.

    owner1 = create_owner random_string('test_owner1')

    id = random_string("content")
    name = "shared_content"
    label = "shared content"
    type = "shared_content_type"
    vendor = "generous vendor"

    content1 = @cp.create_content(owner1["key"], name, id, label, type, vendor)
    expect(content1).to_not be_nil

    content2 = @cp.update_content(owner1["key"], id, { :name => name + "-2" })
    expect(content2).to_not be_nil

    expect(content1["uuid"]).to_not eq(content2["uuid"])

    # content should now be different from both content1 and 2, and content2 should no longer exist
    content = @cp.list_content(owner1["key"])
    content.size.should eq(1)
    expect(content[0]["uuid"]).to_not eq(content1["uuid"])
    expect(content[0]["uuid"]).to eq(content2["uuid"])
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

  it "diverges when updating shared content" do
    owner1 = create_owner random_string('test_owner1')
    owner2 = create_owner random_string('test_owner2')

    id = random_string("content")
    name = "shared_content"
    label = "shared content"
    type = "shared_content_type"
    vendor = "generous vendor"

    content1 = @cp.create_content(owner1["key"], name, id, label, type, vendor)
    content2 = @cp.create_content(owner2["key"], name, id, label, type, vendor)

    # content should be the same here
    expect(content1).to_not be_nil
    expect(content2).to_not be_nil
    expect(content1["uuid"]).to eq(content2["uuid"])

    content3 = @cp.update_content(owner2["key"], id, { :name => name + "-2" })
    expect(content3).to_not be_nil

    # content3 should now be different from both content1 and 2, and content2 should no longer exist
    content = @cp.list_content(owner1["key"])
    content.size.should eq(1)
    expect(content[0]["uuid"]).to eq(content1["uuid"])
    expect(content[0]["uuid"]).to eq(content2["uuid"])
    expect(content[0]["uuid"]).to_not eq(content3["uuid"])

    content = @cp.list_content(owner2["key"])
    content.size.should eq(1)
    expect(content[0]["uuid"]).to_not eq(content1["uuid"])
    expect(content[0]["uuid"]).to_not eq(content2["uuid"])
    expect(content[0]["uuid"]).to eq(content3["uuid"])
  end

  it "deletes content without affecting other orgs" do
    owner1 = create_owner random_string('test_owner')
    owner2 = create_owner random_string('test_owner')

    id = random_string("content")
    name = "shared_content"
    label = "shared content"
    type = "shared_content_type"
    vendor = "generous vendor"

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

  it "should not converge with an orphaned content" do
    # NOTE:
    # This test should be removed/disabled if in-place updating/converging is reenabled, as orphans
    # will not be created in such a case

    owner1 = create_owner random_string('test_owner')
    owner2 = create_owner random_string('test_owner')

    id = random_string('test_content')
    label = "shared content"
    type = "shared_content_type"
    vendor = "generous vendor"

    orphan = @cp.create_content(owner1["key"], id, id, label, type, vendor)
    content1 = @cp.update_content(owner1["key"], id, { :name => "#{id}-update" })
    content2 = @cp.create_content(owner2["key"], id, id, label, type, vendor)

    expect(orphan['uuid']).to_not eq(content1['uuid'])
    expect(orphan['uuid']).to_not eq(content2['uuid'])
    expect(content1['uuid']).to_not eq(content2['uuid'])

    expect(@cp.get_content_by_uuid(orphan['uuid'])).to_not be_nil
    expect(@cp.get_content_by_uuid(content1['uuid'])).to_not be_nil
    expect(@cp.get_content_by_uuid(content2['uuid'])).to_not be_nil
  end

  it 'should cleanup orphans without interfering with normal actions' do
    # NOTE:
    # This test takes advantage of the immutable nature of contents with the in-place update branch
    # disabled. If in-place updates are ever reenabled, we'll need a way to generate large numbers
    # of orphaned contents for this test.

    owner1 = create_owner(random_string('test_owner-1'))
    owner2 = create_owner(random_string('test_owner-2'))

    prefix = "test-content-"
    label = "shared content"
    type = "shared_content_type"
    vendor = "generous vendor"

    offset = 10000
    length = 100

    # Repeat this test a few(ish) times to hopefully catch any synchronization error
    (1..5).each do
      o1_uuids = []
      o2_uuids = []

      # Create a bunch of dummy contents
      (offset..(offset + length - 1)).each do |i|
        id = "#{prefix}#{i}"

        # create content and immediately update it to generate an orphaned content
        @cp.create_content(owner1["key"], id, id, label, type, vendor)
      end

      # Attempt to update and create new contents to get into some funky race conditions with
      # convergence and orphanization
      updater = Thread.new do
        (offset..(offset + length - 1)).each do |i|
          id = "#{prefix}#{i}"
          content = @cp.update_content(owner1["key"], id, { :name => "#{id}-update" })
          o1_uuids << content['uuid']
        end
      end

      generator = Thread.new do
        (offset..(offset + length - 1)).each do |i|
          id = "#{prefix}#{i}"
          content = @cp.create_content(owner2["key"], id, id, label, type, vendor)
          o2_uuids << content['uuid']
        end
      end

      sleep 1
      @cp.trigger_async_job("ORPHAN_CLEANUP");

      updater.join
      generator.join

      # Verify the contents created/updated still exist
      o1_uuids.each do |uuid|
        content = @cp.get_content_by_uuid(uuid)
        expect(content).to_not be_nil
      end

      o2_uuids.each do |uuid|
        content = @cp.get_content_by_uuid(uuid)
        expect(content).to_not be_nil
      end

      offset += length
    end
  end

end
