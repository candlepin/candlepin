# -*- coding: utf-8 -*-
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
    content1 = create_content
    content2 = create_content
    overrides = []
    overrides << create_content_override(content1.label, "field1", "consumer_1")
    overrides << create_content_override(content2.label, "field2", "consumer_1")
    returnOverrides = @cp.add_content_overrides(@consumer1.uuid, overrides)
    returnOverrides.size.should == 2
    # make sure the add content returns the same as the get
    @cp.get_content_overrides(@consumer1.uuid).should =~ returnOverrides
  end

  it "should allow content value overrides updates" do
    content1 = create_content
    content2 = create_content
    overrides = []
    overrides << create_content_override(content1.label, "field1", "consumer_1")
    overrides << create_content_override(content2.label, "field2", "consumer_1")
    returnOverrides = @cp.add_content_overrides(@consumer1.uuid, overrides)
    # update one of the overrides
    overrides = []
    overrides << create_content_override(content1.label, "field1", "consumer_1a")
    returnOverrides = @cp.add_content_overrides(@consumer1.uuid, overrides)
    # make sure that the update does not step on another override
    found1 = false
    found2 = false
    returnOverrides.each do |override|
      found1 |= override.name = "field1" and override.value == "consumer_1a"
      found2 |= override.name = "field2" and override.value == "consumer_1"
    end
    found1.should == true
    found2.should == true
  end

  it "should keep content overrides separate across consumers" do
    content1 = create_content
    content2 = create_content
    content3 = create_content
    overrides = []
    overrides << create_content_override(content1.label, "field1", "consumer_1")
    overrides << create_content_override(content2.label, "field2", "consumer_1")
    overrides << create_content_override(content3.label, "field3", "consumer_1")
    @cp.add_content_overrides(@consumer1.uuid, overrides)

    overrides = []
    overrides << create_content_override(content1.label, "field1", "consumer_2")
    overrides << create_content_override(content2.label, "field2", "consumer_2")
    @cp.add_content_overrides(@consumer2.uuid, overrides)

    found = true
    @cp.get_content_overrides(@consumer1.uuid).each do |override|
      found &= override.value == "consumer_1"
    end
    found.should == true

    found = true
    @cp.get_content_overrides(@consumer2.uuid).each do |override|
      found &= override.value == "consumer_2"
    end
    found.should == true
  end

  it "should allow content value override deletion" do
    content1 = create_content
    content2 = create_content
    content3 = create_content
    content4 = create_content
    content5 = create_content

    overrides = []
    overrides << create_content_override(content1.label, "field1", "value1")
    overrides << create_content_override(content2.label, "field2", "value2")
    overrides << create_content_override(content3.label, "field3", "value3")
    overrides << create_content_override(content4.label, "field4", "value4")
    overrides << create_content_override(content5.label, "field5", "value5")
    returnOverrides = @cp.add_content_overrides(@consumer1.uuid, overrides)
    returnOverrides.size.should == 5

    # delete by repo and name
    del_override = create_content_override(content3.label, "field3")
    @cp.delete_content_overrides(@consumer1.uuid, [del_override])
    gone = true
    @cp.get_content_overrides(@consumer1.uuid).each do |override|
      gone &= override.name != "field3"
    end
    gone.should == true

    # delete by repo
    del_override = create_content_override(content2.label)
    @cp.delete_content_overrides(@consumer1.uuid, [del_override])
    gone = true
    @cp.get_content_overrides(@consumer1.uuid).each do |override|
      gone &= override.contentLabel != content2.label
    end
    gone.should == true
    
    # delete by consumer
    del_override = create_content_override()
    @cp.delete_content_overrides(@consumer1.uuid, [del_override])
    @cp.get_content_overrides(@consumer1.uuid).size.should == 0
  end

  it "should keep content override deletes separate across consumers" do
    content1 = create_content
    content2 = create_content
    content3 = create_content
    content4 = create_content
    content5 = create_content
    overrides = []
    overrides << create_content_override(content1.label, "field1", "consumer_1")
    overrides << create_content_override(content2.label, "field2", "consumer_1")
    overrides << create_content_override(content3.label, "field3", "consumer_1")
    overrides << create_content_override(content4.label, "field4", "consumer_1")
    overrides << create_content_override(content5.label, "field5", "consumer_1")
    @cp.add_content_overrides(@consumer1.uuid, overrides)

    overrides = []
    overrides << create_content_override(content1.label, "field1", "consumer_2")
    overrides << create_content_override(content2.label, "field2", "consumer_2")
    overrides << create_content_override(content3.label, "field3", "consumer_2")
    overrides << create_content_override(content4.label, "field4", "consumer_2")
    overrides << create_content_override(content5.label, "field5", "consumer_2")
    @cp.add_content_overrides(@consumer2.uuid, overrides)

     # delete by repo/name
    del_override = create_content_override(content2.label, "field2")
    @cp.delete_content_overrides(@consumer1.uuid, [del_override])
    found = false
    @cp.get_content_overrides(@consumer2.uuid).each do |override|
      found |= override.name == "field2"
    end
    found.should == true
   
     # delete by repo
    del_override = create_content_override(content4.label)
    @cp.delete_content_overrides(@consumer1.uuid, [del_override])
    found = false
    @cp.get_content_overrides(@consumer2.uuid).each do |override|
      found |= override.contentLabel == content4.label
    end
    found.should == true
    
    # delete by consumer
    del_override = create_content_override()
    @cp.delete_content_overrides(@consumer1.uuid, [del_override])
    @cp.get_content_overrides(@consumer2.uuid).size.should == 5
  end

  it "should reject changes for blacklisted attributes" do
    content1 = create_content
    overrides = []
    overrides << create_content_override(content1.label, "baseurl", "its a no-no")
    lambda do
      @cp.add_content_overrides(@consumer1.uuid, overrides)
    end.should raise_exception(RestClient::BadRequest)
  end

  it "should reject all changes if any blacklisted attributes exist" do
    content1 = create_content
    content2 = create_content
    overrides = []
    overrides << create_content_override(content2.label, "changeable", "its a ok")
    overrides << create_content_override(content1.label, "baseurl", "its a no-no")
    lambda do
      @cp.add_content_overrides(@consumer1.uuid, overrides)
    end.should raise_exception(RestClient::BadRequest)
    @cp.get_content_overrides(@consumer1.uuid).size.should == 0    
  end


  def create_content_override(contentlabel=nil, name=nil, value=nil)
    content = {
      'contentLabel' => contentlabel,
      'name' => name,
      'value' => value
    }
  end
end
