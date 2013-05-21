require 'candlepin_scenarios'

describe 'Distributor Capability' do
  include CandlepinMethods
  include CandlepinScenarios

  before(:each) do
    @owner = create_owner(random_string("test_owner"))
    @user = user_client(@owner, random_string("test_user"))
  end
 
  it 'should allow distributor version creation' do
    count = @cp.get_distributor_versions.size
    dist_version = create_distributor_version("SAMvBillion",
                                    "Subscription Asset Manager Billion",
                                   ["midas touch",
                                    "telepathy",
                                    "lightning speed"]) 
    dist_version.id.should_not be nil
    @cp.get_distributor_versions.size.should == count+1
    dist_version.capabilities.size.should == 3	
  end

  it 'should allow distributor version update' do
    count = @cp.get_distributor_versions.size
    dist_version = create_distributor_version("SAMvBillion",
                                    "Subscription Asset Manager Billion",
                                   ["midas touch",
                                    "telepathy",
                                    "lightning speed"]) 
    dist_version_id = dist_version.id
    dist_version = update_distributor_version(dist_version_id, "SAMvBillion",
                                    "Subscription Asset Manager Billion",
                                   ["midas touch",
                                    "lightning speed"]) 
    @cp.get_distributor_versions.size.should == count+1
    dist_version.id.should == dist_version_id
    dist_version.capabilities.size.should == 2
  end


  it 'can assign consumer capabilities based on distributor version when creating' do
    dist_version = create_distributor_version("SAMvBillion",
                                    "Subscription Asset Manager Billion",
                                   ["midas touch",
                                    "telepathy",
                                    "lightning speed"]) 
    facts = {
      'distributor_version' => 'SAMvBillion'
    }
    consumer = @user.register(random_string("consumer"), :candlepin, nil, facts)
    consumer.capabilities.size.should == 3  

  end

  it 'will assign consumer capabilities based on capability list when creating' do
    dist_version = create_distributor_version("SAMvBillion",
                                    "Subscription Asset Manager Billion",
                                   ["midas touch",
                                    "telepathy",
                                    "lightning speed"]) 
    facts = {
      'distributor_version' => 'SAMvBillion'
    }
    capabilities = ["one","two"]
    consumer = @user.register(random_string("consumer"), :candlepin, nil, facts,
                              nil, nil, [], [], nil, capabilities)
    consumer.capabilities.size.should == 2 

  end


  it 'can update consumer capabilities based on changed distributor version when updating consumer' do
    dist_version = create_distributor_version("SAMvBillion",
                                    "Subscription Asset Manager Billion",
                                   ["midas touch",
                                    "telepathy",
                                    "lightning speed"]) 
    facts = {
      'distributor_version' => 'SAMvBillion'
    }
    consumer = @user.register(random_string("consumer"), :candlepin, nil, facts)
    consumer.capabilities.size.should == 3  

    dist_version = create_distributor_version("SAMvBazillion",
                                    "Subscription Asset Manager Bazillion",
                                   ["midas touch",
                                    "telekenesis",
                                    "ludicrist speed",
                                    "omlet maker"]) 
    facts = {
      'distributor_version' => 'SAMvBazillion'
    }
    @cp.update_consumer({:uuid => consumer['uuid'], :facts => facts})
    consumer = @cp.get_consumer(consumer['uuid'])
    consumer.lastCheckin.should be nil
    consumer.capabilities.size.should == 4
  end

  it 'can update consumer capabilities from capability list' do
    dist_version = create_distributor_version("SAMvBazillion",
                                    "Subscription Asset Manager Bazillion",
                                   ["midas touch",
                                    "telekenesis",
                                    "ludicrist speed",
                                    "omlet maker"])
    facts = {
      'distributor_version' => 'SAMvBazillion'
    }
    consumer = @user.register(random_string("consumer"), :candlepin, nil, facts)
    consumer.capabilities.size.should == 4
    consumer.lastCheckin.should be nil 

    consumer_client = Candlepin.new(username=nil, password=nil,
        cert=consumer['idCert']['cert'],
        key=consumer['idCert']['key'])
    consumer_client.update_consumer({})
    consumer = @cp.get_consumer(consumer['uuid'])
    consumer.lastCheckin.should_not be nil 

    capabilities = ["midas touch",
                    "telekenesis",
                    "ludicrist speed",
                    "omlet maker",
                    "oragmi",
                    "heat vision"]
    @cp.update_consumer({:uuid => consumer['uuid'], :capabilities => capabilities})
    consumer = @cp.get_consumer(consumer['uuid'])
    consumer.capabilities.size.should == 6  
  end

  # The unit tests examine the variations, this is a simple end-to-end test.
  # shows blocking for capability deficiency as well as showing
  # distributor consumers not needing cert version validation
  it 'can stop bind based on consumer capabilities' do
    @product = create_product(nil, nil, :attributes =>
				{:cores => 8})
    subscription = @cp.create_subscription(@owner['key'], @product.id, 10, [], '12345', '6789', 'order1')
    @cp.refresh_pools(@owner['key'])

    consumer = @user.register(random_string("consumer"), :candlepin, nil, {})
    entitlements = @cp.consume_product(@product.id, {:uuid => consumer.uuid})
    nil.should == entitlements


    dist_version = create_distributor_version("SAMvBillion",
                                "Subscription Asset Manager Billion",
                               ["cores"])
    facts = {
      'distributor_version' => 'SAMvBillion'
    }
    @cp.update_consumer({:uuid => consumer['uuid'], :facts => facts})
    entitlements = @cp.consume_product(@product.id, {:uuid => consumer.uuid})
    entitlements.size.should == 1
  end
end
