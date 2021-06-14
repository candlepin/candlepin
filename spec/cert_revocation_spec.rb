require 'spec_helper'
require 'candlepin_scenarios'
require 'openssl'
require 'unpack'

describe 'Certificate Revocation List', :serial => true do

  include CandlepinMethods
  include VirtHelper
  include CertificateMethods


  before do
    @owner = create_owner random_string('test_owner')
    @virt_prod = create_product random_string('virt')
    @monitoring_prod = create_product random_string('monitoring')

    #entitle owner for the virt and monitoring products.
    @monitoring_pool = create_pool_and_subscription(@owner['key'], @monitoring_prod.id, 6, [], '', '', '', nil, nil, true)
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
    expect(crl.revoked.map { |i| i.serial }).to include(serial)
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

  it 'should put revoked CDN cert on CRL' do
    cdn_label = random_string("test-cdn")

    serial = { 'expiration' => Date.today.next_year }

    certificate = {
        'key' => 'test-key',
        'cert' => 'test-cert',
        'serial' => serial
    }
    cdn = create_cdn(cdn_label,
                     "Test CDN",
                     "https://cdn.test.com",
                     certificate)
    cdn.id.should_not be nil

    @cp.delete_cdn(cdn_label)

    expect(revoked_serials).to include(cdn.certificate.serial.serial)
  end

  it 'should put revoked ueber cert on CRL' do
    cert_serial = @cp.generate_ueber_cert(@owner['key']).serial.serial
    delete_owner(@owner)
    expect(revoked_serials).to include(cert_serial)
  end

  it 'should put revoked id cert on CRL' do
    id_cert = OpenSSL::X509::Certificate.new(@system.identity_certificate)
    @system.unregister
    expect(revoked_serials).to include(id_cert.serial.to_i)
  end

  it 'should put revoked content access cert on CRL' do
    skip("candlepin running in standalone mode") unless is_hosted?

    cam_owner = create_owner(random_string("test_owner"), nil, {
      'contentAccessModeList' => "org_environment,entitlement",
      'contentAccessMode' => "org_environment"
    })

    username = random_string('bob')
    user = create_user(cam_owner, username, 'password')
    user_client = Candlepin.new(username, 'password')
    new_system = consumer_client(user_client, random_string('system'),:system, nil,
                                 {'system.certificate_version' => '3.2'})
    certs = new_system.list_certificates
    certs.length.should == 1

    cert_serial = certs[0].serial.serial
    expect(revoked_serials).to_not include(cert_serial)

    @cp.update_owner(cam_owner['key'], {'contentAccessMode' => "entitlement"})
    new_system.list_certificates.length.should == 0
    expect(revoked_serials).to include(cert_serial)
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
