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
import org.candlepin.jackson.CandlepinAttributeDeserializer;
import org.candlepin.jackson.CandlepinLegacyAttributeSerializer;
import org.candlepin.util.DateSource;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.annotation.JsonFilter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import org.apache.commons.lang.StringUtils;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.Cascade;
import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;
import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.LazyCollection;
import org.hibernate.annotations.LazyCollectionOption;

import java.util.Collection;
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
import javax.persistence.JoinTable;
import javax.persistence.ManyToMany;
import javax.persistence.ManyToOne;
import javax.persistence.MapKeyColumn;
import javax.persistence.OneToMany;
import javax.persistence.OneToOne;
import javax.persistence.Table;
import javax.persistence.Transient;
import javax.persistence.Version;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
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
@Table(name = Pool.DB_TABLE)
@JsonFilter("PoolFilter")
public class Pool extends AbstractHibernateObject implements Persisted, Owned, Named, Comparable<Pool>,
    Eventful {

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

        /** Attribute used to determine if a pool was created by sharing */
        public static final String SHARE = "share_derived";

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
        SHARE_DERIVED,
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
                case SHARE_DERIVED:
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
    @XmlTransient
    private SourceStack sourceStack;

    /**
     * Signifies that this pool was created from this subscription (only one
     * pool per subscription id/subkey is allowed)
     */
    @OneToOne(mappedBy = "pool", targetEntity = SourceSubscription.class)
    @Cascade({org.hibernate.annotations.CascadeType.ALL,
        org.hibernate.annotations.CascadeType.DELETE_ORPHAN})
    @XmlTransient
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
     * @JsonProperty(access = Access.WRITE_ONLY)
     */
    @ManyToOne
    @JoinColumn(name = "product_uuid", nullable = false)
    @NotNull
    private Product product;

    /*
     * After Jackson version is upgraded:
     * @JsonProperty(access = Access.WRITE_ONLY)
     */
    @ManyToOne
    @JoinColumn(name = "derived_product_uuid")
    private Product derivedProduct;

    @ManyToMany
    @JoinTable(
        name = "cp2_pool_provided_products",
        joinColumns = {@JoinColumn(name = "pool_id", insertable = false, updatable = false)},
        inverseJoinColumns = {@JoinColumn(name = "product_uuid")})
    @BatchSize(size = 1000)
    private Set<Product> providedProducts;

    @ManyToMany
    @JoinTable(
        name = "cp2_pool_derprov_products",
        joinColumns = {@JoinColumn(name = "pool_id", insertable = false, updatable = false)},
        inverseJoinColumns = {@JoinColumn(name = "product_uuid")})
    @BatchSize(size = 1000)
    private Set<Product> derivedProvidedProducts;

    @ElementCollection
    @BatchSize(size = 1000)
    @CollectionTable(name = "cp_pool_attribute", joinColumns = @JoinColumn(name = "pool_id"))
    @MapKeyColumn(name = "name")
    @Column(name = "value")
    @Cascade({ org.hibernate.annotations.CascadeType.ALL })
    @Fetch(FetchMode.SUBSELECT)
    @JsonSerialize(using = CandlepinLegacyAttributeSerializer.class)
    @JsonDeserialize(using = CandlepinAttributeDeserializer.class)
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

    @Column(name = "quantity_shared")
    @NotNull
    @Min(0)
    private Long shared;

    @OneToMany
    @JoinTable(name = "cp_pool_branding",
        joinColumns = @JoinColumn(name = "pool_id"),
        inverseJoinColumns = @JoinColumn(name = "branding_id"))
    @Cascade({org.hibernate.annotations.CascadeType.ALL, org.hibernate.annotations.CascadeType.DELETE_ORPHAN})
    @BatchSize(size = 1000)
    private Set<Branding> branding;

    @Version
    private int version;

    // Impl note:
    // These properties are only used as temporary stores to hold information that's only present
    // in the pool JSON due to the product itself not being serialized with it. These will be
    // ignored if a product or derived product object is present.
    @Transient
    private String importedProductId;

    @Transient
    private String importedDerivedProductId;

    @Transient
    private Set<ProvidedProduct> providedProductDtos;

    @Transient
    private Set<ProvidedProduct> derivedProvidedProductDtos;

    /**
     * Transient property that holds derived provided products from database. It is
     * populated before serialization happens.
     */
    @Transient
    private Set<ProvidedProduct> derivedProvidedProductDtosCached;

    @Transient
    @JsonSerialize(using = CandlepinLegacyAttributeSerializer.class)
    @JsonDeserialize(using = CandlepinAttributeDeserializer.class)
    @JsonProperty("productAttributes")
    private Map<String, String> importedProductAttributes;

    @Transient
    @JsonSerialize(using = CandlepinLegacyAttributeSerializer.class)
    @JsonDeserialize(using = CandlepinAttributeDeserializer.class)
    @JsonProperty("derivedProductAttributes")
    private Map<String, String> importedDerivedProductAttributes;

    // End legacy manifest/pool JSON properties

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
    @JsonIgnore
    private Cdn cdn;


    public Pool() {
        this.activeSubscription = Boolean.TRUE;
        this.providedProducts = new HashSet<Product>();
        this.derivedProvidedProducts = new HashSet<Product>();
        this.attributes = new HashMap<String, String>();
        this.branding = new HashSet<Branding>();
        this.entitlements = new HashSet<Entitlement>();

        // TODO:
        // This set of properties is used entirely to deal with a strange case that occurs during
        // while using entitlements kicked back from the rules as JSON. Since the JSON for a pool
        // does not encode the product object itself, we have to temporarily store product
        // information to return if, and only if, we do not have an authoritative product object.
        // These values can be set via setters, but will be ignored if a product is present.
        this.importedProductId = null;
        this.importedProductAttributes = null;
        this.importedDerivedProductId = null;
        this.importedDerivedProductAttributes = null;
        this.providedProductDtos = null;
        this.derivedProvidedProductDtos = null;
        this.derivedProvidedProductDtosCached = null;

        this.markedForDelete = false;

        this.setExported(0L);
        this.setConsumed(0L);
        this.setShared(0L);
    }

    public Pool(Owner ownerIn, Product product, Collection<Product> providedProducts,
        Long quantityIn, Date startDateIn, Date endDateIn, String contractNumber,
        String accountNumber, String orderNumber) {
        this();

        this.product = product;
        this.owner = ownerIn;
        this.quantity = quantityIn;
        this.startDate = startDateIn;
        this.endDate = endDateIn;
        this.contractNumber = contractNumber;
        this.accountNumber = accountNumber;
        this.orderNumber = orderNumber;

        this.setProvidedProducts(providedProducts);
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
     * @return the quantity of entitlements in this pool shared to another org.
     */
    public Long getShared() {
        return (shared == null) ? 0 : shared;
    }

    /**
     * @param shared the number to set the share count to
     */
    public void setShared(Long shared) {
        this.shared = shared;
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
        return (this.getProduct() != null ? this.getProduct().getName() : null);
    }

    public String getDerivedProductName() {
        return (this.getDerivedProduct() != null ? this.getDerivedProduct().getName() : null);
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
    @XmlTransient
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
    @XmlTransient
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
     */
    public void setSourceEntitlement(Entitlement sourceEntitlement) {
        this.sourceEntitlement = sourceEntitlement;
    }

    /**
     * @return subscription id associated with this pool.
     */
    @JsonProperty("subscriptionId")
    public String getSubscriptionId() {
        if (this.getSourceSubscription() != null) {
            return this.getSourceSubscription().getSubscriptionId();
        }
        return null;
    }

    @XmlTransient
    public SourceStack getSourceStack() {
        return sourceStack;
    }

    public void setSourceStack(SourceStack sourceStack) {
        if (sourceStack != null) {
            sourceStack.setDerivedPool(this);
            // Setting source Stack should invalidate source subscription
            this.setSourceSubscription(null);
        }
        this.sourceStack = sourceStack;
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
        return String.format(
            "Pool [id=%s, type=%s, product=%s, productName=%s, quantity=%s]",
            this.getId(),
            this.getType(),
            this.getProductId(),
            this.getProductName(),
            this.getQuantity()
        );
    }

    @JsonIgnore
    public Set<Product> getProvidedProducts() {
        return this.providedProducts;
    }

    public void addProvidedProduct(Product provided) {
        this.providedProducts.add(provided);
    }

    public void setProvidedProducts(Collection<Product> providedProducts) {
        this.providedProducts.clear();

        if (providedProducts != null) {
            this.providedProducts.addAll(providedProducts);
        }
    }

    /*
     * Always exported as a DTO for API/import backward compatibility.
     */
    @JsonProperty("providedProducts")
    public Set<ProvidedProduct> getProvidedProductDtos() {
        return providedProductDtos;
    }

    /**
     * This is a helper method to fill in transient fields in this class.
     * The transient fields are providedProductDtos, derivedProvidedProductDtosCached
     *
     * The reason we need to fill transient properties is, that various parts of code
     * that rely on serialization expect Pool object.
     *
     * From style point of view, this method could be moved to other class as well
     * (the ProductManager).
     *
     *
     * @param productCurator
     */
    public void populateAllTransientProvidedProducts(ProductCurator productCurator) {
        Set<ProvidedProduct> prods = new HashSet<ProvidedProduct>();

        if (this.providedProductDtos != null) {
            prods.addAll(this.providedProductDtos);
        }

        for (Product p : productCurator.getPoolProvidedProductsCached(id)) {
            prods.add(new ProvidedProduct(p));
        }

        providedProductDtos = prods;

        derivedProvidedProductDtosCached = new HashSet<ProvidedProduct>();

        for (Product p : productCurator.getPoolDerivedProvidedProductsCached(id)) {
            ProvidedProduct product = new ProvidedProduct(p);
            derivedProvidedProductDtosCached.add(product);
        }
    }

    /*
     * Used temporarily while importing a manifest.
     */
    public void setProvidedProductDtos(Set<ProvidedProduct> dtos) {
        providedProductDtos = dtos;
    }

    /**
     * Return the "top level" product this pool is for.
     * Note that pools can also provide access to other products.
     * See getProvidedProductIds().
     * @return Top level product ID.
     */
    @HateoasInclude
    public String getProductId() {
        return this.product != null ? this.product.getId() : this.importedProductId;
    }

    public void setProductId(String productId) {
        this.importedProductId = productId;
    }

    public void setDerivedProductId(String productId) {
        this.importedDerivedProductId = productId;
    }

    /**
     * Retrieves the Product representing the top-level product for this pool.
     *
     * @return
     *  the top-level product for this pool.
     */
    @JsonIgnore
    public Product getProduct() {
        return this.product;
    }

    /**
     * Sets the Product to represent the top-level product for this pool.
     *
     * @param product
     *  The Product to assign as the top-level product for this pool.
     */
    @JsonProperty
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public void setProduct(Product product) {
        this.product = product;

        if (product != null) {
            this.importedProductId = null;
            this.importedProductAttributes = null;
            this.importedDerivedProductId = null;
            this.importedDerivedProductAttributes = null;
        }
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
        // Unlimited pools can't be overflowing:
        if (this.quantity == -1) {
            return false;
        }
        return getConsumed() > this.quantity;
    }

    @HateoasInclude
    public String getHref() {
        return "/pools/" + getId();
    }

    /**
     * @return the subscriptionSubKey
     */
    @JsonProperty("subscriptionSubKey")
    public String getSubscriptionSubKey() {
        if (this.getSourceSubscription() != null) {
            return this.getSourceSubscription().getSubscriptionSubKey();
        }

        return null;
    }

    public Map<String, String> getCalculatedAttributes() {
        return calculatedAttributes;
    }

    public void setCalculatedAttributes(Map<String, String> calculatedAttributes) {
        this.calculatedAttributes = calculatedAttributes;
    }

    @JsonIgnore
    public Product getDerivedProduct() {
        return this.derivedProduct;
    }

    public String getDerivedProductId() {
        return this.derivedProduct != null ? this.derivedProduct.getId() : this.importedDerivedProductId;
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
        if (this.product != null) {
            return this.product.getAttributes();
        }

        if (this.importedProductAttributes != null) {
            return Collections.unmodifiableMap(this.importedProductAttributes);
        }

        return Collections.<String, String>emptyMap();
    }

    /**
     * Dummy method used to allow us to ignore the productAttributes field during JSON
     * deserialization. This method does nothing.
     *
     * @param attributes
     *  ignored
     *
     * @deprecated
     *  This method should be removed when we upgrade to Jackson 2.6, as it provides functionality
     *  for doing this type of filtering without requiring dummy methods.
     */
    @Deprecated
    public void setProductAttributes(Map<String, String> attributes) {
        if (this.product == null && this.derivedProduct == null) {
            this.importedProductAttributes = new HashMap<String, String>(attributes);
        }
    }

    /**
     * Retrieves the derived product attributes for this pool. The derived product attributes will
     * be identical to those retrieved from getDerivedProduct().getAttributes(), except the set
     * returned by this method is not modifiable.
     *
     * @return
     *  The attributes associated with the derived product for this pool
     */
    public Map<String, String> getDerivedProductAttributes() {
        if (this.derivedProduct != null) {
            return this.derivedProduct.getAttributes();
        }

        if (this.importedDerivedProductAttributes != null) {
            return Collections.unmodifiableMap(this.importedDerivedProductAttributes);
        }

        return Collections.<String, String>emptyMap();
    }

    /**
     * Dummy method used to allow us to ignore the derivedProductAttributes field during JSON
     * deserialization. This method does nothing.
     *
     * @param attributes
     *  ignored
     *
     * @deprecated
     *  This method should be removed when we upgrade to Jackson 2.6, as it provides functionality
     *  for doing this type of filtering without requiring dummy methods.
     */
    @Deprecated
    public void setDerivedProductAttributes(Map<String, String> attributes) {
        if (this.product == null) {
            this.importedDerivedProductAttributes = new HashMap<String, String>(attributes);
        }
    }

    @XmlTransient
    public String getProductAttributeValue(String key) {
        return this.getProductAttributes().get(key);
    }

    @JsonProperty
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public void setDerivedProduct(Product derived) {
        this.derivedProduct = derived;

        if (derived != null) {
            this.importedProductId = null;
            this.importedProductAttributes = null;
            this.importedDerivedProductId = null;
            this.importedDerivedProductAttributes = null;
        }
    }

    @JsonIgnore
    public Set<Product> getDerivedProvidedProducts() {
        return derivedProvidedProducts;
    }

    public void addDerivedProvidedProduct(Product provided) {
        this.derivedProvidedProducts.add(provided);
    }

    public void setDerivedProvidedProducts(Collection<Product> derivedProvidedProducts) {
        this.derivedProvidedProducts.clear();

        if (derivedProvidedProducts != null) {
            this.derivedProvidedProducts.addAll(derivedProvidedProducts);
        }
    }

    /*
     * Always exported as a DTO for API/import backward compatibility.
     */
    @JsonProperty("derivedProvidedProducts")
    public Set<ProvidedProduct> getDerivedProvidedProductDtos() {
        Set<ProvidedProduct> pp = new HashSet<ProvidedProduct>();
        Set<String> added = new HashSet<String>();

        if (this.derivedProvidedProductDtosCached != null) {
            for (ProvidedProduct p : this.derivedProvidedProductDtosCached) {
                pp.add(p);
                added.add(p.getProductId());
            }
        }

        if (this.derivedProvidedProductDtos != null) {
            for (ProvidedProduct p : this.derivedProvidedProductDtos) {
                if (!added.contains(p.getProductId())) {
                    pp.add(p);
                }
            }
        }

        return pp;
    }


    /*
     * Used temporarily while importing a manifest.
     */
    public void setDerivedProvidedProductDtos(Set<ProvidedProduct> dtos) {
        derivedProvidedProductDtos = dtos;
    }

    /*
     * Keeping getSourceStackId to avoid breaking the api
     */
    @JsonProperty("sourceStackId")
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
            else if (hasAttribute(Attributes.SHARE)) {
                return PoolType.SHARE_DERIVED;
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

    @JsonIgnore
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
        return (this.getProduct() != null ?
            this.getProduct().hasAttribute(Product.Attributes.STACKING_ID) : false);
    }

    public String getStackId() {
        return (this.getProduct() != null ?
            this.getProduct().getAttributeValue(Product.Attributes.STACKING_ID) : null);
    }

    @JsonIgnore
    public boolean isUnmappedGuestPool() {
        return "true".equalsIgnoreCase(this.getAttributeValue(Attributes.UNMAPPED_GUESTS_ONLY));
    }

    public boolean isDevelopmentPool() {
        return "true".equalsIgnoreCase(this.getAttributeValue(Attributes.DEVELOPMENT_POOL));
    }

    public Set<Branding> getBranding() {
        return branding;
    }

    public void setBranding(Set<Branding> branding) {
        this.branding = branding;
    }

    @XmlTransient
    public SourceSubscription getSourceSubscription() {
        return sourceSubscription;
    }

    public void setSourceSubscription(SourceSubscription sourceSubscription) {
        if (sourceSubscription != null) {
            sourceSubscription.setPool(this);
        }
        this.sourceSubscription = sourceSubscription;
    }

    public void setSubscriptionId(String subid) {
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
    }

    public void setSubscriptionSubKey(String subkey) {
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
    }

    @JsonIgnore
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
    @XmlTransient
    public String getName() {
        return this.getProductName();
    }

    @XmlTransient
    public boolean isMarkedForDelete() {
        return this.markedForDelete;
    }

    public void setMarkedForDelete(boolean markedForDelete) {
        this.markedForDelete = markedForDelete;
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

    public void setUpstreamPoolId(String upstreamPoolId) {
        this.upstreamPoolId = upstreamPoolId;
    }

    public String getUpstreamEntitlementId() {
        return upstreamEntitlementId;
    }

    public void setUpstreamEntitlementId(String upstreamEntitlementId) {
        this.upstreamEntitlementId = upstreamEntitlementId;
    }

    public String getUpstreamConsumerId() {
        return upstreamConsumerId;
    }

    public void setUpstreamConsumerId(String upstreamConsumerId) {
        this.upstreamConsumerId = upstreamConsumerId;
    }

    public Cdn getCdn() {
        return cdn;
    }

    public void setCdn(Cdn cdn) {
        this.cdn = cdn;
    }

    @XmlTransient
    public SubscriptionsCertificate getCertificate() {
        return this.cert;
    }

    public void setCertificate(SubscriptionsCertificate cert) {
        this.cert = cert;
    }

}
