Feature: Test creating user-licensed pool which then creates user-restricted sub pool
  
    Scenario: Consumer's user name is set with their login id
      Given I am a user "bob"
      When I create a consumer of type person
      Then the consumer's username should be "bob"

    Scenario: Create pool with unlimited user license and consumer type person
      Given I am a user "bob"
      When I create a pool of unlimited license and consumer type person
      Then pool should be of unlimited license and consumer type person

    Scenario: Create unlimited pool and consume entitlement from this pool
      Given I am a user "bob"
      And I create a consumer of type person
      And I create a consumer "bobby" of type "system"
      And I create a consumer "bob" of type "system"
      And I create a pool of unlimited license and consumer type person
      When I consume entitlement from pool for consumer of type person
      Then a new pool should have been created
      Then source entitlement of pool should match the entitlement just created
      Then pool should be of unlimited quantity and restricted to "bob"
      Then I should be able to consume entitlement for "bobby" system from this pool
      Then I should not be able to consume entitlement for a system "bob" does not own
      Then one of "bob" pools should be unlimited pool
	
  Scenario: Pools without filtering consumers should not include unlimited pool
    	Given I am a user "bob"
	    And I create a consumer of type person
      And I create a pool of unlimited license and consumer type person
      When I consume entitlement from pool for consumer of type person
      Then another consumer cannot see user-restricted pool
	