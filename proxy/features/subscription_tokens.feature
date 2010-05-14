Feature: Manipulate Subscription Tokens
    As a user
    I can view and modify my subscription tokens

    Background:
        Given an owner admin "test_owner"
        And I am logged in as "test_owner"

    Scenario: List existing subscription tokens
        Given I have a subscription token called "test-token"
        Then I have at least 1 subscription token

    Scenario: Delete a subscription token
        Given I have a subscription token called "test-token"
        Then I can delete a subscription token "test-token"

    Scenario: Create a subscription token
        Given there is no subscription token called "test-token"
        Then I can create a subscription token "test-token"

#    Scenario: A pool is created if a new subscription returns when binding by token.
#        Given
