require 'candlepin_scenarios'

describe 'Entitlement Resource' do

  include CandlepinMethods
  it_should_behave_like 'Candlepin Scenarios'
  
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
    certificates = @system.list_certificates()
    found = certificates.find {|item|
      ent = @system.get_entitlement(item['entitlement']['id'])
      pool = @system.get_pool(ent['pool']['id'])
      pool['productId'] == @monitoring_prod.id}

    @system.list_certificates([found['serial']['id']]).length.should == 1
  end

  it 'allows listing certificates by serial numbers' do
    @system.consume_product(@monitoring_prod.id)
    @system.list_certificate_serials.length.should == 1
  end
 
end
