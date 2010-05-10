Feature: Entitlement Certificates
    As a consumer
    I can view my entitlment certificates

    Background:
        Given an owner admin "test_owner"
        And I am logged in as "test_owner"
        And owner "test_owner" has 2 entitlements for "virtualization_host"
        And owner "test_owner" has 4 entitlements for "monitoring"

    Scenario: List certificates
        Given I am a consumer "test_consumer"
        When I Consume an Entitlement for the "monitoring" Product
        Then I have 1 certificate

    Scenario: Filter certificates by serial number
        Given I am a consumer "test_consumer"
        When I Consume an Entitlement for the "monitoring" Product
        And I Consume an Entitlement for the "virtualization_host" Product
        When I filter certificates on the serial number for "virtualization_host"
        Then I see 1 certificate


    Scenario: List certificates serial numbers
        Given I am a consumer "test_consumer"
        When I Consume an Entitlement for the "monitoring" Product
        Then I have 1 certificate serial number
