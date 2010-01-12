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
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.JoinTable;
import javax.persistence.OneToMany;
import javax.persistence.SequenceGenerator;
import javax.persistence.Table;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;

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
@SequenceGenerator(name="seq_product", sequenceName="seq_product", allocationSize=1)
public class Product implements Persisted {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator="seq_product")
    private Long id;
    
    @Column(nullable = false, unique = true)
    private String label;
    
    @Column(nullable = false, unique = true)
    private String name;

    @OneToMany(targetEntity = Product.class, cascade = CascadeType.ALL)
    @ForeignKey(name = "fk_product_product_id",
                inverseName = "fk_product_child_product_id")
    @JoinTable(name = "cp_product_hierarchy",
            joinColumns = @JoinColumn(name = "PARENT_PRODUCT_ID"),
            inverseJoinColumns = @JoinColumn(name = "CHILD_PRODUCT_ID"))
    private Set<Product> childProducts;


    @OneToMany
    @JoinColumn(name = "attribute_id")
    private Set<Attribute> attributes;

    /**
     * Constructor
     * 
     * Use this variant when creating a new object to persist.
     * 
     * @param label Product label
     * @param name Human readable Product name
     */
    public Product(String label, String name) {
        setLabel(label);
        setName(name);
    }

    public Product() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Set<Product> getChildProducts() {
        return childProducts;
    }

    public void setChildProducts(Set<Product> childProducts) {
        this.childProducts = childProducts;
    }

    public void addChildProduct(Product p) {
        if (this.childProducts ==  null) {
            this.childProducts = new HashSet<Product>();
        }
        this.childProducts.add(p);
    }
    
    public String getLabel() {
        return label;
    }

    public void setLabel(String labelIn) {
        label = labelIn;
    }

    @Override
    public String toString() {
        return "Product [label = " + label + "]";
    }
    
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

	public Set<Attribute> getAttributes() {
		return attributes;
	}

	public void setAttributes(Set<Attribute> attributes) {
		this.attributes = attributes;
	}

}
