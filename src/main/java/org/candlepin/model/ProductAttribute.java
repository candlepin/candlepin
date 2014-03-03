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

import javax.persistence.Column;
import javax.persistence.Embeddable;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;
import javax.xml.bind.annotation.XmlTransient;

import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.commons.lang.builder.HashCodeBuilder;
import org.hibernate.annotations.ForeignKey;
import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.Index;

/**
 * See Attributes interface for documentation.
 */
@Entity
@Table(name = "cp_product_attribute")
@Embeddable
public class ProductAttribute extends AbstractHibernateObject implements Attribute {

    @Id
    @GeneratedValue(generator = "system-uuid")
    @GenericGenerator(name = "system-uuid", strategy = "uuid2")
    @Column(length = 37)
    protected String id;

    @Column(nullable = false)
    protected String name;

    @Column
    protected String value;


    @ManyToOne
    @ForeignKey(name = "fk_product_attrib_product_id")
    @JoinColumn(nullable = false)
    @Index(name = "cp_prodattribute_prod_fk_idx")
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
    public Product getProduct() {
        return product;
    }

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
