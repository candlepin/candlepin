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

#        And I am a Consumer "my_machine"
#        Then My Identity Certificate exists

    Scenario: Correct UID on Identity Certificate
        Given I am a Consumer "some_box"
        Then The UID on my Identity Certificate's Subject is My Consumer's uuid

    Scenario: Correct CN on Identity Certificate
        Given I am a Consumer "kitt_the_car"
        Then The CN on my Identity Certificate's Subject is kitt_the_car

    Scenario: Correct OU on Identity Certificate
        Given I am a Consumer "foo"
        Then The OU on my Identity Certificate's Subject is candlepin_user

    Scenario: Register by UUID
        Given I have username "test_user"
        And I have password "password"
        And there is no Consumer with uuid "special_uuid"
        When I register a consumer "my_machine" with uuid "special_uuid"
        Then The UID on my Identity Certificate's Subject is special_uuid

    Scenario: Reuse a UUID during registration
        Given I have username "test_user"
        And I have password "password"
        And there is no Consumer with uuid "special_uuid"
        When I register a consumer "my_machine" with uuid "special_uuid"
        Then Registering another Consumer with uuid "special_uuid" causes a bad request
        
    Scenario: Getting a consumer that does not exist should return a Not Found
        Given I have username "test_user"
        And I have password "password"
        And there is no Consumer with uuid "jar_jar_binks"
        Then Searching for a Consumer with uuid "jar_jar_binks" causes a not found     
