/**
 * Copyright (c) 2009 - 2018 Red Hat, Inc.
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
package org.candlepin.service.model;

import java.util.Date;



/**
 * The SubscriptionInfo represents a minimal set of subscription information used by the
 * service adapters.
 *
 * Data which is not set or does not change should be represented by null values. To explicitly
 * clear a value, an empty string or non-null "empty" value should be used instead.
 */
public interface SubscriptionInfo {

    /**
     * Fetches the ID of this subscription. If the ID has not yet been set, this method returns
     * null.
     *
     * @return
     *  The ID of this subscription, or null if the ID has not been set
     */
    String getId();

    /**
     * Fetches the owner of this subscription. If the owner has not yet been set, this method
     * returns null.
     *
     * @return
     *  The owner of this subscription, or null if the owner has not been set
     */
    OwnerInfo getOwner();

    /**
     * Fetches the marketing product (SKU) for this subscription. If the marketing product has not
     * yet been set, this method returns null.
     *
     * @return
     *  The marketing product for this subscription, or null if the product has not been set
     */
    ProductInfo getProduct();

    /**
     * Fetches the derived marketing product (SKU) provided to guests of hosts consuming this
     * subscription. If the derived product has not been set, this method returns null.
     * <p></p>
     * <strong>Note:</strong> Due to the nature of this field, null is always treated as
     * "no value" rather than "no change."
     *
     * @return
     *  The derived marketing product for this subscription, or null if the derived product has not
     *  been set
     */
    ProductInfo getDerivedProduct();

    /**
     * Fetches the quantity of this subscription. If the quantity has not yet been set, this method
     * returns null.
     *
     * @return
     *  The quantity of this subscription, or null if the quantity has not been set
     */
    Long getQuantity();

    /**
     * Fetches the date this subscription becomes active. If the start date has not yet been set,
     * this method returns null.
     *
     * @return
     *  The start date of this subscription, or null if the start date has not been set
     */
    Date getStartDate();

    /**
     * Fetches the date this subscription becomes inactive. If the end date has not yet been set,
     * this method returns null.
     *
     * @return
     *  The end date of this subscription, or null if the end date has not been set
     */
    Date getEndDate();

    /**
     * Fetches the date of the last modification to this subscription. If the last modified date has
     * not yet been set, this method returns null.
     *
     * @return
     *  The last modified date of this subscription, or null if the last modified date has not been
     *  set
     */
    Date getLastModified();

    /**
     * Fetches the contract number for this subscription. If the contract number has not yet been
     * set, this method returns null.
     *
     * @return
     *  The contract number for this subscription, or null if the contract number has not been set
     */
    String getContractNumber();

    /**
     * Fetches the account number for this subscription. If the account number has not yet been set,
     * this method returns null.
     *
     * @return
     *  The account number for this subscription, or null if the account number has not been set
     */
    String getAccountNumber();

    /**
     * Fetches the order number for this subscription. If the order number has not yet been set,
     * this method returns null.
     *
     * @return
     *  The order number for this subscription, or null if the order number has not been set
     */
    String getOrderNumber();

    /**
     * Fetches the upstream pool ID of this subscription. If the upstream pool ID has not yet been
     * set, this method returns null.
     *
     * @return
     *  The upstream pool ID of this subscription, or null if the upstream pool ID has not been set
     */
    String getUpstreamPoolId();

    /**
     * Fetches the upstream entitlement ID of this subscription. If the upstream entitlement ID has
     * not yet been set, this method returns null.
     *
     * @return
     *  The upstream entitlement ID of this subscription, or null if the upstream entitlement ID has
     *  not been set
     */
    String getUpstreamEntitlementId();

    /**
     * Fetches the upstream consumer ID of this subscription. If the upstream consumer ID has not
     * yet been set, this method returns null.
     *
     * @return
     *  The upstream consumer ID of this subscription, or null if the upstream consumer ID has not
     *  been set
     */
    String getUpstreamConsumerId();

    /**
     * Fetches the CDN for this subscription. If the CDN has not been set, this method returns null.
     * <p></p>
     * <strong>Note:</strong> Due to the nature of this field, null is always treated as
     * "no value" rather than "no change."
     *
     * @return
     *  The CDN for this subscription, or null if the CDN has not been set
     */
    CdnInfo getCdn();

    /**
     * Fetches the certificate for this subscription. If the certificate has not been set, this
     * method returns null.
     * <p></p>
     * <strong>Note:</strong> Due to the nature of this field, null is always treated as
     * "no value" rather than "no change."
     *
     * @return
     *  The certificate for this subscription, or null if the certificate has not been set
     */
    CertificateInfo getCertificate();

}
