Feature: Register a Consumer
    In order to make subsequent calls to Candlepin
    As a Consumer
    I want to be able to obtain a valid Identity Certificate

    Scenario: Identity Certificate is Generated
        Given I have username foo
        And I have password bar
        When I Register a New Consumer my_machine
        Then My Identity Certificate exists

    Scenario: Correct UID on Identity Certificate
        Given I have username roberto
        And I have password redhat
        When I Register a New Consumer some_box
        Then The UID on my Identity Certificate's Subject is My Consumer's uuid

    Scenario: Correct CN on Identity Certificate
        Given I have username Billy
        And I have password foo
        When I Register a New Consumer kit_the_car
        Then The CN on my Identity Certificate's Subject is kit_the_car

    Scenario: Correct OU on Identity Certificate
        Given I have username candlepin_user
        And I have password default
        When I Register a New Consumer foo
        Then The OU on my Identity Certificate's Subject is candlepin_user
