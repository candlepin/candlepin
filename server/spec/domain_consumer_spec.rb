require 'spec_helper'
require 'candlepin_scenarios'

describe 'Domain Consumer' do
  include CandlepinMethods

  before(:each) do
    @owner = create_owner(random_string("test_owner"))
    @user = user_client(@owner, random_string("test_user"))
    @monitoring = create_product()
    @domain_product = create_product(nil, random_string("test_product"), {
        :attributes => { :requires_consumer_type => :domain }
    })

    @cp.create_pool(@owner['key'], @monitoring.id, {:quantity => 4})
    @cp.create_pool(@owner['key'], @domain_product.id, {:quantity => 4})
  end

  it 'should not be able to consume non domain specific products' do
    consumer = consumer_client(@user, 'guest_consumer', :domain)

    entitlements = consumer.consume_product @monitoring.id
    entitlements.should == []
  end

  it 'should be able to consume domain products' do
    consumer = consumer_client(@user, 'other_consumer', :domain)

    entitlements = consumer.consume_product @domain_product.id
    entitlements.length.should eq(1)
  end

  it 'should ONLY be able to consume domain products' do
    system = consumer_client(@user, 'non-domain')

    entitlements = system.consume_product(@domain_product.id)
    entitlements.should == []
  end

end

