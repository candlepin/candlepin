/**
 * Copyright (c) 2009 - 2012 Red Hat, Inc.
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

import org.candlepin.audit.Eventful;
import org.candlepin.model.Branding;
import org.candlepin.model.Cdn;
import org.candlepin.model.Named;
import org.candlepin.model.Owned;
import org.candlepin.model.Owner;
import org.candlepin.model.Product;
import org.candlepin.model.SubscriptionsCertificate;

import com.fasterxml.jackson.annotation.JsonFilter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import org.apache.commons.lang.StringUtils;

import java.util.Date;
import java.util.HashSet;
import java.util.Set;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;



/**
 * Represents a Subscription
 */
@XmlRootElement
@XmlAccessorType(XmlAccessType.PROPERTY)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonFilter("DefaultFilter")
public class Subscription implements Owned, Named, Eventful {

    private String id;
    private Date created;
    private Date updated;
    private Owner owner;
    private Product product;
    private Product derivedProduct;
    private Set<Product> providedProducts = new HashSet<Product>();
    private Set<Product> derivedProvidedProducts = new HashSet<Product>();
    private Set<Branding> branding = new HashSet<Branding>();
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
    }

    public Subscription(Owner ownerIn, Product productIn, Set<Product> providedProducts,
            Long maxMembersIn, Date startDateIn, Date endDateIn, Date modified) {
        this.owner = ownerIn;
        this.product = productIn;
        this.providedProducts = providedProducts;
        this.quantity = maxMembersIn;
        this.startDate = startDateIn;
        this.endDate = endDateIn;
        this.modified = modified;
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
     */
    public void setId(String id) {
        this.id = id;
    }

    /**
     * @return then owner of the subscription.
     */
    public Owner getOwner() {
        return owner;
    }

    /**
     * @param owner The owner associated with the subscription.
     */
    public void setOwner(Owner owner) {
        this.owner = owner;
    }

    /**
     * @return the product associated with this subscription.
     */
    public Product getProduct() {
        return product;
    }

    /**
     * @param product The product associated with this subscription.
     */
    public void setProduct(Product product) {
        this.product = product;
    }

    /**
     * @return quantity of this subscription.
     */
    public Long getQuantity() {
        return quantity;
    }

    /**
     * @param quantity number of allowed usage.
     */
    public void setQuantity(Long quantity) {
        this.quantity = quantity;
    }

    /**
     * @return when the subscription started.
     */
    public Date getStartDate() {
        return startDate;
    }

    /**
     * @param startDate when the subscription is to begin.
     */
    public void setStartDate(Date startDate) {
        this.startDate = startDate;
    }

    /**
     * @return when the subscription ends.
     */
    public Date getEndDate() {
        return endDate;
    }

    /**
     * @param endDate when the subscription ends.
     */
    public void setEndDate(Date endDate) {
        this.endDate = endDate;
    }

    /**
     * @return when the subscription was last changed.
     */
    public Date getModified() {
        return modified;
    }

    /**
     * @param modified when the subscription was changed.
     */
    public void setModified(Date modified) {
        this.modified = modified;
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
     */
    public void setContractNumber(String contractNumber) {
        this.contractNumber = contractNumber;
    }

    /**
     * set the account number
     * @param accountNumber
     */
    public void setAccountNumber(String accountNumber) {
        this.accountNumber = accountNumber;
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
     */
    public void setOrderNumber(String orderNumber) {
        this.orderNumber = orderNumber;
    }

    /**
     * @return the order number
     */
    public String getOrderNumber() {
        return orderNumber;
    }

    /**
     * Check if this pool provides the given product ID.
     * @param desiredProductId
     * @return true if subscription provides product
     */
    public Boolean provides(String desiredProductId) {
        // Direct match?
        if (this.product.getUuid().equals(desiredProductId)) {
            return true;
        }

        // Check provided products:
        for (Product p : providedProducts) {
            if (p.getUuid().equals(desiredProductId)) {
                return true;
            }
        }
        return false;
    }

    public Set<Product> getProvidedProducts() {
        return providedProducts;
    }

    public void setProvidedProducts(Set<Product> providedProducts) {
        this.providedProducts.clear();

        if (providedProducts != null) {
            this.providedProducts.addAll(providedProducts);
        }
    }

    public void setUpstreamPoolId(String upstreamPoolId) {
        this.upstreamPoolId = upstreamPoolId;
    }

    public String getUpstreamPoolId() {
        return upstreamPoolId;
    }

    public void setUpstreamEntitlementId(String upstreamEntitlementId) {
        this.upstreamEntitlementId = upstreamEntitlementId;
    }

    public String getUpstreamEntitlementId() {
        return upstreamEntitlementId;
    }

    public void setUpstreamConsumerId(String upstreamConsumerId) {
        this.upstreamConsumerId = upstreamConsumerId;
    }

    public String getUpstreamConsumerId() {
        return upstreamConsumerId;
    }

    public SubscriptionsCertificate getCertificate() {
        return cert;
    }

    public void setCertificate(SubscriptionsCertificate c) {
        cert = c;
    }

    public Product getDerivedProduct() {
        return derivedProduct;
    }

    public void setDerivedProduct(Product subProduct) {
        this.derivedProduct = subProduct;
    }

    public Set<Product> getDerivedProvidedProducts() {
        return derivedProvidedProducts;
    }

    public void setDerivedProvidedProducts(Set<Product> subProvidedProducts) {
        this.derivedProvidedProducts.clear();

        if (subProvidedProducts != null) {
            this.derivedProvidedProducts.addAll(subProvidedProducts);
        }
    }

    public Cdn getCdn() {
        return cdn;
    }

    public void setCdn(Cdn cdn) {
        this.cdn = cdn;
    }

    public Set<Branding> getBranding() {
        return branding;
    }

    public void setBranding(Set<Branding> branding) {
        this.branding = branding;
    }

    public boolean isStacked() {
        return !StringUtils.isBlank(this.product.getAttributeValue("stacking_id"));
    }

    public String getStackId() {
        // Check if we are stacked first so we return null over empty string
        // when stacking_id = ""
        if (this.isStacked()) {
            return this.product.getAttributeValue("stacking_id");
        }
        return null;
    }

    public boolean createsSubPools() {
        String virtLimit = this.getProduct().getAttributeValue("virt_limit");
        return !StringUtils.isBlank(virtLimit) && !"0".equals(virtLimit);
    }

    @XmlTransient
    public String getName() {
        if (getProduct() != null) {
            return getProduct().getName();
        }
        return null;
    }

    @XmlElement
    public Date getCreated() {
        return created;
    }

    public void setCreated(Date created) {
        this.created = created;
    }

    @XmlElement
    public Date getUpdated() {
        return updated;
    }

    public void setUpdated(Date updated) {
        this.updated = updated;
    }
}
