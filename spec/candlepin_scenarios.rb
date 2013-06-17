require 'candlepin_api'

require 'pp'
require 'zip/zip'
require 'base64'

# Provides initialization and cleanup of data that was used in the scenario
module CandlepinScenarios

  def self.included(base)
    base.class_eval do
      before(:each) do
        @cp = Candlepin.new('admin', 'admin')
        @owners = []
        @products = []
        @dist_versions = []
        @users = []
        @roles = []
        @rules = Base64.encode64("")
      end

      after do
        @roles.reverse_each { |r| @cp.delete_role r['id'] }
        @owners.reverse_each { |owner| @cp.delete_owner owner['key'] }
        @users.reverse_each { |user| @cp.delete_user user['username'] }
        @products.reverse_each { |product| @cp.delete_product product['id'] }
        @dist_versions.reverse_each { |dist_version| @cp.delete_distributor_version dist_version['id'] }

        # restore the original rules
        if (@rules)
          @cp.delete_rules
        end
        # TODO:  delete products?
      end
    end
  end
end

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
    id ||= rand(100000).to_s #id has to be a number. OID encoding fails otherwise
    name ||= random_string('testproduct')
    product = @cp.create_product(id, name, params)
    @products <<  product
    return product
  end

  def create_content(params={})
    random_str = rand(1000000)
    # Apologies, passing optional params straight through to prevent just pulling
    # each one out and putting it into a new hash.
    @cp.create_content(random_str, random_str, random_str, "yum",
      random_str, params)
  end

  # Wrapper for ruby API so we can track all distributor versions we created and clean them up.
  def create_distributor_version(dist_name, display_name, capabilities=[])
    dist_version = @cp.create_distributor_version(dist_name, display_name, capabilities)
    @dist_versions << dist_version

    return dist_version
  end

  def update_distributor_version(id, dist_name, display_name, capabilities=[])
    dist_version = @cp.update_distributor_version(id, dist_name, display_name, capabilities)
    @dist_versions << dist_version

    return dist_version
  end

  def user_client(owner, user_name, readonly=false)
    create_user(owner, user_name, 'password', readonly)
    Candlepin.new(user_name, 'password')
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

  def random_string(prefix=nil)
    if prefix
      prefix = "#{prefix}-"
    end
    "#{prefix}#{rand(100000)}"
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

module ExportMethods

  def create_candlepin_export
    # WARNING: Creating export is fairly expensive, so using a before all to
    # create one and use it for all tests. Because before(:all) runs before
    # any before(:each), we cannot use the normal helper methods. :( As such,
    # creating one owner, and cleaning it up manually:
    @cp = Candlepin.new('admin', 'admin')
    @owner = @cp.create_owner(random_string('test_owner'))

    @user = @cp.create_user(random_string('test_user'), 'password')
    Candlepin.new(@user['username'], 'password')
    # Create a role for user to administer the given owner:
    role = create_role(nil, @owner['key'], 'ALL')
    @cp.add_role_user(role['id'], @user['username'])
    owner_client = Candlepin.new(@user['username'], 'password')

    # the before(:each) is not initialized yet, call create_product sans wrapper
    @product1 = @cp.create_product(random_string('prod1'), random_string(),
                              {:multiplier => 2})
    @product2 = @cp.create_product(random_string('prod2'), random_string())
    @virt_product = @cp.create_product(random_string('virt_product'),
                                  random_string('virt_product'),
                                  {:attributes => {:virt_only => true}})
    
    @product3 = @cp.create_product(random_string('sub-prod'), random_string(), {
        :attributes => { :arch => "x86_64", :virt_limit => "unlimited"}
    })
    
    @sub_product = @cp.create_product(random_string('sub-prov-prod'), random_string(),
        {"sockets" => "2"})
    @sub_provided_prod = @cp.create_product(random_string(), random_string());
    
    content = create_content({:metadata_expire => 6000,
                              :required_tags => "TAG1,TAG2"})
    arch_content = create_content({:metadata_expire => 6000,
                                   :content_url => "/path/to/arch/specific/content",
                                   :required_tags => "TAG1,TAG2",
                                   :arches => "i386,x86_64"})
    @cp.add_content_to_product(@product1.id, content.id)
    @cp.add_content_to_product(@product2.id, content.id)
    @cp.add_content_to_product(@product2.id, arch_content.id)
    @cp.add_content_to_product(@sub_product.id, content.id)

    @end_date = Date.new(2025, 5, 29)

    sub1 = @cp.create_subscription(@owner['key'], @product1.id, 2, [], '', '12345', '6789', nil, @end_date)
    sub2 = @cp.create_subscription(@owner['key'], @product2.id, 4, [], '', '12345', '6789', nil, @end_date)
    sub3 = @cp.create_subscription(@owner['key'], @virt_product.id, 10, [], '', '12345', '6789', nil, @end_date)
    sub4 = @cp.create_subscription(@owner['key'], @product3.id, 5, [], '', '12345', '6789', nil, @end_date,
      {'sub_product_id' => @sub_product['id'],  'sub_provided_products' => [@sub_provided_prod['id']]})

    @cp.refresh_pools(@owner['key'])

    pool1 = @cp.list_pools(:owner => @owner.id, :product => @product1.id)[0]
    pool2 = @cp.list_pools(:owner => @owner.id, :product => @product2.id)[0]
    pool3 = @cp.list_pools(:owner => @owner.id, :product => @virt_product.id)[0]
    pool4 = @cp.list_pools(:owner => @owner.id, :product => @product3.id)[0]

    @candlepin_client = consumer_client(owner_client, random_string('test_client'),
        "candlepin", @user['username'])
    @candlepin_consumer = @candlepin_client.get_consumer()

    @entitlement1 = @candlepin_client.consume_pool(pool1.id)[0]
    @entitlement2 = @candlepin_client.consume_pool(pool2.id)[0]
    @candlepin_client.consume_pool(pool3.id)
    @entitlement3 = @candlepin_client.consume_pool(pool4.id)[0]

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

  def create_certificate_export
    ## Use the same consumer, and only grab the entitlements

    # Make a temporary directory where we can safely extract our archive:
    @tmp_dir_certs = File.join(Dir.tmpdir, random_string('candlepin-certs-rspec'))
    @export_dir_certs = File.join(@tmp_dir_certs, "export")
    Dir.mkdir(@tmp_dir_certs)

    @certs_export_filename = @candlepin_client.export_certificates(@tmp_dir_certs)
    @orig_working_dir = Dir.pwd()

    File.exist?(@certs_export_filename).should == true
    unzip_export_file(@certs_export_filename, @tmp_dir_certs)
    unzip_export_file(File.join(@tmp_dir_certs, "consumer_export.zip"), @tmp_dir_certs)
  end

  def create_candlepin_export_update
    ## to determine if the process of updating the entitlement import is successful
    ## use the process for creating the import above to make one that is an update
    ## You must execute the create_candlepin_export method in the same test before
    ## this one.
    product1 = @cp.create_product(random_string(), random_string())
    product2 = @cp.create_product(random_string(), random_string())
    content = create_content({:metadata_expire => 6000,
                              :required_tags => "TAG1,TAG2"})
    arch_content = create_content({:metadata_expire => 6000,
                                   :required_tags => "TAG1,TAG2",
                                   :arches => "i686,x86_64"})
    @cp.add_content_to_product(product1.id, content.id)
    @cp.add_content_to_product(product2.id, content.id)
    @cp.add_content_to_product(product2.id, arch_content.id)
    @end_date = Date.new(2025, 5, 29)

    sub1 = @cp.create_subscription(@owner['key'], product1.id, 12, [], '', '12345', '6789', nil, @end_date)
    sub2 = @cp.create_subscription(@owner['key'], product2.id, 14, [], '', '12345', '6789', nil, @end_date)
    @cp.refresh_pools(@owner['key'])

    pool1 = @cp.list_pools(:owner => @owner.id, :product => product1.id)[0]
    pool2 = @cp.list_pools(:owner => @owner.id, :product => product2.id)[0]

    @candlepin_client.consume_pool(pool1.id)
    @candlepin_client.consume_pool(pool2.id)

    @cp.unbind_entitlement(@entitlement2.id, :uuid => @candlepin_client.uuid)
    @candlepin_client.regenerate_entitlement_certificates_for_entitlement(@entitlement1.id)

    # Make a temporary directory where we can safely extract our archive:
    @tmp_dir_update = File.join(Dir.tmpdir, random_string('candlepin-rspec'))
    @export_dir_update = File.join(@tmp_dir_update, "export")
    Dir.mkdir(@tmp_dir_update)

    @export_filename_update = @candlepin_client.export_consumer(@tmp_dir_update)
    File.exist?(@export_filename_update).should == true
    unzip_export_file(@export_filename_update, @tmp_dir_update)
    unzip_export_file(File.join(@tmp_dir_update, "consumer_export.zip"), @tmp_dir_update)
  end

  def cleanup_candlepin_export
    Dir.chdir(@orig_working_dir)
    FileUtils.rm_rf(@tmp_dir)
    #this will also delete the owner's users
    @cp.delete_owner(@owner['key'])
  end

  def cleanup_certificate_export
    Dir.chdir(@orig_working_dir)
    FileUtils.rm_rf(@tmp_dir_certs)
  end

  def cleanup_candlepin_export_update
    Dir.chdir(@orig_working_dir)
    FileUtils.rm_rf(@tmp_dir)
    FileUtils.rm_rf(@tmp_dir_update)
    @cp.delete_owner(@owner['key'])
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
    JSON.parse File.read(filename)
  end

  def files_in_dir(dir_name)
    Dir.entries(dir_name).select {|e| e != '.' and e != '..' }
  end

end

module CertificateMethods

  def extract_payload(certificate)
    payload = certificate.split("-----BEGIN ENTITLEMENT DATA-----\n")[1]
    payload = payload.split("-----END ENTITLEMENT DATA-----")[0]
    asn1_body = Base64.decode64(payload)
    body = Zlib::Inflate.inflate(asn1_body)
    JSON.parse(body)
  end
end

# This allows for dot notation instead of using hashes for everything
class Hash

  # Not sure if this is a great idea
  # Override Ruby's id method to access our id attribute
  def id
    self['id']
  end

  def method_missing(method, *args)
    if ((method.to_s =~ /=$/) != nil)
        self[method.to_s.gsub(/=$/, '')] = args[0]
    else
        self[method.to_s]
    end
  end
end
