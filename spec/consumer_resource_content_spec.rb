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
    returnOverrides = @cp.add_content_overrides(@consumer1.uuid, overrides)
    returnOverrides.size.should == 2
    # make sure the add content returns the same as the get
    @cp.get_content_overrides(@consumer1.uuid).should =~ returnOverrides
  end

  it "should allow content value overrides updates" do
    overrides = create_content_override_set(2, @consumer1, false);
    returnOverrides = @cp.add_content_overrides(@consumer1.uuid, overrides)
    # update one of the overrides
    overrides = []
    overrides << create_content_override("content1.label", "field1", "consumer_1a")
    returnOverrides = @cp.add_content_overrides(@consumer1.uuid, overrides)
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
    @cp.add_content_overrides(@consumer1.uuid, overrides)
    overrides = create_content_override_set(2, @consumer2, false);
    @cp.add_content_overrides(@consumer2.uuid, overrides)

    found = true
    c1_overrides = @cp.get_content_overrides(@consumer1.uuid)
    c1_overrides.size.should == 3
    c1_overrides.each do |override|
      found &= override.value == @consumer1.uuid
    end
    found.should == true

    found = true
    c2_overrides = @cp.get_content_overrides(@consumer2.uuid)
    c2_overrides.size.should == 2
    c2_overrides.each do |override|
      found &= override.value == @consumer2.uuid
    end
    found.should == true
  end

  it "should allow content value override deletion" do
    overrides = create_content_override_set(5, nil, true);
    returnOverrides = @cp.add_content_overrides(@consumer1.uuid, overrides)
    returnOverrides.size.should == 5

    # delete by repo and name
    del_override = create_content_override("content3.label", "field3")
    @cp.delete_content_overrides(@consumer1.uuid, [del_override])
    gone = true
    @cp.get_content_overrides(@consumer1.uuid).each do |override|
      gone &= override.name != "field3"
    end
    gone.should == true

    # delete by repo
    del_override = create_content_override("content2.label")
    @cp.delete_content_overrides(@consumer1.uuid, [del_override])
    gone = true
    @cp.get_content_overrides(@consumer1.uuid).each do |override|
      gone &= override.contentLabel != "content2.label"
    end
    gone.should == true

    # delete by consumer
    del_override = create_content_override()
    @cp.delete_content_overrides(@consumer1.uuid, [del_override])
    @cp.get_content_overrides(@consumer1.uuid).size.should == 0
  end

  it "should keep content override deletes separate across consumers" do
    overrides = create_content_override_set(5, @consumer1, false);
    @cp.add_content_overrides(@consumer1.uuid, overrides)
    overrides = create_content_override_set(5, @consumer2, false);
    @cp.add_content_overrides(@consumer2.uuid, overrides)

     # delete by repo/name
    del_override = create_content_override("content2.label", "field2")
    @cp.delete_content_overrides(@consumer1.uuid, [del_override])
    found = false
    @cp.get_content_overrides(@consumer2.uuid).each do |override|
      found |= override.name == "field2"
    end
    found.should == true

     # delete by repo
    del_override = create_content_override("content4.label")
    @cp.delete_content_overrides(@consumer1.uuid, [del_override])
    found = false
    @cp.get_content_overrides(@consumer2.uuid).each do |override|
      found |= override.contentLabel == "content4.label"
    end
    found.should == true

    # delete by consumer
    del_override = create_content_override()
    @cp.delete_content_overrides(@consumer1.uuid, [del_override])
    @cp.get_content_overrides(@consumer2.uuid).size.should == 5
  end

  it "should reject changes for blacklisted attributes" do
    overrides = []
    overrides << create_content_override("content1.label", "baseurl", "its a no-no")
    lambda do
      @cp.add_content_overrides(@consumer1.uuid, overrides)
    end.should raise_exception(RestClient::BadRequest)
  end

  it "should reject all changes if any blacklisted attributes exist" do
    overrides = []
    overrides << create_content_override("content2.label", "changeable", "its a ok")
    overrides << create_content_override("content1.label", "baseurl", "its a no-no")
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
