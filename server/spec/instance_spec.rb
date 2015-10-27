require 'spec_helper'
require 'candlepin_scenarios'

describe 'Instance Based Subscriptions' do
  include CandlepinMethods

  before(:each) do
    @owner = create_owner random_string('instance_owner')
    @user = user_client(@owner, random_string('virt_user'))

    #create_product() creates products with numeric IDs by default
    @eng_product = create_product()
    installed_prods = [{'productId' => @eng_product['id'],
      'productName' => @eng_product['name']}]

    # For linking the host and the guest:
    @uuid = random_string('system.uuid')

    @physical_sys = @user.register(random_string('host'), :system, nil,
      {"cpu.cpu_socket(s)" => 8}, nil, nil, [], installed_prods, nil)
    @physical_client = Candlepin.new(nil, nil, @physical_sys['idCert']['cert'], @physical_sys['idCert']['key'])
    @physical_client.update_consumer({:guestIds => [{'guestId' => @uuid}]})

    @guest1 = @user.register(random_string('guest'), :system, nil,
      {'virt.uuid' => @uuid, 'virt.is_guest' => 'true'}, nil, nil,
      [], installed_prods)
    @guest_client = Candlepin.new(nil, nil, @guest1['idCert']['cert'], @guest1['idCert']['key'])
    # create instance based subscription:
    @instance_product = create_product(nil, nil, {
      :attributes => {
        :instance_multiplier => "2",
        :virt_limit => "1",
        :stacking_id => "stackme",
        :sockets => "2",
        :host_limited => "true",
        'multi-entitlement' => "yes"
      }
    })

    create_pool_and_subscription(@owner['key'], @instance_product.id,
      10, [@eng_product['id']])
    @pools = @cp.list_pools :owner => @owner.id, \
      :product => @instance_product.id
    @pools.size.should == 2
    instance_pools = @pools.reject do |p|
      p['attributes'].any? { |attr| attr['name'] == 'unmapped_guests_only' }
    end
    instance_pools.size.should == 1
    @instance_pool = instance_pools.first
    @instance_pool.quantity.should == 20
  end

  it 'should auto-subscribe physical systems with quantity 2 per socket pair' do
    @guest_client.list_pools({:consumer => @guest_client.uuid}).size.should == 1

    @physical_client.consume_product
    ents = @physical_client.list_entitlements
    ents.size.should == 1

    ents[0].quantity.should == 8

    # Guest should now see additional sub-pool:
    @guest_client.list_pools({:consumer => @guest_client.uuid}).size.should == 2
  end

  it 'should auto-subscribe guest systems with quantity 1' do
    @guest_client.consume_product
    ents = @guest_client.list_entitlements
    ents.size.should == 1
    ents[0].quantity.should == 1
  end

end
