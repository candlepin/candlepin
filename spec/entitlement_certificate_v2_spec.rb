require 'candlepin_scenarios'
require 'json'

describe 'Entitlement Certificate V2' do
  include CandlepinMethods
  include CandlepinScenarios

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

  before(:each) do
    @owner = create_owner random_string('test_owner')
    @product = create_product(nil, nil, :attributes => 
				{:version => '6.4',
				 :arch => 'i386, x86_64',
                                 :sockets => 4,
                                 :warning_period => 15,
                                 :management_enabled => true,
                                 :stacking_id => '8888',
                                 :virt_only => 'false',
                                 :support_level => 'standard',
                                 :support_type => 'excellent',})

    @content = create_content({:gpg_url => 'gpg_url',
                               :content_url => 'path_url',
                               :metadata_expire => 6400,
                               :required_tags => 'TAG1,TAG2',})
    @cp.add_content_to_product(@product.id, @content.id, false)
    @subscription = @cp.create_subscription(@owner['key'], @product.id, 10, [], '12345', '6789')
    @cp.refresh_pools(@owner['key'])

    @user = user_client(@owner, random_string('billy'))

    @system = consumer_client(@user, random_string('system1'), :candlepin, nil,
				{'system.certificate_version' => '2.1'})
    @entitlement = @system.consume_product(@product.id)[0]
  end

  it 'generated a version 2.0 certificate' do
    value = retrieve_from_cert(@system.list_certificates[0]['cert'], "1.3.6.1.4.1.2312.9.6")
    value.should == "2.0"
  end

  it 'generated the correct body in the blob' do
    coded_value = retrieve_from_cert(@system.list_certificates[0]['cert'], "1.3.6.1.4.1.2312.9.7")
    compressed_body = Base64.decode64(coded_value)
    body = Zlib::Inflate.inflate(compressed_body)
    json_body = JSON.parse(body)
    json_body['consumer'].should == @system.get_consumer()['uuid']
    json_body['quantity'].should == 1
    json_body['subscription']['sku'].should == @product.id
    json_body['subscription']['name'].should == @product.name
    json_body['subscription']['warning'].should == 15
    json_body['subscription']['sockets'].should == 4
    json_body['subscription']['management'].should == true
    json_body['subscription']['stacking_id'].should == '8888'
    json_body['subscription']['virt_only'].should be_nil
    json_body['subscription']['service']['level'].should == 'standard'
    json_body['subscription']['service']['type'].should == 'excellent'
    json_body['order']['number'].should == @subscription.id
    json_body['order']['quantity'].should == 10
    json_body['order']['start'].should_not be_nil
    json_body['order']['end'].should_not be_nil
    json_body['order']['contract'].should == '12345'
    json_body['order']['account'].should == '6789'
    json_body['products'][0]['id'].should == @product.id
    json_body['products'][0]['name'].should == @product.name
    json_body['products'][0]['version'].should == '6.4'
    json_body['products'][0]['architectures'].size.should == 2
    json_body['products'][0]['content'][0]['id'].should == @content.id
    json_body['products'][0]['content'][0]['type'].should == 'yum'
    json_body['products'][0]['content'][0]['name'].should == @content.name
    json_body['products'][0]['content'][0]['label'].should == @content.label
    json_body['products'][0]['content'][0]['vendor'].should == @content.vendor
    json_body['products'][0]['content'][0]['gpg_url'].should == 'gpg_url'
    json_body['products'][0]['content'][0]['path'].should == 'path_url'
    json_body['products'][0]['content'][0]['enabled'].should == false
    json_body['products'][0]['content'][0]['metadata_expire'].should == 6400
    json_body['products'][0]['content'][0]['required_tags'].size.should == 2
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

  it 'is available after consuming an entitlement' do
    @system.list_certificates.length.should == 1
  end

  it 'can be manually regenerated for a consumer' do
    @system.list_certificates.length.should == 1
    old_certs = @system.list_certificates()
    @system.regenerate_entitlement_certificates()

    new_certs = @system.list_certificates()
    old_certs.size.should == new_certs.size
    old_ids = old_certs.map { |cert| cert['serial']['id']}
    new_ids = new_certs.map { |cert| cert['serial']['id']}
    (old_ids & new_ids).size.should == 0
  end

  it 'can regenerate certificate by entitlement id' do
    @system.list_certificates.length.should == 1
    old_certs = @system.list_certificates()
    ents = @system.list_entitlements()

    @system.regenerate_entitlement_certificates_for_entitlement(ents[0].id)
    new_certs = @system.list_certificates()
    old_ids = old_certs.map { |cert| cert['serial']['id']}
    new_ids = new_certs.map { |cert| cert['serial']['id']}
    (old_ids & new_ids).size.should == 0

  end

  it 'can be manually regenerated for a product' do
    coolapp = create_product
    @cp.create_subscription(@owner['key'], coolapp.id, 10)
    @cp.refresh_pools(@owner['key'])
    @system.consume_product coolapp.id

    @system.list_certificates.length.should == 2
    old_certs = @system.list_certificates()

    @cp.regenerate_entitlement_certificates_for_product(coolapp.id)

    new_certs = @system.list_certificates()
    old_certs.size.should == new_certs.size
    old_ids = old_certs.map { |cert| cert['serial']['id']}
    new_ids = new_certs.map { |cert| cert['serial']['id']}
    # System has two certs, but we only regenerated for one product, so the
    # other serial should have remained the same:
    (old_ids & new_ids).size.should == 1
  end

  it 'will be regenerated when changing existing subscription\'s end date' do
    sub = @cp.list_subscriptions(@owner['key'])[0]
    sub.endDate = sub.endDate.to_date + 2
    old_cert = @system.list_certificates()[0]
    @cp.update_subscription(sub)

    @cp.refresh_pools(@owner['key'])

    new_cert = @system.list_certificates()[0]
    old_cert.serial.id.should_not == new_cert.serial.id

    ent = @cp.get_entitlement(@entitlement.id)
    sub.endDate.should == ent.endDate.to_date
  end

  it 'those in excess will be deleted when existing subscription quantity is decreased' do
      prod = create_product(nil, nil, {:attributes => {"multi-entitlement" => "yes"}})
      sub = @cp.create_subscription(@owner['key'], prod.id, 10)
      @cp.refresh_pools(@owner['key'])
      pool = @cp.list_pools({:owner => @owner['id'], :product => prod['id']})[0]

      @system.consume_pool(pool['id'], {:quantity => 6})
      sub.quantity = sub.quantity.to_i - 5
      @cp.update_subscription(sub)

      @cp.refresh_pools(@owner['key'])

      @system.list_certificates().size.should == 1
  end

  it 'will be regenerated when existing subscription\'s quantity and dates are changed' do
      old_cert = @system.list_certificates()[0]
      change_dt_and_qty()
      new_cert = @system.list_certificates()[0]
      old_cert.serial.id.should_not == new_cert.serial.id
  end

  it 'will be regenerated and dates will have the same values as that of the subscription which was changed' do
      sub = change_dt_and_qty()
      new_cert = @system.list_certificates()[0]
      x509 = OpenSSL::X509::Certificate.new(new_cert['cert'])
      sub['startDate'].should == x509.not_before().strftime('%Y-%m-%d').to_date
      sub['endDate'].should == x509.not_after().strftime('%Y-%m-%d').to_date
  end

  it "won't let one consumer regenerate another's certificates" do
    @system.list_certificates.length.should == 1
    @system2 = consumer_client(@user, random_string('system2'))

    lambda do
      @system2.put("/consumers/#{@system.uuid}/certificates")
    end.should raise_exception(RestClient::Forbidden)
  end

  it "won't let one consumer regenerate another's certificate by entitlement" do
    @system.list_certificates.length.should == 1
    ents = @system.list_entitlements
    @system2 = consumer_client(@user, random_string('system2'))

    lambda do
      @system2.regenerate_entitlement_certificates_for_entitlement(ents.first.id, @system.uuid)
    end.should raise_exception(RestClient::Forbidden)
  end
end
