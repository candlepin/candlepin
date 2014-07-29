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
import com.mongodb.util.JSON;

/**
 * Event - Base class for Candlepin events. Serves as both our semi-permanent
 * audit history in the database, as well as an integral part of the event
 * queue.
 *
 * This class should reflect the one in candlepin, except less strict.
 * We also store the original json as a field in this so that we don't
 * lose values when this class is out of sync with the candlepin version
 */
public class Event extends BasicDBObject {

    private static final String TARGET = "target";
    private static final String TIMESTAMP = "timestamp";
    private static final String OWNER_ID = "ownerId";
    private static final String REFERENCE_ID = "referenceId";
    private static final String REFERENCE_TYPE = "referenceType";
    private static final String PRINCIPAL_STORE = "principalStore";
    private static final String ENTITY_ID = "entityId";
    private static final String OLD_ENTITY = "oldEntity";
    private static final String NEW_ENTITY = "newEntity";
    private static final String CONSUMER_ID = "consumerId";
    private static final String TARGET_NAME = "targetName";
    private static final String MESSAGE_TEXT = "messageText";
    private static final String ID = "id";
    private static final String TYPE = "type";

    public Event() {
    }

    public Event(DBObject dbObject) {
        this.putAll(dbObject);
    }

    public Event(String eventJson) {
        this.putAll((DBObject) JSON.parse(eventJson));
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

    public Long getTimestamp() {
        return this.getLong(TIMESTAMP);
    }

    public void setTimestamp(Long timestamp) {
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

    public String getPrincipalStore() {
        return this.getString(PRINCIPAL_STORE);
    }

    // String representation of the principal. We probably should not be
    // reconstructing any stored principal object.
    public void setPrincipalStore(String principalStore) {
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
    public String getOldEntity() {
        return this.getString(OLD_ENTITY);
    }

    public void setOldEntity(String oldEntity) {
        this.put(OLD_ENTITY, oldEntity);
    }

    public String getNewEntity() {
        return this.getString(NEW_ENTITY);
    }

    public void setNewEntity(String newEntity) {
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
        return this.getString(MESSAGE_TEXT);
    }

    /**
     * @param messageText the messageText to set
     */
    public void setMessageText(String messageText) {
        this.put(MESSAGE_TEXT, messageText);
    }
}

