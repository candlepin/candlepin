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
package org.candlepin.audit;

import org.candlepin.auth.PrincipalData;
import org.candlepin.util.Util;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import tools.jackson.core.JacksonException;

import java.util.Date;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import javax.validation.constraints.NotNull;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;


/**
 * Event - Base class for Candlepin events. Serves as an integral part of the event queue.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@XmlRootElement(namespace = "http://fedorahosted.org/candlepin/Event")
@XmlAccessorType(XmlAccessType.PROPERTY)
public class Event {

    private static final Logger log = LoggerFactory.getLogger(Event.class);

    /**
     * Type - Constant representing the type of this event.
     */
    public enum Type {
        CREATED, MODIFIED, DELETED, EXPIRED,

        /**
         * Represents a deletion event for multiple entities of the given target. Note that because this
         * event type represents many entities, certain event fields *should not* be populated,
         * such as: targetName, or entityId
         */
        BULK_DELETION,

        /**
         * Represents the migration of multiple entities to a target entity. Note that because this event type
         * represents many entities, certain event fields *should not* be populated, such as: targetName or
         * entityId.
         */
        BULK_MIGRATION
    }

    /**
     * Target the type of entity operated on.
     */
    public enum Target {
        CONSUMER, OWNER, ENTITLEMENT, POOL, EXPORT, IMPORT, USER, ROLE, SUBSCRIPTION,
        ACTIVATIONKEY, GUESTID, RULES, COMPLIANCE, SYSTEM_PURPOSE_COMPLIANCE, PRODUCT,
        OWNER_CONTENT_ACCESS_MODE
    }

    /**
     * Describes the value in the referenceId field
     */
    public enum ReferenceType {
        POOL
    }

    @NotNull
    private String id;
    @NotNull
    private Type type;
    @NotNull
    private Target target;
    // This should be there, but may not be
    private String targetName;
    // String representation of the principal. We probably should not be
    // reconstructing
    // any stored principal object.
    @NotNull
    private String principalStore;
    @NotNull
    private Date timestamp;
    private String entityId;
    private String ownerKey;
    private Boolean anonymousOwner;
    private String consumerUuid;
    private String referenceId;
    private ReferenceType referenceType;
    private Map<String, Object> eventData;
    private String messageText;

    /**
     * Default constructor need for deserialization. Should not be used for object creation.
     */
    public Event() {
        // Intentionally left blank
    }

    /**
     * Creates a new event based on the provided type, target, and principal data.
     *
     * @param type
     *  the type of event
     *
     * @param target
     *  the entity of the event
     *
     * @param principalData
     *  the principal data for this event
     *
     * @throws NullPointerException
     *  if the provided type or principal data is null
     */
    public Event(Type type, Target target, PrincipalData principalData) {
        this.type = Objects.requireNonNull(type);
        this.target = Objects.requireNonNull(target);

        try {
            this.principalStore = Util.toJson(principalData);
        }
        catch (JacksonException e) {
            log.error("Error while building JSON for event.", e);
            this.principalStore = "";
        }

        // Set the timestamp to the current date and time.
        this.timestamp = new Date();
        this.id = UUID.randomUUID().toString();
    }

    /**
     * @return the unique identifier for this event.
     */
    public String getId() {
        return this.id;
    }

    /**
     * @return the type of event
     */
    public Type getType() {
        return this.type;
    }

    /**
     * Sets the type of event.
     *
     * @param type
     *  the type to set for this event. The type must not be null.
     *
     * @throws NullPointerException
     *  if type is null
     *
     * @return a reference to this event
     */
    public Event setType(Type type) {
        this.type = Objects.requireNonNull(type);
        return this;
    }

    /**
     * @return the target entity of the event
     */
    public Target getTarget() {
        return this.target;
    }

    /**
     * Sets the target entity for this event.
     *
     * @param target
     *  the target entity for this event. The target must not be null.
     *
     * @throws NullPointerException
     *  if type is null
     *
     * @return a reference to this event
     */
    public Event setTarget(Target target) {
        this.target = Objects.requireNonNull(target);
        return this;
    }

    /**
     * @return the principal data of the event
     */
    public PrincipalData getPrincipalData() {
        return Util.fromJson(this.principalStore,
            PrincipalData.class);
    }

    /**
     * Sets the principal data of the event.
     *
     * @param principalData
     *  the principal data to set for this event
     *
     * @return a reference to this event
     */
    public Event setPrincipalData(PrincipalData principalData) {
        try {
            this.principalStore = Util.toJson(principalData);
        }
        catch (JacksonException e) {
            log.error("Error while building JSON for principal.", e);
            this.principalStore = "";
        }

        return this;
    }

    /**
     * @return the timestamp of when the event was created
     */
    public Date getTimestamp() {
        return this.timestamp;
    }

    /**
     * @return the event owner's key
     */
    public String getOwnerKey() {
        return this.ownerKey;
    }

    /**
     * Sets the owner key for this event.
     *
     * @param ownerKey
     *  the owner key to set for this event
     *
     * @return a reference to this event
     */
    public Event setOwnerKey(String ownerKey) {
        this.ownerKey = ownerKey;
        return this;
    }

    /**
     * @return the anonymous state of the owner for this event
     */
    @JsonProperty("anonymousOwner")
    public Boolean isOwnerAnonymous() {
        return this.anonymousOwner;
    }

    /**
     * Sets if the event's owner is anonymous or not.
     *
     * @param anonymousOwner
     *  if the owner is anonymous or not
     *
     * @return a reference to this event
     */
    @JsonProperty("anonymousOwner")
    public Event setAnonymousOwner(Boolean anonymousOwner) {
        this.anonymousOwner = anonymousOwner;
        return this;
    }

    /**
     * @return the reference ID for this event. The reference ID is a generic ID to be used in case a cross
     * reference is needed to compare some other entity. This is to be used with reference type.
     */
    public String getReferenceId() {
        return referenceId;
    }

    /**
     * Sets the reference ID for this event. The reference ID is a generic ID to be used in case a cross
     * reference is needed to compare some other entity. This is to be used with reference type.
     *
     * @param referenceId
     *  the reference ID to set for this event
     *
     * @return a reference to this event
     */
    public Event setReferenceId(String referenceId) {
        this.referenceId = referenceId;
        return this;
    }

    /**
     * @return the reference type for this event. The reference type classifies the generic id field in case
     * a cross reference is needed to compare some other entity. This is to be used with reference ID.
     */
    public ReferenceType getReferenceType() {
        return this.referenceType;
    }

    /**
     * Sets the reference type for this event. The reference type classifies the generic id field in case a
     * cross reference is needed to compare some other entity. This is to be used with reference ID.
     *
     * @param referenceType
     *  the reference type to set for this event
     *
     * @return a reference to this event
     */
    public Event setReferenceType(ReferenceType referenceType) {
        this.referenceType = referenceType;
        return this;
    }

    /**
     * @return the principal store for this event
     */
    @XmlTransient
    public String getPrincipalStore() {
        return this.principalStore;
    }

    /**
     * @return the entity ID for this event
     */
    public String getEntityId() {
        return this.entityId;
    }

    /**
     * Sets the entity ID for this event.
     *
     * @param entityId
     *  the entity ID to set for this event
     *
     * @return a reference to this event
     */
    public Event setEntityId(String entityId) {
        this.entityId = entityId;
        return this;
    }

    /**
     * @return the data for this event
     */
    public Map<String, Object> getEventData() {
        return this.eventData;
    }

    /**
     * Sets the event data for this event.
     *
     * @param eventData
     *  the event data to set for this event
     *
     * @return a reference to this event
     */
    public Event setEventData(Map<String, Object> eventData) {
        this.eventData = eventData;
        return this;
    }

    /**
     * @return the consumer UUID for this event
     */
    public String getConsumerUuid() {
        return consumerUuid;
    }

    /**
     * Sets the consumer UUID for this event.
     *
     * @param consumerUuid
     *  the consumer UUID to set for this event
     *
     * @return a reference to this event
     */
    public Event setConsumerUuid(String consumerUuid) {
        this.consumerUuid = consumerUuid;
        return this;
    }

    /**
     * @return the targetName
     */
    public String getTargetName() {
        return this.targetName;
    }

    /**
     * Sets the target name for this event.
     *
     * @param targetName
     *  the target name to set for this event
     *
     * @return a reference to this event
     */
    public Event setTargetName(String targetName) {
        this.targetName = targetName;
        return this;
    }

    /**
     * @return the messageText
     */
    public String getMessageText() {
        return this.messageText;
    }

    /**
     * Sets the message text for this event.
     *
     * @param messageText
     *  the message text for the event
     *
     * @return a reference to this event
     */
    public Event setMessageText(String messageText) {
        this.messageText = messageText;
        return this;
    }

    @Override
    public String toString() {
        String date = this.getTimestamp() != null ?
            String.format("%1$tF %1$tT%1$tz", this.getTimestamp()) :
            null;

        return String.format("Event [id: %s, target: %s, type: %s, time: %s, entity: %s]",
            this.getId(), this.getTarget(), this.getType(), date, this.getEntityId());
    }
}
