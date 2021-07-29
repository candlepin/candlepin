require 'spec_helper'
require 'candlepin_scenarios'

describe 'Content Resource' do

  include CandlepinMethods
  include CertificateMethods

  before(:each) do
    @owner = create_owner random_string('test_owner')
    @user = user_client(@owner, random_string('test-user'))
  end

  it 'should show content on products' do
    content = @cp.create_content(
      @owner['key'], "cname", 'test-content', random_string("clabel"), "ctype", "cvendor", {}, true
    )

    product = create_product('test-product', 'some product', {:multiplier => 4})
    @cp.add_content_to_product(@owner['key'], product['id'], content['id'])

    product = @cp.get_product(@owner['key'], product['id'])
    product.productContent.size.should == 1
    product.productContent[0]['content']['name'].should == "cname"
  end

  it "should filter content with mismatched architecture" do
    # We expect this content to NOT be filtered out due to a match with the system's architecture
    content1 = @cp.create_content(
      @owner['key'], "cname1", 'test-content1', random_string("clabel1"), "ctype1", "cvendor1",
      {:content_url=> '/this/is/the/path', :arches => "ppc64"}, true)

    # We expect this content to be filtered out due to a mismatch with the system's architecture
    content2 = @cp.create_content(
      @owner['key'], "cname2", 'test-content2', random_string("clabel2"), "ctype2", "cvendor2",
      {:content_url=> '/this/is/the/path/2', :arches => "x86_64"}, true)

    # We expect this content to NOT be filtered out due it not specifying an architecture
    content3 = @cp.create_content(
      @owner['key'], "cname3", 'test-content3', random_string("clabel3"), "ctype3", "cvendor3",
      {:content_url=> '/this/is/the/path/3' }, true)

    product = create_product(nil, random_string('some product'))

    @cp.add_content_to_product(@owner['key'], product['id'], content1['id'])
    @cp.add_content_to_product(@owner['key'], product['id'], content2['id'])
    @cp.add_content_to_product(@owner['key'], product['id'], content3['id'])

    pool = @cp.create_pool(@owner['key'], product['id'], {:quantity => 10})

    consumer = consumer_client(@user, random_string('consumer-name'), type=:system, username=nil,
      facts={'system.certificate_version' => '3.3', "uname.machine" => "ppc64"}, owner_key=@owner['key'])

    consumer.consume_pool(pool['id'])

    certs = consumer.list_certificates
    certs.length.should == 1

    json_body = extract_payload(certs[0]['cert'])
    json_body['products'].length.should == 1
    json_body['products'][0]['content'].length.should == 2

    # figure out the order of content in the cert, so we can assert properly
    first_content_name = json_body['products'][0]['content'][0].name
    if first_content_name == content1.name
      content1_output = json_body['products'][0]['content'][0]
      content3_output = json_body['products'][0]['content'][1]
    else
      content1_output = json_body['products'][0]['content'][1]
      content3_output = json_body['products'][0]['content'][0]
    end

    expect(content1_output['type']).to eq(content1.type)
    expect(content1_output['name']).to eq(content1.name)
    expect(content1_output['label']).to eq(content1.label)
    expect(content1_output['vendor']).to eq(content1.vendor)
    expect(content1_output['path']).to eq(content1.contentUrl)
    expect(content1_output['arches'][0]).to eq(content1.arches)

    expect(content3_output['type']).to eq(content3.type)
    expect(content3_output['name']).to eq(content3.name)
    expect(content3_output['label']).to eq(content3.label)
    expect(content3_output['vendor']).to eq(content3.vendor)
    expect(content3_output['path']).to eq(content3.contentUrl)
    expect(content3_output['arches'][0]).to eq(content3.arches)
  end

  it "should filter content with mismatched architecture from the product" do
    # Even though this product has no arches specified, and should normally not be filtered out, the product it
    # belongs to has an architecture that mismatches with the system's, so we do expect it to get filtered out.
    content1 = @cp.create_content(
      @owner['key'], "cname1", 'test-content1', random_string("clabel1"), "ctype1", "cvendor1",
      { :content_url=> '/this/is/the/path' }, true)

    product1 = create_product(nil, random_string('some product'), :attributes => { :arch => 'x86_64'} )
    @cp.add_content_to_product(@owner['key'], product1['id'], content1['id'])

    # This content has no arches specified, but the product it belongs to has an arch that matches with that of
    # the system's, so we do NOT expect it to get filtered out.
    content2 = @cp.create_content(
      @owner['key'], "cname2", 'test-content2', random_string("clabel2"), "ctype2", "cvendor2",
      { :content_url=> '/this/is/the/path/2' }, true)

    product2 = create_product(nil, random_string('some product'), :attributes => { :arch => 'ppc64'} )
    @cp.add_content_to_product(@owner['key'], product2['id'], content2['id'])

    pool1 = @cp.create_pool(@owner['key'], product1['id'], {:quantity => 10})
    pool2 = @cp.create_pool(@owner['key'], product2['id'], {:quantity => 10})

    consumer = consumer_client(@user, random_string('consumer-name'), type=:system, username=nil,
      facts={'system.certificate_version' => '3.3', "uname.machine" => "ppc64"}, owner_key=@owner['key'])

    consumer.consume_pool(pool1['id'])
    consumer.consume_pool(pool2['id'])

    certs = consumer.list_certificates
    certs.length.should == 2

    json_body1 = extract_payload(certs[0]['cert'])
    expect(json_body1['products'].length).to eq(1)

    json_body2 = extract_payload(certs[1]['cert'])
    expect(json_body2['products'].length).to eq(1)

    # figure out the order of products in the cert, so we can assert properly
    if json_body1['products'][0].id == product1.id
      product1_output = json_body1['products'][0]
      product2_output = json_body2['products'][0]
    else
      product1_output = json_body2['products'][0]
      product2_output = json_body1['products'][0]
    end

    expect(product1_output['content'].length).to eq(0)
    expect(product2_output['content'].length).to eq(1)

    expect(product2_output['content'][0]['type']).to eq(content2.type)
    expect(product2_output['content'][0]['name']).to eq(content2.name)
    expect(product2_output['content'][0]['label']).to eq(content2.label)
    expect(product2_output['content'][0]['vendor']).to eq(content2.vendor)
    expect(product2_output['content'][0]['path']).to eq(content2.contentUrl)

    # Verify that the content's arch was inherited by the product
    product_arch_value = ''
    product2['attributes'].find { |attr|
      if attr['name'] == 'arch'
        product_arch_value = attr['value']
      end
    }
    expect(product2_output['content'][0]['arches'][0]).to eq(product_arch_value)
  end

end
