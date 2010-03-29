Feature: Unregister a Consumer
    As a Consumer
    I want to be able to obtain to unregister and free up the entitlement pool

    Scenario: Unregister multiple consumers
        Given I am a Consumer consumer
        When I Consume an Entitlement for the "virtualization_host" Product
        And I Consume an Entitlement for the "monitoring" Product
	And I unregister
	Then all my entitlements are unbound
