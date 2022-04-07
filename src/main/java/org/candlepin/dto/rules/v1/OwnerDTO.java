/**
 * Copyright (c) 2009 - 2018 Red Hat, Inc.
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
package org.candlepin.dto.rules.v1;

import org.candlepin.dto.CandlepinDTO;
import org.candlepin.jackson.HateoasInclude;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * A DTO representation of the Owner entity as used by the Rules framework.
 */
@XmlRootElement
@XmlAccessorType(XmlAccessType.PROPERTY)
public class OwnerDTO extends CandlepinDTO<OwnerDTO> {
    public static final long serialVersionUID = 1L;

    protected String id;
    protected String defaultServiceLevel;

    /**
     * Initializes a new OwnerDTO instance with null values.
     */
    public OwnerDTO() {
        // Intentionally left empty
    }

    /**
     * Initializes a new OwnerDTO instance which is a shallow copy of the provided
     * source entity.
     *
     * @param source
     *  The source entity to copy
     */
    public OwnerDTO(OwnerDTO source) {
        super(source);
    }

    @HateoasInclude
    @JsonProperty
    public String getId() {
        return this.id;
    }

    @JsonIgnore
    public OwnerDTO setId(String id) {
        this.id = id;
        return this;
    }

    public String getDefaultServiceLevel() {
        return defaultServiceLevel;
    }

    public OwnerDTO setDefaultServiceLevel(String defaultServiceLevel) {
        this.defaultServiceLevel = defaultServiceLevel;
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return String.format("OwnerDTO [id: %s, defaultServiceLevel: %s]",
            this.getId(), this.getDefaultServiceLevel());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }

        if (obj instanceof OwnerDTO) {
            OwnerDTO that = (OwnerDTO) obj;

            EqualsBuilder builder = new EqualsBuilder()
                .append(this.getId(), that.getId())
                .append(this.getDefaultServiceLevel(), that.getDefaultServiceLevel());

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
            .append(this.getId())
            .append(this.getDefaultServiceLevel());

        return builder.toHashCode();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public OwnerDTO populate(OwnerDTO source) {
        super.populate(source);

        this.setId(source.getId());
        this.setDefaultServiceLevel(source.getDefaultServiceLevel());

        return this;
    }
}
