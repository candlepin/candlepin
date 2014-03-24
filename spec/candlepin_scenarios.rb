require 'candlepin_api'

require 'pp'
require 'zip/zip'

module CandlepinMethods

  # Wrapper for ruby API so we can track all owners we created and clean them
  # up. Note that this entails cleanup of all objects beneath that owner, so
  # most other objects can be created using the ruby API.
  def create_owner(owner_name, parent=nil)
    owner = @cp.create_owner(owner_name, {:parent => parent})
    @owners << owner

    return owner
  end

  def delete_owner(owner, revoke=true)
    @cp.delete_owner(owner['key'], revoke)
    @owners.delete owner
  end

  # Loop to wait for the given job ID to complete, with timeout.
  def wait_for_job(job_id, timeout_seconds)
    states = ['FINISHED', 'CANCELED', 'FAILED']
    wait_interval = 2 # seconds
    total_taken = 0
    while total_taken < timeout_seconds
      sleep wait_interval
      total_taken += wait_interval
      status = @cp.get_job(job_id)
      if states.include? status['state']
        return
      end
    end
  end

  # Wrapper for the ruby API's create product. Products do not get cleaned
  # up when an owner is deleted so we will need to track them.
  def create_product(id=nil, name=nil, params={})
    # For purposes of testing, you can omit id and name to create with
    # random strings.
    id ||= random_string(nil, true) #id has to be a number. OID encoding fails otherwise
    name ||= random_string('testproduct')
    params ||= {}

    #Product IDs are 32 characters or less
    id = id[0..31]

    product = @cp.create_product(id, name, params)
    @created_products <<  product
    return product
  end

  def create_content(params={})
    random_str = random_string(nil, true).to_i
    # Apologies, passing optional params straight through to prevent just pulling
    # each one out and putting it into a new hash.
    @cp.create_content(random_str, random_str, random_str, "yum",
      random_str, params)
  end

  def create_batch_content(count=1)
    contents = []
    (0..count).each do |i|
      random_str = random_string(nil, true).to_i
      contents << @cp.create_content(random_str, random_str, random_str, "yum",
        random_str, {:content_url => "/content/dist/rhel/$releasever#{i}/$basearch#{i}/debug#{i}"}, false)
    end
    @cp.create_batch_content(contents)
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

  def user_client(owner, user_name, readonly=false)
    create_user(owner, user_name, 'password', readonly)
    Candlepin.new(user_name, 'password')
  end

  def user_client_with_perms(owner, username, password, perms)
    user = @cp.create_user(username, password)
    @users << user

    role = @cp.create_role(random_string('testrole'), perms)
    @cp.add_role_user(role['id'], @username)

    return Candlepin.new(username, password)
  end

  # Creates the given user, with access to a role giving them full permissions
  # in the given owner:
  def create_user(owner, username, password, readonly=false)
    user = @cp.create_user(username, password)
    # Only append to the list of things to clean up if the @roles exists.
    # This is so the method can be used in before(:all) blocks.
    @users << user if not @users.nil?
    # Create a role for user to administer the given owner:
    perm = readonly ? 'READ_ONLY' : 'ALL'
    role = create_role(nil, owner['key'], perm)
    @cp.add_role_user(role['id'], user['username'])
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

  # List all the pools for the given owner, and find one that matches
  # a specific subscription ID. (we often want to verify what pool was used,
  # but the pools are created indirectly after a refresh so it's hard to
  # locate a specific reference without this)
  def find_pool(owner_id, sub_id, activeon=nil)
    pools = @cp.list_pools({:owner => owner_id, :activeon => activeon})
    pools.each do |pool|
      if pool['subscriptionId'] == sub_id
        return pool
      end
    end
    return nil
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
    json.has_key?('href').should be_true
    json.has_key?('id').should be_true
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

  def is_hosted?
    return ! @cp.get_status()['standalone']
  end

  def is_standalone?
    return @cp.get_status()['standalone']
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
      Zip::ZipFile::open(filename) do |zf|
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

    @candlepin_client = consumer_client(owner_client, random_string('test_client'),
        "candlepin", user['username'])

  end

  def create_candlepin_export
    export = Export.new
    export.export_filename = @candlepin_client.export_consumer(export.tmp_dir, @opts)
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
end

class StandardExporter < Exporter
  attr_reader :products
  attr_reader :cdn_label

  def initialize
    @cdn_label = random_string("test-cdn")
    super({:cdn_label => @cdn_label, :webapp_prefix => "webapp1", :api_url => "api1"})
    @products = {}
    # the before(:each) is not initialized yet, call create_product sans wrapper
    @products[:product1] = create_product(random_string('prod1'), random_string(),
                              {:multiplier => 2})
    @products[:product2] = create_product(random_string('prod2'), random_string())
    @products[:virt_product] = create_product(random_string('virt_product'),
                                  random_string('virt_product'),
                                  {:attributes => {:virt_only => true}})

    @products[:product3] = create_product(random_string('sub-prod'), random_string(), {
        :attributes => { :arch => "x86_64", :virt_limit => "unlimited"}
    })

    @products[:derived_product] = create_product(random_string('sub-prov-prod'), random_string(),
        {"sockets" => "2"})
    @products[:derived_provided_prod] = create_product(random_string(nil, true), random_string());

    #this is for the update process
    @products[:product_up] = create_product(random_string('product_up'), random_string('product_up'))

    # Create an engineering product:
    @products[:eng_product] = create_product(rand(10000000).to_s, random_string('engproduct'))

    content = create_content({:metadata_expire => 6000,
                              :required_tags => "TAG1,TAG2"})
    arch_content = create_content({:metadata_expire => 6000,
                                   :content_url => "/path/to/arch/specific/content",
                                   :required_tags => "TAG1,TAG2",
                                   :arches => "i386,x86_64"})

    @cp.add_content_to_product(@products[:product1].id, content.id)
    @cp.add_content_to_product(@products[:product2].id, content.id)
    @cp.add_content_to_product(@products[:product2].id, arch_content.id)
    @cp.add_content_to_product(@products[:derived_product].id, content.id)

    end_date = Date.new(2025, 5, 29)

    brandings = [
      {
        :productId => @products[:eng_product]['id'],
        :type => "OS",
        :name => "Branded Eng Product"
      }
    ]
    @cp.create_subscription(@owner['key'], @products[:product1].id, 2,
      [@products[:eng_product]['id']], '', '12345', '6789', nil, end_date,
      {:branding => brandings})
    @cp.create_subscription(@owner['key'], @products[:product2].id, 4, [], '', '12345', '6789', nil, end_date)
    @cp.create_subscription(@owner['key'], @products[:virt_product].id, 10, [], '', '12345', '6789', nil, end_date)
    @cp.create_subscription(@owner['key'], @products[:product3].id, 5, [], '', '12345', '6789', nil, end_date,
      {'derived_product_id' => @products[:derived_product]['id'],  'derived_provided_products' => [@products[:derived_provided_prod]['id']]})
    @cp.create_subscription(@owner['key'], @products[:product_up].id, 10, [], '', '12345', '6789', nil, end_date)

    @cp.refresh_pools(@owner['key'])

    # Pool names is a list of names of instance variables that will be created
    pool_names = ["pool1", "pool2", "pool3", "pool4", "pool_up"]
    pool_products = [:product1, :product2, :product3, :virt_product, :product_up]

    # Take the names and couple them together with keys in the @products hash.
    # Then for each pair, set an instance variable with the value of the list_pools
    # for that product.
    pool_names.zip(pool_products).each do |name, product|
      instance_variable_set("@#{name}", @cp.list_pools(:owner => @owner.id, :product => @products[product].id)[0] )
    end

    @candlepin_client.update_consumer({:facts => {"distributor_version" => "sam-1.3"}})
    @candlepin_consumer = @candlepin_client.get_consumer()

    ent_names = ["entitlement1", "entitlement2", "entitlement3", "entitlement_up"]
    ent_names.zip([@pool1, @pool2, @pool4, @pool_up]).each do |ent_name, pool|
      instance_variable_set("@#{ent_name}", @candlepin_client.consume_pool(pool.id, {:quantity => 1})[0])
    end

    # pool3 is special
    @candlepin_client.consume_pool(@pool3.id, {:quantity => 1})

    @cdn = create_cdn(@cdn_label,
	              "Test CDN",
	              "https://cdn.test.com")
  end

  def create_candlepin_export_update
    ## to determine if the process of updating the entitlement import is successful
    ## use the process for creating the import above to make one that is an update
    ## You must execute the create_candlepin_export method in the same test before
    ## this one.
    product1 = create_product(random_string(nil, true), random_string())
    product2 = create_product(random_string(nil, true), random_string())
    content = create_content({:metadata_expire => 6000,
                              :required_tags => "TAG1,TAG2"})
    arch_content = create_content({:metadata_expire => 6000,
                                   :required_tags => "TAG1,TAG2",
                                   :arches => "i686,x86_64"})
    @cp.add_content_to_product(product1.id, content.id)
    @cp.add_content_to_product(product2.id, content.id)
    @cp.add_content_to_product(product2.id, arch_content.id)

    end_date = Date.new(2025, 5, 29)
    @cp.create_subscription(@owner['key'], product1.id, 12, [], '', '12345', '6789', nil, end_date)
    @cp.create_subscription(@owner['key'], product2.id, 14, [], '', '12345', '6789', nil, end_date)

    @cp.refresh_pools(@owner['key'])

    pool1 = @cp.list_pools(:owner => @owner.id, :product => product1.id)[0]
    pool2 = @cp.list_pools(:owner => @owner.id, :product => product2.id)[0]

    @candlepin_client.consume_pool(pool1.id, {:quantity => 1})
    @candlepin_client.consume_pool(pool2.id, {:quantity => 1})
    @candlepin_client.consume_pool(@pool_up.id, {:quantity => 4})

    @cp.unbind_entitlement(@entitlement2.id, :uuid => @candlepin_client.uuid)
    @cp.unbind_entitlement(@entitlement_up.id, :uuid => @candlepin_client.uuid)
    @candlepin_client.regenerate_entitlement_certificates_for_entitlement(@entitlement1.id)

    create_candlepin_export()
  end

  def create_candlepin_export_update_no_ent
    ## We need to test the behavoir of the manifest update when no entitlements
    ## are included
    ents = @candlepin_client.list_entitlements()
    # remove all entitlements
    ents.each do |ent|
      @cp.unbind_entitlement(ent.id, {:uuid => @candlepin_client.uuid})
    end

    create_candlepin_export()
  end
end
