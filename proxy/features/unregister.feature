Feature: Unregister a Consumer
    As a Consumer
    I want to be able to unregister and free up the entitlement pool

    Background:
        Given an owner admin "test_owner"
        And I am logged in as "test_owner"
        And product "virtualization_host" exists
        And product "monitoring" exists
        And test owner has 2 entitlements for "virtualization_host"
        And test owner has 4 entitlements for "monitoring"

    Scenario: Unregister multiple consumers
        Given I am a consumer "consumer"
        And I have an entitlement named "virt_entitlement" for the "virtualization_host" product
        And I have an entitlement named "monitoring_entitlement" for the "monitoring" product
        When I unregister
        Then The entitlement named "virt_entitlement" should not exist
        And The entitlement named "monitoring_entitlement" should not exist
