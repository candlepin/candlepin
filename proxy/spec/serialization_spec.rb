require 'candlepin_scenarios'

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

  it "references owner as a link" do
    @consumer.has_key?('owner').should be_true
    check_for_hateoas(@consumer['owner'])
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

end

describe 'Entitlement serialization' do

  include CandlepinMethods
  it_should_behave_like 'Candlepin Scenarios'

  before(:each) do
    @owner = @cp.create_owner(random_string("test_owner"))
    @owner_client = user_client(@owner, random_string('testuser'))
    product1 = create_product()
    @pool = @cp.create_pool(product1.id, @owner.id, 2)
    consumer_client = consumer_client(@owner_client, random_string(),
        "candlepin")
    ent_id = consumer_client.consume_pool(@pool.id)[0]['id']
    @ent = @cp.get_entitlement(ent_id)
  end

  it 'references pool as a link' do
    @ent.has_key?('pool').should be_true
    check_for_hateoas(@ent['pool'])
  end
end

