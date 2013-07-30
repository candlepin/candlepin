# A fixture for setting up common functoinality in virt
# tests. This fixture will create a virt limited product and
# will register 2 guests for use in testing.

module VirtFixture

  def self.included(base)
    base.class_eval do
      before(:each) do
        pending("candlepin running in standalone mode") if is_hosted?
        @owner = create_owner random_string('virt_owner')
        @user = user_client(@owner, random_string('virt_user'))

        # Create a sub for a virt limited product:
        @virt_limit_product = create_product(nil, nil, {
          :attributes => {
            'virt_limit' => 3,
            'stacking_id' => 'virtstack',
            'multi-entitlement' => 'yes'
          }
        })


        #create two subs, to do migration testing
        @first_created_sub = @cp.create_subscription(@owner['key'],
            @virt_limit_product.id, 10, [], "eldest-contract", "eldest-account", "eldest-order")
        @sub1 = @cp.create_subscription(@owner['key'],
          @virt_limit_product.id, 10, [], "123", "321", "333")
        @sub2 = @cp.create_subscription(@owner['key'],
          @virt_limit_product.id, 10, [], "456", '', '', nil, Date.today + 380)
        @cp.refresh_pools(@owner['key'])

        @pools = @user.list_pools :owner => @owner.id, \
          :product => @virt_limit_product.id
        @pools.size.should == 3
        @eldest_virt_pool = @pools.find_all {|p| p['contractNumber'] == "eldest-contract"}[0]
        @virt_limit_pool = @pools.find_all {|p| p['contractNumber'] == "123"}[0]

        # Setup two virt guest consumers:
        @uuid1 = random_string('system.uuid')
        @guest1 = @user.register(random_string('guest'), :system, nil,
          {'virt.uuid' => @uuid1, 'virt.is_guest' => 'true'}, nil, nil, [], [])
        @guest1_client = Candlepin.new(username=nil, password=nil,
            cert=@guest1['idCert']['cert'],
            key=@guest1['idCert']['key'])

        @uuid2 = random_string('system.uuid')
        @guest2 = @user.register(random_string('guest'), :system, nil,
          {'virt.uuid' => @uuid2, 'virt.is_guest' => 'true'}, nil, nil, [], [])
        @guest2_client = Candlepin.new(username=nil, password=nil,
            cert=@guest2['idCert']['cert'],
            key=@guest2['idCert']['key'])
      end
    end
  end

  def find_guest_virt_pool(guest_client, guest_uuid)
    pools = guest_client.list_pools :consumer => guest_uuid
    return pools.find_all { |i| !i['sourceStackId'].nil? }[0]
  end
end
