Feature: Manipulate Subscriptions
    As a user
    I can view and modify my subscription data

    Scenario: List existing subscriptions
        Given I have username "foo"
        And I have password "bar"
        Then I have at least 5 subscriptions

    Scenario: Delete a subscription
        Given I have username "foo"
        And I have password "bar"
        Then I can delete a subscription

    Scenario: Create a subscription
        Given I have username "foo"
        And I have password "bar"
        Then I can create a new subscription
