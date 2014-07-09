require 'spec_helper'
require 'candlepin_scenarios'

describe 'Content Resource' do

  include CandlepinMethods

  before(:each) do
    @owner = create_owner random_string('test_owner')
    @user = user_client(@owner, random_string('test-user'))

    @content = @cp.create_content("cname", nil, random_string("clabel"), "ctype", "cvendor", {}, true)
    @content_id = @content['id']

    @product = create_product(nil, 'some product', {:multiplier => 4})
    @cp.add_content_to_product(@product['id'], @content_id)
  end

  it 'should allow content creation' do
    # Make sure the content was really created
    result_content = @cp.get_content(@content_id)
    result_content['name'].should == "cname"
  end

  it 'should show content on products' do
    @product = @cp.get_product(@product['id'])
    @product.productContent.size.should == 1
    @product.productContent[0]['content']['name'].should == "cname"
  end

  it 'should remove content from products upon deletion' do
    @cp.delete_content(@content_id)
    @product = @cp.get_product(@product['id'])
    @product.productContent.size.should == 0
  end

  it 'should allow content to be updated' do
    @cp.update_content(@content_id, {"gpgUrl" => "somegpgurl"})
    @content = @cp.get_content(@content_id)
    @content.gpgUrl.should == "somegpgurl"
  end

  it 'should force entitlements providing changed content to be regenerated' do
    product_sub = @cp.create_subscription(@owner['key'], @product.id, 100, [], '1888', '1234')
    @cp.refresh_pools(@owner['key'])
    consumer_client = consumer_client(@user,  random_string("consumer"))

    pool = find_pool(@owner.id, product_sub.id)
    consumer_client.consume_pool(pool.id, {:quantity => 1})
    original_serial = consumer_client.list_certificate_serials[0]['serial']

    # Modify content
    @cp.update_content(@content_id, {"gpgUrl" => "newgpgurl"})
    new_serial = consumer_client.list_certificate_serials[0]['serial']

    # Verify the serial has changed (the entitlement has been regenerated)
    original_serial.should_not == new_serial
  end
end
