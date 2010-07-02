Feature: Query pools
    As a consumer
    I can view all pools for my owner, or only those applicable to me

    Background:
        Given an owner admin "testowner"
        And product "some_product" exists
        And product "another_product" exists
        And test owner has 2 entitlements for "some_product"
        And test owner has 3 entitlements for "another_product"
        And test owner has 6 entitlements for "some_product"

    Scenario: View all pools
        Given I am a consumer "random_box"
        When I view all pools for my owner
        Then I see 3 pools
        And I see 11 available entitlements

    Scenario: View pools for products I've already subscribed to
        Given I am a consumer "random_box"
        When I consume an entitlement for the "some_product" product
        And I consume an entitlement for the "another_product" product
        And I view all pools for my owner
        Then I see 3 pools
        And I see 9 available entitlements
        And I have 2 entitlements
