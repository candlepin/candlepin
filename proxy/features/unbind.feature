Feature: Unbind an entitlement
    As a Consumer
    I want to be able to unbind and entitlement and have it return to the pool

    Background:
        Given an owner admin "test_owner"
        And I am logged in as "test_owner"
        And product "monitoring" exists
        And test owner has 4 entitlements for "monitoring"

    Scenario: Unbind a single entitlement
        Given I am a consumer "consumer"
        When I consume an entitlement for the "monitoring" product
        And I unbind my "monitoring" entitlement
        Then I have 0 entitlements
        And my "monitoring" entitlement is returned to the pool

    Scenario: Unbinding an entitlement revokes its certificate
        Given I am a consumer "dude"
        When I consume an entitlement for the "monitoring" product
        And I filter certificates on the serial number for "monitoring"
        And I unbind my "monitoring" entitlement
        Then the filtered certificates are revoked

    Scenario: Unbinding an entitlement leaves others alone
        Given product "virt_host" exists
        And test owner has 5 entitlements for "virt_host"
        And I am a consumer "consumer"
        When I consume an entitlement for the "monitoring" product
        And I consume an entitlement for the "virt_host" product
        And I filter certificates on the serial number for "monitoring"
        And I unbind my "virt_host" entitlement
        Then the filtered certificates are not revoked
        
