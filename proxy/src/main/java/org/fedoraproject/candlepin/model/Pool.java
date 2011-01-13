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
package org.fedoraproject.candlepin.model;

import java.util.Date;
import java.util.HashSet;
import java.util.Set;

import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;
import javax.persistence.Table;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;

import org.fedoraproject.candlepin.auth.interceptor.AccessControlValidator;
import org.fedoraproject.candlepin.util.DateSource;
import org.hibernate.annotations.Cascade;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.FilterDef;
import org.hibernate.annotations.FilterDefs;
import org.hibernate.annotations.Filters;
import org.hibernate.annotations.ForeignKey;
import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.ParamDef;

/**
 * Represents a pool of products eligible to be consumed (entitled).
 * For every Product there will be a corresponding Pool.
 */
@XmlRootElement(name = "pool")
@XmlAccessorType(XmlAccessType.PROPERTY)
@Entity
@FilterDefs({
    @FilterDef(
        name = "Pool_OWNER_FILTER", 
        parameters = @ParamDef(name = "owner_id", type = "string")
    ),
    @FilterDef(
        name = "Pool_CONSUMER_FILTER", 
        parameters = @ParamDef(name = "consumer_id", type = "string")
    )
})
@Filters({
    @Filter(name = "Pool_OWNER_FILTER", 
        condition = "id in (select p.id from cp_pool p where p.owner_id = :owner_id)"
    ),
    @Filter(name = "Pool_CONSUMER_FILTER", 
        condition = "id in (select p.id from cp_pool p " +
            "inner join cp_owner o on p.owner_id = o.id " +
            "inner join cp_consumer c on c.owner_id = o.id and c.id = :consumer_id " +
            "and (p.restrictedToUsername is null or p.restrictedToUsername = c.username))"
    )
})
@Table(name = "cp_pool")
public class Pool extends AbstractHibernateObject 
    implements AccessControlEnforced, Linkable {
    
    @Id
    @GeneratedValue(generator = "system-uuid")
    @GenericGenerator(name = "system-uuid", strategy = "uuid")
    @Column(length = 32)
    private String id;
    
    @ManyToOne
    @ForeignKey(name = "fk_pool_owner")
    @JoinColumn(nullable = false)
    private Owner owner;
    
    private Boolean activeSubscription = Boolean.TRUE;

    // An identifier for the subscription this pool is associated with. Note that
    // this is not a database foreign key. The subscription identified could exist
    // in another system only accessible to us as a service. Actual implementations
    // of our SubscriptionService will be used to use this data.
    @Column(nullable = true, unique = true)
    private String subscriptionId;

    /* Indicates this pool was created as a result of granting an entitlement.
     * Allows us to know that we need to clean this pool up if that entitlement
     * if ever revoked. */
    @ManyToOne
    @ForeignKey(name = "fk_pool_source_entitlement")
    @JoinColumn(nullable = true)
    private Entitlement sourceEntitlement;

    @Column(nullable = false)
    private Long quantity;

    @Column(nullable = false)
    private Long consumed;

    @Column(nullable = false)
    private Date startDate;
    
    @Column(nullable = false)
    private Date endDate;
    
    @Column(nullable = false)
    private String productId;
    
    @OneToMany(mappedBy = "pool", targetEntity = ProvidedProduct.class)
    @Cascade({org.hibernate.annotations.CascadeType.ALL,
        org.hibernate.annotations.CascadeType.MERGE,
        org.hibernate.annotations.CascadeType.DELETE_ORPHAN})
    private Set<ProvidedProduct> providedProducts = new HashSet<ProvidedProduct>();

    @OneToMany(cascade = CascadeType.ALL, mappedBy = "pool")
    @Cascade({org.hibernate.annotations.CascadeType.ALL, 
        org.hibernate.annotations.CascadeType.MERGE,
        org.hibernate.annotations.CascadeType.DELETE_ORPHAN})
    private Set<PoolAttribute> attributes = new HashSet<PoolAttribute>();

    @OneToMany(mappedBy = "pool", cascade = CascadeType.ALL)
    private Set<Entitlement> entitlements = new HashSet<Entitlement>();
    
    private String restrictedToUsername;

    private String contractNumber;
    private String accountNumber;
    
    // TODO: May not still be needed, iirc a temporary hack for client.
    private String productName;

    public Pool() {
    }

    public Pool(Owner ownerIn, String productId, Set<ProvidedProduct> providedProducts, 
        Long quantityIn, Date startDateIn, Date endDateIn, String contractNumber,
        String accountNumber) {
        this.productId = productId;
        this.owner = ownerIn;
        this.quantity = quantityIn;
        this.startDate = startDateIn;
        this.endDate = endDateIn;
        this.contractNumber = contractNumber;
        this.accountNumber = accountNumber;
    
        // Always assume none consumed if creating a new pool.
        this.consumed = 0L;

        this.providedProducts = providedProducts;
    }

    /** {@inheritDoc} */
    public String getId() {
        return id;
    }

    /**
     * @param id new db id.
     */
    public void setId(String id) {
        this.id = id;
    }

    /**
     * @return when the pool became active.
     */
    public Date getStartDate() {
        return startDate;
    }

    /**
     * @param startDate set the pool active date.
     */
    public void setStartDate(Date startDate) {
        this.startDate = startDate;
    }

    /**
     * @return when the pool expires.
     */
    public Date getEndDate() {
        return endDate;
    }

    /**
     * @param endDate set the pool expiration date.
     */
    public void setEndDate(Date endDate) {
        this.endDate = endDate;
    }

    /**
     * @return quantity
     */
    public Long getQuantity() {
        return quantity;
    }

    /**
     * @param quantity quantity
     */
    public void setQuantity(Long quantity) {
        this.quantity = quantity;
    }

    /**
     * @return quantity currently consumed.
     */
    public Long getConsumed() {
        return consumed;
    }

    /**
     * @param consumed set the activate uses.
     * TODO: is this really needed?
     */
    public void setConsumed(Long consumed) {
        this.consumed = consumed;
    }
    
    /**
     * @return owner of the pool.
     */
    public Owner getOwner() {
        return owner;
    }

    /**
     * @param owner changes the owner of the pool.
     */
    public void setOwner(Owner owner) {
        this.owner = owner;
    }

    /**
     * The Marketing/Operations product name for the
     * <code>productId</code>.
     * 
     * @return the productName
     */
    public String getProductName() {
        return productName;
    }

    /**
     * The Marketing/Operations product name for the
     * <code>productId</code>.
     * 
     * @param productName the productName to set
     */
    public void setProductName(String productName) {
        this.productName = productName;
    }

    /**
     * Return the contract for this pool's subscription.
     *
     * @return the contract number
     */
    public String getContractNumber() {
        return contractNumber;
    }

    /**
     * Set the contract number.
     * 
     * @param contractNumber
     */
    public void setContractNumber(String contractNumber) {
        this.contractNumber = contractNumber;
    }

    public String getAccountNumber() {
        return accountNumber;
    }

    public void setAccountNumber(String accountNumber) {
        this.accountNumber = accountNumber;
    }
    
    public void bumpConsumed(int quantity) {
        consumed += quantity;
    }

    public void dockConsumed(int quantity) {
        consumed -= quantity;
    }
    
    public static void dockConsumed(Entitlement e) {
        e.getPool().dockConsumed(e.getQuantity());
    }

    public Set<PoolAttribute> getAttributes() {
        if (attributes == null) {
            return new HashSet<PoolAttribute>();
        }
        return attributes;
    }

    public void setAttributes(Set<PoolAttribute> attributes) {
        this.attributes = attributes;
    }

    public void addAttribute(PoolAttribute attrib) {
        if (this.attributes == null) {
            this.attributes = new HashSet<PoolAttribute>();
        }
        attrib.setPool(this);
        this.attributes.add(attrib);
    }

    public void setAttribute(String key, String value) {
        PoolAttribute existing = getAttribute(key);
        if (existing != null) {
            existing.setValue(value);
        }
        else {
            PoolAttribute attr = new PoolAttribute(key, value);
            attr.setPool(this);
            addAttribute(attr);
        }
    }

    public PoolAttribute getAttribute(String key) {
        if (attributes == null) {
            return null;
        }
        for (PoolAttribute a : attributes) {
            if (a.getName().equals(key)) {
                return a;
            }
        }
        return null;
    }

    public String getAttributeValue(String key) {
        if (attributes == null) {
            return null;
        }
        for (PoolAttribute a : attributes) {
            if (a.getName().equals(key)) {
                return a.getValue();
            }
        }
        return null;
    }

    /**
     * returns true if the pool is considered expired based on the given date.
     * @param dateSource date to compare to.
     * @return true if the pool is considered expired based on the given date.
     */
    @XmlTransient
    public boolean isExpired(DateSource dateSource) {
        return getEndDate().before(dateSource.currentDate());
    }

    /**
     * Returns true if there are entitlements available in this pool, basically
     * if 'consumed' is less than the 'quantity'.
     * @return true if entitlements are available.
     */
    public boolean entitlementsAvailable(Integer quantityToConsume) {
        if (isUnlimited()) {
            return true;
        }

        if (getConsumed() + quantityToConsume.intValue() <= getQuantity()) {
            return true;
        }
        return false;
    }
    /**
     * @return True if entitlement pool is unlimited.
     */
    @XmlTransient
    public boolean isUnlimited() {
        if (this.getQuantity() < 0) {
            return true;
        }
        return false;
    }

    /**
     * @return source entitlement.
     */
    public Entitlement getSourceEntitlement() {
        return sourceEntitlement;
    }

    /**
     * @param sourceEntitlement source entitlement
     */
    public void setSourceEntitlement(Entitlement sourceEntitlement) {
        this.sourceEntitlement = sourceEntitlement;
    }

    /**
     * @return subscription id associated with this pool.
     */
    public String getSubscriptionId() {
        return subscriptionId;
    }

    /**
     * @param subscriptionId associates the given subscription.
     */
    public void setSubscriptionId(String subscriptionId) {
        this.subscriptionId = subscriptionId;
    }

    /**
     * @return true if this pool represents an active subscription.
     */
    public Boolean getActiveSubscription() {
        return activeSubscription;
    }

    /**
     * @param activeSubscription TODO
     */
    public void setActiveSubscription(Boolean activeSubscription) {
        this.activeSubscription = activeSubscription;
    }

    public String toString() {
        return "EntitlementPool [id = " + getId() + ", owner = " + owner.getId() +
            ", products = " + productId + " - " + getProvidedProducts() +
            ", sub = " + getSubscriptionId() +
            ", quantity = " + getQuantity() + ", expires = " + getEndDate() + "]";
    }
    
    @Override
    public boolean shouldGrantAccessTo(Owner owner) {
        return AccessControlValidator.shouldGrantAccess(this, owner);
    }
    
    @Override
    public boolean shouldGrantAccessTo(Consumer consumer) {
        return AccessControlValidator.shouldGrantAccess(this, consumer);
    }

    public Set<ProvidedProduct> getProvidedProducts() {
        return providedProducts;
    }

    public void addProvidedProduct(ProvidedProduct provided) {
        provided.setPool(this);
        providedProducts.add(provided);
    }

    public void setProvidedProducts(Set<ProvidedProduct> providedProducts) {
        this.providedProducts = providedProducts;
    }

    /**
     * Check if this pool provides the given product ID.
     * @param productId
     * @return true if pool provides this product
     */
    public Boolean provides(String productId) {
        // Direct match?
        if (this.productId.equals(productId)) {
            return true;
        }
        
        if (providedProducts != null) {
            for (ProvidedProduct p : providedProducts) {
                if (p.getProductId().equals(productId)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Return the "top level" product this pool is for.
     * Note that pools can also provide access to other products. 
     * See getProvidedProductIds().
     * @return Top level product ID.
     */
    public String getProductId() {
        return productId;
    }

    public void setProductId(String productId) {
        this.productId = productId;
    }
    
    /**
     * Gets the entitlements for this instance.
     *
     * @return The entitlements.
     */
    @XmlTransient
    public Set<Entitlement> getEntitlements() {
        return this.entitlements;
    }

    /**
     * Sets the entitlements for this instance.
     *
     * @param entitlements The entitlements.
     */
    public void setEntitlements(Set<Entitlement> entitlements) {
        this.entitlements = entitlements;
    }

    public String getRestrictedToUsername() {
        return restrictedToUsername;
    }

    public void setRestrictedToUsername(String restrictedToUsername) {
        this.restrictedToUsername = restrictedToUsername;
    }

    /**
     * Check whether {@link #consumed} is greater than {@link #quantity}
     * 
     * @return true if consumed>quantity else false.
     */
    @XmlTransient
    public boolean isOverflowing() {
        return this.consumed > this.quantity;
    }
    
    public String getHref() {
        return "/pools/" + getId();
    }
    
    @Override
    public void setHref(String href) {
        /*
         * No-op, here to aid with updating objects which have nested objects that were
         * originally sent down to the client in HATEOAS form.
         */
    }
}
