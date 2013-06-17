require 'candlepin_scenarios'
require 'tmpdir'
require 'openssl'

describe 'Candlepin Export' do

  include CandlepinMethods
  include ExportMethods
  include CandlepinScenarios

  before(:all) do
    create_candlepin_export()
    create_certificate_export()
  end

  after(:all) do
    #cleanup_candlepin_export()
    #cleanup_certificate_export()
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

  it 'should not include consumer json in entitlements' do
    Dir["#{@export_dir}/entitlements/*.json"].find_all do |ent|
      JSON.parse(File.read(ent)).has_key? 'consumer'
    end.should be_empty
  end

  it 'exports entitlement certificates' do
    entitlement_certs_dir = File.join(@export_dir, 'entitlement_certificates')

    exported_entitlement_certs = files_in_dir(entitlement_certs_dir)

    available_certs ||= {}
    @candlepin_client.list_certificates.each do |c|
      available_certs[c['serial']] = c
    end

    exported_entitlement_certs.size.should == 4

    exported_entitlement_certs.each do |file|
      exported_cert = File.read(File.join(entitlement_certs_dir, file))
      exported_cert[0..26].should == "-----BEGIN CERTIFICATE-----"
    end
  end

  it 'exports regenerated entitlement certificates' do
    entitlement_certs_dir = File.join(@export_dir, 'entitlement_certificates')

    exported_entitlement_certs = files_in_dir(entitlement_certs_dir)

    # Regenerate some entitlement certificates, and generate a new manifest:
    @candlepin_client.regenerate_entitlement_certificates()
    new_tmp_dir = File.join(Dir.tmpdir, random_string('candlepin-rspec'))
    new_export_dir = File.join(new_tmp_dir, "export")
    Dir.mkdir(new_tmp_dir)
    new_export_filename = @candlepin_client.export_consumer(new_tmp_dir)
    unzip_export_file(new_export_filename, new_tmp_dir)
    unzip_export_file(File.join(new_tmp_dir, "consumer_export.zip"), new_tmp_dir)
    new_entitlement_certs_dir = File.join(new_export_dir, 'entitlement_certificates')
    new_exported_entitlement_certs = files_in_dir(new_entitlement_certs_dir)

    # Cert filenames should be completely different now:
    (exported_entitlement_certs & new_exported_entitlement_certs).size.should == 0
  end

  it 'should support only exporting certificates' do
    entitlement_certs_dir = File.join(@export_dir_certs, 'entitlement_certificates')

    exported_entitlement_certs = files_in_dir(entitlement_certs_dir)

    available_certs ||= {}
    @candlepin_client.list_certificates.each do |c|
      available_certs[c['serial']] = c
    end

    # All 4 should be there, despite one cert is for a virt product
    exported_entitlement_certs.size.should == 4

    exported_entitlement_certs.each do |file|
      exported_cert = File.read(File.join(entitlement_certs_dir, file))
      exported_cert[0..26].should == "-----BEGIN CERTIFICATE-----"
    end
  end

  it 'exported certs should have nothing else but meta and entitlement_certificate' do
    Dir.entries(@export_dir_certs).size.should == 4
  end

  it 'exports rules' do
    Base64.decode64(@cp.list_rules).should == File.read(
      File.join(@export_dir, "rules2/rules.js"))

    # Should also contain legacy rules file:
    File.exists?(File.join(@export_dir, "rules/default-rules.js")).should be_true
  end
  
  it 'should export products' do
    prod_dir = File.join(@export_dir, 'products')
    File.exists?(prod_dir).should be_true
    files = Dir["#{prod_dir}/*.json"].find_all.collect {|file| file}
    files.length.should == 6
    
    files.each do |file|
      puts file
    end
    
    File.exists?(File.join(prod_dir, "#{@product1.id}.json")).should be_true
    File.exists?(File.join(prod_dir, "#{@product2.id}.json")).should be_true
    File.exists?(File.join(prod_dir, "#{@virt_product.id}.json")).should be_true
    File.exists?(File.join(prod_dir, "#{@product3.id}.json")).should be_true
    File.exists?(File.join(prod_dir, "#{@sub_product.id}.json")).should be_true
    File.exists?(File.join(prod_dir, "#{@sub_provided_prod.id}.json")).should be_true
    
    # Only one product cert should get created as only products with
    # a numeric ID (real products) have certs created on export.
    certs = Dir["#{prod_dir}/*.pem"].find_all.collect {|file| file}
    certs.length.should == 1
    File.exists?(File.join(prod_dir, "#{@sub_provided_prod.id}.pem")).should be_true
  end

end


