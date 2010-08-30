require 'candlepin_scenarios'

describe 'Consumer Resource' do

  include CandlepinMethods
  it_should_behave_like 'Candlepin Scenarios'

  it 'allows super admins to see all consumers' do
    owner1 = create_owner random_string('test_owner1')
    user1 = user_client(owner1, random_string("user1"))
    consumer1 = consumer_client(user1, random_string("consumer1"))

    owner2 = create_owner random_string('test_owner2')
    user2 = user_client(owner2, random_string("user2"))
    consumer2 = consumer_client(user2, random_string("consumer2"))

    uuids = []
    @cp.list_consumers.each do |c|
      # These are HATEOAS serialized consumers, the ID is the numeric DB
      # ID, so pull the UUID off the URL.
      # TODO: Find a better way once client is more HATEOASy.
      uuids << c['href'].split('/')[-1]
    end
    uuids.include?(consumer1.uuid).should be_true
    uuids.include?(consumer2.uuid).should be_true
  end

  it 'lets an owner admin see only their consumers' do
    owner1 = create_owner random_string('test_owner1')
    user1 = user_client(owner1, random_string("user1"))
    consumer1 = consumer_client(user1, random_string("consumer1"))

    owner2 = create_owner random_string('test_owner2')
    user2 = user_client(owner2, random_string("user2"))
    consumer2 = consumer_client(user2, random_string("consumer2"))

    user2.list_consumers.length.should == 1
  end

  it 'lets an owner see only their system consumer types' do
    owner1 = create_owner random_string('test_owner1')
    user1 = user_client(owner1, random_string("user1"))
    consumer1 = consumer_client(user1, random_string("consumer1"))
    consumer2 = consumer_client(user1, random_string("consumer2"), 'candlepin')

    user1.list_consumers({:type => 'system'}).length.should == 1
  end

  it 'lets a super admin see a peson consumer with a given username' do
    owner1 = create_owner random_string('test_owner1')
    username = random_string "user1"
    user1 = user_client(owner1, username)
    consumer1 = consumer_client(user1, random_string("consumer1"), 'person')

    @cp.list_consumers({:type => 'person',
                       :username => username}).length.should == 1
  end

  it 'lets a super admin create person consumer for another user' do
    owner1 = create_owner random_string('test_owner1')
    username = random_string "user1"
    user1 = user_client(owner1, username)
    consumer1 = consumer_client(@cp, random_string("consumer1"), 'person',
                                username)

    @cp.list_consumers({:type => 'person',
                       :username => username}).length.should == 1
  end

  it 'does not let an owner admin create person consumer for another owner' do
    owner1 = create_owner random_string('test_owner1')
    username = random_string "user1"
    user1 = user_client(owner1, username)

    owner2 = create_owner random_string('test_owner2')
    user2 = user_client(owner2, random_string("user2"))

    lambda {
      consumer_client(user2, random_string("consumer1"), 'person',
                      username)
    }.should raise_exception(RestClient::BadRequest)
  end

end
