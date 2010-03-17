Feature: Consume an Entitlement
    In order to Download Content for My Subscription
    As a Consumer
    I want to be able to Get an Entitlement Certificate

    Scenario: Entitlement is Consumed
        When I Register a New Consumer random_box
        And I Consume an Entitlement for the virtualization_host Product
        Then I Have 1 Entitlement

    Scenario: Entitlement's Pool has the correct productId
        When I Register a New Consumer foo
        And I Consume an Entitlement for the monitoring Product 
        Then My Entitlement Pool's productId is monitoring

    Scenario: A Consumer has No Entitlements After Unregistering
        When I Register a New Consumer some_machine
        And I Consume an Entitlement for the virtualization_host Product
        And I Revoke All My Entitlements
        Then I Have 0 Entitlements

    Scenario: A Consumer has No Entitlements After Unregistering Multiple Products
        When I Register a New Consumer foo
        And I Consume an Entitlement for the monitoring Product
        And I Consume an Entitlement for the virtualization_host Product
        And I Revoke All My Entitlements
        Then I Have 0 Entitlements
