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

import java.util.ArrayList;
import java.util.Collection;
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
import javax.persistence.JoinTable;
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
    private Set<ProductAttribute> attributes;

    @OneToMany(mappedBy = "product", orphanRemoval = true)
    @Cascade({ CascadeType.ALL })
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
    private Boolean locked;

    protected Product() {
        // Intentionally left empty
    }

    // TODO:
    // Remove the Owner parameter from these constructors; we no longer need it.

    /**
     * Constructor Use this variant when creating a new object to persist.
     *
     * @param productId The Red Hat product ID for the new product.
     * @param name Human readable Product name
     */
    public Product(String productId, String name, Owner owner) {
        this(productId, name, owner, 1L);
    }

    public Product(String productId, String name, Owner owner, Long multiplier) {
        setId(productId);
        setName(name);
        setMultiplier(multiplier);
        setAttributes(new HashSet<ProductAttribute>());
        setProductContent(new LinkedList<ProductContent>());
        setDependentProductIds(new HashSet<String>());
    }

    public Product(String productId, String name, Owner owner, String variant, String version,
        String arch, String type) {
        this(productId, name, owner, 1L);

        setAttribute("version", version);
        setAttribute("variant", variant);
        setAttribute("type", type);
        setAttribute("arch", arch);
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
        this.setUuid(source.getUuid());
        this.setId(source.getId());

        // Impl note:
        // In most cases, our collection setters copy the contents of the input collections to their
        // own internal collections, so we don't need to worry about our two instances sharing a
        // collection.

        this.setName(source.getName());
        this.setMultiplier(source.getMultiplier());

        // Copy attributes
        if (source.getAttributes() != null) {
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
        else {
            this.setAttributes(null);
        }

        // Copy content
        if (source.getProductContent() != null) {
            List<ProductContent> content = new LinkedList<ProductContent>();
            for (ProductContent src : source.getProductContent()) {
                ProductContent dest = new ProductContent(this, src.getContent(), src.isEnabled());
                dest.setCreated(src.getCreated() != null ? (Date) src.getCreated().clone() : null);
                dest.setUpdated(src.getUpdated() != null ? (Date) src.getUpdated().clone() : null);

                content.add(dest);
            }

            this.setProductContent(content);
        }
        else {
            this.setProductContent(null);
        }

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
        if (source.getAttributes() != null &&
            !Util.collectionsAreEqual(source.getAttributes(), this.getAttributes())) {

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
        if (source.getProductContent() != null &&
            !Util.collectionsAreEqual(source.getProductContent(), this.getProductContent())) {

            List<ProductContent> content = new LinkedList<ProductContent>();
            for (ProductContent src : source.getProductContent()) {
                ProductContent dest = new ProductContent(this, src.getContent(), src.isEnabled());
                dest.setCreated(src.getCreated() != null ? (Date) src.getCreated().clone() : null);
                dest.setUpdated(src.getUpdated() != null ? (Date) src.getUpdated().clone() : null);

                content.add(dest);
            }

            this.setProductContent(content);
        }

        if (source.getDependentProductIds() != null) {
            this.setDependentProductIds(source.getDependentProductIds());
        }

        this.setUpdated(source.getUpdated() != null ? (Date) source.getUpdated().clone() : null);

        return this;
    }

    @Override
    public Product clone() {
        Product copy;

        try {
            copy = (Product) super.clone();
        }
        catch (CloneNotSupportedException e) {
            // This should never happen.
            throw new RuntimeException("Clone not supported", e);
        }

        // Clear the collections, since this and copy will be sharing a single instance initially
        copy.attributes = null;
        copy.productContent = null;
        copy.dependentProductIds = null;

        // Impl note:
        // In most cases, our collection setters copy the contents of the input collections to their
        // own internal collections, so we don't need to worry about our two instances sharing a
        // collection.

        // Copy attributes
        if (this.getAttributes() != null) {
            Set<ProductAttribute> attributes = new HashSet<ProductAttribute>();
            for (ProductAttribute src : this.getAttributes()) {
                ProductAttribute dest = new ProductAttribute(src.getName(), src.getValue());
                dest.setCreated(src.getCreated());
                dest.setUpdated(src.getUpdated());
                dest.setProduct(copy);

                attributes.add(dest);
            }

            copy.setAttributes(attributes);
        }

        // Copy content
        if (this.getProductContent() != null) {
            List<ProductContent> content = new LinkedList<ProductContent>();
            for (ProductContent src : this.getProductContent()) {
                ProductContent dest = new ProductContent(copy, src.getContent(), src.isEnabled());
                dest.setCreated(src.getCreated());
                dest.setUpdated(src.getUpdated());

                content.add(dest);
            }

            copy.setProductContent(content);
        }

        if (this.getDependentProductIds() != null) {
            copy.setDependentProductIds(this.getDependentProductIds());
        }

        copy.setCreated(this.getCreated() != null ? (Date) this.getCreated().clone() : null);
        copy.setUpdated(this.getUpdated() != null ? (Date) this.getUpdated().clone() : null);

        return copy;
    }

    public static Product createUeberProductForOwner(UniqueIdGenerator idGenerator, Owner owner) {
        return new Product(idGenerator.generateId(), ueberProductNameForOwner(owner), owner, 1L);
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

    public void setAttributes(Set<ProductAttribute> attributes) {
        if (this.attributes == null) {
            this.attributes = new HashSet<ProductAttribute>();
        }

        this.attributes.clear();

        if (attributes != null) {
            this.attributes.addAll(attributes);
        }
    }

    public void setAttribute(String key, String value) {
        ProductAttribute existing = getAttribute(key);
        if (existing != null) {
            existing.setValue(value);
        }
        else {
            ProductAttribute attr = new ProductAttribute(key, value);
            attr.setProduct(this);
            addAttribute(attr);
        }
    }

    public void addAttribute(ProductAttribute attrib) {
        if (this.attributes == null) {
            this.attributes = new HashSet<ProductAttribute>();
        }

        if (attrib != null) {
            attrib.setProduct(this);
            this.attributes.add(attrib);
        }
    }

    public Set<ProductAttribute> getAttributes() {
        return attributes;
    }

    public ProductAttribute getAttribute(String key) {
        if (attributes != null) {
            for (ProductAttribute a : attributes) {
                if (a.getName().equals(key)) {
                    return a;
                }
            }
        }

        return null;
    }

    public String getAttributeValue(String key) {
        if (attributes != null) {
            for (ProductAttribute a : attributes) {
                if (a.getName().equals(key)) {
                    return a.getValue();
                }
            }
        }

        return null;
    }

    @XmlTransient
    public Set<String> getAttributeNames() {
        Set<String> toReturn = new HashSet<String>();

        if (attributes != null) {
            for (ProductAttribute attribute : attributes) {
                toReturn.add(attribute.getName());
            }
        }

        return toReturn;
    }

    public boolean hasAttribute(String key) {
        if (attributes != null) {
            for (ProductAttribute attribute : attributes) {
                if (attribute.getName().equals(key)) {
                    return true;
                }
            }
        }

        return false;
    }

    public boolean hasContent(String contentId) {
        if (this.getProductContent() != null) {
            for (ProductContent pc : getProductContent()) {
                if (pc.getContent().getId().equals(contentId)) {
                    return true;
                }
            }
        }

        return false;
    }

    @Override
    public String toString() {
        return String.format("Product [id = %s, name = %s]", this.id, this.name);
    }

    @Override
    public boolean equals(Object comp) {
        if (this == comp) {
            return true;
        }

        if (comp instanceof Product) {
            Product that = (Product) comp;

            // TODO:
            // Maybe it would be better to check the UUID field and only check the following if
            // both products have null UUIDs? By not doing this check, we run the risk of two
            // different products being considered equal if they happen to have the same values at
            // the time they're checked, or two products not being considered equal if they
            // represent the same product in different states.

            boolean equals = new EqualsBuilder()
                .append(this.id, that.id)
                .append(this.name, that.name)
                .append(this.multiplier, that.multiplier)
                .append(this.locked, that.locked)
                .isEquals();

            if (equals) {
                // Check our collections.
                // Impl note: We can't use .equals here on the collections, as Hibernate's special
                // collections explicitly state that they break the contract on .equals. As such, we
                // have to step through each collection and do a manual comparison. Ugh.
                // We also do an extra check here so null values and empty collections are
                // considered equal. If we ever move to real DTOs, those checks should be dropped.

                if (!Util.collectionsAreEqual(this.attributes, that.attributes)) {
                    if (!(this.attributes == null && that.attributes.size() == 0) &&
                        !(that.attributes == null && this.attributes.size() == 0)) {

                        return false;
                    }
                }

                if (!Util.collectionsAreEqual(this.productContent, that.productContent)) {
                    if (!(this.productContent == null && that.productContent.size() == 0) &&
                        !(that.productContent == null && this.productContent.size() == 0)) {

                        return false;
                    }
                }

                if (!Util.collectionsAreEqual(this.dependentProductIds, that.dependentProductIds)) {
                    if (!(this.dependentProductIds == null && that.dependentProductIds.size() == 0) &&
                        !(that.dependentProductIds == null && this.dependentProductIds.size() == 0)) {

                        return false;
                    }
                }
            }

            return equals;
        }

        return false;
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

        if (this.attributes != null && this.attributes.size() > 0) {
            builder.append(this.attributes);
        }

        try {
            // Impl note:
            // Stepping through the collections here is as painful as it looks, but Hibernate, once
            // again, doesn't implement .hashCode reliably on the proxy collections. So, we have to
            // manually step through these and add the elements to ensure the hash code is
            // generated properly.
            if (this.productContent != null && this.productContent.size() > 0) {
                for (ProductContent pc : this.productContent) {
                    builder.append(pc.getContent());
                }
            }

            if (this.dependentProductIds != null && this.dependentProductIds.size() > 0) {
                for (String pid : this.dependentProductIds) {
                    builder.append(pid);
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
     * Adds the specified content to this product as a disabled source.
     *
     * @param content
     *  The content to add to this product
     *
     * @return
     *  true if the content is added successfully; false otherwise
     */
    public boolean addContent(Content content) {
        return this.addContent(content, false);
    }

    /**
     * Adds the specified content to this product, if it doesn't already exist.
     *
     * @param content
     *  The content to add to this product
     *
     * @param enabled
     *  Whether or not the content should be added as an enabled or disabled source
     *
     * @return
     *  true if the content is added successfully; false otherwise
     */
    public boolean addContent(Content content, boolean enabled) {
        return this.addProductContent(new ProductContent(this, content, enabled));
    }

    /**
     * @param productContent the productContent to set
     *
     * @return
     *  a reference to this Product instance
     */
    public Product setProductContent(Collection<ProductContent> productContent) {
        if (this.productContent == null) {
            this.productContent = new LinkedList<ProductContent>();
        }

        this.productContent.clear();

        if (productContent != null) {
            for (ProductContent pc : productContent) {
                this.addProductContent(pc);
            }
        }

        return this;
    }

    /**
     * @return the productContent
     */
    public List<ProductContent> getProductContent() {
        return productContent;
    }

    /**
     * Adds the specified ProductContent to this product. If the content has already been added,
     * this method does nothing.
     *
     * @param content
     *  The ProductContent instance to add to this product
     *
     * @return
     *  true if the ProductContent is added successfully; false otherwise
     */
    public boolean addProductContent(ProductContent content) {
        if (this.productContent == null) {
            this.productContent = new LinkedList<ProductContent>();
        }

        if (content != null) {
            boolean found = false;
            for (ProductContent pc : this.productContent) {
                if (pc.getContent().equals(content.getContent())) {
                    found = true;
                    break;
                }
            }

            if (!found) {
                content.setProduct(this);
                this.productContent.add(content);

                return true;
            }
        }

        return false;
    }

    public void setContent(Set<Content> content) {
        if (this.productContent == null) {
            this.productContent = new LinkedList<ProductContent>();
        }

        this.productContent.clear();

        if (content != null) {
            for (Content newContent : content) {
                this.addContent(newContent, false);
            }
        }
    }

    /**
     * @param dependentProductIds the dependentProductIds to set
     */
    public void setDependentProductIds(Set<String> dependentProductIds) {
        if (this.dependentProductIds == null) {
            this.dependentProductIds = new HashSet<String>();
        }

        this.dependentProductIds.clear();

        if (dependentProductIds != null) {
            this.dependentProductIds.addAll(dependentProductIds);
        }
    }

    /**
     * @return the dependentProductIds
     */
    public Set<String> getDependentProductIds() {
        return dependentProductIds;
    }

    public String getHref() {
        return this.uuid != null ? String.format("/products/%s", this.uuid) : null;
    }

    public void setHref(String href) {
        /*
         * No-op, here to aid with updating objects which have nested objects
         * that were originally sent down to the client in HATEOAS form.
         */
    }

    /**
     * Returns true if this product has a content set which modifies the given
     * product:
     *
     * @param productId
     * @return true if this product modifies the given product ID
     */
    public boolean modifies(String productId) {
        if (getProductContent() != null) {
            for (ProductContent pc : getProductContent()) {
                if (pc.getContent().getModifiedProductIds().contains(productId)) {
                    return true;
                }
            }
        }
        return false;
    }

    public static String ueberProductNameForOwner(Owner o) {
        return o.getKey() + UEBER_PRODUCT_POSTFIX;
    }

    @XmlTransient
    public List<String> getSkuDisabledContentIds() {
        List<String> skuDisabled = new ArrayList<String>();
        if (this.hasAttribute("content_override_disabled") &&
            this.getAttributeValue("content_override_disabled").length() > 0) {
            StringTokenizer stDisable = new StringTokenizer(
                this.getAttributeValue("content_override_disabled"), ",");
            while (stDisable.hasMoreElements()) {
                skuDisabled.add((String) stDisable.nextElement());
            }
        }
        return skuDisabled;
    }

    @XmlTransient
    public List<String> getSkuEnabledContentIds() {
        List<String> skuEnabled = new ArrayList<String>();
        if (this.hasAttribute("content_override_enabled") &&
            this.getAttributeValue("content_override_enabled").length() > 0) {
            StringTokenizer stActive = new StringTokenizer(
                this.getAttributeValue("content_override_enabled"), ",");
            while (stActive.hasMoreElements()) {
                skuEnabled.add((String) stActive.nextElement());
            }
        }
        return skuEnabled;
    }

    @XmlTransient
    @JsonIgnore
    public Product setLocked(boolean locked) {
        this.locked = locked;
        return this;
    }

    @XmlTransient
    public boolean isLocked() {
        return this.locked != null && this.locked;
    }

    @PrePersist
    @PreUpdate
    public void updateEntityVersion() {
        this.entityVersion = this.hashCode();
    }

}
