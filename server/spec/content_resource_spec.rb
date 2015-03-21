require 'spec_helper'
require 'candlepin_scenarios'

describe 'Content Resource' do

  include CandlepinMethods

  before(:each) do
    @owner = create_owner random_string('test_owner')
    @user = user_client(@owner, random_string('test-user'))

    @content = @cp.create_content(
      @owner['key'], "cname", nil, random_string("clabel"), "ctype", "cvendor", {}, true
    )

    @content_id = @content['id']

    @product = create_product(nil, 'some product', {:multiplier => 4})
    @cp.add_content_to_product(@owner['key'], @product['id'], @content_id)
  end


  it 'should throw exceptions on write operations' do
    lambda do
      @cp.post("/content", {})
    end.should raise_exception(RestClient::BadRequest)

    lambda do
      @cp.put("/content/dummyid", {})
    end.should raise_exception(RestClient::BadRequest)

    lambda do
      @cp.post("/content/batch", [])
    end.should raise_exception(RestClient::BadRequest)

    lambda do
      @cp.delete("/content/dummyid")
    end.should raise_exception(RestClient::BadRequest)
  end

  it 'should show content on products' do
    @product = @cp.get_product(@owner['key'], @product['id'])
    @product.productContent.size.should == 1
    @product.productContent[0]['content']['name'].should == "cname"
  end

end
