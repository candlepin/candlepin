require 'spec_helper'
require 'candlepin_scenarios'

describe 'Consumer Resource Content' do

  include CandlepinMethods

  before(:each) do
    @owner1 = create_owner random_string('test_owner1')
    @username1 = random_string("user1")
    @consumername1 = random_string("consumer1")
    @user1 = user_client(@owner1, @username1)
    @consumer1 = consumer_client(@user1, @consumername1)
    @consumer2 = consumer_client(@user1, random_string("consumer2"))
  end

  it "should allow content value overrides per consumer" do
    overrides = create_content_override_set(2, @consumer1, false);
    returnOverrides = @consumer1.add_content_overrides(@consumer1.uuid, overrides)
    returnOverrides.size.should == 2
    results = @consumer1.get_content_overrides(@consumer1.uuid)
    # MySQL doesn't support milliseconds.  The first value returned has them serialized
    # from the java Date object, the one from the database is zeroed.
    [returnOverrides, results].each do |override_list|
      override_list.each do |override|
        override.delete('created')
        override.delete('updated')
      end
    end
    # make sure the add content returns the same as the get
    results.should =~ returnOverrides
  end

  it "should allow content value overrides updates" do
    overrides = create_content_override_set(2, @consumer1, false);
    returnOverrides = @consumer1.add_content_overrides(@consumer1.uuid, overrides)
    # update one of the overrides
    overrides = []
    overrides << create_content_override("content1.label", "field1", "consumer_1a")
    returnOverrides = @consumer1.add_content_overrides(@consumer1.uuid, overrides)
    # make sure its an update not another add
    returnOverrides.size.should == 2
    # make sure that the update does not step on another override
    found1 = false
    found2 = false
    returnOverrides.each do |override|
      found1 |= override.name = "field1" and override.value == "consumer_1a"
      found2 |= override.name = "field2" and override.value == @consumer1.uuid
    end
    found1.should == true
    found2.should == true
  end

  it "should keep content overrides separate across consumers" do
    overrides = create_content_override_set(3, @consumer1, false);
    @consumer1.add_content_overrides(@consumer1.uuid, overrides)
    overrides = create_content_override_set(2, @consumer2, false);
    @consumer2.add_content_overrides(@consumer2.uuid, overrides)

    found = true
    c1_overrides = @consumer1.get_content_overrides(@consumer1.uuid)
    c1_overrides.size.should == 3
    c1_overrides.each do |override|
      found &= override.value == @consumer1.uuid
    end
    found.should == true

    found = true
    c2_overrides = @consumer2.get_content_overrides(@consumer2.uuid)
    c2_overrides.size.should == 2
    c2_overrides.each do |override|
      found &= override.value == @consumer2.uuid
    end
    found.should == true
  end

  it "should allow content value override deletion" do
    overrides = create_content_override_set(5, nil, true);
    returnOverrides = @consumer1.add_content_overrides(@consumer1.uuid, overrides)
    returnOverrides.size.should == 5

    # delete by repo and name
    del_override = create_content_override("content3.label", "field3")
    @consumer1.delete_content_overrides(@consumer1.uuid, [del_override])
    gone = true
    @consumer1.get_content_overrides(@consumer1.uuid).each do |override|
      gone &= override.name != "field3"
    end
    gone.should == true

    # delete by repo
    del_override = create_content_override("content2.label")
    @consumer1.delete_content_overrides(@consumer1.uuid, [del_override])
    gone = true
    @consumer1.get_content_overrides(@consumer1.uuid).each do |override|
      gone &= override.contentLabel != "content2.label"
    end
    gone.should == true

    # delete by consumer
    del_override = create_content_override()
    @consumer1.delete_content_overrides(@consumer1.uuid, [del_override])
    @consumer1.get_content_overrides(@consumer1.uuid).size.should == 0
  end

  it "should keep content override deletes separate across consumers" do
    overrides = create_content_override_set(5, @consumer1, false);
    @consumer1.add_content_overrides(@consumer1.uuid, overrides)
    overrides = create_content_override_set(5, @consumer2, false);
    @consumer2.add_content_overrides(@consumer2.uuid, overrides)

     # delete by repo/name
    del_override = create_content_override("content2.label", "field2")
    @consumer1.delete_content_overrides(@consumer1.uuid, [del_override])
    found = false
    @consumer2.get_content_overrides(@consumer2.uuid).each do |override|
      found |= override.name == "field2"
    end
    found.should == true

     # delete by repo
    del_override = create_content_override("content4.label")
    @consumer1.delete_content_overrides(@consumer1.uuid, [del_override])
    found = false
    @consumer2.get_content_overrides(@consumer2.uuid).each do |override|
      found |= override.contentLabel == "content4.label"
    end
    found.should == true

    # delete by consumer
    del_override = create_content_override()
    @consumer1.delete_content_overrides(@consumer1.uuid, [del_override])
    @consumer2.get_content_overrides(@consumer2.uuid).size.should == 5
  end

  it "should reject changes for blacklisted attributes" do
    overrides = []
    overrides << create_content_override("content1.label", "baseurl", "its a no-no")
    lambda do
      @consumer1.add_content_overrides(@consumer1.uuid, overrides)
    end.should raise_exception(RestClient::BadRequest)
  end
  
  it "should reject changes for blacklisted attributes regardless of case" do
    overrides = []
    overrides << create_content_override("content1.label", "BaseURL", "its a no-no")
    lambda do
      @consumer1.add_content_overrides(@consumer1.uuid, overrides)
    end.should raise_exception(RestClient::BadRequest)
  end

  it "should reject all changes if any blacklisted attributes exist" do
    overrides = []
    overrides << create_content_override("content2.label", "changeable", "its a ok")
    overrides << create_content_override("content1.label", "baseurl", "its a no-no")
    lambda do
      @consumer1.add_content_overrides(@consumer1.uuid, overrides)
    end.should raise_exception(RestClient::BadRequest)
    @consumer1.get_content_overrides(@consumer1.uuid).size.should == 0
  end

  it "should not create a new override for a property with same name but different case" do
    overrides = []
    overrides << create_content_override("my-content", "my-field", "my-value")
    returner = @consumer1.add_content_overrides(@consumer1.uuid, overrides)
    returner.size.should == 1
    
    overrides[0]["name"] = "MY-FIELD"
    overrides[0]["value"] = "changed-value"
    returner = @consumer1.add_content_overrides(@consumer1.uuid, overrides)
    # make sure its an update not another add
    returner.size.should == 1
    returner[0]['name'].should == 'my-field'
    returner[0]['value'].should == 'changed-value'
  end

  it "last property wins when overrides with duplicate names are specified" do
    overrides = []
    overrides << create_content_override("my-content", "my-field", "my-value")
    overrides << create_content_override("my-content", "my-field", "my-changed-value")
    overrides.size.should == 2
    returner = @consumer1.add_content_overrides(@consumer1.uuid, overrides)
    returner.size.should == 1
    returner[0]["name"].should == "my-field"
    returner[0]["value"].should == "my-changed-value"
  end

  it "should result in NotFound if different consumer attempts to get overrides" do
    overrides = []
    overrides << create_content_override("my-content", "my-field", "my-value")
    consumer1_overrides = @consumer1.add_content_overrides(@consumer1.uuid, overrides)
    consumer1_overrides.size.should == 1
    lambda do
        @consumer2.get_content_overrides(@consumer1.uuid)
    end.should raise_exception(RestClient::ResourceNotFound)
  end

  it "should result in NotFound if different consumer attempts to add overrides" do
    overrides = []
    overrides << create_content_override("my-content", "my-field", "my-value")
    consumer1_overrides = @consumer1.add_content_overrides(@consumer1.uuid, overrides)
    consumer1_overrides.size.should == 1
    lambda do
        @consumer2.add_content_overrides(@consumer1.uuid, overrides)
    end.should raise_exception(RestClient::ResourceNotFound)
  end

  it "should result in NotFound if different consumer attempts to delete overrides" do
    overrides = []
    overrides << create_content_override("my-content", "my-field", "my-value")
    consumer1_overrides = @consumer1.add_content_overrides(@consumer1.uuid, overrides)
    consumer1_overrides.size.should == 1
    lambda do
        @consumer2.delete_content_overrides(@consumer1.uuid)
    end.should raise_exception(RestClient::ResourceNotFound)
  end

  def create_content_override(contentlabel=nil, name=nil, value=nil)
    content = {
      'contentLabel' => contentlabel,
      'name' => name,
      'value' => value
    }
  end

  def create_content_override_set(count=1, consumer=nil, increment_value=false)
    overrides = []
    (1..count).each do |i|
      if increment_value
        overrides << create_content_override("content#{i}.label", "field#{i}", "value#{i}")
      elsif consumer
        overrides << create_content_override("content#{i}.label", "field#{i}", consumer.uuid)
      end
    end
    return overrides
  end
end
