
Given /^product "([^\"]*)" exists$/ do |product_name|
  create_product product_name
end

Then /^I can create a product called "([^\"]*)"$/ do |product_name|
  create_product product_name
end

def create_product(product_name)
  @candlepin.create_product(product_name, product_name, product_name.hash.abs, 1, \
    'ALL', 'ALL', 'SVC', [], {'virtualization_host' => 'virtualization_host'})
end

