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

import org.candlepin.jackson.HateoasInclude;
import org.candlepin.util.DateSource;

import com.fasterxml.jackson.annotation.JsonFilter;

import org.hibernate.annotations.Cascade;
import org.hibernate.annotations.ForeignKey;
import org.hibernate.annotations.Formula;
import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.Index;
import org.hibernate.annotations.LazyCollection;
import org.hibernate.annotations.LazyCollectionOption;
import org.hibernate.annotations.Where;

import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
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
import javax.persistence.Transient;
import javax.persistence.UniqueConstraint;
import javax.persistence.Version;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;

/**
 * Represents a pool of products eligible to be consumed (entitled).
 * For every Product there will be a corresponding Pool.
 */
@XmlRootElement(name = "pool")
@XmlAccessorType(XmlAccessType.PROPERTY)
@Entity
@Table(name = "cp_pool", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"subscriptionid", "subscriptionsubkey"})})
@JsonFilter("PoolFilter")
public class Pool extends AbstractHibernateObject implements Persisted, Owned {

    /**
     * PoolType
     *
     * Pools can have be of several major types which can radically alter how they
     * behave.
     *
     * NORMAL - A regular pool. Usually created 1-1 with a subscription.
     *
     * ENTITLEMENT_DERIVED - A pool created as the result of a consumer's use of an
     * entitlement. Will be cleaned up when the source entitlement is revoked.
     *
     * STACK_DERIVED - A pool created as a result of the consumer's use of a stack of
     * entitlements. Will be cleaned up when the last entitlement in the stack is revoked.
     * This type of pool can have certain fields change as a result of adding or removing
     * entitlements to the stack.
     *
     * BONUS - A virt-only pool created only in hosted environments when a subscription
     * has a virt_limit attribute but no host_limited attribute.
     */
    public enum PoolType {
        NORMAL,
        ENTITLEMENT_DERIVED,
        STACK_DERIVED,
        BONUS
    }

    @Id
    @GeneratedValue(generator = "system-uuid")
    @GenericGenerator(name = "system-uuid", strategy = "uuid")
    @Column(length = 32)
    private String id;

    @ManyToOne
    @ForeignKey(name = "fk_pool_owner")
    @JoinColumn(nullable = false)
    @Index(name = "cp_pool_owner_fk_idx")
    private Owner owner;

    private Boolean activeSubscription = Boolean.TRUE;

    // An identifier for the subscription this pool is associated with. Note
    // that this is not a database foreign key. The subscription identified
    // could exist in another system only accessible to us as a service.
    // Actual implementations of our SubscriptionService will be used to use
    // this data.
    @Column(nullable = true)
    private String subscriptionId;

    // since one subscription can create multiple pools, we need to use a
    // combination of subid/some other key to uniquely identify a pool.
    // subscriptionSubKey is set in the js rules, according to the same logic
    // that will create more than one pool per sub.
    @Column(nullable = true)
    private String subscriptionSubKey;

    /**
     * Signifies that this pool is a derived pool linked to this stack (only one
     * sub pool per stack allowed)
     */
    @Column(nullable = true)
    private String sourceStackId;

    /** Indicates this pool was created as a result of granting an entitlement.
     * Allows us to know that we need to clean this pool up if that entitlement
     * if ever revoked. */
    @ManyToOne
    @ForeignKey(name = "fk_pool_source_entitlement")
    @JoinColumn(nullable = true)
    @Index(name = "cp_pool_entitlement_fk_idx")
    private Entitlement sourceEntitlement;

    /**
     * Derived pools belong to a consumer who owns the entitlement(s) which created them.
     * In cases where a pool is linked to a stack of entitlements, the consumer is only
     * loosely linked in the database, so instead we will link directly for any derived
     * pool.
     */
    @ManyToOne
    @JoinColumn(nullable = true)
    private Consumer sourceConsumer;

    @Column(nullable = false)
    private Long quantity;

    @Column(nullable = false)
    private Date startDate;

    @Column(nullable = false)
    private Date endDate;

    @Column(nullable = false)
    private String productId;

    @Column
    private String derivedProductId;

    @OneToMany(targetEntity = ProvidedProduct.class)
    @Cascade({org.hibernate.annotations.CascadeType.ALL,
        org.hibernate.annotations.CascadeType.DELETE_ORPHAN})
    @JoinColumn(name = "pool_id", insertable = false, updatable = false)
    @Where(clause = "dtype='provided'")
    private Set<ProvidedProduct> providedProducts = new HashSet<ProvidedProduct>();

    @OneToMany(targetEntity = DerivedProvidedProduct.class)
    @Cascade({org.hibernate.annotations.CascadeType.ALL,
        org.hibernate.annotations.CascadeType.DELETE_ORPHAN})
    @JoinColumn(name = "pool_id", insertable = false, updatable = false)
    @Where(clause = "dtype='derived'")
    private Set<DerivedProvidedProduct> derivedProvidedProducts =
        new HashSet<DerivedProvidedProduct>();

    @OneToMany(mappedBy = "pool")
    @Cascade({org.hibernate.annotations.CascadeType.ALL,
        org.hibernate.annotations.CascadeType.DELETE_ORPHAN})
    private Set<PoolAttribute> attributes = new HashSet<PoolAttribute>();

    @OneToMany
    @Cascade({org.hibernate.annotations.CascadeType.ALL,
        org.hibernate.annotations.CascadeType.DELETE_ORPHAN})
    @JoinColumn(name = "pool_id", insertable = false, updatable = false)
    @Where(clause = "dtype='product'")
    private Set<ProductPoolAttribute> productAttributes =
        new HashSet<ProductPoolAttribute>();

    @OneToMany
    @Cascade({org.hibernate.annotations.CascadeType.ALL,
        org.hibernate.annotations.CascadeType.DELETE_ORPHAN})
    @JoinColumn(name = "pool_id", insertable = false, updatable = false)
    @Where(clause = "dtype='derived'")
    private Set<DerivedProductPoolAttribute> derivedProductAttributes =
        new HashSet<DerivedProductPoolAttribute>();

    @OneToMany(mappedBy = "pool", cascade = CascadeType.ALL)
    @LazyCollection(LazyCollectionOption.EXTRA)
    private Set<Entitlement> entitlements = new HashSet<Entitlement>();

    private String restrictedToUsername;

    private String contractNumber;
    private String accountNumber;
    private String orderNumber;

    @Formula("(select sum(ent.quantity) from cp_entitlement ent " +
             "where ent.pool_id = id)")
    private Long consumed;

    @Formula("(select sum(ent.quantity) from cp_entitlement ent, cp_consumer cons, " +
        "cp_consumer_type ctype where ent.pool_id = id and ent.consumer_id = cons.id " +
        "and cons.type_id = ctype.id and ctype.manifest = 'Y')")
    private Long exported;

    // TODO: May not still be needed, IIRC a temporary hack for client.
    private String productName;

    private String derivedProductName;

    @Version
    private int version;

    @Transient
    private Map<String, String> calculatedAttributes;

    public Pool() {
    }

    public Pool(Owner ownerIn, String productId, String productName,
        Set<ProvidedProduct> providedProducts,
        Long quantityIn, Date startDateIn, Date endDateIn, String contractNumber,
        String accountNumber, String orderNumber) {
        this.productId = productId;
        this.productName = productName;
        this.owner = ownerIn;
        this.quantity = quantityIn;
        this.startDate = startDateIn;
        this.endDate = endDateIn;
        this.contractNumber = contractNumber;
        this.accountNumber = accountNumber;
        this.orderNumber = orderNumber;
        this.providedProducts = providedProducts;
    }

    /** {@inheritDoc} */
    @Override
    @HateoasInclude
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
        return consumed == null ? 0 : consumed;
    }

    /**
     * @param consumed set the activate uses.
     */
    public void setConsumed(Long consumed) {
        // Even though this is calculated at DB fetch time, we allow
        // setting it for changes in a single transaction
        this.consumed = consumed;
    }

    /**
     * @return quantity currently exported.
     */
    public Long getExported() {
        return exported == null ? 0 : exported;
    }

    /**
     * @param exported set the activate uses.
     */
    public void setExported(Long exported) {
        // Even though this is calculated at DB fetch time, we allow
        // setting it for changes in a single transaction
        this.exported = exported;
    }

    /**
     * @return owner of the pool.
     */
    @Override
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
    @HateoasInclude
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

    public String getOrderNumber() {
        return orderNumber;
    }

    public void setOrderNumber(String orderNumber) {
        this.orderNumber = orderNumber;
    }

    public boolean hasAttribute(String key) {
        return findAttribute(this.attributes, key) != null;
    }

    /**
     * Attribute comparison helper, safe to use even if property is null.
     *
     * Used primarily in the javascript rules.
     *
     * @param key Desired attribute.
     * @param expectedValue Expected value.
     * @return true if the pool has the given attribute and it is equal to the value,
     * false otherwise.
     */
    public boolean attributeEquals(String key, String expectedValue) {
        String val = getAttributeValue(key);
        if (val != null && val.equals(expectedValue))  {
            return true;
        }
        return false;
    }

    public Set<PoolAttribute> getAttributes() {
        if (attributes == null) {
            return new HashSet<PoolAttribute>();
        }
        return attributes;
    }

    public String getAttributeValue(String name) {
        return findAttributeValue(this.attributes, name);
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
        PoolAttribute existing = findAttribute(this.attributes, key);
        if (existing != null) {
            existing.setValue(value);
        }
        else {
            PoolAttribute attr = new PoolAttribute(key, value);
            attr.setPool(this);
            addAttribute(attr);
        }
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

    public String getSourceStackId() {
        return sourceStackId;
    }

    public void setSourceStackId(String sourceStackId) {
        this.sourceStackId = sourceStackId;
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
        return "Pool[id: " + getId() + ", owner: " + owner.getId() +
            ", product: " + productId +
            ", quantity: " + getQuantity() + ", expires: " + getEndDate() + "]";
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
     *
     * @param productId
     * @return true if pool provides this product
     */
    public Boolean provides(String productId) {
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
     * Check if this pool provides the given product ID as a derived provided product.
     * Used when we're looking for pools we could give to a host that will create
     * sub-pools for guest products.
     *
     * If derived product ID is not set, we just use the normal set of products.
     *
     * @param productId
     * @return true if pool provides this product
     */
    public Boolean providesDerived(String productId) {
        if (this.getDerivedProductId() != null) {
            if (getDerivedProductId().equals(productId)) {
                return true;
            }

            if (getDerivedProvidedProducts() != null) {
                for (DerivedProvidedProduct p : getDerivedProvidedProducts()) {
                    if (p.getProductId().equals(productId)) {
                        return true;
                    }
                }
            }
        }
        else {
            return provides(productId);
        }
        return false;
    }

    /**
     * Return the "top level" product this pool is for.
     * Note that pools can also provide access to other products.
     * See getProvidedProductIds().
     * @return Top level product ID.
     */
    @HateoasInclude
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
        return getConsumed() > this.quantity;
    }

    @HateoasInclude
    public String getHref() {
        return "/pools/" + getId();
    }

    public void setProductAttributes(Set<ProductPoolAttribute> attrs) {
        this.productAttributes = attrs;
    }

    public Set<ProductPoolAttribute> getProductAttributes() {
        return productAttributes;
    }

    public Set<DerivedProductPoolAttribute> getDerivedProductAttributes() {
        return derivedProductAttributes;
    }

    public void addProductAttribute(ProductPoolAttribute attrib) {
        attrib.setPool(this);
        this.productAttributes.add(attrib);
    }

    public void addSubProductAttribute(DerivedProductPoolAttribute attrib) {
        attrib.setPool(this);
        this.derivedProductAttributes.add(attrib);
    }

    public void setProductAttribute(String key, String value, String productId) {
        ProductPoolAttribute existing =
            findAttribute(this.productAttributes, key);
        if (existing != null) {
            existing.setValue(value);
            existing.setProductId(productId);
        }
        else {
            ProductPoolAttribute attr = new ProductPoolAttribute(key,
                value, productId);
            addProductAttribute(attr);
        }
    }

    public void setDerivedProductAttribute(String key, String value, String productId) {
        DerivedProductPoolAttribute existing =
            findAttribute(this.derivedProductAttributes, key);
        if (existing != null) {
            existing.setValue(value);
            existing.setProductId(productId);
        }
        else {
            DerivedProductPoolAttribute attr = new DerivedProductPoolAttribute(key,
                value, productId);
            addSubProductAttribute(attr);
        }
    }

    public boolean hasProductAttribute(String name) {
        return findAttribute(this.productAttributes, name) != null;
    }

    public boolean hasSubProductAttribute(String name) {
        return findAttribute(this.derivedProductAttributes, name) != null;
    }

    public ProductPoolAttribute getProductAttribute(String name) {
        return findAttribute(this.productAttributes, name);
    }

    public String getProductAttributeValue(String name) {
        return findAttributeValue(this.productAttributes, name);
    }

    public DerivedProductPoolAttribute getDerivedProductAttribute(String name) {
        return findAttribute(this.derivedProductAttributes, name);
    }

    private <A extends AbstractPoolAttribute> A findAttribute(Set<A> attributes,
        String key) {
        if (attributes == null) {
            return null;
        }
        for (A a : attributes) {
            if (a.getName().equals(key)) {
                return a;
            }
        }
        return null;
    }

    private <A extends AbstractPoolAttribute> String findAttributeValue(Set<A> toSearch,
        String key) {
        if (toSearch == null) {
            return null;
        }
        for (A a : toSearch) {
            if (a.getName().equals(key)) {
                return a.getValue();
            }
        }
        return null;
    }

    /**
     * @return the subscriptionSubKey
     */
    public String getSubscriptionSubKey() {
        return subscriptionSubKey;
    }

    /**
     * @param subscriptionSubKey the subscriptionSubKey to set
     */
    public void setSubscriptionSubKey(String subscriptionSubKey) {
        this.subscriptionSubKey = subscriptionSubKey;
    }

    public Map<String, String> getCalculatedAttributes() {
        return calculatedAttributes;
    }

    public void setCalculatedAttributes(Map<String, String> calculatedAttributes) {
        this.calculatedAttributes = calculatedAttributes;
    }

    public void addCalculatedAttribute(String name, String value) {
        if (calculatedAttributes == null) {
            calculatedAttributes = new HashMap<String, String>();
        }

        calculatedAttributes.put(name, value);
    }

    public String getDerivedProductId() {
        return derivedProductId;
    }

    public void setDerivedProductId(String subProductId) {
        this.derivedProductId = subProductId;
    }

    public Set<DerivedProvidedProduct> getDerivedProvidedProducts() {
        return derivedProvidedProducts;
    }

    public void setDerivedProvidedProducts(
        Set<DerivedProvidedProduct> subProvidedProducts) {
        this.derivedProvidedProducts = subProvidedProducts;
    }

    public String getDerivedProductName() {
        return derivedProductName;
    }

    public void setDerivedProductName(String subProductName) {
        this.derivedProductName = subProductName;
    }

    public Consumer getSourceConsumer() {
        return sourceConsumer;
    }

    public void setSourceConsumer(Consumer sourceConsumer) {
        this.sourceConsumer = sourceConsumer;
    }

    /**
     * There are a number of radically different types of pools. This field is
     * a quick indicator of what type of pool you are looking at.
     * See PoolType comments for descriptions of types.
     *
     * @return pool type
     */
    public PoolType getType() {
        if (hasAttribute("pool_derived")) {
            if (getSourceEntitlement() != null) {
                return PoolType.ENTITLEMENT_DERIVED;
            }
            else if (getSourceStackId() != null) {
                return PoolType.STACK_DERIVED;
            }
            else {
                return PoolType.BONUS;
            }
        }
        return PoolType.NORMAL;
    }

    public boolean isStacked() {
        return hasProductAttribute("stacking_id");
    }

    public String getStackId() {
        return getProductAttributeValue("stacking_id");
    }
}
