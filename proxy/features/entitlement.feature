Feature: Consume an Entitlement
    In order to Download Content for My Subscription
    As a Consumer
    I want to be able to Consumer Entitlements and Get my Entitlement Certificates

    Background:
        Given an owner admin "test_owner"
        And I am logged in as "test_owner"
        And product "virtualization_host" exists
        And product "monitoring" exists
        And product "super_awesome" exists with the following attributes:
         | Name             | Value   |
         | cpu.cpu_socket(s)| 4       |
        And test owner has 20 entitlements for "virtualization_host"
        And test owner has 4 entitlements for "monitoring"
        And test owner has 4 entitlements for "super_awesome"

    Scenario: Candlepin Consumer entitlement bypasses rules
        Given I am a consumer "random_box" of type "candlepin" with facts:
         | Name             | Value   |
         | cpu.cpu_socket(s)| 8       |
        When I consume an entitlement for the "super_awesome" product
        Then I have an entitlement for the "super_awesome" product

    Scenario: An Exception is thrown When Consumer filters Entitlement by Invalid Product ID
        Given I am a consumer "consumer"
        Then I get an exception if I filter by product ID "non_existent"

    Scenario: Entitlement is Consumed
        Given I am a consumer "random_box"
        When I consume an entitlement for the "virtualization_host" product
        Then I have 1 entitlement
        
    Scenario: Entitlement from a Pool is Consumed 
        Given I am a consumer "random_box"
        When I consume an entitlement for the "virtualization_host" pool
        Then I have 1 entitlement

    Scenario: Entitlement With a Quantity of 10 is Consumed 
        Given I am a consumer "random_box"
        When I consume an entitlement for the "virtualization_host" product with a quantity of 10
        Then I have 1 entitlement with a quantity of 10

    Scenario: Multiple Entitlements are Consumed
        Given I am a consumer "consumer"
        When I consume an entitlement for the "virtualization_host" product 
        And I consume an entitlement for the "monitoring" product
        Then I have 2 entitlements

    Scenario: Single Entitlement has the correct productId
        Given I am a consumer "bar"
        When I consume an entitlement for the "monitoring" product
        Then I have an entitlement for the "monitoring" product

    Scenario: Single Entitlement from a Pool has the correct productId 
        Given I am a consumer "random_box"
        When I consume an entitlement for the "monitoring" pool
        Then I have an entitlement for the "monitoring" product

    Scenario: Multiple Entitlements have correct productIds
        Given I am a consumer "michael_knight"
        When I consume an entitlement for the "monitoring" product
        And I consume an entitlement for the "virtualization_host" product
        Then I have an entitlement for the "monitoring" product
        And I have an entitlement for the "virtualization_host" product

    Scenario: A Consumer has No Entitlements After Unregistering
        Given I am a consumer "some_machine"
        When I consume an entitlement for the "virtualization_host" product
        And I revoke all my entitlements
        Then I have 0 entitlements

    Scenario: Entitlement generates a certificate.
        Given I am a consumer "meow"
        When I consume an entitlement for the "virtualization_host" product
        Then I have 1 certificate

    Scenario: A Consumer has No Entitlements After Unregistering Multiple Products
        Given I am a consumer "foo"
        When I consume an entitlement for the "monitoring" product
        And I consume an entitlement for the "virtualization_host" product
        And I revoke all my entitlements
        Then I have 0 entitlements

    Scenario: A Consumer can filter Entitlements by Product ID
        Given I am a consumer "consumer"
        And I consume an entitlement for the "monitoring" product
        And I consume an entitlement for the "virtualization_host" product
        Then I get 1 entitlement when I filter by product ID "monitoring"

    Scenario: Consuming the same entitlement by product twice is not allowed
        Given I am a consumer "random_box"
        When I consume an entitlement for the "virtualization_host" product
        And I try to consume an entitlement for the "virtualization_host" product again
        Then I recieve an http forbidden response

    Scenario: Consuming the same entitlement by pool twice is not allowed
        Given I am a consumer "random_box"
        When I consume an entitlement for the "virtualization_host" pool
        And I try to consume an entitlement for the "virtualization_host" pool again
        Then I recieve an http forbidden response
        

