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
package org.candlepin.dto;

import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.commons.lang.builder.HashCodeBuilder;

import java.util.Date;



/**
 * The TimestampedCandlepinDTO class provides common accessors and mutators for DTOs containing
 * the created and updated timestamps.
 *
 * @param <T>
 *  The DTO type extending this class; should be the name of the subclass
 */
public abstract class TimestampedCandlepinDTO<T extends TimestampedCandlepinDTO> extends CandlepinDTO<T> {
    public static final long serialVersionUID = 1L;

    protected Date created;
    protected Date updated;

    /**
     * Initializes a new TimestampedCandlepinDTO instance with null values.
     */
    public TimestampedCandlepinDTO() {
        // Intentionally left empty
    }

    /**
     * Initializes a new TimestampedCandlepinDTO instance which is a shallow copy of the provided
     * source entity.
     *
     * @param source
     *  The source entity to copy
     */
    public TimestampedCandlepinDTO(T source) {
        super(source);
    }

    /**
     * Initializes a new TimestampedCandlepinDTO instance with the created and updated dates set to
     * the given values.
     *
     * @param created
     *  The creation date of the entity to be represented by this DTO
     *
     * @param updated
     *  The last updated date of the entity to be represented by this DTO
     */
    protected TimestampedCandlepinDTO(Date created, Date updated) {
        this.setCreated(created);
        this.setUpdated(updated);
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
     * Sets the creation date of the entity represented by this DTO.
     *
     * @param created
     *  A Date instance representing the creation date
     *
     * @return
     *  a reference to this DTO
     */
    public T setCreated(Date created) {
        this.created = created;
        return (T) this;
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

    /**
     * Sets the last updated date of the entity represented by this DTO.
     *
     * @param updated
     *  A Date instance representing the last updated date
     *
     * @return
     *  a reference to this DTO
     */
    public T setUpdated(Date updated) {
        this.updated = updated;
        return (T) this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return String.format("TimestampedCandlepinDTO");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }

        if (obj instanceof TimestampedCandlepinDTO) {
            TimestampedCandlepinDTO that = (TimestampedCandlepinDTO) obj;

            EqualsBuilder builder = new EqualsBuilder()
                .append(this.getCreated(), that.getCreated())
                .append(this.getUpdated(), that.getUpdated());

            return builder.isEquals();
        }

        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        HashCodeBuilder builder = new HashCodeBuilder(37, 7)
            .append(this.getCreated())
            .append(this.getUpdated());

        return builder.toHashCode();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public T clone() {
        T copy = super.clone();

        Date created = this.getCreated();
        copy.setCreated(created != null ? (Date) created.clone() : null);

        Date updated = this.getUpdated();
        copy.setUpdated(updated != null ? (Date) updated.clone() : null);

        return copy;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public T populate(T source) {
        super.populate(source);

        this.setCreated(source.getCreated());
        this.setUpdated(source.getUpdated());

        return (T) this;
    }

}
