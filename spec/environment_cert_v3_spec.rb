require 'spec_helper'
require 'candlepin_scenarios'

describe 'Environments Certificate V3' do
  include CandlepinMethods
  include CertificateMethods

  before(:each) do
    @owner = create_owner random_string('test_owner')
    @org_admin = user_client(@owner, random_string('guy'))
    @env = @org_admin.create_environment(@owner['key'], 'testenv1',
      "My Test Env 1", "For test systems only.")
  end

  it 'filters content not promoted to environment' do
    consumer = @org_admin.register(random_string('testsystem'), :system, nil, 
        {'system.certificate_version' => '3.1'},
        nil, nil, [], [], @env['id'])
    consumer['environment'].should_not be_nil
    consumer_cp = Candlepin.new(nil, nil, consumer['idCert']['cert'],
      consumer['idCert']['key'])

    product = create_product
    content = create_content # promoted
    content2 = create_content # not promoted
    @cp.add_content_to_product(product['id'], content['id'])
    @cp.add_content_to_product(product['id'], content2['id'])

    # Override enabled to false:
    @org_admin.promote_content(@env['id'],
        [{
          :contentId => content['id'],
          :enabled => false,
        }])

    @cp.create_subscription(@owner['key'], product['id'], 10)
    @cp.refresh_pools(@owner['key'])

    pools = consumer_cp.list_pools(:owner => @owner['id'], :product => product['id'])
    ent = consumer_cp.consume_pool(pools[0]['id'], {:quantity => 1})[0]

    value = extension_from_cert(ent['certificates'][0]['cert'], "1.3.6.1.4.1.2312.9.6")
    value.should == "3.2"

    json_body = extract_payload(ent['certificates'][0]['cert'])

    json_body['products'][0]['content'].size.should == 1
    json_body['products'][0]['content'][0]['id'].should == content['id']
    json_body['products'][0]['content'][0]['enabled'].should == false
  end

  it 'regenerates certificates after promoting/demoting content' do
    consumer = @org_admin.register(random_string('testsystem'), :system, nil, 
        {'system.certificate_version' => '3.1'},
        nil, nil, [], [], @env['id'])
    consumer['environment'].should_not be_nil
    consumer_cp = Candlepin.new(nil, nil, consumer['idCert']['cert'],
      consumer['idCert']['key'])
 
    product = create_product
    content = create_content # promoted
    content2 = create_content # not promoted
    @cp.add_content_to_product(product['id'], content['id'])
    @cp.add_content_to_product(product['id'], content2['id'])

    # Override enabled to false:
    @org_admin.promote_content(@env['id'],
        [{
          :contentId => content['id'],
        }])

    @cp.create_subscription(@owner['key'], product['id'], 10)
    @cp.refresh_pools(@owner['key'])

    pools = consumer_cp.list_pools(:owner => @owner['id'], :product => product['id'])
    ent = consumer_cp.consume_pool(pools[0]['id'], {:quantity => 1})[0]

    value = extension_from_cert(ent['certificates'][0]['cert'], "1.3.6.1.4.1.2312.9.6")
    value.should == "3.2"

    json_body = extract_payload(ent['certificates'][0]['cert'])

    json_body['products'][0]['content'].size.should == 1
    json_body['products'][0]['content'][0]['id'].should == content['id']
    serial = ent['certificates'][0]['serial']['serial']

    # Promote the other content set and make sure certs were regenerated:
    @org_admin.promote_content(@env['id'],
        [{
          :contentId => content2['id'],
        }])
    sleep 1
    ent = consumer_cp.list_entitlements()[0]

    json_body = extract_payload(ent['certificates'][0]['cert'])

    json_body['products'][0]['content'].size.should == 2
    ids = [json_body['products'][0]['content'][0]['id'],json_body['products'][0]['content'][1]['id']]
    ids.should include(content['id'])
    ids.should include(content2['id'])
    new_serial = ent['certificates'][0]['serial']['serial']
    new_serial.should_not == serial

    # Demote it and check again:
    @org_admin.demote_content(@env['id'], [content2['id']])
    sleep 1
    ent = consumer_cp.list_entitlements()[0]

    json_body = extract_payload(ent['certificates'][0]['cert'])

    json_body['products'][0]['content'].size.should == 1
    json_body['products'][0]['content'][0]['id'].should == content['id']
    another_serial = ent['certificates'][0]['serial']['serial']
    another_serial.should_not == new_serial
  end
end
