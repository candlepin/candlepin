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

import com.fasterxml.jackson.annotation.JsonFilter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.commons.lang.builder.HashCodeBuilder;
import org.hibernate.annotations.GenericGenerator;

import javax.persistence.Column;
import javax.persistence.Embeddable;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import javax.xml.bind.annotation.XmlTransient;

/**
 * See Attributes interface for documentation.f
 */
@Entity
@Table(name = "cpo_product_attributes")
@Embeddable
@JsonFilter("ProductAttributeFilter")
public class ProductAttribute extends AbstractHibernateObject implements Attribute {

    @Id
    @GeneratedValue(generator = "system-uuid")
    @GenericGenerator(name = "system-uuid", strategy = "uuid")
    @NotNull
    protected String id;

    @Column(nullable = false)
    @Size(max = 255)
    @NotNull
    protected String name;

    @Column
    @Size(max = 255)
    protected String value;

    @ManyToOne
    @JoinColumn(name = "product_uuid", nullable = false)
    @NotNull
    @JsonIgnore
    private Product product;


    public ProductAttribute() {
    }

    public ProductAttribute(String name, String val) {
        this.name = name;
        this.value = val;
    }

    public String toString() {
        return "ProductAttribute [id=" + id + ", name=" + name + ", value=" + value +
            ", product=" + product + "]";
    }

    @XmlTransient
    @JsonIgnore
    public Product getProduct() {
        return product;
    }

    @JsonProperty
    public void setProduct(Product product) {
        this.product = product;
    }

    public String getName() {
        return name;
    }

    @XmlTransient
    public String getId() {
        return this.id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    @Override
    public boolean equals(Object anObject) {
        if (this == anObject) {
            return true;
        }
        if (anObject instanceof Attribute) {
            Attribute that = (Attribute) anObject;
            return new EqualsBuilder().append(this.name, that.getName())
                .append(this.value, that.getValue())
                .isEquals();
        }
        return false;
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder(31, 73)
            .append(this.name).append(this.value)
            .toHashCode();
    }
}
