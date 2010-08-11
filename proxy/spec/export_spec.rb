require 'candlepin_scenarios'
require 'zip/zip'
require 'tmpdir'

describe 'Candlepin Export' do

  include CandlepinMethods
  it_should_behave_like 'Candlepin Scenarios'

  before(:all) do
    # WARNING: Creating export is fairly expensive, so using a before all to
    # create one and use it for all tests. Because before(:all) runs before
    # any before(:each), we cannot use the normal helper methods. :( As such,
    # creating one owner, and cleaning it up manually:
    @cp = Candlepin.new('admin', 'admin')
    @owner = @cp.create_owner(random_string('test_owner'))

    owner_client = user_client(@owner, random_string('testuser'))
    product1 = create_product()
    product2 = create_product()
    pool1 = @cp.create_pool(product1.id, @owner.id, 2)
    pool2 = @cp.create_pool(product2.id, @owner.id, 4)
    @candlepin_client = consumer_client(owner_client, random_string(),
        "candlepin")
    @candlepin_client.consume_pool(pool1.id)
    @candlepin_client.consume_pool(pool2.id)

    # Make a temporary directory where we can safely extract our archive:
    @tmp_dir = File.join(Dir.tmpdir, random_string('candlepin-rspec'))
    @export_dir = File.join(@tmp_dir, "export")
    Dir.mkdir(@tmp_dir)

    @export_filename = @candlepin_client.export_consumer(@tmp_dir)
    # Save current working dir so we can return later:
    @orig_working_dir = Dir.pwd()

    File.exist?(@export_filename).should == true
    unzip_export_file(@export_filename, @tmp_dir)
    unzip_export_file(File.join(@tmp_dir, "consumer_export.zip"), @tmp_dir)
  end

  after(:all) do
    Dir.chdir(@orig_working_dir)

    FileUtils.rm_rf(@tmp_dir)
    @cp.delete_owner(@owner.id)
  end

  it 'should export consumer types' do
    consumer_types_dir = File.join(@export_dir, 'consumer_types')

    exported_consumer_types = files_in_dir(consumer_types_dir)

    available_consumer_types ||= {}
    @cp.list_consumer_types.each do |t|
      available_consumer_types[t['label']] = t
    end

    exported_consumer_types.size.should == available_consumer_types.size

    exported_consumer_types.each do |file|
      consumer_type = parse_file(File.join(consumer_types_dir, file))
      available_consumer_types[consumer_type['label']].should_not == nil
    end
  end

  it 'should export consumers' do
    exported_consumer = parse_file(File.join(@export_dir, 'consumer.json'))
    exported_consumer['uuid'].should == @candlepin_client.uuid
  end

  it 'should export entitlements' do
    entitlements_dir = File.join(@export_dir, 'entitlements')

    available_entitlements ||= {}
    @candlepin_client.list_entitlements.each do |e|
      available_entitlements[e['productId']] = e
    end
    exported_entitlements = files_in_dir(entitlements_dir)

    exported_entitlements.size.should == available_entitlements.size

    exported_entitlements.each do |file|
      exported_entitlement = parse_file(File.join(entitlements_dir, file))
      available_entitlements[exported_entitlement['productId']].should_not == nil
    end
  end

  it 'should export entitlement certificates' do
    entitlement_certs_dir = File.join(@export_dir, 'entitlement_certificates')

    exported_entitlement_certs = files_in_dir(entitlement_certs_dir)

    available_certs ||= {}
    @candlepin_client.list_certificates.each do |c|
      available_certs[c['serial']] = c
    end

    exported_entitlement_certs.size.should == available_certs.size

    exported_entitlement_certs.each do |file|
      exported_cert = load_file(File.join(entitlement_certs_dir, file))
      exported_cert[0..26].should == "-----BEGIN CERTIFICATE-----"
    end
  end

  it 'should export rules' do
    Base64.decode64(@cp.list_rules).should == load_file(
      File.join(@export_dir, "rules/rules.js"))
  end

  def unzip_export_file(filename, dest_dir)
    Zip::ZipFile::open(filename) do |zf|
       zf.each do |e|
         fpath = File.join(dest_dir, e.name)
         FileUtils.mkdir_p(File.dirname(fpath))
         zf.extract(e, fpath)
       end
    end
    filename.split('.zip')[0]
  end

  def parse_file(filename)
    JSON.parse(load_file(filename))
  end

  def load_file(filename)
    contents = ''
    f = File.open(filename, "r")
    f.each_line do |line|
      contents += line
    end
    return contents
  end

  def files_in_dir(dir_name)
    Dir.entries(dir_name).select {|e| e != '.' and e != '..' }
  end

end
