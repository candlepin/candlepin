/**
 * Copyright (c) 2009 - 2016 Red Hat, Inc.
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

import org.candlepin.model.Content;
import org.candlepin.model.Product;
import org.candlepin.model.ProductAttribute;
import org.candlepin.model.ProductContent;

import com.fasterxml.jackson.annotation.JsonProperty;

import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.commons.lang.builder.HashCodeBuilder;

import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.xml.bind.annotation.XmlRootElement;



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
 *   "href" : "/products/ff808081554a3e4101554a3e9033005d",
 *   "created" : "2016-06-13T14:51:02+0000",
 *   "updated" : "2016-06-13T14:51:02+0000"
 * }
 * </pre>
 */
@XmlRootElement
public class ProductData extends CandlepinDTO {

    protected String uuid;
    protected String id;
    protected String name;
    protected Long multiplier;
    protected Collection<ProductAttributeData> attributes;
    protected Collection<ProductContentData> content;
    protected Collection<String> dependentProductIds;
    protected String href;

    /**
     * Initializes a new ProductData instance with null values.
     */
    public ProductData() {
        super();
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
     * @param entity
     *  The source entity from which to copy data
     *
     * @throws IllegalArgumentException
     *  if entity is null
     */
    public ProductData(Product entity) {
        if (entity == null) {
            throw new IllegalArgumentException("entity is null");
        }

        this.populate(entity);
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
     *  The ID of the product represented by this DTO, or null to clear the ID
     *
     * @return
     *  a reference to this DTO
     */
    public ProductData setId(String id) {
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
     * Retrieves the attributes of the product represented by this DTO. If the product attributes
     * have not yet been defined, this method returns null.
     *
     * @return
     *  the attributes of the product, or null if the attributes have not yet been defined
     */
    public Collection<ProductAttributeData> getAttributes() {
        return this.attributes != null ? Collections.unmodifiableCollection(this.attributes) : null;
    }

    /**
     * Adds the specified product attribute DTO to the this product DTO. If the attribute has
     * already been added to this product, the existing value will be overwritten.
     *
     * @param attribute
     *  The product attribute DTO to add to this product DTO
     *
     * @throws IllegalArgumentException
     *  if attribute is null or incomplete
     *
     * @return
     *  a reference to this DTO
     */
    public ProductData addAttribute(ProductAttributeData attribute) {
        if (attribute == null) {
            throw new IllegalArgumentException("attribute is null");
        }

        if (attribute.getName() == null) {
            throw new IllegalArgumentException("attribute name is null");
        }

        if (this.attributes == null) {
            this.attributes = new LinkedList<ProductAttributeData>();
            this.attributes.add(attribute);
        }
        else {
            // TODO:
            // Replace this with a map of attribute key/value pairs so we don't have this mess
            this.removeAttribute(attribute.getName());
            this.attributes.add(attribute);
        }

        return this;
    }

    /**
     * Adds the specified attribute to this product DTO. If the attribute has already been added to
     * this product, the existing value will be overwritten.
     *
     * @param attribute
     *  The name or key of the attribute to add
     *
     * @param value
     *  The value to assign to the attribute
     *
     * @throws IllegalArgumentException
     *  if attribute is null
     *
     * @return
     *  a reference to this DTO
     */
    public ProductData addAttribute(String attribute, String value) {
        if (attribute == null) {
            throw new IllegalArgumentException("attribute is null");
        }

        return this.addAttribute(new ProductAttributeData(attribute, value));
    }

    /**
     * Removes the product attribute represented by the given product attribute DTO from this
     * product. Any product attribute with the same key as the key of the given attribute DTO will
     * be removed.
     *
     * @param attribute
     *  The product attribute to remove from this product DTO
     *
     * @throws IllegalArgumentException
     *  if attribute is null or incomplete
     *
     * @return
     *  a reference to this DTO
     */
    public ProductData removeAttribute(ProductAttributeData attribute) {
        if (attribute == null) {
            throw new IllegalArgumentException("attribute is null");
        }

        if (attribute.getName() == null) {
            throw new IllegalArgumentException("attribute name is null");
        }

        return this.removeAttribute(attribute.getName());
    }

    /**
     * Removes the product attribute with the given attribute key from this product DTO.
     *
     * @param attribute
     *  The name/key of the attribute to remove
     *
     * @throws IllegalArgumentException
     *  if attribute is null
     *
     * @return
     *  a reference to this DTO
     */
    public ProductData removeAttribute(String attribute) {
        if (attribute == null) {
            throw new IllegalArgumentException("attribute is null");
        }

        Set<ProductAttributeData> remove = new HashSet<ProductAttributeData>();

        if (this.attributes != null) {
            for (ProductAttributeData attribdata : this.attributes) {
                if (attribute.equals(attribdata.getName())) {
                    remove.add(attribdata);
                }
            }

            this.attributes.removeAll(remove);
        }

        return this;
    }

    /**
     * Sets the attributes of the product represented by this DTO.
     *
     * @param attributes
     *  A collection of product attributes DTO to attach to this DTO, or null to clear the
     *  attributes
     *
     * @return
     *  a reference to this DTO
     */
    public ProductData setAttributes(Collection<ProductAttributeData> attributes) {
        if (attributes != null) {
            if (this.attributes != null) {
                this.attributes.clear();
            }

            for (ProductAttributeData attribute : attributes) {
                this.addAttribute(attribute);
            }
        }
        else {
            this.attributes = null;
        }

        return this;
    }

    /**
     * Retrieves the content of the product represented by this DTO. If the product content has not
     * yet been defined, this method returns null.
     *
     * @return
     *  the content of the product, or null if the content not yet been defined
     */
    public Collection<ProductContentData> getContent() {
        return this.content != null ? Collections.unmodifiableCollection(this.content) : null;
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
     *  a reference to this DTO
     */
    public ProductData addContent(ProductContentData content) {
        if (content == null) {
            throw new IllegalArgumentException("content is null");
        }

        if (this.content == null) {
            this.content = new LinkedList<ProductContentData>();
            this.content.add(content);
        }
        else {
            this.removeContent(content);
            this.content.add(content);
        }

        return this;
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
     *  a reference to this DTO
     */
    public ProductData addContent(ProductContent content) {
        if (content == null) {
            throw new IllegalArgumentException("content is null");
        }

        return this.addContent(new ProductContentData(content));
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
     *  a reference to this DTO
     */
    public ProductData addContent(ContentData content, boolean enabled) {
        if (content == null) {
            throw new IllegalArgumentException("content is null");
        }

        return this.addContent(new ProductContentData(content, enabled));
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
     *  a reference to this DTO
     */
    public ProductData addContent(Content content, boolean enabled) {
        if (content == null) {
            throw new IllegalArgumentException("content is null");
        }

        return this.addContent(new ProductContentData(new ContentData(content), enabled));
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
     *  a reference to this DTO
     */
    public ProductData removeContent(String contentId) {
        if (contentId == null) {
            throw new IllegalArgumentException("contentId is null");
        }

        if (this.content != null) {
            Collection<ProductContentData> remove = new LinkedList<ProductContentData>();

            for (ProductContentData pcd : this.content) {
                ContentData cd = pcd.getContent();

                if (cd != null && contentId.equals(cd.getId())) {
                    remove.add(pcd);
                }
            }

            this.content.removeAll(remove);
        }

        return this;
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
     *  a reference to this DTO
     */
    public ProductData removeContent(Content content) {
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
     * @param content
     *  The product content DTO representing the content to remove from this product
     *
     * @throws IllegalArgumentException
     *  if content is null or incomplete
     *
     * @return
     *  a reference to this DTO
     */
    public ProductData removeContent(ContentData content) {
        if (content == null) {
            throw new IllegalArgumentException("content is null");
        }

        if (content.getId() == null) {
            throw new IllegalArgumentException("content is incomplete");
        }

        return this.removeContent(content.getId());
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
     *  a reference to this DTO
     */
    public ProductData removeContent(ProductContent content) {
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
     * @param content
     *  The product content DTO representing the content to remove from this product
     *
     * @throws IllegalArgumentException
     *  if content is null or incomplete
     *
     * @return
     *  a reference to this DTO
     */
    public ProductData removeContent(ProductContentData content) {
        if (content == null) {
            throw new IllegalArgumentException("content is null");
        }

        if (content.getContent() == null || content.getContent().getId() == null) {
            throw new IllegalArgumentException("content is incomplete");
        }

        return this.removeContent(content.getContent().getId());
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
    public ProductData setContent(Collection<ProductContentData> content) {
        if (content != null) {
            if (this.content != null) {
                this.content.clear();
            }

            for (ProductContentData pcd : content) {
                this.addContent(pcd);
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
     *
     * @return
     *  the dependent product IDs of the product, or null if the dependent product IDs have not yet
     *  been defined
     */
    public Collection<String> getDependentProductIds() {
        return this.dependentProductIds != null ?
            Collections.unmodifiableCollection(this.dependentProductIds) :
            null;
    }

    public ProductData addDependentProductId(String productId) {
        if (productId == null) {
            throw new IllegalArgumentException("productId is null");
        }

        if (this.dependentProductIds == null) {
            this.dependentProductIds = new HashSet<String>();
        }

        this.dependentProductIds.add(productId);
        return this;
    }

    public ProductData removeDependentProductId(String productId) {
        if (this.dependentProductIds != null) {
            this.dependentProductIds.remove(productId);
        }

        return this;
    }

    /**
     * Sets the dependent product IDs of the product represented by this DTO.
     *
     * @param attributes
     *  A collection of dependent product IDs to attach to this DTO, or null to clear the
     *  dependent products
     *
     * @return
     *  a reference to this DTO
     */
    public ProductData setDependentProductIds(Collection<String> dependentProductIds) {
        if (dependentProductIds != null) {
            if (this.dependentProductIds != null) {
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
    public boolean equals(Object obj) {
        if (obj == null || !(obj instanceof ProductData)) {
            return false;
        }

        if (obj == this) {
            return true;
        }

        ProductData that = (ProductData) obj;

        EqualsBuilder builder = new EqualsBuilder()
            .append(this.id, that.id)
            .append(this.name, that.name)
            .append(this.multiplier, that.multiplier)
            .append(this.attributes, that.attributes)
            .append(this.href, that.href)
            .append(this.content, that.content)
            .append(this.dependentProductIds, that.dependentProductIds);

        return super.equals(obj) && builder.isEquals();
    }

    @Override
    public int hashCode() {
        HashCodeBuilder builder = new HashCodeBuilder(7, 17)
            .append(super.hashCode())
            .append(this.id)
            .append(this.name)
            .append(this.multiplier)
            .append(this.href)
            .append(this.attributes)
            .append(this.content)
            .append(this.dependentProductIds);

        return builder.toHashCode();
    }

    @Override
    public Object clone() {
        ProductData copy = (ProductData) super.clone();

        copy.uuid = this.uuid;
        copy.id = this.id;
        copy.name = this.name;
        copy.multiplier = this.multiplier;
        copy.href = this.href;

        if (this.attributes != null) {
            copy.attributes = new HashSet<ProductAttributeData>();

            for (ProductAttributeData pad : this.attributes) {
                copy.attributes.add((ProductAttributeData) pad.clone());
            }
        }
        else {
            copy.attributes = null;
        }

        if (this.content != null) {
            copy.content = new HashSet<ProductContentData>();

            for (ProductContentData pac : this.content) {
                copy.content.add((ProductContentData) pac.clone());
            }
        }
        else {
            copy.content = null;
        }

        if (this.dependentProductIds != null) {
            copy.dependentProductIds = new HashSet<String>();
            copy.dependentProductIds.addAll(this.dependentProductIds);
        }
        else {
            copy.dependentProductIds = null;
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
    public ProductData populate(ProductData source) {
        if (source == null) {
            throw new IllegalArgumentException("source is null");
        }

        super.populate(source);

        this.created = source.getCreated();
        this.updated = source.getUpdated();

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

        this.created = source.getCreated();
        this.updated = source.getUpdated();

        return this;
    }
}
