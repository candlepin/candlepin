Feature: Create a Content
	 As a Owner
	 I want to be able to create a Content

	 Scenario: Create a Content
	 	   Then I can create a content called "test-content"
		   Then I can create a product called "test-product"
		   Then I have Content 
		   Then I add a content "test-content" to a product "test-product"