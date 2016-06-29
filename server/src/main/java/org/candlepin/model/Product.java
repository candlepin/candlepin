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

import org.candlepin.model.dto.ProductAttributeData;
import org.candlepin.model.dto.ProductContentData;
import org.candlepin.model.dto.ProductData;
import org.candlepin.service.UniqueIdGenerator;
import org.candlepin.util.Util;

import org.hibernate.LazyInitializationException;
import org.hibernate.annotations.Cascade;
import org.hibernate.annotations.CascadeType;
import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;
import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.LazyCollection;
import org.hibernate.annotations.LazyCollectionOption;
import org.hibernate.annotations.Type;

import com.fasterxml.jackson.annotation.JsonIgnore;

import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.commons.lang.builder.HashCodeBuilder;

import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.StringTokenizer;

import javax.persistence.CollectionTable;
import javax.persistence.Column;
import javax.persistence.ElementCollection;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.OneToMany;
import javax.persistence.PrePersist;
import javax.persistence.PreUpdate;
import javax.persistence.Table;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;



/**
 * Represents a Product that can be consumed and entitled. Products define the
 * software or entity they want to entitle i.e. RHEL Server. They also contain
 * descriptive meta data that might limit the Product i.e. 4 cores per server
 * with 4 guests.
 */
@XmlRootElement
@XmlAccessorType(XmlAccessType.PROPERTY)
@Entity
@Table(name = "cp2_products")
public class Product extends AbstractHibernateObject implements SharedEntity, Linkable, Cloneable {
    public static final String UEBER_PRODUCT_POSTFIX = "_ueber_product";

    public static final String CONTENT_OVERRIDE_ENABLED_ATTRIB = "content_override_enabled";
    public static final String CONTENT_OVERRIDE_DISABLED_ATTRIB = "content_override_disabled";

    // Object ID
    @Id
    @GeneratedValue(generator = "system-uuid")
    @GenericGenerator(name = "system-uuid", strategy = "uuid")
    @NotNull
    private String uuid;

    // Internal RH product ID,
    @Column(name = "product_id")
    @NotNull
    private String id;

    @Column(nullable = false)
    @Size(max = 255)
    @NotNull
    private String name;

    /**
     * How many entitlements per quantity
     */
    @Column
    private Long multiplier;

    @OneToMany(mappedBy = "product", orphanRemoval = true)
    @Cascade({ CascadeType.ALL })
    @Fetch(FetchMode.SUBSELECT)
    private List<ProductAttribute> attributes;

    @ElementCollection
    @CollectionTable(name = "cp2_product_content", joinColumns = @JoinColumn(name = "product_uuid"))
    @Column(name = "element")
    @LazyCollection(LazyCollectionOption.EXTRA) // allows .size() without loading all data
    private List<ProductContent> productContent;

    /*
     * hibernate persists empty set as null, and tries to fetch
     * dependentProductIds upon a fetch when we lazy load. to fix this, we eager
     * fetch.
     */
    @ElementCollection
    @CollectionTable(name = "cp2_product_dependent_products",
        joinColumns = @JoinColumn(name = "product_uuid"))
    @Column(name = "element")
    @LazyCollection(LazyCollectionOption.FALSE)
    private Set<String> dependentProductIds;

    @XmlTransient
    @Column(name = "entity_version")
    private Integer entityVersion;

    @XmlTransient
    @Column
    @Type(type = "org.hibernate.type.NumericBooleanType")
    private boolean locked;

    protected Product() {
        this.attributes = new LinkedList<ProductAttribute>();
        this.productContent = new LinkedList<ProductContent>();
        this.dependentProductIds = new HashSet<String>();
    }

    /**
     * Constructor Use this variant when creating a new object to persist.
     *
     * @param productId The Red Hat product ID for the new product.
     * @param name Human readable Product name
     */
    public Product(String productId, String name) {
        this();

        this.setId(productId);
        this.setName(name);
    }

    public Product(String productId, String name, Long multiplier) {
        this(productId, name);

        this.setMultiplier(multiplier);
    }

    public Product(String productId, String name, String variant, String version, String arch, String type) {
        this(productId, name, 1L);

        this.setAttribute("version", version);
        this.setAttribute("variant", variant);
        this.setAttribute("type", type);
        this.setAttribute("arch", arch);
    }

    /**
     * Creates a shallow copy of the specified source product. Owners, attributes and content are
     * not duplicated, but the joining objects are (ProductAttribute, ProductContent, etc.).
     * <p></p>
     * Unlike the merge method, all properties from the source product are copied, including the
     * state of any null collections and any identifier fields.
     *
     * @param source
     *  The Product instance to copy
     */
    public Product(Product source) {
        this();

        this.setUuid(source.getUuid());
        this.setId(source.getId());

        // Impl note:
        // In most cases, our collection setters copy the contents of the input collections to their
        // own internal collections, so we don't need to worry about our two instances sharing a
        // collection.

        this.setName(source.getName());
        this.setMultiplier(source.getMultiplier());

        // Copy attributes
        Set<ProductAttribute> attributes = new HashSet<ProductAttribute>();
        for (ProductAttribute src : source.getAttributes()) {
            ProductAttribute dest = new ProductAttribute(src.getName(), src.getValue());
            dest.setCreated(src.getCreated() != null ? (Date) src.getCreated().clone() : null);
            dest.setUpdated(src.getUpdated() != null ? (Date) src.getUpdated().clone() : null);
            dest.setProduct(this);

            attributes.add(dest);
        }

        this.setAttributes(attributes);

        // Copy content
        List<ProductContent> content = new LinkedList<ProductContent>();
        for (ProductContent src : source.getProductContent()) {
            ProductContent dest = new ProductContent(this, src.getContent(), src.isEnabled());
            dest.setCreated(src.getCreated() != null ? (Date) src.getCreated().clone() : null);
            dest.setUpdated(src.getUpdated() != null ? (Date) src.getUpdated().clone() : null);

            content.add(dest);
        }

        this.setProductContent(content);

        this.setDependentProductIds(source.getDependentProductIds());

        this.setCreated(source.getCreated() != null ? (Date) source.getCreated().clone() : null);
        this.setUpdated(source.getUpdated() != null ? (Date) source.getUpdated().clone() : null);
        this.setLocked(source.isLocked());
    }

    /**
     * Copies several properties from the given product on to this product instance. Properties that
     * are not copied over include any identifiying fields (UUID, ID), the creation date and locking
     * states. Values on the source product which are null will be ignored.
     *
     * @param source
     *  The source product instance from which to pull product information
     *
     * @return
     *  this product instance
     */
    public Product merge(Product source) {
        if (source.getName() != null) {
            this.setName(source.getName());
        }

        if (source.getMultiplier() != null) {
            this.setMultiplier(source.getMultiplier());
        }

        // Copy attributes
        if (!Util.collectionsAreEqual(source.getAttributes(), this.getAttributes())) {
            Set<ProductAttribute> attributes = new HashSet<ProductAttribute>();
            for (ProductAttribute src : source.getAttributes()) {
                ProductAttribute dest = new ProductAttribute(src.getName(), src.getValue());
                dest.setCreated(src.getCreated() != null ? (Date) src.getCreated().clone() : null);
                dest.setUpdated(src.getUpdated() != null ? (Date) src.getUpdated().clone() : null);
                dest.setProduct(this);

                attributes.add(dest);
            }

            this.setAttributes(attributes);
        }

        // Copy content
        if (!Util.collectionsAreEqual(source.getProductContent(), this.getProductContent())) {

            List<ProductContent> content = new LinkedList<ProductContent>();
            for (ProductContent src : source.getProductContent()) {
                ProductContent dest = new ProductContent(this, src.getContent(), src.isEnabled());
                dest.setCreated(src.getCreated() != null ? (Date) src.getCreated().clone() : null);
                dest.setUpdated(src.getUpdated() != null ? (Date) src.getUpdated().clone() : null);

                content.add(dest);
            }

            this.setProductContent(content);
        }

        this.setDependentProductIds(source.getDependentProductIds());

        this.setUpdated(source.getUpdated() != null ? (Date) source.getUpdated().clone() : null);

        return this;
    }

    @Override
    public Object clone() {
        Product copy;

        try {
            copy = (Product) super.clone();
        }
        catch (CloneNotSupportedException e) {
            // This should never happen.
            throw new RuntimeException("Clone not supported", e);
        }

        // Impl note:
        // In most cases, our collection setters copy the contents of the input collections to their
        // own internal collections, so we don't need to worry about our two instances sharing a
        // collection.

        // Copy attributes
        copy.attributes = new LinkedList<ProductAttribute>();
        for (ProductAttribute src : this.getAttributes()) {
            ProductAttribute dest = new ProductAttribute(src.getName(), src.getValue());
            dest.setCreated(src.getCreated() != null ? (Date) src.getCreated().clone() : null);
            dest.setUpdated(src.getUpdated() != null ? (Date) src.getUpdated().clone() : null);
            dest.setProduct(copy);

            copy.attributes.add(dest);
        }

        // Copy content
        copy.productContent = new LinkedList<ProductContent>();
        for (ProductContent src : this.getProductContent()) {
            ProductContent dest = new ProductContent(copy, src.getContent(), src.isEnabled());
            dest.setCreated(src.getCreated() != null ? (Date) src.getCreated().clone() : null);
            dest.setUpdated(src.getUpdated() != null ? (Date) src.getUpdated().clone() : null);

            copy.productContent.add(dest);
        }

        // Copy dependent product IDs
        copy.dependentProductIds = new HashSet<String>();
        copy.dependentProductIds.addAll(this.dependentProductIds);

        copy.setCreated(this.getCreated() != null ? (Date) this.getCreated().clone() : null);
        copy.setUpdated(this.getUpdated() != null ? (Date) this.getUpdated().clone() : null);

        return copy;
    }

    /**
     * Returns a DTO representing this entity.
     *
     * @return
     *  a DTO representing this entity
     */
    public ProductData toDTO() {
        return new ProductData(this);
    }

    public static Product createUeberProductForOwner(UniqueIdGenerator idGenerator, Owner owner) {
        return new Product(idGenerator.generateId(), ueberProductNameForOwner(owner), 1L);
    }


    public static String ueberProductNameForOwner(Owner owner) {
        return owner.getKey() + UEBER_PRODUCT_POSTFIX;
    }

    /**
     * Retrieves this product's object/database UUID. While the product ID may exist multiple times
     * in the database (if in use by multiple owners), this UUID uniquely identifies a
     * product instance.
     *
     * @return
     *  this product's database UUID.
     */
    public String getUuid() {
        return uuid;
    }

    /**
     * Sets this product's object/database ID. Note that this ID is used to uniquely identify this
     * particular object and has no baring on the Red Hat product ID.
     *
     * @param uuid
     *  The object ID to assign to this product.
     */
    public void setUuid(String uuid) {
        this.uuid = uuid;
    }

    /**
     * Retrieves this product's ID. Assigned by the content provider, and may exist in
     * multiple owners, thus may not be unique in itself.
     *
     * @return
     *  this product's ID.
     */
    public String getId() {
        return this.id;
    }

    /**
     * Sets the product ID for this product. The product ID is the Red Hat product ID and should not
     * be confused with the object ID.
     *
     * @param productId
     *  The new product ID for this product.
     */
    public void setId(String productId) {
        this.id = productId;
    }

    /**
     * @return the product name
     */
    public String getName() {
        return name;
    }

    /**
     * sets the product name.
     *
     * @param name name of the product
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * @return the number of entitlements to create from a single subscription
     */
    public Long getMultiplier() {
        return multiplier;
    }

    /**
     * @param multiplier the multiplier to set
     */
    public void setMultiplier(Long multiplier) {
        if (multiplier == null) {
            this.multiplier = 1L;
        }
        else {
            this.multiplier = Math.max(1L, multiplier);
        }
    }

    /**
     * Retrieves the attributes of the product represented by this product. If this product does
     * not have any attributes, this method returns an empty collection.
     *
     * @return
     *  a collection containing the attributes of the product
     */
    public Collection<ProductAttribute> getAttributes() {
        return Collections.unmodifiableList(this.attributes);
    }

    /**
     * Retrieves the attribute data associated with the given attribute. If the attribute is not
     * set, this method returns null.
     *
     * @param key
     *  The key (name) of the attribute to lookup
     *
     * @throws IllegalArgumentException
     *  if key is null
     *
     * @return
     *  the attribute data for the given attribute, or null if the attribute is not set
     */
    @XmlTransient
    public ProductAttribute getAttribute(String key) {
        if (key == null) {
            throw new IllegalArgumentException("key is null");
        }

        for (ProductAttribute attrib : this.attributes) {
            if (key.equals(attrib.getName())) {
                return attrib;
            }
        }

        return null;
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

        ProductAttribute attrib = this.getAttribute(key);
        return attrib != null ? attrib.getValue() : null;
    }

    /**
     * Checks if the given attribute has been defined on this product.
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

        return this.getAttribute(key) != null;
    }

    /**
     * Adds the specified product attribute to the this product. If the attribute has already been
     * added to this product, the existing value will be overwritten.
     *
     * @param attribute
     *  The product attribute to add to this product
     *
     * @throws IllegalArgumentException
     *  if attribute is null or incomplete
     *
     * @return
     *  a reference to this product
     */
    public boolean addAttribute(ProductAttribute attribute) {
        if (attribute == null) {
            throw new IllegalArgumentException("attribute is null");
        }

        if (attribute.getName() == null) {
            throw new IllegalArgumentException("attribute name/key is null");
        }

        // TODO:
        // Replace this with a map of attribute key/value pairs so we don't have this mess
        boolean changed = false;
        boolean matched = false;
        Set<ProductAttribute> remove = new HashSet<ProductAttribute>();

        for (ProductAttribute attribdata : this.attributes) {
            if (attribute.getName().equals(attribdata.getName())) {
                matched = true;

                if (!(attribdata.getValue() != null ? attribdata.getValue().equals(attribute.getValue()) :
                    attribute.getValue() == null)) {

                    remove.add(attribdata);
                }
            }
        }

        if (!matched || remove.size() > 0) {
            attribute.setProduct(this);

            this.attributes.removeAll(remove);
            changed = this.attributes.add(attribute);
        }

        return changed;
    }

    /**
     * Sets the specified attribute for this product. If the attribute has already been set for
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
     *  a reference to this product
     */
    public boolean setAttribute(String key, String value) {
        if (key == null) {
            throw new IllegalArgumentException("key is null");
        }

        return this.addAttribute(new ProductAttribute(key, value));
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
     *  true if the attribute was removed successfully; false otherwise
     */
    public boolean removeAttribute(ProductAttribute attribute) {
        if (attribute == null) {
            throw new IllegalArgumentException("attribute is null");
        }

        if (attribute.getName() == null) {
            throw new IllegalArgumentException("attribute name is null");
        }

        return this.removeAttribute(attribute.getName());
    }

    /**
     * Removes the product attribute with the given attribute key from this product.
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

        Set<ProductAttribute> remove = new HashSet<ProductAttribute>();

        for (ProductAttribute attribdata : this.attributes) {
            if (key.equals(attribdata.getName())) {
                remove.add(attribdata);
            }
        }

        return this.attributes.removeAll(remove);
    }

    /**
     * Clears all product attributes currently set for this product.
     *
     * @return
     *  a reference to this product
     */
    public Product clearAttributes() {
        this.attributes.clear();
        return this;
    }

    /**
     * Sets the attributes of the product represented by this product.
     *
     * @param attributes
     *  A collection of product attributes to attach to this product, or null to clear the
     *  attributes
     *
     * @return
     *  a reference to this product
     */
    public Product setAttributes(Collection<ProductAttribute> attributes) {
        this.attributes.clear();

        if (attributes != null) {
            for (ProductAttribute attribute : attributes) {
                this.addAttribute(attribute);
            }
        }

        return this;
    }

    @XmlTransient
    public List<String> getSkuEnabledContentIds() {
        List<String> skus = new LinkedList<String>();

        ProductAttribute attrib = this.getAttribute(CONTENT_OVERRIDE_ENABLED_ATTRIB);

        if (attrib != null && attrib.getValue() != null && attrib.getValue().length() > 0) {
            StringTokenizer tokenizer = new StringTokenizer(attrib.getValue(), ",");

            while (tokenizer.hasMoreElements()) {
                skus.add((String) tokenizer.nextElement());
            }
        }

        return skus;
    }

    @XmlTransient
    public List<String> getSkuDisabledContentIds() {
        List<String> skus = new LinkedList<String>();

        ProductAttribute attrib = this.getAttribute(CONTENT_OVERRIDE_DISABLED_ATTRIB);

        if (attrib != null && attrib.getValue() != null && attrib.getValue().length() > 0) {
            StringTokenizer tokenizer = new StringTokenizer(attrib.getValue(), ",");

            while (tokenizer.hasMoreElements()) {
                skus.add((String) tokenizer.nextElement());
            }
        }

        return skus;
    }

    /**
     * Retrieves the content of the product represented by this product. If this product does not
     * have any associated content, this method returns an empty collection.
     *
     * @return
     *  the product content associated with this product
     */
    public Collection<ProductContent> getProductContent() {
        return Collections.unmodifiableList(this.productContent);
    }

    /**
     * Retrieves the product content for the specified content ID. If no such content has been
     * assocaited with this product, this method returns null.
     *
     * @param contentId
     *  The ID of the content to retrieve
     *
     * @throws IllegalArgumentException
     *  if contentId is null
     *
     * @return
     *  the content associated with this product using the given ID, or null if such content does
     *  not exist
     */
    public ProductContent getProductContent(String contentId) {
        if (contentId == null) {
            throw new IllegalArgumentException("contentId is null");
        }

        for (ProductContent pcd : this.productContent) {
            if (pcd.getContent() != null && contentId.equals(pcd.getContent().getId())) {
                return pcd;
            }
        }

        return null;
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

        return this.getProductContent(contentId) != null;
    }

    /**
     * Adds the given content to this product. If a matching content has already been added to
     * this product, it will be overwritten by the specified content.
     *
     * @param productContent
     *  The product content to add to this product
     *
     * @throws IllegalArgumentException
     *  if content is null or incomplete
     *
     * @return
     *  true if adding the content resulted in a change to this product; false otherwise
     */
    public boolean addProductContent(ProductContent productContent) {
        if (productContent == null) {
            throw new IllegalArgumentException("productContent is null");
        }

        if (productContent.getContent() == null || productContent.getContent().getId() == null) {
            throw new IllegalArgumentException("content is incomplete");
        }

        boolean changed = false;
        boolean matched = false;
        Collection<ProductContent> remove = new LinkedList<ProductContent>();

        // We're operating under the assumption that we won't be doing janky things like
        // adding product content, then changing it. It's too bad this isn't all immutable...
        for (ProductContent pcd : this.productContent) {
            Content cd = pcd.getContent();

            if (cd != null && cd.getId() != null && cd.getId().equals(productContent.getContent().getId())) {
                matched = true;

                if (pcd.isEnabled() != productContent.isEnabled() ||
                    !cd.equals(productContent.getContent())) {

                    remove.add(pcd);
                }
            }
        }

        if (!matched || remove.size() > 0) {
            productContent.setProduct(this);

            this.productContent.removeAll(remove);
            changed = this.productContent.add(productContent);
        }

        return changed;
    }

    /**
     * Adds the given content to this product. If a matching content has already been added to
     * this product, it will be overwritten by the specified content.
     *
     * @param content
     *  The product content to add to this product
     *
     * @param enabled
     *  Whether or not the content should be enabled for this product
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

        return this.addProductContent(new ProductContent(this, content, enabled));
    }

    /**
     * Removes the content with the given content ID from this product.
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

        Collection<ProductContent> remove = new LinkedList<ProductContent>();

        for (ProductContent pcd : this.productContent) {
            Content cd = pcd.getContent();

            if (cd != null && contentId.equals(cd.getId())) {
                remove.add(pcd);
            }
        }

        return this.productContent.removeAll(remove);
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
     * Clears all product content currently associated with this product.
     *
     * @return
     *  a reference to this product
     */
    public Product clearProductContent() {
        this.productContent.clear();
        return this;
    }

    /**
     * Sets the content of the product represented by this product.
     *
     * @param content
     *  A collection of product content to attach to this product, or null to clear the content
     *
     * @return
     *  a reference to this product
     */
    public Product setProductContent(Collection<ProductContent> content) {
        this.productContent.clear();

        if (content != null) {
            for (ProductContent pcd : content) {
                this.addProductContent(pcd);
            }
        }

        return this;
    }

    /**
     * Returns true if this product has a content set which modifies the given
     * product:
     *
     * @param productId
     * @return true if this product modifies the given product ID
     */
    public boolean modifies(String productId) {
        for (ProductContent pc : this.productContent) {
            if (pc.getContent().getModifiedProductIds().contains(productId)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Retrieves the dependent product IDs for this product. If the dependent product IDs have not
     * yet been defined, this method returns an empty collection.
     *
     * @return
     *  the dependent product IDs of this product
     */
    public Collection<String> getDependentProductIds() {
        return Collections.unmodifiableSet(this.dependentProductIds);
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
        if (productId == null) {
            throw new IllegalArgumentException("productId is null");
        }

        return this.dependentProductIds.remove(productId);
    }

    /**
     * Clears all dependent product IDs currently set for this product.
     *
     * @return
     *  a reference to this product
     */
    public Product clearDependentProductIds() {
        this.dependentProductIds.clear();
        return this;
    }

    /**
     * Sets the dependent product IDs of this product.
     *
     * @param dependentProductIds
     *  A collection of dependent product IDs to attach to this product, or null to clear the
     *  dependent products
     *
     * @return
     *  a reference to this product
     */
    public Product setDependentProductIds(Collection<String> dependentProductIds) {
        this.dependentProductIds.clear();

        if (dependentProductIds != null) {
            for (String pid : dependentProductIds) {
                this.addDependentProductId(pid);
            }
        }

        return this;
    }

    public String getHref() {
        return this.uuid != null ? String.format("/products/%s", this.uuid) : null;
    }

    @Override
    public String toString() {
        return String.format("Product [id = %s, name = %s]", this.id, this.name);
    }

    @XmlTransient
    @JsonIgnore
    public Product setLocked(boolean locked) {
        this.locked = locked;
        return this;
    }

    @XmlTransient
    public boolean isLocked() {
        return this.locked;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }

        boolean equals = false;

        if (obj instanceof Product) {
            Product that = (Product) obj;

            // TODO:
            // Maybe it would be better to check the UUID field and only check the following if
            // both products have null UUIDs? By not doing this check, we run the risk of two
            // different products being considered equal if they happen to have the same values at
            // the time they're checked, or two products not being considered equal if they
            // represent the same product in different states.

            equals = new EqualsBuilder()
                .append(this.id, that.id)
                .append(this.name, that.name)
                .append(this.multiplier, that.multiplier)
                .append(this.locked, that.locked)
                .isEquals();

            // Check our collections.
            // Impl note: We can't use .equals here on the collections, as Hibernate's special
            // collections explicitly state that they break the contract on .equals. As such, we
            // have to step through each collection and do a manual comparison. Ugh.
            equals = equals &&
                Util.collectionsAreEqual(this.attributes, that.attributes) &&
                Util.collectionsAreEqual(this.dependentProductIds, that.dependentProductIds) &&
                Util.collectionsAreEqual(this.productContent, that.productContent);
        }

        return equals;
    }

    @Override
    public int hashCode() {
        // This must always be a subset of equals
        HashCodeBuilder builder = new HashCodeBuilder(37, 7)
            .append(this.id)
            .append(this.name)
            .append(this.multiplier)
            .append(this.locked);

        // Impl note:
        // Because we handle the collections specially in .equals, we have to do the same special
        // treatment here to ensure our output doesn't give us wonky results when compared to the
        // output of .equals
        for (ProductAttribute attrib : this.attributes) {
            builder.append(attrib);
        }

        try {
            // Impl note:
            // Stepping through the collections here is as painful as it looks, but Hibernate, once
            // again, doesn't implement .hashCode reliably on the proxy collections. So, we have to
            // manually step through these and add the elements to ensure the hash code is
            // generated properly.
            if (this.dependentProductIds.size() > 0) {
                for (String pid : this.dependentProductIds) {
                    builder.append(pid);
                }
            }

            if (this.productContent.size() > 0) {
                for (ProductContent pc : this.productContent) {
                    builder.append(pc);
                }
            }
        }
        catch (LazyInitializationException e) {
            // One of the above collections (likely the first) has not been initialized and we're
            // not able to fetch them. We still need a hashCode, and the caller is likely to run
            // into this exception in the very near future, but we still need to generate
            // something here. We'll treat it as if they were empty (and not added).

            // This typically only occurs when we're initially pulling the entity down from a normal
            // lookup. Hibernate stores the entity in a hashmap, which triggers a call to this
            // method before Hibernate has fully hydrated it and before it's assigned it to an
            // entity manager or session. As such, as soon as we try to use one of the above
            // collections, things explode.

            // Note that this only applies to lazy collections, so the product attributes are safe
            // to check for now.
        }

        return builder.toHashCode();
    }

    /**
     * Determines whether or not this entity would be changed if the given DTO were applied to this
     * object.
     *
     * @param dto
     *  The product DTO to check for changes
     *
     * @throws IllegalArgumentException
     *  if dto is null
     *
     * @return
     *  true if this product would be changed by the given DTO; false otherwise
     */
    public boolean isChangedBy(ProductData dto) {
        if (dto == null) {
            throw new IllegalArgumentException("dto is null");
        }

        // Check simple properties first
        if (dto.getId() != null && !dto.getId().equals(this.id)) {
            return true;
        }

        if (dto.getName() != null && !dto.getName().equals(this.name)) {
            return true;
        }

        if (dto.getMultiplier() != null && !dto.getMultiplier().equals(this.multiplier)) {
            return true;
        }

        if (dto.isLocked() != null && !dto.isLocked().equals(this.locked)) {
            return true;
        }

        Collection<String> dependentProductIds = dto.getDependentProductIds();
        if (dependentProductIds != null &&
            !Util.collectionsAreEqual(this.dependentProductIds, dependentProductIds)) {

            return true;
        }

        Collection<ProductAttributeData> attributes = dto.getAttributes();
        if (attributes != null) {
            Comparator comparator = new Comparator<Object>() {
                public int compare(Object lhs, Object rhs) {
                    return ((ProductAttribute) lhs).isChangedBy((ProductAttributeData) rhs) ? 1 : 0;
                }
            };

            if (!Util.collectionsAreEqual(
                (Collection) this.attributes, (Collection) attributes, comparator)) {

                return true;
            }
        }

        Collection<ProductContentData> productContent = dto.getProductContent();
        if (productContent != null) {
            Comparator comparator = new Comparator<Object>() {
                public int compare(Object lhs, Object rhs) {
                    return ((ProductContent) lhs).isChangedBy((ProductContentData) rhs) ? 1 : 0;
                }
            };

            if (!Util.collectionsAreEqual(
                (Collection) this.productContent, (Collection) productContent, comparator)) {

                return true;
            }
        }

        return false;
    }

    @PrePersist
    @PreUpdate
    public void updateEntityVersion() {
        this.entityVersion = this.hashCode();
    }

}
