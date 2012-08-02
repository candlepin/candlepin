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

  it 'allows regular users to view products' do
    owner = @cp.create_owner random_string('test')
    user_cp = user_client(owner, random_string('testuser'), true)
    prod = create_product
    user_cp.get_product(prod['id'])
  end

  it 'create two products with the same name' do
    product1 = create_product(id=nil, name='doppelganger')
    product2 = create_product(id=nil, name='doppelganger')
    product1.id.should_not  == product2.id
    product1.name.should == product2.name
  end

  it 'retrieves the owners of a product' do
    owner = create_owner(random_string('owner'))
    product = create_product(random_string("test_id"),
      random_string("test_name"))
    provided_product = create_product()
    @cp.create_subscription(owner['key'], product.id, 10, [provided_product.id])
    @cp.refresh_pools(owner['key'])
    user = user_client(owner, random_string('billy'))
    system = consumer_client(user, 'system6')
    system.consume_product(product.id)
    product_owners = @cp.get_product_owners([provided_product.id])
    product_owners.should have(1).things
    product_owners[0]['key'].should == owner['key']
  end
end

