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

import org.hibernate.annotations.Cascade;
import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.LazyCollection;
import org.hibernate.annotations.LazyCollectionOption;

import java.util.ArrayList;
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
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;
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
@Table(name = "cpo_products")
public class Product extends AbstractHibernateObject implements Linkable, Cloneable {

    public static final  String UEBER_PRODUCT_POSTFIX = "_ueber_product";

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

    @ManyToOne
    @JoinColumn(nullable = false)
    @NotNull
    private Owner owner;

    /**
     * How many entitlements per quantity
     */
    @Column
    private Long multiplier;

    // NOTE: we need a product "type" so we can tell what class of
    // product we are...

    @OneToMany(mappedBy = "product")
    @Cascade({ org.hibernate.annotations.CascadeType.ALL,
        org.hibernate.annotations.CascadeType.DELETE_ORPHAN })
    private Set<ProductAttribute> attributes;

    @ElementCollection
    @CollectionTable(name = "cpo_product_content",
                     joinColumns = @JoinColumn(name = "product_uuid"))
    @Column(name = "element")
    @LazyCollection(LazyCollectionOption.EXTRA) // allows .size() without loading all data
    private List<ProductContent> productContent;

    /*
     * hibernate persists empty set as null, and tries to fetch
     * dependentProductIds upon a fetch when we lazy load. to fix this, we eager
     * fetch.
     */
    @ElementCollection
    @CollectionTable(name = "cpo_product_dependent_products",
                     joinColumns = @JoinColumn(name = "product_uuid"))
    @Column(name = "element")
    @LazyCollection(LazyCollectionOption.FALSE)
    private Set<String> dependentProductIds; // Should these be product references?

    protected Product() {

    }

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
        setOwner(owner);
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
     *
     * @param source
     *  The Product instance to copy
     */
    protected Product(Product source) {
        this.setUuid(source.getUuid());
        this.setId(source.getId());
        this.setName(source.getName());
        this.setOwner(source.getOwner());
        this.setMultiplier(source.getMultiplier());

        // Copy attributes
        Set<ProductAttribute> attributes = new HashSet<ProductAttribute>();
        for (ProductAttribute src : source.getAttributes()) {
            ProductAttribute dest = new ProductAttribute(src.getName(), src.getValue());
            dest.setCreated(src.getCreated());
            dest.setUpdated(src.getUpdated());
            dest.setProduct(this);

            attributes.add(dest);
        }
        this.setAttributes(attributes);

        // Copy content
        List<ProductContent> content = new LinkedList<ProductContent>();
        for (ProductContent src : source.getProductContent()) {
            ProductContent dest = new ProductContent(this, src.getContent(), src.getEnabled());
            dest.setCreated(src.getCreated());
            dest.setUpdated(src.getUpdated());

            content.add(dest);
        }
        this.setProductContent(content);

        this.setDependentProductIds(source.getDependentProductIds());

        this.setCreated(source.getCreated());
        this.setUpdated(source.getUpdated());
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
     * @return The product's owner/organization
     */
    public Owner getOwner() {
        return this.owner;
    }

    /**
     * Sets the product's owner.
     *
     * @param owner
     * The new owner/organization for this product.
     */
    public void setOwner(Owner owner) {
        this.owner = owner;
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
        String ownerKey = (owner != null ? owner.getKey() : null);
        return "Product [owner = " + ownerKey + ", id = " + id + ", name = " + name + "]";
    }

    @Override
    public boolean equals(Object anObject) {
        if (this == anObject) {
            return true;
        }
        if (!(anObject instanceof Product)) {
            return false;
        }

        Product another = (Product) anObject;

        // TODO: Maybe checking the UUID would be better here...?
        return (this.getOwner() != null ? this.getOwner().equals(another.getOwner()) :
            another.getOwner() == null) && getId().equals(another.getId()) &&
            name.equals(another.getName());
    }

    @Override
    public Object clone() {
        return new Product(this);
    }

    @Override
    public int hashCode() {
        if (id != null) {
            return id.hashCode() * 31;
        }
        return 31;
    }

    /**
     * @param content
     */
    public void addContent(Content content) {
        this.addProductContent(new ProductContent(this, content, false));
    }

    /**
     * @param content
     */
    public void addEnabledContent(Content content) {
        this.addProductContent(new ProductContent(this, content, true));
    }

    /**
     * @param productContent the productContent to set
     */
    public void setProductContent(List<ProductContent> productContent) {
        if (this.productContent == null) {
            this.productContent = new LinkedList<ProductContent>();
        }

        this.productContent.clear();

        if (productContent != null) {
            for (ProductContent pc : productContent) {
                this.addProductContent(pc);
            }
        }
    }

    /**
     * @return the productContent
     */
    public List<ProductContent> getProductContent() {
        return productContent;
    }

    public void addProductContent(ProductContent content) {
        if (this.productContent == null) {
            this.productContent = new LinkedList<ProductContent>();
        }

        if (content != null) {
            content.setProduct(this);
            this.productContent.add(content);
        }
    }

    public void setContent(Set<Content> content) {
        if (this.productContent == null) {
            this.productContent = new LinkedList<ProductContent>();
        }

        this.productContent.clear();

        if (content != null) {
            for (Content newContent : content) {
                this.productContent.add(new ProductContent(this, newContent, false));
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

    @Override
    public String getHref() {
        // If we don't have an owner here, we're in a bit of trouble.
        return (this.getOwner() != null && this.getOwner().getKey() != null && this.getId() != null) ?
            "/owners/" + this.getOwner().getKey() + "/products/" + this.getId() :
            "";
    }

    @Override
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

}
