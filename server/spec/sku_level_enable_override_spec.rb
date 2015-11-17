require 'spec_helper'
require 'candlepin_scenarios'

describe 'SKU Level Enable Override' do
  include CandlepinMethods
  include CertificateMethods

  before(:each) do
    @owner = create_owner random_string('test_owner')
    @org_admin = user_client(@owner, random_string('user'))

    @content1 = create_content
    @content2 = create_content
    @content3 = create_content
    @content_list = @content1.id + "," + @content2.id + "," + @content3.id
  end

  it 'sku override for enable shows on provided product in entitlement' do
    consumer = @org_admin.register(random_string('consumer'), :system, nil,
        {'system.certificate_version' => '3.2'})
    consumer_cp = Candlepin.new(nil, nil, consumer['idCert']['cert'],
      consumer['idCert']['key'])

    product = create_product(nil, nil,  {:attributes => { :content_override_enabled => @content_list }})
    providedProduct = create_product
    @cp.add_content_to_product(@owner['key'], providedProduct['id'], @content1['id'], false)
    pool = create_pool_and_subscription(@owner['key'], product['id'], 10, [providedProduct.id])
    ent = consumer_cp.consume_pool(pool['id'], {:quantity => 1})[0]

    json_body = extract_payload(ent['certificates'][0]['cert'])
    products = json_body['products']
    products.size.should == 2

    products.each do |p|
      if p.id == providedProduct.id
        p['content'].size.should == 1
        p['content'][0]['id'].should == @content1['id']
        p['content'][0]['enabled'].should be_nil # true
      end
    end
  end

  it 'sku override for disable shows on provided product in entitlement' do
    consumer = @org_admin.register(random_string('consumer'), :system, nil,
        {'system.certificate_version' => '3.2'})
    consumer_cp = Candlepin.new(nil, nil, consumer['idCert']['cert'],
      consumer['idCert']['key'])

    product = create_product(nil, nil,  {:attributes => { :content_override_disabled => @content_list }})
    providedProduct = create_product
    @cp.add_content_to_product(@owner['key'], providedProduct['id'], @content2['id'], true)
    pool = create_pool_and_subscription(@owner['key'], product.id, 10, [providedProduct.id])
    ent = consumer_cp.consume_pool(pool['id'], {:quantity => 1})[0]

    json_body = extract_payload(ent['certificates'][0]['cert'])
    products = json_body['products']
    products.size.should == 2

    products.each do |p|
      if p.id == providedProduct.id
        p['content'].size.should == 1
        p['content'][0]['id'].should == @content2['id']
        p['content'][0]['enabled'].should == false
      end
    end
  end

  it 'sku override for enabled superceded by environment promotion' do
    env = @org_admin.create_environment(@owner['key'], 'testenv1',
      "My Test Env 1", "For test systems only.")
    consumer = @org_admin.register(random_string('consumer'), :system, nil,
        {'system.certificate_version' => '3.2'},
        nil, nil, [], [], env['id'])
    consumer['environment'].should_not be_nil
    consumer_cp = Candlepin.new(nil, nil, consumer['idCert']['cert'],
      consumer['idCert']['key'])

    product = create_product(nil, nil,  {:attributes => { :content_override_disabled => @content_list }})
    providedProduct = create_product
    @cp.add_content_to_product(@owner['key'], providedProduct['id'], @content3['id'], false)
    pool = create_pool_and_subscription(@owner['key'], product.id, 10, [providedProduct.id])

    # Override enabled to true:
    job = @org_admin.promote_content(env['id'],
        [{
          :contentId => @content3['id'],
          :enabled => true,
        }])
    wait_for_job(job['id'], 15)

    ent = consumer_cp.consume_pool(pool['id'], {:quantity => 1})[0]

    json_body = extract_payload(ent['certificates'][0]['cert'])
    products = json_body['products']
    products.size.should == 2

    products.each do |p|
      if p.id == providedProduct.id
        p['content'].size.should == 1
        p['content'][0]['id'].should == @content3['id']
        p['content'][0]['enabled'].should be_nil # true
      end
    end
  end

  it 'sku override for enable shows on provided product in V1 entitlement' do
    consumer = @org_admin.register(random_string('consumer'), :system, nil,
        {'system.certificate_version' => '1.0'})
    consumer_cp = Candlepin.new(nil, nil, consumer['idCert']['cert'],
      consumer['idCert']['key'])

    product = create_product(nil, nil,  {:attributes => { :content_override_enabled => @content_list }})
    providedProduct = create_product
    @cp.add_content_to_product(@owner['key'], providedProduct['id'], @content1['id'], false)
    pool = create_pool_and_subscription(@owner['key'], product.id, 10, [providedProduct.id])
    ent = consumer_cp.consume_pool(pool['id'], {:quantity => 1})[0]

    x509 = OpenSSL::X509::Certificate.new(ent['certificates'][0]['cert'])
    extensions_hash = Hash[x509.extensions.collect { |ext| [ext.oid, ext.value] }]
    extensions_hash["1.3.6.1.4.1.2312.9.2.#{@content1['id']}.1.8"].should == '..1'
  end

  it 'sku override for disable shows on provided product in V1 entitlement' do
    consumer = @org_admin.register(random_string('consumer'), :system, nil,
        {'system.certificate_version' => '1.0'})
    consumer_cp = Candlepin.new(nil, nil, consumer['idCert']['cert'],
      consumer['idCert']['key'])

    product = create_product(nil, nil,  {:attributes => { :content_override_disabled => @content_list }})
    providedProduct = create_product
    @cp.add_content_to_product(@owner['key'], providedProduct['id'], @content2['id'], true)
    pool = create_pool_and_subscription(@owner['key'], product.id, 10, [providedProduct.id])
    ent = consumer_cp.consume_pool(pool['id'], {:quantity => 1})[0]

    x509 = OpenSSL::X509::Certificate.new(ent['certificates'][0]['cert'])
    extensions_hash = Hash[x509.extensions.collect { |ext| [ext.oid, ext.value] }]
    extensions_hash["1.3.6.1.4.1.2312.9.2.#{@content2['id']}.1.8"].should == '..0'
  end

  it 'sku override for enabled superceded by environment promotion V1' do
    env = @org_admin.create_environment(@owner['key'], 'testenv1',
      "My Test Env 1", "For test systems only.")
    consumer = @org_admin.register(random_string('consumer'), :system, nil,
        {'system.certificate_version' => '1.0'},
        nil, nil, [], [], env['id'])
    consumer['environment'].should_not be_nil
    consumer_cp = Candlepin.new(nil, nil, consumer['idCert']['cert'],
      consumer['idCert']['key'])

    product = create_product(nil, nil,  {:attributes => { :content_override_disabled => @content_list }})
    providedProduct = create_product
    @cp.add_content_to_product(@owner['key'], providedProduct['id'], @content3['id'], false)
    pool = create_pool_and_subscription(@owner['key'], product.id, 10, [providedProduct.id])
    # Override enabled to true:
    job = @org_admin.promote_content(env['id'],
        [{
          :contentId => @content3['id'],
          :enabled => true,
        }])
    wait_for_job(job['id'], 15)

    ent = consumer_cp.consume_pool(pool['id'], {:quantity => 1})[0]

    x509 = OpenSSL::X509::Certificate.new(ent['certificates'][0]['cert'])
    extensions_hash = Hash[x509.extensions.collect { |ext| [ext.oid, ext.value] }]
    extensions_hash["1.3.6.1.4.1.2312.9.2.#{@content3['id']}.1.8"].should == '..1'
  end
end
