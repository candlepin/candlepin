require 'spec_helper'
require 'candlepin_scenarios'

describe 'Consumer Facts' do

  include CandlepinMethods

  before(:each) do
    @owner = create_owner random_string('test_owner')
    user = user_client(@owner, random_string("user"))

    facts = {
      'uname.machine' => 'i686',
      'uname.system'     => 'Linux',
    }

    @consumer = user.register(random_string("consumer"), :system, nil, facts,
        nil, nil, [], [{:productId => 'installedprod',
           :productName => "Installed"}])

    @consumer_api = Candlepin.new(username=nil, password=nil,
                                  cert=@consumer['idCert']['cert'],
                                  key=@consumer['idCert']['key'])
  end

  it 'allows a single fact to be added' do
    updated_facts = {
      'uname.machine' => 'x86_64',
      'uname.system'     => 'Linux',
      'memory.memtotal'  => '100',
    }
    @consumer_api.update_consumer({:facts => updated_facts})

    consumer = @consumer_api.get_consumer
    consumer['facts']['memory.memtotal'].should == '100'

    # Make sure we didn't clobber installed products when updating:
    consumer['installedProducts'].length.should == 1
  end

  it 'allows a fact to be updated' do
    updated_facts = {
      'uname.machine' => 'x86_64',
      'uname.system'     => 'BSD',
    }
    @consumer_api.update_consumer({:facts => updated_facts})

    consumer = @consumer_api.get_consumer
    consumer['facts']['uname.system'].should == 'BSD'
  end

  it 'allows a fact to be removed' do
    updated_facts = {
      'uname.machine' => 'x86_64',
    }
    @consumer_api.update_consumer({:facts => updated_facts})

    consumer = @consumer_api.get_consumer
    consumer['facts']['memory.memtotal'].should be_nil
  end

  it 'emits an event when facts are updated' do
    updated_facts = {
      'uname.machine' => 'x86_64',
      'uname.system'     => 'Linux',
    }
    @consumer_api.update_consumer({:facts => updated_facts})

    events = @consumer_api.list_consumer_events(@consumer.uuid)
    # Look for a consumer modified event:
    events.find { |e| e['type'] == 'MODIFIED' and e['target'] == 'CONSUMER' }.should_not be_nil
  end

  it 'does not emit an event when facts do not change' do
    updated_facts = {
      'uname.machine' => 'i686',
      'uname.system'     => 'Linux',
    }
    @consumer_api.update_consumer({:facts => updated_facts})
    events = @consumer_api.list_consumer_events(@consumer.uuid)
    # No consumer modified event should exist as facts did not change:
    events.find { |e| e['type'] == 'MODIFIED' and e['target'] == 'CONSUMER' }.should be_nil
  end

  it 'updates consumer updated date when facts are updated' do
    updated_facts = {
      'uname.system' => 'x86_64',
    }
    initial_date = @consumer.updated
    @consumer_api.update_consumer({:facts => updated_facts})

    updated_consumer = @consumer_api.get_consumer

    initial_date.should < updated_consumer['updated']
  end

  it 'can clear all facts' do
    updated_facts = {}
    @consumer_api.update_consumer({:facts => updated_facts})
    consumer = @consumer_api.get_consumer
    consumer['facts'].length.should == 0
  end


  it 'should return correct exception for contraint violations' do
    lambda {
      facts = {
        'uname.machine' => "a" * 256
      }
      user = user_client(@owner, random_string("user"))
      @consumer = user.register(random_string("consumer"), :system, nil, facts)
    }.should raise_exception(RestClient::BadRequest)
  end

end
