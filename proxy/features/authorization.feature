Feature: Proper Authentication for viewing a Consumer
    In order secure by Consumer data
    As a Consumer
    I want to be able to restrict access to my data to myself and my Owner

    Background:
        Given an owner admin "guy"
        And I am logged in as "guy"

    Scenario: A Consumer can access their data
        Given I am a consumer "consumer"
        Then I should be able to view my Consumer data

    Scenario: A different Consumer is Denied
        Given Consumer "other_machine" exists with uuid "abcde"
        And I am a consumer "rhel_box"
        Then I should not be able to view Consumer "abcde"

    Scenario: A Non-existant Consumer is Not Found
        Given I am a consumer "some_name"
        Then I should not find Consumer "made_up_uuid"
