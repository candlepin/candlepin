Feature: Proper Authentication for viewing a Consumer
    In order secure by Consumer data
    As a Consumer
    I want to be able to restrict access to my data to myself and my Owner

    Scenario: A Consumer can access their data
        Given I am a Consumer "consumer"
        Then I should be able to view my Consumer data

    Scenario: A different Consumer is Denied
        Given I am a Consumer "rhel_box"
        Then I should not be able to view Consumer "made_up_uuid"
