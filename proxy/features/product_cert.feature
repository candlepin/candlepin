Feature: Create a Product Certificate
  In order to obtain a product certificate for testing
  As a super user
  I want to be able to create a valid product certificate

  Background:
    Given product "test-product" exists

  Scenario: Product cert is valid x509
    Then the certificate for "test-product" is valid

  Scenario: Product cert has correct product name
    Then the certificate for "test-product" has extension "name" with value "test-product"

