Feature: Entitlement Pools have Product Name
    In order to know which Entitlement Pool to choose
    As a Consumer
    I want to be able to see the Marketing name for all entitlement pools

    Scenario: First pool available has the correct name
        Given product "virtualization_host" exists
        And an owner admin "test_owner"
        And I am logged in as "test_owner"
        And test owner has 2 entitlements for "virtualization_host"
        And I am a consumer "consumer"
        Then I have access to a pool for product "virtualization_host"
