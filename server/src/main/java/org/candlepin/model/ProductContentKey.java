/**
 * Copyright (c) 2009 - 2017 Red Hat, Inc.
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

import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.commons.lang.builder.HashCodeBuilder;

import java.io.Serializable;



/**
 * Class representing the composite key for OwnerProduct instances
 */
public class ProductContentKey implements Serializable {
    private static final long serialVersionUID = 1L;

    private String productUuid;
    private String contentUuid;

    public ProductContentKey() {
        // Intentionally left empty
    }

    public ProductContentKey(String productUuid, String contentUuid) {
        this.productUuid = productUuid;
        this.contentUuid = contentUuid;
    }

    public String getProductUuid() {
        return productUuid;
    }

    public void setProductUuid(String productUuid) {
        this.productUuid = productUuid;
    }

    public String getContentUuid() {
        return contentUuid;
    }

    public void setContentUuid(String contentUuid) {
        this.contentUuid = contentUuid;
    }

    public String toString() {
        return String.format("PCKey [prod_uuid: %s, cont_uuid: %s]", this.productUuid, this.contentUuid);
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder(37, 7)
            .append(this.productUuid)
            .append(this.contentUuid)
            .toHashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }

        if (obj == null || this.getClass() != obj.getClass()) {
            return false;
        }

        ProductContentKey that = (ProductContentKey) obj;

        return new EqualsBuilder()
            .append(this.productUuid, that.productUuid)
            .append(this.contentUuid, that.contentUuid)
            .isEquals();
    }
}
