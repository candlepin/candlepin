Feature: Export of ConsumerType, Consumer, Products, and Entitlements
  As a consumer
  I want to be able to export my products and entitlements
  
  Background: 
    Given an owner admin "test_owner"
    And I am logged in as "test_owner"
    And product "virtualization_host" exists
    And product "monitoring" exists
    And test owner has 2 entitlements for "virtualization_host"
    And test owner has 4 entitlements for "monitoring"
    
  Scenario: A Consumer can export data
    Given I am a consumer "random_box"
    And I Consume an Entitlement for the "virtualization_host" Product
    When I perform export
    Then I get an archived extract of data
    