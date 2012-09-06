require 'candlepin_scenarios'
require 'json'
require 'unpack'

describe 'Entitlement Certificate V3' do
  include CandlepinMethods
  include CandlepinScenarios
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
                               :content_url => '/content/dist/rhel/$releasever/$basearch/os',
                               :metadata_expire => 6400,
                               :required_tags => 'TAG1,TAG2',})

    @cp.add_content_to_product(@product.id, @content.id, false)

    @subscription = @cp.create_subscription(@owner['key'], @product.id, 10, [], '12345', '6789')
    @cp.refresh_pools(@owner['key'])

    @user = user_client(@owner, random_string('billy'))

    @system = consumer_client(@user, random_string('system1'), :candlepin, nil,
				{'system.certificate_version' => '3.1'})
    @entitlement = @system.consume_product(@product.id)[0]
  end

  it 'generated a version 3.0 certificate' do
    value = extension_from_cert(@system.list_certificates[0]['cert'], "1.3.6.1.4.1.2312.9.6")
    value.should == "3.0"
  end

  it 'generated the correct body in the blob' do
    payload = @system.list_certificates[0]['payload']
    payload_end = payload.length - 32
    payload = payload[33..payload_end] 
    asn1_body = Base64.decode64(payload)
    body = Zlib::Inflate.inflate(asn1_body)
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
    json_body['products'][0]['content'][0]['path'].should == '/content/dist/rhel/$releasever/$basearch/os'
    json_body['products'][0]['content'][0]['enabled'].should == false
    json_body['products'][0]['content'][0]['metadata_expire'].should == 6400
    json_body['products'][0]['content'][0]['required_tags'].size.should == 2
  end

  it 'encoded the content urls' do
    @content_1 = create_content({:content_url => '/content/dist/rhel/$releasever/$basearch/debug',})
    @cp.add_content_to_product(@product.id, @content_1.id, true)
    @content_2 = create_content({:content_url => '/content/beta/rhel/$releasever/$basearch/source/SRPMS',})
    @cp.add_content_to_product(@product.id, @content_2.id, true)
    @cp.refresh_pools(@owner['key'])
    @system.consume_product(@product.id)[0]

    payload = @system.list_certificates[1]['payload']
    payload_end = payload.length - 32
    payload = payload[33..payload_end] 
    asn1_body = Base64.decode64(payload)
    body = Zlib::Inflate.inflate(asn1_body)
    json_body = JSON.parse(body)

    json_body['products'][0]['content'].size.should == 3

    value = extension_from_cert(@system.list_certificates[1]['cert'], "1.3.6.1.4.1.2312.9.7")

    File.open('tmp.bin', 'w') do |f1|  
      f1.puts value
    end
  
    urls = []
    urls[0] = '/content/dist/rhel/$releasever/$basearch/os'
    urls[1] = '/content/dist/rhel/$releasever/$basearch/debug'
    urls[2] = '/content/beta/rhel/$releasever/$basearch/source/SRPMS'
    are_content_urls_present(value, urls).should == true

  end
end
