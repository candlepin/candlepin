# -*- coding: utf-8 -*-
require 'spec_helper'
require 'candlepin_scenarios'

def hash_diff(h1, h2)
  h1.keys.inject({}) do |memo, key|
    unless h1[key] == h2[key]
      memo[key] = [h1[key], h2[key]]
    end
    memo
  end
end

describe 'Response JSON Filtering' do

  include CandlepinMethods

  before(:each) do
    @owner1 = create_owner random_string('test_owner1')
    @username1 = random_string("user1")
    @consumername1 = random_string("consumer1")
    @user1 = user_client(@owner1, @username1)
    @consumer1 = consumer_client(@user1, @consumername1)
  end

  it "should allow a single filter" do
    consumer_full = @cp.get("/consumers/#{@consumer1.uuid}")
    consumer_filtered = @cp.get("/consumers/#{@consumer1.uuid}?exclude=href")
    newdict = hash_diff(consumer_full, consumer_filtered)
    # There should only be one changed key
    newdict.keys.size.should == 1
    # there should be no href in the new response
    newdict["href"][1].should == nil
  end

  it "should allow a multiple filters" do
    consumer_full = @cp.get("/consumers/#{@consumer1.uuid}")
    consumer_filtered = @cp.get("/consumers/#{@consumer1.uuid}?exclude=href&exclude=facts")
    newdict = hash_diff(consumer_full, consumer_filtered)
    # There should only be one changed key
    newdict.keys.size.should == 2
    # there should be no or facts href in the new response
    newdict["href"][1].should == nil
    newdict["facts"][1].should == nil
  end

  it "should allow a single include" do
    consumer_filtered = @cp.get("/consumers/#{@consumer1.uuid}?include=href")
    consumer_filtered.keys.size.should == 1
    # there should be only href in the new response
    consumer_filtered["href"].should_not == nil
  end

  it "should allow multiple includes" do
    consumer_filtered = @cp.get("/consumers/#{@consumer1.uuid}?include=href&include=facts")
    consumer_filtered.keys.size.should == 2
    # there should be only href or facts in the new response
    consumer_filtered["href"].should_not == nil
    consumer_filtered["facts"].should_not == nil
  end

  it 'should allow filters on encapsulated objects' do
    consumer_filtered = @cp.get("/consumers/#{@consumer1.uuid}?include=id&include=owner.id")
    consumer_filtered.keys.size.should == 2
    consumer_filtered["id"].should_not == nil
    consumer_filtered["owner"].keys.size.should == 1
    consumer_filtered["owner"]["id"].should_not == nil
  end

  it 'should allow filters on encapsulated lists' do
    consumer = @user1.register(random_string("test1"))
    consumer = @user1.register(random_string("test2"))
    consumers = @cp.get("/consumers?type=system&include=id&include=owner.id")
    consumers.each do |consumer|
      consumer.keys.size.should == 2
      consumer["id"].should_not == nil
      consumer["owner"].keys.size.should == 1
      consumer["owner"]["id"].should_not == nil
    end
  end
end
