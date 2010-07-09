Feature: Generate a Certificate Revocation List
    In order to protect the value of a subscription and give proper access to our content
    As a content provider
    I want to be able to generate a certificate revocation list (CRL) so that only consumers with valid entitlement certificates can access content

    Background:
        Given an owner admin "test_owner"
        And I am logged in as "test_owner"
        And product "virt" exists
        And product "monitoring" exists
        And test owner has 3 entitlements for "virt"
        And test owner has 6 entitlements for "monitoring"
        And I am a consumer "system5"

    Scenario: A single revoked entitlement is present
        When I consume an entitlement for the "monitoring" product
        And I filter certificates on the serial number for "monitoring"
        And I revoke all my entitlements
        Then the filtered certificates are in the CRL

    Scenario: A single valid entitlement is not in the CRL
        When I consume an entitlement for the "virt" product
        And I filter certificates on the serial number for "virt"
        Then the filtered certificates are not in the CRL

    Scenario: Mix of revoked and valid entitlements: revoked
        When I consume an entitlement for the "virt" product
        And I consume an entitlement for the "monitoring" product
        And I filter certificates on the serial number for "virt"
        And I unbind my "virt" entitlement
        Then the filtered certificates are in the CRL

    Scenario: Mix of revoked and valid entitlements: valid
        When I consume an entitlement for the "virt" product
        And I consume an entitlement for the "monitoring" product
        And I filter certificates on the serial number for "monitoring"
        And I unbind my "virt" entitlement
        Then the filtered certificates are not in the CRL
