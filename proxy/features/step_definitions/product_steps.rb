
# Static map of extension labels to OID values
@@oid_map = {
  'name' => '1',
  'variant' => '2',
  'arch' => '3',
  'version' => '4'
}

Before do
  @products = {}
end

Given /^product "([^\"]*)" exists$/ do |product_name|
  create_product product_name
end

Given /^product "([^\"]*)" exists with ID (\d+)$/ do |product_name, product_id|
  create_product(product_name, product_id)
end

Given /^product "([^\"]*)" exists with multiplier (-?\d+)$/ do |product_name, multiplier|
  create_product(product_name, nil, multiplier.to_i)
end

Given /^product "([^\"]*)" exists with the following attributes:$/ do |product_name, table|
      # table is a Cucumber::Ast::Table
  attrs = table.rows_hash.delete_if { |key, value| key == 'Name' } # Get rid of the Name:Value entry
  create_product(product_name, nil, 1, attrs)
end

Then /^the certificate for "([^\"]*)" is valid$/ do |product_name|
  cert = get_product_certificate(product_name)
  cert.should_not be_nil
end

Then /^the certificate for "([^\"]*)" has extension "([^\"]*)" with value "([^\"]*)"$/ do
|product_name, extension_name, expected_value|

  oid = get_oid(product_name, extension_name)
  cert = get_product_certificate(product_name)

  # Loop over extensions and find the one with the correct OID
  extension = cert.extensions.select { |ext| ext.oid == oid}[0]

  value = extension.value
  value = value[2..-1] if value.match(/^\.\./)    # Weird ssl cert issue - have to strip the leading dots

  value.should == expected_value
end

def create_product(product_name, product_id=nil, multiplier=1, attrs={})
  # Product ID is a string - just reuse the name for simplicity
  product_id ||= product_name.hash.abs

  @products[product_name] = @candlepin.create_product(product_id, product_name,
      {:multiplier => multiplier, :attributes => attrs})
end

def get_product_certificate(product_name)
  id = @products[product_name]['id']
  product_cert = @candlepin.get_product_cert(id)
  OpenSSL::X509::Certificate.new(product_cert['cert'])
end

def get_oid(product_name, label)
  product_id = @products[product_name]['id']
  oid = @@oid_map[label]
  
  "1.3.6.1.4.1.2312.9.1.#{product_id}.#{oid}"
end
