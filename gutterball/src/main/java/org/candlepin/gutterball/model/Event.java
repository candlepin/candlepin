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
package org.candlepin.gutterball.model;

import java.util.Date;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * Event - Base class for Candlepin events. Serves as both our semi-permanent
 * audit history in the database, as well as an integral part of the event
 * queue.
 *
 * This class should reflect the one in candlepin, except less strict.
 * We also store the original json as a field in this so that we don't
 * lose values when this class is out of sync with the candlepin version
 */
@XmlRootElement
@XmlAccessorType(XmlAccessType.PROPERTY)
public class Event {

    private String id;

    private String type;

    private String target;

    // This should be there, but may not be
    // moo
    private String targetName;

    // String representation of the principal. We probably should not be
    // reconstructing
    // any stored principal object.
    private String principalStore;

    private Date timestamp;

    // Uniquely identifies the entity's ID when combined with the event type.
    // The entity type can be determined from the type field.
    private String entityId;

    private String ownerId;

    private String consumerId;

    // Generic id field in case a cross reference is needed to some other entity
    // Use with reference type)
    private String referenceId;

    // Classifies Generic id field in case a cross reference is needed to some
    // other entity
    // Use with reference id
    private String referenceType;

    // Both old/new may be null for creation/deletion events. These are marked
    // Transient as we decided we do not necessarily want to store the object
    // state
    // in our Events table. The Event passing through the message queue will
    // still
    // carry them.
    private String oldEntity;
    private String newEntity;

    private String messageText;

    private String originalJson;

    public Event() {
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getTarget() {
        return target;
    }

    public void setTarget(String target) {
        this.target = target;
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

    public String getReferenceType() {
        return referenceType;
    }

    public void setReferenceType(String referenceType) {
        this.referenceType = referenceType;
    }

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

    public String getOldEntity() {
        return oldEntity;
    }

    public void setOldEntity(String oldEntity) {
        this.oldEntity = oldEntity;
    }

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

    /**
     * @return the originalJson
     */
    public String getOriginalJson() {
        return originalJson;
    }

    /**
     * @param originalJson the originalJson to set
     */
    public void setOriginalJson(String originalJson) {
        this.originalJson = originalJson;
    }
}

