Feature: Register a Consumer
    In order to make subsequent calls to Candlepin
    As a Consumer
    I want to be able to obtain a valid Identity Certificate

    Background:
        Given an owner admin "bill"

    Scenario: Identity Certificate is Generated
        Given I am logged in as "bill"
        And I register a consumer "someconsumer"
        Then my consumer should have an identity certificate

    Scenario: Correct UID on Identity Certificate
        Given I am logged in as "bill"
        And I register a consumer "some_box"
        Then the UID on my identity certificate's subject is my consumer's UUID

    Scenario: Correct CN on Identity Certificate
        Given I am logged in as "bill"
        And I register a consumer "kitt_the_car"
        Then the CN on my identity certificate's subject is kitt_the_car

    Scenario: Correct OU on Identity Certificate
        Given I am logged in as "bill"
        And I register a consumer "foo"
        Then the OU on my identity certificate's subject is bill

    Scenario: Register by UUID
        Given I am logged in as "bill"
        And there is no consumer with uuid "special_uuid"
        When I register a consumer "my_machine" with uuid "special_uuid"
        Then the UID on my identity certificate's subject is special_uuid

    Scenario: Reuse a UUID during registration
        Given I am logged in as "bill"
        And there is no consumer with uuid "special_uuid"
        When I register a consumer "my_machine" with uuid "special_uuid"
        Then Registering another Consumer with uuid "special_uuid" causes a bad request
        
    Scenario: Getting a consumer that does not exist should return a Not Found
        Given I am logged in as "bill"
        And there is no consumer with uuid "jar_jar_binks"
        Then Searching for a Consumer with uuid "jar_jar_binks" causes a not found     
