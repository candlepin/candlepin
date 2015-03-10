require 'spec_helper'
require 'candlepin_scenarios'

require 'rubygems'
require 'rest_client'

describe 'Uebercert' do
  include CandlepinMethods

  it 'owner can be deleted' do
    owner = @cp.create_owner random_string("test_owner1")
    @cp.generate_ueber_cert(owner['key'])
    @cp.delete_owner(owner['key'], false)

    lambda do
      @cp.get_owner(owner['key'])
    end.should raise_exception(RestClient::ResourceNotFound)
  end

  it 'consumers can be deleted' do
    owner = @cp.create_owner random_string("test_owner1")
    @cp.generate_ueber_cert(owner['key'])
    consumers = @cp.list_consumers({:owner => owner['key']})
    consumers.size.should == 1
    uber_consumer = consumers[0]
    @cp.unregister(uber_consumer['uuid'])
    @cp.list_consumers({:owner => owner['key']}).size.should == 0
  end

end

