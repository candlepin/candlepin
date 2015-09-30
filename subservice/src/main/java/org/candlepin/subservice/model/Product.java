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
package org.candlepin.subservice.model;

import org.hibernate.annotations.Cascade;
import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.LazyCollection;
import org.hibernate.annotations.LazyCollectionOption;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import javax.persistence.CollectionTable;
import javax.persistence.Column;
import javax.persistence.ElementCollection;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.OneToMany;
import javax.persistence.Table;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;



@XmlRootElement
@XmlAccessorType(XmlAccessType.PROPERTY)
@Entity
@Table(name = "cps_products")
public class Product {

    // Internal RH product ID,
    @Id
    @GeneratedValue(generator = "system-uuid")
    @GenericGenerator(name = "system-uuid", strategy = "uuid")
    @Size(max = 32)
    @NotNull
    protected String id;

    @Column(nullable = false)
    @Size(max = 255)
    @NotNull
    protected String name;

    @Column
    protected Long multiplier;

    @OneToMany(mappedBy = "product")
    @Cascade({ org.hibernate.annotations.CascadeType.ALL,
        org.hibernate.annotations.CascadeType.DELETE_ORPHAN })
    protected Set<ProductAttribute> attributes;

    @ElementCollection
    @CollectionTable(name = "cps_product_content",
        joinColumns = @JoinColumn(name = "product_uuid"))
    @Column(name = "element")
    @LazyCollection(LazyCollectionOption.EXTRA) // allows .size() without loading all data
    protected List<ProductContent> productContent;

    @ElementCollection
    @CollectionTable(name = "cps_product_dependent_products",
        joinColumns = @JoinColumn(name = "product_uuid"))
    @Column(name = "element")
    protected Set<String> dependentProductIds; // Should these be product references?

    /**
     * Temporary object used to prevent infinite recursion during conversion to the CP model
     */
    private org.candlepin.model.Product cpmodel;

    public Product() {
        this.attributes = new HashSet<ProductAttribute>();
        this.productContent = new LinkedList<ProductContent>();
        this.dependentProductIds = new HashSet<String>();
    }

    // TODO:
    // Add convenience constructors



    public String getId() {
        return this.id;
    }

    public Product setId(String id) {
        this.id = id;
        return this;
    }

    public String getName() {
        return this.name;
    }

    public Product setName(String name) {
        this.name = name;
        return this;
    }

    public Long getMultiplier() {
        return this.multiplier;
    }

    public Product setMultiplier(Long multiplier) {
        this.multiplier = multiplier;
        return this;
    }

    public Set<ProductAttribute> getAttributes() {
        return this.attributes;
    }

    public Product setAttributes(Set<ProductAttribute> attributes) {
        this.attributes.clear();

        if (attributes != null) {
            this.attributes.addAll(attributes);
        }

        return this;
    }

    public List<ProductContent> getProductContent() {
        return this.productContent;
    }

    public Product setProductContent(List<ProductContent> productContent) {
        this.productContent.clear();

        if (productContent != null) {
            this.productContent.addAll(productContent);
        }

        return this;
    }

    public Set<String> getDependentProductIds() {
        return this.dependentProductIds;
    }

    public Product setDependentProductIds(Set<String> dependentProductIds) {
        this.dependentProductIds.clear();

        if (dependentProductIds != null) {
            this.dependentProductIds.addAll(dependentProductIds);
        }

        return this;
    }

    public synchronized org.candlepin.model.Product toCandlepinModel() {
        try {
            if (this.cpmodel == null) {
                org.candlepin.model.Product output = new org.candlepin.model.Product();
                this.cpmodel = output;

                output.setId(this.getId());
                output.setName(this.getName());
                output.setMultiplier(this.getMultiplier());

                Set<ProductAttribute> attributes = this.getAttributes();
                if (attributes != null && attributes.size() > 0) {
                    Set<org.candlepin.model.ProductAttribute> converted =
                        new HashSet<org.candlepin.model.ProductAttribute>();

                    for (ProductAttribute attribute : attributes) {
                        converted.add(attribute.toCandlepinModel());
                    }

                    output.setAttributes(converted);
                }
                else {
                    output.setAttributes(null);
                }

                List<ProductContent> productContent = this.getProductContent();
                if (productContent != null && productContent.size() > 0) {
                    List<org.candlepin.model.ProductContent> converted =
                        new LinkedList<org.candlepin.model.ProductContent>();

                    for (ProductContent content : productContent) {
                        converted.add(content.toCandlepinModel());
                    }

                    output.setProductContent(converted);
                }
                else {
                    output.setProductContent(null);
                }

                output.setDependentProductIds(this.getDependentProductIds());
                output.setCreated(this.getCreated());
                output.setUpdated(this.getUpdated());

                return output;
            }
            else {
                return this.cpmodel;
            }
        }
        finally {
            this.cpmodel = null;
        }
    }
}
