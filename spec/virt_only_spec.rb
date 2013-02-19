require 'candlepin_scenarios'

describe 'Virt Only Pools' do
  include CandlepinMethods
  include CandlepinScenarios

  before(:each) do
    @owner = create_owner random_string('virt_owner')
    @user = user_client(@owner, random_string('virt_user'))
  end

  it 'should allow virt guests to consume from virt_only pools' do
    virt_product = new_product true
    guest = consumer_client(@user, 'virty', :system, nil, {
      'virt.is_guest' => true
    })

    entitlement = guest.consume_product(virt_product.id)
    entitlement.first.quantity.should == 1
  end

  it 'should allow virt guests to consume from physical pools' do
    physical_product = new_product false
    guest = consumer_client(@user, 'virty', :system, nil, {
      'virt.is_guest' => true
    })

    entitlement = guest.consume_product(physical_product.id)
    entitlement.first.quantity.should == 1
  end

  it 'should deny physical consumers from virt_only pools' do
    virt_product = new_product true
    guest = consumer_client(@user, 'metal', :system, nil, {
      'virt.is_guest' => false
    })

    entitlement = guest.consume_product(virt_product.id)
    entitlement.should be_nil
  end

  it 'virt_only product should result in virt_only pool' do
    virt_product = new_product true
    guest = consumer_client(@user, 'virty', :system, nil, {
      'virt.is_guest' => true
    })

    entitlement = guest.consume_product(virt_product.id)
    @cp.get_pool(entitlement.first.pool.id).attributes.each do |att|
      found = false
      if att.name == 'virt_only'
        att.value.should == 'true'
        found = true
      end
      found.should == true
    end
  end

  it 'should allow virt_only pools to be exported for manifest consumers' do
    virt_product = new_product true
    manifest = consumer_client(@user, 'virty', :candlepin, nil, {
      'virt.is_guest' => false
    })

    entitlement = manifest.consume_product(virt_product.id)
    pools = @cp.list_pools({:consumer => manifest.uuid})
    pools.size.should == 1
    virtonly = pools[0]['attributes'].find_all {|i| i['name'] == 'virt_only'}[0]
    virtonly['value'].should == 'true'

  end

  private

  def new_product(virt_only)
    product = create_product(nil, nil, {
      :attributes => {
        :virt_only => virt_only
      }
    })

    @cp.create_subscription(@owner['key'], product.id, 10)
    @cp.refresh_pools(@owner['key'])

    product
  end
end
