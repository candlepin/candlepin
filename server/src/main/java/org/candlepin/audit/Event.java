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
package org.candlepin.audit;

import java.util.Date;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.persistence.Transient;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;

import org.candlepin.auth.Principal;
import org.candlepin.auth.PrincipalData;
import org.candlepin.model.Persisted;
import org.candlepin.util.Util;
import org.hibernate.annotations.GenericGenerator;

/**
 * Event - Base class for Candlepin events. Serves as both our semi-permanent
 * audit history in the database, as well as an integral part of the event
 * queue.
 */
@Entity
@Table(name = "cp_event")
@XmlRootElement(namespace = "http://fedorahosted.org/candlepin/Event")
@XmlAccessorType(XmlAccessType.PROPERTY)
public class Event implements Persisted {

    private static final long serialVersionUID = 1L;

    /**
     * Type - Constant representing the type of this event.
     */
    public enum Type {
        CREATED, MODIFIED, DELETED
    }

    /**
     * Target the type of entity operated on.
     */
    public enum Target {
        CONSUMER, OWNER, ENTITLEMENT, POOL, EXPORT, IMPORT, USER, ROLE, SUBSCRIPTION,
        ACTIVATIONKEY, GUESTID, RULES
    }

    /**
     * Describes the value in the referenceId field
     */
    public enum ReferenceType {
        POOL
    }

    // Uniquely identifies the event:
    @Id
    @GeneratedValue(generator = "system-uuid")
    @GenericGenerator(name = "system-uuid", strategy = "uuid")
    @Column(length = 32)
    @NotNull
    private String id;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    @NotNull
    private Type type;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    @NotNull
    private Target target;

    // This should be there, but may not be
    // moo
    @Column(nullable = true)
    @Size(max = 255)
    private String targetName;

    // String representation of the principal. We probably should not be
    // reconstructing
    // any stored principal object.
    @Column(nullable = false, name = "principal")
    @Size(max = 255)
    @NotNull
    private String principalStore;

    @Column(nullable = false)
    @NotNull
    private Date timestamp;

    // Uniquely identifies the entity's ID when combined with the event type.
    // The entity type can be determined from the type field.
    @Column(nullable = false)
    @Size(max = 255)
    @NotNull
    private String entityId;

    @Column(nullable = true)
    @Size(max = 255)
    private String ownerId;

    @Column(nullable = true)
    @Size(max = 255)
    private String consumerId;

    // Generic id field in case a cross reference is needed to some other entity
    // Use with reference type
    @Column(nullable = true)
    @Size(max = 255)
    private String referenceId;

    // Classifies Generic id field in case a cross reference is needed to some
    // other entity
    // Use with reference id
    @Column(nullable = true)
    @Enumerated(EnumType.STRING)
    private ReferenceType referenceType;

    // Both old/new may be null for creation/deletion events. These are marked
    // Transient as we decided we do not necessarily want to store the object
    // state
    // in our Events table. The Event passing through the message queue will
    // still
    // carry them.
    @Transient
    private String oldEntity;
    @Transient
    private String newEntity;

    @Transient
    private String messageText;

    public Event() {
    }

    public Event(Type type, Target target, String targetName,
        Principal principal, String ownerId, String consumerId,
        String entityId, String oldEntity, String newEntity,
        String referenceId, ReferenceType referenceType) {
        this.type = type;
        this.target = target;
        this.targetName = targetName;

        // TODO: toString good enough? Need something better?
        this.principalStore = Util.toJson(principal.getData());
        this.ownerId = ownerId;

        this.entityId = entityId;
        this.oldEntity = oldEntity;
        this.newEntity = newEntity;
        this.consumerId = consumerId;
        this.referenceId = referenceId;
        this.referenceType = referenceType;

        // Set the timestamp to the current date and time.
        this.timestamp = new Date();
    }

    @Override
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Type getType() {
        return type;
    }

    public void setType(Type type) {
        this.type = type;
    }

    public Target getTarget() {
        return target;
    }

    public void setTarget(Target target) {
        this.target = target;
    }

    public PrincipalData getPrincipal() {
        return (PrincipalData) Util.fromJson(this.principalStore,
            PrincipalData.class);
    }

    public void setPrincipal(PrincipalData principal) {
        this.principalStore = Util.toJson(principal);
    }

    public Date getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Date timestamp) {
        this.timestamp = timestamp;
    }

    public String getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(String ownerId) {
        this.ownerId = ownerId;
    }

    public String getReferenceId() {
        return referenceId;
    }

    public void setReferenceId(String referenceId) {
        this.referenceId = referenceId;
    }

    public ReferenceType getReferenceType() {
        return referenceType;
    }

    public void setReferenceType(ReferenceType referenceType) {
        this.referenceType = referenceType;
    }

    @XmlTransient
    public String getPrincipalStore() {
        return principalStore;
    }

    public void setPrincipalStore(String principalStore) {
        this.principalStore = principalStore;
    }

    public String getEntityId() {
        return entityId;
    }

    public void setEntityId(String entityId) {
        this.entityId = entityId;
    }

    @XmlTransient
    public String getOldEntity() {
        return oldEntity;
    }

    public void setOldEntity(String oldEntity) {
        this.oldEntity = oldEntity;
    }

    @XmlTransient
    public String getNewEntity() {
        return newEntity;
    }

    public void setNewEntity(String newEntity) {
        this.newEntity = newEntity;
    }

    @Override
    public String toString() {
        return "Event [" + "id=" + getId() + ", target=" + getTarget() +
            ", type=" + getType() + ", time=" + getTimestamp() + ", entity=" +
            getEntityId() + "]";
    }

    public String getConsumerId() {
        return consumerId;
    }

    public void setConsumerId(String consumerId) {
        this.consumerId = consumerId;
    }

    /**
     * @return the targetName
     */
    public String getTargetName() {
        return targetName;
    }

    /**
     * @param targetName the targetName to set
     */
    public void setTargetName(String targetName) {
        this.targetName = targetName;
    }

    /**
     * @return the messageText
     */
    public String getMessageText() {
        return messageText;
    }

    /**
     * @param messageText the messageText to set
     */
    public void setMessageText(String messageText) {
        this.messageText = messageText;
    }
}
