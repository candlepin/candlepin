require 'spec_helper'
require 'candlepin_scenarios'

describe 'Multiplier Products' do
  include CandlepinMethods

  before(:each) do
    @owner = create_owner random_string('some_owner')
    @user = user_client(@owner, random_string('guy'))
  end

  it 'should have the correct quantity' do
    calendaring = create_product('8723775392', 'Calendaring - 25 Pack', :multiplier => 25)
    @cp.create_pool(@owner['key'], calendaring.id, {:quantity => 4})

    pools = @user.list_pools :owner => @owner.id

    pools.length.should eq(1)
    pools.first.quantity.should == 100
  end

  it 'should default the multiplier to 1 if it is negative' do
    product = create_product('23049', 'Some Product', :multiplier => -10)
    @cp.create_pool(@owner['key'], product.id, {:quantity => 34})

    pools = @user.list_pools :owner => @owner.id

    pools.length.should eq(1)
    pools.first.quantity.should == 34
  end

  it 'should default the multiplier to 1 if it is zero' do
    product = create_product('9382533329', 'Some Other Product', :multiplier => 0)
    @cp.create_pool(@owner['key'], product.id, {:quantity => 18})

    pools = @user.list_pools :owner => @owner.id

    pools.length.should eq(1)
    pools.first.quantity.should == 18
  end

  it 'should have the correct quantity after a refresh' do
    product = create_product('875875844', 'Product - 100 Pack', :multiplier => 100)
    @cp.create_pool(@owner['key'], product.id, {:quantity => 5})

    # Now we refresh again to update the pool
    @cp.refresh_pools @owner['key']

    pools = @user.list_pools :owner => @owner.id

    pools.length.should eq(1)
    pools.first.quantity.should == 500
  end
end
