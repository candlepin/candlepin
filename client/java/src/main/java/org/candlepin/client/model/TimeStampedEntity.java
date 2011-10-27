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
package org.candlepin.client.model;

import java.util.Date;

import org.apache.commons.lang.builder.ToStringBuilder;
import org.codehaus.jackson.annotate.JsonIgnoreProperties;

/**
 * The Class TimeStampedEntity.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public abstract class TimeStampedEntity {

    /** The created. */
    private Date created, updated;

    /**
     * Gets the created.
     *
     * @return the created
     */
    public final Date getCreated() {
        return created;
    }

    /**
     * Sets the created.
     *
     * @param createdDt the new created
     */
    public final void setCreated(Date createdDt) {
        this.created = createdDt;
    }

    /**
     * Gets the updated.
     *
     * @return the updated
     */
    public final Date getUpdated() {
        return updated;
    }

    /**
     * Sets the updated.
     *
     * @param up the new updated
     */
    public final void setUpdated(Date up) {
        this.updated = up;
    }

    @Override
    public String toString() {
        return ToStringBuilder.reflectionToString(this);
    }
}
