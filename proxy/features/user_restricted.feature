Feature: Filter Entitlement Pools on "user_restricted" attribute
  In order to see only valid entitlement pools
  As a consumer
  I want to have "user_restricted" pools filtered from my view

  Background:
    Given an owner admin "billy003"
    And I am a consumer "box1" registered by "billy003"

  Scenario: Restricted product for the correct user
    Given product "Great Product" exists
    And I have a pool of quantity 5 for "Great Product" with a "user_restricted" attribute of "billy003"
    Then I have access to a pool for product "Great Product"

  Scenario: Restricted product is filtered out
    Given product "test-product" exists
    And I have a pool of quantity 2 for "test-product" with a "user_restricted" attribute of "ann_h"
    Then I have access to 0 pools

  Scenario: Mix of restricted and unrestricted
    Given product "my-product" exists
    And product "not-mine" exists
    And product "no-restrictions" exists
    And I have a pool of quantity 7 for "my-product" with a "user_restricted" attribute of "billy003"
    And I have a pool of quantity 1 for "not-mine" with a "user_restricted" attribute of "bob"
    And I have a pool of quantity 15 for "no-restrictions"
    Then I have access to a pool for product "my-product"
    And I have access to 2 pools
    
    
    

    
