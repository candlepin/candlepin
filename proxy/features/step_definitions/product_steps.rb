

Then /^I can create a product called "([^\"]*)"$/ do |product_name|
#  	@owner_admin_cp.create_product('test_product', product_name, 'server',
#				  '1.0', 'ALL', 1231231, 'SVC', [])
	@candlepin.create_product(product_name, product_name)
end



