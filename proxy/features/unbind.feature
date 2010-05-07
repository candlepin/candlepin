Feature: Unbind an entitlement
    As a Consumer
    I want to be able to unbind and entitlement and have it return to the pool

    Scenario: Unbind a single entitlement
        Given I am a consumer "consumer"
        When I Consume an Entitlement for the "monitoring" Product
        And I unbind my "monitoring" Entitlement
        Then I Have 0 Entitlements
        And my "monitoring" entitlement is returned to the pool
