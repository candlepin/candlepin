Feature: Entitlement Certificates
    As a consumer
    I can view my entitlment certificates

    Scenario: List certificates
        Given I am a Consumer "test_consumer"
        When I Consume an Entitlement for the "1852089416" Product
        Then I have 1 certificate

    Scenario: Filter certificates by serial number
        Given I am a Consumer "test_consumer"
        When I Consume an Entitlement for the "1852089416" Product
        And I Consume an Entitlement for the "72093906" Product
        When I filter certificates on the serial number for "72093906"
        Then I see 1 certificate


    Scenario: List certificates serial numbers
        Given I am a Consumer "test_consumer"
        When I Consume an Entitlement for the "1852089416" Product
        Then I have 1 certificate serial number
