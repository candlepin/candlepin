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
package org.candlepin.dto.manifest.v1;

import org.candlepin.dto.TimestampedCandlepinDTO;
import org.candlepin.jackson.CandlepinAttributeDeserializer;
import org.candlepin.jackson.CandlepinLegacyAttributeSerializer;
import org.candlepin.util.MapView;
import org.candlepin.util.SetView;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

import tools.jackson.databind.annotation.JsonDeserialize;
import tools.jackson.databind.annotation.JsonSerialize;

import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;


/**
 * A DTO representation of the Pool entity as used by the manifest import/export framework.
 */
public class PoolDTO extends TimestampedCandlepinDTO<PoolDTO> {
    public static final long serialVersionUID = 1L;

    /**
     * Internal DTO object for ProvidedProduct
     */
    public static class ProvidedProductDTO {
        private final String productId;
        private final String productName;

        @JsonCreator
        public ProvidedProductDTO(
            @JsonProperty("productId") String productId,
            @JsonProperty("productName") String productName) {
            if (productId == null || productId.isEmpty()) {
                throw new IllegalArgumentException("The product id is null or empty.");
            }

            this.productId = productId;
            this.productName = productName;
        }

        public String getProductId() {
            return this.productId;
        }

        public String getProductName() {
            return this.productName;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this) {
                return true;
            }

            if (obj instanceof ProvidedProductDTO) {
                ProvidedProductDTO that = (ProvidedProductDTO) obj;

                EqualsBuilder builder = new EqualsBuilder()
                    .append(this.getProductId(), that.getProductId())
                    .append(this.getProductName(), that.getProductName());

                return builder.isEquals();
            }

            return false;
        }

        @Override
        public int hashCode() {
            HashCodeBuilder builder = new HashCodeBuilder(37, 7)
                .append(this.getProductId())
                .append(this.getProductName());

            return builder.toHashCode();
        }
    }

    private String id;
    private String type;
    private OwnerDTO owner;
    private Boolean activeSubscription;
    private EntitlementDTO sourceEntitlement;
    private Long quantity;

    private Map<String, String> attributes;
    private String restrictedToUsername;
    private String contractNumber;
    private String accountNumber;
    private String orderNumber;
    private Long consumed;
    private Long exported;

    private Set<BrandingDTO> branding;

    private Map<String, String> calculatedAttributes;
    private String upstreamPoolId;
    private String upstreamEntitlementId;
    private String upstreamConsumerId;

    private String productName;
    private String productId;

    private Map<String, String> productAttributes;

    private String stackId;
    private Boolean stacked;
    private String sourceStackId;

    private Map<String, String> derivedProductAttributes;

    private String derivedProductId;
    private String derivedProductName;

    private Set<ProvidedProductDTO> providedProducts;
    private Set<ProvidedProductDTO> derivedProvidedProducts;

    private String subscriptionSubKey;
    private String subscriptionId;

    private Date startDate;
    private Date endDate;

    private CertificateDTO certificate;

    /**
     * Initializes a new PoolDTO instance with null values.
     */
    public PoolDTO() {
        // Intentionally left empty
    }

    /**
     * Initializes a new PoolDTO instance which is a shallow copy of the provided
     * source entity.
     *
     * @param source
     *  The source entity to copy
     */
    public PoolDTO(PoolDTO source) {
        super(source);
    }

    /**
     * Returns the internal db id.
     *
     * @return the db id.
     */
    public String getId() {
        return id;
    }

    /**
     * Sets the internal db id.
     *
     * @param id new db id.
     *
     * @return a reference to this PoolDTO object.
     */
    public PoolDTO setId(String id) {
        this.id = id;
        return this;
    }

    /**
     * Returns the pool's type.
     *
     * @return this pool's type.
     */
    @JsonProperty
    public String getType() {
        return type;
    }

    /**
     * Sets the pool's type.
     *
     * @param type set the pool type.
     *
     * @return a reference to this PoolDTO object.
     */
    @JsonIgnore
    public PoolDTO setType(String type) {
        this.type = type;
        return this;
    }

    /**
     * Return the owner of this pool.
     *
     * @return the owner of this pool.
     */
    public OwnerDTO getOwner() {
        return owner;
    }

    /**
     * Sets the owner of this pool.
     *
     * @param owner set the owner of this pool.
     *
     * @return a reference to this PoolDTO object.
     */
    public PoolDTO setOwner(OwnerDTO owner) {
        this.owner = owner;
        return this;
    }

    /**
     * Returns true if this pool represents an active subscription, false otherwise.
     *
     * @return true if this pool represents an active subscription, false otherwise.
     */
    public Boolean isActiveSubscription() {
        return activeSubscription;
    }

    /**
     * Sets if this pool represents an active subscription or not.
     *
     * @param activeSubscription set if this pool represents an active subscription or not.
     *
     * @return a reference to this PoolDTO object.
     */
    public PoolDTO setActiveSubscription(Boolean activeSubscription) {
        this.activeSubscription = activeSubscription;
        return this;
    }

    /**
     * Returns the source entitlement of this pool.
     *
     * @return the source entitlement.
     */
    public EntitlementDTO getSourceEntitlement() {
        return sourceEntitlement;
    }

    /**
     * Sets the source entitlement of this pool.
     *
     * @param sourceEntitlement set the source entitlement.
     *
     * @return a reference to this PoolDTO object.
     */
    public PoolDTO setSourceEntitlement(EntitlementDTO sourceEntitlement) {
        this.sourceEntitlement = sourceEntitlement;
        return this;
    }

    /**
     * Return this pool's quantity.
     *
     * @return this pool's quantity.
     */
    public Long getQuantity() {
        return quantity;
    }

    /**
     * Sets this pool's quantity.
     *
     * @param quantity set this pool's quantity.
     *
     * @return a reference to this PoolDTO object.
     */
    public PoolDTO setQuantity(Long quantity) {
        this.quantity = quantity;
        return this;
    }

    /**
     * Retrieves a view of the attributes for the pool represented by this DTO. If the attributes
     * have not yet been defined, this method returns null.
     * <p></p>
     * Note that the collection returned by this method is a view of the collection backing this
     * set of attributes. Elements cannot be added to the collection, but elements may be removed.
     * Changes made to the collection will be reflected by this pool DTO instance.
     *
     * @return the attributes associated with this pool, or null if they have not yet been defined.
     */
    @JsonSerialize(using = CandlepinLegacyAttributeSerializer.class)
    public Map<String, String> getAttributes() {
        return this.attributes != null ? new MapView<>(this.attributes) : null;
    }

    /**
     * Sets the attributes for this pool DTO.
     *
     * @param attributes
     *  A map of attribute key, value pairs to assign to this pool DTO, or null to clear the
     *  existing ones
     *
     * @return a reference to this PoolDTO object.
     */
    @JsonDeserialize(using = CandlepinAttributeDeserializer.class)
    public PoolDTO setAttributes(Map<String, String> attributes) {
        if (attributes != null) {
            if (this.attributes == null) {
                this.attributes = new HashMap<>();
            }
            else {
                this.attributes.clear();
            }
            this.attributes.putAll(attributes);
        }
        else {
            this.attributes = null;
        }
        return this;
    }

    /**
     * Checks if the given attribute has been defined on this poolDTO.
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
    @JsonIgnore
    public boolean hasAttribute(String key) {
        if (key == null) {
            throw new IllegalArgumentException("key is null");
        }

        if (this.attributes == null) {
            return false;
        }

        return this.attributes.containsKey(key);
    }

    /**
     * Removes the given attribute from this pool DTO.
     *
     * @param key
     *  The key (name) of the attribute to remove
     *
     * @return
     *  True if the attribute was removed; false otherwise.
     */
    @JsonIgnore
    public boolean removeAttribute(String key) {
        if (key == null) {
            throw new IllegalArgumentException("key is null");
        }

        if (this.attributes == null) {
            return false;
        }

        boolean present = this.attributes.containsKey(key);
        this.attributes.remove(key);

        return present;
    }

    /**
     * Returns the username of the user to which this pool is restricted.
     *
     * @return the username of the user to which this pool is restricted.
     */
    public String getRestrictedToUsername() {
        return restrictedToUsername;
    }

    /**
     * Sets the username of the user to which this pool is restricted.
     *
     * @param restrictedToUsername the username of the user to which this pool is restricted.
     *
     * @return a reference to this PoolDTO object.
     */
    public PoolDTO setRestrictedToUsername(String restrictedToUsername) {
        this.restrictedToUsername = restrictedToUsername;
        return this;
    }


    /**
     * Return the contract number for this pool's subscription.
     *
     * @return the contract number.
     */
    public String getContractNumber() {
        return contractNumber;
    }

    /**
     * Sets the contract number for this pool's subscription.
     *
     * @param contractNumber set the contract number of this subscription
     *
     * @return a reference to this PoolDTO object.
     */
    public PoolDTO setContractNumber(String contractNumber) {
        this.contractNumber = contractNumber;
        return this;
    }

    /**
     * Returns this pool's account number.
     *
     * @return this pool's account number.
     */
    public String getAccountNumber() {
        return accountNumber;
    }

    /**
     * Sets this pool's account number.
     *
     * @param accountNumber the account number to set on this pool.
     *
     * @return a reference to this PoolDTO object.
     */
    public PoolDTO setAccountNumber(String accountNumber) {
        this.accountNumber = accountNumber;
        return this;
    }

    /**
     * Returns this pool's order number.
     *
     * @return this pool's order number.
     */
    public String getOrderNumber() {
        return orderNumber;
    }

    /**
     * Sets this pool's order number.
     *
     * @param orderNumber the order number to set on this pool.
     *
     * @return a reference to this PoolDTO object.
     */
    public PoolDTO setOrderNumber(String orderNumber) {
        this.orderNumber = orderNumber;
        return this;
    }

    /**
     * Retrieves a view of the branding for the pool represented by this DTO. If the branding items
     * have not yet been defined, this method returns null.
     * <p></p>
     * Note that the collection returned by this method is a view of the collection backing this
     * set of branding items. Elements cannot be added to the collection, but elements may be removed.
     * Changes made to the collection will be reflected by this pool DTO instance.
     *
     * @return
     *  the branding items associated with this key, or null if they have not yet been defined
     */
    public Set<BrandingDTO> getBranding() {
        return this.branding != null ? new SetView<>(this.branding) : null;
    }

    /**
     * Adds the collection of branding items to this Pool DTO.
     *
     * @param branding
     *  A set of branding items to attach to this DTO, or null to clear the existing ones
     *
     * @return
     *  A reference to this DTO
     */
    public PoolDTO setBranding(Set<BrandingDTO> branding) {
        if (branding != null) {
            if (this.branding == null) {
                this.branding = new HashSet<>();
            }
            else {
                this.branding.clear();
            }

            for (BrandingDTO dto : branding) {
                if (isNullOrIncomplete(dto)) {
                    throw new IllegalArgumentException(
                        "collection contains null or incomplete branding objects");
                }
            }

            this.branding.addAll(branding);
        }
        else {
            this.branding = null;
        }
        return this;
    }

    /**
     * Adds the given branding to this pool DTO.
     *
     * @param branding
     *  The branding to add to this pool DTO.
     *
     * @return
     *  true if this branding was not already contained in this pool DTO.
     */
    @JsonIgnore
    public boolean addBranding(BrandingDTO branding) {
        if (isNullOrIncomplete(branding)) {
            throw new IllegalArgumentException("branding is null or incomplete");
        }

        if (this.branding == null) {
            this.branding = new HashSet<>();
        }

        return this.branding.add(branding);
    }

    /**
     * Utility method to validate BrandingDTO input
     */
    private boolean isNullOrIncomplete(BrandingDTO branding) {
        return branding == null ||
            branding.getProductId() == null || branding.getProductId().isEmpty() ||
            branding.getName() == null || branding.getName().isEmpty() ||
            branding.getType() == null || branding.getType().isEmpty();
    }

    /**
     * Returns the quantity of the pool that is currently consumed.
     *
     * @return the quantity currently consumed.
     */
    public Long getConsumed() {
        return consumed;
    }

    /**
     * Sets the quantity of the pool that is currently consumed.
     *
     * @param consumed set the activated uses.
     *
     * @return a reference to this PoolDTO object.
     */
    public PoolDTO setConsumed(Long consumed) {
        this.consumed = consumed;
        return this;
    }

    /**
     * Returns the quantity of the pool that is currently exported.
     *
     * @return quantity currently exported.
     */
    public Long getExported() {
        return exported;
    }

    /**
     * Sets the quantity of the pool that is currently exported.
     *
     * @param exported set the activated uses.
     *
     * @return a reference to this PoolDTO object.
     */
    public PoolDTO setExported(Long exported) {
        this.exported = exported;
        return this;
    }

    /**
     * Retrieves a view of the calculated attributes for the pool represented by this DTO. If the calculated
     * attributes have not yet been defined, this method returns null.
     * <p></p>
     * Note that the collection returned by this method is a view of the collection backing this
     * set of calculated attributes. Elements cannot be added to the collection, but elements may be removed.
     * Changes made to the collection will be reflected by this pool DTO instance.
     *
     * @return the calculated attributes associated with this pool,
     * or null if the they have not yet been defined.
     */
    public Map<String, String> getCalculatedAttributes() {
        return this.calculatedAttributes != null ? new MapView<>(this.calculatedAttributes) : null;
    }

    /**
     * Sets the calculated attributes for this pool DTO.
     *
     * @param calculatedAttributes
     *  A map of calculated attribute key, value pairs to assign to this pool DTO, or null to clear the
     *  existing ones
     *
     * @return a reference to this PoolDTO object.
     */
    public PoolDTO setCalculatedAttributes(Map<String, String> calculatedAttributes) {
        if (calculatedAttributes != null) {
            if (this.calculatedAttributes == null) {
                this.calculatedAttributes = new HashMap<>();
            }
            else {
                this.calculatedAttributes.clear();
            }
            this.calculatedAttributes.putAll(calculatedAttributes);
        }
        else {
            this.calculatedAttributes = null;
        }
        return this;
    }

    /**
     * Returns the upstream pool id.
     *
     * @return the upstream pool id.
     */
    public String getUpstreamPoolId() {
        return upstreamPoolId;
    }

    /**
     * Sets the upstream pool id.
     *
     * @param upstreamPoolId set the upstream pool id.
     *
     * @return a reference to this PoolDTO object.
     */
    public PoolDTO setUpstreamPoolId(String upstreamPoolId) {
        this.upstreamPoolId = upstreamPoolId;
        return this;
    }

    /**
     * Returns the upstream entitlement id.
     *
     * @return the upstream entitlement id.
     */
    public String getUpstreamEntitlementId() {
        return upstreamEntitlementId;
    }

    /**
     * Sets the upstream entitlement id.
     *
     * @param upstreamEntitlementId set the upstream entitlement id.
     *
     * @return a reference to this PoolDTO object.
     */
    public PoolDTO setUpstreamEntitlementId(String upstreamEntitlementId) {
        this.upstreamEntitlementId = upstreamEntitlementId;
        return this;
    }

    /**
     * Returns the upstream consumer id.
     *
     * @return the upstream consumer id.
     */
    public String getUpstreamConsumerId() {
        return upstreamConsumerId;
    }

    /**
     * Sets the upstream consumer id.
     *
     * @param upstreamConsumerId set the upstream consumer id.
     *
     * @return a reference to this PoolDTO object.
     */
    public PoolDTO setUpstreamConsumerId(String upstreamConsumerId) {
        this.upstreamConsumerId = upstreamConsumerId;
        return this;
    }

    /**
     * The Marketing/Operations product name for the
     * <code>id</code>.
     *
     * @return the name
     */
    @JsonProperty
    public String getProductName() {
        return productName;
    }

    /**
     * Set the Marketing/Operations product name for the
     * <code>id</code>.
     *
     * @param productName set the productName
     *
     * @return a reference to this PoolDTO object.
     */
    @JsonIgnore
    public PoolDTO setProductName(String productName) {
        this.productName = productName;
        return this;
    }

    /**
     * Return the "top level" product this pool is for.
     * Note that pools can also provide access to other products.
     * See getProvidedProducts().
     *
     * @return Top level product ID.
     */
    public String getProductId() {
        return productId;
    }

    /**
     * Set the "top level" product this pool is for.
     * Note that pools can also provide access to other products.
     * See getProvidedProducts().
     *
     * @param productId Top level product ID.
     *
     * @return a reference to this PoolDTO object.
     */
    public PoolDTO setProductId(String productId) {
        this.productId = productId;
        return this;
    }

    /**
     * Retrieves a view of the product attributes for the pool represented by this DTO. If the product
     * attributes have not yet been defined, this method returns null.
     * <p></p>
     * Note that the collection returned by this method is a view of the collection backing this
     * set of product attributes. Elements cannot be added to the collection, but elements may be removed.
     * Changes made to the collection will be reflected by this pool DTO instance.
     *
     * @return the product attributes associated with this pool,
     * or null if they have not yet been defined.
     */
    @JsonSerialize(using = CandlepinLegacyAttributeSerializer.class)
    public Map<String, String> getProductAttributes() {
        return this.productAttributes != null ? new MapView<>(this.productAttributes) : null;
    }

    /**
     * Sets the product attributes for this pool DTO.
     *
     * @param productAttributes
     *  A map of product attribute key, value pairs to assign to this pool DTO, or null to clear the
     *  existing ones
     *
     * @return a reference to this PoolDTO object.
     */
    @JsonDeserialize(using = CandlepinAttributeDeserializer.class)
    public PoolDTO setProductAttributes(Map<String, String> productAttributes) {
        if (productAttributes != null) {
            if (this.productAttributes == null) {
                this.productAttributes = new HashMap<>();
            }
            else {
                this.productAttributes.clear();
            }
            this.productAttributes.putAll(productAttributes);
        }
        else {
            this.productAttributes = null;
        }
        return this;
    }

    /**
     * Checks if the given product attribute has been defined on this poolDTO.
     *
     * @param key
     *  The key (name) of the product attribute to lookup
     *
     * @throws IllegalArgumentException
     *  if key is null
     *
     * @return
     *  true if the product attribute is defined for this pool; false otherwise
     */
    @JsonIgnore
    public boolean hasProductAttribute(String key) {
        if (key == null) {
            throw new IllegalArgumentException("key is null");
        }

        if (this.productAttributes == null) {
            return false;
        }

        return this.productAttributes.containsKey(key);
    }

    /**
     * Returns the identifier of stacked products/pools.
     *
     * @return the identifier of stacked products/pools.
     */
    @JsonProperty
    public String getStackId() {
        return stackId;
    }

    /**
     * Sets the identifier of stacked products/pools.
     *
     * @param stackId set the identifier of stacked products/pools.
     *
     * @return a reference to this PoolDTO object.
     */
    @JsonIgnore
    public PoolDTO setStackId(String stackId) {
        this.stackId = stackId;
        return this;
    }

    /**
     * Returns true if this pool is stacked, false otherwise.
     *
     * @return true if this pool is stacked, false otherwise.
     */
    @JsonProperty
    public Boolean isStacked() {
        return stacked;
    }

    /**
     * Sets if this pool is stacked or not.
     *
     * @param stacked set if this pool is stacked or not.
     *
     * @return a reference to this PoolDTO object.
     */
    @JsonIgnore
    public PoolDTO setStacked(Boolean stacked) {
        this.stacked = stacked;
        return this;
    }

    /**
     * Returns the source stack id of this pool.
     *
     * @return the source stack id of this pool.
     */
    @JsonProperty
    public String getSourceStackId() {
        return sourceStackId;
    }

    /**
     * Sets the source stack id of this pool.
     *
     * @param sourceStackId set the source stack id of this pool.
     *
     * @return a reference to this PoolDTO object.
     */
    @JsonIgnore
    public PoolDTO setSourceStackId(String sourceStackId) {
        this.sourceStackId = sourceStackId;
        return this;
    }

    /**
     * Retrieves a view of the derived product attributes for the pool represented by this DTO.
     * If the derived product attributes have not yet been defined, this method returns null.
     * <p></p>
     * Note that the collection returned by this method is a view of the collection backing this
     * set of derived product attributes. Elements cannot be added to the collection,
     * but elements may be removed.
     * Changes made to the collection will be reflected by this pool DTO instance.
     *
     * @return the derived product attributes associated with this pool,
     * or null if they have not yet been defined.
     */
    @JsonSerialize(using = CandlepinLegacyAttributeSerializer.class)
    public Map<String, String> getDerivedProductAttributes() {
        return this.derivedProductAttributes != null ? new MapView<>(this.derivedProductAttributes) : null;
    }

    /**
     * Sets the derived product attributes for this pool DTO.
     *
     * @param derivedProductAttributes
     *  A map of derived product attribute key, value pairs to assign to this pool DTO,
     *  or null to clear the existing ones
     *
     * @return a reference to this PoolDTO object.
     */
    @JsonDeserialize(using = CandlepinAttributeDeserializer.class)
    public PoolDTO setDerivedProductAttributes(Map<String, String> derivedProductAttributes) {
        if (derivedProductAttributes != null) {
            if (this.derivedProductAttributes == null) {
                this.derivedProductAttributes = new HashMap<>();
            }
            else {
                this.derivedProductAttributes.clear();
            }
            this.derivedProductAttributes.putAll(derivedProductAttributes);
        }
        else {
            this.derivedProductAttributes = null;
        }
        return this;
    }

    /**
     * Returns the derived product id of this pool.
     *
     * @return the derived product id of this pool.
     */
    public String getDerivedProductId() {
        return derivedProductId;
    }

    /**
     * Sets the derived product id of this pool.
     *
     * @param derivedProductId set the derived product id of this pool.
     *
     * @return a reference to this PoolDTO object.
     */
    public PoolDTO setDerivedProductId(String derivedProductId) {
        this.derivedProductId = derivedProductId;
        return this;
    }

    /**
     * Returns the derived product name of this pool.
     *
     * @return the derived product name of this pool.
     */
    @JsonProperty
    public String getDerivedProductName() {
        return derivedProductName;
    }

    /**
     * Sets the derived product name of this pool.
     *
     * @param derivedProductName set the derived product name of this pool.
     *
     * @return a reference to this PoolDTO object.
     */
    @JsonIgnore
    public PoolDTO setDerivedProductName(String derivedProductName) {
        this.derivedProductName = derivedProductName;
        return this;
    }

    /**
     * Retrieves a view of the provided products for the pool represented by this DTO.
     * If the provided products have not yet been defined, this method returns null.
     * <p></p>
     * Note that the collection returned by this method is a view of the collection backing this
     * set of provided products. Elements cannot be added to the collection, but elements may be removed.
     * Changes made to the collection will be reflected by this pool DTO instance.
     *
     * @return
     *  the provided products associated with this key, or null if they have not yet been defined
     */
    public Set<ProvidedProductDTO> getProvidedProducts() {
        return this.providedProducts != null ? new SetView<>(this.providedProducts) : null;
    }

    /**
     * Adds the collection of provided products to this Pool DTO.
     *
     * @param providedProducts
     *  A set of provided products to attach to this DTO, or null to clear the existing ones
     *
     * @return
     *  A reference to this DTO
     */
    public PoolDTO setProvidedProducts(Collection<ProvidedProductDTO> providedProducts) {
        if (providedProducts != null) {
            if (this.providedProducts == null) {
                this.providedProducts = new HashSet<>();
            }
            else {
                this.providedProducts.clear();
            }

            for (ProvidedProductDTO dto : providedProducts) {
                if (isNullOrIncomplete(dto)) {
                    throw new IllegalArgumentException(
                        "collection contains null or incomplete provided products");
                }
            }

            this.providedProducts.addAll(providedProducts);
        }
        else {
            this.providedProducts = null;
        }
        return this;
    }

    /**
     * Adds the given provided product to this pool DTO.
     *
     * @param providedProduct
     *  The provided product to add to this pool DTO.
     *
     * @return
     *  true if this provided product was not already contained in this pool DTO.
     */
    @JsonIgnore
    public boolean addProvidedProduct(ProvidedProductDTO providedProduct) {
        if (isNullOrIncomplete(providedProduct)) {
            throw new IllegalArgumentException("providedProduct is null or incomplete");
        }

        if (this.providedProducts == null) {
            this.providedProducts = new HashSet<>();
        }

        return this.providedProducts.add(providedProduct);
    }

    /**
     * Retrieves a view of the derived provided products for the pool represented by this DTO.
     * If the derived provided products have not yet been defined, this method returns null.
     * <p></p>
     * Note that the collection returned by this method is a view of the collection backing this
     * set of derived provided products. Elements cannot be added to the collection,
     * but elements may be removed.
     * Changes made to the collection will be reflected by this pool DTO instance.
     *
     * @return
     *  the derived provided products associated with this key, or null if they have not yet been defined
     */
    public Set<ProvidedProductDTO> getDerivedProvidedProducts() {
        return this.derivedProvidedProducts != null ?
            (new SetView<>(this.derivedProvidedProducts)) : null;
    }

    /**
     * Adds the collection of derived provided products to this Pool DTO.
     *
     * @param derivedProvidedProducts
     *  A set of derived provided products to attach to this DTO, or null to clear the existing ones
     *
     * @return
     *  A reference to this DTO
     */
    public PoolDTO setDerivedProvidedProducts(Collection<ProvidedProductDTO> derivedProvidedProducts) {
        if (derivedProvidedProducts != null) {
            if (this.derivedProvidedProducts == null) {
                this.derivedProvidedProducts = new HashSet<>();
            }
            else {
                this.derivedProvidedProducts.clear();
            }

            for (ProvidedProductDTO dto : derivedProvidedProducts) {
                if (isNullOrIncomplete(dto)) {
                    throw new IllegalArgumentException(
                        "collection contains null or incomplete derived provided products");
                }
            }

            this.derivedProvidedProducts.addAll(derivedProvidedProducts);
        }
        else {
            this.derivedProvidedProducts = null;
        }
        return this;
    }

    /**
     * Adds the given derived provided product to this pool DTO.
     *
     * @param derivedProvidedProduct
     *  The derived provided product to add to this pool DTO.
     *
     * @return
     *  true if this derived provided product was not already contained in this pool DTO.
     */
    @JsonIgnore
    public boolean addDerivedProvidedProduct(ProvidedProductDTO derivedProvidedProduct) {
        if (isNullOrIncomplete(derivedProvidedProduct)) {
            throw new IllegalArgumentException("derivedProvidedProduct is null or incomplete");
        }

        if (this.derivedProvidedProducts == null) {
            this.derivedProvidedProducts = new HashSet<>();
        }

        return this.derivedProvidedProducts.add(derivedProvidedProduct);
    }

    /**
     * Utility method to validate ProvidedProductDTO input
     */
    private boolean isNullOrIncomplete(ProvidedProductDTO derivedProvidedProduct) {
        return derivedProvidedProduct == null ||
            derivedProvidedProduct.getProductId() == null ||
            derivedProvidedProduct.getProductId().isEmpty();
    }

    /**
     * Returns the subscription key of this pool.
     *
     * @return the subscription key of this pool.
     */
    public String getSubscriptionSubKey() {
        return subscriptionSubKey;
    }

    /**
     * Sets the subscription key of this pool.
     *
     * @param subscriptionSubKey set the subscription key of this pool.
     *
     * @return a reference to this PoolDTO object.
     */
    public PoolDTO setSubscriptionSubKey(String subscriptionSubKey) {
        this.subscriptionSubKey = subscriptionSubKey;
        return this;
    }

    /**
     * Returns the subscription id of this pool.
     *
     * @return the subscription id associated with this pool.
     */
    public String getSubscriptionId() {
        return subscriptionId;
    }

    /**
     * Sets the subscription id of this pool.
     *
     * @param subscriptionId set the subscription id associated with this pool.
     *
     * @return a reference to this PoolDTO object.
     */
    public PoolDTO setSubscriptionId(String subscriptionId) {
        this.subscriptionId = subscriptionId;
        return this;
    }

    /**
     * Returns the start date of this pool.
     *
     * @return Returns the startDate of this pool.
     */
    public Date getStartDate() {
        return startDate;
    }

    /**
     * Sets the start date of this pool.
     *
     * @param startDate the startDate of this pool.
     *
     * @return a reference to this PoolDTO object.
     */
    public PoolDTO setStartDate(Date startDate) {
        this.startDate = startDate;
        return this;
    }

    /**
     * Returns the end date of this pool.
     *
     * @return Returns the endDate of this pool.
     */
    public Date getEndDate() {
        return endDate;
    }

    /**
     * Sets the end date of this pool.
     *
     * @param endDate the endDate of this pool.
     *
     * @return a reference to this PoolDTO object.
     */
    public PoolDTO setEndDate(Date endDate) {
        this.endDate = endDate;
        return this;
    }

    /**
     * Returns the Subscription Certificate of this pool.
     *
     * @return the subscription certificate of this pool.
     */
    @JsonIgnore
    public CertificateDTO getCertificate() {
        return this.certificate;
    }

    /**
     * Sets the Subscription Certificate of this pool.
     *
     * @param certificate the Subscription Certificate to set on this pool.
     *
     * @return a reference to this PoolDTO object.
     */
    @JsonProperty
    public PoolDTO setCertificate(CertificateDTO certificate) {
        this.certificate = certificate;
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return String.format("PoolDTO [id: %s, type: %s, product id: %s, product name: %s, quantity: %s]",
            this.getId(), this.getType(), this.getProductId(), this.getProductName(),
            this.getQuantity());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }

        if (obj instanceof PoolDTO && super.equals(obj)) {
            PoolDTO that = (PoolDTO) obj;

            // We are not interested in making sure nested objects are equal; we're only
            // concerned with the reference to such an object.
            String thisOwnerId = this.getOwner() != null ? this.getOwner().getId() : null;
            String thatOwnerId = that.getOwner() != null ? that.getOwner().getId() : null;

            String thisSourceEntitlementId =
                this.getSourceEntitlement() != null ? this.getSourceEntitlement().getId() : null;
            String thatSourceEntitlementId =
                that.getSourceEntitlement() != null ? that.getSourceEntitlement().getId() : null;

            String thisCertificateId =
                this.getCertificate() != null ? this.getCertificate().getId() : null;
            String thatCertificateId =
                that.getCertificate() != null ? that.getCertificate().getId() : null;

            EqualsBuilder builder = new EqualsBuilder()
                .append(this.getId(), that.getId())
                .append(this.getType(), that.getType())
                .append(thisOwnerId, thatOwnerId)
                .append(this.isActiveSubscription(), that.isActiveSubscription())
                .append(thisSourceEntitlementId, thatSourceEntitlementId)
                .append(this.getQuantity(), that.getQuantity())
                .append(this.getStartDate(), that.getStartDate())
                .append(this.getEndDate(), that.getEndDate())
                .append(this.getAttributes(), that.getAttributes())
                .append(this.getRestrictedToUsername(), that.getRestrictedToUsername())
                .append(this.getContractNumber(), that.getContractNumber())
                .append(this.getAccountNumber(), that.getAccountNumber())
                .append(this.getOrderNumber(), that.getOrderNumber())
                .append(this.getConsumed(), that.getConsumed())
                .append(this.getExported(), that.getExported())
                .append(this.getBranding(), that.getBranding())
                .append(this.getCalculatedAttributes(), that.getCalculatedAttributes())
                .append(this.getUpstreamPoolId(), that.getUpstreamPoolId())
                .append(this.getUpstreamEntitlementId(), that.getUpstreamEntitlementId())
                .append(this.getUpstreamConsumerId(), that.getUpstreamConsumerId())
                .append(this.getProductName(), that.getProductName())
                .append(this.getProductId(), that.getProductId())
                .append(this.getProductAttributes(), that.getProductAttributes())
                .append(this.getStackId(), that.getStackId())
                .append(this.isStacked(), that.isStacked())
                .append(this.getDerivedProductAttributes(), that.getDerivedProductAttributes())
                .append(this.getDerivedProductId(), that.getDerivedProductId())
                .append(this.getDerivedProductName(), that.getDerivedProductName())
                .append(this.getProvidedProducts(), that.getProvidedProducts())
                .append(this.getDerivedProvidedProducts(), that.getDerivedProvidedProducts())
                .append(this.getSourceStackId(), that.getSourceStackId())
                .append(this.getSubscriptionSubKey(), that.getSubscriptionSubKey())
                .append(this.getSubscriptionId(), that.getSubscriptionId())
                .append(thisCertificateId, thatCertificateId);

            return builder.isEquals();
        }

        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        HashCodeBuilder builder = new HashCodeBuilder(37, 7)
            .append(super.hashCode())
            .append(this.getId())
            .append(this.getType())
            .append(this.getOwner() != null ? this.getOwner().getId() : null)
            .append(this.isActiveSubscription())
            .append(this.getSourceEntitlement() != null ? this.getSourceEntitlement().getId() : null)
            .append(this.getQuantity())
            .append(this.getStartDate())
            .append(this.getEndDate())
            .append(this.getAttributes())
            .append(this.getRestrictedToUsername())
            .append(this.getContractNumber())
            .append(this.getAccountNumber())
            .append(this.getOrderNumber())
            .append(this.getConsumed())
            .append(this.getExported())
            .append(this.getBranding())
            .append(this.getCalculatedAttributes())
            .append(this.getUpstreamPoolId())
            .append(this.getUpstreamEntitlementId())
            .append(this.getUpstreamConsumerId())
            .append(this.getProductName())
            .append(this.getProductId())
            .append(this.getProductAttributes())
            .append(this.getStackId())
            .append(this.isStacked())
            .append(this.getDerivedProductAttributes())
            .append(this.getDerivedProductId())
            .append(this.getDerivedProductName())
            .append(this.getProvidedProducts())
            .append(this.getDerivedProvidedProducts())
            .append(this.getSourceStackId())
            .append(this.getSubscriptionSubKey())
            .append(this.getSubscriptionId())
            .append(this.getCertificate() != null ? this.getCertificate().getId() : null);

        return builder.toHashCode();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public PoolDTO clone() {
        PoolDTO copy = super.clone();

        OwnerDTO owner = this.getOwner();
        copy.setOwner(owner != null ? owner.clone() : null);

        CertificateDTO certificate = this.getCertificate();
        copy.setCertificate(certificate != null ? certificate.clone() : null);

        Date startDate = this.getStartDate();
        copy.setStartDate(startDate != null ? (Date) startDate.clone() : null);

        Date endDate = this.getEndDate();
        copy.setEndDate(endDate != null ? (Date) endDate.clone() : null);

        copy.setAttributes(this.getAttributes());
        copy.setCalculatedAttributes(this.getCalculatedAttributes());
        copy.setProductAttributes(this.getProductAttributes());
        copy.setDerivedProductAttributes(this.getDerivedProductAttributes());
        copy.setBranding(this.getBranding());
        copy.setProvidedProducts(this.getProvidedProducts());
        copy.setDerivedProvidedProducts(this.getDerivedProvidedProducts());

        return copy;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public PoolDTO populate(PoolDTO source) {
        super.populate(source);

        this.setId(source.getId());
        this.setType(source.getType());
        this.setOwner(source.getOwner());
        this.setActiveSubscription(source.isActiveSubscription());
        this.setSourceEntitlement(source.getSourceEntitlement());
        this.setQuantity(source.getQuantity());
        this.setStartDate(source.getStartDate());
        this.setEndDate(source.getEndDate());
        this.setAttributes(source.getAttributes());
        this.setRestrictedToUsername(source.getRestrictedToUsername());
        this.setContractNumber(source.getContractNumber());
        this.setAccountNumber(source.getAccountNumber());
        this.setOrderNumber(source.getOrderNumber());
        this.setConsumed(source.getConsumed());
        this.setExported(source.getExported());
        this.setBranding(source.getBranding());
        this.setCalculatedAttributes(source.getCalculatedAttributes());
        this.setUpstreamPoolId(source.getUpstreamPoolId());
        this.setUpstreamEntitlementId(source.getUpstreamEntitlementId());
        this.setUpstreamConsumerId(source.getUpstreamConsumerId());
        this.setProductName(source.getProductName());
        this.setProductId(source.getProductId());
        this.setProductAttributes(source.getProductAttributes());
        this.setStackId(source.getStackId());
        this.setStacked(source.isStacked());
        this.setDerivedProductAttributes(source.getDerivedProductAttributes());
        this.setDerivedProductId(source.getDerivedProductId());
        this.setDerivedProductName(source.getDerivedProductName());
        this.setProvidedProducts(source.getProvidedProducts());
        this.setDerivedProvidedProducts(source.getDerivedProvidedProducts());
        this.setSourceStackId(source.getSourceStackId());
        this.setSubscriptionSubKey(source.getSubscriptionSubKey());
        this.setSubscriptionId(source.getSubscriptionId());
        this.setCertificate(source.getCertificate());

        return this;
    }
}
