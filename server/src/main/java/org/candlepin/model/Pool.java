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

import org.candlepin.common.jackson.HateoasInclude;
import org.candlepin.service.model.SubscriptionInfo;
import org.candlepin.util.DateSource;

import org.apache.commons.lang.StringUtils;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.Cascade;
import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;
import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.LazyCollection;
import org.hibernate.annotations.LazyCollectionOption;
import org.hibernate.annotations.Type;

import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.persistence.CascadeType;
import javax.persistence.CollectionTable;
import javax.persistence.Column;
import javax.persistence.ElementCollection;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.MapKeyColumn;
import javax.persistence.OneToMany;
import javax.persistence.OneToOne;
import javax.persistence.Table;
import javax.persistence.Transient;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;



/**
 * Represents a pool of products eligible to be consumed (entitled).
 * For every Product there will be a corresponding Pool.
 */
@Entity
@Table(name = Pool.DB_TABLE)
public class Pool extends AbstractHibernateObject<Pool> implements Owned, Named, Comparable<Pool>,
    Eventful, SubscriptionInfo {

    /** Name of the table backing this object in the database */
    public static final String DB_TABLE = "cp_pool";

    /**
     * Common pool attributes
     */
    public static final class Attributes {
        /** Attribute used to determine whether or not the pool is derived from the use of an entitlement */
        public static final String DERIVED_POOL = "pool_derived";

        /** Attribute used to determine whether or not the pool was created for a development entitlement */
        public static final String DEVELOPMENT_POOL = "dev_pool";

        /** Attribute used to specify consumer types allowed to consume this pool */
        public static final String ENABLED_CONSUMER_TYPES = "enabled_consumer_types";

        /** Attribute used to identify multi-entitlement enabled pools. */
        public static final String MULTI_ENTITLEMENT = "multi-entitlement";

        /** Attribute specifying the product family of a given pool */
        public static final String PRODUCT_FAMILY = "product_family";

        /** Attribute for specifying the pool is only available to physical systems */
        public static final String PHYSICAL_ONLY = "physical_only";

        /** Attribute used to determine which specific consumer the pool was created for */
        public static final String REQUIRES_CONSUMER = "requires_consumer";

        /** Attribute used to determine which specific consumer type the pool was created for */
        public static final String REQUIRES_CONSUMER_TYPE = "requires_consumer_type";

        /** Attribute used to determine which specific host the pool was created for */
        public static final String REQUIRES_HOST = "requires_host";

        /** Attribute for specifying the source pool from which a derived pool originates */
        public static final String SOURCE_POOL_ID = "source_pool_id";

        /** Attribute for specifying the pool is only available to guests */
        public static final String VIRT_ONLY = "virt_only";

        /** Attribute used to identify unmapped guest pools. Pool must also be a derived pool */
        public static final String UNMAPPED_GUESTS_ONLY = "unmapped_guests_only";
    }

    /**
     * PoolType
     *
     * Pools can be of several major types which can radically alter how they behave.
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
     *
     * UNMAPPED_GUEST - TODO
     *
     * DEVELOPMENT = TODO
     */
    public enum PoolType {
        NORMAL,
        ENTITLEMENT_DERIVED,
        STACK_DERIVED,
        BONUS,
        UNMAPPED_GUEST,
        DEVELOPMENT;

        /**
         * Checks if this type represents a derived pool type
         *
         * @return
         *  True if this PoolType instance represents a derived pool type; false otherwise
         */
        public boolean isDerivedType() {
            switch (this) {
                case ENTITLEMENT_DERIVED:
                case STACK_DERIVED:
                    return true;

                default:
                    return false;
            }
        }
    }

    /**
     * PoolComplianceType
     *
     * Indicates how a pool can be used
     */
    public enum PoolComplianceType {
        UNKNOWN("Other"),
        STANDARD("Standard"),
        INSTANCE_BASED("Instance Based"),
        STACKABLE("Stackable"),
        UNIQUE_STACKABLE("Stackable only with other subscriptions"),
        MULTI_ENTITLEMENT("Multi-Entitleable");

        private final String description;

        PoolComplianceType(String description) {
            if (description == null || description.length() < 1) {
                throw new IllegalArgumentException("description is null or empty");
            }

            this.description = description;
        }

        public String getDescription() {
            return this.description;
        }
    }

    @Id
    @GeneratedValue(generator = "system-uuid")
    @GenericGenerator(name = "system-uuid", strategy = "uuid")
    @Column(length = 32)
    @NotNull
    private String id;

    @Enumerated(EnumType.STRING)
    @NotNull
    private PoolType type;

    @ManyToOne
    @JoinColumn(nullable = false)
    @NotNull
    private Owner owner;

    private Boolean activeSubscription;

    /** Indicates this pool was created as a result of granting an entitlement.
     * Allows us to know that we need to clean this pool up if that entitlement
     * if ever revoked. */
    @ManyToOne
    @JoinColumn(nullable = true)
    private Entitlement sourceEntitlement;

    /**
     * Signifies that this pool is a derived pool linked to this stack (only one
     * sub pool per stack allowed)
     */
    @OneToOne(mappedBy = "derivedPool", targetEntity = SourceStack.class)
    @Cascade({org.hibernate.annotations.CascadeType.ALL,
        org.hibernate.annotations.CascadeType.DELETE_ORPHAN})
    private SourceStack sourceStack;

    /**
     * Signifies that this pool was created from this subscription (only one
     * pool per subscription id/subkey is allowed)
     */
    @OneToOne(mappedBy = "pool", targetEntity = SourceSubscription.class)
    @Cascade({org.hibernate.annotations.CascadeType.ALL,
        org.hibernate.annotations.CascadeType.DELETE_ORPHAN})
    private SourceSubscription sourceSubscription;

    @Column(nullable = false)
    @NotNull
    private Long quantity;

    @Column(nullable = false)
    @NotNull
    private Date startDate;

    @Column(nullable = false)
    @NotNull
    private Date endDate;

    /*
     * After Jackson version is upgraded:
     */
    @ManyToOne
    @JoinColumn(name = "product_uuid", nullable = false)
    @NotNull
    private Product product;

    @ElementCollection
    @BatchSize(size = 1000)
    @CollectionTable(name = "cp_pool_attribute", joinColumns = @JoinColumn(name = "pool_id"))
    @MapKeyColumn(name = "name")
    @Column(name = "value")
    @Cascade({ org.hibernate.annotations.CascadeType.ALL })
    @Fetch(FetchMode.SUBSELECT)
    private Map<String, String> attributes;

    @OneToMany(mappedBy = "pool")
    @LazyCollection(LazyCollectionOption.EXTRA)
    private Set<Entitlement> entitlements;

    @Size(max = 255)
    private String restrictedToUsername;

    @Size(max = 255)
    private String contractNumber;

    @Size(max = 255)
    private String accountNumber;

    @Size(max = 255)
    private String orderNumber;

    @Column(name = "quantity_consumed")
    @NotNull
    private Long consumed;

    @Column(name = "quantity_exported")
    @NotNull
    private Long exported;

    @Transient
    private Map<String, String> calculatedAttributes;

    @Transient
    private boolean markedForDelete = false;

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

    /**
     * A "locked (true)" pool is logically equivalent to a non-"custom" pool.
     * A "un-locked (false)" pool is logically equivalent to a "custom" pool.
     */
    @Column(name = "locked")
    @Type(type = "org.hibernate.type.NumericBooleanType")
    private Boolean locked;

    public Pool() {
        this.activeSubscription = Boolean.TRUE;
        this.attributes = new HashMap<>();
        this.entitlements = new HashSet<>();

        this.markedForDelete = false;
        this.locked = false;

        this.setExported(0L);
        this.setConsumed(0L);
    }

    /** {@inheritDoc} */
    @Override
    @HateoasInclude
    public String getId() {
        return id;
    }

    /**
     * @param id new db id.
     *
     * @return
     *  a reference to this pool instance
     */
    public Pool setId(String id) {
        this.id = id;
        return this;
    }

    /**
     * @return when the pool became active.
     */
    public Date getStartDate() {
        return startDate;
    }

    /**
     * @param startDate set the pool active date.
     *
     * @return
     *  a reference to this pool instance
     */
    public Pool setStartDate(Date startDate) {
        this.startDate = startDate;
        return this;
    }

    /**
     * @return when the pool expires.
     */
    public Date getEndDate() {
        return endDate;
    }

    /**
     * @param endDate set the pool expiration date.
     *
     * @return
     *  a reference to this pool instance
     */
    public Pool setEndDate(Date endDate) {
        this.endDate = endDate;
        return this;
    }

    /**
     * {@inheritDoc}
     */
    public Date getLastModified() {
        return this.getUpdated();
    }

    /**
     * @return quantity
     */
    public Long getQuantity() {
        return quantity;
    }

    /**
     * @param quantity quantity
     *
     * @return
     *  a reference to this pool instance
     */
    public Pool setQuantity(Long quantity) {
        this.quantity = quantity;
        return this;
    }

    /**
     * @return quantity currently consumed.
     */
    public Long getConsumed() {
        return consumed == null ? 0 : consumed;
    }

    /**
     * @param consumed set the activate uses.
     *
     * @return
     *  a reference to this pool instance
     */
    public Pool setConsumed(Long consumed) {
        // Even though this is calculated at DB fetch time, we allow
        // setting it for changes in a single transaction
        this.consumed = consumed;
        return this;
    }

    /**
     * @return quantity currently exported.
     */
    public Long getExported() {
        return exported == null ? 0 : exported;
    }

    /**
     * @param exported set the activate uses.
     *
     * @return
     *  a reference to this pool instance
     */
    public Pool setExported(Long exported) {
        // Even though this is calculated at DB fetch time, we allow
        // setting it for changes in a single transaction
        this.exported = exported;
        return this;
    }

    /**
     * @return owner of the pool.
     */
    public Owner getOwner() {
        return owner;
    }

    /**
     * @return the owner Id of this Pool.
     */
    @Override
    public String getOwnerId() {
        return (owner == null) ? null : owner.getId();
    }

    /**
     * @param owner changes the owner of the pool.
     *
     * @return
     *  a reference to this pool instance
     */
    public Pool setOwner(Owner owner) {
        this.owner = owner;
        return this;
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
     * Sets the contract number for this pool
     *
     * @param contractNumber
     *
     * @return
     *  a reference to this pool instance
     */
    public Pool setContractNumber(String contractNumber) {
        this.contractNumber = contractNumber;
        return this;
    }

    public String getAccountNumber() {
        return accountNumber;
    }

    public Pool setAccountNumber(String accountNumber) {
        this.accountNumber = accountNumber;
        return this;
    }

    public String getOrderNumber() {
        return orderNumber;
    }

    public Pool setOrderNumber(String orderNumber) {
        this.orderNumber = orderNumber;
        return this;
    }

    /**
     * Retrieves the attributes for this pool. If this pool does not have any attributes,
     * this method returns an empty map.
     *
     * @return
     *  a map containing the attributes for this pool
     */
    public Map<String, String> getAttributes() {
        return Collections.unmodifiableMap(this.attributes);
    }

    /**
     * Retrieves the value associated with the given attribute. If the attribute is not set, this
     * method returns null.
     *
     * @param key
     *  The key (name) of the attribute to lookup
     *
     * @throws IllegalArgumentException
     *  if key is null
     *
     * @return
     *  the value set for the given attribute, or null if the attribute is not set
     */
    public String getAttributeValue(String key) {
        if (key == null) {
            throw new IllegalArgumentException("key is null");
        }

        return this.attributes.get(key);
    }

    /**
     * Checks if the given attribute has been defined on this pool.
     *
     * @param key
     *  The key (name) of the attribute to lookup
     *
     * @throws IllegalArgumentException
     *  if key is null
     *
     * @return
     *  true if the attribute is defined for this pool; false otherwise
     */
    public boolean hasAttribute(String key) {
        if (key == null) {
            throw new IllegalArgumentException("key is null");
        }

        return this.attributes.containsKey(key);
    }

    /**
     * Sets the specified attribute for this pool. If the attribute has already been set for
     * this pool, the existing value will be overwritten. If the given attribute value is null
     * or empty, the attribute will be removed.
     *
     * @param key
     *  The name or key of the attribute to set
     *
     * @param value
     *  The value to assign to the attribute, or null to remove the attribute
     *
     * @throws IllegalArgumentException
     *  if key is null
     *
     * @return
     *  a reference to this pool
     */
    public Pool setAttribute(String key, String value) {
        if (key == null) {
            throw new IllegalArgumentException("key is null");
        }

        // Impl note:
        // We can't standardize the value at all here; some attributes allow null, some expect
        // empty strings, and others have their own sential values. Unless we make a concerted
        // effort to fix all of these inconsistencies with a massive database update, we can't
        // perform any input sanitation/massaging.
        this.attributes.put(key, value);
        return this;
    }

    /**
     * Removes the attribute with the given attribute key from this pool.
     *
     * @param key
     *  The name/key of the attribute to remove
     *
     * @throws IllegalArgumentException
     *  if key is null
     *
     * @return
     *  true if the attribute was removed successfully; false otherwise
     */
    public boolean removeAttribute(String key) {
        if (key == null) {
            throw new IllegalArgumentException("key is null");
        }

        boolean present = this.attributes.containsKey(key);

        this.attributes.remove(key);
        return present;
    }

    /**
     * Clears all attributes currently set for this pool.
     *
     * @return
     *  a reference to this pool
     */
    public Pool clearAttributes() {
        this.attributes.clear();
        return this;
    }

    /**
     * Sets the attributes for this pool.
     *
     * @param attributes
     *  A map of attribute key, value pairs to assign to this pool, or null to clear the
     *  attributes
     *
     * @return
     *  a reference to this pool
     */
    public Pool setAttributes(Map<String, String> attributes) {
        this.attributes.clear();

        if (attributes != null) {
            this.attributes.putAll(attributes);
        }

        return this;
    }

    /**
     * Checks if the given attribute is defined on this pool or its product.
     *
     * @param key
     *  The key (name) of the attribute to check
     *
     * @throws IllegalArgumentException
     *  if key is null
     *
     * @return
     *  true if the attribute is set on this pool or its product; false otherwise
     */
    public boolean hasMergedAttribute(String key) {
        if (key == null) {
            throw new IllegalArgumentException("key is null");
        }

        return this.attributes.containsKey(key) || (this.product != null && this.product.hasAttribute(key));
    }

    /**
     * Retrieves the value for the given attribute on this pool. If the attribute is not set on
     * this pool, its product, if available, will be checked instead.
     *
     * @param key
     *  The key (name) of the attribute for which to fetch the value
     *
     * @throws IllegalArgumentException
     *  if key is null
     *
     * @return
     *  the value of the attribute for this pool or its product, or null if the attribute is not
     *  set on either
     */
    public String getMergedAttribute(String key) {
        if (key == null) {
            throw new IllegalArgumentException("key is null");
        }

        String value = this.attributes.get(key);
        return (value == null && this.product != null) ? this.product.getAttributeValue(key) : value;
    }

    /**
     * Checks if the given attribute is defined on the product, and if not, then the pool.
     *
     * @param key
     *  The key (name) of the attribute to check
     *
     * @throws IllegalArgumentException
     *  if key is null
     *
     * @return
     *  true if the attribute is set on the product or the pool; false otherwise
     */
    public boolean hasMergedProductAttribute(String key) {
        if (key == null) {
            throw new IllegalArgumentException("key is null");
        }

        return (this.product != null && this.product.hasAttribute(key)) || this.attributes.containsKey(key);
    }

    /**
     * Retrieves the value for the given attribute on this pool's product. If the pool has an available
     * product, and the attribute is not set on it, or the pool does not have a product, then the pool
     * will be checked instead.
     *
     * @param key
     *  The key (name) of the attribute for which to fetch the value
     *
     * @throws IllegalArgumentException
     *  if key is null
     *
     * @return
     *  the value of the attribute for this product or pool, or null if the attribute is not set on either
     */
    public String getMergedProductAttribute(String key) {
        if (key == null) {
            throw new IllegalArgumentException("key is null");
        }

        String value = this.product != null ? this.product.getAttributeValue(key) : null;
        return value == null ? this.attributes.get(key) : value;
    }

    /**
     * returns true if the pool is considered expired based on the given date.
     * @param dateSource date to compare to.
     * @return true if the pool is considered expired based on the given date.
     */
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
    public boolean isUnlimited() {
        return this.getQuantity() < 0;
    }

    /**
     * @return source entitlement.
     */
    public Entitlement getSourceEntitlement() {
        return sourceEntitlement;
    }

    /**
     * @param sourceEntitlement source entitlement
     *
     * @return
     *  a reference to this pool instance
     */
    public Pool setSourceEntitlement(Entitlement sourceEntitlement) {
        this.sourceEntitlement = sourceEntitlement;
        return this;
    }

    /**
     * @return subscription id associated with this pool.
     */
    public String getSubscriptionId() {
        if (this.getSourceSubscription() != null) {
            return this.getSourceSubscription().getSubscriptionId();
        }

        return null;
    }

    public SourceStack getSourceStack() {
        return sourceStack;
    }

    public Pool setSourceStack(SourceStack sourceStack) {
        if (sourceStack != null) {
            sourceStack.setDerivedPool(this);
            // Setting source Stack should invalidate source subscription
            this.setSourceSubscription(null);
        }
        this.sourceStack = sourceStack;

        return this;
    }

    /**
     * @return true if this pool represents an active subscription.
     */
    public Boolean getActiveSubscription() {
        return activeSubscription;
    }

    /**
     * @param activeSubscription TODO
     *
     * @return
     *  a reference to this pool instance
     */
    public Pool setActiveSubscription(Boolean activeSubscription) {
        this.activeSubscription = activeSubscription;
        return this;
    }

    /**
     * Retrieves the Product representing the top-level product for this pool.
     *
     * @return
     *  the top-level product for this pool.
     */
    public Product getProduct() {
        return this.product;
    }

    /**
     * Sets the Product to represent the top-level product for this pool.
     *
     * @param product
     *  The Product to assign as the top-level product for this pool.
     *
     * @return
     *  a reference to this pool instance
     */
    public Pool setProduct(Product product) {
        this.product = product;
        return this;
    }

    /**
     * Fetches the ID of the product for this pool. If this pool does not yet have a product, or the
     * product does not yet have an ID, this method returns null.
     *
     * @return
     *  the ID of the product for this pool, or null if the pool does not have a product with a
     *  defined ID
     */
    public String getProductId() {
        Product product = this.getProduct();
        return product != null ? product.getId() : null;
    }

    /**
     * Fetches the marketing name of the product for this pool. If the pool does not yet have a
     * product, or the pool does not define a marketing name, this method returns null.
     *
     * @return
     *  the marketing name of the product for this pool, or null if the pool does not have a
     *  product with a defined marketing name
     */
    public String getProductName() {
        Product product = this.getProduct();
        return product != null ? product.getName() : null;
    }

    /**
     * Retrieves the product attributes for this pool. The product attributes will be identical to
     * those retrieved from getProduct().getAttributes(), except the set returned by this method is
     * not modifiable.
     *
     * @return
     *  The attributes associated with the marketing product (SKU) for this pool
     */
    public Map<String, String> getProductAttributes() {
        Product product = this.getProduct();
        return product != null ? product.getAttributes() : Collections.emptyMap();
    }

    /**
     * Checks if the given attribute has been defined on this pool's product. If this pool does not
     * have a product, If the pool does not have a product, or the product does not have the
     * specified attribute, this method returns false.
     *
     * @param key
     *  The key (name) of the attribute to lookup
     *
     * @throws IllegalArgumentException
     *  if key is null
     *
     * @return
     *  true if the attribute is defined for this pool's product; false otherwise
     */
    public boolean hasProductAttribute(String key) {
        if (key == null) {
            throw new IllegalArgumentException("key is null");
        }

        return this.getProductAttributes().containsKey(key);
    }

    /**
     * Retrieves the derived product of this pool, if one is defined on the product for this pool.
     * If no derived product is defined on this pool's product, this method returns null.
     *
     * @return
     *  the derived product for this pool, or null if a derived product is not defined on the product
     */
    public Product getDerivedProduct() {
        Product product = this.getProduct();
        return product != null ? product.getDerivedProduct() : null;
    }

    /**
     * Retreives the ID of the derived product of the product of this pool. If any of the objects
     * along the way are null, or the derived product does not yet have an ID, this method returns
     * null.
     *
     * @return
     *  the ID of the derived product for this pool, or null if the derived product is not set, or
     *  does not yet have an ID
     */
    public String getDerivedProductId() {
        Product derived = this.getDerivedProduct();
        return derived != null ? derived.getId() : null;
    }

    /**
     * Retreives the name of the derived product of the product of this pool. If any of the objects
     * along the way are null, or the derived product does not yet have a name, this method returns
     * null.
     *
     * @return
     *  the name of the derived product for this pool, or null if the derived product is not set, or
     *  does not yet have an name
     */
    public String getDerivedProductName() {
        Product derived = this.getDerivedProduct();
        return derived != null ? derived.getName() : null;
    }

    /**
     * Retrieves the derived product attributes for this pool. The derived product attributes will
     * be identical to those retrieved from getDerivedProduct().getAttributes(), except the map
     * returned by this method is not modifiable.
     *
     * @return
     *  The attributes associated with the derived product for this pool
     */
    public Map<String, String> getDerivedProductAttributes() {
        Product derived = this.getDerivedProduct();
        return derived != null ? derived.getAttributes() : Collections.emptyMap();
    }

    /**
     * Gets the entitlements for this instance.
     *
     * @return The entitlements.
     */
    public Set<Entitlement> getEntitlements() {
        return this.entitlements;
    }

    /**
     * Sets the entitlements for this instance.
     *
     * @param entitlements The entitlements.
     *
     * @return
     *  a reference to this pool instance
     */
    public Pool setEntitlements(Set<Entitlement> entitlements) {
        this.entitlements = entitlements;
        return this;
    }

    public String getRestrictedToUsername() {
        return restrictedToUsername;
    }

    public Pool setRestrictedToUsername(String restrictedToUsername) {
        this.restrictedToUsername = restrictedToUsername;
        return this;
    }

    /**
     * Check whether {@link #consumed} is greater than {@link #quantity}
     *
     * @return true if consumed>quantity else false.
     */
    public boolean isOverflowing() {
        // Unlimited pools can't be overflowing:
        if (this.quantity == -1) {
            return false;
        }
        return getConsumed() > this.quantity;
    }

    /**
     * @return the subscriptionSubKey
     */
    public String getSubscriptionSubKey() {
        if (this.getSourceSubscription() != null) {
            return this.getSourceSubscription().getSubscriptionSubKey();
        }

        return null;
    }

    public Map<String, String> getCalculatedAttributes() {
        return calculatedAttributes;
    }

    public Pool setCalculatedAttributes(Map<String, String> calculatedAttributes) {
        this.calculatedAttributes = calculatedAttributes;
        return this;
    }

    /*
     * Keeping getSourceStackId to avoid breaking the api
     */
    public String getSourceStackId() {
        if (this.getSourceStack() != null) {
            return this.getSourceStack().getSourceStackId();
        }
        return null;
    }

    /**
     * There are a number of radically different types of pools. This field is
     * a quick indicator of what type of pool you are looking at.
     * See PoolType comments for descriptions of types.
     *
     * @return pool type
     */
    public PoolType getType() {
        if (hasAttribute(Attributes.DERIVED_POOL)) {
            if (hasAttribute(Attributes.UNMAPPED_GUESTS_ONLY)) {
                return PoolType.UNMAPPED_GUEST;
            }
            else if (getSourceEntitlement() != null) {
                return PoolType.ENTITLEMENT_DERIVED;
            }
            else if (getSourceStack() != null) {
                return PoolType.STACK_DERIVED;
            }
            else {
                return PoolType.BONUS;
            }
        }
        else if (hasAttribute(Attributes.DEVELOPMENT_POOL)) {
            return PoolType.DEVELOPMENT;
        }

        return PoolType.NORMAL;
    }

    public PoolComplianceType getComplianceType() {
        Product product = this.getProduct();

        if (product != null) {
            boolean isStacking = product.hasAttribute(Product.Attributes.STACKING_ID);
            boolean isMultiEnt = "yes".equalsIgnoreCase(
                product.getAttributeValue(Attributes.MULTI_ENTITLEMENT));

            if (product.hasAttribute(Product.Attributes.INSTANCE_MULTIPLIER)) {
                if (isStacking && isMultiEnt) {
                    return PoolComplianceType.INSTANCE_BASED;
                }
            }
            else {
                if (isStacking) {
                    return isMultiEnt ? PoolComplianceType.STACKABLE : PoolComplianceType.UNIQUE_STACKABLE;
                }

                return isMultiEnt ? PoolComplianceType.MULTI_ENTITLEMENT : PoolComplianceType.STANDARD;
            }
        }

        return PoolComplianceType.UNKNOWN;
    }

    public boolean isStacked() {
        return this.getProduct() != null &&
            this.getProduct().hasAttribute(Product.Attributes.STACKING_ID);
    }

    public String getStackId() {
        return (this.getProduct() != null ?
            this.getProduct().getAttributeValue(Product.Attributes.STACKING_ID) : null);
    }

    public boolean isUnmappedGuestPool() {
        return "true".equalsIgnoreCase(this.getAttributeValue(Attributes.UNMAPPED_GUESTS_ONLY));
    }

    public boolean isDevelopmentPool() {
        return "true".equalsIgnoreCase(this.getAttributeValue(Attributes.DEVELOPMENT_POOL));
    }

    public SourceSubscription getSourceSubscription() {
        return sourceSubscription;
    }

    public Pool setSourceSubscription(SourceSubscription sourceSubscription) {
        if (sourceSubscription != null) {
            sourceSubscription.setPool(this);
        }

        this.sourceSubscription = sourceSubscription;
        return this;
    }

    public Pool setSubscriptionId(String subid) {
        if (sourceSubscription == null && !StringUtils.isBlank(subid)) {
            setSourceSubscription(new SourceSubscription());
        }

        if (sourceSubscription != null) {
            sourceSubscription.setSubscriptionId(subid);
            if (StringUtils.isBlank(sourceSubscription.getSubscriptionId()) &&
                StringUtils.isBlank(sourceSubscription.getSubscriptionSubKey())) {
                sourceSubscription = null;
            }
        }

        return this;
    }

    public Pool setSubscriptionSubKey(String subkey) {
        if (sourceSubscription == null && !StringUtils.isBlank(subkey)) {
            setSourceSubscription(new SourceSubscription());
        }

        if (sourceSubscription != null) {
            sourceSubscription.setSubscriptionSubKey(subkey);
            if (StringUtils.isBlank(sourceSubscription.getSubscriptionId()) &&
                StringUtils.isBlank(sourceSubscription.getSubscriptionSubKey())) {
                sourceSubscription = null;
            }
        }

        return this;
    }

    /**
     * adjusts current quantity by adding the input quantity
     * @param quantityToAdjust the quantity to add
     * @return the updated quantity
     */
    public long adjustQuantity(long quantityToAdjust) {
        long newCount = getQuantity() + quantityToAdjust;
        if (newCount < 0) {
            newCount = 0;
        }
        return newCount;
    }

    public static Long parseQuantity(String quantity) {
        Long q;
        if (quantity.equalsIgnoreCase("unlimited")) {
            q = -1L;
        }
        else {
            try {
                q = Long.parseLong(quantity);
            }
            catch (NumberFormatException e) {
                q = 0L;
            }
        }
        return q;
    }

    @Override
    public int compareTo(Pool other) {
        return (this.getId() == null ^ other.getId() == null) ?
            (this.getId() == null ? -1 : 1) :
                this.getId() == other.getId() ? 0 :
                    this.getId().compareTo(other.getId());
    }

    @Override
    public String getName() {
        return this.getProductName();
    }

    public boolean isMarkedForDelete() {
        return this.markedForDelete;
    }

    public Pool setMarkedForDelete(boolean markedForDelete) {
        this.markedForDelete = markedForDelete;
        return this;
    }

    @Override
    protected void onCreate() {
        super.onCreate();
        this.type = this.getType();
    }

    @Override
    protected void onUpdate() {
        super.onCreate();
        this.type = this.getType();
    }

    public String getUpstreamPoolId() {
        return upstreamPoolId;
    }

    public Pool setUpstreamPoolId(String upstreamPoolId) {
        this.upstreamPoolId = upstreamPoolId;
        return this;
    }

    public String getUpstreamEntitlementId() {
        return upstreamEntitlementId;
    }

    public Pool setUpstreamEntitlementId(String upstreamEntitlementId) {
        this.upstreamEntitlementId = upstreamEntitlementId;
        return this;
    }

    public String getUpstreamConsumerId() {
        return upstreamConsumerId;
    }

    public Pool setUpstreamConsumerId(String upstreamConsumerId) {
        this.upstreamConsumerId = upstreamConsumerId;
        return this;
    }

    public Cdn getCdn() {
        return cdn;
    }

    public Pool setCdn(Cdn cdn) {
        this.cdn = cdn;
        return this;
    }

    public SubscriptionsCertificate getCertificate() {
        return this.cert;
    }

    public Pool setCertificate(SubscriptionsCertificate cert) {
        this.cert = cert;
        return this;
    }

    public boolean isLocked() {
        return this.locked != null && locked;
    }

    public Pool setLocked(boolean locked) {
        this.locked = locked;
        return this;
    }

    public boolean isDerived() {
        return "true".equals(this.getAttributeValue(Pool.Attributes.DERIVED_POOL));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return String.format("Pool [id: %s, type: %s, product: %s, productName: %s, quantity: %s]",
            this.getId(), this.getType(), this.getProductId(), this.getProductName(), this.getQuantity());
    }
}
