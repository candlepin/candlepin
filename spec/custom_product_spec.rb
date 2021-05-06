require 'spec_helper'
require 'candlepin_scenarios'

describe 'Custom Product' do

  include CandlepinMethods


  before(:each) do
    @owner = create_owner(random_string("test_owner"))
  end

 it 'create custom products and subscribe' do
    owner_client = user_client(@owner, random_string('testuser'))

    product1 = create_product(
        @owner['key'] + '_product_1', @owner['key'] + '_product_1', {:custom => false}
    )
    product2 = create_product(
        'bacon', @owner['key'] + '_product_2', {:custom => true}
    )
    content = create_content({:metadata_expire => 6000, :required_tags => "TAG1,TAG2"})
    @cp.add_content_to_product(@owner['key'], product1.id, content.id)
    @cp.add_content_to_product(@owner['key'], product2.id, content.id)
    @end_date = Date.new(2025, 5, 29)

    create_pool_and_subscription(@owner['key'], product1.id, 2, [], '', '12345', '6789', nil, @end_date, true)
    create_pool_and_subscription(@owner['key'], product2.id, 4, [], '', '12345', '6789', nil, @end_date)

    pool1 = @cp.list_pools(:owner => @owner.id, :product => product1.id)[0]
    pool2 = @cp.list_pools(:owner => @owner.id, :product => product2.id)[0]

    candlepin_client = consumer_client(owner_client, random_string(), "candlepin")
    candlepin_client.consume_pool(pool1.id, {:quantity => 1})[0]
    candlepin_client.consume_pool(pool2.id, {:quantity => 1})[0]

    product1.id.should == @owner['key'] + '_product_1'
    product2.id.should_not == ''

 end


end
