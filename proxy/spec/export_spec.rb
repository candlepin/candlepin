require 'candlepin_scenarios'
require 'tmpdir'
require 'openssl'

describe 'Candlepin Export' do

  include CandlepinMethods
  include ExportMethods
  include CandlepinScenarios

  before(:all) do
    create_candlepin_export()
  end

  after(:all) do
    cleanup_candlepin_export()
  end

  it 'exports consumer types' do
    consumer_types_dir = File.join(@export_dir, 'consumer_types')

    exported_consumer_types = files_in_dir(consumer_types_dir)

    available_consumer_types ||= {}
    @cp.list_consumer_types.each do |t|
      available_consumer_types[t['label']] = t
    end

    exported_consumer_types.size.should == available_consumer_types.size

    exported_consumer_types.each do |file|
      consumer_type = parse_file(File.join(consumer_types_dir, file))
      available_consumer_types[consumer_type['label']].should_not be_nil
    end
  end

  it 'exports consumers' do
    exported_consumer = parse_file(File.join(@export_dir, 'consumer.json'))
    exported_consumer['uuid'].should == @candlepin_client.uuid
  end

  it 'exports only physical entitlements' do
    entitlements_dir = File.join(@export_dir, 'entitlements')

    available_entitlements = {}
    @candlepin_client.list_entitlements.each do |ent|
      pool = @cp.get_pool ent.pool.id
      available_entitlements[pool.productId] = ent
    end

    # We are expecting the virt_only entitlement to not be exported
    exported_entitlements = files_in_dir(entitlements_dir)
    exported_entitlements.should have(2).things

    exported_entitlements.each do |file|
      exported_entitlement = parse_file(File.join(entitlements_dir, file))
      available_entitlements[exported_entitlement.pool.productId].should_not be_nil
    end
  end

  it 'should not export any virt product entitlements' do
    Dir["#{@export_dir}/entitlements/*.json"].find_all do |ent|
      JSON.parse(File.read(ent)).pool['productName'].include? 'virt_product'
    end.should be_empty
  end

  it 'should not export virt product entitlement certificates' do
     Dir["#{@export_dir}/entitlement_certificates/*.pem"].find_all do |ent|
      cert = OpenSSL::X509::Certificate.new(File.read(ent))
      get_extension(cert, '1.3.6.1.4.1.2312.9.4.1').include? 'virt_product'
    end.should be_empty
  end

  it 'does not trigger flex expiry rules' do
    flex_ent_file = File.join(@export_dir, 'entitlements', '%s.json' %
        @flex_entitlement.id)
    exported_ent = parse_file(flex_ent_file)
    # Check the flex expiry product did not modify end dates:
    date = Date.strptime(exported_ent['endDate'])
    date.month.should == 5
    date.day.should == 29
    date.year.should == 2025

    # Flex days should *not* have been set, Candlepin consumers do not
    # execute the rules. The product will carry the attribute downstream.
    exported_ent['flexExpiryDays'].should == 0
  end

  it 'exports entitlement certificates' do
    entitlement_certs_dir = File.join(@export_dir, 'entitlement_certificates')

    exported_entitlement_certs = files_in_dir(entitlement_certs_dir)

    available_certs ||= {}
    @candlepin_client.list_certificates.each do |c|
      available_certs[c['serial']] = c
    end

    # Only 2 of the 3 should be there, as one cert is for a virt product
    exported_entitlement_certs.size.should == 2

    exported_entitlement_certs.each do |file|
      exported_cert = File.read(File.join(entitlement_certs_dir, file))
      exported_cert[0..26].should == "-----BEGIN CERTIFICATE-----"
    end
  end

  it 'exports rules' do
    Base64.decode64(@cp.list_rules).should == File.read(
      File.join(@export_dir, "rules/rules.js"))
  end

end


