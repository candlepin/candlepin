Feature: Consume an Entitlement
    In order to Download Content for My Subscription
    As a Consumer
    I want to be able to Consumer Entitlements and Get my Entitlement Certificates

    Background:
        Given an owner admin "test_owner"
        And I am logged in as "test_owner"
        And product "virtualization_host" exists
        And product "monitoring" exists
        And test owner has 2 entitlements for "virtualization_host"
        And test owner has 4 entitlements for "monitoring"

    Scenario: An Exception is thrown When Consumer filters Entitlement by Invalid Product ID
        Given I am a consumer "consumer"
        Then I Get an Exception If I Filter by Product ID "non_existent"

    Scenario: Entitlement is Consumed
        Given I am a consumer "random_box"
        When I Consume an Entitlement for the "virtualization_host" Product
        Then I Have 1 Entitlement
        
    Scenario: Entitlement from a Pool is Consumed 
        Given I am a consumer "random_box"
        When I Consume an Entitlement for the "virtualization_host" Pool
        Then I Have 1 Entitlement

    Scenario: Entitlement With a Quantity of 10 is Consumed 
        Given I am a consumer "random_box"
        When I Consume an Entitlement for the "virtualization_host" Product With a Quantity of 10
        Then I Have 1 Entitlement With a Quantity of 10

    Scenario: Multiple Entitlements are Consumed
        Given I am a consumer "consumer"
        When I Consume an Entitlement for the "virtualization_host" Product 
        And I Consume an Entitlement for the "monitoring" Product
        Then I Have 2 Entitlements

    Scenario: Single Entitlement has the correct productId
        Given I am a consumer "bar"
        When I Consume an Entitlement for the "monitoring" Product
        Then I Have an Entitlement for the "monitoring" Product

    Scenario: Single Entitlement from a Pool has the correct productId 
        Given I am a consumer "random_box"
        When I Consume an Entitlement for the "monitoring" Pool
        Then I Have an Entitlement for the "monitoring" Product

    Scenario: Multiple Entitlements have correct productIds
        Given I am a consumer "michael_knight"
        When I Consume an Entitlement for the "monitoring" Product
        And I Consume an Entitlement for the "virtualization_host" Product
        Then I Have an Entitlement for the "monitoring" Product
        And I Have an Entitlement for the "virtualization_host" Product

    Scenario: A Consumer has No Entitlements After Unregistering
        Given I am a consumer "some_machine"
        When I Consume an Entitlement for the "virtualization_host" Product
        And I revoke all my entitlements
        Then I Have 0 Entitlements

    Scenario: Entitlement generates a certificate.
        Given I am a consumer "meow"
        When I Consume an Entitlement for the "virtualization_host" Product
        Then I Have 1 Certificate

    Scenario: A Consumer has No Entitlements After Unregistering Multiple Products
        Given I am a consumer "foo"
        When I Consume an Entitlement for the "monitoring" Product
        And I Consume an Entitlement for the "virtualization_host" Product
        And I revoke all my entitlements
        Then I Have 0 Entitlements

    Scenario: A Consumer can filter Entitlements by Product ID
        Given I am a consumer "consumer"
        And I Consume an Entitlement for the "monitoring" Product
        And I Consume an Entitlement for the "virtualization_host" Product
        Then I Get 1 Entitlement When I Filter by Product ID "monitoring"

    Scenario: Consuming the same entitlement by product twice is not allowed
        Given I am a consumer "random_box"
        When I Consume an Entitlement for the "virtualization_host" Product
        And I try to consume an Entitlement for the "virtualization_host" Product again
        Then I recieve an http forbidden response

    Scenario: Consuming the same entitlement by pool twice is not allowed
        Given I am a consumer "random_box"
        When I Consume an Entitlement for the "virtualization_host" Pool
        And I try to consume an Entitlement for the "virtualization_host" Pool again
        Then I recieve an http forbidden response

