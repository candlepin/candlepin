Feature: Unbind an entitlement
    As a Consumer
    I want to be able to unbind and entitlement and have it return to the pool

    Scenario: Unbind a single entitlement
        Given I am a Consumer "consumer"
        When I Consume an Entitlement for the "RHN Satellite Monitoring" Product
        And I unbind my "RHN Satellite Monitoring" Entitlement
        Then I Have 0 Entitlements
        And my "RHN Satellite Monitoring" entitlement is returned to the pool
