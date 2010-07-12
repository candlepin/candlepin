Feature: Entitlement Certificates
    As a consumer
    I can view my entitlment certificates

    Background:
        Given an owner admin "test_owner"
        And I am logged in as "test_owner"
        And product "virtualization_host" exists
        And product "monitoring" exists
        And test owner has 2 entitlements for "virtualization_host"
        And test owner has 4 entitlements for "monitoring"

    Scenario: List certificates
        Given I am a consumer "test_consumer"
        When I consume an entitlement for the "monitoring" product
        Then I have 1 certificate

    Scenario: Filter certificates by serial number
        Given I am a consumer "test_consumer"
        When I consume an entitlement for the "monitoring" product
        And I consume an entitlement for the "virtualization_host" product
        When I filter certificates on the serial number for "virtualization_host"
        Then I have 1 filtered certificate

    Scenario: List certificates serial numbers
        Given I am a consumer "test_consumer"
        When I consume an entitlement for the "monitoring" product
        Then I have 1 certificate serial number

    Scenario: Regenerate entitlement certificates for a consumer
        Given I am a consumer "test_consumer"
        When I consume an entitlement for the "virtualization_host" product
        And I regenerate all my entitlement certificates
        Then I have new entitlement certificates

    Scenario: Regenerate multiple entitlement certificates of a consumer
        Given I am a consumer "test_consumer"
        When I consume an entitlement for the "monitoring" product
        And I consume an entitlement for the "virtualization_host" product
        And I regenerate all my entitlement certificates
        Then I have new entitlement certificates