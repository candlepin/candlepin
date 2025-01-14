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

import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.Type;

import java.util.Date;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.validation.constraints.NotNull;



@Entity
@Table(name = ContentAccessPayload.DB_TABLE)
public class ContentAccessPayload {

    /** Name of the table backing this object in the database */
    public static final String DB_TABLE = "cp_content_access_payload";

    // I hate that this field exists, but Hibernate/JPA requires a bunch of extra garbage to make composite
    // keys function. Namely, we need a wrapper class around each field in the key, and it needs to be
    // serializable. Barf.
    @Id
    @GeneratedValue(generator = "system-uuid")
    @GenericGenerator(name = "system-uuid", strategy = "uuid")
    @Column(name = "id", nullable = false)
    private String id;

    @Column(name = "owner_id", nullable = false)
    private String ownerId;

    @Column(name = "payload_key", nullable = false)
    private String payloadKey;

    @Column(name = "timestamp", nullable = false)
    private Date timestamp;

    @Column(name = "payload", nullable = false)
    private String payload;




    public ContentAccessPayload() {
        // Intentionally left empty
    }


    public ContentAccessPayload setId(String id) {
        if (id == null) {
            throw new IllegalArgumentException("id is null");
        }

        this.id = id;
        return this;
    }

    public String getId() {
        return this.id;
    }

    public ContentAccessPayload setOwner(Owner owner) {
        if (owner == null) {
            throw new IllegalArgumentException("owner is null");
        }

        if (owner.getId() == null) {
            throw new IllegalStateException("owner lacks an ID");
        }

        this.ownerId = owner.getId();
        return this;
    }

    public ContentAccessPayload setOwnerId(String ownerId) {
        if (ownerId == null) {
            throw new IllegalArgumentException("ownerId is null");
        }

        this.ownerId = ownerId;
        return this;
    }

    public String getOwnerId() {
        return this.ownerId;
    }


    public ContentAccessPayload setPayloadKey(String payloadKey) {
        if (payloadKey == null) {
            throw new IllegalArgumentException("payloadKey is null");
        }

        this.payloadKey = payloadKey;
        return this;
    }

    public String getPayloadKey() {
        return this.payloadKey;
    }



    public ContentAccessPayload setTimestamp(Date timestamp) {
        if (timestamp == null) {
            throw new IllegalArgumentException("timestamp is null");
        }

        this.timestamp = timestamp;
        return this;
    }

    public Date getTimestamp() {
        return this.timestamp;
    }


    public ContentAccessPayload setPayload(String payload) {
        if (payload == null) {
            throw new IllegalArgumentException("payload is null");
        }

        this.payload = payload;
        return this;
    }

    public String getPayload() {
        return this.payload;
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        Date timestamp = this.getTimestamp();
        String tsString = timestamp != null ? String.format("%1$tF %1$tT%1$tz", timestamp) : null;

        return String.format(
            "ContentAccessPayload [org: %s, payload_key: %s, timestamp: %s]",
            this.getOwnerId(), this.getPayloadKey(), tsString);
    }

}
