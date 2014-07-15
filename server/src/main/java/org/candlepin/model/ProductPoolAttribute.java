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
import javax.persistence.DiscriminatorColumn;
import javax.persistence.DiscriminatorType;
import javax.persistence.DiscriminatorValue;
import javax.persistence.Embeddable;
import javax.persistence.Entity;
import javax.persistence.Inheritance;
import javax.persistence.InheritanceType;
import javax.persistence.Table;
import javax.validation.constraints.NotNull;

import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.commons.lang.builder.HashCodeBuilder;

import com.fasterxml.jackson.annotation.JsonFilter;

/**
 * ProductPoolAttribute
 */
@Entity
@Table(name = "cp_product_pool_attribute")
@Embeddable
@JsonFilter("ProductPoolAttributeFilter")
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "dtype", discriminatorType = DiscriminatorType.STRING)
@DiscriminatorValue("product")
public class ProductPoolAttribute extends AbstractPoolAttribute {

    @Column(nullable = false)
    @NotNull
    private String productId;

    public ProductPoolAttribute() {
    }

    public ProductPoolAttribute(String name, String val, String productId) {
        super(name, val);
        this.productId = productId;
    }

    public String toString() {
        return "ProductPoolAttribute [id=" + id + ", name=" + name + ", value=" +
            value + ", productId=" + productId + "]";
    }

    public void setProductId(String productId) {
        this.productId = productId;
    }

    public String getProductId() {
        return productId;
    }

//    @Override
//    public boolean equals(Object anObject) {
//        if (!(anObject instanceof ProductPoolAttribute)) {
//            return false;
//        }
//        ProductPoolAttribute another = (ProductPoolAttribute) anObject;
//        return super.equals(anObject) && getProductId().equals(another.getProductId());
//    }
    @Override
    public boolean equals(Object anObject) {
        if (this == anObject) {
            return true;
        }
        if (anObject instanceof ProductPoolAttribute) {
            ProductPoolAttribute that = (ProductPoolAttribute) anObject;
            return new EqualsBuilder().append(this.name, that.getName())
                .append(this.value, that.getValue())
                .isEquals();
        }
        return false;
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder(59, 61).
            append(name).
            append(value).
            append(productId).
            toHashCode();
    }
}
