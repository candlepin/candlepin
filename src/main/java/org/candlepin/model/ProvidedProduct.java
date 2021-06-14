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

import org.apache.commons.lang.builder.HashCodeBuilder;

import java.io.Serializable;

/**
 * Represents a product provided by a Pool
 * ProvidedProduct
 */
public class ProvidedProduct implements Serializable {

    private static final long serialVersionUID = -6596019311091733072L;

    private String productId;
    private String productName;

    public ProvidedProduct() {

    }

    public ProvidedProduct(Product p) {
        this.productId = p.getId();
        this.productName = p.getName();
    }

    /**
     * @return the productId
     */
    public String getProductId() {
        return productId;
    }

    /**
     * @param productId the productId to set
     */
    public void setProductId(String productId) {
        this.productId = productId;
    }


    /**
     * @return the productName
     */
    public String getProductName() {
        return productName;
    }

    /**
     * @param productName the productName to set
     */
    public void setProductName(String productName) {
        this.productName = productName;
    }

    @Override
    public boolean equals(Object anObject) {
        if (this == anObject) {
            return true;
        }

        if (!(anObject instanceof ProvidedProduct)) {
            return false;
        }

        ProvidedProduct another = (ProvidedProduct) anObject;

        return productId.equals(another.getProductId()) &&
            productName.equals(another.getProductName());
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder(449, 3).
            append(productId).
            append(productName).
            toHashCode();
    }
}
