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

import org.candlepin.dto.TimestampedCandlepinDTO;

import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.commons.lang.builder.HashCodeBuilder;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * A DTO representation of the DeletedConsumer entity
 */
@XmlRootElement
@XmlAccessorType(XmlAccessType.PROPERTY)
public class DeletedConsumerDTO extends TimestampedCandlepinDTO<DeletedConsumerDTO> {
    public static final long serialVersionUID = 1L;

    private String id;
    private String consumerUuid;
    private String ownerId;
    private String ownerKey;
    private String ownerDisplayName;
    private String principalName;

    /**
     * Initializes a new DeletedConsumerDTO instance with null values.
     */
    public DeletedConsumerDTO() {
        // Intentionally left empty
    }

    /**
     * Initializes a new DeletedConsumerDTO instance which is a shallow copy of the provided
     * source entity.
     *
     * @param source
     *  The source entity to copy
     */
    public DeletedConsumerDTO(DeletedConsumerDTO source) {
        super(source);
    }

    /**
     * Sets the id to set on this DeletedConsumerDTO object.
     *
     * @param id the id to set on this DeletedConsumerDTO object.
     *
     * @return a reference to this DTO object.
     */
    public DeletedConsumerDTO setId(String id) {
        this.id = id;
        return this;
    }

    /**
     * Retrieves the id field of this DeletedConsumerDTO object.
     *
     * @return the id field of this DeletedConsumerDTO object.
     */
    public String getId() {
        return id;
    }

    /**
     * Sets the consumer uuid to set on this DeletedConsumerDTO object.
     *
     * @param consumerUuid the consumer uuid to set on this DeletedConsumerDTO object.
     *
     * @return a reference to this DTO object.
     */
    public DeletedConsumerDTO setConsumerUuid(String consumerUuid) {
        this.consumerUuid = consumerUuid;
        return this;
    }

    /**
     * Retrieves the consumer uuid field of this DeletedConsumerDTO object.
     *
     * @return the consumer uuid field of this DeletedConsumerDTO object.
     */
    public String getConsumerUuid() {
        return consumerUuid;
    }

    /**
     * Sets the owner id to set on this DeletedConsumerDTO object.
     *
     * @param ownerId the owner id to set on this DeletedConsumerDTO object.
     *
     * @return a reference to this DTO object.
     */
    public DeletedConsumerDTO setOwnerId(String ownerId) {
        this.ownerId = ownerId;
        return this;
    }

    /**
     * Retrieves the owner id field of this DeletedConsumerDTO object.
     *
     * @return the owner id field of this DeletedConsumerDTO object.
     */
    public String getOwnerId() {
        return ownerId;
    }

    /**
     * Sets the owner key to set on this DeletedConsumerDTO object.
     *
     * @param ownerKey the owner key to set on this DeletedConsumerDTO object.
     *
     * @return a reference to this DTO object.
     */
    public DeletedConsumerDTO setOwnerKey(String ownerKey) {
        this.ownerKey = ownerKey;
        return this;
    }

    /**
     * Retrieves the owner key field of this DeletedConsumerDTO object.
     *
     * @return the owner key field of this DeletedConsumerDTO object.
     */
    public String getOwnerKey() {
        return ownerKey;
    }

    /**
     * Sets the owner display name to set on this DeletedConsumerDTO object.
     *
     * @param ownerDisplayName the owner display name to set on this DeletedConsumerDTO object.
     *
     * @return a reference to this DTO object.
     */
    public DeletedConsumerDTO setOwnerDisplayName(String ownerDisplayName) {
        this.ownerDisplayName = ownerDisplayName;
        return this;
    }

    /**
     * Retrieves the owner display name field of this DeletedConsumerDTO object.
     *
     * @return the owner display name field of this DeletedConsumerDTO object.
     */
    public String getOwnerDisplayName() {
        return ownerDisplayName;
    }

    /**
     * Sets or clears the name of the principal that caused the consumer to be deleted. If the
     * incoming principal name is null, any existing value will be cleared.
     *
     * @param principalName
     *  the name of the principal to set for this deletion event, or null to clear it
     *
     * @return
     *  a reference to this DTO
     */
    public DeletedConsumerDTO setPrincipalName(String principalName) {
        this.principalName = principalName;
        return this;
    }

    /**
     * Fetches the name of the principal that caused the consumer to be deleted, or null if the
     * principal has not yet been set.
     *
     * @return
     *  the name of the principal that caused the consumer to be deleted, or null if the principal
     *  has not been set
     */
    public String getPrincipalName() {
        return this.principalName;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return String.format("DeletedConsumerDTO [id: %s, consumer uuid: %s, owner key: %s]",
            this.getId(), this.getConsumerUuid(), this.getOwnerKey());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }

        if (obj instanceof DeletedConsumerDTO && super.equals(obj)) {
            DeletedConsumerDTO that = (DeletedConsumerDTO) obj;

            EqualsBuilder builder = new EqualsBuilder()
                .append(this.getId(), that.getId())
                .append(this.getConsumerUuid(), that.getConsumerUuid())
                .append(this.getOwnerId(), that.getOwnerId())
                .append(this.getOwnerKey(), that.getOwnerKey())
                .append(this.getOwnerDisplayName(), that.getOwnerDisplayName())
                .append(this.getPrincipalName(), that.getPrincipalName());

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
            .append(this.getConsumerUuid())
            .append(this.getOwnerId())
            .append(this.getOwnerKey())
            .append(this.getOwnerDisplayName())
            .append(this.getPrincipalName());

        return builder.toHashCode();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public DeletedConsumerDTO clone() {
        return super.clone();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public DeletedConsumerDTO populate(DeletedConsumerDTO source) {
        super.populate(source);

        this.setId(source.getId())
            .setConsumerUuid(source.getConsumerUuid())
            .setOwnerId(source.getOwnerId())
            .setOwnerKey(source.getOwnerKey())
            .setOwnerDisplayName(source.getOwnerDisplayName())
            .setPrincipalName(source.getPrincipalName());

        return this;
    }
}
