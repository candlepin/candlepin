require 'candlepin_api'
require 'hostedtest_api'

require 'pp'
require 'zip'

begin
  require 'qpid_proton'
rescue LoadError
  # This is okay
end

module CandlepinMethods

  include HostedTest

  # Wrapper for ruby API so we can track all owners we created and clean them
  # up. Note that this entails cleanup of all objects beneath that owner, so
  # most other objects can be created using the ruby API.
  def create_owner(owner_name, parent=nil, params={})
    params[:parent] = parent

    # Set the content access mode list for new owners. We do this here instead of doing it explicitly
    # in each test due to some test objects creating owners internally which need this list.
    if not params['contentAccessModeList']
      params['contentAccessModeList']= 'org_environment,test_access_mode,entitlement'
      params['contentAccessMode'] = 'entitlement'
    end

    owner = @cp.create_owner(owner_name, params)
    @owners << owner

    return owner
  end

  def delete_owner(owner, revoke=true, force=false)
    @cp.delete_owner(owner['key'], revoke, force)
    @owners.delete owner
  end

  # Loop to wait for the given job ID to complete, with timeout.
  def wait_for_job(job_id, timeout_seconds = 30, wait_interval = 2)
    states = ['FAILED', 'CANCELED', 'ABORTED', 'FINISHED']
    start_time = Time.now
    status = nil

    loop do
      status = @cp.get_job(job_id)

      break if (states.include? status['state']) || (timeout_seconds > 0 && Time.now - start_time > timeout_seconds)
      sleep wait_interval
    end

    return status
  end

  def create_product(id=nil, name=nil, params={})
    # If owner given in params, use it, if not, try to find @owner, if neither
    # is set error out.
    # NOTE: this is the owner key being passed in as a string
    if params[:owner]
      owner_key = params[:owner]
    elsif @owner
      owner_key = @owner['key']
    end
    if ! owner_key
      raise "Must call create_product with owner (key) param or set @owner in spec suite."
    end

    # For purposes of testing, you can omit id and name to create with
    # random strings.
    id ||= random_string(nil, true) #id has to be a number. OID encoding fails otherwise
    name ||= random_string('testproduct')
    params ||= {}

    #Product IDs are 32 characters or less
    id = id[0..31]

    product = @cp.create_product(owner_key, id, name, params)
    @created_products <<  product
    return product
  end

  def create_content(params={})
    # If owner given in params, use it, if not, try to find @owner, if neither
    # is set error out.
    # NOTE: this is the owner key being passed in as a string
    if params[:owner]
      owner = params[:owner]
    elsif @owner
      owner = @owner['key']
    end
    if ! owner
      raise "Must call create_product with owner param or set @owner in spec suite."
    end

    random_str = random_string(nil, true).to_i
    label = random_string("label")
    # Apologies, passing optional params straight through to prevent just pulling
    # each one out and putting it into a new hash.
    @cp.create_content(owner, random_str, random_str, label, "yum", random_str, params)
  end

  def create_batch_content(count=1, params={})
    # If owner given in params, use it, if not, try to find @owner, if neither
    # is set error out.
    # NOTE: this is the owner key being passed in as a string
    if params[:owner]
      owner = params[:owner]
    elsif @owner
      owner = @owner['key']
    end
    if ! owner
      raise "Must call create_product with owner param or set @owner in spec suite."
    end

    contents = []
    (0..count).each do |i|
      random_str = random_string(nil, true).to_i
      contents << @cp.create_content(owner, random_str, random_str, random_str, "yum",
                                     random_str, {:content_url => "/content/dist/rhel/$releasever#{i}/$basearch#{i}/debug#{i}"}, false)
    end

    @cp.create_batch_content(owner, contents)
  end

  # Wrapper for ruby API so we can track all distributor versions we created and clean them up.
  def create_distributor_version(dist_name, display_name, capabilities=[])
    dist_version = @cp.create_distributor_version(dist_name, display_name, capabilities)
    @dist_versions << dist_version

    return dist_version
  end

  def update_distributor_version(id, dist_name, display_name, capabilities=[])
    dist_version = @cp.update_distributor_version(id, dist_name, display_name, capabilities)
    if not @dist_versions.map { |dv| dist_version['id'] }.include?(id)
      @dist_versions << dist_version
    end
    return dist_version
  end

  # Wrapper for ruby API so we can track all content delivery network we created and clean them up.
  def create_cdn(key, name, url, cert=nil)
    cdn = @cp.create_cdn(key, name, url, cert)
    @cdns << cdn

    return cdn
  end

  def update_cdn(key, name, url, cert=nil)
    cdn = @cp.update_cdn(key, name, url, cert)
    if not @cdns.map { |item| cdn['label'] }.include?(key)
      @cdns << cdn
    end

    return cdn
  end

  def user_client(owner, user_name, readonly=false, superadmin=false)
    create_user(owner, user_name, 'password', readonly, superadmin)
    Candlepin.new(user_name, 'password')
  end

  def user_client_with_perms(username, password, perms)
    user = @cp.create_user(username, password)
    @users << user

    role = @cp.create_role(random_string('testrole'), perms)
    @cp.add_role_user(role['name'], username)

    return Candlepin.new(username, password)
  end

  # Creates the given user, with access to a role giving them full permissions
  # in the given owner:
  def create_user(owner, username, password, readonly=false, superadmin=false)
    user = @cp.create_user(username, password,superadmin)
    # Only append to the list of things to clean up if the @roles exists.
    # This is so the method can be used in before(:all) blocks.
    @users << user if not @users.nil?
    # Create a role for user to administer the given owner:
    perm = readonly ? 'READ_ONLY' : 'ALL'
    role = create_role(nil, owner['key'], perm)
    @cp.add_role_user(role['name'], user['username'])
    return user
  end

  # Create a role with a single permission. Additional permissions can be added
  # with the appropriate API calls.
  def create_role(name, owner_key, access_type)
    name ||= random_string 'test_role'
    perms = [{
                 :type => "OWNER",
                 :owner => {:key => owner_key},
                 :access => access_type,
             }]
    role = @cp.create_role(name, perms)

    # Only append to the list of things to clean up if the @roles exists.
    # This is so the method can be used in before(:all) blocks.
    @roles << role if not @roles.nil?
    return role
  end

  def upload_rules(rules)
    @rules = rules
    @cp.upload_rules(rules)
  end

  def consumer_client(cp_client, consumer_name, type=:system, username=nil, facts= {}, owner_key=nil)
    consumer = cp_client.register(consumer_name, type, nil, facts, username, owner_key)
    Candlepin.new(nil, nil, consumer.idCert.cert, consumer.idCert['key'])
  end

  def registered_consumer_client(consumer)
    Candlepin.new(nil, nil, consumer.idCert.cert, consumer.idCert['key'])
  end

  def trusted_consumer_client(uuid)
    Candlepin.new(nil, nil, nil, nil, "localhost", "8443", nil, uuid)
  end

  def trusted_user_client(username)
    Candlepin.new(username, nil, nil, nil, "localhost", "8443", nil, nil, true)
  end

  def random_string(prefix=nil, numeric_only=false)
    if prefix
      prefix = "#{prefix}-"
    end

    if numeric_only
      suffix = rand(9999999)
    else
      # This is actually a bit faster than using SecureRandom.  Go figure.
      o = [('a'..'z'), ('A'..'Z'), ('0'..'9')].map { |i| i.to_a }.flatten
      suffix = (0..7).map { o[rand(o.length)] }.join
    end
    "#{prefix}#{suffix}"
  end

  def check_for_hateoas(json)
    json.has_key?('href').should be true
    json.has_key?('id').should be true
  end

  def extension_from_cert(cert, extension_id)
    x509 = OpenSSL::X509::Certificate.new(cert)
    extensions_hash = Hash[x509.extensions.collect { |ext| [ext.oid, ext.to_der()] }]
    asn1_body = nil
    if extensions_hash[extension_id]
      asn1 = OpenSSL::ASN1.decode(extensions_hash[extension_id])
      OpenSSL::ASN1.traverse(asn1.value[1]) do| depth, offset, header_len, length, constructed, tag_class, tag|
        asn1_body = asn1.value[1].value[header_len, length]
      end
    end
    asn1_body
  end

  def get_extensions_hash(cert)
    x509 = OpenSSL::X509::Certificate.new(cert)
    extensions_hash = Hash[x509.extensions.collect { |ext| [ext.oid, ext.to_der()] }]

    extensions_hash.each do |key, val|

      asn1 = OpenSSL::ASN1.decode(val)
      OpenSSL::ASN1.traverse(asn1.value[1]) do| depth, offset, header_len, length, constructed, tag_class, tag|
        extensions_hash[key] = asn1.value[1].value[header_len, length]
      end

    end

    return extensions_hash

  end

end

class Export
  include CandlepinMethods

  attr_reader :tmp_dir
  attr_reader :export_dir
  attr_accessor :export_filename

  def initialize
    @tmp_dir = File.join(Dir.tmpdir, random_string('candlepin-rspec'))
    Dir.mkdir(@tmp_dir)

    @export_dir = File.join(@tmp_dir, "export")
  end

  def extract
    File.exist?(@export_filename).should == true
    self.class.unzip_export_file(@export_filename, @tmp_dir)
    self.class.unzip_export_file(File.join(@tmp_dir, "consumer_export.zip"), @tmp_dir)
  end

  def cleanup
    FileUtils.rm_rf(@tmp_dir)
  end

  class << self
    def unzip_export_file(filename, dest_dir)
      Zip::File::open(filename) do |zf|
        zf.each do |e|
          fpath = File.join(dest_dir, e.name)
          FileUtils.mkdir_p(File.dirname(fpath))
          zf.extract(e, fpath)
        end
      end
      filename.split('.zip')[0]
    end
  end
end

class Exporter
  include CandlepinMethods
  include CleanupHooks

  attr_reader :candlepin_client
  attr_reader :owner

  # Creating an export is fairly expensive, so we generally build them
  # in before(:all) blocks and use them throughout the various tests.
  #
  # One thing to keep in mind is that the exporter attempts to clean up
  # any products it created.  If you have imported an export, when the exporter
  # attempts to clean up it will fail to delete the product if the import
  # has subscriptions to the product.  The best way to deal with this is to
  # allow the after(:each) block in spec_helper.rb to clean up the import
  # owner and then clean up all the exporters in an after(:all) block.
  def initialize(opts={})
    cleanup_before()
    @opts ||= opts
    @orig_working_dir = Dir.pwd()
    @exports = []

    # Create a role for user to administer the given owner:
    @owner = create_owner(random_string('CPExport_owner'))
    user = create_user(@owner, random_string('CPExport_user'), 'password')

    owner_client = Candlepin.new(user['username'], 'password')

    @candlepin_client = consumer_client(owner_client, random_string('test_client'), "candlepin", user['username'])
  end

  def do_export(client, dest_dir, opts={}, uuid=nil)
    client.export_consumer(dest_dir, opts, uuid)
  end

  def create_candlepin_export
    export = Export.new
    export.export_filename = do_export(@candlepin_client, export.tmp_dir, @opts)
    export.extract()
    @exports << export
    export
  end

  def create_candlepin_export_with_ro_user
    ro_user_client = user_client(@owner, random_string('CPExport_user'), true)
    export = Export.new
    export.export_filename = do_export(ro_user_client, export.tmp_dir, @opts, @candlepin_client.uuid)
    export.extract()
    @exports << export
    export
  end

  def create_certificate_export
    export = Export.new
    export.export_filename = @candlepin_client.export_certificates(export.tmp_dir)
    export.extract()
    @exports << export
    export
  end

  def cleanup
    Dir.chdir(@orig_working_dir)
    @exports.each do |export|
      export.cleanup()
    end
    if RSpec.configuration.run_after_hook?
      cleanup_after()
    end
  end

  #Convenience methods to get the file name and directory of the most recent
  #export
  def export_filename
    @exports.last.export_filename
  end

  def export_dir
    @exports.last.export_dir
  end

  def tmp_dir
    @exports.last.tmp_dir
  end
end

class ImportUpdateBrandingExporter < Exporter
  include CandlepinMethods
  include CleanupHooks

  BRANDINGS_PER_POOL = 2
  POOLS_COUNT = 10

  attr_reader :export1_filename
  attr_reader :export2_filename

  def initialize
    super()
    #generate 1st (base) export
    product = create_product(random_string(), random_string())

    pools = create_pools_and_subs_with_brandings(product, @owner, candlepin_client)
    @export1_filename = create_candlepin_export.export_filename

    #generate 2nd (updated) export
    update_pools_or_subs_on_brandings(pools)
    @export2_filename = create_candlepin_export.export_filename
  end

  def create_pools_and_subs_with_brandings(product, owner, candlepin_client)
    pools = Array.new
    i = 0
    begin
      b = createBrandings(product['id'], BRANDINGS_PER_POOL)
      params = {:branding => b, :quantity => 2}
      end_date = Date.new(2025, 5, 29)
      pool = create_pool_and_subscription(owner['key'], product.id, 200 ,
                                          [] , '', '', '', nil, end_date, false, params)
      candlepin_client.consume_pool(pool.id, {:quantity => 1})
      pools[i] = pool
      i += 1
    end while i < POOLS_COUNT
    return pools
  end

  def createBrandings(productId, countOfBrandings)
    brandings = Array.new
    i = 0
    begin
      brandings[i] = createBranding(productId)
      i += 1
    end while i < countOfBrandings
    return brandings
  end

  def createBranding(productId, type=nil, name=nil)
    b = Hash.new
    b[:productId] = productId
    b[:type] = type || random_string("BrandingType")
    b[:name] = name || random_string("BrandingName")
    return b
  end

  def update_pools_or_subs_on_brandings(pools)
    pools.each do |pool|
      subOrPool = get_pool_or_subscription(pool)
      brandings = subOrPool['branding']
      brandings.each do |b|
        b['name'] = random_string("UPDATE-BrandingName")
      end
      subOrPool['branding'] = brandings
      update_pool_or_subscription(subOrPool)
    end
  end

  def to_s
    "ImportUpdateExporter [#{@owner['key']}, #@export1_filename, #@export2_filename, ...]"
  end
end

class ImportUpdateExporter < Exporter
  attr_reader :pool
  attr_reader :entitlement1

  def initialize
    super()
    product = create_product(random_string("test_prod"), random_string())
    end_date = Date.new(2025, 5, 29)
    @pool = create_pool_and_subscription(@owner['key'], product.id, 200, [], '', '12345', '6789', nil, end_date)
    @entitlement1 = @candlepin_client.consume_pool(@pool.id, {:quantity => 15})[0]
  end
end

class StandardExporter < Exporter
  attr_reader :products
  attr_reader :content
  attr_reader :cdn_label

  def initialize
    @cdn_label = random_string("test-cdn")
    super({:cdn_label => @cdn_label, :webapp_prefix => "webapp1", :api_url => "api1"})
    @products = {}
    @content = {}

    # Create an engineering product:
    @products[:eng_product] = create_product(rand(10000000).to_s, random_string('engproduct'))

    brandings = [
        {
            :productId => @products[:eng_product]['id'],
            :type => "OS",
            :name => "Branded Eng Product"
        }
    ]

    @products[:product1] = create_product(random_string('prod1'), random_string, {
        :multiplier => 2, :branding => brandings, :providedProducts => [@products[:eng_product]['id']]})
    @products[:product2] = create_product(random_string('prod2'), random_string())
    @products[:virt_product] = create_product(random_string('virt_product'), random_string('virt_product'), {
        :attributes => {
            :virt_only => true
        }
    })

    @products[:product3] = create_product(random_string('sub-prod'), random_string(), {
        :attributes => {
            :arch => "x86_64",
            :virt_limit => "unlimited"
        }
    })

    @products[:product_vdc] = create_product(random_string('prod-vdc'), random_string(), {
        :attributes => {
            :arch => "x86_64",
            :virt_limit => "unlimited", 'stacking_id' => 'stack-vdc'
        }
    })

    @products[:product_dc] = create_product(random_string('prod-dc'), random_string(), {
        :attributes => {
            :arch => "x86_64", 'stacking_id' => 'stack-dc'
        }
    })

    @products[:derived_provided_prod] = create_product(random_string(nil, true), random_string());

    @products[:derived_product] = create_product(random_string('sub-prov-prod'), random_string(), {
        "sockets" => "2", :providedProducts => [@products[:derived_provided_prod]['id']]
    })

    #this is for the update process
    @products[:product_up] = create_product(random_string('product_up'), random_string('product_up'))

    @content[:content1] = create_content({:metadata_expire => 6000,
                              :required_tags => "TAG1,TAG2"})
    @content[:arch_content] = create_content({:metadata_expire => 6000,
                                   :content_url => "/path/to/arch/specific/content",
                                   :required_tags => "TAG1,TAG2",
                                   :arches => "i386,x86_64"})

    @cp.add_content_to_product(@owner['key'], @products[:product1].id, @content[:content1].id)
    @cp.add_content_to_product(@owner['key'], @products[:product2].id, @content[:content1].id)
    @cp.add_content_to_product(@owner['key'], @products[:product2].id, @content[:arch_content].id)
    @cp.add_content_to_product(@owner['key'], @products[:derived_product].id, @content[:content1].id)

    end_date = Date.new(2025, 5, 29)

    @cp.create_pool(@owner['key'], @products[:product1].id, {
        :quantity => 2,
        :provided_products => [@products[:eng_product]['id']],
        :contract_number => '',
        :account_number => '12345',
        :order_number => '6789',
        :end_date => end_date,
        :branding => brandings,
        :subscription_id => random_str('source_sub'),
        :upstream_pool_id => random_str('upstream')
    })

    @cp.create_pool(@owner['key'], @products[:product2].id, {
        :quantity => 4,
        :provided_products => [],
        :contract_number => '',
        :account_number => '12345',
        :order_number => '6789',
        :end_date => end_date,
        :subscription_id => random_str('source_sub'),
        :upstream_pool_id => random_str('upstream')
    })

    @cp.create_pool(@owner['key'], @products[:virt_product].id, {
        :quantity => 2,
        :provided_products => [],
        :contract_number => '',
        :account_number => '12345',
        :order_number => '6789',
        :end_date => end_date,
        :subscription_id => random_str('source_sub'),
        :upstream_pool_id => random_str('upstream')
    })

    @cp.create_pool(@owner['key'], @products[:product3].id, {
        :quantity => 5,
        :provided_products => [],
        :contract_number => '',
        :account_number => '12345',
        :order_number => '6789',
        :end_date => end_date,
        :derived_product_id => @products[:derived_product]['id'],
        :derived_provided_products => [@products[:derived_provided_prod]['id']],
        :subscription_id => random_str('source_sub'),
        :upstream_pool_id => random_str('upstream')
    })

    @cp.create_pool(@owner['key'], @products[:product_up].id, {
        :quantity => 10,
        :provided_products => [],
        :contract_number => '',
        :account_number => '12345',
        :order_number => '6789',
        :end_date => end_date,
        :subscription_id => random_str('source_sub'),
        :upstream_pool_id => random_str('upstream')
    })

    @cp.create_pool(@owner['key'], @products[:product_vdc].id, {
        :quantity => 5,
        :provided_products => [],
        :contract_number => '',
        :account_number => '12345',
        :order_number => '6789',
        :end_date => end_date,
        :derived_product_id => @products[:product_dc]['id'],
        :subscription_id => random_str('source_sub'),
        :upstream_pool_id => random_str('upstream')
    })

    # Pool names is a list of names of instance variables that will be created
    pool_names = ["pool1", "pool2", "pool3", "pool4", "pool_up", "pool_vdc"]
    pool_products = [:product1, :product2, :product3, :virt_product, :product_up, :product_vdc]

    # Take the names and couple them together with keys in the @products hash.
    # Then for each pair, set an instance variable with the value of the list_pools
    # for that product.
    pool_names.zip(pool_products).each do |name, product|
      instance_variable_set("@#{name}", @cp.list_pools(:owner => @owner.id, :product => @products[product].id)[0] )
    end

    @candlepin_client.update_consumer({:facts => {"distributor_version" => "sam-1.3"}})
    @candlepin_consumer = @candlepin_client.get_consumer()

    ent_names = ["entitlement1", "entitlement2", "entitlement3", "entitlement_up", "entitlement_vdc"]
    ent_names.zip([@pool1, @pool2, @pool4, @pool_up, @pool_vdc]).each do |ent_name, pool|
      instance_variable_set("@#{ent_name}", @candlepin_client.consume_pool(pool.id, {:quantity => 1})[0])
    end
    # pool3 is special
    @candlepin_client.consume_pool(@pool3.id, {:quantity => 1})

    @cdn = create_cdn(@cdn_label, "Test CDN", "https://cdn.test.com")
  end

  def create_candlepin_export_update
    ## to determine if the process of updating the entitlement import is successful
    ## use the process for creating the import above to make one that is an update
    ## You must execute the create_candlepin_export method in the same test before
    ## this one.
    product1 = create_product(random_string(nil, true), random_string())
    product2 = create_product(random_string(nil, true), random_string())
    content = create_content({
        :metadata_expire => 6000,
        :required_tags => "TAG1,TAG2"
    })

    arch_content = create_content({
        :metadata_expire => 6000,
        :required_tags => "TAG3",
        :arches => "i686,x86_64"
    })

    @cp.add_content_to_product(@owner['key'], product1.id, content.id)
    @cp.add_content_to_product(@owner['key'], product2.id, content.id)
    @cp.add_content_to_product(@owner['key'], product2.id, arch_content.id)

    end_date = Date.new(2025, 5, 29)
    pool1 = @cp.create_pool(@owner['key'], product1.id, {
        :quantity => 12,
        :provided_products => [],
        :contract_number => '',
        :account_number => '12345',
        :order_number => '6789',
        :end_date => end_date,
        :subscription_id => random_str('source_sub'),
        :upstream_pool_id => random_str('upstream')
    })

    pool2 = @cp.create_pool(@owner['key'], product2.id, {
        :quantity => 14,
        :provided_products => [],
        :contract_number => '',
        :account_number => '12345',
        :order_number => '6789',
        :end_date => end_date,
        :subscription_id => random_str('source_sub'),
        :upstream_pool_id => random_str('upstream')
    })

    @candlepin_client.consume_pool(pool1.id, {:quantity => 1})
    @candlepin_client.consume_pool(pool2.id, {:quantity => 1})
    @candlepin_client.consume_pool(@pool_up.id, {:quantity => 4})

    @cp.unbind_entitlement(@entitlement2.id, :uuid => @candlepin_client.uuid)
    @cp.unbind_entitlement(@entitlement_up.id, :uuid => @candlepin_client.uuid)
    @candlepin_client.regenerate_entitlement_certificates_for_entitlement(@entitlement1.id)

    # Make changes to existing entities to verify they are updated as well
    @cp.update_product(@owner['key'], @products[:product1].id, {
        :name => "#{@products[:product1].name}-updated"
    })

    @cp.update_content(@owner['key'], @content[:content1].id, {
        :requiredTags => "TAG2,TAG4,TAG6"
    })

    create_candlepin_export()
  end

  def create_candlepin_export_update_no_ent
    ## We need to test the behavior of the manifest update when no entitlements
    ## are included
    ents = @candlepin_client.list_entitlements()
    # remove all entitlements
    ents.each do |ent|
      @cp.unbind_entitlement(ent.id, {:uuid => @candlepin_client.uuid})
    end

    create_candlepin_export()
  end
end

#To test virt_limit subscriptions and revocation of excess
#ENTITLEMENT_DERIVED pools
class VirtLimitExporter < Exporter

  def initialize
    @cdn_label = random_string("test-cdn")
    super({:cdn_label => @cdn_label, :webapp_prefix => "webapp1", :api_url => "api1"})
    # the before(:each) is not initialized yet, call create_product sans wrapper
    @product3 = create_product(random_string('p3'), random_string(), {
        :attributes => { :arch => "x86_64", :virt_limit => "2", :'multi-entitlement' => 'yes'}
    })
    end_date = Date.new(2025, 5, 29)
    create_pool_and_subscription(@owner['key'], @product3.id, 2, [], '', '12345', '6789', nil, end_date, false,
                                 {})
    # Pool names is a list of names of instance variables that will be created
    @pools = @cp.list_pools(:owner => @owner.id, :product => @product3.id)
    @pool3 = @pools.select{|i| i['type']=='NORMAL'}[0]
    @candlepin_client.update_consumer({:facts => {"distributor_version" => "sam-1.3"}})
    @candlepin_consumer = @candlepin_client.get_consumer()

    @candlepin_client.consume_pool(@pool3.id, {:quantity => 2})

    @cdn = create_cdn(@cdn_label, "Test CDN", "https://cdn.test.com")

  end

  def create_candlepin_export_update
    ents = @candlepin_client.list_entitlements()
    ents.each do |ent|
      @cp.unbind_entitlement(ent.id, {:uuid => @candlepin_client.uuid})
    end

    @candlepin_client.consume_pool(@pool3.id, {:quantity => 1})
    create_candlepin_export()
  end
end

class AsyncStandardExporter < StandardExporter

  def do_export(client, dest_dir, opts={}, uuid=nil)
    job = client.export_consumer_async(opts, uuid)
    # Wait a little longer here as export can take a bit of time
    wait_for_job(job["id"], 60)
    status = client.get_job(job["id"], true)
    if status["state"] == "FAILED"
      raise AsyncExportFailure.new(status)
    end

    result = status["resultData"]
    client.download_consumer_export(result["exportedConsumer"], result["exportId"], dest_dir)
  end
end

# We assume existence of queue allmsg that is bound to event exchange
class CandlepinQpid
  def initialize(address, cert, key)
    @address = address
    @qpid_crt = cert
    @qpid_key = key
  end

  def no_keys?
    !File.file?(@qpid_crt) or !File.file?(@qpid_key)
  end

  def stop
    `if which supervisorctl > /dev/null 2>&1; then sudo supervisorctl stop qpidd; else sudo systemctl stop qpidd; fi`
  end

  def start
    `if which supervisorctl > /dev/null 2>&1; then sudo supervisorctl start qpidd; else sudo systemctl start qpidd; fi`
  end

  #Create non-durable queue and bind it to an exchange
  def create_queue(qname, exchange, args = '')
    `sudo qpid-config -b amqps://localhost:5671 --ssl-certificate #{@qpid_crt} --ssl-key #{@qpid_key} add queue #{qname} #{args}`
    `sudo qpid-config -b amqps://localhost:5671 --ssl-certificate #{@qpid_crt} --ssl-key #{@qpid_key} bind #{exchange} #{qname} "#"`
  end

  #Force removes the queue
  def delete_queue(qname)
    `sudo qpid-config -b amqps://localhost:5671 --ssl-certificate #{@qpid_crt} --ssl-key #{@qpid_key} del queue #{qname} --force`
  end

  def receive(messages = -1, blocking = true, timeout = 90)
    if (no_keys?)
      raise "One or more Qpid keys were not found"
    end

    messenger = Qpid::Proton::Messenger::Messenger.new

    messenger.certificate = @qpid_crt
    messenger.private_key = @qpid_key
    messenger.blocking = blocking
    messenger.timeout = (timeout * 1000).to_i

    messenger.start
    messenger.subscribe(@address)

    msgs = []
    received = 0

    loop do
      messenger.receive(messages)

      while messenger.incoming.nonzero?
        msg = Qpid::Proton::Message.new
        messenger.get(msg)
        msgs << msg

        received += 1
      end

      break if received >= messages
    end

    messenger.stop

    return msgs
  end
end
