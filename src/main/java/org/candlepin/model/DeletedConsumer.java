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
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * DeletedConsumer
 */
@XmlRootElement
@XmlAccessorType(XmlAccessType.PROPERTY)
@Entity
@Table(name = "cp_deleted_consumers")
public class DeletedConsumer extends AbstractHibernateObject {

    @Id
    @GeneratedValue(generator = "system-uuid")
    @GenericGenerator(name = "system-uuid", strategy = "uuid")
    @Column(length = 32)
    private String id;

    /**
     * using the id instead of actual Consumer because we will be deleting the
     * real consumer hence no foreign key.
     */
    @Column(name = "consumer_uuid", length = 255, nullable = false, unique = true)
    private String consumerUuid;

    /**
     * using the id instead of actual Owner because the owner could be deleted
     * and we still want to keep this record around.
     */
    @Column(name = "owner_id", length = 32, nullable = false)
    private String ownerId;

    @Column(name = "owner_key", length = 255, nullable = false)
    private String ownerKey;

    @Column(name = "owner_displayname", length = 255, nullable = false)
    private String ownerDisplayName;

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

    /**
     * @param id the db id.
     */
    public void setId(String id) {
        this.id = id;
    }

    public void setConsumerUuid(String cid) {
        consumerUuid = cid;
    }

    public String getConsumerUuid() {
        return consumerUuid;
    }

    public void setOwnerId(String oid) {
        ownerId = oid;
    }

    public String getOwnerId() {
        return ownerId;
    }

    public void setOwnerKey(String okey) {
        ownerKey = okey;
    }

    public String getOwnerKey() {
        return ownerKey;
    }
    public void setOwnerDisplayName(String oname) {
        ownerDisplayName = oname;
    }

    public String getOwnerDisplayName() {
        return ownerDisplayName;
    }
}
