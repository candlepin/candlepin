require 'candlepin_scenarios'

describe 'Modifier Entitlement' do

  include CandlepinMethods
  it_should_behave_like 'Candlepin Scenarios'

  before(:each) do
    @owner = create_owner(random_string('modifier_spec'))

    # Normal product, will be "modified" by the modifier_product content sets:
    @normal_product = create_product()
    @normal_content = create_content()
    @cp.add_content_to_product(@normal_product.id, @normal_content.id)
    @normal_sub = @cp.create_subscription(@owner.key, @normal_product.id, 10)

    # Setup the modifier product which modifies the above product:
    @modifier_product = create_product()
    @modifier_content = create_content({:modified_products => [@normal_product.id]})
    @cp.add_content_to_product(@modifier_product.id, @modifier_content.id)
    @modifier_sub = @cp.create_subscription(@owner.key, @modifier_product.id, 10)
    @cp.refresh_pools(@owner.key)

    owner_client = user_client(@owner, random_string('testowner'))
    @consumer_cp = consumer_client(owner_client, random_string('consumer123'))
  end

  it 'includes modifier content sets' do

    # Bind to the normal subscription first:
    @consumer_cp.consume_product(@normal_product.id)

    # Bind to the modifier subscription which modifies it:
    ent = @consumer_cp.consume_product(@modifier_product.id)

    # Modifier certificate should contain the modifier content:
    modifier_cert = OpenSSL::X509::Certificate.new(ent[0]['certificates'][0]['cert'])
    content_ext = get_extension(modifier_cert, "1.3.6.1.4.1.2312.9.2." +
      @modifier_content.id + ".1")
    content_ext.should_not be_nil
  end

  it 'does not include modifier content sets consumer should not have access to' do

    # Bind to the modifier subscription without having an entitlement to
    # the product it modifies:
    ent = @consumer_cp.consume_product(@modifier_product.id)

    # Resulting modifier cert should not contain modifier content set:
    modifier_cert = OpenSSL::X509::Certificate.new(ent[0]['certificates'][0]['cert'])
    content_ext = get_extension(modifier_cert, "1.3.6.1.4.1.2312.9.2." +
      @modifier_content.id + ".1")
    content_ext.should be_nil
  end

#  it 'is regenerated when consumer receives access to modified product' do
#  end

#  it 'is regenerated when consumer loses access to modified product' do
#  end

#  it 'is regenerated when modified product subscription disappears' do
    # Delete sub and refresh pools.
#  end

end

