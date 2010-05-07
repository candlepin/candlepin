Feature: Unregister a Consumer
    As a Consumer
    I want to be able to unregister and free up the entitlement pool

    Scenario: Unregister multiple consumers
        Given an owner admin "bill"
        And I am a consumer "consumer"
        And I have an entitlement named "virt_entitlement" for the "virtualization_host" product
        And I have an entitlement named "monitoring_entitlement" for the "monitoring" product
        When I unregister
        Then The entitlement named "virt_entitlement" should not exist
        And The entitlement named "monitoring_entitlement" should not exist
