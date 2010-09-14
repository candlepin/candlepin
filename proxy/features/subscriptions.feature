Feature: Manipulate Subscriptions
    As a user
    I can view and modify my subscription data

    Background:
        Given an owner admin "testowner"
        And I am logged in as "testowner"
        And product "some_product" exists
        And product "another_product" exists
        And product "one_more_product" exists
        And product "monitoring" exists

    Scenario: List existing subscriptions
        Given test owner has 2 entitlements for "some_product"
        And test owner has 3 entitlements for "another_product"
        And test owner has 2 entitlements for "one_more_product"
        When I am logged in as "testowner"
        Then I have 3 subscriptions

    Scenario: Delete a subscription
        Given test owner has 5 entitlements for "monitoring"
        When I am logged in as "testowner"
        And I delete the subscription for product "monitoring"
        Then I have 0 subscriptions

    Scenario: Update existing subscription's date and check that entitlement certificates are regenerated.
        Given I am a consumer "jackiechan"
        And test owner has 10 entitlements for "some_product"
        When I consume an entitlement for the "some_product" product
        When test owner changes the "endDate" of the subscription by 2 days
	And he refreshes the pools
        Then I have new entitlement certificates
        And the properties "endDate" of entitlement and certificates should equal subscriptions


    Scenario: Update existing subscription quantity and check that entitlement certificates are deleted
        Given I am a consumer "jackiechan"
	And test owner has 10 entitlements for "some_product"
	When I consume an entitlement for the "some_product" product with a quantity of 6
	When test owner changes the quantity of the subscription by -5
	And he refreshes the pools
	Then I have 0 certificates

     Scenario: Update existing subscription quantity and date, check that entitlement certificates are regenerated.
        Given I am a consumer "jackiechan"
	And test owner has 10 entitlements for "some_product"
	When I consume an entitlement for the "some_product" product
	When test owner changes the quantity of the subscription by 10
        When test owner changes the "startDate" of the subscription by -10 days
	When test owner changes the "endDate" of the subscription by 10 days
	And he refreshes the pools
        Then I have new entitlement certificates
        And the properties "startDate, endDate" of entitlement and certificates should equal subscriptions
