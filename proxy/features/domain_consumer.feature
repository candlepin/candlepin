Feature: Limit products to a domain consumer type
    As a consumer
    I can only consume entitlements for products limited to domain consumers
    If my type is domain

    Background:
        Given an owner admin "test_owner"
        And I am logged in as "test_owner"
        And product "monitoring" exists
        And product "domain_product" exists with the following attributes:
            | Name                   | Value  |
            | requires_consumer_type | domain |
        And test owner has 4 entitlements for "monitoring"
        And test owner has 4 entitlements for "domain_product"


    Scenario: A domain consumer can consume non domain specific products
        Given I am a consumer "guest_consumer" of type "domain"
        When I Consume an Entitlement for the "monitoring" Product
        Then I Have 1 Entitlement

    Scenario: A domain consumer can consume domain specific products
        Given I am a consumer "guest_consumer" of type "domain"
        When I Consume an Entitlement for the "domain_product" Product
        Then I Have 1 Entitlement

    Scenario: A non domain consumer cannot consume domain specific products
        Given I am a consumer "guest_consumer" of type "system"
        Then attempting to Consume an entitlement for the "domain_product" product is forbidden
        And I Have 0 Entitlements

