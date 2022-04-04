/**
 * Copyright (c) 2009 - 2012 Red Hat, Inc.
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

import org.hibernate.annotations.GenericGenerator;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * DeletedConsumer
 */
@XmlRootElement
@XmlAccessorType(XmlAccessType.PROPERTY)
@Entity
@Table(name = DeletedConsumer.DB_TABLE)
public class DeletedConsumer extends AbstractHibernateObject {

    /** Name of the table backing this object in the database */
    public static final String DB_TABLE = "cp_deleted_consumers";

    @Id
    @GeneratedValue(generator = "system-uuid")
    @GenericGenerator(name = "system-uuid", strategy = "uuid")
    @Column(length = 32)
    @NotNull
    private String id;

    /**
     * using the id instead of actual Consumer because we will be deleting the
     * real consumer hence no foreign key.
     */
    @Column(name = "consumer_uuid", length = 255, nullable = false, unique = true)
    @Size(max = 255)
    @NotNull
    private String consumerUuid;

    @Column(name = "consumer_name", length = 255)
    @Size(max = 255)
    @NotNull
    private String consumerName;

    /**
     * using the id instead of actual Owner because the owner could be deleted
     * and we still want to keep this record around.
     */
    @Column(name = "owner_id", length = 32, nullable = false)
    @Size(max = 32)
    @NotNull
    private String ownerId;

    @Column(name = "owner_key", length = 255, nullable = false)
    @Size(max = 255)
    private String ownerKey;

    @Column(name = "owner_displayname", length = 255, nullable = false)
    @Size(max = 255)
    private String ownerDisplayName;

    @Column(name = "principal_name")
    private String principalName;

    public DeletedConsumer(String cuuid, String oid, String okey, String oname) {
        consumerUuid = cuuid;
        ownerId = oid;
        ownerKey = okey;
        ownerDisplayName = oname;
    }

    public DeletedConsumer() {

    }

    @Override
    public String getId() {
        return id;
    }

    public DeletedConsumer setId(String id) {
        this.id = id;
        return this;
    }

    public DeletedConsumer setConsumerUuid(String cid) {
        consumerUuid = cid;
        return this;
    }

    public String getConsumerUuid() {
        return consumerUuid;
    }

    public DeletedConsumer setOwnerId(String oid) {
        ownerId = oid;
        return this;
    }

    public DeletedConsumer setConsumerName(String consumerName) {
        this.consumerName = consumerName;
        return this;
    }

    public String getConsumerName() {
        return consumerName;
    }

    public String getOwnerId() {
        return ownerId;
    }

    public DeletedConsumer setOwnerKey(String okey) {
        ownerKey = okey;
        return this;
    }

    public String getOwnerKey() {
        return ownerKey;
    }

    public DeletedConsumer setOwnerDisplayName(String oname) {
        ownerDisplayName = oname;
        return this;
    }

    public String getOwnerDisplayName() {
        return ownerDisplayName;
    }

    public DeletedConsumer setPrincipalName(String principalName) {
        this.principalName = principalName;
        return this;
    }

    public String getPrincipalName() {
        return this.principalName;
    }
}
