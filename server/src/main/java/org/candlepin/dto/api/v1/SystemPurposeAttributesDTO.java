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
package org.candlepin.dto.api.v1;

import org.candlepin.dto.CandlepinDTO;
import org.candlepin.dto.TimestampedCandlepinDTO;
import org.candlepin.util.MapView;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonProperty.Access;

import io.swagger.annotations.ApiModel;

import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.commons.lang.builder.HashCodeBuilder;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * DTO representation of the system purpose attributes available across the set of products or consumers
 * belonging to an owner
 */
@XmlRootElement
@XmlAccessorType(XmlAccessType.PROPERTY)
@ApiModel(parent = TimestampedCandlepinDTO.class, description = "DTO representing the system purpose " +
    "attributes available across the set of products or consumers belonging to an owner")
public class SystemPurposeAttributesDTO extends CandlepinDTO<SystemPurposeAttributesDTO> {
    private static final long serialVersionUID = 1L;

    @JsonProperty(access = Access.READ_ONLY)
    protected NestedOwnerDTO owner;

    @JsonProperty(access = Access.READ_ONLY)
    protected Map<String, Set<String>> systemPurposeAttributes;

    /**
     * Initializes a new instance with null values.
     */
    public SystemPurposeAttributesDTO() {
        // Empty constructor
    }

    /**
     * Initializes a new instance which is a shallow copy of the provided source entity.
     *
     * @param source
     *  The source entity to copy
     */
    public SystemPurposeAttributesDTO(SystemPurposeAttributesDTO source) {
        super(source);
    }

    public NestedOwnerDTO getOwner() {
        return owner;
    }

    public void setOwner(NestedOwnerDTO owner) {
        this.owner = owner;
    }

    public Map<String, Set<String>> getSystemPurposeAttributes() {
        return (this.systemPurposeAttributes != null) ? new MapView<>(this.systemPurposeAttributes) : null;
    }

    public SystemPurposeAttributesDTO setSystemPurposeAttributes(Map<String, Set<String>> attributes) {
        if (attributes == null) {
            this.systemPurposeAttributes = null;
        }
        else {
            this.systemPurposeAttributes = new HashMap<>();

            for (Map.Entry<String, Set<String>> e : attributes.entrySet()) {
                this.systemPurposeAttributes.put(e.getKey(), new HashSet<>(e.getValue()));
            }
        }
        return this;
    }

    @Override
    public String toString() {
        return String.format("SystemPurposeAttributesDTO [owner: %s, attributes: %s", owner,
            systemPurposeAttributes);
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        }

        if (o instanceof SystemPurposeAttributesDTO) {
            SystemPurposeAttributesDTO that = (SystemPurposeAttributesDTO) o;

            EqualsBuilder builder = new EqualsBuilder()
                .append(this.getOwner(), that.getOwner())
                .append(this.getSystemPurposeAttributes(), that.getSystemPurposeAttributes());
            return builder.isEquals();
        }
        return false;
    }

    @Override
    public int hashCode() {
        HashCodeBuilder builder = new HashCodeBuilder(65537, 13)
            .append(this.getOwner())
            .append(this.getSystemPurposeAttributes());
        return builder.toHashCode();
    }

    @Override
    public SystemPurposeAttributesDTO populate(SystemPurposeAttributesDTO source) {
        super.populate(source);

        this.setOwner(source.getOwner());
        this.setSystemPurposeAttributes(source.getSystemPurposeAttributes());
        return this;
    }
}
