Feature: Register a Child Consumer
  In order to inherit access to entitlement pools
  As a Consumer
  I want to be able to register myself as a child of another consumer

  Scenario: Child consumer is set properly
    Given an owner admin "Bobby"
    And I am logged in as "Bobby"
    And I have registered a personal consumer with uuid "bobby-consumer"
    When I register a consumer "bobbys-system" with uuid "bobbys-system"
    Then consumer "bobbys-system" has parent consumer "bobby-consumer"
    
