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
}
