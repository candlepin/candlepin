/**
 * Copyright (c) 2009 Red Hat, Inc.
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
package org.fedoraproject.candlepin.model;

import java.util.HashSet;
import java.util.Set;

import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.JoinTable;
import javax.persistence.ManyToMany;
import javax.persistence.OneToMany;
import javax.persistence.SequenceGenerator;
import javax.persistence.Table;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;

import org.hibernate.annotations.ForeignKey;

/**
 * Represents a Product that can be consumed and entitled. Products define
 * the software or entity they want to entitle i.e. RHEL Server. They also 
 * contain descriptive meta data that might limit the Product i.e. 4 cores
 * per server with 4 guests. 
 */
@XmlRootElement
@XmlAccessorType(XmlAccessType.PROPERTY)
@Entity
@Table(name = "cp_product")
@SequenceGenerator(name = "seq_product", sequenceName = "seq_product", allocationSize = 1)
public class Product extends AbstractHibernateObject {
   
    // Product ID is stored as a string.
    // This is a subset of the product OID known as the hash.
    @Id
    private String id;
    
    @Column(nullable = false, unique = true)
    private String name;

    
    // NOTE: we need a product "type" so we can tell what class of
    //       product we are... 
    @ManyToMany(targetEntity = Product.class, cascade = CascadeType.ALL,
            fetch = FetchType.EAGER)
    @ForeignKey(name = "fk_product_product_id",
                inverseName = "fk_product_child_product_id")
    @JoinTable(name = "cp_product_hierarchy",
            joinColumns = @JoinColumn(name = "parent_product_id"),
            inverseJoinColumns = @JoinColumn(name = "child_product_id"))
    private Set<Product> childProducts;

    @OneToMany(cascade = CascadeType.ALL)
    @JoinTable(name = "cp_product_attribute")
    private Set<Attribute> attributes = new HashSet<Attribute>();

    @ManyToMany(cascade = CascadeType.ALL)
    @JoinTable(name = "cp_product_content")
    private Set<Content> content;
 
  
    @ManyToMany(cascade = CascadeType.ALL)
    @JoinTable(name = "cp_product_enabled_content")
    private Set<Content> enabledContent;
    
   
    /**
     * Constructor
     * 
     * Use this variant when creating a new object to persist.
     * 
     * @param id Product label
     * @param name Human readable Product name
     */
    public Product(String id, String name) {
        setId(id);
        setName(name);
    }
    
    public Product(String id, String name, String variant,
                   String version, String arch, String type,
                   Set<Product> childProducts, Set<Content> content) {
        setId(id);
        setName(name);
        setChildProducts(childProducts);
        setContent(content);
        // FIXME
        setEnabledContent(content);
        setAttribute("version", version);
        setAttribute("variant", variant);
        setAttribute("type", type);
        setAttribute("arch", arch);
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

  
    
    /**
     * @return set of child products.
     */
    public Set<Product> getChildProducts() {
        return childProducts;
    }

    /**
     * replaces all of the product children with the new set.
     * @param childProducts new child products.
     */
    public void setChildProducts(Set<Product> childProducts) {
        this.childProducts = childProducts;
    }

    /**
     * Add the given product as a child of this product.
     * @param p child product to add.
     */
    public void addChildProduct(Product p) {
        if (this.childProducts ==  null) {
            this.childProducts = new HashSet<Product>();
        }
        this.childProducts.add(p);
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
     * @param name name of the product
     */
    public void setName(String name) {
        this.name = name;
    }
    
    public void setAttribute(String key, String value) {
        Attribute existing = getAttribute(key);
        if (existing != null) {
            existing.setValue(value);
        }
        else {
            addAttribute(new Attribute(key, value));
        }
    }

    public Set<Attribute> getAttributes() {
        return attributes;
    }
    
    @XmlTransient
    public Set<String> getAttributeNames() {
        Set<String> toReturn = new HashSet<String>();
        
        for (Attribute attribute : attributes) {
            toReturn.add(attribute.getName());
        }
        return toReturn;
    }

    public void setAttributes(Set<Attribute> attributes) {
        this.attributes = attributes;
    }

    public void addAttribute(Attribute attrib) {
        this.attributes.add(attrib);
    }
    
    public Attribute getAttribute(String key) {
        for (Attribute a : attributes) {
            if (a.getName().equals(key)) {
                return a;
            }
        }
        return null;
    }
    

    public String getAttributeValue(String key) {
        for (Attribute a : attributes) {
            if (a.getName().equals(key)) {
                return a.getValue();
            }
        }
        return null;
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

        return
            id.equals(another.getId()) &&
            name.equals(another.getName());
    }

    @Override
    public int hashCode() {
        return id.hashCode() * 31 + name.hashCode();
    }

  
    public Set<Content> getContent() {
        return content;
    }

    public void setContent(Set<Content> content) {
        this.content = content;
    }
    
    public void addContent(Content content) {
        if (this.content != null) {
            this.content = new HashSet<Content>();
        }
        if (!this.content.contains(content)) { 
            this.content.add(content);
        }
    }

    /**
     * @param enabledContent the enabledContent to set
     */
    public void setEnabledContent(Set<Content> enabledContent) {
        this.enabledContent = enabledContent;
    }
    
    public void addEnabledContent(Content content) {
        if (this.enabledContent != null) {
            this.enabledContent = new HashSet<Content>();
        }
        if (!this.enabledContent.contains(content)) { 
            this.enabledContent.add(content);
        }
    }

    /**
     * @return the enabledContent
     */
    public Set<Content> getEnabledContent() {
//        return enabledContent;
        return content;
    }

    /**
     * Return true if this product provides the requested product.
     * This method also checks if this product is a direct match itself.
     *
     * @param desiredProductId Product we are looking for:
     * @return True if product is provided, otherwise false.
     */
    public Boolean provides(String desiredProductId) {
        // Check if I'm a direct match:
        if (getId().equals(desiredProductId)) {
            return Boolean.TRUE;
        }
        // No child products, can't be a match:
        if (getChildProducts() == null) {
            return Boolean.FALSE;
        }

        // Otherwise check if any of our child products provides:
        for (Product child : getChildProducts()) {
            if (child.provides(desiredProductId)) {
                return Boolean.TRUE;
            }
        }

        // Must not be a match:
        return Boolean.FALSE;
    }
 
}
