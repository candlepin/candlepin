/**
 * Copyright (c) 2009 Red Hat, Inc.
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
package org.candlepin.client.model;

import java.util.Date;
import java.util.Set;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;

import org.codehaus.jackson.annotate.JsonIgnore;

/**
 * Simple Pool Model.
 */
@XmlRootElement
@XmlAccessorType(XmlAccessType.PROPERTY)
public final class Pool extends TimeStampedEntity {

    /** The id. */
    private Long id;

    /** The product name. */
    private String productName;

    /** The product id. */
    private String productId;

    /** The quantity. */
    private Long quantity;

    /** The consumed. */
    private Long consumed;

    /** The start date. */
    private Date startDate;

    /** The end date. */
    private Date endDate;

    /** The attributes. */
    private Set<Attribute> attributes;

    /** The active. */
    private boolean active;

    /** The subscription id. */
    private Long subscriptionId;

    /** The source entitlement. */
    private Entitlement sourceEntitlement;

    /** The active subscription. */
    private boolean activeSubscription;

    /**
     * Gets the id.
     *
     * @return the id
     */
    public Long getId() {
        return id;
    }

    /**
     * Sets the id.
     *
     * @param id the new id
     */
    public void setId(Long id) {
        this.id = id;
    }

    /**
     * Gets the product name.
     *
     * @return the product name
     */
    public String getProductName() {
        return productName;
    }

    /**
     * Sets the product name.
     *
     * @param productName the new product name
     */
    public void setProductName(String productName) {
        this.productName = productName;
    }

    /**
     * Gets the product id.
     *
     * @return the product id
     */
    public String getProductId() {
        return productId;
    }

    /**
     * Sets the product id.
     *
     * @param productId the new product id
     */
    public void setProductId(String productId) {
        this.productId = productId;
    }

    /**
     * Gets the quantity.
     *
     * @return the quantity
     */
    public Long getQuantity() {
        return quantity;
    }

    /**
     * Sets the quantity.
     *
     * @param quantity the new quantity
     */
    public void setQuantity(Long quantity) {
        this.quantity = quantity;
    }

    /**
     * Gets the consumed.
     *
     * @return the consumed
     */
    public Long getConsumed() {
        return consumed;
    }

    /**
     * Sets the consumed.
     *
     * @param consumed the new consumed
     */
    public void setConsumed(Long consumed) {
        this.consumed = consumed;
    }

    /**
     * Gets the start date.
     *
     * @return the start date
     */
    public Date getStartDate() {
        return startDate;
    }

    /**
     * Sets the start date.
     *
     * @param startDate the new start date
     */
    public void setStartDate(Date startDate) {
        this.startDate = startDate;
    }

    /**
     * Gets the end date.
     *
     * @return the end date
     */
    public Date getEndDate() {
        return endDate;
    }

    /**
     * Sets the end date.
     *
     * @param endDate the new end date
     */
    public void setEndDate(Date endDate) {
        this.endDate = endDate;
    }

    /**
     * Gets the attributes.
     *
     * @return the attributes
     */
    public Set<Attribute> getAttributes() {
        return attributes;
    }

    /**
     * Sets the attributes.
     *
     * @param attributes the new attributes
     */
    public void setAttributes(Set<Attribute> attributes) {
        this.attributes = attributes;
    }

    /**
     * Checks if is active.
     *
     * @return true, if is active
     */
    public boolean isActive() {
        return active;
    }

    /**
     * Sets the active.
     *
     * @param active the new active
     */
    public void setActive(boolean active) {
        this.active = active;
    }

    /**
     * Gets the subscription id.
     *
     * @return the subscription id
     */
    public Long getSubscriptionId() {
        return subscriptionId;
    }

    /**
     * Sets the subscription id.
     *
     * @param subscriptionId the new subscription id
     */
    public void setSubscriptionId(Long subscriptionId) {
        this.subscriptionId = subscriptionId;
    }

    /**
     * Sets the unlimited.
     *
     * @param value the new unlimited
     */
    @JsonIgnore
    public void setUnlimited(boolean value) {
    }

    /**
     * Gets the source entitlement.
     *
     * @return the source entitlement
     */
    public Entitlement getSourceEntitlement() {
        return sourceEntitlement;
    }

    /**
     * Sets the source entitlement.
     *
     * @param sourceEntitlement the new source entitlement
     */
    public void setSourceEntitlement(Entitlement sourceEntitlement) {
        this.sourceEntitlement = sourceEntitlement;
    }

    /**
     * Checks if is active subscription.
     *
     * @return true, if is active subscription
     */
    public boolean isActiveSubscription() {
        return activeSubscription;
    }

    /**
     * Sets the active subscription.
     *
     * @param activeSubscription the new active subscription
     */
    public void setActiveSubscription(boolean activeSubscription) {
        this.activeSubscription = activeSubscription;
    }

}
