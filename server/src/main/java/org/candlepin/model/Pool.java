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
import org.candlepin.util.DateSource;

import com.fasterxml.jackson.annotation.JsonFilter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import org.apache.commons.lang.StringUtils;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.Cascade;
import org.hibernate.annotations.Formula;
import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.LazyCollection;
import org.hibernate.annotations.LazyCollectionOption;

import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.persistence.Basic;
import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.JoinTable;
import javax.persistence.ManyToMany;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;
import javax.persistence.OneToOne;
import javax.persistence.Table;
import javax.persistence.Transient;
import javax.persistence.Version;
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

        /** Attribute used to identify unmapped guest pools. Pool must also be a derived pool */
        public static final String UNMAPPED_GUESTS_ONLY = "unmapped_guests_only";

        /** Product attribute used to identify multi-entitlement enabled pools. */
        public static final String MULTI_ENTITLEMENT = "multi-entitlement";

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

    private Boolean activeSubscription = Boolean.TRUE;

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
    private Set<Product> providedProducts = new HashSet<Product>();

    @ManyToMany
    @JoinTable(
        name = "cp2_pool_derprov_products",
        joinColumns = {@JoinColumn(name = "pool_id", insertable = false, updatable = false)},
        inverseJoinColumns = {@JoinColumn(name = "product_uuid")})
    @BatchSize(size = 1000)
    private Set<Product> derivedProvidedProducts = new HashSet<Product>();

    /**
     * Set of provided product DTOs used for compatibility with the pre-2.0 JSON.
     * Used when serializing a pool to JSON over the API, and when importing a manifest.
     * Collection is transient and should never make it to the database.
     */
    @Transient
    private Set<ProvidedProduct> providedProductDtos = new HashSet<ProvidedProduct>();

    /**
     * Set of provided product DTOs used for compatibility with the pre-2.0 JSON.
     * Used when serializing a pool to JSON over the API, and when importing a manifest.
     * Collection is transient and should never make it to the database.
     */
    @Transient
    private Set<ProvidedProduct> derivedProvidedProductDtos = new HashSet<ProvidedProduct>();;

    /**
     * Transient property that holds derived provided products from database. It is
     * populated before serialization happens.
     */
    @Transient
    private Set<ProvidedProduct> derivedProvidedProductDtosCached = new HashSet<ProvidedProduct>();;

    @OneToMany(mappedBy = "pool")
    @Cascade({org.hibernate.annotations.CascadeType.ALL,
        org.hibernate.annotations.CascadeType.DELETE_ORPHAN})
    @BatchSize(size = 1000)
    private Set<PoolAttribute> attributes = new HashSet<PoolAttribute>();

    @OneToMany(mappedBy = "pool", cascade = CascadeType.ALL)
    @LazyCollection(LazyCollectionOption.EXTRA)
    private Set<Entitlement> entitlements = new HashSet<Entitlement>();

    @Size(max = 255)
    private String restrictedToUsername;

    @Size(max = 255)
    private String contractNumber;

    @Size(max = 255)
    private String accountNumber;

    @Size(max = 255)
    private String orderNumber;

    // leave FROM capitalized until hibernate 5.0.3
    // https://hibernate.atlassian.net/browse/HHH-1400
    @Formula("(select sum(ent.quantity) FROM cp_entitlement ent where ent.pool_id = id)")
    private Long consumed;

    // leave FROM capitalized until hibernate 5.0.3
    // https://hibernate.atlassian.net/browse/HHH-1400
    @Formula("(select sum(ent.quantity) FROM cp_entitlement ent, cp_consumer cons, " +
        "cp_consumer_type ctype where ent.pool_id = id and ent.consumer_id = cons.id " +
        "and cons.type_id = ctype.id and ctype.manifest = 'Y')")
    // Only calculate exported lazily due to join fetches from entitlements taking extraordinary
    // amounts of time
    @Basic(fetch = FetchType.LAZY)
    private Long exported;

    @OneToMany
    @JoinTable(name = "cp_pool_branding",
        joinColumns = @JoinColumn(name = "pool_id"),
        inverseJoinColumns = @JoinColumn(name = "branding_id"))
    @Cascade({org.hibernate.annotations.CascadeType.ALL,
        org.hibernate.annotations.CascadeType.DELETE_ORPHAN})
    @BatchSize(size = 1000)
    private Set<Branding> branding = new HashSet<Branding>();

    @Version
    private int version;

    @Transient
    private Set<ProductAttribute> productAttributes = new HashSet<ProductAttribute>();

    @Transient
    private Map<String, String> calculatedAttributes;

    @Transient
    private boolean markedForDelete = false;

    /*
     * Only used for importing legacy manifests.
     */
    @Transient
    private String importedProductId = null;
    @Transient
    private String importedDerivedProductId = null;

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
    }

    public Pool(Owner ownerIn, Product product, Collection<Product> providedProducts,
        Long quantityIn, Date startDateIn, Date endDateIn, String contractNumber,
        String accountNumber, String orderNumber) {

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

    public boolean hasAttribute(String key) {
        return this.findAttribute(key) != null;
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
        return attributes;
    }

    public String getAttributeValue(String name) {
        PoolAttribute attribute = this.findAttribute(name);
        return attribute != null ? attribute.getValue() : null;
    }

    public void setAttributes(Set<PoolAttribute> attributes) {
        this.attributes.clear();

        if (attributes != null) {
            this.attributes.addAll(attributes);
        }
    }

    public void addAttribute(PoolAttribute attrib) {
        attrib.setPool(this);
        this.attributes.add(attrib);
    }

    public void setAttribute(String key, String value) {
        PoolAttribute existing = this.findAttribute(key);

        if (existing != null) {
            existing.setValue(value);
        }
        else {
            this.addAttribute(new PoolAttribute(key, value));
        }
    }

    private PoolAttribute findAttribute(String name) {
        for (PoolAttribute attribute : this.attributes) {
            if (attribute.getName().equals(name)) {
                return attribute;
            }
        }

        return null;
    }

    public boolean hasMergedAttribute(String name) {
        return this.getMergedAttribute(name) != null;
    }

    public Attribute getMergedAttribute(String name) {
        Attribute attribute = this.findAttribute(name);

        if (attribute == null) {
            attribute = this.product.getAttribute(name);
        }

        return attribute;
    }

    /**
     * Removes the specified attribute from this pool, returning its last known value. If the
     * attribute does not exist, this method returns null.
     *
     * @param key
     *  The attribute to remove from this pool
     *
     * @return
     *  the last value of the removed attribute, or null if the attribute did not exist
     */
    public String removeAttribute(String key) {
        PoolAttribute attrib = this.findAttribute(key);
        String value = null;

        if (attrib != null) {
            this.attributes.remove(attrib);
            attrib.setPool(null);

            value = attrib.getValue();
        }

        return value;
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
        if (getProduct() != null) {
            return this.getProduct().getId();
        }
        else if (getImportedProductId() != null) {
            return getImportedProductId();
        }
        return null;
    }

    public void setProductId(String productId) {
        this.importedProductId = productId;
    }

    @XmlTransient
    public String getImportedProductId() {
        return this.importedProductId;
    }

    public void setDerivedProductId(String productId) {
        this.importedDerivedProductId = productId;
    }

    @XmlTransient
    public String getDerivedImportedProductId() {
        return this.importedDerivedProductId;
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

    public void setProductAttributes(Set<ProductAttribute> attrs) {
        this.productAttributes = attrs;
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
        if (getDerivedProduct() != null) {
            return this.getDerivedProduct().getId();
        }
        else if (getDerivedImportedProductId() != null) {
            return getDerivedImportedProductId();
        }

        return null;
    }

    /**
     * Retrieves the product attributes for this pool. The product attributes will be identical to
     * those retrieved from getProduct().getAttributes(), except the set returned by this method is
     * not modifiable.
     *
     * @return
     *  The attributes associated with the marketing product (SKU) for this pool
     */
    public Collection<ProductAttribute> getProductAttributes() {
        return this.getProduct() != null ?
            this.getProduct().getAttributes() :
            this.productAttributes;
    }

    /**
     * Retrieves the derived product attributes for this pool. The derived product attributes will
     * be identical to those retrieved from getDerivedProduct().getAttributes(), except the set
     * returned by this method is not modifiable.
     *
     * @return
     *  The attributes associated with the derived product (???) for this pool
     */
    public Collection<ProductAttribute> getDerivedProductAttributes() {
        return this.getDerivedProduct() != null ?
            this.getDerivedProduct().getAttributes() :
            new HashSet<ProductAttribute>();
    }

    @XmlTransient
    public ProductAttribute getProductAttribute(String key) {
        if (key != null) {
            for (ProductAttribute attribute : this.getProductAttributes()) {
                if (key.equalsIgnoreCase(attribute.getName())) {
                    return attribute;
                }
            }
        }

        return null;
    }

    @XmlTransient
    public String getProductAttributeValue(String key) {
        ProductAttribute attribute = this.getProductAttribute(key);
        return attribute != null ? attribute.getValue() : null;
    }

    @JsonProperty
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public void setDerivedProduct(Product derived) {
        this.derivedProduct = derived;
    }

    @JsonIgnore
    public Set<Product> getDerivedProvidedProducts() {
        return derivedProvidedProducts;
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

        for (ProvidedProduct p : derivedProvidedProductDtosCached) {
            pp.add(p);
            added.add(p.getProductId());
        }

        for (ProvidedProduct p : derivedProvidedProductDtos) {
            if (!added.contains(p.getProductId())) {
                pp.add(p);
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
     * Keeping getSourceConsumer to avoid breaking the api
     */
    @JsonProperty("sourceConsumer")
    public Consumer getSourceConsumer() {
        if (this.getSourceEntitlement() != null) {
            return this.getSourceEntitlement().getConsumer();
        }

        if (this.getSourceStack() != null) {
            return sourceStack.getSourceConsumer();
        }

        return null;
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
                product.getAttributeValue(Attributes.MULTI_ENTITLEMENT)
            );

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
