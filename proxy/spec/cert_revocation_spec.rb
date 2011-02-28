require 'candlepin_scenarios'

describe 'Certificate Revocation List' do

  include CandlepinMethods
  it_should_behave_like 'Candlepin Scenarios'
  
  before do
    @owner = create_owner random_string('test_owner')
    @virt_prod = create_product random_string('virt')
    @monitoring_prod = create_product random_string('monitoring')

    #entitle owner for the virt and monitoring products.
    @cp.create_subscription(@owner.key, @monitoring_prod.id, 6)
    @cp.create_subscription(@owner.key, @virt_prod.id, 3)
    
    @cp.refresh_pools(@owner.key)

    #create consumer 
    user = user_client(@owner, 'billy')
    @system = consumer_client(user, 'system6')
  end 

  it 'contains the serial of entitlement(s) revoked' do
      #consume an entitlement, revoke it and check that CRL contains the new serial.
      @system.consume_product(@monitoring_prod.id)
      serial = filter_serial(@monitoring_prod)
      
      @system.revoke_all_entitlements()
      revoked_serials.should include(serial)
  end
  
  it 'does not contain the serial of a valid entitlement(s)' do
      @system.consume_product(@virt_prod.id)
      revoked_serials.should be_empty
  end 
  
  it 'contains the serials of revoked entitlement(s) and not the unrevoked ones' do
      virt_entitlement = @system.consume_product(@virt_prod.id)
      @system.consume_product(@monitoring_prod.id)
      serial = filter_serial(@virt_prod)      
      @system.unbind_entitlement(virt_entitlement[0].id)
      revoked_serials.should include(serial)
  end

  it 'does not contain the serial of unrevoked entitlement(s) when entitlement(s) are revoked' do
      mp_entitlement = @system.consume_product(@monitoring_prod.id)
      @system.consume_product(@virt_prod.id)
      serial = filter_serial(@virt_prod)
      @system.unbind_entitlement(mp_entitlement[0].id)
      revoked_serials.should_not include(serial)
  end

  it 'should not contain entitlements from an owner deleted with revoke=false' do
    @system.consume_product @monitoring_prod.id
    @system.consume_product @virt_prod.id

    serials = [filter_serial(@monitoring_prod),
               filter_serial(@virt_prod)]

    # Delete owner without revoking certs
    delete_owner(@owner, false)

    revoked_serials.should_not include(serials)
  end
  
  def filter_serial(product, consumer=@system)
    entitlement = consumer.list_entitlements.find do |ent|
      @cp.get_pool(ent.pool.id).productId == product.id
    end

    return entitlement.certificates[0].serial.id unless entitlement.certificates.empty?
  end
  
  def revoked_serials
    return @cp.get_crl.revoked.map {|entry| entry.serial.to_i }
  end
end
