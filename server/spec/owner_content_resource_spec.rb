require 'spec_helper'
require 'candlepin_scenarios'

describe 'Owner Content Resource' do

  include CandlepinMethods

  before(:each) do
    @owner = create_owner random_string('test_owner')
    @user = user_client(@owner, random_string('test-user'))

    @content = @cp.create_content(
      @owner['key'], "cname", 'test-content', random_string("clabel"), "ctype", "cvendor", {}, true
    )

    @content_id = @content['id']

    @product = create_product('test-product', 'some product', {:multiplier => 4})
    @cp.add_content_to_product(@owner['key'], @product['id'], @content_id)
  end



  it 'lists content in pages' do
    @owner = create_owner random_string('test_owner')

    c1_id = "test_content-1"
    c2_id = "test_content-2"
    c3_id = "test_content-3"

    # The creation order here is important. By default, Candlepin sorts in descending order of the
    # entity's creation time, so we need to create them backward to let the default sorting order
    # let us page through them in ascending order.
    c3 = @cp.create_content(@owner['key'], "c3", c3_id, "c3", "ctype", "cvendor", {}, true)
    sleep 1
    c2 = @cp.create_content(@owner['key'], "c2", c2_id, "c2", "ctype", "cvendor", {}, true)
    sleep 1
    c1 = @cp.create_content(@owner['key'], "c1", c1_id, "c1", "ctype", "cvendor", {}, true)

    c1set = @cp.list_content(@owner['key'], {:page=>1, :per_page=>1})
    expect(c1set.size).to eq(1)
    expect(c1set[0]['id']).to eq(c1_id)

    c2set = @cp.list_content(@owner['key'], {:page=>2, :per_page=>1})
    expect(c2set.size).to eq(1)
    expect(c2set[0]['id']).to eq(c2_id)

    c3set = @cp.list_content(@owner['key'], {:page=>3, :per_page=>1})
    expect(c3set.size).to eq(1)
    expect(c3set[0]['id']).to eq(c3_id)

    c4set = @cp.list_content(@owner['key'], {:page=>4, :per_page=>1})
    expect(c4set.size).to eq(0)
  end

  it 'lists content in sorted pages' do
    @owner = create_owner random_string('test_owner')

    c1_id = "test_content-1"
    c2_id = "test_content-2"
    c3_id = "test_content-3"

    # The creation order here is important so we don't accidentally setup the correct ordering by
    # default.
    c2 = @cp.create_content(@owner['key'], "c2", c2_id, "c2", "ctype", "cvendor", {}, true)
    sleep 1
    c1 = @cp.create_content(@owner['key'], "c1", c1_id, "c1", "ctype", "cvendor", {}, true)
    sleep 1
    c3 = @cp.create_content(@owner['key'], "c3", c3_id, "c3", "ctype", "cvendor", {}, true)

    c1set = @cp.list_content(@owner['key'], {:page=>1, :per_page=>1, :sort_by=>"id"})
    expect(c1set.size).to eq(1)
    expect(c1set[0]['id']).to eq(c3_id)

    c2set = @cp.list_content(@owner['key'], {:page=>2, :per_page=>1, :sort_by=>"id"})
    expect(c2set.size).to eq(1)
    expect(c2set[0]['id']).to eq(c2_id)

    c3set = @cp.list_content(@owner['key'], {:page=>3, :per_page=>1, :sort_by=>"id"})
    expect(c3set.size).to eq(1)
    expect(c3set[0]['id']).to eq(c1_id)

    c4set = @cp.list_content(@owner['key'], {:page=>4, :per_page=>1, :sort_by=>"id"})
    expect(c4set.size).to eq(0)
  end

  it 'should allow content creation' do
    # Make sure the content was really created
    result_content = @cp.get_content(@owner['key'], @content_id)
    result_content['name'].should == "cname"
  end

  it 'should show content on products' do
    product = @cp.get_product(@owner['key'], @product['id'])
    product.productContent.size.should == 1
    product.productContent[0]['content']['name'].should == "cname"
  end

  it 'should remove content from products upon deletion' do
    @cp.delete_content(@owner['key'], @content_id)
    product = @cp.get_product(@owner['key'], @product['id'])
    product.productContent.size.should == 0
  end

  it 'should allow content to be updated' do
    @cp.update_content(@owner['key'], @content_id, {"gpgUrl" => "somegpgurl"})
    content = @cp.get_content(@owner['key'], @content_id)
    content.gpgUrl.should == "somegpgurl"
  end

  it 'should force entitlements providing changed content to be regenerated' do
    # tests standalone mode specific API. in hosted, we refresh, so this test is captured in refresh spec.
    skip("candlepin running in hosted mode") if is_hosted?
    product_pool = create_pool_and_subscription(@owner['key'], @product.id, 100, [], '1888', '1234')
    consumer_client = consumer_client(@user,  random_string("consumer"))

    consumer_client.consume_pool(product_pool.id, {:quantity => 1})
    original_serial = consumer_client.list_certificate_serials[0]['serial']

    # Modify content
    @cp.update_content(@owner['key'], @content_id, {"gpgUrl" => "newgpgurl"})
    new_serial = consumer_client.list_certificate_serials[0]['serial']

    # Verify the serial has changed (the entitlement has been regenerated)
    original_serial.should_not == new_serial
  end
end
