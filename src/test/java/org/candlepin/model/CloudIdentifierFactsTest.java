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

import org.junit.jupiter.api.Test;

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
}
