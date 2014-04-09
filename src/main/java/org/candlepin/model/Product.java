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

import java.util.HashSet;
import java.util.Set;

import javax.persistence.CollectionTable;
import javax.persistence.Column;
import javax.persistence.ElementCollection;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToMany;
import javax.persistence.OneToMany;
import javax.persistence.Table;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;

import org.hibernate.annotations.Cascade;
import org.hibernate.annotations.LazyCollection;
import org.hibernate.annotations.LazyCollectionOption;

/**
 * Represents a Product that can be consumed and entitled. Products define the
 * software or entity they want to entitle i.e. RHEL Server. They also contain
 * descriptive meta data that might limit the Product i.e. 4 cores per server
 * with 4 guests.
 */
@XmlRootElement
@XmlAccessorType(XmlAccessType.PROPERTY)
@Entity
@Table(name = "cp_product")
public class Product extends AbstractHibernateObject implements Linkable {

    public static final  String UEBER_PRODUCT_POSTFIX = "_ueber_product";

    // Product ID is stored as a string.
    // This is a subset of the product OID known as the hash.
    @Id
    @Column(length = 32, unique = true)
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

    // NOTE: we need a product "type" so we can tell what class of
    // product we are...

    @OneToMany(mappedBy = "product")
    @Cascade({ org.hibernate.annotations.CascadeType.ALL,
        org.hibernate.annotations.CascadeType.DELETE_ORPHAN })
    private Set<ProductAttribute> attributes;

    @ElementCollection
    @CollectionTable(name = "cp_product_content",
                     joinColumns = @JoinColumn(name = "product_id"))
    @Column(name = "element")
    @LazyCollection(LazyCollectionOption.EXTRA) // allows .size() without loading all data
    private Set<ProductContent> productContent;

    @ManyToMany(mappedBy = "providedProducts")
    private Set<Subscription> subscriptions;

    @ElementCollection
    @CollectionTable(name = "cp_product_dependent_products",
                     joinColumns = @JoinColumn(name = "cp_product_id"))
    @Column(name = "element")
    @Size(max = 255)
    private Set<String> dependentProductIds;

    @ElementCollection
    @CollectionTable(name = "cp_product_reliance",
                     joinColumns = @JoinColumn(name = "parent_product_id"))
    @Column(name = "child_product_id")
    private Set<String> reliantProductIds;

    /**
     * Constructor Use this variant when creating a new object to persist.
     *
     * @param id Product label
     * @param name Human readable Product name
     */
    public Product(String id, String name) {
        this(id, name, 1L);
    }

    public Product(String id, String name, Long multiplier) {
        setId(id);
        setName(name);
        setMultiplier(multiplier);
        setAttributes(new HashSet<ProductAttribute>());
        setProductContent(new HashSet<ProductContent>());
        setSubscriptions(new HashSet<Subscription>());
        setDependentProductIds(new HashSet<String>());
        setReliesOn(new HashSet<String>());
    }

    public Product(String id, String name, String variant, String version,
        String arch, String type) {
        setId(id);
        setName(name);
        setMultiplier(1L);
        setAttributes(new HashSet<ProductAttribute>());
        setProductContent(new HashSet<ProductContent>());
        setSubscriptions(new HashSet<Subscription>());
        setDependentProductIds(new HashSet<String>());
        setReliesOn(new HashSet<String>());
        setAttribute("version", version);
        setAttribute("variant", variant);
        setAttribute("type", type);
        setAttribute("arch", arch);
    }

    public static Product createUeberProductForOwner(Owner o) {
        return new Product(null, ueberProductNameForOwner(o), 1L);
    }

    protected Product() {
    }

    /** {@inheritDoc} */
    public String getId() {
        return id;

    }

    /**
     * @param id product id
     */
    public void setId(String id) {
        this.id = id;
    }

    @Override
    public String toString() {
        return "Product [id = " + id + ", name = " + name + "]";
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
        this.attributes = attributes;
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
        attrib.setProduct(this);
        this.attributes.add(attrib);
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
    public boolean equals(Object anObject) {
        if (this == anObject) {
            return true;
        }
        if (!(anObject instanceof Product)) {
            return false;
        }

        Product another = (Product) anObject;

        return id.equals(another.getId()) && name.equals(another.getName());
    }

    @Override
    public int hashCode() {
        return id.hashCode() * 31;
    }

    /**
     * @param content
     */
    public void addContent(Content content) {
        if (productContent == null) {
            productContent = new HashSet<ProductContent>();
        }
        productContent.add(new ProductContent(this, content, false));
    }

    /**
     * @param content
     */
    public void addEnabledContent(Content content) {
        if (productContent == null) {
            productContent = new HashSet<ProductContent>();
        }
        productContent.add(new ProductContent(this, content, true));
    }

    /**
     * @param productContent the productContent to set
     */
    public void setProductContent(Set<ProductContent> productContent) {
        this.productContent = productContent;
    }

    /**
     * @return the productContent
     */
    public Set<ProductContent> getProductContent() {
        return productContent;
    }

    // FIXME: this seems wrong, shouldn't this reset the content
    // not add to it?
    public void setContent(Set<Content> content) {
        if (content == null) {
            return;
        }
        if (productContent == null) {
            productContent = new HashSet<ProductContent>();
        }
        for (Content newContent : content) {
            productContent.add(new ProductContent(this, newContent, false));
        }
    }

    public void setEnabledContent(Set<Content> content) {
        if (content == null) {
            return;
        }
        if (productContent == null) {
            productContent = new HashSet<ProductContent>();
        }
        for (Content newContent : content) {
            productContent.add(new ProductContent(this, newContent, true));
        }
    }

    @XmlTransient
    public Set<Subscription> getSubscriptions() {
        return subscriptions;
    }

    public void setSubscriptions(Set<Subscription> subscriptions) {
        this.subscriptions = subscriptions;
    }

    /**
     * @param dependentProductIds the dependentProductIds to set
     */
    public void setDependentProductIds(Set<String> dependentProductIds) {
        this.dependentProductIds = dependentProductIds;
    }

    /**
     * @return the dependentProductIds
     */
    public Set<String> getDependentProductIds() {
        return dependentProductIds;
    }

    /**
     * @param reliantProductIds the reliantProductIds to set
     */
    public void setReliesOn(Set<String> reliantProductIds) {
        this.reliantProductIds = reliantProductIds;
    }

    /**
     * @return the reliantProductIds
     */
    public Set<String> getReliesOn() {
        return reliantProductIds;
    }

    public void addRely(String relyId) {
        if (getReliesOn() == null) {
            this.reliantProductIds = new HashSet<String>();
        }
        this.reliantProductIds.add(relyId);
    }

    public void removeRely(String relyId) {
        if (getReliesOn() != null) {
            this.reliantProductIds.remove(relyId);
        }
    }

    @Override
    public String getHref() {
        return "/products/" + getId();
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

}
