Feature: Manipulate Subscriptions
    As a user
    I can view and modify my subscription data

    Background:
        Given an owner admin "testowner"
        And I am logged in as "test_owner"
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
