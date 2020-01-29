require 'spec_helper'
require 'candlepin_scenarios'

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
    @bundled_pool_1 = create_pool_and_subscription(@owner['key'], @bundled_product_1.id, 10,
      [@required_product_1.id, @required_product_2.id, @required_product_3.id])

    # This bundled product only contains two of the provided products our conditional content
    # will require
    @bundled_product_2 = create_product(nil, nil,
      {:providedProducts => [@required_product_1.id, @required_product_2.id]})
    @bundled_pool_2 = create_pool_and_subscription(@owner['key'], @bundled_product_2.id, 10,
      [@required_product_1.id, @required_product_2.id])

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
    @dependent_pool = create_pool_and_subscription(@owner['key'], @dependent_product.id, 10, [@dependent_provided_product.id])

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
    delete_pool_and_subscription(@bundled_pool_1)
    delete_pool_and_subscription(@bundled_pool_2)
    @cp.refresh_pools(@owner['key'])

    # Re-fetch the modifier entitlement...
    entitlement = @consumer_cp.get_entitlement(entitlement['id'])
    expect(entitlement).to_not be_nil

    # Verify that we don't have any content repos anymore
    dependent_cert = entitlement_cert(entitlement)
    expect(content_repo_type(dependent_cert, @conditional_content_1.id)).to be_nil
    expect(content_repo_type(dependent_cert, @conditional_content_2.id)).to be_nil
    expect(content_repo_type(dependent_cert, @conditional_content_3.id)).to be_nil
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
end
