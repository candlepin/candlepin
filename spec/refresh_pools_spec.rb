require 'spec_helper'
require 'candlepin_scenarios'

require 'rubygems'
require 'rest_client'


describe 'Refresh Pools' do
  include CandlepinMethods
  include AttributeHelper
  include CertificateMethods
  include VirtHelper
  include CertificateMethods

  before(:each) do
    skip("candlepin running in standalone mode") unless is_hosted?
  end

  it 'deletes bonus pools when virt_limit attribute removed from master pool' do
    owner_key = random_string('test_owner')
    owner = create_owner(owner_key)

    content_id = random_string('test_content')
    content = create_upstream_content(content_id, { :content_url => 'http://www.url.com' })

    product_id = random_string(nil, true)
    product = create_upstream_product(product_id, {
        :name => 'test_prod',
        :attributes => {
            "virt_limit" => "unlimited",
            "host_limited" => "true" }
        })

    add_content_to_product_upstream(product_id, content_id)

    sub_id1 = random_string('test_subscription')
    create_upstream_subscription(sub_id1, owner_key, {
        :product => product,
        :quantity => 5
    })

    sub_id2 = random_string('test_subscription')
    create_upstream_subscription(sub_id2, owner_key, {
        :product => product,
        :quantity => 5
    })

    @cp.refresh_pools(owner_key)
    pools = @cp.list_pools({:owner => owner.id})
    expect(pools.length).to eq(4) # 2 master pools + 2 bonus pools

    # segregate pools based on type
    bonus_pools = []
    master_pools = []
    pools.each do |pool|
      if pool.type == "UNMAPPED_GUEST"
        bonus_pools.push(pool)
      else
        master_pools.push(pool)
      end
    end

    # Attach one guest to a bonus pool
    user1 = user_client(owner, random_string('test_user'))
    guest1_client = consumer_client(user1, random_string('test_consumer'), :system, nil,
                                    { 'system.certificate_version' => '3.0', 'virt.is_guest' => true})

    entitlements = guest1_client.consume_pool(bonus_pools[0].id, { :quantity => 1 })
    expect(entitlements.length).to eq(1)

    consumer = @cp.get_consumer(guest1_client.uuid)
    expect(consumer.entitlementCount).to eq(1)

    expect(bonus_pools[0].quantity).to eq(-1)
    expect(bonus_pools[1].quantity).to eq(-1)
    expect(master_pools[0].quantity).to eq(5)
    expect(master_pools[1].quantity).to eq(5)

    # Verify the entitlement count for the two bonus pools
    entitlements = @cp.list_pool_entitlements(bonus_pools[0].id)
    expect(entitlements.length).to eq(1)
    entitlements = @cp.list_pool_entitlements(bonus_pools[1].id)
    expect(entitlements.length).to eq(0)

    # Modify the subscription upstream (remove the virt_limit attribute) & do another refresh
    update_upstream_product(product_id, { :attributes => { "host_limited" => "true" } })

    @cp.refresh_pools(owner_key)

    # Verify that both bonus pools where deleted
    pools = @cp.list_pools({:owner => owner.id})
    expect(pools.length).to eq(2)
    expect(pools[0].type).to eq("NORMAL")
    expect(pools[1].type).to eq("NORMAL")

    # Check the the entitlement from the deleted bonus pool was revoked
    consumer = @cp.get_consumer(guest1_client.uuid)
    expect(consumer.entitlementCount).to eq(0)
  end

  it 'invalidates bonus pool entitlements when master pool quantity is reduced' do
    owner_key = random_string('test_owner')
    owner = create_owner(owner_key)

    content_id = random_string('test_content')
    content = create_upstream_content(content_id, { :content_url => 'http://www.url.com' })

    product_id = random_string(nil, true)
    product = create_upstream_product(product_id, { :name => 'test_prod', :attributes => { "virt_limit" => "1" } })

    add_content_to_product_upstream(product_id, content_id)

    sub_id = random_string('test_subscription')
    create_upstream_subscription(sub_id, owner_key, {:quantity => 5, :product => product})

    @cp.refresh_pools(owner_key)
    pools = @cp.list_pools({:owner => owner.id})
    expect(pools.length).to eq(2) # pool + bonus pool

    # Swap if the order isn't our expected order
    pools[0], pools[1] = pools[1], pools[0] if pools.first.type == "BONUS"

    expect(pools.first.quantity).to eq(5)
    expect(pools.last.quantity).to eq(5)

    # Verify the product exists in its initial state
    ds_product = @cp.get_product(owner_key, product_id)
    expect(ds_product).to_not be_nil
    expect(ds_product.name).to eq('test_prod')

    # Consume the pool multiple times so we have entitlements to revoke
    5.times do |i|
      user = user_client(owner, random_string('test_user'))
      consumer_client = consumer_client(user, random_string('test_consumer'), :system, nil,
        { 'system.certificate_version' => '3.0', 'virt.is_guest' => true})

      entitlements = consumer_client.consume_pool(pools.last.id, { :quantity => 1 })
      expect(entitlements.length).to eq(1)

      consumer = @cp.get_consumer(consumer_client.uuid)
      expect(consumer.entitlementCount).to eq(1)
    end

    # Verify the entitlement count for this pool
    entitlements = @cp.list_pool_entitlements(pools.first.id)
    expect(entitlements.length).to eq(0)
    entitlements = @cp.list_pool_entitlements(pools.last.id)
    expect(entitlements.length).to eq(5)

    # Modify the subscription upstream
    update_upstream_subscription(sub_id, {:quantity => 1, :product => product})

    @cp.refresh_pools(owner_key)
    pools = @cp.list_pools({:owner => owner.id})
    expect(pools.length).to eq(2) # pool + bonus pool

    # Swap if the order isn't our expected order
    pools[0], pools[1] = pools[1], pools[0] if pools.first.type == "BONUS"

    expect(pools.first.quantity).to eq(1)
    expect(pools.last.quantity).to eq(1)

    # Verify the entitlement count has changed
    entitlements = @cp.list_pool_entitlements(pools.first.id)
    expect(entitlements.length).to eq(0)
    entitlements = @cp.list_pool_entitlements(pools.last.id)
    expect(entitlements.length).to eq(1)
  end

  it 'invalidates bonus pool entitlements when bonus pool quantity is reduced' do
    owner_key = random_string('test_owner')
    owner = create_owner(owner_key)

    content_id = random_string('test_content')
    content = create_upstream_content(content_id, { :content_url => 'http://www.url.com' })

    product_id = random_string(nil, true)
    product = create_upstream_product(product_id, { :name => 'test_prod', :attributes => { "virt_limit" => "5" } })

    add_content_to_product_upstream(product_id, content_id)

    sub_id = random_string('test_subscription')
    create_upstream_subscription(sub_id, owner_key, {:quantity => 1, :product => product})

    @cp.refresh_pools(owner_key)
    pools = @cp.list_pools({:owner => owner.id})
    expect(pools.length).to eq(2) # pool + bonus pool

    # Swap if the order isn't our expected order
    pools[0], pools[1] = pools[1], pools[0] if pools.first.type == "BONUS"

    expect(pools.first.quantity).to eq(1)
    expect(pools.last.quantity).to eq(5)

    # Verify the product exists in its initial state
    ds_product = @cp.get_product(owner_key, product_id)
    expect(ds_product).to_not be_nil
    expect(ds_product.name).to eq('test_prod')

    # Consume the pool multiple times so we have entitlements to revoke
    5.times do |i|
      user = user_client(owner, random_string('test_user'))
      consumer_client = consumer_client(user, random_string('test_consumer'), :system, nil,
        { 'system.certificate_version' => '3.0', 'virt.is_guest' => true})

      entitlements = consumer_client.consume_pool(pools.last.id, { :quantity => 1 })
      expect(entitlements.length).to eq(1)

      consumer = @cp.get_consumer(consumer_client.uuid)
      expect(consumer.entitlementCount).to eq(1)
    end

    # Verify the entitlement count for this pool
    entitlements = @cp.list_pool_entitlements(pools.first.id)
    expect(entitlements.length).to eq(0)
    entitlements = @cp.list_pool_entitlements(pools.last.id)
    expect(entitlements.length).to eq(5)

    # Modify the subscription upstream
    update_upstream_product(product.id, { :attributes => { "virt_limit" => "1" } })

    @cp.refresh_pools(owner_key)
    pools = @cp.list_pools({:owner => owner.id})
    expect(pools.length).to eq(2) # pool + bonus pool

    # Swap if the order isn't our expected order
    pools[0], pools[1] = pools[1], pools[0] if pools.first.type == "BONUS"

    expect(pools.first.quantity).to eq(1)
    expect(pools.last.quantity).to eq(1)

    # Verify the entitlement count has changed
    entitlements = @cp.list_pool_entitlements(pools.first.id)
    expect(entitlements.length).to eq(0)
    entitlements = @cp.list_pool_entitlements(pools.last.id)
    expect(entitlements.length).to eq(1)
  end

  def concat_serials(normal_ent, bonus_ent)
    normal_serial = normal_ent['certificates'][0]['serial']['id']
    bonus_serial = bonus_ent['certificates'][0]['serial']['id']
    'normalEnt:' + normal_serial.to_s + '::bonusEnt:' + bonus_serial.to_s
  end

  def test_entitlement_regeneration
    owner_key = random_string('test_owner')
    owner = create_owner(owner_key)

    prov_product = create_upstream_product(random_string(nil, true), {
        :name => random_string('prov_prod', true),
        :attributes => {
            :version => '6.4',
            :arch => 'i386, x86_64',
            :sockets => 4,
            :cores => 8,
            :ram => 16,
            :warning_period => 15,
            :management_enabled => true,
            :stacking_id => '8888',
            :virt_only => 'false',
            :support_level => 'standard',
            :support_type => 'excellent'
        }
    })

    der_prov_product = create_upstream_product(random_string(nil, true), {
        :name => random_string('der_prov_prod', true),
        :attributes => {
            :version => '6.4',
            :arch => 'i386, x86_64',
            :sockets => 4,
            :cores => 8,
            :ram => 16,
            :warning_period => 15,
            :management_enabled => true,
            :stacking_id => '8888',
            :virt_only => 'false',
            :support_level => 'standard',
            :support_type => 'excellent'
        }
    })

    content_id1 = random_string('test_content_1')
    content1 = create_upstream_content(content_id1, {
        :gpg_url => 'gpg_url',
        :content_url => '/content/dist/rhel/$releasever/$basearch/os',
        :metadata_expire => 6400,
        :required_tags => 'TAG1,TAG2'
    })

    content_id2 = random_string('test_content_2')
    content2 = create_upstream_content(content_id2, {
        :gpg_url => 'gpg_url',
        :content_url => '/content/dist/rhel/$releasever/$basearch/os',
        :metadata_expire => 6400,
        :required_tags => 'TAG1,TAG2'
    })

    content_id3 = random_string('test_content_3')
    content3 = create_upstream_content(content_id3, {
        :gpg_url => 'gpg_url',
        :content_url => '/content/dist/rhel/$releasever/$basearch/os',
        :metadata_expire => 6400,
        :required_tags => 'TAG1,TAG2'
    })


    add_content_to_product_upstream(prov_product.id, content_id2, false)
    add_content_to_product_upstream(der_prov_product.id, content_id3, false)

    der_product = create_upstream_product(random_string(nil, true), {
      :name => random_string('der_prod', true),
      :attributes => {
        :cores => 2,
        :sockets => 4
      },
      :providedProducts => [der_prov_product]
    })

    product = create_upstream_product(random_string(nil, true), {
      :name => random_string('prod', true),
      :attributes => {
        :version => '6.4',
        :arch => 'i386, x86_64',
        :sockets => 4,
        :cores => 8,
        :ram => 16,
        :warning_period => 15,
        :management_enabled => true,
        :stacking_id => '8888',
        :virt_limit => "unlimited",
        :host_limited => "true",
        :virt_only => 'false',
        :support_level => 'standard',
        :support_type => 'excellent'
      },
      :derivedProduct => der_product,
      :providedProducts => [prov_product]
    })

    product = add_content_to_product_upstream(product.id, content_id1, false)

    sub_id = random_string('test_subscription_1')
    sub = create_upstream_subscription(sub_id, owner_key, {
      :quantity => 10,
      :contract_number => '12345',
      :account_number => '6789',
      :order_number => 'order1',
      :product => product
    })

    @cp.refresh_pools(owner_key)
    pools = @cp.list_pools({:owner => owner.id})
    expect(pools.length).to eq(2) # Expecting base pool + bonus pool

    pool = pools.select {|p| p['type'] != 'UNMAPPED_GUEST' }[0]
    bonus_pool = pools.select {|p| p['type'] == 'UNMAPPED_GUEST' }[0]


    # create an entitlement with a product and content
    user = user_client(owner, random_string('billy'))
    system = consumer_client(user, random_string('system1'), :system, nil, {
      'system.certificate_version' => '3.3',
      'uname.machine' => 'i386'
    })

    guest = consumer_client(user, 'virty', :system, nil, {
      'system.certificate_version' => '3.3',
      'virt.is_guest' => true
    })

    entitlement = system.consume_pool(pool['id'], {:quantity => 1})[0]
    bonus_entitlement = guest.consume_pool(bonus_pool['id'], {:quantity => 1})[0]

    json_body = extract_payload(entitlement['certificates'][0]['cert'])
    bonus_json_body = extract_payload(bonus_entitlement['certificates'][0]['cert'])

    serial_concat = concat_serials(entitlement, bonus_entitlement)

    # verify serial does not change on simple refresh
    @cp.refresh_pools(owner_key, false, false)
    entitlement = @cp.get_entitlement(entitlement['id'])
    bonus_entitlement = @cp.get_entitlement(bonus_entitlement['id'])

    expect(concat_serials(entitlement, bonus_entitlement)).to eq(serial_concat)

    # Yield to encapsulating test
    yield(owner, sub)

    # verify serial does not change on content update request that does not regenerate cert
    entitlement = @cp.get_entitlement(entitlement['id'])
    bonus_entitlement = @cp.get_entitlement(bonus_entitlement['id'])
    expect(concat_serials(entitlement, bonus_entitlement)).to eq(serial_concat)

    # this time when we refresh, serial should change
    @cp.refresh_pools(owner_key, false, false)
    entitlement = @cp.get_entitlement(entitlement['id'])
    bonus_entitlement = @cp.get_entitlement(bonus_entitlement['id'])
    expect(concat_serials(entitlement, bonus_entitlement)).to_not eq(serial_concat)
    json_body = extract_payload(entitlement['certificates'][0]['cert'])

    return json_body, product
  end

  it 'regenerates entitlements when modifiedProductIds of content change' do
    test_entitlement_regeneration { |owner, subscription|
      product_id2 = random_string(nil, true)
      product2 = create_upstream_product(product_id2, { :name => 'test_prod_2' })

      content = subscription['product']['productContent'].first['content']
      content.modifiedProductIds = [product_id2]
      update_upstream_content(content.id, content)
    }
  end

  it 'regenerates entitlements when modifiedProductIds of content of a provided product change' do
    test_entitlement_regeneration { |owner, subscription|
      product_id2 = random_string(nil, true)
      product2 = create_upstream_product(product_id2, { :name => 'test_prod_2' })

      content = subscription['product']['providedProducts'].first['productContent'].first['content']
      content.modifiedProductIds = [product_id2]
      update_upstream_content(content.id, content)
    }
  end

  it 'regenerates entitlements when modifiedProductIds of content of a derived provided product change' do
    test_entitlement_regeneration { |owner, subscription|
      product_id2 = random_string(nil, true)
      product2 = create_upstream_product(product_id2, { :name => 'test_prod_2' })

      content = subscription['product']['derivedProduct']['providedProducts'].first['productContent'].first['content']
      content.modifiedProductIds = [product_id2]
      update_upstream_content(content.id, content)
    }
  end

  it 'regenerates entitlements when provided product is added' do
    prov_product_id = random_string(nil, true)

    json_body, main_product = test_entitlement_regeneration { |owner, subscription|
      prov_product = create_upstream_product(prov_product_id, {
        :name => 'test_prov_prod',
        :version => '6.4',
        :arch => 'i386, x86_64',
        :sockets => 4,
        :cores => 8,
        :ram => 16,
        :warning_period => 15,
        :management_enabled => true,
        :stacking_id => '8888',
        :virt_only => 'false',
        :support_level => 'standard',
        :support_type => 'excellent'
      })

      content_id = random_string('test_content_')
      content = create_upstream_content(content_id, {
        :gpg_url => 'gpg_url',
        :content_url => '/content/dist/rhel/$releasever/$basearch/os',
        :metadata_expire => 6400,
        :required_tags => 'TAG1,TAG2'
      })

      add_content_to_product_upstream(prov_product.id, content_id)

      product = subscription['product']
      product['providedProducts'].push(prov_product)

      update_upstream_product(product.id, product)
    }

    prov_product = json_body['products'].find {|p| p['id'] == prov_product_id}
    expect(prov_product).to_not be_nil
  end

  it 'regenerates entitlements when provided product is removed' do
    json_body, main_product = test_entitlement_regeneration { |owner, subscription|
      product = subscription['product']
      product['providedProducts'] = []
      update_upstream_product(product.id, product)
    }

    json_body['products'].size.should == 1
  end

  it 'regenerates entitlements when label of a content changes' do
    json_body, main_product = test_entitlement_regeneration { |owner, subscription|
      content = subscription['product']['productContent'].first['content']
      content.label = 'shakeItOff'
      update_upstream_content(content.id, content)
    }

    product_json = json_body['products'].find {|p| p['id'] == main_product['id']}
    product_json['content'][0]['label'].should == 'shakeItOff'
  end

  it 'regenerates entitlements when releaseVer of a content changes' do
    test_entitlement_regeneration { |owner, subscription|
      content = subscription['product']['productContent'].first['content']
      content.releaseVer = 'badBlood'
      update_upstream_content(content.id, content)
    }

    # releasever is not in json
  end

  it 'regenerates entitlements when vendor of a content changes' do
    json_body, main_product = test_entitlement_regeneration { |owner, subscription|
      content = subscription['product']['productContent'].first['content']
      content.vendor = 'blankSpace'
      update_upstream_content(content.id, content)
    }

    product_json = json_body['products'].find {|p| p['id'] == main_product['id']}
    product_json['content'][0]['vendor'].should == 'blankSpace'
  end

  it 'regenerates entitlements when adding a content' do
    json_body, main_product = test_entitlement_regeneration { |owner, subscription|
      product = subscription['product']

      content = create_upstream_content("twentyTwo", {
        :type => "yum",
        :label => "teardropsOnMyGuitar",
        :name => "swiftrocks",
        :vendor => "fifteen",
        :releaseVer => nil
      })

      add_content_to_product_upstream(product.id, content.id)
    }

    product_json = json_body['products'].find {|p| p['id'] == main_product['id']}
    content = product_json['content'].find {|c| c['id'] == 'twentyTwo'}

    expect(content).to_not be_nil
    expect(content['name']).to eq('swiftrocks')
  end

  it 'regenerates entitlements when deleting content' do
    json_body, main_product = test_entitlement_regeneration { |owner, subscription|
      product = subscription['product']
      content = product['productContent'].first['content']

      remove_content_from_product_upstream(product.id, content.id)
    }

    product_json = json_body['products'].find {|p| p['id'] == main_product['id']}
    product_json['content'].should == []
  end

  it 'regenerates entitlements when pool start date changes' do
    owner_key = random_string('test_owner')
    owner = create_owner(owner_key)

    product = create_upstream_product(random_string('test_prod'))
    sub = create_upstream_subscription(random_string('test_sub'), owner_key, {
      :product => product,
      :start_date => Date.today - 20,
      :end_date => Date.today + 20
    })

    @cp.refresh_pools(owner_key)
    pools = @cp.list_pools({:owner => owner.id})
    expect(pools.length).to eq(1)

    user = user_client(owner, random_string('test_user'))
    consumer = consumer_client(user, random_string('test_consumer'))

    entitlements = consumer.consume_pool(pools.first['id'])
    expect(entitlements.length).to eq(1)

    consumer_entitlements = @cp.list_entitlements({:uuid => consumer.uuid})
    expect(consumer_entitlements.length).to eq(1)
    entitlement = consumer_entitlements.first

    # Update subscription, then refresh. The entitlements should be regenerated
    update_upstream_subscription(sub.id, { :start_date => Date.today - 10 })
    @cp.refresh_pools(owner_key)

    consumer_entitlements = @cp.list_entitlements({:uuid => consumer.uuid})
    expect(consumer_entitlements.length).to eq(1)
    new_entitlement = consumer_entitlements.first

    expect(new_entitlement['pool']['id']).to eq(entitlement['pool']['id'])
    expect(new_entitlement['certificates'].first['id']).to_not eq(entitlement['certificates'].first['id'])
  end

  it 'regenerates entitlements when pool end date changes' do
    owner_key = random_string('test_owner')
    owner = create_owner(owner_key)

    product = create_upstream_product(random_string('test_prod'))
    sub = create_upstream_subscription(random_string('test_sub'), owner_key, {
      :product => product,
      :start_date => Date.today - 20,
      :end_date => Date.today + 20
    })

    @cp.refresh_pools(owner_key)
    pools = @cp.list_pools({:owner => owner.id})
    expect(pools.length).to eq(1)

    user = user_client(owner, random_string('test_user'))
    consumer = consumer_client(user, random_string('test_consumer'))

    entitlements = consumer.consume_pool(pools.first['id'])
    expect(entitlements.length).to eq(1)

    consumer_entitlements = @cp.list_entitlements({:uuid => consumer.uuid})
    expect(consumer_entitlements.length).to eq(1)
    entitlement = consumer_entitlements.first

    # Update subscription, then refresh. The entitlements should be regenerated
    update_upstream_subscription(sub.id, { :end_date => Date.today + 10 })
    @cp.refresh_pools(owner_key)

    consumer_entitlements = @cp.list_entitlements({:uuid => consumer.uuid})
    new_entitlement = consumer_entitlements.first

    expect(new_entitlement['pool']['id']).to eq(entitlement['pool']['id'])
    expect(new_entitlement['certificates'].first['id']).to_not eq(entitlement['certificates'].first['id'])
  end

  it 'regenerates entitlements when pool product changes' do
    owner_key = random_string('test_owner')
    owner = create_owner(owner_key)

    product = create_upstream_product(random_string('test_prod'), { :attributes => { 'attrib' => 'value' }})
    sub = create_upstream_subscription(random_string('test_sub'), owner_key, { :product => product })

    @cp.refresh_pools(owner_key)
    pools = @cp.list_pools({:owner => owner.id})
    expect(pools.length).to eq(1)

    user = user_client(owner, random_string('test_user'))
    consumer = consumer_client(user, random_string('test_consumer'))

    entitlements = consumer.consume_pool(pools.first['id'])
    expect(entitlements.length).to eq(1)

    consumer_entitlements = @cp.list_entitlements({:uuid => consumer.uuid})
    expect(consumer_entitlements.length).to eq(1)
    entitlement = consumer_entitlements.first

    # Update product, then refresh. The entitlements should be regenerated
    update_upstream_product(product.id, { :attributes => { 'attrib' => 'new_value' }})
    @cp.refresh_pools(owner_key)

    consumer_entitlements = @cp.list_entitlements({:uuid => consumer.uuid})
    new_entitlement = consumer_entitlements.first

    expect(new_entitlement['pool']['id']).to eq(entitlement['pool']['id'])
    expect(new_entitlement['certificates'].first['id']).to_not eq(entitlement['certificates'].first['id'])
  end

  it 'regenerates entitlements when derived pools are removed' do
    # Testing for BZ 1567922:
    # - We have a main pool with a finite quantity, and also a virt_limit attribute on it
    # - A host consumes entitlement(s) from it; creating a derived pool as a result of that entitlement
    #   attachment
    # - The main pool is changed in one of two ways:
    #   a) the quantity of it is reduced, resulting in the host's entitlement being revoked (because now it
    #   would be overconsuming), and that would mean we need to delete the derived pool that was created
    #   because of it, or
    #   b) virt_limit is removed from the main pool's product (meaning it no longer should provide derived
    #   pools), which would mean we need to delete the derived pool
    # - That derived pool that was marked for deletion is attempted to be locked+updated later on, which
    #   results on some kind of error

    owner_key = random_string('test_owner')
    owner = create_owner(owner_key)

    derived_product = create_upstream_product(random_string('derived_prod'))

    product = create_upstream_product(random_string('host_prod'), {
      :derived_product => derived_product,
      :attributes => {
        'virt_limit' => 5
      }
    })

    sub = create_upstream_subscription(random_string('test_sub'), owner_key, {
      :product => product,
      :quantity => 2
    })

    @cp.refresh_pools(owner_key)
    pools = @cp.list_pools({:owner => owner.id})
    expect(pools.length).to eq(2)

    host_pool = pools.find {|pool| pool.type == 'NORMAL'}
    guest_pool = pools.find {|pool| pool.type == 'BONUS'}

    expect(host_pool).to_not be_nil
    expect(guest_pool).to_not be_nil
    expect(host_pool.id).to_not eq(guest_pool.id)


    guest_uuid = random_string('system.uuid')
    user = user_client(owner, random_string('test_user'))

    host = user.register(random_string('host'), :system)
    host_client = Candlepin.new(nil, nil, host['idCert']['cert'], host['idCert']['key'])

    guest = user.register(random_string('guest'), :system, nil,
      {'virt.uuid' => guest_uuid, 'virt.is_guest' => 'true', 'uname.machine' => 'x86_64'}, nil, nil, [], [])
    guest_client = Candlepin.new(nil, nil, guest['idCert']['cert'], guest['idCert']['key'])

    host_client.update_guestids([{'guestId' => guest_uuid}])

    entitlements = host_client.consume_pool(host_pool.id)
    expect(entitlements.length).to eq(1)
    host_entitlement = entitlements.first

    entitlements = guest_client.consume_pool(guest_pool.id)
    expect(entitlements.length).to eq(1)
    guest_entitlement = entitlements.first


    # Update main sub product to no longer have a derived product
    update_upstream_product(product.id, {
      :derived_product => nil,
      :attributes => {}
    })

    @cp.refresh_pools(owner_key)
    pools = @cp.list_pools({:owner => owner.id})
    expect(pools.length).to eq(1)
    expect(pools.find {|pool| pool.type == 'BONUS'}).to be_nil

    entitlements = @cp.list_entitlements({:uuid => host.uuid})
    expect(entitlements.length).to eq(1)

    entitlements = @cp.list_entitlements({:uuid => guest.uuid})
    expect(entitlements.length).to eq(0)
  end

  it 'deduplicates products and content' do
    # Create some orgs
    owners = []
    3.times do |i|
      key = random_string("test_owner-#{i}")
      owners << create_owner(key)
    end

    # Create products and content to be shared across all orgs
    provided = []
    3.times do |i|
      content = create_upstream_content(random_string("prov_content-#{i}"), {'name' => "prov content #{i}"})
      prov_product = create_upstream_product(random_string("provided-#{i}"))
      add_content_to_product_upstream(prov_product['id'], content['id'])

      provided << prov_product
    end

    product = create_upstream_product(random_string('test_prod'), :providedProducts => provided)

    # Set up orgs with different subscriptions containing the product
    owners.each do |owner|
      create_upstream_subscription(random_string('test_sub'), owner['key'], {
        :product => product
      })
    end

    # Refresh orgs in serial (note: this *cannot* safely be done in parallel)
    owners.each do |owner|
      @cp.refresh_pools(owner['key'])
    end

    # Verify that the products used by both org is the same underlying product (i.e. same product UUID)
    product_uuids = []
    owners.each do |owner|
      pools = @cp.list_pools({:owner => owner['id']})
      expect(pools).to_not be_nil
      expect(pools.size).to eq(1)

      product = @cp.get_product(owner['key'], pools.first['productId'])

      expect(product).to_not be_nil
      expect(product).to have_key('uuid')
      expect(product['uuid']).to_not be_nil

      product_uuids << product['uuid']
    end

    expect(product_uuids.size).to eq(owners.size)
    expect(product_uuids).to all(eq(product_uuids.first))
  end
end