require 'spec_helper'
require 'candlepin_scenarios'

describe 'Instance Based Subscriptions' do
  include CandlepinMethods
  include AttributeHelper

  before(:each) do
    @owner = create_owner random_string('instance_owner')
    @user = user_client(@owner, random_string('virt_user'))

    if is_hosted?
      @eng_product = create_upstream_product()
    else
      @eng_product = create_product()
    end
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

    # create product and instance based subscription:
    if is_hosted?
      @instance_product = create_upstream_product(nil, {
        :attributes => {
          :instance_multiplier => "2",
          :virt_limit => "1",
          :stacking_id => "stackme",
          :sockets => "2",
          :host_limited => "true",
          'multi-entitlement' => "yes"
        },
        :providedProducts => [@eng_product]
      })

      create_upstream_subscription(random_string('instance_sub'), @owner['key'],
      { :quantity => 10, :product => @instance_product })

      @cp.refresh_pools(@owner['key'])
    else
      @instance_product = create_product(nil, nil, {
        :attributes => {
          :instance_multiplier => "2",
          :virt_limit => "1",
          :stacking_id => "stackme",
          :sockets => "2",
          :host_limited => "true",
          'multi-entitlement' => "yes"
        },
        :providedProducts => [@eng_product.id]
      })

      # In standalone, the pool needs source subscription and upstream pool id
      # in order for the subpool to be created
       @cp.create_pool(@owner['key'], @instance_product.id, { :quantity => 10,
          :subscription_id => random_str('source_sub'),
          :upstream_pool_id => random_str('upstream')})
    end

    @pools = @cp.list_pools :owner => @owner.id, \
      :product => @instance_product.id
    @pools.size.should == 2
    instance_pools = @pools.reject do |p|
      has_attribute(p['attributes'], 'unmapped_guests_only')
    end
    instance_pools.size.should == 1
    # In hosted, we increase the quantity on the subscription. However in standalone,
    # we assume this already has happened in hosted and the accurate quantity was
    # exported
    @instance_pool = instance_pools.first
    if is_hosted?
      @instance_pool.quantity.should == 20
    else
      @instance_pool.quantity.should == 10
    end
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
