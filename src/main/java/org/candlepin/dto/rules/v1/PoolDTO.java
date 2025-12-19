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
package org.candlepin.dto.rules.v1;

import org.candlepin.dto.CandlepinDTO;
import org.candlepin.jackson.CandlepinAttributeDeserializer;
import org.candlepin.jackson.CandlepinLegacyAttributeSerializer;
import org.candlepin.util.MapView;
import org.candlepin.util.SetView;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonFilter;
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
 * A DTO representation of the Pool entity as used by the Rules framework.
 */
@JsonFilter("PoolFilter")
public class PoolDTO extends CandlepinDTO<PoolDTO> {
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
    private Long quantity;
    private Date startDate;
    private Date endDate;

    private Map<String, String> attributes;
    private String restrictedToUsername;
    private Long consumed;

    private String productId;

    private Map<String, String> productAttributes;

    private String derivedProductId;
    private Set<ProvidedProductDTO> providedProducts;
    private Set<ProvidedProductDTO> derivedProvidedProducts;

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
     * Return the date the pool became active.
     *
     * @return when the pool became active.
     */
    public Date getStartDate() {
        return startDate;
    }

    /**
     * Sets the date the pool became active.
     *
     * @param startDate set the date that the pool became active.
     *
     * @return a reference to this PoolDTO object.
     */
    public PoolDTO setStartDate(Date startDate) {
        this.startDate = startDate;
        return this;
    }

    /**
     * Return the date the pool expires.
     *
     * @return when the pool expires.
     */
    public Date getEndDate() {
        return endDate;
    }

    /**
     * Set the date the pool expires.
     *
     * @param endDate set the date that the pool expires.
     *
     * @return a reference to this PoolDTO object.
     */
    public PoolDTO setEndDate(Date endDate) {
        this.endDate = endDate;
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
        return this.derivedProvidedProducts != null ? new SetView<>(this.derivedProvidedProducts) : null;
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
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return String.format("PoolDTO [id: %s, product id: %s, quantity: %s]",
                this.getId(), this.getProductId(), this.getQuantity());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }

        if (obj instanceof PoolDTO) {
            PoolDTO that = (PoolDTO) obj;

            EqualsBuilder builder = new EqualsBuilder()
                .append(this.getId(), that.getId())
                .append(this.getQuantity(), that.getQuantity())
                .append(this.getStartDate(), that.getStartDate())
                .append(this.getEndDate(), that.getEndDate())
                .append(this.getAttributes(), that.getAttributes())
                .append(this.getRestrictedToUsername(), that.getRestrictedToUsername())
                .append(this.getConsumed(), that.getConsumed())
                .append(this.getProductId(), that.getProductId())
                .append(this.getProductAttributes(), that.getProductAttributes())
                .append(this.getDerivedProductId(), that.getDerivedProductId())
                .append(this.getProvidedProducts(), that.getProvidedProducts())
                .append(this.getDerivedProvidedProducts(), that.getDerivedProvidedProducts());

            return builder.isEquals();
        }

        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        // Like with the equals method, we are not interested in hashing nested objects; we're only
        // concerned with the reference to such an object.

        HashCodeBuilder builder = new HashCodeBuilder(37, 7)
            .append(this.getId())
            .append(this.getQuantity())
            .append(this.getStartDate())
            .append(this.getEndDate())
            .append(this.getAttributes())
            .append(this.getRestrictedToUsername())
            .append(this.getConsumed())
            .append(this.getProductId())
            .append(this.getProductAttributes())
            .append(this.getDerivedProductId())
            .append(this.getProvidedProducts())
            .append(this.getDerivedProvidedProducts());

        return builder.toHashCode();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public PoolDTO clone() {
        PoolDTO copy = super.clone();

        copy.setAttributes(this.getAttributes());
        copy.setProductAttributes(this.getProductAttributes());
        copy.setProvidedProducts(this.getProvidedProducts());
        copy.setDerivedProvidedProducts(this.getDerivedProvidedProducts());

        Date startDate = this.getStartDate();
        copy.setStartDate(startDate != null ? (Date) startDate.clone() : null);

        Date endDate = this.getEndDate();
        copy.setEndDate(endDate != null ? (Date) endDate.clone() : null);

        return copy;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public PoolDTO populate(PoolDTO source) {
        super.populate(source);

        this.setId(source.getId());
        this.setQuantity(source.getQuantity());
        this.setStartDate(source.getStartDate());
        this.setEndDate(source.getEndDate());
        this.setAttributes(source.getAttributes());
        this.setRestrictedToUsername(source.getRestrictedToUsername());
        this.setConsumed(source.getConsumed());
        this.setProductId(source.getProductId());
        this.setProductAttributes(source.getProductAttributes());
        this.setDerivedProductId(source.getDerivedProductId());
        this.setProvidedProducts(source.getProvidedProducts());
        this.setDerivedProvidedProducts(source.getDerivedProvidedProducts());

        return this;
    }
}
