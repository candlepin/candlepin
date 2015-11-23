require 'spec_helper'
require 'candlepin_scenarios'

require 'rubygems'
require 'rest_client'

require "base64"
require "zip/zip"
require 'openssl'


describe 'Uebercert' do
  include CandlepinMethods
  include CertificateMethods

  it 'owner can be deleted' do
    owner = @cp.create_owner random_string("test_owner1")
    @cp.generate_ueber_cert(owner['key'])
    @cp.delete_owner(owner['key'], false)

    lambda do
      @cp.get_owner(owner['key'])
    end.should raise_exception(RestClient::ResourceNotFound)
  end

  it 'consumers can be deleted' do
    owner = @cp.create_owner random_string("test_owner1")
    @cp.generate_ueber_cert(owner['key'])
    consumers = @cp.list_consumers({:owner => owner['key']})
    consumers.size.should == 1
    uber_consumer = consumers[0]
    @cp.unregister(uber_consumer['uuid'])
    @cp.list_consumers({:owner => owner['key']}).size.should == 0
  end

  it "contains all content for the entire org" do
    prod_id1 = "test_prod_1"
    prod_id2 = "test_prod_2"
    prod_id3 = "test_prod_3"

    owner1 = create_owner(random_string("test_owner_1"))
    owner2 = create_owner(random_string("test_owner_2"))

    prod1 = create_product(prod_id1, "test product 1", {:owner => owner1['key']})
    prod2 = create_product(prod_id2, "test product 2", {:owner => owner1['key']})
    prod3 = create_product(prod_id3, "test product 3", {:owner => owner1['key']})

    content1 = create_content({:owner => owner1['key']})
    content2 = create_content({:owner => owner1['key']})
    content3 = create_content({:owner => owner1['key']})

    @cp.add_content_to_product(owner1['key'], prod1.id, content1.id, true)
    @cp.add_content_to_product(owner1['key'], prod1.id, content2.id, true)
    @cp.add_content_to_product(owner1['key'], prod2.id, content3.id, true)

    create_pool_and_subscription(owner1['key'], prod1.id, 10, [], '12345', '6789', 'order1',
				nil, nil, true)
    create_pool_and_subscription(owner1['key'], prod2.id, 10, [], 'abcde', 'fghi', 'order2',
				nil, nil, true)
    create_pool_and_subscription(owner1['key'], prod3.id, 10, [], 'qwert', 'yuio', 'order3')

    # generate and verify cert
    ueber_cert = @cp.generate_ueber_cert(owner1['key'])
    ueber_cert.should_not be_nil

    consumers = @cp.list_consumers({:owner => owner1['key'], :type => "uebercert"})
    consumers.should_not be_nil
    consumers.length.should == 1

    ueber_consumer = consumers[0]
    ueber_consumer["uuid"].should_not be_nil
    ueber_consumer["uuid"].should_not be_empty

    entitlements = @cp.list_entitlements({:uuid => ueber_consumer["uuid"]})
    entitlements.should_not be_nil
    entitlements.length.should == 1
    entitlements[0].should_not be_nil

    ueber_entitlement = entitlements[0]
    ueber_entitlement["certificates"].should_not be_nil
    ueber_entitlement["certificates"].length.should == 1

    x509 = OpenSSL::X509::Certificate.new(ueber_entitlement["certificates"][0]["cert"])
    extensions_hash = Hash[x509.extensions.collect { |ext| [ext.oid, ext.to_der()] }]

    cert_product = nil
    cert_content = nil

    REDHAT_OID = "1.3.6.1.4.1.2312.9"

    extensions_hash.each do |ext_id, ext_value|
      if ext_id.start_with?(REDHAT_OID)
        ext_id = ext_id.slice(REDHAT_OID.length() + 1, ext_id.length())

        match = /\A(1|2)\.(.+)\.1(?:\.(\d))?/.match(ext_id)

        if match
          asn1_body = nil
          asn1 = OpenSSL::ASN1.decode(ext_value)
          OpenSSL::ASN1.traverse(asn1.value[1]) do |depth, offset, header_len, length, constructed, tag_class, tag|
            asn1_body = asn1.value[1].value[header_len, length]
          end

          if match[1] == "1"
            cert_product.should be_nil
            cert_product = asn1_body
          elsif match[1] == "2" and match[3] == "2"
            cert_content.should be_nil
            cert_content = asn1_body
          end
        end
      end
    end

    exp_product_suffix = "ueber_product"
    exp_content_suffix = "ueber_content"

    cert_product.should_not be_nil
    cert_product_suffix = cert_product.slice(-(exp_product_suffix.length()), exp_product_suffix.length())
    cert_product_suffix.should == exp_product_suffix

    cert_content.should_not be_nil
    cert_content_suffix = cert_content.slice(-(exp_content_suffix.length()), exp_content_suffix.length())
    cert_content_suffix.should == exp_content_suffix
  end
end

