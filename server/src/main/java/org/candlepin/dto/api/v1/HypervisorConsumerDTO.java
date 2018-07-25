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

import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.commons.lang.builder.HashCodeBuilder;
import org.candlepin.dto.CandlepinDTO;

/**
 * A DTO representation of the Consumer entity, as exposed by the API for hypervisor updates.
 */
public class HypervisorConsumerDTO extends CandlepinDTO<HypervisorConsumerDTO> {
    private static final long serialVersionUID = 1L;

    /**
     * Internal DTO object representing the owner field.
     */
    public static class OwnerDTO extends CandlepinDTO<OwnerDTO> {

        private String key;

        public OwnerDTO() {
            // Intentionally left blank
        }

        /**
         * Retrieves the key field of this OwnerDTO object.
         *
         * @return the key of the owner, or null if the key has not yet been defined
         */
        public String getKey() {
            return key;
        }

        /**
         * Sets the key to set on this OwnerDTO object.
         *
         * @param key the key to set on this OwnerDTO object.
         *
         * @return a reference to this DTO object.
         */
        public OwnerDTO setKey(String key) {
            this.key = key;
            return this;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this) {
                return true;
            }

            if (obj instanceof OwnerDTO) {
                OwnerDTO that = (OwnerDTO) obj;

                if (this.getKey() == that.getKey()) {
                    return true;
                }

                return this.getKey().equals(that.getKey());
            }

            return false;
        }

        @Override
        public int hashCode() {
            if (this.getKey() == null) {
                return 0;
            }
            return this.getKey().hashCode();
        }
    }

    private String uuid;
    private String name;
    private OwnerDTO owner;

    /**
     * Initializes a new HypervisorConsumerDTO instance with null values.
     */
    public HypervisorConsumerDTO() {
        // Intentionally left blank
    }

    /**
     * Initializes a new HypervisorConsumerDTO instance which is a shallow copy of the provided
     * source entity.
     *
     * @param source
     *  The source entity to copy
     */
    public HypervisorConsumerDTO(HypervisorConsumerDTO source) {
        super(source);
    }

    /**
     * Retrieves the uuid field of this HypervisorConsumerDTO object.
     *
     * @return the uuid of the consumer, or null if the uuid has not yet been defined
     */
    public String getUuid() {
        return this.uuid;
    }

    /**
     * Sets the uuid to set on this HypervisorConsumerDTO object.
     *
     * @param uuid the id to set on this HypervisorConsumerDTO object.
     *
     * @return a reference to this DTO object.
     */
    public HypervisorConsumerDTO setUuid(String uuid) {
        this.uuid = uuid;
        return this;
    }

    /**
     * Retrieves the name field of this HypervisorConsumerDTO object.
     *
     * @return the name of the consumer, or null if the name has not yet been defined
     */
    public String getName() {
        return this.name;
    }

    /**
     * Sets the name to set on this HypervisorConsumerDTO object.
     *
     * @param name the name to set on this HypervisorConsumerDTO object.
     *
     * @return a reference to this DTO object.
     */
    public HypervisorConsumerDTO setName(String name) {
        this.name = name;
        return this;
    }

    /**
     * Retrieves the owner of this HypervisorConsumerDTO object.
     *
     * @return the owner of the consumer, or null if the owner has not yet been defined
     */
    public OwnerDTO getOwner() {
        return this.owner;
    }

    /**
     * Sets the owner to set on this HypervisorConsumerDTO object.
     *
     * @param owner the owner to set on this HypervisorConsumerDTO object.
     *
     * @return a reference to this DTO object.
     */
    public HypervisorConsumerDTO setOwner(OwnerDTO owner) {
        this.owner = owner;
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        String ownerKey = this.getOwner() != null ? this.getOwner().getKey() : null;
        return String.format("HypervisorConsumerDTO [uuid: %s, name: %s, owner key: %s]",
            this.getUuid(), this.getName(), ownerKey);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }

        if (obj instanceof HypervisorConsumerDTO) {
            HypervisorConsumerDTO that = (HypervisorConsumerDTO) obj;

            String thisOkey = this.getOwner() != null ? this.getOwner().getKey() : null;
            String thatOkey = that.getOwner() != null ? that.getOwner().getKey() : null;

            EqualsBuilder builder = new EqualsBuilder()
                .append(this.getUuid(), that.getUuid())
                .append(this.getName(), that.getName())
                .append(thisOkey, thatOkey);

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
            .append(this.getUuid())
            .append(this.getName())
            .append(this.getOwner() != null ? this.getOwner().getKey() : null);

        return builder.toHashCode();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public HypervisorConsumerDTO clone() {
        HypervisorConsumerDTO copy = super.clone();

        OwnerDTO owner = this.getOwner();
        copy.setOwner(owner != null ? owner.clone() : null);

        return copy;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public HypervisorConsumerDTO populate(HypervisorConsumerDTO source) {
        super.populate(source);

        this.setUuid(source.getUuid());
        this.setName(source.getName());
        this.setOwner(source.getOwner());

        return this;
    }
}
