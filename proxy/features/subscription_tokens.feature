Feature: Manipulate Subscription Tokens
    As a user
    I can view and modify my subscription tokens

    Background:
        Given an owner admin "test_owner"
        And I am logged in as "test_owner"
        And I register a consumer "someconsumer"

    Scenario: List existing subscription tokens
        Given I have a subscription token called "test-token"
        Then I have at least 1 subscription token

    Scenario: Delete a subscription token
        Given I have a subscription token called "test-token"
        Then I can delete a subscription token "test-token"

    Scenario: Create a subscription token
        Given there is no subscription token called "test-token"
        Then I can create a subscription token "test-token"

    Scenario: A pool is created if a new subscription returns when binding by token.
        Given product "fauxproduct" exists
        And test owner has a subscription for "fauxproduct" with quantity 10 and token "fauxtoken"
        And test owner has no pools for "fauxproduct"
        When consumer "someconsumer" binds by token "fauxtoken"
        Then test owner has 1 pool for "fauxproduct"
