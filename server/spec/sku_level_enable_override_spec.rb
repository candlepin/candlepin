require 'spec_helper'
require 'candlepin_scenarios'

describe 'SKU Level Enable Override' do
  include CandlepinMethods
  include CertificateMethods

  before(:each) do
    @owner = create_owner random_string('test_owner')
    @org_admin = user_client(@owner, random_string('user'))
    @env = @org_admin.create_environment(@owner['key'], 'testenv1',
      "My Test Env 1", "For test systems only.")

    @content1 = create_content
    @content2 = create_content
    content_list = @content1.id + "," + @content2.id
    @product1 = create_product(nil, nil,  {:attributes => { :content_override_active => content_list }})
    @product2 = create_product(nil, nil,  {:attributes => { :content_override_disabled => content_list }})
    @providedProduct = create_product
    @cp.add_content_to_product(@providedProduct['id'], @content1['id'], false)

    @cp.create_subscription(@owner['key'], @product1['id'], 10, [@providedProduct.id])
    @cp.create_subscription(@owner['key'], @product2['id'], 10, [@providedProduct.id])
    @cp.refresh_pools(@owner['key'])
  end

  it 'sku override for enable shows on provided product in entitlement' do
    consumer = @org_admin.register(random_string('consumer'), :system, nil,
        {'system.certificate_version' => '3.2'})
    consumer_cp = Candlepin.new(nil, nil, consumer['idCert']['cert'],
      consumer['idCert']['key'])

    pools = consumer_cp.list_pools(:owner => @owner['id'], :product => @product1['id'])
    ent = consumer_cp.consume_pool(pools[0]['id'], {:quantity => 1})[0]

    json_body = extract_payload(ent['certificates'][0]['cert'])
    products = json_body['products']
    products.size.should == 2

    products.each do |p|
      if p.id == @providedProduct.id
        p['content'].size.should == 1
        p['content'][0]['id'].should == @content1['id']
        p['content'][0]['enabled'].should be_nil # true
      end
    end
  end

  it 'sku override for enabled superceded by environment promotion' do
    consumer = @org_admin.register(random_string('consumer'), :system, nil,
        {'system.certificate_version' => '3.2'},
        nil, nil, [], [], @env['id'])
    consumer['environment'].should_not be_nil
    consumer_cp = Candlepin.new(nil, nil, consumer['idCert']['cert'],
      consumer['idCert']['key'])

    # Override enabled to true:
    job = @org_admin.promote_content(@env['id'],
        [{
          :contentId => @content1['id'],
          :enabled => true,
        }])
    wait_for_job(job['id'], 15)

    pools = consumer_cp.list_pools(:owner => @owner['id'], :product => @product2['id'])
    ent = consumer_cp.consume_pool(pools[0]['id'], {:quantity => 1})[0]

    json_body = extract_payload(ent['certificates'][0]['cert'])
    products = json_body['products']
    products.size.should == 2

    products.each do |p|
      if p.id == @providedProduct.id
        p['content'].size.should == 1
        p['content'][0]['id'].should == @content1['id']
        p['content'][0]['enabled'].should be_nil # true
      end
    end
  end
end
