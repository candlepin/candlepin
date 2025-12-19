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

import org.candlepin.dto.CandlepinDTO;
import org.candlepin.dto.TimestampedCandlepinDTO;
import org.candlepin.jackson.CandlepinAttributeDeserializer;
import org.candlepin.jackson.CandlepinLegacyAttributeSerializer;
import org.candlepin.service.model.ProductContentInfo;
import org.candlepin.service.model.ProductInfo;
import org.candlepin.util.MapView;
import org.candlepin.util.SetView;
import org.candlepin.util.Util;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

import tools.jackson.databind.annotation.JsonDeserialize;
import tools.jackson.databind.annotation.JsonSerialize;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

import javax.xml.bind.annotation.XmlRootElement;


/**
 * DTO representing the product data exposed to the manifest import/export framework.
 */
@XmlRootElement
public class ProductDTO extends TimestampedCandlepinDTO<ProductDTO> implements ProductInfo {
    public static final long serialVersionUID = 1L;

    /**
     * Join object DTO for joining products to content
     */
    public static class ProductContentDTO extends CandlepinDTO<ProductContentDTO>
        implements ProductContentInfo {

        protected final ContentDTO content;
        protected Boolean enabled;

        @JsonCreator
        public ProductContentDTO(
            @JsonProperty("content") ContentDTO content,
            @JsonProperty("enabled") Boolean enabled) {

            if (content == null || content.getId() == null) {
                throw new IllegalArgumentException("content is null or is missing an identifier");
            }

            this.content = content;
            this.setEnabled(enabled);
        }

        public ContentDTO getContent() {
            return this.content;
        }

        public ProductContentDTO setEnabled(Boolean enabled) {
            this.enabled = enabled;
            return this;
        }

        public Boolean isEnabled() {
            return this.enabled;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this) {
                return true;
            }

            if (obj instanceof ProductContentDTO) {
                ProductContentDTO that = (ProductContentDTO) obj;

                EqualsBuilder builder = new EqualsBuilder()
                    .append(this.getContent().getId(), that.getContent().getId())
                    .append(this.isEnabled(), that.isEnabled());

                return builder.isEquals();
            }

            return false;
        }

        @Override
        public int hashCode() {
            HashCodeBuilder builder = new HashCodeBuilder(37, 7)
                .append(this.getContent().getId())
                .append(this.isEnabled());

            return builder.toHashCode();
        }
    }

    protected String uuid;
    protected Long multiplier;
    protected String id;
    protected String name;

    @JsonSerialize(using = CandlepinLegacyAttributeSerializer.class)
    @JsonDeserialize(using = CandlepinAttributeDeserializer.class)
    protected Map<String, String> attributes;

    protected ProductDTO derivedProduct;
    protected Set<ProductDTO> providedProducts;

    protected Map<String, ProductContentDTO> productContent;
    protected Set<String> dependentProductIds;
    protected Set<BrandingDTO> branding;

    protected Boolean locked;


    /**
     * Initializes a new ProductDTO instance with null values.
     */
    public ProductDTO() {
        super();
    }

    /**
     * Initializes a new ProductDTO instance using the data contained by the given DTO.
     *
     * @param source
     *  The source DTO from which to copy data
     *
     * @throws IllegalArgumentException
     *  if source is null
     */
    public ProductDTO(ProductDTO source) {
        if (source == null) {
            throw new IllegalArgumentException("source is null");
        }

        this.populate(source);
    }

    /**
     * Retrieves the UUID of the product represented by this DTO. If the UUID has not yet been
     * defined, this method returns null.
     *
     * @return
     *  the UUID of the product, or null if the UUID has not yet been defined
     */
    public String getUuid() {
        return this.uuid;
    }

    /**
     * Sets the UUID of the product represented by this DTO.
     *
     * @param uuid
     *  The UUID of the product represented by this DTO, or null to clear the UUID
     *
     * @return
     *  a reference to this DTO
     */
    public ProductDTO setUuid(String uuid) {
        this.uuid = uuid;
        return this;
    }

    /**
     * Retrieves the multiplier of the product represented by this DTO. If the multiplier has not
     * yet been defined, this method returns null.
     *
     * @return
     *  the multiplier of the product, or null if the multiplier has not yet been defined
     */
    public Long getMultiplier() {
        return this.multiplier;
    }

    /**
     * Sets the multiplier of the product represented by this DTO.
     *
     * @param multiplier
     *  The multiplier of the product represented by this DTO, or null to clear the multiplier
     *
     * @return
     *  a reference to this DTO
     */
    public ProductDTO setMultiplier(Long multiplier) {
        this.multiplier = multiplier;
        return this;
    }

    /**
     * Retrieves the ID of the product represented by this DTO. If the ID has not yet been
     * defined, this method returns null.
     *
     * @return
     *  the ID of the product, or null if the ID has not yet been defined
     */
    public String getId() {
        return this.id;
    }

    /**
     * Sets the ID of the product represented by this DTO.
     *
     * @param id
     *  The ID of the product represented by this DTO
     *
     * @return
     *  a reference to this DTO
     */
    public ProductDTO setId(String id) {
        this.id = id;
        return this;
    }

    /**
     * Retrieves the UUID of the product represented by this DTO. If the UUID has not yet been
     * defined, this method returns null.
     *
     * @return
     *  the UUID of the product, or null if the UUID has not yet been defined
     */
    public String getName() {
        return this.name;
    }

    /**
     * Sets the name of the product represented by this DTO.
     *
     * @param name
     *  The name of the product represented by this DTO, or null to clear the name
     *
     * @return
     *  a reference to this DTO
     */
    public ProductDTO setName(String name) {
        this.name = name;
        return this;
    }

    /**
     * Retrieves a view of the attributes for the product represented by this DTO. If the product
     * attributes have not yet been defined, this method returns null.
     * <p></p>
     * Note that the collection returned by this method is a view of the collection backing this
     * product data. Elements cannot be added to the collection, but elements may be removed.
     * Changes made to the collection will be reflected by this product data instance.
     *
     * @return
     *  the attributes of the product, or null if the attributes have not yet been defined
     */
    public Map<String, String> getAttributes() {
        return this.attributes != null ? new MapView(this.attributes) : null;
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
    @JsonIgnore
    public String getAttributeValue(String key) {
        if (key == null) {
            throw new IllegalArgumentException("key is null");
        }

        return this.attributes != null ? this.attributes.get(key) : null;
    }

    /**
     * Checks if the given attribute has been defined on this product DTO.
     *
     * @param key
     *  The key (name) of the attribute to lookup
     *
     * @throws IllegalArgumentException
     *  if key is null
     *
     * @return
     *  true if the attribute is defined for this product; false otherwise
     */
    @JsonIgnore
    public boolean hasAttribute(String key) {
        if (key == null) {
            throw new IllegalArgumentException("key is null");
        }

        return this.attributes != null && this.attributes.containsKey(key);
    }

    /**
     * Sets the specified attribute for this product DTO. If the attribute has already been set for
     * this product, the existing value will be overwritten.
     *
     * @param key
     *  The name or key of the attribute to set
     *
     * @param value
     *  The value to assign to the attribute
     *
     * @throws IllegalArgumentException
     *  if key is null
     *
     * @return
     *  a reference to this DTO
     */
    @JsonIgnore
    public ProductDTO setAttribute(String key, String value) {
        if (key == null) {
            throw new IllegalArgumentException("key is null");
        }

        if (this.attributes == null) {
            this.attributes = new HashMap<String, String>();
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
     * Removes the product attribute with the given attribute key from this product DTO.
     *
     * @param key
     *  The key (name) of the attribute to remove
     *
     * @throws IllegalArgumentException
     *  if key is null
     *
     * @return
     *  true if the attribute was removed successfully; false otherwise
     */
    @JsonIgnore
    public boolean removeAttribute(String key) {
        if (key == null) {
            throw new IllegalArgumentException("key is null");
        }

        if (this.attributes != null && this.attributes.containsKey(key)) {
            this.attributes.remove(key);
            return true;
        }

        return false;
    }

    /**
     * Sets the attributes of the product represented by this DTO.
     *
     * @param attributes
     *  A map of product attributes to attach to this DTO, or null to clear the attributes
     *
     * @return
     *  a reference to this DTO
     */
    public ProductDTO setAttributes(Map<String, String> attributes) {
        this.attributes = attributes != null ?
            new HashMap<>(attributes) :
            null;

        return this;
    }

    /**
     * Retrieves the derived product of this product. If the derived product has not yet been set, or
     * this product does not have a derived product, this method returns null.
     *
     * @return
     *  the derived product of this product, or null if this product does not have a derived product
     */
    public ProductDTO getDerivedProduct() {
        return this.derivedProduct;
    }

    /**
     * Sets or clears the derived product for this product.
     *
     * @param derivedProduct
     *  the product to set as the derived product of this product, or null to clear any existing
     *  value
     *
     * @return
     *  a reference to this DTO
     */
    public ProductDTO setDerivedProduct(ProductDTO derivedProduct) {
        this.derivedProduct = derivedProduct;
        return this;
    }

    /**
     * Retrieves a view of the provided products for the product represented by this DTO.
     * If the provided products have not yet been defined, this method returns null.
     *
     * Note that the collection returned by this method is a view of the collection backing this
     * set of provided products. Elements cannot be added to the collection, but elements may be removed.
     * Changes made to the collection will be reflected by this product DTO instance.
     *
     * @return
     *  The provided products associated with this product, or null if they have not yet been
     *  defined
     */
    public Set<ProductDTO> getProvidedProducts() {
        return this.providedProducts != null ? new SetView<>(this.providedProducts) : null;
    }

    /**
     * Utility method to validate ProvidedProductDTO input
     *
     * @param providedProductDTO
     *  Product's DTO to be checked.
     */
    private boolean isNullOrIncomplete(ProductDTO providedProductDTO) {
        return providedProductDTO == null ||
            providedProductDTO.getId() == null ||
            providedProductDTO.getId().isEmpty();
    }

    /**
     * Adds the collection of provided products to this Product DTO.
     *
     * @param providedProducts
     *  A set of provided products to attach to this DTO, or null to clear the existing ones
     *
     * @return
     *  A reference to this DTO
     */
    public ProductDTO setProvidedProducts(Collection<ProductDTO> providedProducts) {
        if (providedProducts != null) {
            Consumer<ProductDTO> validator = elem -> {
                if (this.isNullOrIncomplete(elem)) {
                    throw new IllegalArgumentException(
                        "collection contains null or incomplete product objects");
                }
            };

            this.providedProducts = providedProducts.stream()
                .peek(validator)
                .collect(Collectors.toSet());
        }
        else {
            this.providedProducts = null;
        }

        return this;
    }

    /**
     * Adds the given provided product to this product DTO.
     *
     * @param providedProduct
     *  The provided product to add to this product DTO.
     *
     * @return
     *  True if this provided product was not already contained in this product DTO.
     */
    @JsonIgnore
    public boolean addProvidedProduct(ProductDTO providedProduct) {
        if (isNullOrIncomplete(providedProduct)) {
            throw new IllegalArgumentException("providedProduct is null or incomplete");
        }

        if (this.providedProducts == null) {
            this.providedProducts = new HashSet<>();
        }

        return this.providedProducts.add(providedProduct);
    }

    /**
     * Retrieves the content of the product represented by this DTO. If the product content has not
     * yet been defined, this method returns null.
     * <p></p>
     * Note that the collection returned by this method is a view of the collection backing this
     * product data. Elements cannot be added to the collection, but elements may be removed.
     * Changes made to the collection will be reflected by this product data instance.
     *
     * @return
     *  the content of the product, or null if the content not yet been defined
     */
    public Collection<ProductContentDTO> getProductContent() {
        return this.productContent != null ? this.productContent.values() : null;
    }

    /**
     * Retrieves the product content for the specified content ID. If no such content has been
     * associated with this product DTO, this method returns null.
     *
     * @param contentId
     *  The ID of the content to retrieve
     *
     * @throws IllegalArgumentException
     *  if contentId is null
     *
     * @return
     *  the product content associated with this DTO using the given ID, or null if such content
     *  does not exist
     */
    @JsonIgnore
    public ProductContentDTO getProductContent(String contentId) {
        if (contentId == null) {
            throw new IllegalArgumentException("contentId is null");
        }

        return this.productContent != null ? this.productContent.get(contentId) : null;
    }

    /**
     * Retrieves the unwrapped content for the specified content ID. If no such content has been
     * associated with this product, this method returns null.
     *
     * @param contentId
     *  The ID of the content to retrieve
     *
     * @throws IllegalArgumentException
     *  if contentId is null
     *
     * @return
     *  the content associated with this DTO using the given ID, or null if such content does not
     */
    @JsonIgnore
    public ContentDTO getContent(String contentId) {
        if (contentId == null) {
            throw new IllegalArgumentException("contentId is null");
        }

        ProductContentDTO pc = this.productContent != null ? this.productContent.get(contentId) : null;
        return pc != null ? pc.getContent() : null;
    }

    /**
     * Checks if any content with the given content ID has been associated with this product.
     *
     * @param contentId
     *  The ID of the content to check
     *
     * @throws IllegalArgumentException
     *  if contentId is null
     *
     * @return
     *  true if any content with the given content ID has been associated with this product; false
     *  otherwise
     */
    @JsonIgnore
    public boolean hasContent(String contentId) {
        if (contentId == null) {
            throw new IllegalArgumentException("contentId is null");
        }

        return this.productContent != null && this.productContent.containsKey(contentId);
    }

    /**
     * Adds the given content to this product DTO. If a matching content has already been added to
     * this product, it will be overwritten by the specified content.
     *
     * @param dto
     *  The product content DTO to add to this product
     *
     * @throws IllegalArgumentException
     *  if content is null or incomplete
     *
     * @return
     *  true if adding the content resulted in a change to this product; false otherwise
     */
    @JsonIgnore
    public boolean addProductContent(ProductContentDTO dto) {
        if (dto == null) {
            throw new IllegalArgumentException("dto is null");
        }

        if (dto.getContent() == null || dto.getContent().getId() == null) {
            throw new IllegalArgumentException("dto references incomplete content");
        }

        // We're operating under the assumption that we won't be doing janky things like
        // adding product content, then changing its ID. It's too bad this isn't all immutable...

        boolean changed = false;
        boolean matched = false;
        String contentId = dto.getContent().getId();


        if (this.productContent == null) {
            this.productContent = new HashMap<String, ProductContentDTO>();
            changed = true;
        }
        else {
            ProductContentDTO existing = this.productContent.get(dto.getContent().getId());
            changed = !dto.equals(existing);
        }

        if (changed) {
            this.productContent.put(dto.getContent().getId(), dto);
        }

        return changed;
    }

    /**
     * Adds the given content to this product DTO. If a matching content has already been added to
     * this product, it will be overwritten by the specified content.
     *
     * @param dto
     *  The product content DTO to add to this product
     *
     * @throws IllegalArgumentException
     *  if content is null
     *
     * @return
     *  true if adding the content resulted in a change to this product; false otherwise
     */
    @JsonIgnore
    public boolean addContent(ContentDTO dto, boolean enabled) {
        if (dto == null) {
            throw new IllegalArgumentException("dto is null");
        }

        return this.addProductContent(new ProductContentDTO(dto, enabled));
    }

    /**
     * Removes the content with the given content ID from this product DTO.
     *
     * @param contentId
     *  The ID of the content to remove
     *
     * @throws IllegalArgumentException
     *  if contentId is null
     *
     * @return
     *  true if the content was removed successfully; false otherwise
     */
    @JsonIgnore
    public boolean removeContent(String contentId) {
        if (contentId == null) {
            throw new IllegalArgumentException("contentId is null");
        }

        boolean updated = false;

        if (this.productContent != null) {
            ProductContentDTO existing = this.productContent.remove(contentId);
            updated = (existing != null);
        }

        return updated;
    }

    /**
     * Removes the content represented by the given content DTO from this product. Any content with
     * the same ID as the ID of the given content DTO will be removed.
     *
     * @param dto
     *  The product content DTO representing the content to remove from this product
     *
     * @throws IllegalArgumentException
     *  if content is null or incomplete
     *
     * @return
     *  true if the content was removed successfully; false otherwise
     */
    @JsonIgnore
    public boolean removeContent(ContentDTO dto) {
        if (dto == null) {
            throw new IllegalArgumentException("dto is null");
        }

        if (dto.getId() == null) {
            throw new IllegalArgumentException("dto is incomplete");
        }

        return this.removeContent(dto.getId());
    }

    /**
     * Removes the content represented by the given content DTO from this product. Any content with
     * the same ID as the ID of the given content DTO will be removed.
     *
     * @param dto
     *  The product content DTO representing the content to remove from this product
     *
     * @throws IllegalArgumentException
     *  if dto is null or incomplete
     *
     * @return
     *  true if the content was removed successfully; false otherwise
     */
    @JsonIgnore
    public boolean removeProductContent(ProductContentDTO dto) {
        if (dto == null) {
            throw new IllegalArgumentException("dto is null");
        }

        if (dto.getContent() == null || dto.getContent().getId() == null) {
            throw new IllegalArgumentException("dto is incomplete");
        }

        return this.removeContent(dto.getContent().getId());
    }

    /**
     * Sets the content of the product represented by this DTO.
     *
     * @param productContent
     *  A collection of product content DTO to attach to this DTO, or null to clear the content
     *
     * @throws IllegalArgumentException
     *  if the collection contains null or incomplete content DTOs
     *
     * @return
     *  a reference to this DTO
     */
    public ProductDTO setProductContent(Collection<ProductContentDTO> productContent) {
        if (productContent != null) {
            Consumer<ProductContentDTO> validator = elem -> {
                if (elem == null || elem.getContent() == null || elem.getContent().getId() == null) {
                    throw new IllegalArgumentException("collection contains null or incomplete dtos");
                }
            };

            this.productContent = productContent.stream()
                .peek(validator)
                .collect(Collectors.toMap(pcdto -> pcdto.getContent().getId(), Function.identity()));
        }
        else {
            this.productContent = null;
        }

        return this;
    }

    /**
     * Retrieves the dependent product IDs of the product represented by this DTO. If the product
     * dependent product IDs have not yet been defined, this method returns null.
     * <p></p>
     * Note that the collection returned by this method is a view of the collection backing this
     * product data. Elements cannot be added to the collection, but elements may be removed.
     * Changes made to the collection will be reflected by this product data instance.
     *
     * @return
     *  the dependent product IDs of the product, or null if the dependent product IDs have not yet
     *  been defined
     */
    public Collection<String> getDependentProductIds() {
        return this.dependentProductIds != null ? new SetView(this.dependentProductIds) : null;
    }

    /**
     * Adds the ID of the specified product as a dependent product of this product. If the product
     * is already a dependent product, it will not be added again.
     *
     * @param productId
     *  The ID of the product to add as a dependent product
     *
     * @throws IllegalArgumentException
     *  if productId is null
     *
     * @return
     *  true if the dependent product was added successfully; false otherwise
     */
    @JsonIgnore
    public boolean addDependentProductId(String productId) {
        if (productId == null) {
            throw new IllegalArgumentException("productId is null");
        }

        if (this.dependentProductIds == null) {
            this.dependentProductIds = new HashSet<String>();
        }

        return this.dependentProductIds.add(productId);
    }

    /**
     * Removes the specified product as a dependent product of this product. If the product is not
     * dependent on this product, this method does nothing.
     *
     * @param productId
     *  The ID of the product to add as a dependent product
     *
     * @throws IllegalArgumentException
     *  if productId is null
     *
     * @return
     *  true if the dependent product was removed successfully; false otherwise
     */
    @JsonIgnore
    public boolean removeDependentProductId(String productId) {
        return this.dependentProductIds != null && this.dependentProductIds.remove(productId);
    }

    /**
     * Sets the dependent product IDs of the product represented by this DTO.
     *
     * @param dependentProductIds
     *  A collection of dependent product IDs to attach to this DTO, or null to clear the
     *  dependent products
     *
     * @return
     *  a reference to this DTO
     */
    public ProductDTO setDependentProductIds(Collection<String> dependentProductIds) {
        this.dependentProductIds = dependentProductIds != null ?
            new HashSet(dependentProductIds) :
            null;

        return this;
    }

    /**
     * Retrieves a view of the branding for the product represented by this DTO. If the branding items
     * have not yet been defined, this method returns null.
     * <p></p>
     * Note that the collection returned by this method is a view of the collection backing this
     * set of branding items. Elements cannot be added to the collection, but elements may be removed.
     * Changes made to the collection will be reflected by this product DTO instance.
     *
     * IMPORTANT: The current manifest API does not support having branding on products, but rather on
     * pools, so this field should be ignored by the serializer.
     *
     * @return
     *  the branding items associated with this key, or null if they have not yet been defined
     */
    @JsonIgnore
    @Override
    public Set<BrandingDTO> getBranding() {
        return this.branding != null ? new SetView<>(this.branding) : null;
    }

    /**
     * Adds the collection of branding items to this Product DTO.
     *
     * IMPORTANT: The current manifest API does not support having branding on products, but rather on
     * pools, so this field should be ignored by the serializer.
     *
     * @param branding
     *  A set of branding items to attach to this DTO, or null to clear the existing ones
     *
     * @return
     *  A reference to this DTO
     */
    @JsonIgnore
    public ProductDTO setBranding(Collection<BrandingDTO> branding) {
        if (branding != null) {
            Consumer<BrandingDTO> validator = elem -> {
                if (this.isNullOrIncomplete(elem)) {
                    throw new IllegalArgumentException(
                        "collection contains null or incomplete branding objects");
                }
            };

            this.branding = branding.stream()
                .peek(validator)
                .collect(Collectors.toSet());
        }
        else {
            this.branding = null;
        }
        return this;
    }

    /**
     * Adds the given branding to this product DTO.
     *
     * @param branding
     *  The branding to add to this product DTO.
     *
     * @return
     *  true if this branding was not already contained in this product DTO.
     */
    @JsonIgnore
    public boolean addBranding(BrandingDTO branding) {
        if (this.isNullOrIncomplete(branding)) {
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

    @Override
    public String toString() {
        return String.format("ProductDTO [id = %s, uuid = %s, name = %s]", this.getId(), this.getUuid(),
            this.getName());
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }

        if (obj instanceof ProductDTO && super.equals(obj)) {
            ProductDTO that = (ProductDTO) obj;

            boolean equals = new EqualsBuilder()
                .append(this.getUuid(), that.getUuid())
                .append(this.getMultiplier(), that.getMultiplier())
                .append(this.getId(), that.getId())
                .append(this.getName(), that.getName())
                .append(this.getAttributes(), that.getAttributes())
                .append(this.getDerivedProduct(), that.getDerivedProduct())
                .append(this.getDependentProductIds(), that.getDependentProductIds())
                .isEquals();

            // As with many collections here, we need to explicitly check the elements ourselves,
            // since it seems very common for collection implementations to not properly implement
            // .equals
            // Note that we're using the boolean operator here as a shorthand way to skip checks
            // when the equality check has already failed.
            equals = equals && Util.collectionsAreEqual(this.getProvidedProducts(),
                that.getProvidedProducts());

            equals = equals && Util.collectionsAreEqual(this.getProductContent(), that.getProductContent());

            equals = equals && Util.collectionsAreEqual(this.getBranding(), that.getBranding());

            return equals;
        }

        return false;
    }

    @Override
    public int hashCode() {
        // Map.values doesn't properly implement .hashCode, so we need to manually calculate
        // this ourselves using the algorithm defined by list.hashCode()
        int pcHashCode = 0;
        Collection<ProductContentDTO> productContent = this.getProductContent();

        if (productContent != null) {
            for (ProductContentDTO dto : productContent) {
                pcHashCode = 31 * pcHashCode + (dto != null ? dto.hashCode() : 0);
            }
        }

        HashCodeBuilder builder = new HashCodeBuilder(37, 7)
            .append(super.hashCode())
            .append(this.getUuid())
            .append(this.getMultiplier())
            .append(this.getId())
            .append(this.getName())
            .append(this.getAttributes())
            .append(this.getDerivedProduct())
            .append(this.getProvidedProducts())
            .append(this.getDependentProductIds())
            .append(this.getBranding())
            .append(pcHashCode);

        return builder.toHashCode();
    }

    @Override
    public ProductDTO clone() {
        ProductDTO copy = super.clone();

        copy.setAttributes(this.getAttributes());
        copy.setProductContent(this.getProductContent());
        copy.setDependentProductIds(this.getDependentProductIds());

        ProductDTO srcDerived = this.getDerivedProduct();
        copy.setDerivedProduct(srcDerived != null ? (ProductDTO) srcDerived.clone() : null);

        Collection<ProductDTO> srcProvidedProducts = this.getProvidedProducts();
        if (srcProvidedProducts != null) {
            Set<ProductDTO> destProvidedProducts = srcProvidedProducts.stream()
                .map(dto -> dto.clone())
                .collect(Collectors.toSet());

            copy.setProvidedProducts(destProvidedProducts);
        }
        else {
            copy.setProvidedProducts(null);
        }

        Collection<BrandingDTO> srcBranding = this.getBranding();
        if (srcBranding != null) {
            Set<BrandingDTO> destBranding = srcBranding.stream()
                .map(dto -> dto.clone())
                .collect(Collectors.toSet());

            copy.setBranding(destBranding);
        }
        else {
            copy.setBranding(null);
        }

        return copy;
    }

    /**
     * Populates this DTO with the data from the given source DTO.
     *
     * @param source
     *  The source DTO from which to copy data
     *
     * @throws IllegalArgumentException
     *  if source is null
     *
     * @return
     *  a reference to this DTO
     */
    public ProductDTO populate(ProductDTO source) {
        super.populate(source);

        this.setUuid(source.getUuid());
        this.setId(source.getId());

        this.setMultiplier(source.getMultiplier());
        this.setName(source.getName());
        this.setAttributes(source.getAttributes());
        this.setDerivedProduct(source.getDerivedProduct());
        this.setProvidedProducts(source.getProvidedProducts());
        this.setProductContent(source.getProductContent());
        this.setDependentProductIds(source.getDependentProductIds());
        this.setBranding(source.getBranding());

        return this;
    }
}
