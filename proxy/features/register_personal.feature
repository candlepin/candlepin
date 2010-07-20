Feature: Register a Personal Consumer
  In order to consume personal entitlements
  As a user
  I want to be able to register a consumer that is associated with me

  Scenario: Identity Certificate is Generated
    Given an owner admin "Roger"
    And I am logged in as "Roger"
    When I register a personal consumer
    Then my consumer should have an identity certificate

  Scenario:  Correct CN on identity certificate
    Given an owner admin "test-account"
    And I am logged in as "test-account"
    When I register a personal consumer
    Then the "CN" on my identity certificate's subject is my consumer's UUID
    Then the consumers name in the certificate is "test-account"

  Scenario:  Multiple personal consumers cannot be registered
    Given an owner admin "Roger"
    And I am logged in as "Roger"
    When I register a personal consumer
    Then I should not be able to register a new personal consumer
