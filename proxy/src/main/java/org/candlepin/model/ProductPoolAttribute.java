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
package org.candlepin.model;

import javax.persistence.Column;
import javax.persistence.Embeddable;
import javax.persistence.Entity;
import javax.persistence.Table;

/**
 * ProductProvidedPoolAttribute
 */
@Entity
@Table(name = "cp_product_pool_attribute")
@Embeddable
public class ProductPoolAttribute extends AbstractPoolAttribute {

    @Column(nullable = false)
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

}
