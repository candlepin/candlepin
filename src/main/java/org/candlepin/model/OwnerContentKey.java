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

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

import java.io.Serializable;



/**
 * Class representing the composite key for OwnerContent instances
 */
public class OwnerContentKey implements Serializable {
    private static final long serialVersionUID = 1L;

    private String ownerId;
    private String contentUuid;

    public OwnerContentKey() {
        // Intentionally left empty
    }

    public OwnerContentKey(String ownerId, String contentUuid) {
        this.ownerId = ownerId;
        this.contentUuid = contentUuid;
    }

    public String getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(String ownerId) {
        this.ownerId = ownerId;
    }

    public String getContentUuid() {
        return contentUuid;
    }

    public void setContentUuid(String contentUuid) {
        this.contentUuid = contentUuid;
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder(37, 7)
            .append(this.ownerId)
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

        OwnerContentKey that = (OwnerContentKey) obj;

        return new EqualsBuilder()
            .append(this.ownerId, that.ownerId)
            .append(this.contentUuid, that.contentUuid)
            .isEquals();
    }
}
