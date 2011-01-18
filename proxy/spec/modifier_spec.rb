require 'candlepin_scenarios'

describe 'Modifier Entitlement' do

  include CandlepinMethods
  it_should_behave_like 'Candlepin Scenarios'

  before(:each) do
    @owner = create_owner(random_string('modifier_spec'))

    # Normal product, will be "modified" by the modifier_product content sets:
    @normal_product = create_product()
    @normal_sub = @cp.create_subscription(@owner.key, @normal_product.id, 10)

    # Setup the modifier product which modifies the above product:
    content = create_content({:modified_products => [@normal_product.id]})
    @modifier_product = create_product()
    @cp.add_content_to_product(@modifier_product.id, content.id)
    @modifier_sub = @cp.create_subscription(@owner.key, @modifier_product.id, 10)
    @cp.refresh_pools(@owner.key)

    owner_client = user_client(@owner, random_string('testowner'))
    @consumer_cp = consumer_client(owner_client, random_string('consumer123'))
  end

  it 'skips content sets consumer does not have access to' do
    # If we just bind to the modifier sub, we should get an entitlement cert
    # but with no content sets:
    ent = @consumer_cp.consume_product(@modifier_product.id)
    cert_json = @consumer_cp.list_certificates()[0]
    modifier_cert = OpenSSL::X509::Certificate.new(cert_json['cert'])
#    pp get_extension(modifier_cert, "1.3.6.1.4.1.2312.9.2")
    
  end

#  it 'is regenerated when consumer receives access to modified product' do
#  end

#  it 'is regenerated when consumer loses access to modified product' do
#  end

#  it 'is regenerated when modified product subscription disappears' do
    # Delete sub and refresh pools.
#  end

end

