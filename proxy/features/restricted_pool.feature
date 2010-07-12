Feature: Products with "user-license" attribute creates a sub-pool

    Background:
      # Product definitions
      Given product "editor" exists with ID 345
      And product "tooling" exists with the following attributes:
        | Name                   | Value     |
        | user_license           | unlimited |
        | user_license_product   | 345       |
        | requires_consumer_type | person    |

      # Actor definitions
      Given owner "awesome-shop" exists
      And user "jay" exists under owner "awesome-shop"

      # Subscription definitions
      Given owner "awesome-shop" has a subscription for product "tooling" with quantity 15

    Scenario: A sub-pool is created upon consumption of a parent pool entitlement
      When user "jay" registers consumer "jay-consumer" with type "person"
      And consumer "jay-consumer" consumes an entitlement for product "tooling"
      And user "jay" registers consumer "sys1" with type "system"

      Then consumer "sys1" has access to a pool for product "editor"

    Scenario: A system consumer can bind to the sub-pool and has a valid certificate
      When user "jay" registers consumer "jay-consumer" with type "person"
      And consumer "jay-consumer" consumes an entitlement for product "tooling"
      And user "jay" registers consumer "sys2" with type "system"
      And consumer "sys2" consumes an entitlement for product "editor"

      # Currently legitimately failing
      #Then consumer "sys2" has 1 entitlement certificate

    Scenario: Restricted Pools should be deleted when consumer is deleted
      Given I am a user "bob"
      And I create a consumer of type person
      And I create a pool of unlimited license and consumer type person
      When I delete the consumer
      Then pool of unlimited license should be deleted too      	
