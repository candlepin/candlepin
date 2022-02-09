require 'spec_helper'
require 'candlepin_scenarios'
include CertificateMethods

describe 'Conditional Content and Dependent Entitlements' do

  include CandlepinMethods

  before(:each) do
    @owner = create_owner(random_string('conditional_content_owner'))

    # Three provided products, will be bundled in different ways:
    @required_product_1 = create_product()
    @required_product_2 = create_product()
    @required_product_3 = create_product()

    # This bundled product contains all of the provided products our conditional content will require
    @bundled_product_1 = create_product(nil, nil,
      {:providedProducts => [@required_product_1.id, @required_product_2.id, @required_product_3.id]})
    @bundled_pool_1 = @cp.create_pool(@owner['key'], @bundled_product_1.id, { :quantity => 10 })

    # This bundled product only contains two of the provided products our conditional content
    # will require
    @bundled_product_2 = create_product(nil, nil,
      {:providedProducts => [@required_product_1.id, @required_product_2.id]})
    @bundled_pool_2 = @cp.create_pool(@owner['key'], @bundled_product_2.id, { :quantity => 10 })

    # Create our dependent provided product, which carries content sets -- each of which of which
    # requires one of the provided products above
    @dependent_provided_product = create_product()
    @conditional_content_1 = create_content({:modified_products => [@required_product_1.id]})
    @conditional_content_2 = create_content({:modified_products => [@required_product_2.id]})
    @conditional_content_3 = create_content({:modified_products => [@required_product_3.id]})
    @cp.add_content_to_product(@owner['key'], @dependent_provided_product.id, @conditional_content_1.id)
    @cp.add_content_to_product(@owner['key'], @dependent_provided_product.id, @conditional_content_2.id)
    @cp.add_content_to_product(@owner['key'], @dependent_provided_product.id, @conditional_content_3.id)

    # Create a dependent pool, providing only the product containing our conditional content
    @dependent_product = create_product(nil, nil, {:providedProducts => [@dependent_provided_product.id]})
    @dependent_pool = @cp.create_pool(@owner['key'], @dependent_product.id, { :quantity => 10 })

    owner_client = user_client(@owner, random_string('testowner'))
    @consumer_cp = consumer_client(owner_client, random_string('consumer123'))
  end

  it 'includes conditional content sets' do
    # Bind to the normal subscription first:
    @consumer_cp.consume_product(@bundled_product_1.id)

    # Bind to the dependent subscription which requires the product(s) provided by the previously
    # bound subscription:
    ent = @consumer_cp.consume_product(@dependent_product.id)

    # dependent certificate should now contain the conditional content:
    dependent_cert = entitlement_cert(ent)
    content_repo_type(dependent_cert, @conditional_content_1.id).should == 'yum'
    content_repo_type(dependent_cert, @conditional_content_2.id).should == 'yum'
    content_repo_type(dependent_cert, @conditional_content_3.id).should == 'yum'
  end

  it 'includes conditional content sets selectively' do
    # Bind to the normal subscription first:
    @consumer_cp.consume_product(@bundled_product_2.id)

    # Bind to the dependent subscription which requires the product(s) provided by the previously
    # bound subscription:
    ent = @consumer_cp.consume_product(@dependent_product.id)

    # dependent certificate should now contain some of the conditional content:
    dependent_cert = entitlement_cert(ent)
    content_repo_type(dependent_cert, @conditional_content_1.id).should == 'yum'
    content_repo_type(dependent_cert, @conditional_content_2.id).should == 'yum'
    content_repo_type(dependent_cert, @conditional_content_3.id).should be_nil
  end

  it 'does not include conditional content without the required products' do
    # Bind to the dependent subscription without being entitled to any of the required products
    ent = @consumer_cp.consume_product(@dependent_product.id)

    # Resulting dependent cert should not contain any of the conditional content
    dependent_cert = entitlement_cert(ent)
    content_repo_type(dependent_cert, @conditional_content_1.id).should be_nil
    content_repo_type(dependent_cert, @conditional_content_2.id).should be_nil
    content_repo_type(dependent_cert, @conditional_content_3.id).should be_nil
  end

  it 'is regenerated when consumer receives access to a required product' do
    # Bind to the dependent subscription without being entitled to any of the required products
    ent = @consumer_cp.consume_product(@dependent_product.id)

    # Resulting dependent cert should not contain any of the conditional content sets:
    dependent_cert = entitlement_cert(ent)
    expect(content_repo_type(dependent_cert, @conditional_content_1.id)).to be_nil

    # Bind to the required product...
    entitlements = @consumer_cp.consume_product(@bundled_product_1.id)
    expect(entitlements).to_not be_nil
    expect(entitlements.length).to be > 0
    normal_serial = entitlements[0]['certificates'][0]['serial']['serial']

    # Old certificate should be gone:
    certs = @consumer_cp.list_certificates()
    expect(certs.length).to eq(2)
    old_cert = certs.find_index{ |c| c.serial.serial == dependent_cert.serial.to_i}
    expect(old_cert).to be_nil

    # There should be a new certificate in its place
    new_cert_index = certs.find_index{ |c| c.serial.serial != normal_serial}
    expect(new_cert_index).to_not be_nil

    # And it should have the conditional content set:
    new_cert = OpenSSL::X509::Certificate.new(certs[new_cert_index]['cert'])
    expect(content_repo_type(new_cert, @conditional_content_1.id)).to eq('yum')
    expect(content_repo_type(new_cert, @conditional_content_2.id)).to eq('yum')
    expect(content_repo_type(new_cert, @conditional_content_3.id)).to eq('yum')
  end

  it 'is regenerated when the consumer loses access to required products' do
    # Bind to the "modifier" subscription
    entitlement_ids = []

    entitlements = @consumer_cp.consume_product(@dependent_product.id)
    expect(entitlements).to_not be_nil
    expect(entitlements.length).to eq(1)
    entitlement = entitlements.first

    # Verify that we don't have any content repos yet...
    dependent_cert = entitlement_cert(entitlement)
    expect(content_repo_type(dependent_cert, @conditional_content_1.id)).to be_nil
    expect(content_repo_type(dependent_cert, @conditional_content_2.id)).to be_nil
    expect(content_repo_type(dependent_cert, @conditional_content_3.id)).to be_nil

    # Bind to a normal subscription...
    entitlements = @consumer_cp.consume_product(@bundled_product_2.id)
    expect(entitlements).to_not be_nil
    expect(entitlements.length).to eq(1)
    entitlement_ids << entitlements.first['id']

    # Re-fetch the modifier entitlement...
    entitlement = @consumer_cp.get_entitlement(entitlement['id'])
    expect(entitlement).to_not be_nil

    # Modifier certificate should now contain some conditional content...
    dependent_cert = entitlement_cert(entitlement)
    expect(content_repo_type(dependent_cert, @conditional_content_1.id)).to eq('yum')
    expect(content_repo_type(dependent_cert, @conditional_content_2.id)).to eq('yum')
    expect(content_repo_type(dependent_cert, @conditional_content_3.id)).to be_nil

    # Bind to another normal subscription...
    entitlements = @consumer_cp.consume_product(@bundled_product_1.id)
    expect(entitlements).to_not be_nil
    expect(entitlements.length).to eq(1)
    entitlement_ids << entitlements.first['id']

    # Re-fetch the modifier entitlement...
    entitlement = @consumer_cp.get_entitlement(entitlement['id'])
    expect(entitlement).to_not be_nil

    # Modifier certificate should now contain all conditional content...
    dependent_cert = entitlement_cert(entitlement)
    expect(content_repo_type(dependent_cert, @conditional_content_1.id)).to eq('yum')
    expect(content_repo_type(dependent_cert, @conditional_content_2.id)).to eq('yum')
    expect(content_repo_type(dependent_cert, @conditional_content_3.id)).to eq('yum')

    # Unbind the pools to revoke our entitlements...
    entitlement_ids.each do |eid|
        @consumer_cp.unbind_entitlement(eid)
    end

    # Re-fetch the modifier entitlement...
    entitlement = @consumer_cp.get_entitlement(entitlement['id'])
    expect(entitlement).to_not be_nil

    # Verify that we don't have any content repos anymore
    dependent_cert = entitlement_cert(entitlement)
    expect(content_repo_type(dependent_cert, @conditional_content_1.id)).to be_nil
    expect(content_repo_type(dependent_cert, @conditional_content_2.id)).to be_nil
    expect(content_repo_type(dependent_cert, @conditional_content_3.id)).to be_nil
  end

  it 'is regenerated when the required product subscriptions disappear' do
    # Bind to the "modifier" subscription
    entitlement_ids = []

    entitlements = @consumer_cp.consume_product(@dependent_product.id)
    expect(entitlements).to_not be_nil
    expect(entitlements.length).to eq(1)
    entitlement = entitlements.first

    # Verify that we don't have any content repos yet...
    dependent_cert = entitlement_cert(entitlement)
    expect(content_repo_type(dependent_cert, @conditional_content_1.id)).to be_nil
    expect(content_repo_type(dependent_cert, @conditional_content_2.id)).to be_nil
    expect(content_repo_type(dependent_cert, @conditional_content_3.id)).to be_nil

    # Bind to a normal subscription...
    entitlements = @consumer_cp.consume_product(@bundled_product_2.id)
    expect(entitlements).to_not be_nil
    expect(entitlements.length).to eq(1)
    entitlement_ids << entitlements.first['id']

    # Re-fetch the modifier entitlement...
    entitlement = @consumer_cp.get_entitlement(entitlement['id'])
    expect(entitlement).to_not be_nil

    # Modifier certificate should now contain some conditional content...
    dependent_cert = entitlement_cert(entitlement)
    expect(content_repo_type(dependent_cert, @conditional_content_1.id)).to eq('yum')
    expect(content_repo_type(dependent_cert, @conditional_content_2.id)).to eq('yum')
    expect(content_repo_type(dependent_cert, @conditional_content_3.id)).to be_nil

    # Bind to another normal subscription...
    entitlements = @consumer_cp.consume_product(@bundled_product_1.id)
    expect(entitlements).to_not be_nil
    expect(entitlements.length).to eq(1)
    entitlement_ids << entitlements.first['id']

    # Re-fetch the modifier entitlement...
    entitlement = @consumer_cp.get_entitlement(entitlement['id'])
    expect(entitlement).to_not be_nil

    # Modifier certificate should now contain all conditional content...
    dependent_cert = entitlement_cert(entitlement)
    expect(content_repo_type(dependent_cert, @conditional_content_1.id)).to eq('yum')
    expect(content_repo_type(dependent_cert, @conditional_content_2.id)).to eq('yum')
    expect(content_repo_type(dependent_cert, @conditional_content_3.id)).to eq('yum')

    # Unbind the pools to revoke our entitlements...
    @cp.delete_pool(@bundled_pool_1.id)
    @cp.delete_pool(@bundled_pool_2.id)

    # Re-fetch the modifier entitlement...
    entitlement = @consumer_cp.get_entitlement(entitlement['id'])
    expect(entitlement).to_not be_nil

    # Verify that we don't have any content repos anymore
    dependent_cert = entitlement_cert(entitlement)
    expect(content_repo_type(dependent_cert, @conditional_content_1.id)).to be_nil
    expect(content_repo_type(dependent_cert, @conditional_content_2.id)).to be_nil
    expect(content_repo_type(dependent_cert, @conditional_content_3.id)).to be_nil
  end

  it 'v3 cert includes conditional content after auto attach that also entitles the required product' do
    owner = create_owner random_string('owner')
    user = user_client(owner, random_string('user'))

    rh00271_eng_product = create_product("204", "Red Hat Enterprise Linux Server - Extended Life Cycle Support", {:owner => owner['key']})
    rh00271_product = create_product("RH00271", "Extended Life Cycle Support (Unlimited Guests)", {
      :owner => owner['key'],
      :providedProducts => [rh00271_eng_product.id],
      :multiplier => 1,
      :attributes => {
        :stacking_id => "RH00271"
      }
    })

    rh00051_eng_product = create_product("69", "Red Hat Enterprise Linux Server", {:owner => owner['key']})
    rh00051_product = create_product("RH00051", "Red Hat Enterprise Linux for Virtual Datacenters with Smart Management, Standard", {
      :owner => owner['key'],
      :providedProducts => [rh00051_eng_product.id],
      :multiplier => 1,
      :attributes => {
        :stacking_id => "RH00051"
      }
    })

    rh00051_content = @cp.create_content(
      owner['key'], "cname-c1", 'test-content-c1', random_string("clabel"), "ctype", "cvendor",
      {:content_url=> '/this/is/the/path'}, true)

    # Content that has a required/modified product 'rh00051_eng_product' (this eng product needs to be entitled to the
    # consumer already, or otherwise this content will get filtered out during entitlement cert generation)
    rh00271_content = @cp.create_content(
      owner['key'], "cname-c2", 'test-content-c2', random_string("clabel"), "ctype", "cvendor",
      {:content_url=> '/this/is/the/path', :modified_products => [rh00051_eng_product["id"]]}, true)

    @cp.add_content_to_product(owner['key'], rh00051_eng_product['id'], rh00051_content['id'], true)
    @cp.add_content_to_product(owner['key'], rh00271_eng_product['id'], rh00271_content['id'], true)

    # creating master pool for the RH00051 product
    rh00051_pool = @cp.create_pool(owner['key'], rh00051_product['id'], {:quantity => 10,
      :subscription_id => "random",
      :upstream_pool_id => "random",
      :provided_products => [rh00051_eng_product.id],
      :locked => "true"})

    #creating master pool for the RH00271 product
    rh00271_pool = @cp.create_pool(owner['key'], rh00271_product['id'], {:quantity => 10,
      :subscription_id => "random",
      :upstream_pool_id => "random",
      :provided_products => [rh00271_eng_product.id],
      :locked => "true"})

    # Create system with V3 certificate version
    installed_prods = [{'productId' => rh00271_eng_product['id'], 'productName' => rh00271_eng_product['name']},
      {'productId' => rh00051_eng_product['id'], 'productName' => rh00051_eng_product['name']}]
    system1 = user.register(random_string('system55'), :system, nil, {"system.certificate_version" => "3.2" },
       nil, owner['key'], [], installed_prods)
    system1_client = Candlepin.new(nil, nil, system1['idCert']['cert'], system1['idCert']['key'])

    # Auto-attach the system
    ents = system1_client.consume_product
    expect(ents.length).to eq(2)

    # Verify each entitlement cert contains the appropriate content set
    rh00271_cert_json_body = nil
    rh00051_cert_json_body = nil
    ents.each { |ent|
      if ent.pool.id == rh00051_pool.id
        rh00051_cert_json_body = extract_payload(ent['certificates'][0]['cert'])
      end

      if ent.pool.id == rh00271_pool.id
        rh00271_cert_json_body = extract_payload(ent['certificates'][0]['cert'])
      end
    }
    expect(rh00051_cert_json_body).to_not be_nil
    expect(rh00271_cert_json_body).to_not be_nil

    expect(rh00051_cert_json_body['products'][0]['content'].size).to eq(1)
    expect(rh00051_cert_json_body['products'][0]['content'][0].id).to eq(rh00051_content.id)

    # rh00271_content (which depends on modified product id 69) should not have been filtered out, because the
    # engineering product 69 should already be covered by entitlement rh00051:
    expect(rh00271_cert_json_body['products'][0]['content'].size).to eq(1)
    expect(rh00271_cert_json_body['products'][0]['content'][0].id).to eq(rh00271_content.id)
  end

  it 'v1 cert includes conditional content after auto attach that also entitles the required product' do
    owner = create_owner random_string('owner')
    user = user_client(owner, random_string('user'))

    rh00271_eng_product = create_product("204", "Red Hat Enterprise Linux Server - Extended Life Cycle Support", {:owner => owner['key']})
    rh00271_product = create_product("RH00271", "Extended Life Cycle Support (Unlimited Guests)", {
      :owner => owner['key'],
      :providedProducts => [rh00271_eng_product.id],
      :multiplier => 1,
      :attributes => {
        :stacking_id => "RH00271"
      }
    })

    rh00051_eng_product = create_product("69", "Red Hat Enterprise Linux Server", {:owner => owner['key']})
    rh00051_product = create_product("RH00051", "Red Hat Enterprise Linux for Virtual Datacenters with Smart Management, Standard", {
      :owner => owner['key'],
      :providedProducts => [rh00051_eng_product.id],
      :multiplier => 1,
      :attributes => {
        :stacking_id => "RH00051"
      }
    })

    # Note: for v1 certificates, we only support certain types of content type, like 'yum', so we must set the type
    # to yum here, and also only numeric ids
    rh00051_content = @cp.create_content(
      owner['key'], "cname-c1", '111555', random_string("clabel"), "yum", "cvendor",
      {:content_url=> '/this/is/the/path'}, true)

    # Content that has a required/modified product 'rh00051_eng_product' (this eng product needs to be entitled to the
    # consumer already, or otherwise this content will get filtered out during entitlement cert generation)
    rh00271_content = @cp.create_content(
      owner['key'], "cname-c2", '222333', random_string("clabel"), "yum", "cvendor",
      {:content_url=> '/this/is/the/path', :modified_products => [rh00051_eng_product["id"]]}, true)

    @cp.add_content_to_product(owner['key'], rh00051_eng_product['id'], rh00051_content['id'], true)
    @cp.add_content_to_product(owner['key'], rh00271_eng_product['id'], rh00271_content['id'], true)

    # creating master pool for the RH00051 product
    rh00051_pool = @cp.create_pool(owner['key'], rh00051_product['id'], {:quantity => 10,
      :subscription_id => "random",
      :upstream_pool_id => "random",
      :provided_products => [rh00051_eng_product.id],
      :locked => "true"})

    #creating master pool for the RH00271 product
    rh00271_pool = @cp.create_pool(owner['key'], rh00271_product['id'], {:quantity => 10,
      :subscription_id => "random",
      :upstream_pool_id => "random",
      :provided_products => [rh00271_eng_product.id],
      :locked => "true"})

    # creating system with V1 certificate version
    installed_prods = [{'productId' => rh00271_eng_product['id'], 'productName' => rh00271_eng_product['name']},
      {'productId' => rh00051_eng_product['id'], 'productName' => rh00051_eng_product['name']}]
    system1 = user.register(random_string('system55'), :system, nil, {"system.certificate_version" => "1.0" },
       nil, owner['key'], [], installed_prods)
    system1_client = Candlepin.new(nil, nil, system1['idCert']['cert'], system1['idCert']['key'])

    # Auto-attach the system
    ents = system1_client.consume_product
    expect(ents.length).to eq(2)

    # Verify each entitlement cert contains the appropriate content set
    rh00271_cert = nil
    rh00051_cert = nil
    ents.each { |ent|
      if ent.pool.id == rh00051_pool.id
        rh00051_cert = entitlement_cert(ent)
      end

      if ent.pool.id == rh00271_pool.id
        rh00271_cert = entitlement_cert(ent)
      end
    }
    expect(rh00271_cert).to_not be_nil
    expect(rh00051_cert).to_not be_nil

    expect(content_repo_type(rh00051_cert, rh00051_content.id)).to eq(rh00051_content.type)
    expect(content_name(rh00051_cert, rh00051_content.id)).to eq(rh00051_content.name)

    # rh00271_content (which depends on modified product id 69) should not have been filtered out, because the
    # engineering product 69 should already be covered by entitlement rh00051:
    expect(content_repo_type(rh00271_cert, rh00271_content.id)).to eq(rh00271_content.type)
    expect(content_name(rh00271_cert, rh00271_content.id)).to eq(rh00271_content.name)
  end

  private

  def entitlement_cert(entitlement)
    if entitlement.is_a?(Array)
        return OpenSSL::X509::Certificate.new(entitlement.first['certificates'].first['cert'])
    else
        return OpenSSL::X509::Certificate.new(entitlement['certificates'].first['cert'])
    end
  end

  def content_repo_type(cert, content_id)
    extension_from_cert(cert, "1.3.6.1.4.1.2312.9.2.#{content_id}.1")
  end

  def content_name(cert, content_id)
    extension_from_cert(cert, "1.3.6.1.4.1.2312.9.2.#{content_id}.1.1")
  end
end
