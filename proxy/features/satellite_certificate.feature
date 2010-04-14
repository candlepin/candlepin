Feature: Load entitlement data from a Satellite Certificate
    As an administrator
    I can populate entitlement data from a Satellite Certificate

    Scenario: List products from a Satellite Certificate
        Given I am user "Spacewalk Public Cert" with password "testuserpass"
        And I have imported a Satellite Certificate
        Then I should have 5 products available
