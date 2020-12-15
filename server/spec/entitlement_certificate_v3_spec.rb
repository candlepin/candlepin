require 'spec_helper'
require 'candlepin_scenarios'
require 'unpack'

describe 'Entitlement Certificate V3' do
  include CandlepinMethods
  include CertificateMethods
  include Unpack

  class String
      def to_date
          return Date.strptime(self, "%Y-%m-%d")
      end
  end

  def change_dt_and_qty
      sub = @cp.list_subscriptions(@owner['key'])[0]
      sub.endDate = sub.endDate.to_date + 10
      sub.startDate = sub.startDate.to_date - 10
      sub.quantity = sub.quantity + 10

      @cp.update_subscription(sub)
      @cp.refresh_pools(@owner['key'])
      return sub
  end

  # TODO: FIXME: Rewrite this test suite. It's currently creating both upstream and downstream objects and
  # expecting them to be reconciled through the hosted test API stuff, which doesn't properly test the
  # refresh operations.

  def create_content_ex(params = {})
    if is_hosted?
      return create_upstream_content(nil, params)
    else
      return create_content(params)
    end
  end

  def map_content_to_product(owner_key, product_id, content_id, enabled)
    if is_hosted?
      return add_content_to_product_upstream(product_id, content_id, enabled)
    else
      return @cp.add_content_to_product(owner_key, product_id, content_id, enabled)
    end
  end

  before(:each) do
    @owner = create_owner random_string('test_owner')

    @product = create_product(nil, nil, :attributes =>
                {:version => '6.4',
                 :arch => 'i386, x86_64',
                 :sockets => 4,
                 :cores => 8,
                 :ram => 16,
                 :usage => 'Disaster Recovery',
                 :roles => 'Red Hat Enterprise Linux Server, Red Hat Enterprise Linux Workstation',
                 :addons => 'my_server_addon, my_workstation_addon',
                 :warning_period => 15,
                 :management_enabled => true,
                 :stacking_id => '8888',
                 :virt_only => 'false',
                 :support_level => 'standard',
                 :support_type => 'excellent',})

    @product_30 = create_product(nil, nil, :attributes =>
                {:version => '6.4',
                 :arch => 'i386, x86_64',
                 :sockets => 4,
                 :warning_period => 15,
                 :management_enabled => true,
                 :virt_only => 'false',
                 :support_level => 'standard',
                 :support_type => 'excellent',})

    @content = create_content({:gpg_url => 'gpg_url',
                               :content_url => '/content/dist/rhel/$releasever/$basearch/os',
                               :metadata_expire => 6400,
                               :required_tags => 'TAG1,TAG2',})

    @arch_content = create_content({:gpg_url => 'gpg_url',
                                    :content_url => '/content/dist/rhel/arch/specific/$releasever/$basearch/os',
                                    :metadata_expire => 6400,
                                    :arches => "i386,x86_64",
                                    :required_tags => 'TAG1,TAG2',})


    @cp.add_content_to_product(@owner['key'], @product.id, @content.id, false)
    @cp.add_content_to_product(@owner['key'], @product.id, @arch_content.id, false)


    # Create these products and content upstream
    if is_hosted?
      create_upstream_product(@product.id, @product)
      create_upstream_product(@product_30.id, @product_30)

      create_upstream_content(@content.id, @content)
      create_upstream_content(@arch_content.id, @arch_content)

      add_content_to_product_upstream(@product.id, @content.id, false)
      add_content_to_product_upstream(@product.id, @arch_content.id, false)
    end

    @pool = create_pool_and_subscription(@owner['key'], @product.id, 10, [], '12345', '6789', 'order1')
    @pool_30 = create_pool_and_subscription(@owner['key'], @product_30.id, 10, [], '123456', '67890', 'order2')

    @user = user_client(@owner, random_string('billy'))

    @system = consumer_client(@user, random_string('system1'), :system, nil,
                {'system.certificate_version' => '3.4',
                 'uname.machine' => 'i386'})
  end

  it 'generated a version 3.4 certificate when requesting a 3.0 certificate' do
    # NOTE: This test covers the case where the system supports 3.0 certs, but
    # the server is creating 3.4 certs, and the product contains attributes
    # supported by 3.0.
    v3_system = consumer_client(@user, random_string('v3system'), :system, nil,
                                  {'system.certificate_version' => '3.0',
                                   'uname.machine' => 'i386'})
    v3_system.consume_product(@product_30.id)
    value = extension_from_cert(v3_system.list_certificates[0]['cert'], "1.3.6.1.4.1.2312.9.6")
    value.should == "3.4"
  end

  it 'generated a version 3.4 certificate' do
    entitlement = @system.consume_product(@product.id)[0]
    value = extension_from_cert(@system.list_certificates[0]['cert'], "1.3.6.1.4.1.2312.9.6")
    value.should == "3.4"
    @system.unbind_entitlement entitlement.id
  end

  it 'generated the correct body in the blob' do
    entitlement = @system.consume_product(@product.id)[0]
    json_body = extract_payload(@system.list_certificates[0]['cert'])

    json_body['consumer'].should == @system.get_consumer()['uuid']
    json_body['quantity'].should == 1
    json_body['pool']['id'].should_not be_nil
    json_body['subscription']['sku'].should == @product.id
    json_body['subscription']['name'].should == @product.name
    json_body['subscription']['warning'].should == 15
    json_body['subscription']['sockets'].should == 4
    json_body['subscription']['cores'].should == 8
    json_body['subscription']['ram'].should == 16
    json_body['subscription']['usage'].should == 'Disaster Recovery'
    json_body['subscription']['roles'].should include('Red Hat Enterprise Linux Server')
    json_body['subscription']['roles'].should include('Red Hat Enterprise Linux Workstation')
    json_body['subscription']['addons'].should include('my_server_addon')
    json_body['subscription']['addons'].should include('my_workstation_addon')
    json_body['subscription']['management'].should == true
    json_body['subscription']['stacking_id'].should == '8888'
    json_body['subscription']['virt_only'].should be_nil
    json_body['subscription']['service']['level'].should == 'standard'
    json_body['subscription']['service']['type'].should == 'excellent'
    json_body['order']['number'].should == 'order1'
    json_body['order']['quantity'].should == 10
    json_body['order']['start'].should_not be_nil
    json_body['order']['end'].should_not be_nil
    json_body['order']['contract'].should == '12345'
    json_body['order']['account'].should == '6789'
    json_body['products'][0]['id'].should == @product.id
    json_body['products'][0]['name'].should == @product.name
    json_body['products'][0]['version'].should == '6.4'
    json_body['products'][0]['architectures'].size.should == 2
    json_body['products'][0]['brand_name'].should == nil
    json_body['products'][0]['brand_type'].should == nil
    contents = json_body['products'][0]['content']
    reg_ret_content = nil
    arch_ret_content = nil
    contents.each do |content_set|
        if content_set['id'] == @content.id
            reg_ret_content = content_set
        end
        if content_set['id'] == @arch_content.id
            arch_ret_content = content_set
        end
    end

    reg_ret_content['type'].should == 'yum'
    reg_ret_content['name'].should == @content.name
    reg_ret_content['label'].should == @content.label
    reg_ret_content['vendor'].should == @content.vendor
    reg_ret_content['gpg_url'].should == 'gpg_url'
    reg_ret_content['path'].should == '/content/dist/rhel/$releasever/$basearch/os'
    reg_ret_content['enabled'].should == false
    reg_ret_content['metadata_expire'].should == 6400
    reg_ret_content['required_tags'].size.should == 2

    arch_ret_content['type'].should == 'yum'
    arch_ret_content['name'].should == @arch_content.name
    arch_ret_content['label'].should == @arch_content.label
    arch_ret_content['vendor'].should == @arch_content.vendor
    arch_ret_content['gpg_url'].should == 'gpg_url'
    arch_ret_content['path'].should == '/content/dist/rhel/arch/specific/$releasever/$basearch/os'
    arch_ret_content['enabled'].should == false
    arch_ret_content['metadata_expire'].should == 6400
    arch_ret_content['required_tags'].size.should == 2
    arch_ret_content['arches'].size.should == 2
    arch_ret_content['arches'].include?('i386').should be true
    arch_ret_content['arches'].include?('x86_64').should be true

    @system.unbind_entitlement entitlement.id
  end

  it 'verify branding info is correct in json blob' do

    branding = [{:productId => "111", :type => 'Some Type', :name => 'Super Branded Name'}]

    if is_hosted?
      eng_product = create_upstream_product("111", { :name => 'engineering_product_name' })
      mkt_product = create_upstream_product('555', { :branding => branding, :providedProducts => [eng_product] })
    else
      eng_product = create_product("111", "engineering_product_name")
      mkt_product = create_product("555", "marketing_product_name", :branding => branding, :providedProducts => [eng_product.id])
    end
    expect(mkt_product.branding.size).to eq(1)

    now = DateTime.now
    pool = create_pool_and_subscription(@owner['key'], mkt_product.id, 10, [eng_product.id],
                                        '12345', '6789', 'order1', now - 10, now + 365, false)
    expect(pool.branding.size).to eq(1)

    entitlement = @system.consume_product(eng_product.id)[0]
    json_body = extract_payload(@system.list_certificates[0]['cert'])

    json_body['subscription']['name'].should == mkt_product.name
    json_body['consumer'].should == @system.get_consumer()['uuid']

    # Verify branding info
    json_body['products'][0]['brand_name'].should == 'Super Branded Name'
    json_body['products'][0]['brand_type'].should == 'Some Type'
end

  it 'encoded the content urls' do
    @content_1 = create_content_ex({:content_url => '/content/dist/rhel/$releasever/$basearch/debug',})
    map_content_to_product(@owner['key'], @product.id, @content_1.id, true)
    @content_2 = create_content_ex({:content_url => '/content/beta/rhel/$releasever/$basearch/source/SRPMS',})
    map_content_to_product(@owner['key'], @product.id, @content_2.id, true)
    #refresh the subscription - product resolution will take care of adding the content automatically
    refresh_upstream_subscription(@pool)
    entitlement = @system.consume_product(@product.id)[0]

    json_body = extract_payload(entitlement.certificates[0]['cert'])

    json_body['products'][0]['content'].size.should == 4

    value = extension_from_cert(entitlement.certificates[0]['cert'], "1.3.6.1.4.1.2312.9.7")

    urls = []
    urls[0] = '/content/dist/rhel/$releasever/$basearch/os'
    urls[1] = '/content/dist/rhel/$releasever/$basearch/debug'
    urls[2] = '/content/beta/rhel/$releasever/$basearch/source/SRPMS'
    are_content_urls_present(value, urls).should == true
    @system.unbind_entitlement entitlement.id
  end

  it 'encoded many content urls' do
    number = 100
    number.times do |i|
      content = create_content_ex({:content_url => "/content/dist/rhel/$releasever#{i}/$basearch#{i}/debug#{i}",})
      map_content_to_product(@owner['key'], @product.id, content.id, true)
    end

    #refresh the subscription - product resolution will take care of adding the content automatically
    refresh_upstream_subscription(@pool)
    entitlement = @system.consume_product(@product.id)[0]

    json_body = extract_payload(entitlement.certificates[0]['cert'])

    json_body['products'][0]['content'].size.should == 102

    value = extension_from_cert(entitlement.certificates[0]['cert'], "1.3.6.1.4.1.2312.9.7")

    # Can dump binary to file
    #File.open('tmp.bin', 'w') do |f1|
    #  f1.puts value
    #end

    urls = []
    urls[0] = '/content/dist/rhel/$releasever0/$basearch0/debug0'
    urls[1] = '/content/dist/rhel/$releasever29/$basearch29/debug29'
    urls[2] = '/content/dist/rhel/$releasever41/$basearch41/debug41'
    urls[3] = '/content/dist/rhel/$releasever75/$basearch75/debug75'
    urls[4] = '/content/dist/rhel/$releasever99/$basearch99/debug99'
    are_content_urls_present(value, urls).should == true
    @system.unbind_entitlement entitlement.id
  end

  it 'generates a version 3.4 certificate on distributors with a cert_v3 capability' do
    dist_name = random_string("SAMvBillion")
    dist_version = create_distributor_version(dist_name,
      "Subscription Asset Manager Billion",
      ["cert_v3"])
    facts = { 'distributor_version' => dist_name}

    consumer = @user.register(random_string('v3_system'), :candlepin, nil, facts, nil, nil)
    v3_system = Candlepin.new(nil, nil, consumer.idCert.cert, consumer.idCert['key'])
    v3_system.consume_product(@product_30.id)

    value = extension_from_cert(v3_system.list_certificates[0]['cert'], "1.3.6.1.4.1.2312.9.6")
    value.should == "3.4"
   end
end
