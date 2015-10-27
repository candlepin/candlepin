require 'spec_helper'
require 'candlepin_scenarios'
require 'time'

describe 'Healing' do

  include CandlepinMethods

  before(:each) do
    @owner = create_owner random_string('test_owner1')
    @username = random_string("user1")
    consumername1 = random_string("consumer1")
    @user_cp = user_client(@owner, @username)

    @product1 = create_product()
    @product2 = create_product()
    @product3 = create_product()
    installed = [
        {'productId' => @product1['id'], 'productName' => @product1['name']},
        {'productId' => @product2['id'], 'productName' => @product2['name']}]

    @consumer = @user_cp.register(consumername1, :system, nil,
      {'cpu.cpu_socket(s)' => '8'}, nil, @owner['key'], [], installed)
    @consumer_cp = Candlepin.new(nil, nil, @consumer.idCert.cert, @consumer.idCert['key'])
  end

  it 'entitles non-compliant products' do
    parent_prod = create_product()
    current_pool = create_pool_and_subscription(@owner['key'], parent_prod['id'],
      10, [@product1['id'], @product2['id']])

    # Create a future sub, the entitlement should not come from this one:
    future_pool = create_pool_and_subscription(@owner['key'], parent_prod['id'],
      10, [@product1['id'], @product2['id']], '', '', Date.today + 30,
        Date.today + 60)

    ents = @consumer_cp.consume_product()
    ents.size.should == 1
    ents[0]['pool']['id'].should == current_pool['id']
  end

  it 'entitles non-compliant products despite a valid future entitlement' do
    parent_prod = create_product()
    current_pool = create_pool_and_subscription(@owner['key'], parent_prod['id'],
      10, [@product1['id'], @product2['id']])

    # Create a future sub, the entitlement should not come from this one:
    future_pool = create_pool_and_subscription(@owner['key'], parent_prod['id'],
      10, [@product1['id'], @product2['id']], '', '', '', Date.today + 30,
        Date.today + 60)

    # 35 days in future should land in our sub:
    future_iso8601 = (Time.now + (60 * 60 * 24 * 35)).utc.iso8601 # a string

    ent = @consumer_cp.consume_pool(future_pool['id'], {:quantity => 1})

    ents = @consumer_cp.consume_product()
    ents.size.should == 1
    ents[0]['pool']['id'].should == current_pool['id']
  end

  it 'entitles non-compliant products at a future date' do
    parent_prod = create_product()

    # This one should be skipped, as we're going to specify a future date:
    current_pool = create_pool_and_subscription(@owner['key'], parent_prod['id'],
      10, [@product1['id'], @product2['id']])

    # Create a future sub, entitlement should end up coming from here:
    future_pool = create_pool_and_subscription(@owner['key'], parent_prod['id'],
      10, [@product1['id'], @product2['id']], '', '', '', Date.today + 365 * 2,
        Date.today + 365 * 4) # valid 2-4 years from now

    future_iso8601 = (Time.now + (60 * 60 * 24 * 365 * 3)).utc.iso8601 # a string

    # Heal for 3 years in the future, right in the middle of our future subscription:
    ents = @consumer_cp.consume_product(nil,
      {:entitle_date => future_iso8601})
    ents.size.should == 1
    ents[0]['pool']['id'].should == future_pool['id']
  end

  it 'can multi-entitle stacked entitlements' do
    stack_id = 'mystack'
    parent_prod = create_product(nil, nil, :attributes => {
      :sockets => '2', :'multi-entitlement' => 'yes', :stacking_id => stack_id})
    current_pool = create_pool_and_subscription(@owner['key'], parent_prod['id'],
      10, [@product1['id'], @product2['id']])

    ents = @consumer_cp.consume_product()
    ents.size.should == 1
    ents[0]['pool']['id'].should == current_pool['id']
    ents[0]['quantity'].should == 4
  end

  it 'can complete partial stacks with no installed prod' do
    stack_id = 'mystack'
    parent_prod = create_product(nil, nil, :attributes => {
      :sockets => '2', :'multi-entitlement' => 'yes', :stacking_id => stack_id})
    current_pool = create_pool_and_subscription(@owner['key'], parent_prod['id'],
      10, [@product3['id']])

    # Consume 2 of the four required
    @consumer_cp.consume_pool(current_pool['id'], {:quantity => 2})
    # Now we have a partial stack that covers no installed products

    ents = @consumer_cp.consume_product()
    # Should have added one entitlement, quantity 2
    ents.size.should == 1
    ents[0]['pool']['id'].should == current_pool['id']
    ents[0]['quantity'].should == 2
  end

  it 'can multi-entitle stacked entitlements across pools' do
    stack_id = 'mystack'
    parent_prod = create_product(nil, nil, :attributes => {
      :sockets => '2', :'multi-entitlement' => 'yes', :stacking_id => stack_id})
    create_pool_and_subscription(@owner['key'], parent_prod['id'],
      2, [@product1['id'], @product2['id']])
    parent_prod2 = create_product(nil, nil, :attributes => {
      :sockets => '2', :'multi-entitlement' => 'yes', :stacking_id => stack_id})
    create_pool_and_subscription(@owner['key'], parent_prod2['id'],
      2, [@product1['id'], @product2['id']])

    ents = @consumer_cp.consume_product()
    ents.size.should == 2
    ents[0]['quantity'].should == 2
    ents[1]['quantity'].should == 2
  end

  it 'can complete a pre-existing partial stack' do
    stack_id = 'mystack'
    parent_prod = create_product(nil, nil, :attributes => {
      :sockets => '2', :'multi-entitlement' => 'yes', :stacking_id => stack_id})
    current_pool = create_pool_and_subscription(@owner['key'], parent_prod['id'],
      10, [@product1['id'], @product2['id']])

    # First a normal bind to get two entitlements covering 4 of our 8 sockets:
    @consumer_cp.consume_pool(current_pool['id'], {:quantity => 2})

    installed = [
        {'productId' => @product1['id'], 'productName' => @product1['name']},
    ]

    @consumer_cp.update_consumer({:installedProducts => installed})
    # Healing should now get us another entitlement also of quantity 2:
    ents = @consumer_cp.consume_product()
    ents.size.should == 1
    ents[0]['quantity'].should == 2
  end

end
