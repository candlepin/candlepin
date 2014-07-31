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

import com.mongodb.BasicDBObject;
import com.mongodb.DBObject;

import java.util.Date;

/**
 *
 * @param <EntityType> type of oldEntity and newEntity, could be json string, or some DBObject
 * @param <PrincipalType> type of principalStore, could be json string, or some DBObject
 */
public class AbstractEvent<EntityType, PrincipalType> extends BasicDBObject {

    public static final String TARGET = "target";
    public static final String TIMESTAMP = "timestamp";
    public static final String OWNER_ID = "ownerId";
    public static final String REFERENCE_ID = "referenceId";
    public static final String REFERENCE_TYPE = "referenceType";
    public static final String PRINCIPAL_STORE = "principalStore";
    public static final String ENTITY_ID = "entityId";
    public static final String OLD_ENTITY = "oldEntity";
    public static final String NEW_ENTITY = "newEntity";
    public static final String CONSUMER_ID = "consumerId";
    public static final String TARGET_NAME = "targetName";
    public static final String MESSAGE_TEXT = "messageText";
    public static final String ID = "id";
    public static final String TYPE = "type";
    public static final String SOURCE_EVENT = "_source_id";

    public AbstractEvent() {
    }

    public AbstractEvent(DBObject dbObject) {
        this.putAll(dbObject);
    }

    public String getId() {
        return this.getString(ID);
    }

    public void setId(String id) {
        this.put(ID, id);
    }

    public String getType() {
        return this.getString(TYPE);
    }

    public void setType(String type) {
        this.put(TYPE, type);
    }

    public String getTarget() {
        return this.getString(TARGET);
    }

    public void setTarget(String target) {
        this.put(TARGET, target);
    }

    public Date getTimestamp() {
        return this.getDate(TIMESTAMP);
    }

    public void setTimestamp(Date timestamp) {
        this.put(TIMESTAMP, timestamp);
    }

    public String getOwnerId() {
        return this.getString(OWNER_ID);
    }

    public void setOwnerId(String ownerId) {
        this.put(OWNER_ID, ownerId);
    }

    // Generic id field in case a cross reference is needed to some other entity
    // Use with reference type)
    public String getReferenceId() {
        return this.getString(REFERENCE_ID);
    }

    public void setReferenceId(String referenceId) {
        this.put(REFERENCE_ID, referenceId);
    }

    // Classifies Generic id field in case a cross reference is needed to some
    // other entity
    // Use with reference id
    public String getReferenceType() {
        return getString(REFERENCE_TYPE);
    }

    public void setReferenceType(String referenceType) {
        this.put(REFERENCE_TYPE, referenceType);
    }

    @SuppressWarnings("unchecked")
    public PrincipalType getPrincipalStore() {
        return (PrincipalType) this.get(PRINCIPAL_STORE);
    }

    // String representation of the principal. We probably should not be
    // reconstructing any stored principal object.
    public void setPrincipalStore(PrincipalType principalStore) {
        this.put(PRINCIPAL_STORE, principalStore);
    }

    public String getEntityId() {
        return this.getString(ENTITY_ID);
    }

    public void setEntityId(String entityId) {
        this.put(ENTITY_ID, entityId);
    }

    // Both old/new may be null for creation/deletion events. These are marked
    // Transient as we decided we do not necessarily want to store the object
    // state in our Events table. The Event passing through the message queue will
    // still carry them.
    @SuppressWarnings("unchecked")
    public EntityType getOldEntity() {
        return (EntityType) this.get(OLD_ENTITY);
    }

    public void setOldEntity(EntityType oldEntity) {
        this.put(OLD_ENTITY, oldEntity);
    }

    @SuppressWarnings("unchecked")
    public EntityType getNewEntity() {
        return (EntityType) this.get(NEW_ENTITY);
    }

    public void setNewEntity(EntityType newEntity) {
        this.put(NEW_ENTITY, newEntity);
    }

    @Override
    public String toString() {
        return "Event [" + "id=" + getId() + ", target=" + getTarget() +
            ", type=" + getType() + ", time=" + getTimestamp() + ", entity=" +
            getEntityId() + "]";
    }

    public String getConsumerId() {
        return this.getString(CONSUMER_ID);
    }

    public void setConsumerId(String consumerId) {
        this.put(CONSUMER_ID, consumerId);
    }

    /**
     * @return the targetName
     */
    public String getTargetName() {
        return this.getString(TARGET_NAME);
    }

    /**
     * @param targetName the targetName to set
     */
    public void setTargetName(String targetName) {
        this.put(TARGET_NAME, targetName);
    }

    /**
     * @return the messageText
     */
    public String getMessageText() {
        return this.getMessageText();
    }

    /**
     * @param messageText the messageText to set
     */
    public void setMessageText(String messageText) {
        this.put(MESSAGE_TEXT, messageText);
    }
}
