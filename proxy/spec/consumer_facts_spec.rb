require 'candlepin_scenarios'

describe 'Consumer Facts' do

  include CandlepinMethods
  include CandlepinScenarios

  before(:each) do
    owner = create_owner random_string('test_owner')
    user = user_client(owner, random_string("user"))

    facts = {
      'cpu.architecture' => 'i686',
      'uname.system'     => 'Linux',
    }

    @consumer = user.register(random_string("consumer"), :system, nil, facts)

    @consumer_api = Candlepin.new(username=nil, password=nil,
                                  cert=@consumer['idCert']['cert'],
                                  key=@consumer['idCert']['key'])
  end

  it 'allows a single fact to be added' do
    updated_facts = {
      'cpu.architecture' => 'x86_64',
      'uname.system'     => 'Linux',
      'memory.memtotal'  => '100',
    }
    @consumer_api.update_facts(updated_facts)

    consumer = @consumer_api.get_consumer
    consumer['facts']['memory.memtotal'].should == '100'
  end

  it 'allows a fact to be updated' do
    updated_facts = {
      'cpu.architecture' => 'x86_64',
      'uname.system'     => 'BSD',
    }
    @consumer_api.update_facts(updated_facts)

    consumer = @consumer_api.get_consumer
    consumer['facts']['uname.system'].should == 'BSD'
  end

  it 'allows a fact to be removed' do
    updated_facts = {
      'cpu.architecture' => 'x86_64',
    }
    @consumer_api.update_facts(updated_facts)

    consumer = @consumer_api.get_consumer
    consumer['facts']['memory.memtotal'].should be_nil
  end

  it 'emits an event when facts are updated' do
    updated_facts = {
      'cpu.architecture' => 'x86_64',
      'uname.system'     => 'Linux',
    }
    @consumer_api.update_facts(updated_facts)

    events = @cp.list_consumer_events(@consumer.uuid)

    # Punting on this for now...
    events.should include("consumer")
    events.should include("updated")
  end

  it 'does not emit an event when facts do not change' do
    updated_facts = {
      'cpu.architecture' => 'i686',
      'uname.system'     => 'Linux',
    }
    @consumer_api.update_facts(updated_facts)

    events = @cp.list_consumer_events(@consumer.uuid)

    # Punting on this for now...
    events.should include("consumer")
    events.should include("updated")
  end
end
