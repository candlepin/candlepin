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

import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.commons.lang.builder.HashCodeBuilder;

import java.io.Serializable;



public class OwnerProductKey implements Serializable {
    private static final long serialVersionUID = 1L;

    private String ownerId;
    private String productUuid;

    public OwnerProductKey() {
        // Intentionally left empty
    }

    public OwnerProductKey(String ownerId, String productUuid) {
        this.ownerId = ownerId;
        this.productUuid = productUuid;
    }

    public String getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(String ownerId) {
        this.ownerId = ownerId;
    }

    public String getProductUuid() {
        return productUuid;
    }

    public void setProductUuid(String productUuid) {
        this.productUuid = productUuid;
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder(37, 7)
            .append(this.ownerId)
            .append(this.productUuid)
            .toHashCode();
    }

    @Override
    public boolean equals(Object value) {
        if (this == value) {
            return true;
        }

        if (value == null) {
            return false;
        }

        if (this.getClass() != value.getClass()) {
            return false;
        }

        OwnerProductKey that = (OwnerProductKey) value;

        return new EqualsBuilder()
            .append(this.ownerId, that.ownerId)
            .append(this.productUuid, that.productUuid)
            .isEquals();
    }

}
