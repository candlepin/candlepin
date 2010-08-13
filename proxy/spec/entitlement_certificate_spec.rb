require 'candlepin_scenarios'

describe 'Entitlement Certificate' do
  include CandlepinMethods
  it_should_behave_like 'Candlepin Scenarios'

  before(:each) do
    @owner = create_owner 'test_owner'
    monitoring = create_product()

    @cp.create_subscription(@owner.id, monitoring.id, 10)
    @cp.refresh_pools @owner.key

    user = user_client(@owner, 'billy')

    @system = consumer_client(user, 'system1')
    @system.consume_product monitoring.id
  end

  it 'is available after consuming an entitlement' do
    @system.list_certificates.length.should == 1
  end

  it 'can be manually regenerated for a consumer' do
    @system.list_certificates.length.should == 1
    old_certs = @system.list_certificates()
    @system.regenerate_entitlement_certificates()
    
    new_certs = @system.list_certificates()
    old_certs.size.should == new_certs.size
    old_ids = old_certs.map { |cert| cert['serial']['id']}
    new_ids = new_certs.map { |cert| cert['serial']['id']}
    (old_ids & new_ids).size.should == 0
  end

  it 'can be manually regenerated for a product' do
    coolapp = create_product
    @cp.create_subscription(@owner.id, coolapp.id, 10)
    @cp.refresh_pools @owner.key
    @system.consume_product coolapp.id
    
    @cp.regenerate_entitlement_certificates_for_product(coolapp.id)  
    
    @system.list_certificates.length.should == 2
    old_certs = @system.list_certificates()
    @system.regenerate_entitlement_certificates()
    
    new_certs = @system.list_certificates()
    old_certs.size.should == new_certs.size
    old_ids = old_certs.map { |cert| cert['serial']['id']}
    new_ids = new_certs.map { |cert| cert['serial']['id']}
    (old_ids & new_ids).size.should == 0
  end

end
