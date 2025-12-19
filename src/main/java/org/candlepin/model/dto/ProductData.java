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
package org.candlepin.model.dto;

import org.candlepin.jackson.CandlepinAttributeDeserializer;
import org.candlepin.jackson.CandlepinLegacyAttributeSerializer;
import org.candlepin.model.Branding;
import org.candlepin.model.Content;
import org.candlepin.model.Product;
import org.candlepin.model.ProductContent;
import org.candlepin.service.model.ProductInfo;
import org.candlepin.util.MapView;
import org.candlepin.util.SetView;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

import tools.jackson.databind.annotation.JsonDeserialize;
import tools.jackson.databind.annotation.JsonSerialize;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;


/**
 * DTO representing the product data exposed to the API and adapter layers.
 *
 * <pre>
 * {
 *   "uuid" : "ff808081554a3e4101554a3e9033005d",
 *   "id" : "5051",
 *   "name" : "Admin OS Developer Bits",
 *   "multiplier" : 1,
 *   "attributes" : [ ... ],
 *   "productContent" : [ ... ],
 *   "dependentProductIds" : [ ... ],
 *   "branding" : [ ... ],
 *   "href" : "/products/ff808081554a3e4101554a3e9033005d",
 *   "created" : "2016-06-13T14:51:02+0000",
 *   "updated" : "2016-06-13T14:51:02+0000"
 * }
 * </pre>
 */
@XmlRootElement
public class ProductData extends CandlepinDTO implements ProductInfo {
    public static final long serialVersionUID = 1L;

    protected String uuid;

    protected String id;

    protected String name;

    protected Long multiplier;

    @JsonSerialize(using = CandlepinLegacyAttributeSerializer.class)
    @JsonDeserialize(using = CandlepinAttributeDeserializer.class)
    protected Map<String, String> attributes;

    protected ProductData derivedProduct;

    protected Set<ProductData> providedProducts;

    protected Map<String, ProductContentData> content;

    protected Set<String> dependentProductIds;

    protected Set<Branding> branding;

    protected String href;

    /**
     * Initializes a new ProductData instance with null values.
     */
    public ProductData() {
        super();
    }

    /**
     * Initializes a new ProductData instance with the specified Red Hat ID and name.
     * <p/></p>
     * <strong>Note</strong>: This constructor passes the provided values to their respective
     * mutator methods, and does not capture any exceptions they may throw as due to malformed
     * values.
     *
     * @param id
     *  The ID of the product to be represented by this DTO; cannot be null
     *
     * @param name
     *  The name of the product to be represented by this DTO
     */
    public ProductData(String id, String name) {
        super();

        this.setId(id);
        this.setName(name);
    }

    /**
     * Initializes a new ProductData instance using the data contained by the given DTO.
     *
     * @param source
     *  The source DTO from which to copy data
     *
     * @throws IllegalArgumentException
     *  if source is null
     */
    public ProductData(ProductData source) {
        if (source == null) {
            throw new IllegalArgumentException("source is null");
        }

        this.populate(source);
    }

    /**
     * Initializes a new ProductData instance using the data contained by the given entiy.
     *
     * @param source
     *  The source entity from which to copy data
     *
     * @throws IllegalArgumentException
     *  if source is null
     */
    public ProductData(Product source) {
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
    public ProductData setUuid(String uuid) {
        this.uuid = uuid;
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
     * @throws IllegalArgumentException
     *  if id is null or empty
     *
     * @return
     *  a reference to this DTO
     */
    public ProductData setId(String id) {
        if (id == null || id.length() == 0) {
            throw new IllegalArgumentException("id is null or empty");
        }

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
    public ProductData setName(String name) {
        this.name = name;
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
    public ProductData setMultiplier(Long multiplier) {
        this.multiplier = multiplier;
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
    @XmlTransient
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
    @XmlTransient
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
    public ProductData setAttribute(String key, String value) {
        if (key == null) {
            throw new IllegalArgumentException("key is null");
        }

        if (this.attributes == null) {
            this.attributes = new HashMap<>();
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
    public ProductData setAttributes(Map<String, String> attributes) {
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
     * Sets or clears the derived product for this product.
     *
     * @param derivedProduct
     *  the product to set as the derived product of this product, or null to clear any existing
     *  value
     *
     * @return
     *  a reference to this DTO
     */
    public ProductData setDerivedProduct(ProductData derivedProduct) {
        this.derivedProduct = derivedProduct;
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ProductData getDerivedProduct() {
        return this.derivedProduct;
    }

    /**
     * Retrieves a collection of provided product for this product.
     *
     * @return
     *  Returns the provided product of this product.
     */
    public Collection<ProductData> getProvidedProducts() {
        return this.providedProducts != null ? new SetView(this.providedProducts) : null;
    }

    /**
     * Method to set provided products for this product.
     *
     * @param providedProducts
     *  A collection of provided products.
     * @return
     *  A reference to this product.
     */
    public ProductData setProvidedProducts(Collection<ProductData> providedProducts) {
        if (providedProducts != null) {
            if (this.providedProducts == null) {
                this.providedProducts = new HashSet<>();
            }
            else {
                this.providedProducts.clear();
            }

            for (ProductData pData : providedProducts) {
                this.addProvidedProduct(pData);
            }
        }
        else {
            this.providedProducts = null;
        }

        return this;
    }

    /**
     * Adds the provided product for this product.
     *
     * @param providedProduct
     *  Provided product to be added.
     *
     * @return
     *  Returns true is added successfully, otherwise false.
     */
    public boolean addProvidedProduct(ProductData providedProduct) {
        if (providedProduct == null) {
            throw new IllegalArgumentException("Provided product is null");
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
    public Collection<ProductContentData> getProductContent() {
        return this.content != null ? this.content.values() : null;
    }

    /**
     * Retrieves the product content for the specified content ID. If no such content has been
     * assocaited with this product DTO, this method returns null.
     *
     * @param contentId
     *  The ID of the content to retrieve
     *
     * @throws IllegalArgumentException
     *  if contentId is null
     *
     * @return
     *  the content associated with this DTO using the given ID, or null if such content does not
     *  exist
     */
    public ProductContentData getProductContent(String contentId) {
        if (contentId == null) {
            throw new IllegalArgumentException("contentId is null");
        }

        return this.content != null ? this.content.get(contentId) : null;
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
    public boolean hasContent(String contentId) {
        if (contentId == null) {
            throw new IllegalArgumentException("contentId is null");
        }

        return this.content != null ? this.content.containsKey(contentId) : false;
    }

    /**
     * Adds the given content to this product DTO. If a matching content has already been added to
     * this product, it will be overwritten by the specified content.
     *
     * @param contentData
     *  The product content DTO to add to this product
     *
     * @throws IllegalArgumentException
     *  if content is null or incomplete
     *
     * @return
     *  true if adding the content resulted in a change to this product; false otherwise
     */
    public boolean addProductContent(ProductContentData contentData) {
        if (contentData == null) {
            throw new IllegalArgumentException("contentData is null");
        }

        if (contentData.getContent() == null || contentData.getContent().getId() == null) {
            throw new IllegalArgumentException("contentData or incomplete");
        }

        // We're operating under the assumption that we won't be doing janky things like
        // adding product content, then changing it. It's too bad this isn't all immutable...

        boolean changed = false;

        if (this.content == null) {
            this.content = new HashMap<>();
            changed = true;
        }
        else {
            ProductContentData existing = this.content.get(contentData.getContent().getId());
            changed = (existing == null || !existing.equals(contentData));
        }

        if (changed) {
            this.content.put(contentData.getContent().getId(), contentData);
        }

        return changed;
    }

    /**
     * Adds the given content to this product DTO. If a matching content has already been added to
     * this product, it will be overwritten by the specified content.
     *
     * @param productContent
     *  The product content DTO to add to this product
     *
     * @throws IllegalArgumentException
     *  if productContent is null
     *
     * @return
     *  true if adding the content resulted in a change to this product; false otherwise
     */
    public boolean addProductContent(ProductContent productContent) {
        if (productContent == null) {
            throw new IllegalArgumentException("productContent is null");
        }

        return this.addProductContent(productContent.toDTO());
    }

    /**
     * Adds the given content to this product DTO. If a matching content has already been added to
     * this product, it will be overwritten by the specified content.
     *
     * @param contentData
     *  The product content DTO to add to this product
     *
     * @throws IllegalArgumentException
     *  if content is null
     *
     * @return
     *  true if adding the content resulted in a change to this product; false otherwise
     */
    public boolean addContent(ContentData contentData, boolean enabled) {
        if (contentData == null) {
            throw new IllegalArgumentException("contentData is null");
        }

        return this.addProductContent(new ProductContentData(contentData, enabled));
    }

    /**
     * Adds the given content to this product DTO. If a matching content has already been added to
     * this product, it will be overwritten by the specified content.
     *
     * @param content
     *  The product content DTO to add to this product
     *
     * @throws IllegalArgumentException
     *  if content is null
     *
     * @return
     *  true if adding the content resulted in a change to this product; false otherwise
     */
    public boolean addContent(Content content, boolean enabled) {
        if (content == null) {
            throw new IllegalArgumentException("content is null");
        }

        return this.addProductContent(new ProductContentData(content.toDTO(), enabled));
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
    public boolean removeContent(String contentId) {
        if (contentId == null) {
            throw new IllegalArgumentException("contentId is null");
        }

        boolean updated = false;

        if (this.content != null) {
            ProductContentData existing = this.content.remove(contentId);
            updated = (existing != null);
        }

        return updated;
    }

    /**
     * Removes the content represented by the given content entity from this product. Any content
     * with the same ID as the ID of the given content entity will be removed.
     *
     * @param content
     *  The content entity representing the content to remove from this product
     *
     * @throws IllegalArgumentException
     *  if content is null or incomplete
     *
     * @return
     *  true if the content was removed successfully; false otherwise
     */
    public boolean removeContent(Content content) {
        if (content == null) {
            throw new IllegalArgumentException("content is null");
        }

        if (content.getId() == null) {
            throw new IllegalArgumentException("content is incomplete");
        }

        return this.removeContent(content.getId());
    }

    /**
     * Removes the content represented by the given content DTO from this product. Any content with
     * the same ID as the ID of the given content DTO will be removed.
     *
     * @param contentData
     *  The product content DTO representing the content to remove from this product
     *
     * @throws IllegalArgumentException
     *  if content is null or incomplete
     *
     * @return
     *  true if the content was removed successfully; false otherwise
     */
    public boolean removeContent(ContentData contentData) {
        if (contentData == null) {
            throw new IllegalArgumentException("contentData is null");
        }

        if (contentData.getId() == null) {
            throw new IllegalArgumentException("contentData is incomplete");
        }

        return this.removeContent(contentData.getId());
    }

    /**
     * Removes the content represented by the given content entity from this product. Any content
     * with the same ID as the ID of the given content entity will be removed.
     *
     * @param content
     *  The product content entity representing the content to remove from this product
     *
     * @throws IllegalArgumentException
     *  if content is null or incomplete
     *
     * @return
     *  true if the content was removed successfully; false otherwise
     */
    public boolean removeProductContent(ProductContent content) {
        if (content == null) {
            throw new IllegalArgumentException("content is null");
        }

        if (content.getContent() == null || content.getContent().getId() == null) {
            throw new IllegalArgumentException("content is incomplete");
        }

        return this.removeContent(content.getContent().getId());
    }

    /**
     * Removes the content represented by the given content DTO from this product. Any content with
     * the same ID as the ID of the given content DTO will be removed.
     *
     * @param contentData
     *  The product content DTO representing the content to remove from this product
     *
     * @throws IllegalArgumentException
     *  if contentData is null or incomplete
     *
     * @return
     *  true if the content was removed successfully; false otherwise
     */
    public boolean removeProductContent(ProductContentData contentData) {
        if (contentData == null) {
            throw new IllegalArgumentException("contentData is null");
        }

        if (contentData.getContent() == null || contentData.getContent().getId() == null) {
            throw new IllegalArgumentException("contentData is incomplete");
        }

        return this.removeContent(contentData.getContent().getId());
    }

    /**
     * Sets the content of the product represented by this DTO.
     *
     * @param content
     *  A collection of product content DTO to attach to this DTO, or null to clear the content
     *
     * @return
     *  a reference to this DTO
     */
    public ProductData setProductContent(Collection<ProductContentData> content) {
        if (content != null) {
            if (this.content == null) {
                this.content = new HashMap<>();
            }
            else {
                this.content.clear();
            }

            for (ProductContentData pcd : content) {
                this.addProductContent(pcd);
            }
        }
        else {
            this.content = null;
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
    public boolean addDependentProductId(String productId) {
        if (productId == null) {
            throw new IllegalArgumentException("productId is null");
        }

        if (this.dependentProductIds == null) {
            this.dependentProductIds = new HashSet<>();
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
    public boolean removeDependentProductId(String productId) {
        return this.dependentProductIds != null ? this.dependentProductIds.remove(productId) : false;
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
    public ProductData setDependentProductIds(Collection<String> dependentProductIds) {
        if (dependentProductIds != null) {
            if (this.dependentProductIds == null) {
                this.dependentProductIds = new HashSet<>();
            }
            else {
                this.dependentProductIds.clear();
            }

            for (String pid : dependentProductIds) {
                this.addDependentProductId(pid);
            }
        }
        else {
            this.dependentProductIds = null;
        }

        return this;
    }

    /**
     * Retrieves the branding of the product represented by this DTO. If the product branding has not
     * yet been defined, this method returns null.
     * <p></p>
     * Note that the collection returned by this method is a view of the collection backing this
     * product data. Elements cannot be added to the collection, but elements may be removed.
     * Changes made to the collection will be reflected by this product data instance.
     *
     * @return
     *  the branding of the product, or null if the content not yet been defined
     */
    public Collection<Branding> getBranding() {
        return this.branding != null ? new SetView(this.branding) : null;
    }

    /**
     * Sets the branding of the product represented by this DTO.
     *
     * @param branding
     *  A collection of brandings to attach to this DTO, or null to clear the existing brandings
     *
     * @return
     *  a reference to this DTO
     */
    public ProductData setBranding(Collection<Branding> branding) {
        if (branding != null) {
            if (this.branding == null) {
                this.branding = new HashSet<>();
            }
            else {
                this.branding.clear();
            }

            for (Branding brand : branding) {
                this.addBranding(brand);
            }
        }
        else {
            this.branding = null;
        }

        return this;
    }

    /**
     * Adds the branding to this product. If the branding
     * is already added, it will not be added again.
     *
     * @param branding
     *  The branding to add
     *
     * @throws IllegalArgumentException
     *  if branding is null
     *
     * @return
     *  true if the branding was added successfully; false otherwise
     */
    public boolean addBranding(Branding branding) {
        if (branding == null) {
            throw new IllegalArgumentException("branding is null");
        }

        if (this.branding == null) {
            this.branding = new HashSet<>();
        }

        // This is a DTO; we don't want references to Product model entities.
        branding = new Branding(branding.getProductId(), branding.getName(), branding.getType());
        return this.branding.add(branding);
    }

    /**
     * Removes the specified branding from this product. If the branding is not on this product, this method
     * does nothing.
     *
     * @param branding
     *  The branding to remove
     *
     * @throws IllegalArgumentException
     *  if branding is null
     *
     * @return
     *  true if the branding was removed successfully; false otherwise
     */
    public boolean removeBranding(Branding branding) {
        return this.branding != null ? this.branding.remove(branding) : false;
    }

    /**
     * Retrieves the link of the product represented by this DTO. If the product hyperlink has not
     * yet been defined, this method returns null.
     *
     * @return
     *  the link of the product, or null if the link have not yet been defined
     */
    public String getHref() {
        return this.href;
    }

    /**
     * Sets the hyperlink of the product represented by this DTO.
     *
     * @param href
     *  The hyperlink of the product represented by this DTO, or null to clear the hyperlink
     *
     * @return
     *  a reference to this DTO
     */
    public ProductData setHref(String href) {
        this.href = href;
        return this;
    }

    @Override
    public String toString() {
        return String.format("ProductData [id = %s, name = %s]", this.id, this.name);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null || !(obj instanceof ProductData)) {
            return false;
        }

        if (obj == this) {
            return true;
        }

        ProductData that = (ProductData) obj;

        EqualsBuilder builder = new EqualsBuilder()
            .append(this.uuid, that.uuid)
            .append(this.id, that.id)
            .append(this.name, that.name)
            .append(this.multiplier, that.multiplier)
            .append(this.attributes, that.attributes)
            .append(this.derivedProduct, that.derivedProduct)
            .append(this.providedProducts, that.providedProducts)
            .append(this.content, that.content)
            .append(this.dependentProductIds, that.dependentProductIds)
            .append(this.branding, that.branding)
            .append(this.href, that.href);

        return super.equals(obj) && builder.isEquals();
    }

    @Override
    public int hashCode() {
        HashCodeBuilder builder = new HashCodeBuilder(37, 7)
            .append(super.hashCode())
            .append(this.uuid)
            .append(this.id)
            .append(this.name)
            .append(this.multiplier)
            .append(this.href)
            .append(this.attributes)
            .append(this.derivedProduct)
            .append(this.providedProducts)
            .append(this.content)
            .append(this.dependentProductIds)
            .append(this.branding);

        return builder.toHashCode();
    }

    @Override
    public Object clone() {
        ProductData copy = (ProductData) super.clone();

        if (this.attributes != null) {
            copy.attributes = new HashMap<>();
            copy.attributes.putAll(this.attributes);
        }

        if (this.content != null) {
            copy.content = new HashMap<>();

            for (Map.Entry<String, ProductContentData> entry : this.content.entrySet()) {
                copy.content.put(entry.getKey(), (ProductContentData) entry.getValue().clone());
            }
        }

        if (this.dependentProductIds != null) {
            copy.dependentProductIds = new HashSet<>();
            copy.dependentProductIds.addAll(this.dependentProductIds);
        }

        ProductData srcDerived = this.getDerivedProduct();
        copy.setDerivedProduct(srcDerived != null ? (ProductData) srcDerived.clone() : null);

        Collection<ProductData> srcProvidedProducts = this.getProvidedProducts();
        if (srcProvidedProducts != null) {
            Set<ProductData> destProvidedProducts = srcProvidedProducts.stream()
                .map(dto -> (ProductData) dto.clone())
                .collect(Collectors.toSet());

            copy.setProvidedProducts(destProvidedProducts);
        }
        else {
            copy.setProvidedProducts(null);
        }

        copy.setBranding(this.getBranding());

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
    public ProductData populate(ProductData source) {
        if (source == null) {
            throw new IllegalArgumentException("source is null");
        }

        super.populate(source);

        this.uuid = source.getUuid();
        this.id = source.getId();
        this.name = source.getName();
        this.multiplier = source.getMultiplier();
        this.href = source.getHref();

        this.setAttributes(source.getAttributes());
        this.setDerivedProduct(source.getDerivedProduct());
        this.setProvidedProducts(source.getProvidedProducts());
        this.setProductContent(source.getProductContent());
        this.setDependentProductIds(source.getDependentProductIds());
        this.setBranding(source.getBranding());

        return this;
    }

    /**
     * Populates this DTO with data from the given source entity.
     *
     * @param source
     *  The source entity from which to copy data
     *
     * @throws IllegalArgumentException
     *  if source is null
     *
     * @return
     *  a reference to this DTO
     */
    public ProductData populate(Product source) {
        if (source == null) {
            throw new IllegalArgumentException("source is null");
        }

        super.populate(source);

        this.uuid = source.getUuid();
        this.id = source.getId();
        this.name = source.getName();
        this.multiplier = source.getMultiplier();
        this.href = source.getHref();

        this.setAttributes(source.getAttributes());

        Product srcDerived = source.getDerivedProduct();
        this.setDerivedProduct(srcDerived != null ? new ProductData(srcDerived) : null);

        if (source.getProvidedProducts() != null) {
            if (this.providedProducts == null) {
                this.providedProducts = new HashSet<>();
            }
            else {
                this.providedProducts.clear();
            }

            for (Product pData : source.getProvidedProducts()) {
                this.providedProducts.add(new ProductData(pData));
            }
        }
        else {
            this.setProvidedProducts(null);
        }

        if (source.getProductContent() != null) {
            if (this.content == null) {
                this.content = new HashMap<>();
            }
            else {
                this.content.clear();
            }

            for (ProductContent entity : source.getProductContent()) {
                this.addProductContent(entity.toDTO());
            }
        }
        else {
            this.setProductContent(null);
        }

        this.setDependentProductIds(source.getDependentProductIds());
        this.setBranding(source.getBranding());

        return this;
    }
}
