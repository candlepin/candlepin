require 'spec_helper'
require 'candlepin_scenarios'
require 'openssl'

describe 'Certificate Revocation List', :serial => true do

  include CandlepinMethods

  before do
    @owner = create_owner random_string('test_owner')
    @virt_prod = create_product random_string('virt')
    @monitoring_prod = create_product random_string('monitoring')

    #entitle owner for the virt and monitoring products.
    create_pool_and_subscription(@owner['key'], @monitoring_prod.id, 6,
				[], '', '', '', nil, nil, true)
    create_pool_and_subscription(@owner['key'], @virt_prod.id, 3)

    #create consumer
    username = random_string('billy')
    @user = create_user(@owner, username, 'password')
    user = Candlepin.new(username, 'password')
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
      serial = filter_serial(@virt_prod)
      revoked_serials.should_not include(serial)
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

  it 'should regenerate the on-disk crl and revoke' do
    crl = OpenSSL::X509::CRL.new File.read "/var/lib/candlepin/candlepin-crl.crl"
    old_time = File.mtime("/var/lib/candlepin/candlepin-crl.crl")
    #consume an entitlement, revoke it and check that CRL contains the new serial.
    @system.consume_product(@monitoring_prod.id)
    serial = filter_serial(@monitoring_prod)

    @system.revoke_all_entitlements()
    revoked_serials.should include(serial)

    # ensure that the on-disk crl got updated
    new_time = File.mtime("/var/lib/candlepin/candlepin-crl.crl")
    new_time.should_not == old_time
    crl = OpenSSL::X509::CRL.new File.read "/var/lib/candlepin/candlepin-crl.crl"
    crl.revoked.map { |i| i.serial }.should include(serial)
  end

  it 'should regenerate the on-disk crl' do
    old_time = File.mtime("/var/lib/candlepin/candlepin-crl.crl")
    # do some stuff
    @system.consume_product @monitoring_prod.id
    @system.consume_product @virt_prod.id

    serials = [filter_serial(@monitoring_prod),
               filter_serial(@virt_prod)]

    # Delete owner without revoking certs
    delete_owner(@owner, false)

    revoked_serials.should_not include(serials)
    # ensure that the on-disk crl got updated
    new_time = File.mtime("/var/lib/candlepin/candlepin-crl.crl")
    new_time.should_not == old_time
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
