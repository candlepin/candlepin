Feature: Consume an Entitlement
    In order to Download Content for My Subscription
    As a Consumer
    I want to be able to Consumer Entitlements and Get my Entitlement Certificates

    Scenario: Entitlement is Consumed
        Given I am a Consumer random_box
        When I Consume an Entitlement for the "virtualization_host" Product
        Then I Have 1 Entitlement
        
    Scenario: Entitlement from a Pool is Consumed 
        Given I am a Consumer random_box
        When I Consume an Entitlement for the "virtualization_host" Pool
        Then I Have 1 Entitlement

    Scenario: Multiple Entitlements are Consumed
        Given I am a Consumer consumer
        When I Consume an Entitlement for the "virtualization_host" Product
        And I Consume an Entitlement for the "monitoring" Product
        Then I Have 2 Entitlements

    Scenario: Single Entitlement has the correct productId
        Given I am a Consumer bar
        When I Consume an Entitlement for the "monitoring" Product
        Then I Have an Entitlement for the "monitoring" Product

    Scenario: Multiple Entitlements have correct productIds
        Given I am a Consumer michael_knight
        When I Consume an Entitlement for the "monitoring" Product
        And I Consume an Entitlement for the "virtualization_host" Product
        Then I Have an Entitlement for the "monitoring" Product
        And I Have an Entitlement for the "virtualization_host" Product

    Scenario: A Consumer has No Entitlements After Unregistering
        Given I am a Consumer some_machine
        When I Consume an Entitlement for the "virtualization_host" Product
        And I Revoke All My Entitlements
        Then I Have 0 Entitlements

    Scenario: A Consumer has No Entitlements After Unregistering Multiple Products
        Given I am a Consumer foo
        When I Consume an Entitlement for the "monitoring" Product
        And I Consume an Entitlement for the "virtualization_host" Product
        And I Revoke All My Entitlements
        Then I Have 0 Entitlements
