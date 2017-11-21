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
package org.candlepin.dto.api.v1;

import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.commons.lang.builder.HashCodeBuilder;

import io.swagger.annotations.ApiModel;

/**
 * A DTO representation of the Consumer Capability entity
 */
@ApiModel(parent = TimestampedCandlepinDTO.class, description = "DTO representing a consumer capability")
public class CapabilityDTO extends TimestampedCandlepinDTO<CapabilityDTO> {
    public static final long serialVersionUID = 1L;

    protected String id;
    protected String name;

    /**
     * Initializes a new CapabilityDTO instance with null values.
     */
    public CapabilityDTO() {
        // Intentionally left empty
    }

    /**
     * Initializes a new CapabilityDTO instance which is a shallow copy of the provided
     * source entity.
     *
     * @param source
     *  The source entity to copy
     */
    public CapabilityDTO(CapabilityDTO source) {
        super(source);
    }

    /**
     * Retrieves the id field of this CapabilityDTO object.
     *
     * @return the id of the capability, or null if the id has not yet been defined
     */
    public String getId() {
        return this.id;
    }

    /**
     * Sets the id to set on this CapabilityDTO object.
     *
     * @param id the id to set on this CapabilityDTO object.
     *
     * @return a reference to this DTO object.
     */
    public CapabilityDTO setId(String id) {
        this.id = id;
        return this;
    }

    /**
     * Retrieves the name field of this CapabilityDTO object.
     *
     * @return the name of the capability, or null if the id has not yet been defined
     */
    public String getName() {
        return this.name;
    }

    /**
     * Sets the name to set on this CapabilityDTO object.
     *
     * @param name the name to set on this CapabilityDTO object.
     *
     * @return a reference to this DTO object.
     */
    public CapabilityDTO setName(String name) {
        this.name = name;
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return String.format(
            "CapabilityDTO [id: %s, name: %s]",
            this.getId(), this.getName());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }

        if (obj instanceof CapabilityDTO && super.equals(obj)) {
            CapabilityDTO that = (CapabilityDTO) obj;

            EqualsBuilder builder = new EqualsBuilder()
                .append(this.getId(), that.getId())
                .append(this.getName(), that.getName());

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
            .append(super.hashCode())
            .append(this.getId())
            .append(this.getName());

        return builder.toHashCode();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CapabilityDTO populate(CapabilityDTO source) {
        super.populate(source);

        this.setId(source.getId());
        this.setName(source.getName());

        return this;
    }
}
