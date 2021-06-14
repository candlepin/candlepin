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
package org.candlepin.dto.manifest.v1;

import org.candlepin.dto.CandlepinDTO;
import org.candlepin.dto.TimestampedCandlepinDTO;
import org.candlepin.util.SetView;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.commons.lang.builder.HashCodeBuilder;

import java.util.HashSet;
import java.util.Set;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * A DTO representation of the DistributorVersion entity as used by the manifest import/export framework.
 */
@XmlRootElement(name = "distributorversion")
@XmlAccessorType(XmlAccessType.PROPERTY)
public class DistributorVersionDTO extends TimestampedCandlepinDTO<DistributorVersionDTO> {
    public static final long serialVersionUID = 1L;

    /**
     * Internal DTO object for DistributorVersionCapability
     */
    @XmlRootElement(name = "distributorversioncapability")
    @XmlAccessorType(XmlAccessType.PROPERTY)
    public static class DistributorVersionCapabilityDTO
        extends CandlepinDTO<DistributorVersionCapabilityDTO> {

        private String id;
        private String name;

        @JsonCreator
        public DistributorVersionCapabilityDTO(
            @JsonProperty("id") String id,
            @JsonProperty("name") String name) {
            if (name == null || name.isEmpty()) {
                throw new IllegalArgumentException(
                    "The distributor version capability name is null or empty.");
            }

            this.id = id;
            this.name = name;
        }

        public String getId() {
            return this.id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getName() {
            return this.name;
        }

        public void setName(String name) {
            this.name = name;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this) {
                return true;
            }

            if (obj instanceof DistributorVersionCapabilityDTO) {
                DistributorVersionCapabilityDTO that = (DistributorVersionCapabilityDTO) obj;

                EqualsBuilder builder = new EqualsBuilder()
                    .append(this.getId(), that.getId())
                    .append(this.getName(), that.getName());

                return builder.isEquals();
            }

            return false;
        }

        @Override
        public int hashCode() {
            HashCodeBuilder builder = new HashCodeBuilder(37, 7)
                .append(this.getId())
                .append(this.getName());

            return builder.toHashCode();
        }
    }

    private String id;
    private String name;
    private String displayName;
    private Set<DistributorVersionCapabilityDTO> capabilities;

    /**
     * Initializes a new DistributorVersionDTO instance with null values.
     */
    public DistributorVersionDTO() {
        // Intentionally left empty
    }

    /**
     * Initializes a new DistributorVersionDTO instance which is a shallow copy of the provided
     * source entity.
     *
     * @param source
     *  The source entity to copy
     */
    public DistributorVersionDTO(DistributorVersionDTO source) {
        super(source);
    }

    /**
     * Retrieves the id field of this DistributorVersionDTO object.
     *
     * @return the id field of this DistributorVersionDTO object.
     */
    public String getId() {
        return this.id;
    }

    /**
     * Sets the id to set on this DistributorVersionDTO object.
     *
     * @param id the id to set on this DistributorVersionDTO object.
     *
     * @return a reference to this DTO object.
     */
    public DistributorVersionDTO setId(String id) {
        this.id = id;
        return this;
    }

    /**
     * Retrieves the name field of this DistributorVersionDTO object.
     *
     * @return the name field of this DistributorVersionDTO object.
     */
    public String getName() {
        return name;
    }

    /**
     * Sets the name to set on this DistributorVersionDTO object.
     *
     * @param name the name to set on this DistributorVersionDTO object.
     *
     * @return a reference to this DTO object.
     */
    public DistributorVersionDTO setName(String name) {
        this.name = name;
        return this;
    }

    /**
     * Retrieves the display name field of this DistributorVersionDTO object.
     *
     * @return the display name field of this DistributorVersionDTO object.
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * Sets the display name to set on this DistributorVersionDTO object.
     *
     * @param displayName the display name to set on this DistributorVersionDTO object.
     *
     * @return a reference to this DTO object.
     */
    public DistributorVersionDTO setDisplayName(String displayName) {
        this.displayName = displayName;
        return this;
    }

    /**
     * Retrieves a view of the capabilities for the distributor version represented by this DTO.
     * If the capabilities have not yet been defined, this method returns null.
     * <p></p>
     * Note that the collection returned by this method is a view of the collection backing this
     * set of capabilities. Elements cannot be added to the collection, but elements may be removed.
     * Changes made to the collection will be reflected by this distributor version DTO instance.
     *
     * @return
     *  the capabilities associated with this distributor version, or null if they have not yet been defined
     */
    public Set<DistributorVersionCapabilityDTO> getCapabilities() {
        return this.capabilities != null ? new SetView<>(this.capabilities) : null;
    }

    /**
     * Adds the collection of capabilities to this DistributorVersionDTO.
     *
     * @param capabilities
     *  A set of capabilities to attach to this DTO, or null to clear the existing ones
     *
     * @return
     *  A reference to this DTO
     */
    public DistributorVersionDTO setCapabilities(Set<DistributorVersionCapabilityDTO> capabilities) {
        if (capabilities != null) {
            if (this.capabilities == null) {
                this.capabilities = new HashSet<>();
            }
            else {
                this.capabilities.clear();
            }

            for (DistributorVersionCapabilityDTO dto : capabilities) {
                if (isNullOrIncomplete(dto)) {
                    throw new IllegalArgumentException(
                        "collection contains null or incomplete capabilities");
                }
            }

            this.capabilities.addAll(capabilities);
        }
        else {
            this.capabilities = null;
        }
        return this;
    }

    /**
     * Adds the given capability to this DistributorVersionDTO.
     *
     * @param capability
     *  The capability to add to this DistributorVersionDTO.
     *
     * @return
     *  true if this capability was not already contained in this DistributorVersionDTO.
     */
    @JsonIgnore
    public boolean addCapability(DistributorVersionCapabilityDTO capability) {
        if (isNullOrIncomplete(capability)) {
            throw new IllegalArgumentException("capability is null or incomplete");
        }

        if (this.capabilities == null) {
            this.capabilities = new HashSet<>();
        }

        return this.capabilities.add(capability);
    }

    /**
     * Utility method to validate DistributorVersionCapabilityDTO input
     */
    private boolean isNullOrIncomplete(DistributorVersionCapabilityDTO capability) {
        return capability == null || capability.getName() == null || capability.getName().isEmpty();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return String.format("DistributorVersionDTO [id: %s, name: %s, displayName: %s]",
            this.getId(), this.getName(), this.getDisplayName());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }

        if (obj instanceof DistributorVersionDTO && super.equals(obj)) {
            DistributorVersionDTO that = (DistributorVersionDTO) obj;

            EqualsBuilder builder = new EqualsBuilder()
                .append(this.getId(), that.getId())
                .append(this.getName(), that.getName())
                .append(this.getDisplayName(), that.getDisplayName())
                .append(this.getCapabilities(), that.getCapabilities());

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
            .append(this.getName())
            .append(this.getDisplayName())
            .append(this.getCapabilities());

        return builder.toHashCode();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public DistributorVersionDTO clone() {
        DistributorVersionDTO copy = super.clone();

        copy.setCapabilities(this.getCapabilities());

        return copy;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public DistributorVersionDTO populate(DistributorVersionDTO source) {
        super.populate(source);

        this.setId(source.getId())
            .setName(source.getName())
            .setDisplayName(source.getDisplayName())
            .setCapabilities(source.getCapabilities());

        return this;
    }
}
