require 'candlepin_scenarios'

describe 'Entitlement Resource' do

  include CandlepinMethods
  include CandlepinScenarios
  
  before do
    @owner = create_owner 'test_owner'
    @monitoring_prod = create_product(name='monitoring')
    @virt_prod= create_product(name='virtualization')

    #entitle owner for the virt and monitoring products.
    @cp.create_subscription(@owner.key, @monitoring_prod.id, 6)
    @cp.create_subscription(@owner.key, @virt_prod.id, 6)

    @cp.refresh_pools(@owner.key)

    #create consumer 
    user = user_client(@owner, 'billy')
    @system = consumer_client(user, 'system6')
  end 
  
  it 'should allow entitlement certificate regeneration based on product id' do
    @system.consume_product(@monitoring_prod.id)
    old_ent = @system.list_certificate_serials()[0]
    @cp.regenerate_entitlement_certificates_for_product(@monitoring_prod.id)
    new_ent = @system.list_certificate_serials()[0]
    old_ent.serial.should_not == new_ent.serial
  end

  it 'allows filtering certificates by serial number' do
    @system.consume_product(@monitoring_prod.id)
    @system.consume_product(@virt_prod.id)

    entitlements = @system.list_entitlements()

    # filter out entitlements for different products
    entitlements = entitlements.select do |ent|
      @system.get_pool(ent['pool']['id'])['productId'] == @monitoring_prod.id
    end

    # Just grab the cert serial ids
    entitlements.collect! do |ent|
      ent['certificates'].collect do |cert|
        cert['serial']['id']
      end
    end

    serials = entitlements
    serials.flatten!

    @system.list_certificates(serials).length.should == 1
  end

  it 'allows listing certificates by serial numbers' do
    @system.consume_product(@monitoring_prod.id)
    @system.list_certificate_serials.length.should == 1
  end

  it 'does not allow a consumer to view entitlements from a different consumer' do
    # Given
    bad_owner = create_owner 'baddie'
    bad_user = user_client(bad_owner, 'bad_dude')
    system2 = consumer_client(bad_user, 'wrong_system')

    # When
    lambda do
      system2.list_entitlements(:uuid => @system.uuid)

      # Then
    end.should raise_exception(RestClient::Forbidden)
  end
 
end
