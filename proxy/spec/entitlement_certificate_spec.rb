require 'candlepin_scenarios'

describe 'Entitlement Certificate' do
  include CandlepinMethods
  it_should_behave_like 'Candlepin Scenarios'

  it 'should be available after consuming an entitlement' do
    # Given
    owner = create_owner 'test_owner'
    monitoring = create_product 'monitoring'

    # Create a subscription
    @cp.create_subscription(owner.id, monitoring.id, 10)
    @cp.refresh_pools owner.key

    # Set up user
    billy = user_client(owner, 'billy')

    # And register a consumer
    system1 = consumer_client(billy, 'system1')

    #When
    system1.consume_product monitoring.id

    #Then
    system1.get_certificates.length.should == 1
  end
end