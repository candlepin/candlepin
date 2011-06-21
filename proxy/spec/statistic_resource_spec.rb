require 'candlepin_scenarios'

describe 'Statistic Resource' do

  include CandlepinMethods
  include CandlepinScenarios

  it 'view statistics for created pool and product' do
    owner1 = create_owner random_string('test_owner')
    owner1_client = user_client(owner1, random_string('testuser'))

    product = create_product
    @cp.create_subscription(owner1.key, product.id, 10)
    @cp.refresh_pools(owner1.key)

    pool = owner1_client.list_pools(:owner => owner1.id).first

    consumer_client = consumer_client(owner1_client, random_string('testsystem'))
    p = consumer_client.get_pool(pool.id)
    consumer_client.consume_pool(p.id)
    
    @cp.generate_statistics

    @cp.get_owner_perpool(owner1.key, p.id, 'CONSUMED').first.value.should == 1
    @cp.get_owner_perpool(owner1.key, p.id, 'USED').first.value.should == 1
    @cp.get_owner_perpool(owner1.key, p.id, 'PERCENTAGECONSUMED').first.value.should == 10

    @cp.get_owner_perproduct(owner1.key, product.id, 'CONSUMED').first.value.should == 1
    @cp.get_owner_perproduct(owner1.key, product.id, 'USED').first.value.should == 1
    @cp.get_owner_perproduct(owner1.key, product.id, 'PERCENTAGECONSUMED').first.value.should == 10

    @cp.get_perpool(p.id, 'CONSUMED').first.value.should == 1
    @cp.get_perpool(p.id, 'USED').first.value.should == 1
    @cp.get_perpool(p.id, 'PERCENTAGECONSUMED').first.value.should == 10

    @cp.get_perproduct(product.id, 'CONSUMED').first.value.should == 1
    @cp.get_perproduct(product.id, 'USED').first.value.should == 1
    @cp.get_perproduct(product.id, 'PERCENTAGECONSUMED').first.value.should == 10

    @cp.get_consumer_count(owner1.key).first.value.should == 1
    @cp.get_subscription_count_raw(owner1.key).first.value.should == 10
    @cp.get_subscription_consumed_count_raw(owner1.key).first.value.should == 1
    @cp.get_subscription_consumed_count_percentage(owner1.key).first.value.should == 10

    @cp.get_system_physical_count(owner1.key).first.value.should == 1
    @cp.get_system_virtual_count(owner1.key).first.value.should == 0


  end

  
end
