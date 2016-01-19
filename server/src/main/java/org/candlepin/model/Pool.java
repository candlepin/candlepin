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

import org.candlepin.audit.Eventful;
import org.candlepin.common.jackson.HateoasInclude;
import org.candlepin.util.DateSource;

import com.fasterxml.jackson.annotation.JsonFilter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import org.apache.commons.lang.StringUtils;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.Cascade;
import org.hibernate.annotations.Formula;
import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.LazyCollection;
import org.hibernate.annotations.LazyCollectionOption;

import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
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
@Table(name = "cp_pool")
@JsonFilter("PoolFilter")
public class Pool extends AbstractHibernateObject implements Persisted, Owned, Named, Comparable<Pool>,
    Eventful {

    /**
     * Attribute used to determine whether or not the pool is derived from the use of an
     * entitlement.
     */
    public static final String DERIVED_POOL_ATTRIBUTE = "pool_derived";

    /**
     * Attribute used to identify unmapped guest pools. Pool must also be a derived pool.
     */
    public static final String UNMAPPED_GUESTS_ATTRIBUTE = "unmapped_guests_only";

    /**
     * Product attribute used to identify stacked pools.
     */
    public static final String STACKING_ATTRIBUTE = "stacking_id";

    /**
     * Product attribute used to identify multi-entitlement enabled pools.
     */
    public static final String MULTI_ENTITLEMENT_ATTRIBUTE = "multi-entitlement";

    /**
     * Product attribute used to specify the instance multiplier for a pool.
     */
    public static final String INSTANCE_ATTRIBUTE = "instance_multiplier";

    /**
     * Attribute used to determine whether or not the pool was created for a development
     * entitlement.
     */
    public static final String DEVELOPMENT_POOL_ATTRIBUTE = "dev_pool";

    /**
     * Attribute used to determine which specific consumer the pool was created for.
     */
    public static final String REQUIRES_CONSUMER_ATTRIBUTE = "requires_consumer";

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

    @ManyToOne
    @JoinColumn(name = "product_uuid", nullable = false)
    @NotNull
    private Product product;

    @ManyToOne
    @JoinColumn(name = "derived_product_uuid")
    private Product derivedProduct;

    @ManyToMany
    @JoinTable(
        name = "cpo_pool_provided_products",
        joinColumns = {@JoinColumn(name = "pool_id", insertable = false, updatable = false)},
        inverseJoinColumns = {@JoinColumn(name = "product_uuid")}
    )
    @BatchSize(size = 1000)
    private Set<Product> providedProducts = new HashSet<Product>();

    @ManyToMany
    @JoinTable(
        name = "cpo_pool_derived_products",
        joinColumns = {@JoinColumn(name = "pool_id", insertable = false, updatable = false)},
        inverseJoinColumns = {@JoinColumn(name = "product_uuid")}
    )
    @BatchSize(size = 1000)
    private Set<Product> derivedProvidedProducts = new HashSet<Product>();

    /**
     * Set of provided product DTOs used for compatibility with the pre-2.0 JSON.
     * Used when serializing a pool to JSON over the API, and when importing a manifest.
     * Collection is transient and should never make it to the database.
     */
    @Transient
    private Set<ProvidedProduct> providedProductDtos = null;

    /**
     * Set of provided product DTOs used for compatibility with the pre-2.0 JSON.
     * Used when serializing a pool to JSON over the API, and when importing a manifest.
     * Collection is transient and should never make it to the database.
     */
    @Transient
    private Set<ProvidedProduct> derivedProvidedProductDtos = null;

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

    @Formula("(select sum(ent.quantity) from cp_entitlement ent " +
             "where ent.pool_id = id)")
    private Long consumed;

    @Formula("(select sum(ent.quantity) from cp_entitlement ent, cp_consumer cons, " +
        "cp_consumer_type ctype where ent.pool_id = id and ent.consumer_id = cons.id " +
        "and cons.type_id = ctype.id and ctype.manifest = 'Y')")
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
    private Cdn cdn;


    public Pool() {
    }

    public Pool(Owner ownerIn, Product product, Set<Product> providedProducts,
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

        if (providedProducts != null) {
            this.setProvidedProducts(providedProducts);
        }
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
            "Pool<type=%s, product=%s, productName=%s, id=%s, quantity=%s>",
            this.getType(),
            this.getProductId(),
            this.getProductName(),
            this.getId(),
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

    public void setProvidedProducts(Set<Product> providedProducts) {
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
        Set<ProvidedProduct> prods = new HashSet<ProvidedProduct>();

        // TODO:
        // These DTOs need to be resolved or we could start running into conflicts. Including these
        // DTOs in the list is not a long-term solution.
        if (this.providedProductDtos != null) {
            prods.addAll(this.providedProductDtos);
        }

        for (Product p : getProvidedProducts()) {
            prods.add(new ProvidedProduct(p));
        }

        return prods;
    }

    /*
     * Used temporarily while importing a manifest.
     */
    public void setProvidedProductDtos(Set<ProvidedProduct> dtos) {
        providedProductDtos = dtos;
    }

    /**
     * Check if this pool provides the given product
     *
     * @param productId
     *  The Red Hat product ID for which to search.
     *
     * @return true if pool provides this product
     */
    public Boolean provides(String productId) {
        if (this.getProductId().equals(productId)) {
            return true;
        }

        if (providedProducts != null) {
            for (Product product : providedProducts) {
                if (product.getId().equals(productId)) {
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
        if (this.getDerivedProduct() != null) {
            if (getDerivedProduct().equals(productId)) {
                return true;
            }

            if (getDerivedProvidedProducts() != null) {
                for (Product product : getDerivedProvidedProducts()) {
                    if (product.getId().equals(productId)) {
                        return true;
                    }
                }
            }
        }
        else {
            return this.provides(productId);
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
    public Product getProduct() {
        return this.product;
    }

    /**
     * Sets the Product to represent the top-level product for this pool.
     *
     * @param product
     *  The Product to assign as the top-level product for this pool.
     */
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

    public Set<ProductAttribute> getProductAttributes() {
        return this.getProduct() != null ?
            this.getProduct().getAttributes() :
            new HashSet<ProductAttribute>();
    }

    public Set<ProductAttribute> getDerivedProductAttributes() {
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

    public void setDerivedProduct(Product derived) {
        this.derivedProduct = derived;
    }

    @JsonIgnore
    public Set<Product> getDerivedProvidedProducts() {
        return derivedProvidedProducts;
    }

    public void setDerivedProvidedProducts(Set<Product> derivedProvidedProducts) {
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
        Set<ProvidedProduct> prods = new HashSet<ProvidedProduct>();
        Map<String, ProvidedProduct> prodMap = new HashMap<String, ProvidedProduct>();

        for (Product p : getDerivedProvidedProducts()) {
            ProvidedProduct product = new ProvidedProduct(p);
            prods.add(product);
            prodMap.put(product.getProductId(), product);
        }

        if (this.derivedProvidedProductDtos != null) {
            for (ProvidedProduct product : this.derivedProvidedProductDtos) {
                if (!prodMap.containsKey(product.getProductId())) {
                    prods.add(product);
                }
            }
        }

        return prods;
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
        if (hasAttribute(DERIVED_POOL_ATTRIBUTE)) {
            if (hasAttribute(UNMAPPED_GUESTS_ATTRIBUTE)) {
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
        else if (hasAttribute(DEVELOPMENT_POOL_ATTRIBUTE)) {
            return PoolType.DEVELOPMENT;
        }
        return PoolType.NORMAL;
    }

    public PoolComplianceType getComplianceType() {
        Product product = this.getProduct();

        if (product != null) {
            boolean isStacking = product.hasAttribute(STACKING_ATTRIBUTE);
            boolean isMultiEnt = "yes".equalsIgnoreCase(
                product.getAttributeValue(MULTI_ENTITLEMENT_ATTRIBUTE)
            );

            if (product.hasAttribute(INSTANCE_ATTRIBUTE)) {
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
        return (this.getProduct() != null ? this.getProduct().hasAttribute(STACKING_ATTRIBUTE) : false);
    }

    public String getStackId() {
        return (this.getProduct() != null ? this.getProduct().getAttributeValue(STACKING_ATTRIBUTE) : null);
    }

    public boolean isUnmappedGuestPool() {
        return "true".equalsIgnoreCase(this.getAttributeValue(UNMAPPED_GUESTS_ATTRIBUTE));
    }

    public boolean isDevelopmentPool() {
        return "true".equalsIgnoreCase(this.getAttributeValue(DEVELOPMENT_POOL_ATTRIBUTE));
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
