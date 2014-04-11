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
package org.candlepin.model;

import java.util.Date;
import java.util.HashSet;
import java.util.Set;

import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.JoinTable;
import javax.persistence.ManyToMany;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;
import javax.persistence.OneToOne;
import javax.persistence.Table;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;

import org.hibernate.annotations.Cascade;
import org.hibernate.annotations.ForeignKey;
import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.Index;

/**
 * Represents a Subscription
 */
@XmlRootElement
@XmlAccessorType(XmlAccessType.PROPERTY)
@Entity
@Table(name = "cp_subscription")
public class Subscription extends AbstractHibernateObject {

    @Id
    @GeneratedValue(generator = "system-uuid")
    @GenericGenerator(name = "system-uuid", strategy = "uuid")
    @Column(length = 32)
    @NotNull
    private String id;

    @ManyToOne
    @ForeignKey(name = "fk_subscription_owner")
    @JoinColumn(nullable = false)
    @Index(name = "cp_subscription_owner_fk_idx")
    @NotNull
    private Owner owner;

    @ManyToOne
    @ForeignKey(name = "fk_subscription_product")
    @JoinColumn(nullable = false)
    @NotNull
    private Product product;

    @ManyToOne
    @ForeignKey(name = "fk_sub_derivedprod")
    @JoinColumn(nullable = true)
    private Product derivedProduct;

    @ManyToMany(targetEntity = Product.class)
    @ForeignKey(name = "fk_subscription_id",
            inverseName = "fk_product_id")
    @JoinTable(name = "cp_subscription_products",
        joinColumns = @JoinColumn(name = "subscription_id"),
        inverseJoinColumns = @JoinColumn(name = "product_id"))
    private Set<Product> providedProducts = new HashSet<Product>();

    @ManyToMany(targetEntity = Product.class)
    @ForeignKey(name = "fk_product_id",
            inverseName = "fk_subscription_id")
    @JoinTable(name = "cp_sub_derivedprods",
        joinColumns = @JoinColumn(name = "subscription_id"),
        inverseJoinColumns = @JoinColumn(name = "product_id"))
    private Set<Product> derivedProvidedProducts = new HashSet<Product>();

    @OneToMany
    @ForeignKey(name = "fk_sub_branding_branding_id",
            inverseName = "fk_sub_branding_sub_id")
    @JoinTable(name = "cp_sub_branding",
        joinColumns = @JoinColumn(name = "subscription_id"),
        inverseJoinColumns = @JoinColumn(name = "branding_id"))
    @Cascade({org.hibernate.annotations.CascadeType.ALL,
        org.hibernate.annotations.CascadeType.DELETE_ORPHAN})
    private Set<Branding> branding = new HashSet<Branding>();

    @Column(nullable = false)
    @NotNull
    private Long quantity;

    @Column(nullable = false)
    @NotNull
    private Date startDate;

    @Column(nullable = false)
    @NotNull
    private Date endDate;

    @Size(max = 255)
    private String contractNumber;

    @Size(max = 255)
    private String accountNumber;

    private Date modified;

    @Size(max = 255)
    private String orderNumber;

    @Column(name = "upstream_pool_id")
    @Size(max = 255)
    private String upstreamPoolId;

    @Column(name = "upstream_entitlement_id")
    @Size(max = 37)
    private String upstreamEntitlementId;

    @Column(name = "upstream_consumer_id")
    @Size(max = 255)
    private String upstreamConsumerId;

    @OneToOne(cascade = CascadeType.ALL)
    @JoinColumn(name = "certificate_id")
    private SubscriptionsCertificate cert;

    @OneToOne
    @JoinColumn(name = "cdn_id")
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
        return "Subscription [id = " + getId() + ", product = " + getProduct().getId() +
            ", quantity = " + getQuantity() + ", expires = " + getEndDate() + "]";
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
        if (this.product.getId().equals(desiredProductId)) {
            return true;
        }

        // Check provided products:
        for (Product p : providedProducts) {
            if (p.getId().equals(desiredProductId)) {
                return true;
            }
        }
        return false;
    }

    public Set<Product> getProvidedProducts() {
        return providedProducts;
    }

    public void setProvidedProducts(Set<Product> providedProducts) {
        this.providedProducts = providedProducts;
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
        this.derivedProvidedProducts = subProvidedProducts;
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


}
