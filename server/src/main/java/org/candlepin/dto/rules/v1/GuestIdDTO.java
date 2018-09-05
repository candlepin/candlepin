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

import org.candlepin.common.jackson.HateoasArrayExclude;
import org.candlepin.common.jackson.HateoasInclude;
import org.candlepin.dto.CandlepinDTO;
import org.candlepin.util.MapView;

import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.commons.lang.builder.HashCodeBuilder;

import java.util.HashMap;
import java.util.Map;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * A DTO representation of the GuestId entity for the Rules framework
 */
@XmlRootElement
@XmlAccessorType(XmlAccessType.PROPERTY)
public class GuestIdDTO extends CandlepinDTO<GuestIdDTO> {
    public static final long serialVersionUID = 1L;

    protected String id;
    protected String guestId;
    protected Map<String, String> attributes;

    /**
     * Initializes a new GuestIdDTO instance with null values.
     */
    public GuestIdDTO() {
        // Intentionally left empty
    }

    /**
     * Initializes a new GuestIdDTO instance which is a shallow copy of the provided
     * source entity.
     *
     * @param source
     *  The source entity to copy
     */
    public GuestIdDTO(GuestIdDTO source) {
        super(source);
    }

    /**
     * Retrieves the id field of this GuestIdDTO object.
     *
     * @return the id field of this GuestIdDTO object.
     */
    @HateoasInclude
    public String getId() {
        return this.id;
    }

    /**
     * Sets the id to set on this GuestIdDTO object.
     *
     * @param id the id to set on this GuestIdDTO object.
     *
     * @return a reference to this DTO object.
     */
    public GuestIdDTO setId(String id) {
        this.id = id;
        return this;
    }

    /**
     * Retrieves the guestId field of this GuestIdDTO object.
     *
     * @return the guestId field of this GuestIdDTO object.
     */
    @HateoasInclude
    public String getGuestId() {
        return this.guestId;
    }

    /**
     * Sets the guestId to set on this GuestIdDTO object.
     *
     * @param guestId the guestId to set on this GuestIdDTO object.
     *
     * @return a reference to this DTO object.
     */
    public GuestIdDTO setGuestId(String guestId) {
        this.guestId = guestId;
        return this;
    }

    /**
     * Retrieves the attributes field of this GuestIdDTO object.
     *
     * @return the attributes field of this GuestIdDTO object.
     */
    @HateoasArrayExclude
    public Map<String, String> getAttributes() {
        return this.attributes != null ? new MapView<>(attributes) : null;
    }

    /**
     * Sets the attributes on this GuestIdDTO object.
     *
     * Please note the absence of the add/remove attribute methods.
     * feel free to add them if needed.
     *
     * @param attributes the attributes to set on this GuestIdDTO object.
     *
     * @return a reference to this DTO object.
     */
    public GuestIdDTO setAttributes(Map<String, String> attributes) {
        if (attributes != null) {
            if (this.attributes == null) {
                this.attributes = new HashMap<>();
            }
            else {
                this.attributes.clear();
            }
            this.attributes.putAll(attributes);
        }
        else {
            this.attributes = null;
        }
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return String.format("GuestIdDTO [id: %s, guestId: %s]", this.getId(), this.getGuestId());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }

        if (obj instanceof GuestIdDTO) {
            GuestIdDTO that = (GuestIdDTO) obj;

            EqualsBuilder builder = new EqualsBuilder()
                .append(this.getId(), that.getId())
                .append(this.getGuestId(), that.getGuestId())
                .append(this.getAttributes(), that.getAttributes());

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
            .append(this.getGuestId())
            .append(this.getAttributes());

        return builder.toHashCode();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public GuestIdDTO clone() {
        GuestIdDTO copy = super.clone();

        Map<String, String> attributes = this.getAttributes();
        copy.setAttributes(null);
        if (attributes != null) {
            copy.setAttributes(attributes);
        }
        return copy;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public GuestIdDTO populate(GuestIdDTO source) {
        super.populate(source);

        if (source != this) {
            this.setId(source.getId());
            this.setGuestId(source.getGuestId());
            this.setAttributes(source.getAttributes());
        }

        return this;
    }
}
