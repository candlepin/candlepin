require 'base64'
require 'zip'


module CleanupHooks
  def cleanup_before
    @cp = Candlepin.new('admin', 'admin')
    @owners = []
    @created_products = []
    @dist_versions = []
    @users = []
    @roles = []
    @rules = nil
    @cdns = []
  end

  def cleanup_after
    @roles.reverse_each { |r| @cp.delete_role r['name'] }
    @owners.reverse_each { |owner| @cp.delete_owner(owner['key'], true, true) }
    @users.reverse_each { |user| @cp.delete_user user['username'] }
    @dist_versions.reverse_each { |dist_version| @cp.delete_distributor_version dist_version['id'] }
    @cdns.reverse_each { |cdn| @cp.delete_cdn cdn['label'] }

    # restore the original rules
    if (@rules)
      @cp.delete_rules
    end

    if !@cp.get_status()['standalone']
      begin
        @cp.delete('/hostedtest/', {}, nil, true)
      rescue RestClient::ResourceNotFound
        puts "skipping hostedtest cleanup"
      end
    end
  end
end

RSpec.configure do |config|
  # TODO: The "should" method has been deprecated in RSpec 2.11 and replaced with "expect".
  # See also http://myronmars.to/n/dev-blog/2012/06/rspecs-new-expectation-syntax
  config.expect_with :rspec do |c|
    c.syntax = [:expect, :should]
  end

  # Sometimes when diagnosing a test failure, you might not want to
  # run the :after hook so you can do a post-mortem.  If that's the case
  # set this value to false using an environment variable.
  config.add_setting(:run_after_hook, :default => true)
  RSpec.configuration.run_after_hook = false if ENV['run_after_hook'] == 'false'

  include CleanupHooks

  config.before(:each) do
    cleanup_before()
  end

  config.before(:each, :type => :virt) do
    skip("candlepin running in standalone mode") if is_hosted?
    @owner = create_owner random_string('virt_owner')
    @user = user_client(@owner, random_string('virt_user'))

    # Create a sub for a virt limited product:
    @virt_limit_product = create_product(nil, nil, {
      :attributes => {
        :virt_limit => 3
      }
    })

    #create two subs, to do migration testing
    create_pool_and_subscription(@owner['key'], @virt_limit_product.id, 10, [], '', '', '', nil, nil, true)
    create_pool_and_subscription(@owner['key'], @virt_limit_product.id, 10)

    @pools = @user.list_pools :owner => @owner.id, :product => @virt_limit_product.id

    # includes an extra unmapped guest pool for each
    @pools.size.should == 4
    @virt_limit_pool = filter_unmapped_guest_pools(@pools)[0]

    # Setup two virt guest consumers:
    @uuid1 = random_string('system.uuid')
    @uuid2 = random_string('system.uuid')
    @guest1 = @user.register(random_string('guest'), :system, nil,
      {'virt.uuid' => @uuid1, 'virt.is_guest' => 'true', 'uname.machine' => 'x86_64'}, nil, nil, [], [])
    @guest1_client = Candlepin.new(nil, nil, @guest1['idCert']['cert'], @guest1['idCert']['key'])

    @guest2 = @user.register(random_string('guest'), :system, nil,
      {'virt.uuid' => @uuid2, 'virt.is_guest' => 'true'}, nil, nil, [], [])
    @guest2_client = Candlepin.new(nil, nil, @guest2['idCert']['cert'], @guest2['idCert']['key'])
  end

  config.after(:each) do
    if RSpec.configuration.run_after_hook?
      cleanup_after()
    end
  end
end

module VirtHelper
  def find_guest_virt_pool(guest_client, guest_uuid)
    pools = guest_client.list_pools :consumer => guest_uuid
    return pools.find_all { |i| !i['sourceEntitlement'].nil? }[0]
  end

  def filter_unmapped_guest_pools(pools)
    # need to ignore the unmapped guest pools
    pools.select! do |pool|
      unmapped = nil
      pool["attributes"].each do |attribute|
        if (attribute["name"] == "unmapped_guests_only")
          unmapped = attribute["value"]
          break
        end
      end

      # unmapped = get_attribute_value(pool["attributes"], "unmapped_guests_only")
      unmapped.nil? || unmapped == 'false'
    end
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

module SpecUtils
  def parse_file(filename)
    JSON.parse File.read(filename)
  end

  def files_in_dir(dir_name)
    Dir.entries(dir_name).select {|e| e != '.' and e != '..' }
  end
end

module AttributeHelper
  def normalize_attributes(attribute_list)
    attributes = {}

    if attribute_list.is_a?(Array)
      attribute_list.each do |attribute_obj|
        if (attribute_obj.is_a?(Hash) && attribute_obj.include?("name"))
          attributes[attribute_obj["name"]] = attribute_obj.include?("value") ? attribute_obj["value"] : nil
        end
      end
    end

    return attributes
  end

  def get_attribute(attribute_list, attribute)
    if attribute_list.is_a?(Array)
      attribute_list.each do |attribute_obj|
        if (attribute_obj.is_a?(Hash) && attribute_obj.include?("name") && attribute_obj["name"] == attribute)
          return attribute_obj
        end
      end
    end

    return nil
  end

  def has_attribute(attribute_list, attribute)
    return !get_attribute(attribute_list, attribute).nil?
  end

  def get_attribute_value(attribute_list, attribute)
    attribute_obj = get_attribute(attribute_list, attribute)
    return !attribute_obj.nil? && attribute_obj.include?("value") ? attribute_obj["value"] : nil
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
