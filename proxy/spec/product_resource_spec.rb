require 'candlepin_scenarios'

describe 'Product Resource' do

  include CandlepinMethods
  include CandlepinScenarios

  it 'removes content from products.' do
    prod = create_product
    content = create_content
    @cp.add_content_to_product(prod['id'], content['id'])
    prod = @cp.get_product(prod['id'])
    prod['productContent'].size.should == 1

    @cp.remove_content_from_product(prod['id'], content['id'])
    prod = @cp.get_product(prod['id'])
    prod['productContent'].should be_empty
  end

  it 'create and delete a product' do
    prod = create_product
    @cp.delete_product(prod['id'])
  end

  it 'delete an unknown product' do
    lambda {
       @cp.delete_product('this-is-not-a-product')
    }.should raise_exception(RestClient::ResourceNotFound)
  end
end

