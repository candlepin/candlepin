require 'spec_helper'
require 'candlepin_scenarios'

describe 'Product Certificate' do
  include CandlepinMethods

  before(:each) do
    @owner = create_owner(random_string('test_owner'))

    # Static map of extension labels to OID values
    @oid_map = {
      'name' => '1',
      'variant' => '2',
      'arch' => '3',
      'version' => '4'
    }
    @product = create_product(nil, random_string('test-product'))

    cert_data = @cp.get_product_cert(@owner['key'], @product.id)
    @cert = OpenSSL::X509::Certificate.new(cert_data.cert)
  end

  it 'should be valid' do
    @cert.should_not be_nil
  end

  it 'should have the correct product name' do
    oid = get_oid(@product, 'name')

    # Loop over extensions and find the one with the correct OID
    extension = @cert.extensions.select { |ext| ext.oid == oid}[0]

    value = extension.value
    value = value[2..-1] if value.match(/^\.\./)    # Weird ssl cert issue - have to strip the leading dots

    value.should == @product.name
  end

  # TODO:  Add in other OID tests?

  private

  def get_oid(product, label)
    oid = @oid_map[label]

    "1.3.6.1.4.1.2312.9.1.#{product.id}.#{oid}"
  end
end
