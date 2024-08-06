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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public enum CloudIdentifierFacts {

    // Cloud Offering IDs
    AZURE_OFFER("azure_offer"),
    AWS_MARKETPLACE_PRODUCT_CODES("aws_marketplace_product_codes"),
    AWS_BILLING_PRODUCTS("aws_billing_products"),
    GCP_LICENSE_CODES("gcp_license_codes"),

    // Cloud Account IDs
    AWS_ACCOUNT_ID("aws_account_id"),
    AZURE_SUBSCRIPTION_ID("azure_subscription_id"),
    GCP_PROJECT_ID("gcp_project_id"),

    // Instance IDs
    AWS_INSTANCE_ID("aws_instance_id"),
    AZURE_INSTANCE_ID("azure_instance_id"),
    GCP_INSTANCE_ID("gcp_instance_id");

    public static final String AWS_SHORT_NAME = "AWS";
    public static final String AZURE_SHORT_NAME = "AZURE";
    public static final String GCP_SHORT_NAME = "GCP";

    private final String value;

    CloudIdentifierFacts(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    /**
     * Extracts the cloud offering IDs from the provided map of facts.
     *
     * @param facts
     *      a map containing various key-value pairs representing facts about the consumer
     *
     * @return a list of cloud offering IDs if found, otherwise an empty list
     */
    public static List<String> extractCloudOfferingIds(Map<String, String> facts) {
        List<String> offeringIds = new ArrayList<>();
        addValue(offeringIds, facts, AZURE_OFFER);
        addValue(offeringIds, facts, AWS_MARKETPLACE_PRODUCT_CODES);
        addValue(offeringIds, facts, AWS_BILLING_PRODUCTS);
        addValue(offeringIds, facts, GCP_LICENSE_CODES);
        return offeringIds;
    }

    private static void addValue(
        List<String> list, Map<String, String> facts, CloudIdentifierFacts identifier) {
        String key = identifier.getValue();
        if (facts.containsKey(key)) {
            list.add(facts.get(key));
        }
    }

    /**
     * Extracts the cloud account ID from the provided map of facts.
     *
     * @param facts
     *      a map containing various key-value pairs representing facts about the consumer
     *
     * @return the account ID of the cloud account if found, otherwise null
     */
    public static String extractCloudAccountId(Map<String, String> facts) {
        if (facts.containsKey(AWS_ACCOUNT_ID.getValue())) {
            return facts.get(AWS_ACCOUNT_ID.getValue());
        }
        if (facts.containsKey(AZURE_SUBSCRIPTION_ID.getValue())) {
            return facts.get(AZURE_SUBSCRIPTION_ID.getValue());
        }
        if (facts.containsKey(GCP_PROJECT_ID.getValue())) {
            return facts.get(GCP_PROJECT_ID.getValue());
        }
        return null;
    }

    /**
     * Extracts the cloud instance ID from the provided map of facts.
     *
     * @param facts
     *      a map containing various key-value pairs representing facts about the consumer
     *
     * @return the instance ID of the cloud instance if found, otherwise null
     */
    public static String extractCloudInstanceId(Map<String, String> facts) {
        if (facts.containsKey(AWS_INSTANCE_ID.getValue())) {
            return facts.get(AWS_INSTANCE_ID.getValue());
        }
        if (facts.containsKey(AZURE_INSTANCE_ID.getValue())) {
            return facts.get(AZURE_INSTANCE_ID.getValue());
        }
        if (facts.containsKey(GCP_INSTANCE_ID.getValue())) {
            return facts.get(GCP_INSTANCE_ID.getValue());
        }
        return null;
    }

    /**
     * Determines the cloud provider (AWS, Azure, or GCP) based on the provided map of facts.
     *
     * @param facts
     *      a map containing various key-value pairs representing facts about the consumer
     *
     * @return a short name representing the detected cloud provider ("AWS", "AZURE", or "GCP"),
     *         or null if no cloud provider facts are found
     *
     * @throws IllegalStateException if multiple cloud provider facts are detected
     */
    public static String extractCloudProviderShortName(Map<String, String> facts) {
        boolean hasAWS = facts.containsKey(AWS_ACCOUNT_ID.getValue()) ||
            facts.containsKey(AWS_INSTANCE_ID.getValue()) ||
            facts.containsKey(AWS_MARKETPLACE_PRODUCT_CODES.getValue()) ||
            facts.containsKey(AWS_BILLING_PRODUCTS.getValue());
        boolean hasAzure = facts.containsKey(AZURE_SUBSCRIPTION_ID.getValue()) ||
            facts.containsKey(AZURE_INSTANCE_ID.getValue()) || facts.containsKey(AZURE_OFFER.getValue());
        boolean hasGCP = facts.containsKey(GCP_PROJECT_ID.getValue()) ||
            facts.containsKey(GCP_INSTANCE_ID.getValue()) || facts.containsKey(GCP_LICENSE_CODES.getValue());

        if ((hasAWS && hasAzure) || (hasAWS && hasGCP) || (hasAzure && hasGCP)) {
            throw new IllegalArgumentException("Facts from multiple different cloud providers were found.");
        }
        if (hasAWS) {
            return AWS_SHORT_NAME;
        }
        if (hasAzure) {
            return AZURE_SHORT_NAME;
        }
        if (hasGCP) {
            return GCP_SHORT_NAME;
        }
        return null;
    }
}
