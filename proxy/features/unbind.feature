Feature: Unbind an entitlement
    As a Consumer
    I want to be able to unbind and entitlement and have it return to the pool

    Scenario: Unbind a single entitlement
        Given I am a Consumer "consumer"
        When I Consume an Entitlement for the "1852089416" Product
        And I unbind my "1852089416" Entitlement
        Then I Have 0 Entitlements
        And my "1852089416" entitlement is returned to the pool
