/**
 * Copyright (c) 2009 - 2016 Red Hat, Inc.
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
package org.candlepin.model.dto;

import org.candlepin.model.AbstractHibernateObject;

import com.fasterxml.jackson.annotation.JsonFilter;

import java.io.Serializable;
import java.util.Date;



/**
 * The CandlepinDTO class provides common properties and functionality common to many DTOs.
 */
@JsonFilter("DefaultFilter")
public abstract class CandlepinDTO implements Cloneable, Serializable {
    public static final long serialVersionUID = 1L;

    protected Date created;
    protected Date updated;

    /**
     * Initializes a new CandlepinDTO instance with null values.
     */
    protected CandlepinDTO() {
        // Intentionally left empty
    }

    /**
     * Initializes a new CandlepinDTO instance with the created and updated dates set to the given
     * values.
     *
     * @param created
     *  The creation date of the entity to be represented by this DTO
     *
     * @param updated
     *  The last updated date of the entity to be represented by this DTO
     */
    protected CandlepinDTO(Date created, Date updated) {
        this.created = created;
        this.updated = updated;
    }

    /**
     * Initializes a new CandlepinDTO instance using the data contained by the given DTO.
     *
     * @param source
     *  The source DTO from which to copy data
     *
     * @throws IllegalArgumentException
     *  if source is null
     */
    protected CandlepinDTO(CandlepinDTO source) {
        if (source == null) {
            throw new IllegalArgumentException("source is null");
        }

        this.populate(source);
    }

    /**
     * Initializes a new CandlepinDTO instance using the data contained by the given entity.
     *
     * @param source
     *  The source entity from which to copy data
     *
     * @throws IllegalArgumentException
     *  if source is null
     */
    protected CandlepinDTO(AbstractHibernateObject source) {
        if (source == null) {
            throw new IllegalArgumentException("source is null");
        }

        this.populate(source);
    }

    /**
     * Sets the creation date of the entity represented by this DTO.
     *
     * @param created
     *  A Date instance representing the creation date
     *
     * @return
     *  a reference to this DTO
     */
    public CandlepinDTO setCreated(Date created) {
        this.created = created;
        return this;
    }

    /**
     * Retrieves the creation date of the entity represented by this DTO. If the creation date has
     * not yet been defined, this method returns null.
     *
     * @return
     *  the creation date of the entity, or null if the creation date has not yet been defined
     */
    public Date getCreated() {
        return created;
    }

    /**
     * Sets the last updated date of the entity represented by this DTO.
     *
     * @param updated
     *  A Date instance representing the last updated date
     *
     * @return
     *  a reference to this DTO
     */
    public CandlepinDTO setUpdated(Date updated) {
        this.updated = updated;
        return this;
    }

    /**
     * Retrieves the last update date of the entity represented by this DTO. If the last update
     * date has not yet been defined, this method returns null.
     *
     * @return
     *  the last update date of the entity, or null if the last update date has not yet been
     *  defined
     */
    public Date getUpdated() {
        return updated;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null || !(obj instanceof CandlepinDTO)) {
            return false;
        }

        CandlepinDTO that = (CandlepinDTO) obj;

        return this == that ||
            ((this.created == that.created || (this.created != null && this.created.equals(that.created))) &&
            (this.updated == that.updated || (this.updated != null && this.updated.equals(that.updated))));
    }

    @Override
    public int hashCode() {
        int hash = 7;

        hash = 17 * hash + (this.created != null ? this.created.hashCode() : 0);
        hash = 17 * hash + (this.updated != null ? this.updated.hashCode() : 0);

        return hash;
    }

    @Override
    public Object clone() {
        CandlepinDTO copy;

        try {
            copy = (CandlepinDTO) super.clone();
        }
        catch (CloneNotSupportedException e) {
            // This should never happen.
            throw new RuntimeException("Clone not supported", e);
        }

        copy.created = this.created != null ? (Date) this.created.clone() : null;
        copy.updated = this.updated != null ? (Date) this.updated.clone() : null;

        return copy;
    }

    /**
     * Populates this DTO with the data from the given source DTO.
     *
     * @param source
     *  The source DTO from which to copy data
     *
     * @throws IllegalArgumentException
     *  if source is null
     *
     * @return
     *  a reference to this DTO
     */
    public CandlepinDTO populate(CandlepinDTO source) {
        if (source == null) {
            throw new IllegalArgumentException("source is null");
        }

        this.created = source.getCreated();
        this.updated = source.getUpdated();

        return this;
    }

    /**
     * Populates this DTO with data from the given source entity.
     *
     * @param source
     *  The source entity from which to copy data
     *
     * @throws IllegalArgumentException
     *  if source is null
     *
     * @return
     *  a reference to this DTO
     */
    public CandlepinDTO populate(AbstractHibernateObject source) {
        if (source == null) {
            throw new IllegalArgumentException("source is null");
        }

        this.created = source.getCreated();
        this.updated = source.getUpdated();

        return this;
    }
}
