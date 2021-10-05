require 'spec_helper'
require 'candlepin_scenarios'

describe 'Environments Certificate V3' do
  include CandlepinMethods
  include CertificateMethods

  before(:each) do
    @owner = create_owner random_string('test_owner')
    @org_admin = user_client(@owner, random_string('guy'))
    @env = @org_admin.create_environment(@owner['key'], random_string('test_env'),
      "My Test Env 1", "For test systems only.")
  end

  it 'filters content not promoted to environment' do
    consumer = @org_admin.register(random_string('testsystem'), :system, nil,
        {'system.certificate_version' => '3.1'},
        nil, nil, [], [], [{'id' => @env['id']}])
    expect(consumer['environments']).not_to be_nil
    consumer_cp = Candlepin.new(nil, nil, consumer['idCert']['cert'],
      consumer['idCert']['key'])

    product = create_product
    content = create_content # promoted
    content2 = create_content # not promoted
    @cp.add_content_to_product(@owner['key'], product['id'], content['id'])
    @cp.add_content_to_product(@owner['key'], product['id'], content2['id'])

    # Override enabled to false:
    job = @org_admin.promote_content(@env['id'],
        [{
          :contentId => content['id'],
          :enabled => false,
        }])
    wait_for_job(job['id'], 15)

    pool = create_pool_and_subscription(@owner['key'], product['id'], 10)
    ent = consumer_cp.consume_pool(pool['id'], {:quantity => 1})[0]

    value = extension_from_cert(ent['certificates'][0]['cert'], "1.3.6.1.4.1.2312.9.6")
    value.should == "3.4"

    json_body = extract_payload(ent['certificates'][0]['cert'])

    json_body['products'][0]['content'].size.should == 1
    json_body['products'][0]['content'][0]['id'].should == content['id']
    json_body['products'][0]['content'][0]['enabled'].should == false
  end

  it 'regenerates certificates after promoting/demoting content' do
    consumer = @org_admin.register(random_string('testsystem'), :system, nil,
        {'system.certificate_version' => '3.1'},
        nil, nil, [], [], [{'id' => @env['id']}])
    expect(consumer['environments']).not_to be_nil
    consumer_cp = Candlepin.new(nil, nil, consumer['idCert']['cert'],
      consumer['idCert']['key'])

    provided_product = create_product
    product = create_product(nil,nil,{:providedProducts => [provided_product.id]})
    content = create_content # promoted
    content2 = create_content # not promoted
    content3 = create_content # not promoted, for provided
    @cp.add_content_to_product(@owner['key'], product['id'], content['id'])
    @cp.add_content_to_product(@owner['key'], product['id'], content2['id'])
    @cp.add_content_to_product(@owner['key'], provided_product['id'], content3['id'])

    # Override enabled to false:
    job = @org_admin.promote_content(@env['id'],
        [{
          :contentId => content['id']
        }])
    wait_for_job(job['id'], 15)

    pool = @cp.create_pool(@owner['key'], product.id)
    ent = consumer_cp.consume_pool(pool['id'], {:quantity => 1})[0]

    value = extension_from_cert(ent['certificates'][0]['cert'], "1.3.6.1.4.1.2312.9.6")
    value.should == "3.4"

    json_body = extract_payload(ent['certificates'][0]['cert'])
    if json_body['products'][0]['id'] == product['id']
      returned_product = json_body['products'][0]
    else
      returned_product = json_body['products'][1]
    end

    returned_product['content'].size.should == 1
    returned_product['content'][0]['id'].should == content['id']
    serial1 = ent['certificates'][0]['serial']['serial']

    # Promote the other content set and make sure certs were regenerated:
    job = @org_admin.promote_content(@env['id'],
        [{
          :contentId => content2['id']
        }])
    wait_for_job(job['id'], 15)
    ent = consumer_cp.list_entitlements()[0]

    json_body = extract_payload(ent['certificates'][0]['cert'])
    if json_body['products'][0]['id'] == product['id']
      returned_product = json_body['products'][0]
    else
      returned_product = json_body['products'][1]
    end

    returned_product['content'].size.should == 2
    ids = [returned_product['content'][0]['id'],returned_product['content'][1]['id']]
    ids.should include(content['id'])
    ids.should include(content2['id'])
    serial2 = ent['certificates'][0]['serial']['serial']
    serial2.should_not == serial1

    # Demote it and check again:
    job = @org_admin.demote_content(@env['id'], [content2['id']])
    wait_for_job(job['id'], 15)
    ent = consumer_cp.list_entitlements()[0]

    json_body = extract_payload(ent['certificates'][0]['cert'])
    if json_body['products'][0]['id'] == product['id']
      returned_product = json_body['products'][0]
    else
      returned_product = json_body['products'][1]
    end

    returned_product['content'].size.should == 1
    returned_product['content'][0]['id'].should == content['id']
    serial3 = ent['certificates'][0]['serial']['serial']
    serial3.should_not == serial2

    # Promote content on the provided product and check
    job = @org_admin.promote_content(@env['id'],
        [{
           :contentId => content3['id']
        }])
    wait_for_job(job['id'], 15)
    ent = consumer_cp.list_entitlements()[0]

    json_body = extract_payload(ent['certificates'][0]['cert'])
    if json_body['products'][0]['id'] == product['id']
      returned_product = json_body['products'][0]
      returned_provided = json_body['products'][1]
    else
      returned_product = json_body['products'][1]
      returned_provided = json_body['products'][0]
    end

    returned_product['content'].size.should == 1
    returned_product['content'][0]['id'].should == content['id']
    returned_provided['content'].size.should == 1
    returned_provided['content'][0]['id'].should == content3['id']
    serial4 = ent['certificates'][0]['serial']['serial']
    serial4.should_not == serial3

    # Demote content on the provided product and check
    job = @org_admin.demote_content(@env['id'], [content3['id']])
    wait_for_job(job['id'], 15)
    ent = consumer_cp.list_entitlements()[0]

    json_body = extract_payload(ent['certificates'][0]['cert'])
    if json_body['products'][0]['id'] == product['id']
      returned_product = json_body['products'][0]
      returned_provided = json_body['products'][1]
    else
      returned_product = json_body['products'][1]
      returned_provided = json_body['products'][0]
    end

    returned_product['content'].size.should == 1
    returned_product['content'][0]['id'].should == content['id']
    returned_provided['content'].size.should == 0
    serial5 = ent['certificates'][0]['serial']['serial']
    serial5.should_not == serial4
  end
end
