require 'candlepin_scenarios'

describe 'Products with "user-license" attributes' do
  include CandlepinMethods
  include CandlepinScenarios

  before(:each) do
    @owner = create_owner 'awesome-shop'
    @user = user_client(@owner, 'jay')

    @editor = create_product(345, random_string('editor'))
    @tooling = create_product(nil, random_string('tooling'), {
      :attributes => {
        :user_license => 'unlimited',
        :user_license_product => 345,
        :requires_consumer_type => :person
      }
    })

    @cp.create_subscription(@owner['key'], @tooling.id, 15)
    @cp.refresh_pools @owner['key']
  end

  it 'should revoke entitlements of consumers of sub-pools when pool is deleted' do
    consumer = consumer_client(@user, 'jay-consumer', :person)
    consumer.consume_product @tooling.id

    sys2 = consumer_client(@user, 'sys2')
    sys2.consume_product @editor.id

    # unregister the person
    @user.unregister(consumer.uuid)

    sys2.list_entitlements.should be_empty
  end

  it 'should create a sub-pool when the parent pool is consumed' do
    consumer = consumer_client(@user, 'jay-consumer', :person)
    consumer.consume_product @tooling.id

    sys1 = consumer_client(@user, 'sys1')

    sys1.list_pools({:consumer => sys1.uuid,
        :product => @editor.id}).should_not be_empty
  end

  it 'should create a sub-pool that is restricted to the user\'s consumers' do
    consumer_client(@user, 'jay-consumer', :person).consume_product @tooling.id

    user = user_client(@owner, 'another-guy')
    system = consumer_client(user, 'different-box')

    system.list_pools(:consumer => system.uuid).should be_empty
  end

  it 'should have a certificate for an entitlement from the sub-pool' do
    consumer = consumer_client(@user, 'jay-consumer', :person)
    consumer.consume_product @tooling.id

    sys2 = consumer_client(@user, 'sys2')
    sys2.consume_product @editor.id

    # Not the most robust, but this is what the cuke test was checking...
    sys2.list_certificates.should have(1).things
  end

  it 'should delete the sub-pool when the person consumer is deleted' do
    consumer = consumer_client(@user, 'jay-consumer', :person)
    consumer.consume_product @tooling.id

    sys1 = consumer_client(@user, 'sys1')

    @user.unregister(consumer.uuid)

    sys1.list_pools(:consumer => sys1.uuid).should be_empty
  end

end

