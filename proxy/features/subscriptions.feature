Feature: Manipulate Subscriptions
    As a user
    I can view and modify my subscription data

    Background:
        Given an owner admin "testowner"

    Scenario: List existing subscriptions
        Given owner "testowner" has 2 entitlements for "some_product"
        And owner "testowner" has 3 entitlements for "another_product"
        And owner "testowner" has 2 entitlements for "one_more_product"
        Then I have 3 subscriptions

    Scenario: Delete a subscription
        Given owner "testowner" has 5 entitlements for "monitoring"
        When I delete the subscription for product "monitoring"
        Then I have 0 subscriptions
