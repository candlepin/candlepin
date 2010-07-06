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
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.JoinTable;
import javax.persistence.OneToMany;
import javax.persistence.SequenceGenerator;
import javax.persistence.Table;

import org.hibernate.annotations.Cascade;
import org.hibernate.annotations.CollectionOfElements;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;

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
    
    /**
     * How many entitlements per quantity 
     */
    @Column
    private Long multiplier;

    
    // NOTE: we need a product "type" so we can tell what class of
    //       product we are... 

    @OneToMany(cascade = CascadeType.ALL)
    @Cascade({org.hibernate.annotations.CascadeType.ALL, 
        org.hibernate.annotations.CascadeType.MERGE,
        org.hibernate.annotations.CascadeType.DELETE_ORPHAN})
    private Set<ProductAttribute> attributes = new HashSet<ProductAttribute>();
    
    @CollectionOfElements
    @JoinTable(name = "cp_product_content", joinColumns = @JoinColumn(name = "product_id"))
    private Set<ProductContent> productContent = new HashSet<ProductContent>();
   
    /**
     * Constructor
     * 
     * Use this variant when creating a new object to persist.
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
    }
    
    public Product(String id, String name, String variant,
                   String version, String arch, String type) {
        setId(id);
        setName(name);
        setMultiplier(1L);
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

    public void setAttribute(String key, String value) {
        Attribute existing = getAttribute(key);
        if (existing != null) {
            existing.setValue(value);
        }
        else {
            addAttribute(new ProductAttribute(key, value));
        }
    }

    public Set<ProductAttribute> getAttributes() {
        return attributes;
    }
    
    @XmlTransient
    public Set<String> getAttributeNames() {
        Set<String> toReturn = new HashSet<String>();
        
        for (ProductAttribute attribute : attributes) {
            toReturn.add(attribute.getName());
        }
        return toReturn;
    }

    public void setAttributes(Set<ProductAttribute> attributes) {
        this.attributes = attributes;
    }

    public void addAttribute(ProductAttribute attrib) {
        attrib.setProduct(this);
        this.attributes.add(attrib);
    }
    
    public ProductAttribute getAttribute(String key) {
        for (ProductAttribute a : attributes) {
            if (a.getName().equals(key)) {
                return a;
            }
        }
        return null;
    }
    
    public String getAttributeValue(String key) {
        for (ProductAttribute a : attributes) {
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
        return id.hashCode() * 31;
    }

  
    /**
     * @param content
     */
    public void addContent(Content content) {
        productContent.add(new ProductContent(this, content, false));
    }

    /**
     * @param content
     */
    public void addEnabledContent(Content content) {
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
    @XmlTransient
    public Set<ProductContent> getProductContent() {
        return productContent;
    }
    
    
    public void setContent(Set<Content> content) {
        if (content == null) {
            return;
        }
        for (Content newContent : content) {
            productContent.add(new ProductContent(this, newContent, false));
        }    
    }   
    
    public void setEnabledContent(Set<Content> content) {
        if (content == null) {
            return;
        }
        for (Content newContent : content) {
            productContent.add(new ProductContent(this, newContent, true));
        }
    }
    
}
