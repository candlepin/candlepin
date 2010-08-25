require 'candlepin_scenarios'

describe 'Owner serialization' do

  include CandlepinMethods
  it_should_behave_like 'Candlepin Scenarios'

  before(:each) do
    @owner = @cp.create_owner(random_string("test_owner"))
    @owner_client = user_client(@owner, random_string('testuser'))

    # Create some pools:
    product1 = create_product()
    @cp.create_pool(product1.id, @owner.id, 2)
    @cp.create_pool(product1.id, @owner.id, 2)
  end

  it "references consumers as links" do
    
    # Create a consumer, somewhat expensive:
    consumer_client(@owner_client, random_string(), "candlepin")

    o = @cp.get_owner(@owner.id)
    o.has_key?('consumers').should be_true
    o['consumers'].each do |c|
      check_for_hateoas(c)
    end
  end

  it "references pools as links" do
    o = @cp.get_owner(@owner.id)
    o.has_key?('pools').should be_true
    o['pools'].each do |p|
      check_for_hateoas(p)
    end
  end

  it "references owners collection as links" do
    owners = @cp.list_owners()
    owners.each do |o|
      check_for_hateoas(o)
    end
  end

end

describe 'Consumer serialization' do

  include CandlepinMethods
  it_should_behave_like 'Candlepin Scenarios'

  before(:each) do
    @owner = @cp.create_owner(random_string("test_owner"))
    @owner_client = user_client(@owner, random_string('testuser'))
    @consumer_client = consumer_client(@owner_client, random_string(),
        "candlepin")
    @consumer = @cp.get_consumer(@consumer_client.uuid)
  end

  it "references consumers collection as links" do
    @cp.list_consumers().each do |c|
      check_for_hateoas(c)
    end
  end

  it "references owner as a link" do
    @consumer.has_key?('owner').should be_true
    check_for_hateoas(@consumer['owner'])
  end

  it "references entitlements as links" do
    product1 = create_product()
    pool1 = @cp.create_pool(product1.id, @owner.id, 2)
    @consumer_client.consume_pool(pool1.id)

    # Look this up last so it has everything we created:
    @consumer = @cp.get_consumer(@consumer_client.uuid)

    @consumer['entitlements'].each do |e|
      check_for_hateoas(e)
    end
  end

end

describe 'Pool serialization' do

  include CandlepinMethods
  it_should_behave_like 'Candlepin Scenarios'

  before(:each) do
    @owner = @cp.create_owner(random_string("test_owner"))
    @owner_client = user_client(@owner, random_string('testuser'))
    product1 = create_product()
    @pool = @cp.create_pool(product1.id, @owner.id, 2)
  end

  it 'references owner as a link' do
    @pool.has_key?('owner').should be_true
    check_for_hateoas(@pool['owner'])
  end

  it 'references entitlements as links' do
    consumer_client = consumer_client(@owner_client, random_string(),
        "candlepin")
    consumer_client.consume_pool(@pool.id)

    @pool = @cp.get_pool(@pool.id)
    pp @pool
    @pool.has_key?('entitlements').should be_true
    @pool['entitlements'].each do |e|
      check_for_hateoas(e)
    end
  end
end

