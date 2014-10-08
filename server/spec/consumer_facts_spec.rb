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

    # Event needs to be received by hornetq listener and stored to db, a
    # second should be plenty time.
    sleep 1

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

    # MySQL drops millis, we need to wait a bit longer
    sleep 1
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

  it 'should truncate long facts' do
    facts = {
      'uname.machine' => "a" * 256
    }
    user = user_client(@owner, random_string("user"))
    @consumer = user.register(random_string("consumer"), :system, nil, facts)
    @consumer.facts['uname.machine'].should == 'a'*252 + '...'
  end

  it 'should allow creation with length 255 fact keys and values' do
    consumer =  @cp.register('c', :system, nil, {'a'*255 => 'b'*255}, nil, @owner['key'])
    consumer.facts['a'*255].should == 'b'*255
  end

  it 'should allow creation length 256 fact keys and values, however truncated for the db' do
    consumer =  @cp.register('c', :system, nil, {'a'*256 => 'b'*256}, nil, @owner['key'])
    consumer.facts['a'*252 + '...'].should == 'b'*252 + '...'
  end

  it 'should allow update with length 255 fact keys and values' do
    consumer =  @cp.register('c', :system, nil, {}, nil, @owner['key'])
    @cp.update_consumer({:uuid => consumer['uuid'], :facts => {'a'*255 => 'b'*255}})
    consumer = @cp.get_consumer(consumer['uuid'])
    consumer.facts['a'*255].should == 'b'*255
  end

  it 'should allow update length 256 fact keys and values, however truncated for the db' do
    consumer =  @cp.register('c', :system, nil, {}, nil, @owner['key'])
    @cp.update_consumer({:uuid => consumer['uuid'], :facts => {'a'*256 => 'b'*256}})
    consumer = @cp.get_consumer(consumer['uuid'])
    consumer.facts['a'*252 + '...'].should == 'b'*252 + '...'
  end
end
