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

  it 'creates a valid job' do
    owner = create_owner random_string('test_owner')

    status = @cp.refresh_pools(owner['key'], true)

    expect(status).to_not be_nil
    expect(status['state']).to eq('CREATED')

    # Wait for the job to finish (should be quick)
    wait_for_job(status['id'])

    # Cleanup the job now that it's finished
    @cp.cleanup_jobs({:id => status['id']})
  end

  it 'contains the proper return value' do
    test_owner = random_string('test_owner')
    owner = create_owner test_owner
    result = @cp.refresh_pools(owner['key'])

    if !is_hosted?
      expect(result).to be_nil
    else
      expect(result).to eq("Pools refreshed for owner: #{test_owner}")
    end
  end

  it 'creates the correct number of pools' do
    owner = create_owner random_string('some-owner')

    # Create 6 subscriptions to different products
    6.times do |i|
      product = create_upstream_product(random_string("product-#{i}"))
      create_upstream_subscription(random_string("sub-#{i}"), owner['key'], {:product => product})
    end

    @cp.refresh_pools(owner['key'])
    @cp.list_pools({:owner => owner.id}).length.should == 6
  end

  it 'detects changes in provided products' do
    owner_key = random_string('test_owner')
    owner = create_owner(owner_key)

    provided = []

    3.times do |i|
      provided << create_upstream_product(random_string("provided-#{i}"))
    end

    product = create_upstream_product(random_string('test_prod'), :providedProducts => provided[0..1])
    sub = create_upstream_subscription(random_string('test_sub'), owner_key, {
      :product => product
    })

    @cp.refresh_pools(owner_key)
    pools = @cp.list_pools({:owner => owner.id})
    expect(pools.length).to eq(1)
    expect(pools[0].providedProducts.length).to eq(2)

    provided_ids = provided[0..1].collect { |p| p.id }
    pools[0].providedProducts.each do |p|
      expect(provided_ids).to include(p.productId)
      provided_ids.delete(p.productId)
    end

    # Remove the old provided products and add a new one...
    product.providedProducts = [provided[2]]
    update_upstream_product(product.id, :provided_products => provided[2])
    update_upstream_subscription(sub.id, {
        :product => product
    })


    @cp.refresh_pools(owner_key)
    pools = @cp.list_pools({:owner => owner.id})
    expect(pools.length).to eq(1)
    expect(pools[0].providedProducts.length).to eq(1)
    expect(pools[0].providedProducts[0].productId).to eq(provided[2].id)
  end

  it 'detects changes in provided products with products hierarchy' do
    owner_key = random_string('test_owner')
    owner = create_owner(owner_key)

    provided = []

    3.times do |i|
      provided << create_upstream_product(random_string("provided-#{i}"))
    end
    product = create_upstream_product(random_string('test_prod'), :providedProducts => provided[0..1])
    sub = create_upstream_subscription(random_string('test_sub'), owner_key, {
        :product => product
    })
    @cp.refresh_pools(owner_key)
    pools = @cp.list_pools({:owner => owner.id})

    expect(pools.length).to eq(1)
    expect(pools[0].providedProducts.length).to eq(2)

    provided_ids = provided[0..1].collect { |p| p.id }
    pools[0].providedProducts.each do |p|
      expect(provided_ids).to include(p.productId)
      provided_ids.delete(p.productId)
    end

    # Remove the old provided products and add a new one...
    update_upstream_product(product.id, :provided_products => provided[2])
    product.providedProducts = [provided[2]]
    update_upstream_subscription(sub.id, {
        :product => product
    })

    @cp.refresh_pools(owner_key)
    pools = @cp.list_pools({:owner => owner.id})

    expect(pools.length).to eq(1)
    expect(pools[0].providedProducts.length).to eq(1)
    expect(pools[0].providedProducts[0].productId).to eq(provided[2].id)
  end

  it 'detects changes in branding' do
    owner_key = random_string('test_owner')
    owner = create_owner(owner_key)

    b1 = {:productId => 'prodid1',
      :type => 'type1', :name => 'branding1'}
    b2 = {:productId => 'prodid2',
      :type => 'type2', :name => 'branding2'}
    product = create_upstream_product(random_string('test_prod'), { :branding => [b1] })

    sub = create_upstream_subscription(random_string('test_sub'), owner_key, {:product => product})

    @cp.refresh_pools(owner_key)

    # Check the branding is visible on the pool.
    pools = @cp.list_pools({:owner => owner.id})

    expect(pools.length).to eq(1)
    expect(pools[0].branding.length).to eq(1)
    expect(pools[0].branding[0].name).to eq('branding1')

    # Check the branding set is visible on the product.
    products = @cp.list_products_by_owner(owner_key)
    expect(products[0].branding.length).to eq(1)
    expect(products[0].branding[0].name).to eq('branding1')

    # Add an additional branding to the upstream product than the one we had initially...
    product.branding = [b1, b2]
    update_upstream_product(product.id, :branding => product.branding)

    @cp.refresh_pools(owner_key)

    # Check the updated branding is visible on the pool response.
    pools = @cp.list_pools({:owner => owner.id})

    expect(pools.length).to eq(1)
    expect(pools[0].branding.length).to eq(2)
    expect('branding2').to eq(pools[0].branding[0].name).or(eq(pools[0].branding[1].name))

    # Check the updated branding set is visible on the product.
    products = @cp.list_products_by_owner(owner_key)
    expect(products[0].branding.length).to eq(2)
    expect('branding2').to eq(products[0].branding[0].name).or(eq(products[0].branding[1].name))
  end

  it 'deletes expired subscriptions\' pools and entitlements' do
    owner_key = random_string('test_owner')
    owner = create_owner(owner_key)

    product = create_upstream_product(random_string('test_prod'))
    sub = create_upstream_subscription(random_string('test_sub'), owner_key, { :quantity => 500,
      :product => product })

    @cp.refresh_pools(owner_key)
    pools = @cp.list_pools({:owner => owner.id})
    expect(pools.length).to eq(1)

    user = user_client(owner, random_string('test_user'))
    consumer = consumer_client(user, random_string('test_consumer'))
    entitlements = consumer.consume_pool(pools.first.id, {:quantity => 1})
    expect(entitlements.length).to eq(1)

    consumer = @cp.get_consumer(consumer.uuid)
    expect(consumer.entitlementCount).to eq(1)

    # Update subscription such that it's expired, then refresh. The entitlements should be removed.
    update_upstream_subscription(sub.id, {
      :start_date => Date.today - 20,
      :end_date => Date.today - 10
    })
    @cp.refresh_pools(owner_key)

    pools = @cp.list_pools({:owner => owner.id})
    consumer = @cp.get_consumer(consumer.uuid)

    expect(pools.length).to eq(0)
    expect(consumer.entitlementCount).to eq(0)
  end

  it 'regenerates entitlements' do
    owner_key = random_string('test_owner')
    owner = create_owner(owner_key)

    product1 = create_upstream_product(random_string('test_prod1'))
    product2 = create_upstream_product(random_string('test_prod2'))
    sub = create_upstream_subscription(random_string('test_sub'), owner_key, {:product => product1})

    @cp.refresh_pools(owner_key)
    pools = @cp.list_pools({:owner => owner.id})
    expect(pools.length).to eq(1)

    user = user_client(owner, random_string('test_user'))
    consumer = consumer_client(user, random_string('test_consumer'))
    entitlements = consumer.consume_pool(pools.first.id, {:quantity => 1})
    expect(entitlements.length).to eq(1)

    consumer = @cp.get_consumer(consumer.uuid)
    expect(consumer.entitlementCount).to eq(1)

    entitlement = entitlements.first
    old_cert = entitlement['certificates'].first
    old_serial = old_cert['serial']['serial']

    # Update the subscription's product to trigger an entitlement regeneration
    update_upstream_subscription(sub.id, {
      :product => { :id => product2.id }
    })

    @cp.refresh_pools(owner_key)

    consumer = @cp.get_consumer(consumer.uuid)
    expect(consumer.entitlementCount).to eq(1)

    updated_ent = @cp.get_entitlement(entitlement['id'])
    new_cert = updated_ent['certificates'].first
    new_serial = new_cert['serial']['serial']

    expect(new_serial).to_not eq(old_serial)
  end

  it 'handle derived products being removed' do
    # 998317: is caused by refresh pools dying with an NPE
    # this happens when subscriptions no longer have
    # derived products resulting in a null during the refresh
    # which we didn't handle in all cases.
    owner_key = random_string('test_owner')
    owner = create_owner(owner_key)

    datacenter_product = create_upstream_product(random_string('dc_prod'), {
      :attributes => {
        :virt_limit => "unlimited",
        :stacking_id => "stackme",
        :sockets => "2",
        'multi-entitlement' => "yes"
      }
    })

    derived_eng_product = create_upstream_product(random_string(nil, true))
    derived_product = create_upstream_product(random_string('derived_prod'), {
      :attributes => {
        :cores => 2,
        :sockets=>4
      }, :providedProducts => [derived_eng_product]
    })

    eng_product = create_upstream_product(random_string('eng_prod'))

    sub = create_upstream_subscription(random_string('dc_sub'), owner_key, {
      :quantity => 10,
      :derived_product => derived_product,
      :product => datacenter_product
    })

    @cp.refresh_pools(owner_key)
    pools = @cp.list_pools({:owner => owner.id})
    expect(pools.length).to eq(2) # We're expecting the base pool + a virt-only bonus pool for guests

    # Swap pools if necessary
    if pools[0].productId != datacenter_product.id
      pools[0], pools[1] = pools[1], pools[0]
    end

    expect(pools.first).to have_key('derivedProvidedProducts')
    expect(pools.first.derivedProvidedProducts.length).to eq(1)

    update_upstream_subscription(sub.id, {
        :product => datacenter_product,
        :derived_product => nil
    })

    @cp.refresh_pools(owner_key)

    pools = @cp.list_pools :owner => owner.id, :product => datacenter_product.id
    expect(pools.length).to eq(2)

    # Swap pools if necessary
    if pools[0].productId != datacenter_product.id
      pools[0], pools[1] = pools[1], pools[0]
    end

    expect(pools.first).to have_key('derivedProvidedProducts')
    expect(pools.first.derivedProvidedProducts.length).to eq(0)
  end

  it 'can migrate subscriptions' do
    owner_key1 = random_string('test_owner_1')
    owner_key2 = random_string('test_owner_2')
    owner1 = create_owner(owner_key1)
    owner2 = create_owner(owner_key2)

    product = create_upstream_product(random_string('test_prod'))
    sub = create_upstream_subscription(random_string('test_sub'), owner_key1, {:product=>product})

    @cp.refresh_pools(owner_key1)
    @cp.refresh_pools(owner_key2)

    pools1 = @cp.list_pools({:owner => owner1.id})
    pools2 = @cp.list_pools({:owner => owner2.id})
    expect(pools1.length).to eq(1)
    expect(pools2.length).to eq(0)

    # Update sub to be owned by the second owner
    update_upstream_subscription(sub.id, {:owner => { :key => owner_key2 }, :product => product})

    @cp.refresh_pools(owner_key1)
    @cp.refresh_pools(owner_key2)

    pools1 = @cp.list_pools({:owner => owner1.id})
    pools2 = @cp.list_pools({:owner => owner2.id})
    expect(pools1.length).to eq(0)
    expect(pools2.length).to eq(1)
  end

  it 'removes pools from other owners when subscription is migrated' do
    owner_key1 = random_string('test_owner_1')
    owner_key2 = random_string('test_owner_2')
    owner1 = create_owner(owner_key1)
    owner2 = create_owner(owner_key2)

    product = create_upstream_product(random_string('test_prod'))
    sub = create_upstream_subscription(random_string('test_sub'), owner_key1, {:product => product})

    @cp.refresh_pools(owner_key1)
    @cp.refresh_pools(owner_key2)

    pools1 = @cp.list_pools({:owner => owner1.id})
    pools2 = @cp.list_pools({:owner => owner2.id})
    expect(pools1.length).to eq(1)
    expect(pools2.length).to eq(0)

    # Update sub to be owned by the second owner
    update_upstream_subscription(sub.id, { :owner => { :key => owner_key2 }, :product => product})

    @cp.refresh_pools(owner_key2)

    pools1 = @cp.list_pools({:owner => owner1.id})
    pools2 = @cp.list_pools({:owner => owner2.id})
    expect(pools1.length).to eq(0)
    expect(pools2.length).to eq(1)
  end

  # Testing bug #1150234:
  it 'can change attributes and revoke entitlements at same time' do
    owner_key = random_string('test_owner')
    owner = create_owner(owner_key)

    product = create_upstream_product(random_string('multient_prod'), {
      :attributes => {
        'multi-entitlement' => 'yes'
      }
    })

    sub = create_upstream_subscription(random_string('multient_sub'), owner_key, {
      :quantity => 2,
      :product => product
    })

    user = user_client(owner, random_string('test_user'))
    consumer_client = consumer_client(user, random_string('test_consumer'))

    @cp.refresh_pools(owner_key)
    pools = @cp.list_pools({:owner => owner.id})
    expect(pools.length).to eq(1)

    # We'll consume quantity 2, later we will reduce the pool to 1 forcing revokation of this entitlement
    entitlements = consumer_client.consume_pool(pools.first.id, { :quantity => 2 })
    expect(entitlements.length).to eq(1)

    # FIXME: This seems like a bug. Our entitlement count here should be 1, right?
    consumer = @cp.get_consumer(consumer_client.uuid)
    expect(consumer.entitlementCount).to eq(2)

    # Add a new attribute to the product
    product = update_upstream_product(product.id, {
      :attributes => {
        'new_attrib' => 'new value',
        'multi-entitlement' => 'yes'
      }
    })
    # ...and reduce the quantity available on the subscription
    update_upstream_subscription(sub.id, {
      :quantity => 1,
      :product => product
    })

    # Refresh pools for this org
    @cp.refresh_pools(owner_key)

    pools = @cp.list_pools({:owner => owner.id})
    expect(pools.length).to eq(1)

    # Verify the pool's product now contains the new attribute
    expect(pools.first).to have_key('productAttributes')

    attributes = normalize_attributes(pools.first.productAttributes)
    expect(attributes).to eq({
      'new_attrib' => 'new value',
      'multi-entitlement' => 'yes'
    })

    # Verify that the entitlement was revoked
    consumer = @cp.get_consumer(consumer_client.uuid)
    expect(consumer.entitlementCount).to eq(0)

    lambda do
      @cp.get_entitlement(entitlements[0].id)
    end.should raise_exception(RestClient::ResourceNotFound)
  end

  it 'regenerates entitlements when content for an entitled pool changes' do
    owner_key = random_string('test_owner')
    owner = create_owner(owner_key)

    content_id = random_string('test_content')
    content = create_upstream_content(content_id, { :label => 'test_label', :content_url => 'http://www.url.com' })

    product_id = random_string(nil, true)
    product = create_upstream_product(product_id)

    add_content_to_product_upstream(product_id, content_id)

    sub_id = random_string('test_subscription')
    create_upstream_subscription(sub_id, owner_key, {:product => product})

    @cp.refresh_pools(owner_key)
    pools = @cp.list_pools({:owner => owner.id})
    expect(pools.length).to eq(1)

    # Verify the content exists in its initial state
    ds_content = @cp.get_content(owner_key, content_id)
    expect(ds_content).to_not be_nil
    expect(ds_content.label).to eq('test_label')

    # Consume the pool so we have an entitlement
    user = user_client(owner, random_string('test_user'))
    consumer_client = consumer_client(user, random_string('test_consumer'), :system, nil,
      { 'system.certificate_version' => '3.0' })

    entitlements = consumer_client.consume_pool(pools.first.id, { :quantity => 1 })
    expect(entitlements.length).to eq(1)

    consumer = @cp.get_consumer(consumer_client.uuid)
    expect(consumer.entitlementCount).to eq(1)

    entitlement = entitlements.first
    ent_cert = entitlement['certificates'].first
    ent_cert_serial = ent_cert['serial']['serial']

    # Modify the content for this product/sub
    update_upstream_content(content_id, { :label => 'updated_label' })

    @cp.refresh_pools(owner_key)
    pools = @cp.list_pools({:owner => owner.id})
    expect(pools.length).to eq(1)

    # Verify the content change has been pulled down
    ds_content = @cp.get_content(owner_key, content_id)
    expect(ds_content).to_not be_nil
    expect(ds_content.label).to eq('updated_label')

    # Verify the entitlement cert has changed as a result
    updated_ent = @cp.get_entitlement(entitlement['id'])
    updated_cert = updated_ent['certificates'].first
    updated_cert_serial = updated_cert['serial']['serial']

    expect(updated_cert_serial).to_not eq(ent_cert_serial)
  end

  it 'regenerates entitlements when products for an entitled pool changes' do
    owner_key = random_string('test_owner')
    owner = create_owner(owner_key)

    content_id = random_string('test_content')
    content = create_upstream_content(content_id, { :content_url => 'http://www.url.com' })

    product_id = random_string(nil, true)
    product = create_upstream_product(product_id, { :name => 'test_prod' })

    add_content_to_product_upstream(product_id, content_id)

    sub_id = random_string('test_subscription')
    create_upstream_subscription(sub_id, owner_key, {:product => product})

    @cp.refresh_pools(owner_key)
    pools = @cp.list_pools({:owner => owner.id})
    expect(pools.length).to eq(1)

    # Verify the product exists in its initial state
    ds_product = @cp.get_product(owner_key, product_id)
    expect(ds_product).to_not be_nil
    expect(ds_product.name).to eq('test_prod')

    # Consume the pool so we have an entitlement
    user = user_client(owner, random_string('test_user'))
    consumer_client = consumer_client(user, random_string('test_consumer'), :system, nil,
      { 'system.certificate_version' => '3.0' })

    entitlements = consumer_client.consume_pool(pools.first.id, { :quantity => 1 })
    expect(entitlements.length).to eq(1)

    consumer = @cp.get_consumer(consumer_client.uuid)
    expect(consumer.entitlementCount).to eq(1)

    entitlement = entitlements.first
    ent_cert = entitlement['certificates'].first
    ent_cert_serial = ent_cert['serial']['serial']

    # Modify the product for this sub
    update_upstream_product(product_id, { :name => 'updated_name' })

    @cp.refresh_pools(owner_key)
    pools = @cp.list_pools({:owner => owner.id})
    expect(pools.length).to eq(1)

    # Verify the product change has been pulled down
    ds_product = @cp.get_product(owner_key, product_id)
    expect(ds_product).to_not be_nil
    expect(ds_product.name).to eq('updated_name')

    # Verify the entitlement cert has changed as a result
    updated_ent = @cp.get_entitlement(entitlement['id'])
    updated_cert = updated_ent['certificates'].first
    updated_cert_serial = updated_cert['serial']['serial']

    expect(updated_cert_serial).to_not eq(ent_cert_serial)
  end

  it 'regenerates entitlements when required products change' do
    owner_key = random_string('test_owner')
    owner = create_owner(owner_key)

    eng_id1 = random_string(nil, true)
    eng_id2 = random_string(nil, true)
    eng_prod1 = create_upstream_product(eng_id1)
    eng_prod2 = create_upstream_product(eng_id2)

    content_id1 = random_string('test_content_1')
    content1 = create_upstream_content(content_id1, { :content_url => 'http://www.url.com/c1' })

    content_id2 = random_string('test_content_2')
    content2 = create_upstream_content(content_id2, { :content_url => 'http://www.url.com/c2' })

    content_id3 = random_string('test_content_3')
    content3 = create_upstream_content(content_id3, { :content_url => 'http://www.url.com/c3' })

    add_content_to_product_upstream(eng_id1, content_id1)
    add_content_to_product_upstream(eng_id2, content_id2)
    add_content_to_product_upstream(eng_id2, content_id3)

    sku_id1 = random_string('required_prod', true)
    sku_id2 = random_string('dependent_prod', true)
    sku_prod1 = create_upstream_product(sku_id1, {:providedProducts => [eng_prod1]})
    sku_prod2 = create_upstream_product(sku_id2, {:providedProducts => [eng_prod2]} )
    sub_id1 = random_string('test_subscription_1')
    sub1 = create_upstream_subscription(sub_id1, owner_key, {:product => sku_prod1})
    sub_id2 = random_string('test_subscription_2')
    sub2 = create_upstream_subscription(sub_id2, owner_key, {:product => sku_prod2 })

    @cp.refresh_pools(owner_key)
    pools = @cp.list_pools({:owner => owner.id})
    expect(pools.length).to eq(2)

    # Rearrange the pools if they're backward
    if pools.first.productId == sku_id2
      pools[0], pools[1] = pools[1], pools[0]
    end

    products = @cp.list_products_by_owner(owner_key)
    expect(products.length).to eq(4)

    content = @cp.list_content(owner_key)
    expect(content.length).to eq(3)

    # Consume both pools
    user = user_client(owner, random_string('test_user'))
    consumer_client = consumer_client(user, random_string('test_consumer'), :system, nil,
      { 'system.certificate_version' => '3.0' })

    pool_ents = []

    entitlements = consumer_client.consume_pool(pools[0].id, { :quantity => 1 })
    expect(entitlements.length).to eq(1)
    pool_ents << entitlements.first

    entitlements = consumer_client.consume_pool(pools[1].id, { :quantity => 1 })
    expect(entitlements.length).to eq(1)
    pool_ents << entitlements.first

    entitlements = @cp.list_entitlements({ :uuid => consumer_client.uuid })
    expect(entitlements.length).to eq(2)

    payload1 = extract_payload(pool_ents[0].certificates.first['cert'])
    payload2 = extract_payload(pool_ents[1].certificates.first['cert'])

    # Verify the entitlements contains the products and content
    expect(payload1).to have_key('products')
    expect(payload1.products.length).to eq(1)
    expect(payload1.products.first.id).to eq(eng_id1)
    expect(payload1.products.first).to have_key('content')
    expect(payload1.products.first.content.length).to eq(1)
    expect(payload1.products.first.content.first.id).to eq(content1.id)
    expect(payload1.products.first.content.first.path).to eq(content1.contentUrl)

    expect(payload2).to have_key('products')
    expect(payload2.products.length).to eq(1)
    expect(payload2.products.first).to have_key('content')
    expect(payload2.products.first.id).to eq(eng_id2)
    expect(payload2.products.first.content.length).to eq(2)

    if payload2.products.first.content[0].id == content2.id
      expect(payload2.products.first.content[0].id).to eq(content2.id)
      expect(payload2.products.first.content[0].path).to eq(content2.contentUrl)
      expect(payload2.products.first.content[1].id).to eq(content3.id)
      expect(payload2.products.first.content[1].path).to eq(content3.contentUrl)
    else
      expect(payload2.products.first.content[0].id).to eq(content3.id)
      expect(payload2.products.first.content[0].path).to eq(content3.contentUrl)
      expect(payload2.products.first.content[1].id).to eq(content2.id)
      expect(payload2.products.first.content[1].path).to eq(content2.contentUrl)
    end

    ent_cert = pool_ents[1]['certificates'].first
    ent_cert_serial = ent_cert['serial']['serial']

    # Add a dependent product to content2 for a product the consumer is entitled to
    update_upstream_content(content_id2, { :modified_product_ids => [eng_id1] })
    @cp.refresh_pools(owner_key)

    # Verify the content change has been pulled down
    content = @cp.get_content(owner_key, content_id2)
    expect(content.modifiedProductIds).to eq([eng_id1])

    # Verify the entitlement has been regenerated
    updated_ent = @cp.get_entitlement(pool_ents[1]['id'])
    updated_cert = updated_ent['certificates'].first
    updated_cert_serial = updated_cert['serial']['serial']

    expect(updated_cert_serial).to_not eq(ent_cert_serial)

    # Verify the content path is still present in the entitlement
    payload = extract_payload(updated_ent['certificates'].first['cert'])

    expect(payload).to have_key('products')
    expect(payload.products.length).to eq(1)
    expect(payload.products.first).to have_key('content')
    expect(payload.products.first.id).to eq(eng_id2)
    expect(payload.products.first.content.length).to eq(2)

    if payload.products.first.content[0].id == content2.id
      expect(payload.products.first.content[0].id).to eq(content2.id)
      expect(payload.products.first.content[0].path).to eq(content2.contentUrl)
      expect(payload.products.first.content[1].id).to eq(content3.id)
      expect(payload.products.first.content[1].path).to eq(content3.contentUrl)
    else
      expect(payload.products.first.content[0].id).to eq(content3.id)
      expect(payload.products.first.content[0].path).to eq(content3.contentUrl)
      expect(payload.products.first.content[1].id).to eq(content2.id)
      expect(payload.products.first.content[1].path).to eq(content2.contentUrl)
    end

    # Add a dependent product to content3 for a product the consumer is NOT entitled to
    update_upstream_content(content_id3, { :modified_product_ids => ['fake_pid'] })
    @cp.refresh_pools(owner_key)

    # Verify the content change has been pulled down
    content = @cp.get_content(owner_key, content_id3)
    expect(content.modifiedProductIds).to eq(['fake_pid'])

    # Verify the entitlement has been regenerated
    updated_ent = @cp.get_entitlement(pool_ents[1]['id'])
    updated_cert = updated_ent['certificates'].first
    updated_cert_serial = updated_cert['serial']['serial']

    expect(updated_cert_serial).to_not eq(ent_cert_serial)

    # Verify the content path is still present in the entitlement
    payload = extract_payload(updated_ent['certificates'].first['cert'])

    expect(payload).to have_key('products')
    expect(payload.products.length).to eq(1)
    expect(payload.products.first).to have_key('content')
    expect(payload.products.first.id).to eq(eng_id2)
    expect(payload.products.first.content.length).to eq(1)
    expect(payload.products.first.content.first.id).to eq(content2.id)
    expect(payload.products.first.content.first.path).to eq(content2.contentUrl)
  end

  it 'regenerates entitlements when branding for a product of an entitled pool changes' do
    owner_key = random_string('test_owner')
    owner = create_owner(owner_key)

    b1 = {:productId => 'prodid1',
      :type => 'type1', :name => 'branding1'}
    product = create_upstream_product(random_string('test_prod'), { :branding => [b1] })

    sub = create_upstream_subscription(random_string('test_sub'), owner_key, {:product => product})

    @cp.refresh_pools(owner_key)
    pools = @cp.list_pools({:owner => owner.id})
    expect(pools.length).to eq(1)

    # Verify the product exists in its initial state
    ds_product = @cp.get_product(owner_key, product.id)
    expect(ds_product).to_not be_nil
    expect(ds_product.branding.length).to eq(1)
    expect(ds_product.branding[0].name).to eq('branding1')

    # Consume the pool so we have an entitlement
    user = user_client(owner, random_string('test_user'))
    consumer_client = consumer_client(user, random_string('test_consumer'), :system, nil,
      { 'system.certificate_version' => '3.0' })

    entitlements = consumer_client.consume_pool(pools.first.id, { :quantity => 1 })
    expect(entitlements.length).to eq(1)

    consumer = @cp.get_consumer(consumer_client.uuid)
    expect(consumer.entitlementCount).to eq(1)

    entitlement = entitlements.first
    ent_cert = entitlement['certificates'].first
    ent_cert_serial = ent_cert['serial']['serial']

    # Update the name of the branding of the upstream product...
    product.branding[0].name = 'new branding name!'
    update_upstream_product(product.id, :branding => product.branding)

    @cp.refresh_pools(owner_key)
    pools = @cp.list_pools({:owner => owner.id})
    expect(pools.length).to eq(1)

    # Verify the branding change on the product has been pulled down
    ds_product = @cp.get_product(owner_key, product.id)
    expect(ds_product).to_not be_nil
    expect(ds_product.branding.length).to eq(1)
    expect(ds_product.branding[0].name).to eq('new branding name!')

    # Verify the entitlement cert has changed as a result
    updated_ent = @cp.get_entitlement(entitlement['id'])
    updated_cert = updated_ent['certificates'].first
    updated_cert_serial = updated_cert['serial']['serial']

    expect(updated_cert_serial).to_not eq(ent_cert_serial)
  end

  it 'invalidates entitlements when pool quantity is reduced' do
    owner_key = random_string('test_owner')
    owner = create_owner(owner_key)

    content_id = random_string('test_content')
    content = create_upstream_content(content_id, { :content_url => 'http://www.url.com' })

    product_id = random_string(nil, true)
    product = create_upstream_product(product_id, { :name => 'test_prod' })

    add_content_to_product_upstream(product_id, content_id)

    sub_id = random_string('test_subscription')
    create_upstream_subscription(sub_id, owner_key, {:quantity => 5, :product => product})

    @cp.refresh_pools(owner_key)
    pools = @cp.list_pools({:owner => owner.id})
    expect(pools.length).to eq(1)
    expect(pools.first.quantity).to eq(5)

    # Verify the product exists in its initial state
    ds_product = @cp.get_product(owner_key, product_id)
    expect(ds_product).to_not be_nil
    expect(ds_product.name).to eq('test_prod')

    # Consume the pool multiple times so we have entitlements to revoke
    5.times do |i|
      user = user_client(owner, random_string('test_user'))
      consumer_client = consumer_client(user, random_string('test_consumer'), :system, nil,
        { 'system.certificate_version' => '3.0' })

      entitlements = consumer_client.consume_pool(pools.first.id, { :quantity => 1 })
      expect(entitlements.length).to eq(1)

      consumer = @cp.get_consumer(consumer_client.uuid)
      expect(consumer.entitlementCount).to eq(1)
    end

    # Verify the entitlement count for this pool
    entitlements = @cp.list_pool_entitlements(pools.first.id)
    expect(entitlements.length).to eq(5)

    # Modify the subscription upstream
    update_upstream_subscription(sub_id, {:quantity => 1, :product => product})

    @cp.refresh_pools(owner_key)
    pools = @cp.list_pools({:owner => owner.id})
    expect(pools.length).to eq(1)
    expect(pools.first.quantity).to eq(1)

    # Verify the entitlement count has changed
    entitlements = @cp.list_pool_entitlements(pools.first.id)
    expect(entitlements.length).to eq(1)
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
      }, :providedProducts => [prov_product]
    })

    add_content_to_product_upstream(product.id, content_id1, false)

    der_product = create_upstream_product(random_string(nil, true), {
      :name => random_string('der_prod', true),
      :attributes => {
        :cores => 2,
        :sockets => 4
      }, :providedProducts => [der_prov_product]
    })

    sub_id = random_string('test_subscription_1')
    sub = create_upstream_subscription(sub_id, owner_key, {
      :quantity => 10,
      :contract_number => '12345',
      :account_number => '6789',
      :order_number => 'order1',
      :product => product,
      :derived_product => der_product,
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
    @cp.refresh_pools(owner_key, false, false, false)
    entitlement =  @cp.get_entitlement(entitlement['id'])
    bonus_entitlement =  @cp.get_entitlement(bonus_entitlement['id'])

    concat_serials(entitlement, bonus_entitlement).should == serial_concat

    # Yield to encapsulating test, applying any change it may have made
    sub = yield(owner, sub)
    update_upstream_subscription(sub.id, sub)

    # verify serial does not change on content update request that does not regenerate cert
    entitlement =  @cp.get_entitlement(entitlement['id'])
    bonus_entitlement =  @cp.get_entitlement(bonus_entitlement['id'])
    concat_serials(entitlement, bonus_entitlement).should == serial_concat

    # this time when we refresh, serial should change
    @cp.refresh_pools(owner_key, false, false, false)
    entitlement =  @cp.get_entitlement(entitlement['id'])
    bonus_entitlement =  @cp.get_entitlement(bonus_entitlement['id'])
    concat_serials(entitlement, bonus_entitlement).should_not == serial_concat
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

      subscription
    }
  end

  it 'regenerates entitlements when modifiedProductIds of content of a provided product change' do
    test_entitlement_regeneration { |owner, subscription|
      product_id2 = random_string(nil, true)
      product2 = create_upstream_product(product_id2, { :name => 'test_prod_2' })

      content = subscription['providedProducts'][0]['productContent'][0]['content']
      content.modifiedProductIds = [product_id2]
      update_upstream_content(content.id, content)

      subscription
    }
  end

  it 'regenerates entitlements when modifiedProductIds of content of a derived provided product change' do
    test_entitlement_regeneration { |owner, subscription|
      product_id2 = random_string(nil, true)
      product2 = create_upstream_product(product_id2, { :name => 'test_prod_2' })

      content = subscription['derivedProvidedProducts'][0]['productContent'][0]['content']
      content.modifiedProductIds = [product_id2]
      update_upstream_content(content.id, content)

      subscription
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

      subscription['product']['providedProducts'].push(prov_product)

      subscription
    }

    prov_product = json_body['products'].find {|p| p['id'] == prov_product_id}
    expect(prov_product).to_not be_nil
  end

  it 'regenerates entitlements when provided product is removed' do
    json_body, main_product = test_entitlement_regeneration { |owner, subscription|
      subscription['product']['providedProducts'] = []
      subscription
    }

    json_body['products'].size.should == 1
  end

  it 'regenerates entitlements when label of a content changes' do
    json_body, main_product = test_entitlement_regeneration { |owner, subscription|
      content = subscription['product']['productContent'].first['content']
      content.label = 'shakeItOff'
      update_upstream_content(content.id, content)

      subscription
    }

    product_json = json_body['products'].find {|p| p['id'] == main_product['id']}
    product_json['content'][0]['label'].should == 'shakeItOff'
  end

  it 'regenerates entitlements when releaseVer of a content changes' do
    test_entitlement_regeneration { |owner, subscription|
      content = subscription['product']['productContent'].first['content']
      content.releaseVer = 'badBlood'
      update_upstream_content(content.id, content)

      subscription
    }

    # releasever is not in json
  end

  it 'regenerates entitlements when vendor of a content changes' do
    json_body, main_product = test_entitlement_regeneration { |owner, subscription|
      content = subscription['product']['productContent'].first['content']
      content.vendor = 'blankSpace'
      update_upstream_content(content.id, content)

      subscription
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

      # Need to return the updated, upstream subscription here so we don't risk clobbering the addition.
      # We shouldn't, anyway, but there's no need to risk it unnecessarily.
      get_upstream_subscription(subscription.id)
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

      # Need to return the updated, upstream subscription here so we don't risk clobbering the removal.
      # We shouldn't, anyway, but there's no need to risk it unnecessarily.
      get_upstream_subscription(subscription.id)
    }

    product_json = json_body['products'].find {|p| p['id'] == main_product['id']}
    product_json['content'].should == []
  end
end
