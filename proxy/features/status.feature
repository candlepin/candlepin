Feature: Status URL

    Scenario: Status should not be Unknown 
        When I visit the status URI
        Then status should be known 
