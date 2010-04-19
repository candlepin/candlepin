Feature: Manipulate Subscription Tokens
    As a user
    I can view and modify my subscription tokens

    Scenario: List existing subscription tokens
        Given I have username "foo"
        And I have password "bar"
        And I have a subscription token called "test-token"
        Then I have at least 1 subscription token

    Scenario: Delete a subscription token
        Given I have username "foo"
        And I have password "bar"
        And there is no subscription token called "test-token"
        Then I can create a subscription token "test-token"
        And I can delete a subscription token "test-token"

    Scenario: Create a subscription token
        Given I have username "foo"
        And I have password "bar"
        And there is no subscription token called "test-token"
        Then I can create a subscription token "test-token"
