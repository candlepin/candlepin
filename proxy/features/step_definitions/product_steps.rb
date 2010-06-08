
# Static map of extension labels to OID values
@@oid_map = {
  'name' => '1',
  'variant' => '2',
  'arch' => '3',
  'version' => '4'
}

Given /^product "([^\"]*)" exists$/ do |product_name|
  create_product product_name
end

Then /^I can create a product called "([^\"]*)"$/ do |product_name|
  create_product product_name

  @product.should_not be_nil
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

Given /^product "([^\"]*)" exists with the following attributes:$/ do |product_name, table|
      # table is a Cucumber::Ast::Table
  attrs = {}
  table.hashes.each do |row|
      attrs[row['Name']] = row['Value']
  end
  create_product(product_name, attrs)
end

def create_product(product_name, attrs=nil)
  id = get_product_id(product_name)
  if attrs.nil?
      attrs = {'virtualization_host' => 'virtualization_host' }
  end
  @product = @candlepin.create_product(product_name, id, 1,
                                       'ALL', 'ALL', 'SVC', [],
                                       attrs)
end

def get_product_id(product_name)
  product_name.hash.abs
end

def get_product_certificate(product_name)
  id = get_product_id(product_name)
  product_cert = @candlepin.get_product_cert(id)
  OpenSSL::X509::Certificate.new(product_cert['cert'])
end

def get_oid(product_name, label)
  product_id = get_product_id(product_name)
  oid = @@oid_map[label]
  
  "1.3.6.1.4.1.2312.9.1.#{product_id}.#{oid}"
end
