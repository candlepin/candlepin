require 'spec_helper'
require 'candlepin_scenarios'

describe 'Content Resource' do

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

  it 'should show content on products' do
    @product = @cp.get_product(@owner['key'], @product['id'])
    @product.productContent.size.should == 1
    @product.productContent[0]['content']['name'].should == "cname"
  end

end
