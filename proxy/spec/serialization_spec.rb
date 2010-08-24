require 'candlepin_scenarios'

describe 'HATEOAS owners' do

  include CandlepinMethods
  it_should_behave_like 'Candlepin Scenarios'

  before(:each) do
    @owner = @cp.create_owner(random_string("test_owner"))
    owner_client = user_client(@owner, random_string('testuser'))

    # Create some consumers:
    consumer_client(owner_client, random_string(), "candlepin")
    consumer_client(owner_client, random_string(), "candlepin")

    # Create some pools:
    product1 = create_product()
    @cp.create_pool(product1.id, @owner.id, 2)
    @cp.create_pool(product1.id, @owner.id, 2)
  end

  it "serializes consumer links" do
    o = @cp.get_owner(@owner.id)
    pp o
    o.has_key?('consumers').should be_true
    o['consumers'].each do |c|
      c.keys.length.should == 1
      c.has_key?('href').should be_true
    end
  end

  it "serializes pool links" do
    o = @cp.get_owner(@owner.id)
    o.has_key?('pools').should be_true
    o['pools'].each do |p|
      p.keys.length.should == 1
      p.has_key?('href').should be_true
    end
  end
end
