require 'candlepin_scenarios'

describe 'Subscription Resource' do

  include CandlepinMethods
  it_should_behave_like 'Candlepin Scenarios'

  before do
    @owner = create_owner random_string('test_owner')
    @some_product = create_product(name='some_product')
    @another_product = create_product('another_product')
    @one_more_product = create_product('one_more_product')
    @monitoring_product = create_product('monitoring')
  end

  it 'should allow owners to create subscriptions and retrieve all' do
      @cp.create_subscription(@owner.id, @some_product.id, 2)
      @cp.create_subscription(@owner.id, @another_product.id, 3)
      @cp.create_subscription(@owner.id, @one_more_product.id, 2)
      @cp.list_subscriptions(@owner.id).size.should == 3
  end

  it 'should allow owners to delete their subscriptions' do
      subs = @cp.create_subscription(@owner.id, @monitoring_product.id, 5)
      puts "deleting subscription #{subs.id}"
      @cp.delete_subscription(subs.id)
      @cp.list_subscriptions(@owner.id).size.should == 0
  end

end
