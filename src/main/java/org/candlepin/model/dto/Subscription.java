/*
 * Copyright (c) 2009 - 2023 Red Hat, Inc.
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
package org.candlepin.model.dto;

import org.candlepin.model.Cdn;
import org.candlepin.model.Eventful;
import org.candlepin.model.Named;
import org.candlepin.model.Owned;
import org.candlepin.model.Owner;
import org.candlepin.model.Product;
import org.candlepin.model.SubscriptionsCertificate;
import org.candlepin.service.model.SubscriptionInfo;

import com.fasterxml.jackson.annotation.JsonFilter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonProperty.Access;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Date;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;



/**
 * Represents a Subscription
 */
@XmlRootElement
@XmlAccessorType(XmlAccessType.PROPERTY)
@JsonFilter("DefaultFilter")
public class Subscription extends CandlepinDTO implements Owned, Named, Eventful, SubscriptionInfo {
    private static Logger log = LoggerFactory.getLogger(Subscription.class);

    private String id;
    private Owner owner;
    private ProductData product;
    private Long quantity;
    private Date startDate;
    private Date endDate;
    private String contractNumber;
    private String accountNumber;
    private Date modified;
    private String orderNumber;
    private String upstreamPoolId;
    private String upstreamEntitlementId;
    private String upstreamConsumerId;
    private SubscriptionsCertificate cert;
    private Cdn cdn;


    public Subscription() {
        // Intentionally left empty
    }

    public Subscription(Owner ownerIn, ProductData productIn, Long maxMembersIn, Date startDateIn,
        Date endDateIn, Date modified) {
        this.owner = ownerIn;
        this.product = productIn;
        this.quantity = maxMembersIn;
        this.startDate = startDateIn;
        this.endDate = endDateIn;
        this.modified = modified;
    }

    /**
     * Creates a new subscription DTO, copying data the given DTO
     *
     * @param source
     *  The source subscription DTO from which to copy data
     *
     * @throws IllegalArgumentException
     *  if pool is null
     */
    public Subscription(Subscription source) {
        if (source == null) {
            throw new IllegalArgumentException("source is null");
        }

        this.populate(source);
    }

    public String toString() {
        String subscription = "Subscription [id = " + getId();
        if (product != null) {
            subscription += ", product = " + getProduct().getId();
        }
        if (owner != null) {
            subscription += ", owner = " + getOwner().getKey();
        }
        return subscription + "]";
    }

    /**
     * @return the subscription id
     */
    public String getId() {
        return id;
    }

    /**
     * @param id subscription id
     *
     * @return
     *  a reference to this subscription instance
     */
    public Subscription setId(String id) {
        this.id = id;
        return this;
    }

    /**
     * @return then owner of the subscription.
     */
    public Owner getOwner() {
        return owner;
    }

    /**
     * @return the owner Id of this Consumer.
     */
    @Override
    public String getOwnerId() {
        return (owner == null) ? null : owner.getId();
    }

     /**
     * {@inheritDoc}
     */
    @Override
    public String getOwnerKey() {
        Owner owner = this.getOwner();
        return owner == null ? null : owner.getKey();
    }

    /**
     * @param owner The owner associated with the subscription.
     *
     * @return
     *  a reference to this subscription instance
     */
    public Subscription setOwner(Owner owner) {
        this.owner = owner;
        return this;
    }

    /**
     * @return the product associated with this subscription.
     */
    public ProductData getProduct() {
        return product;
    }

    /**
     * @param product The product associated with this subscription.
     *
     * @return
     *  a reference to this subscription instance
     */
    public Subscription setProduct(ProductData product) {
        this.product = product;
        return this;
    }

    @JsonProperty(access = Access.READ_ONLY)
    public Collection<ProductData> getProvidedProducts() {
        ProductData product = this.getProduct();
        return product != null ? product.getProvidedProducts() : null;
    }

    @JsonProperty(access = Access.READ_ONLY)
    public ProductData getDerivedProduct() {
        ProductData product = this.getProduct();
        return product != null ? product.getDerivedProduct() : null;
    }

    @JsonProperty(access = Access.READ_ONLY)
    public Collection<ProductData> getDerivedProvidedProducts() {
        ProductData derived = this.getDerivedProduct();
        return derived != null ? derived.getProvidedProducts() : null;
    }

    /**
     * @return quantity of this subscription.
     */
    public Long getQuantity() {
        return quantity;
    }

    /**
     * @param quantity number of allowed usage.
     *
     * @return
     *  a reference to this subscription instance
     */
    public Subscription setQuantity(Long quantity) {
        this.quantity = quantity;
        return this;
    }

    /**
     * @return when the subscription started.
     */
    public Date getStartDate() {
        return startDate;
    }

    /**
     * @param startDate when the subscription is to begin.
     *
     * @return
     *  a reference to this subscription instance
     */
    public Subscription setStartDate(Date startDate) {
        this.startDate = startDate;
        return this;
    }

    /**
     * @return when the subscription ends.
     */
    public Date getEndDate() {
        return endDate;
    }

    /**
     * @param endDate when the subscription ends.
     *
     * @return
     *  a reference to this subscription instance
     */
    public Subscription setEndDate(Date endDate) {
        this.endDate = endDate;
        return this;
    }

    /**
     * @return when the subscription was last changed.
     */
    public Date getModified() {
        return modified;
    }

    /**
     * @param modified when the subscription was changed.
     *
     * @return
     *  a reference to this subscription instance
     */
    public Subscription setModified(Date modified) {
        this.modified = modified;
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Date getLastModified() {
        return this.getModified();
    }

    /**
     *
     * @return the subscription's contract number
     */
    public String getContractNumber() {
        return contractNumber;
    }

    /**
     * set the contract number
     * @param contractNumber
     *
     * @return
     *  a reference to this subscription instance
     */
    public Subscription setContractNumber(String contractNumber) {
        this.contractNumber = contractNumber;
        return this;
    }

    /**
     * set the account number
     * @param accountNumber
     *
     * @return
     *  a reference to this subscription instance
     */
    public Subscription setAccountNumber(String accountNumber) {
        this.accountNumber = accountNumber;
        return this;
    }

    /**
     * @return the customer's account number
     */
    public String getAccountNumber() {
        return accountNumber;
    }

    /**
     * set the order number
     * @param orderNumber
     *
     * @return
     *  a reference to this subscription instance
     */
    public Subscription setOrderNumber(String orderNumber) {
        this.orderNumber = orderNumber;
        return this;
    }

    /**
     * @return the order number
     */
    public String getOrderNumber() {
        return orderNumber;
    }

    public Subscription setUpstreamPoolId(String upstreamPoolId) {
        this.upstreamPoolId = upstreamPoolId;
        return this;
    }

    public String getUpstreamPoolId() {
        return upstreamPoolId;
    }

    public Subscription setUpstreamEntitlementId(String upstreamEntitlementId) {
        this.upstreamEntitlementId = upstreamEntitlementId;
        return this;
    }

    public String getUpstreamEntitlementId() {
        return upstreamEntitlementId;
    }

    public Subscription setUpstreamConsumerId(String upstreamConsumerId) {
        this.upstreamConsumerId = upstreamConsumerId;
        return this;
    }

    public String getUpstreamConsumerId() {
        return upstreamConsumerId;
    }

    public SubscriptionsCertificate getCertificate() {
        return cert;
    }

    public Subscription setCertificate(SubscriptionsCertificate c) {
        cert = c;
        return this;
    }

    public Cdn getCdn() {
        return cdn;
    }

    public Subscription setCdn(Cdn cdn) {
        this.cdn = cdn;
        return this;
    }

    public boolean isStacked() {
        return this.product != null &&
            !StringUtils.isBlank(this.product.getAttributeValue(Product.Attributes.STACKING_ID));
    }

    public String getStackId() {
        // Check if we are stacked first so we return null over empty string
        // when stacking_id = "
        if (this.isStacked()) {
            return this.product.getAttributeValue(Product.Attributes.STACKING_ID);
        }

        return null;
    }

    public boolean createsSubPools() {
        String virtLimit = this.getProduct().getAttributeValue(Product.Attributes.VIRT_LIMIT);
        return !StringUtils.isBlank(virtLimit) && !"0".equals(virtLimit);
    }

    @JsonIgnore
    public String getName() {
        if (getProduct() != null) {
            return getProduct().getName();
        }

        return null;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null || !(obj instanceof Subscription)) {
            return false;
        }

        if (obj == this) {
            return true;
        }

        Subscription that = (Subscription) obj;

        EqualsBuilder builder = new EqualsBuilder()
            .append(this.id, that.id)
            .append(this.owner, that.owner)
            .append(this.product, that.product)
            .append(this.quantity, that.quantity)
            .append(this.startDate, that.startDate)
            .append(this.endDate, that.endDate)
            .append(this.contractNumber, that.contractNumber)
            .append(this.accountNumber, that.accountNumber)
            .append(this.modified, that.modified)
            .append(this.orderNumber, that.orderNumber)
            .append(this.upstreamPoolId, that.upstreamPoolId)
            .append(this.upstreamEntitlementId, that.upstreamEntitlementId)
            .append(this.upstreamConsumerId, that.upstreamConsumerId)
            .append(this.cert, that.cert)
            .append(this.cdn, that.cdn);

        return super.equals(obj) && builder.isEquals();
    }

    @Override
    public int hashCode() {
        HashCodeBuilder builder = new HashCodeBuilder(37, 7)
            .append(super.hashCode())
            .append(this.id)
            .append(this.owner)
            .append(this.product)
            .append(this.quantity)
            .append(this.startDate)
            .append(this.endDate)
            .append(this.contractNumber)
            .append(this.accountNumber)
            .append(this.modified)
            .append(this.orderNumber)
            .append(this.upstreamPoolId)
            .append(this.upstreamEntitlementId)
            .append(this.upstreamConsumerId)
            .append(this.cert)
            .append(this.cdn);

        return builder.toHashCode();
    }

    @Override
    public Object clone() {
        Subscription copy = (Subscription) super.clone();

        copy.product = this.product != null ? (ProductData) this.product.clone() : null;
        copy.startDate = this.startDate != null ? (Date) this.startDate.clone() : null;
        copy.endDate = this.endDate != null ? (Date) this.endDate.clone() : null;
        copy.modified = this.modified != null ? (Date) this.modified.clone() : null;

        return copy;
    }

    /**
     * Populates this DTO with the data from the given source DTO.
     *
     * @param source
     *  The source DTO from which to copy data
     *
     * @throws IllegalArgumentException
     *  if source is null
     *
     * @return
     *  a reference to this DTO
     */
    public Subscription populate(Subscription source) {
        if (source == null) {
            throw new IllegalArgumentException("source is null");
        }

        super.populate(source);

        this.setId(source.getId());
        this.setOwner(source.getOwner());
        this.setProduct(source.getProduct());
        this.setQuantity(source.getQuantity());
        this.setStartDate(source.getStartDate());
        this.setEndDate(source.getEndDate());
        this.setModified(source.getModified());
        this.setContractNumber(source.getContractNumber());
        this.setAccountNumber(source.getAccountNumber());
        this.setOrderNumber(source.getOrderNumber());
        this.setUpstreamPoolId(source.getUpstreamPoolId());
        this.setUpstreamEntitlementId(source.getUpstreamEntitlementId());
        this.setUpstreamConsumerId(source.getUpstreamConsumerId());
        this.setCertificate(source.getCertificate());
        this.setCdn(source.getCdn());

        return this;
    }
}
