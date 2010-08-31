require 'candlepin_scenarios'

describe 'Entitlement Resource' do

  include CandlepinMethods
  it_should_behave_like 'Candlepin Scenarios'
  
  before do
    @owner = create_owner 'test_owner'
    @monitoring_prod = create_product(name='monitoring')

    #entitle owner for the virt and monitoring products.
    @cp.create_subscription(@owner.key, @monitoring_prod.id, 6)

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
 
end
