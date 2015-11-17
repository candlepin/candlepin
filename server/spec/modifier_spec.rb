require 'spec_helper'
require 'candlepin_scenarios'

describe 'Modifier Entitlement' do

  include CandlepinMethods

  before(:each) do
    @owner = create_owner(random_string('modifier_spec'))

    # Three provided products, will be bundled in different ways:
    @provided_product_1 = create_product()
    @provided_product_2 = create_product()
    @provided_product_3 = create_product()

    # This bundled product contains all three of the provided products our modifier
    # product will modify:
    @bundled_product_1 = create_product()
    @bundled_pool_1 = create_pool_and_subscription(@owner['key'], @bundled_product_1.id, 10,
      [@provided_product_1.id, @provided_product_2.id, @provided_product_3.id])

    # This bundled product only contains two of the provided products our modifier
    # product will modify.
    @bundled_product_2 = create_product()
    @bundled_pool_2 = create_pool_and_subscription(@owner['key'], @bundled_product_2.id, 10,
      [@provided_product_1.id, @provided_product_2.id])


    # Create our modifier provided product, carries three content sets, each
    # of which modify one of the provided products above:
    @modifier_provided_product = create_product()
    @modifier_content_1 = create_content({:modified_products => [@provided_product_1.id]})
    @cp.add_content_to_product(@owner['key'], @modifier_provided_product.id, @modifier_content_1.id)
    @modifier_content_2 = create_content({:modified_products => [@provided_product_2.id]})
    @cp.add_content_to_product(@owner['key'], @modifier_provided_product.id, @modifier_content_2.id)
    @modifier_content_3 = create_content({:modified_products => [@provided_product_3.id]})
    @cp.add_content_to_product(@owner['key'], @modifier_provided_product.id, @modifier_content_3.id)

    # Create a bundled modifier product, just contains the modifier provided product:
    @modifier_product = create_product()
    @modifier_pool = create_pool_and_subscription(@owner['key'], @modifier_product.id, 10,
      [@modifier_provided_product.id])

    owner_client = user_client(@owner, random_string('testowner'))
    @consumer_cp = consumer_client(owner_client, random_string('consumer123'))
  end

  it 'includes modifier content sets' do

    # Bind to the normal subscription first:
    @consumer_cp.consume_product(@bundled_product_1.id)

    # Bind to the modifier subscription which modifies it:
    ent = @consumer_cp.consume_product(@modifier_product.id)

    # Modifier certificate should contain the modifier content:
    modifier_cert = entitlement_cert(ent)
    content_repo_type(modifier_cert, @modifier_content_1.id).should == 'yum'
    content_repo_type(modifier_cert, @modifier_content_2.id).should == 'yum'
    content_repo_type(modifier_cert, @modifier_content_3.id).should == 'yum'
  end

  it 'includes selective modifier content sets' do

    # Bind to the normal subscription first:
    @consumer_cp.consume_product(@bundled_product_2.id)

    # Bind to the modifier subscription which modifies it:
    ent = @consumer_cp.consume_product(@modifier_product.id)

    # Modifier certificate should contain the modifier content:
    modifier_cert = entitlement_cert(ent)
    content_repo_type(modifier_cert, @modifier_content_1.id).should == 'yum'
    content_repo_type(modifier_cert, @modifier_content_2.id).should == 'yum'
    content_repo_type(modifier_cert, @modifier_content_3.id).should be_nil
  end

  it 'does not include modifier content sets consumer should not have access to' do
    # Bind to the modifier subscription without having an entitlement to
    # the product it modifies:
    ent = @consumer_cp.consume_product(@modifier_product.id)

    # Resulting modifier cert should not contain modifier content set:
    modifier_cert = entitlement_cert(ent)
    content_repo_type(modifier_cert, @modifier_content_1.id).should be_nil
    content_repo_type(modifier_cert, @modifier_content_2.id).should be_nil
    content_repo_type(modifier_cert, @modifier_content_3.id).should be_nil
  end

  it 'is regenerated when consumer receives access to modified product' do
    # Bind to the modifier subscription without having an entitlement to
    # the product it modifies:
    ent = @consumer_cp.consume_product(@modifier_product.id)

    # Resulting modifier cert should not contain modifier content set:
    modifier_cert = OpenSSL::X509::Certificate.new(ent[0]['certificates'][0]['cert'])
    content_ext = extension_from_cert(modifier_cert, "1.3.6.1.4.1.2312.9.2." +
      @modifier_content_1.id + ".1")
    content_ext.should be_nil

    # Now bind to the product being modified:
    normal_serial = @consumer_cp.consume_product(@bundled_product_1.id)[0]\
      ['certificates'][0]['serial']['serial']

    # Old certificate should be gone:
    certs = @consumer_cp.list_certificates()
    certs.length.should == 2
    old_cert = certs.find_index{ |c| c.serial.serial == modifier_cert.serial.to_i}
    old_cert.should be_nil

    # Should be a new certificate in it's place:
    new_cert_index = certs.find_index{ |c| c.serial.serial != normal_serial}
    new_cert_index.should_not be_nil

    # And it should have the modifer content set:
    new_cert = OpenSSL::X509::Certificate.new(certs[new_cert_index]['cert'])
    content_repo_type(new_cert, @modifier_content_1.id).should == 'yum'
    content_repo_type(new_cert, @modifier_content_2.id).should == 'yum'
    content_repo_type(new_cert, @modifier_content_3.id).should == 'yum'
  end

  it 'is regenerated when consumer loses access to modified product' do
    # Bind to the normal subscription first:
    normal = @consumer_cp.consume_product(@bundled_product_1.id)

    # Bind to the modifier subscription which modifies it:
    modifier = @consumer_cp.consume_product(@modifier_product.id)

    # Now unbind from the original subscription
    @consumer_cp.unbind_entitlement(normal.first['id'])

    # Then refetch the modifier entitlement
    modifier = [@consumer_cp.get_entitlement(modifier.first['id'])]

    # Resulting modifier cert should not contain modifier content set:
    modifier_cert = entitlement_cert(modifier)
    content_repo_type(modifier_cert, @modifier_content_1.id).should be_nil
    content_repo_type(modifier_cert, @modifier_content_2.id).should be_nil
    content_repo_type(modifier_cert, @modifier_content_3.id).should be_nil
  end

  it 'is regenerated when modified product subscription disappears' do
    # Delete sub and refresh pools.
    # Bind to the normal subscription first:
    normal = @consumer_cp.consume_product(@bundled_product_1.id)

    # Bind to the modifier subscription which modifies it:
    modifier = @consumer_cp.consume_product(@modifier_product.id)

    # Now kill the subscription to the @bundled_product_1
    delete_pool_and_subscription(@bundled_pool_1)
    @cp.refresh_pools(@owner['key'])

    # Then refetch the modifier entitlement
    modifier = [@consumer_cp.get_entitlement(modifier.first['id'])]

    # Resulting modifier cert should not contain modifier content set:
    modifier_cert = entitlement_cert(modifier)
    content_repo_type(modifier_cert, @modifier_content_1.id).should be_nil
    content_repo_type(modifier_cert, @modifier_content_2.id).should be_nil
    content_repo_type(modifier_cert, @modifier_content_3.id).should be_nil
  end

  private

  def entitlement_cert(entitlement)
    OpenSSL::X509::Certificate.new(entitlement.first['certificates'].first['cert'])
  end

  def content_repo_type(cert, content_id)
    extension_from_cert(cert, "1.3.6.1.4.1.2312.9.2.#{content_id}.1")
  end

end

