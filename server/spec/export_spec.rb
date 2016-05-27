require 'spec_helper'
require 'candlepin_scenarios'
require 'tmpdir'
require 'openssl'


# An error occurred in an after(:all) hook.
#   RestClient::BadRequest: 400 Bad Request
#   occurred at /home/crog/.gem/ruby/gems/rest-client-1.6.7/lib/restclient/abstract_response.rb:48:in `return!'


describe 'Export', :serial => true do

  include CandlepinMethods
  include SpecUtils

  before(:all) do
    @exporter = StandardExporter.new
    @cp_export = @exporter.create_candlepin_export()

    @cert_export = StandardExporter.new
    @cert_export.create_certificate_export()
  end

  after(:all) do
    @exporter.cleanup
    @cert_export.cleanup
  end

  it 'exports consumer types' do
    consumer_types_dir = File.join(@cp_export.export_dir, 'consumer_types')

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
    exported_consumer = parse_file(
      File.join(@cp_export.export_dir, 'consumer.json')
    )
    exported_consumer['uuid'].should == @exporter.candlepin_client.uuid
  end

  it 'exports CDN URL' do
   exported_meta = parse_file(File.join(@cp_export.export_dir, 'meta.json'))
   exported_meta['cdnLabel'].should == @exporter.cdn_label
  end

  it 'should not include consumer json in entitlements' do
    Dir["#{@cp_export.export_dir}/entitlements/*.json"].find_all do |ent|
      parse_file(ent).has_key? 'consumer'
    end.should be_empty
  end

  # We export the old style of provided product DTOs, not the full
  # new reference to an actual product.
  it 'exports candlepin 0.x backward compatable product definitions' do
    Dir["#{@cp_export.export_dir}/entitlements/*.json"].find_all do |ent|
      json = parse_file(ent)
      pool = json['pool']
      pool['providedProducts'].each do |pp|
        pp['productId'].should_not be_nil
        pp['productName'].should_not be_nil
      end
      pool['derivedProvidedProducts'].each do |pp|
        pp['productId'].should_not be_nil
        pp['productName'].should_not be_nil
      end
    end
  end

  it 'exports entitlement certificates' do
    entitlement_certs_dir = File.join(@cp_export.export_dir,
      'entitlement_certificates')

    exported_entitlement_certs = files_in_dir(entitlement_certs_dir)

    available_certs ||= {}
    @exporter.candlepin_client.list_certificates.each do |c|
      available_certs[c['serial']] = c
    end

    exported_entitlement_certs.size.should == 6

    exported_entitlement_certs.each do |file|
      exported_cert = File.read(File.join(entitlement_certs_dir, file))
      exported_cert[0..26].should == "-----BEGIN CERTIFICATE-----"
    end
  end

  it 'exports regenerated entitlement certificates' do
    entitlement_certs_dir = File.join(@cp_export.export_dir,
      'entitlement_certificates')

    exported_entitlement_certs = files_in_dir(entitlement_certs_dir)

    # Regenerate some entitlement certificates, and generate a new manifest:
    @exporter.candlepin_client.regenerate_entitlement_certificates()
    new_export = @exporter.create_candlepin_export()
    new_entitlement_certs_dir = File.join(new_export.export_dir, 'entitlement_certificates')
    new_exported_entitlement_certs = files_in_dir(new_entitlement_certs_dir)

    # Cert filenames should be completely different now:
    (exported_entitlement_certs & new_exported_entitlement_certs).size.should == 0
  end

  it 'should support only exporting certificates' do
    entitlement_certs_dir = File.join(@cert_export.export_dir, 'entitlement_certificates')

    exported_entitlement_certs = files_in_dir(entitlement_certs_dir)

    available_certs ||= {}
    @cert_export.candlepin_client.list_certificates.each do |c|
      available_certs[c['serial']] = c
    end

    # All 5 should be there, despite one cert is for a virt product
    exported_entitlement_certs.size.should == 6

    exported_entitlement_certs.each do |file|
      exported_cert = File.read(File.join(entitlement_certs_dir, file))
      exported_cert[0..26].should == "-----BEGIN CERTIFICATE-----"
    end
  end

  it 'exported certs should have nothing else but meta and entitlement_certificate' do
    Dir.entries(@cert_export.export_dir).size.should == 4
  end

  it 'exports rules' do
    Base64.decode64(@cp.list_rules).should == File.read(
      File.join(@cp_export.export_dir, "rules2/rules.js"))

    # Should also contain legacy rules file:
    File.exists?(File.join(@cp_export.export_dir, "rules/default-rules.js")).should be true
  end

  it 'should export products' do
    prod_dir = File.join(@cp_export.export_dir, 'products')
    File.exists?(prod_dir).should be true
    files = Dir["#{prod_dir}/*.json"].find_all.collect {|file| file}
    files.length.should == @exporter.products.length

    expected_certs = 0
    @exporter.products.each do |name, product|
      File.exists?(File.join(prod_dir, "#{product.id}.json")).should be true
      # Count numeric ids
      if !!(product.id =~ /^[-+]?[0-9]+$/)
        expected_certs += 1
      end
    end

    # Only one product cert should get created as only products with
    # a numeric ID (real products) have certs created on export.
    certs = Dir["#{prod_dir}/*.pem"].find_all.collect {|file| file}
    certs.length.should == expected_certs
    File.exists?(File.join(prod_dir, "#{@exporter.products[:derived_provided_prod].id}.pem")).should be true
  end

  it 'should allow manifest creation with read only user' do
    @exporter = StandardExporter.new
    @cp_export = @exporter.create_candlepin_export_with_ro_user()
  end

end


