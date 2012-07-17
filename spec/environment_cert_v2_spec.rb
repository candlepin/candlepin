require 'candlepin_scenarios'

describe 'Environments Certificate V2' do
  include CandlepinMethods
  include CandlepinScenarios

  before(:each) do
    @owner = create_owner random_string('test_owner')
    @org_admin = user_client(@owner, 'guy')
    @env = @org_admin.create_environment(@owner['key'], 'testenv1',
      "My Test Env 1", "For test systems only.")
  end

  it 'filters content not promoted to environment' do
    consumer = @org_admin.register(random_string('testsystem'), :system, nil, 
        {'system.certificate_version' => '2.1'},
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

    pools = @cp.list_pools(:owner => @owner['id'], :product => product['id'])
    ent = consumer_cp.consume_pool(pools[0]['id'])[0]

    value = retrieve_from_cert(ent['certificates'][0]['cert'], "1.3.6.1.4.1.2312.9.6")
    value.should == "2.0"

    coded_value = retrieve_from_cert(ent['certificates'][0]['cert'], "1.3.6.1.4.1.2312.9.7")
    compressed_body = Base64.decode64(coded_value)
    body = Zlib::Inflate.inflate(compressed_body)
    json_body = JSON.parse(body)
    json_body['products'][0]['content'].size.should == 1
    json_body['products'][0]['content'][0]['id'].should == content['id']
    json_body['products'][0]['content'][0]['enabled'].should == false
  end

  it 'regenerates certificates after promoting/demoting content' do
    consumer = @org_admin.register(random_string('testsystem'), :system, nil, 
        {'system.certificate_version' => '2.1'},
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

    pools = @cp.list_pools(:owner => @owner['id'], :product => product['id'])
    ent = consumer_cp.consume_pool(pools[0]['id'])[0]

    value = retrieve_from_cert(ent['certificates'][0]['cert'], "1.3.6.1.4.1.2312.9.6")
    value.should == "2.0"

    coded_value = retrieve_from_cert(ent['certificates'][0]['cert'], "1.3.6.1.4.1.2312.9.7")
    compressed_body = Base64.decode64(coded_value)
    body = Zlib::Inflate.inflate(compressed_body)
    json_body = JSON.parse(body)
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
    coded_value = retrieve_from_cert(ent['certificates'][0]['cert'], "1.3.6.1.4.1.2312.9.7")
    compressed_body = Base64.decode64(coded_value)
    body = Zlib::Inflate.inflate(compressed_body)
    json_body = JSON.parse(body)
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
    coded_value = retrieve_from_cert(ent['certificates'][0]['cert'], "1.3.6.1.4.1.2312.9.7")
    compressed_body = Base64.decode64(coded_value)
    body = Zlib::Inflate.inflate(compressed_body)
    json_body = JSON.parse(body)
    json_body['products'][0]['content'].size.should == 1
    json_body['products'][0]['content'][0]['id'].should == content['id']
    another_serial = ent['certificates'][0]['serial']['serial']
    another_serial.should_not == new_serial
  end

  # extension_id is a FULL extension id
  def retrieve_from_cert(cert, extension_id)
    cert_text = ''
    result = '' 

    IO.popen('openssl x509 -text -certopt ext_parse', "w+") do |pipe|
      pipe.puts(cert)
      pipe.close_write
      cert_text = pipe.read
    end

    itsnext = false
    cert_text.each do |line|
      if itsnext
        result = line
        return result.split(":").last.strip
      end
      if line.include?extension_id+':'
        itsnext = true
      end
    end      
    result
  end
end
