Feature: Consume an Entitlement
    In order to Download Content for My Subscription
    As a Consumer
    I want to be able to Consumer Entitlements and Get my Entitlement Certificates

    Scenario: An Exception is thrown When Consumer filters Entitlement by Invalid Product ID
        Given I am a Consumer "consumer"
        Then I Get an Exception If I Filter by Product ID "non_existent"

    Scenario: Entitlement is Consumed
        Given I am a Consumer "random_box"
        When I Consume an Entitlement for the "72093906" Product
        Then I Have 1 Entitlement
        
    Scenario: Entitlement from a Pool is Consumed 
        Given I am a Consumer "random_box"
        When I Consume an Entitlement for the "72093906" Pool
        Then I Have 1 Entitlement

    Scenario: Multiple Entitlements are Consumed
        Given I am a Consumer "consumer"
        When I Consume an Entitlement for the "72093906" Product 
        And I Consume an Entitlement for the "1852089416" Product
        Then I Have 2 Entitlements

    Scenario: Single Entitlement has the correct productId
        Given I am a Consumer "bar"
        When I Consume an Entitlement for the "1852089416" Product
        Then I Have an Entitlement for the "1852089416" Product

    Scenario: Single Entitlement from a Pool has the correct productId 
        Given I am a Consumer "random_box"
        When I Consume an Entitlement for the "1852089416" Pool
        Then I Have an Entitlement for the "1852089416" Product

    Scenario: Multiple Entitlements have correct productIds
        Given I am a Consumer "michael_knight"
        When I Consume an Entitlement for the "1852089416" Product
        And I Consume an Entitlement for the "72093906" Product
        Then I Have an Entitlement for the "1852089416" Product
        And I Have an Entitlement for the "72093906" Product

    Scenario: A Consumer has No Entitlements After Unregistering
        Given I am a Consumer "some_machine"
        When I Consume an Entitlement for the "72093906" Product
        And I Revoke All My Entitlements
        Then I Have 0 Entitlements

    Scenario: Entitlement generates a certificate.
        Given I am a Consumer "meow"
        When I Consume an Entitlement for the "72093906" Product
        Then I Have 1 Certificate

    Scenario: A Consumer has No Entitlements After Unregistering Multiple Products
        Given I am a Consumer "foo"
        When I Consume an Entitlement for the "1852089416" Product
        And I Consume an Entitlement for the "72093906" Product
        And I Revoke All My Entitlements
        Then I Have 0 Entitlements

    Scenario: A Consumer can filter Entitlements by Product ID
        Given I am a Consumer "consumer"
        And I Consume an Entitlement for the "1852089416" Product
        And I Consume an Entitlement for the "72093906" Product
        Then I Get 1 Entitlement When I Filter by Product ID "RHN Satellite Monitoring"

    Scenario: Consuming the same entitlement by product twice is not allowed
        Given I am a Consumer "random_box"
        When I Consume an Entitlement for the "72093906" Product
        And I try to consume an Entitlement for the "72093906" Product again
        Then I recieve an http forbidden response

    Scenario: Consuming the same entitlement by pool twice is not allowed
        Given I am a Consumer "random_box"
        When I Consume an Entitlement for the "72093906" Pool
        And I try to consume an Entitlement for the "72093906" Pool again
        Then I recieve an http forbidden response

