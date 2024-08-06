/*
 * Copyright (c) 2009 - 2024 Red Hat, Inc.
 *
 * This software is licensed to you under the GNU General Public License,
 * version 2 (GPLv2). There is NO WARRANTY for this software, express or
 * implied, including the implied warranties of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. You should have received a copy of GPLv2
 * along with this software; if not, see
 * http://www.gnu.org/licenses/old-licenses/gpl-2.0.txt.
 *
 * Red Hat trademarks are not licensed under GPLv2. No permission is
 * granted to use or replicate Red Hat trademarks that are incorporated
 * in this software or its documentation.
 */

package org.candlepin.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CloudIdentifierFactsTest {

    @Test
    public void testGetValue() {
        assertEquals("azure_offer", CloudIdentifierFacts.AZURE_OFFER.getValue());
        assertEquals("aws_marketplace_product_codes",
            CloudIdentifierFacts.AWS_MARKETPLACE_PRODUCT_CODES.getValue());
        assertEquals("aws_billing_products", CloudIdentifierFacts.AWS_BILLING_PRODUCTS.getValue());
        assertEquals("gcp_license_codes", CloudIdentifierFacts.GCP_LICENSE_CODES.getValue());
        assertEquals("aws_account_id", CloudIdentifierFacts.AWS_ACCOUNT_ID.getValue());
        assertEquals("azure_subscription_id", CloudIdentifierFacts.AZURE_SUBSCRIPTION_ID.getValue());
        assertEquals("gcp_project_id", CloudIdentifierFacts.GCP_PROJECT_ID.getValue());
        assertEquals("aws_instance_id", CloudIdentifierFacts.AWS_INSTANCE_ID.getValue());
        assertEquals("azure_instance_id", CloudIdentifierFacts.AZURE_INSTANCE_ID.getValue());
        assertEquals("gcp_instance_id", CloudIdentifierFacts.GCP_INSTANCE_ID.getValue());
    }

    @Test
    public void testExtractCloudOfferingIds() {
        Map<String, String> facts = new HashMap<>();
        facts.put("azure_offer", "offer1");
        facts.put("aws_marketplace_product_codes", "product1");
        facts.put("aws_billing_products", "billing1");
        facts.put("gcp_license_codes", "license1");

        List<String> offeringIds = CloudIdentifierFacts.extractCloudOfferingIds(facts);

        assertEquals(4, offeringIds.size());
        assertTrue(offeringIds.contains("offer1"));
        assertTrue(offeringIds.contains("product1"));
        assertTrue(offeringIds.contains("billing1"));
        assertTrue(offeringIds.contains("license1"));
    }

    @Test
    public void testExtractCloudAccountId() {
        Map<String, String> facts = new HashMap<>();
        facts.put("aws_account_id", "aws_account_123");

        String accountId = CloudIdentifierFacts.extractCloudAccountId(facts);
        assertEquals("aws_account_123", accountId);

        facts.clear();
        facts.put("azure_subscription_id", "azure_subscription_123");

        accountId = CloudIdentifierFacts.extractCloudAccountId(facts);
        assertEquals("azure_subscription_123", accountId);

        facts.clear();
        facts.put("gcp_project_id", "gcp_project_123");

        accountId = CloudIdentifierFacts.extractCloudAccountId(facts);
        assertEquals("gcp_project_123", accountId);

        facts.clear();
        accountId = CloudIdentifierFacts.extractCloudAccountId(facts);
        assertNull(accountId);
    }

    @Test
    public void testExtractCloudInstanceId() {
        Map<String, String> facts = new HashMap<>();
        facts.put("aws_instance_id", "aws_instance_123");

        String instanceId = CloudIdentifierFacts.extractCloudInstanceId(facts);
        assertEquals("aws_instance_123", instanceId);

        facts.clear();
        facts.put("azure_instance_id", "azure_instance_123");

        instanceId = CloudIdentifierFacts.extractCloudInstanceId(facts);
        assertEquals("azure_instance_123", instanceId);

        facts.clear();
        facts.put("gcp_instance_id", "gcp_instance_123");

        instanceId = CloudIdentifierFacts.extractCloudInstanceId(facts);
        assertEquals("gcp_instance_123", instanceId);

        facts.clear();
        instanceId = CloudIdentifierFacts.extractCloudInstanceId(facts);
        assertNull(instanceId);
    }

    @Test
    public void testExtractCloudProviderShortName() {
        Map<String, String> facts = new HashMap<>();
        facts.put("aws_instance_id", "aws_instance_123");

        String provider = CloudIdentifierFacts.extractCloudProviderShortName(facts);
        assertEquals("AWS", provider);

        facts.clear();
        facts.put("azure_instance_id", "azure_instance_123");

        provider = CloudIdentifierFacts.extractCloudProviderShortName(facts);
        assertEquals("AZURE", provider);

        facts.clear();
        facts.put("gcp_instance_id", "gcp_instance_123");

        provider = CloudIdentifierFacts.extractCloudProviderShortName(facts);
        assertEquals("GCP", provider);

        facts.clear();
        provider = CloudIdentifierFacts.extractCloudProviderShortName(facts);
        assertNull(provider);
    }

    @Test
    public void testExtractCloudProviderShortNameMultipleProviders() {
        Map<String, String> facts = new HashMap<>();
        facts.put("aws_account_id", "aws123");
        facts.put("azure_subscription_id", "azure123");

        assertThrows(IllegalArgumentException.class, () -> {
            CloudIdentifierFacts.extractCloudProviderShortName(facts);
        });
    }
}
