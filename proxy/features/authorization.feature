Feature: Proper Authentication for viewing a Consumer
    In order secure by Consumer data
    As a Consumer
    I want to be able to restrict access to my data to myself and my Owner

    Background:
        Given an owner admin "guy"
        And I am logged in as "guy"

    Scenario: A Consumer can access their data
        Given I register a consumer "consumer"
        When I am logged in as consumer "consumer"
        Then I should be able to view my consumer data

    Scenario: A different Consumer is Denied
        Given I register a consumer "consumer1"
        And I register a consumer "consumer2"
        When I am logged in as consumer "consumer2"
        Then I should not be able to view consumer "consumer1"

    Scenario: A Non-existant Consumer is Not Found
        Given I register a consumer "consumer1"
        When I am logged in as consumer "consumer1"
        Then I should not find consumer "made_up_uuid"
