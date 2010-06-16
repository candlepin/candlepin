Feature: Subscription quantity is multiplied properly
  In order to consume the entitlements that I have paid for
  As an owner
  My consumers have access to the correct quantity in their entitlement pool

  Scenario: Simple > 1 multiplier
    Given product "Calendaring - 25 Pack" exists with multiplier 25
    And test owner has 4 entitlements for "Calendaring - 25 Pack"
    When I view all pools for my owner
    Then I see 1 pool
    And I see 100 available entitlements

  Scenario: Negative multiplier is ignored
    Given product "Fake Product" exists with multiplier -10
    And test owner has 34 entitlements for "Fake Product"
    When I view all pools for my owner
    Then I see 1 pool
    And I see 34 available entitlements
      
