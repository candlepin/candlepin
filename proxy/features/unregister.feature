Feature: Unregister a Consumer
    As a Consumer
    I want to be able to unregister and free up the entitlement pool

    Scenario: Unregister multiple consumers
        Given I am a Consumer "consumer"
        And I have an Entitlement named "virt_entitlement" for the "RHN Satellite Virtulization Host" Product
        And I have an Entitlement named "monitoring_entitlement" for the "RHN Satellite Monitoring" Product
        When I unregister
        And I become user "foo" with password "bar"
        Then The entitlement named "virt_entitlement" should not exist
        And The entitlement named "monitoring_entitlement" should not exist
