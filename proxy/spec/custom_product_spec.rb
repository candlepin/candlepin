require 'candlepin_scenarios'

describe 'Custom Product' do

  include CandlepinMethods
  include CandlepinScenarios


  before(:each) do
    @owner = create_owner(random_string("test_owner"))
  end

 it 'create custom products and subscribe' do
    owner_client = user_client(@owner, random_string('testuser'))

    product1 = create_product(@owner.key + '_product_1', @owner.key + '_product_1',
        {:custom => false})
    product2 = create_product('', @owner.key + '_product_2',
        {:custom => true})
    content = create_content({:metadata_expire => 6000,
      :required_tags => "TAG1,TAG2"})
    @cp.add_content_to_product(product1.id, content.id)
    @cp.add_content_to_product(product2.id, content.id)
    @end_date = Date.new(2025, 5, 29)

    sub1 = @cp.create_subscription(@owner.key, product1.id, 2, [], '', '12345', nil, @end_date)
    sub2 = @cp.create_subscription(@owner.key, product2.id, 4, [], '', '12345', nil, @end_date)
    @cp.refresh_pools(@owner.key)

    pool1 = @cp.list_pools(:owner => @owner.id, :product => product1.id)[0]
    pool2 = @cp.list_pools(:owner => @owner.id, :product => product2.id)[0]

    candlepin_client = consumer_client(owner_client, random_string(),
        "candlepin")
    candlepin_client.consume_pool(pool1.id)[0]
    candlepin_client.consume_pool(pool2.id)[0]

    product1.id.should == @owner.key + '_product_1'
    product2.id.should_not == ''
    @cp.delete_subscription(sub1['id'])
    @cp.delete_subscription(sub2['id'])

 end


end
