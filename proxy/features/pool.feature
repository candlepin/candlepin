Feature: Entitlement Pools have Product Name
    In order to know which Entitlement Pool to choose
    As a Consumer
    I want to be able to see the Marketing name for all entitlement pools

    Scenario: First pool available has the correct name
        Given I am a Consumer "consumer"
        Then The first pool's product name is "Spacewalk"
