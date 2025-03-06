/*
 * Copyright (c) 2009 - 2025 Red Hat, Inc.
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
package org.candlepin.model;

import org.candlepin.util.Util;

import org.hibernate.annotations.GenericGenerator;

import java.util.Date;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.Table;

/**
 * A ContentAccessPayload entity represents an encoded and compressed collection of content that is typically
 * included in a {@link SCACertificate}.
 */
@Entity
@Table(name = ContentAccessPayload.DB_TABLE)
public class ContentAccessPayload extends AbstractHibernateObject<ContentAccessPayload> {

    /** Name of the table backing this object in the database */
    public static final String DB_TABLE = "cp_content_access_payload";

    @Id
    @GeneratedValue(generator = "system-uuid")
    @GenericGenerator(name = "system-uuid", strategy = "uuid")
    @Column(name = "id", nullable = false)
    private String id;

    @Column(name = "owner_id", nullable = false)
    private String ownerId;

    @Column(name = "payload_key", nullable = false)
    private String payloadKey;

    @Column(name = "payload", nullable = false)
    private String payload;

    @Column(name = "timestamp", nullable = false)
    private Date timestamp;

    public ContentAccessPayload() {
        // Intentionally left empty
    }

    /**
     * Sets the ID for this ContentAccessPayload instance.
     *
     * @param id
     *  the ID to set
     *
     * @throws IllegalArgumentException
     *  if the provided ID is null
     *
     * @return this ContentAccessPayload instance
     */
    public ContentAccessPayload setId(String id) {
        if (id == null) {
            throw new IllegalArgumentException("id is null");
        }

        this.id = id;
        return this;
    }

    /**
     * @return the ID of this instance
     */
    public String getId() {
        return id;
    }

    /**
     * Sets the owner for this ContentAccessPayload instance.
     *
     * @param owner
     *  the owner to set
     *
     * @throws IllegalArgumentException
     *  if the provided owner is null or if the owner has a null ID
     *
     * @return this ContentAccessPayload instance
     */
    public ContentAccessPayload setOwner(Owner owner) {
        if (owner == null) {
            throw new IllegalArgumentException("owner is null");
        }

        if (owner.getId() == null) {
            throw new IllegalArgumentException("owner ID is null");
        }

        this.ownerId = owner.getId();
        return this;
    }

    /**
     * Sets the owner ID for this ContentAccessPayload instance.
     *
     * @param ownerId
     *  the owner ID to set
     *
     * @throws IllegalArgumentException
     *  if the provided owner ID is null
     *
     * @return this ContentAccessPayload instance
     */
    public ContentAccessPayload setOwnerId(String ownerId) {
        if (ownerId == null) {
            throw new IllegalArgumentException("ownerId is null");
        }

        this.ownerId = ownerId;
        return this;
    }

    /**
     * @return the owner ID of this instance
     */
    public String getOwnerId() {
        return ownerId;
    }

    /**
     * Sets the payload key for this ContentAccessPayload instance.
     *
     * @param payloadKey
     *  the payload key to set
     *
     * @throws IllegalArgumentException
     *  if the provided payload key is null
     *
     * @return this ContentAccessPayload instance
     */
    public ContentAccessPayload setPayloadKey(String payloadKey) {
        if (payloadKey == null) {
            throw new IllegalArgumentException("payloadKey is null");
        }

        this.payloadKey = payloadKey;
        return this;
    }

    /**
     * @return the payload key of this instance
     */
    public String getPayloadKey() {
        return payloadKey;
    }

    /**
     * Sets the payload for this ContentAccessPayload instance.
     *
     * @param payload
     *  the payload to set
     *
     * @throws IllegalArgumentException
     *  if the provided payload is null
     *
     * @return this ContentAccessPayload instance
     */
    public ContentAccessPayload setPayload(String payload) {
        if (payload == null) {
            throw new IllegalArgumentException("payload is null");
        }

        this.payload = payload;
        return this;
    }

    /**
     * @return the payload of this instance
     */
    public String getPayload() {
        return this.payload;
    }

    /**
     * Sets the timestamp for this ContentAccessPayload instance.
     *
     * @param timestamp
     *  the timestamp to set
     *
     * @throws IllegalArgumentException
     *  if the provided timestamp is null
     *
     * @return this ContentAccessPayload instance
     */
    public ContentAccessPayload setTimestamp(Date timestamp) {
        if (timestamp == null) {
            throw new IllegalArgumentException("timestamp is null");
        }

        this.timestamp = timestamp;
        return this;
    }

    /**
     * @return the timestamp of this instance
     */
    public Date getTimestamp() {
        return Util.firstOf(this.timestamp, this.getCreated(), new Date());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        Date timestamp = this.getTimestamp();
        String formattedTimestamp = timestamp != null ? String.format("%1$tF %1$tT%1$tz", timestamp) : null;

        return String.format(
            "ContentAccessPayload [org: %s, payload_key: %s, timestamp: %s]",
            this.getOwnerId(), this.getPayloadKey(), formattedTimestamp);
    }

}
