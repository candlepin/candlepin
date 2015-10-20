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

import org.candlepin.gutterball.jackson.PrincipalJsonToStringConverter;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import org.hibernate.annotations.GenericGenerator;

import java.util.Date;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.PrePersist;
import javax.persistence.PreUpdate;
import javax.persistence.Table;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * A model representing and Event as recieved from Candlepin via AMQP. An event stores its
 * candlepin entities as TEXT strings in a single field so that they can be looked up (and
 * potentially re-processed) at a later date.
 */
@XmlRootElement
@XmlAccessorType(XmlAccessType.PROPERTY)
@Entity
@Table(name = "gb_event")
public class Event {

    /**
     * Represents the state of this event.
     *
     * Can be used to scan for events we received but did not have event handlers for (at
     * that time), or events that failed to process.
     */
    public enum Status {

        /**
         * Event was received and stored only. If an event remains in this state, it
         * indicates processing failed for some reason. Gutterball can periodically re-try
         * processing of this event, possibly after an application upgrade.
         */
        RECEIVED,

        /**
         * Event received, but we did not have an event handler for it, or the event
         * handler did not implement a method for the event's type. Events in this state
         * may be processed at a later date after gutterball is upgraded.
         */
        SKIPPED,

        /**
         * Event received and processed successfully by a handler.
         */
        PROCESSED,
    }

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private Status status;

    @Id
    @GeneratedValue(generator = "system-uuid")
    @GenericGenerator(name = "system-uuid", strategy = "uuid")
    @Column(length = 32)
    @NotNull
    private String id;

    @Column(nullable = false)
    private String messageId;

    @Column(nullable = false)
    @NotNull
    private String type;

    @Column(nullable = false)
    @NotNull
    private String target;

    @Column(nullable = true)
    @Size(max = 255)
    private String targetName;

    // String representation of the principal. We probably should not be
    // reconstructing any stored principal object.
    @Column(nullable = false)
    @Size(max = 255)
    @NotNull
    @JsonProperty("principal")
    @JsonDeserialize(converter = PrincipalJsonToStringConverter.class)
    private String principal;

    @Column(nullable = false)
    @NotNull
    private Date timestamp;

    @Column(nullable = true)
    @Size(max = 255)
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
    private String referenceType;

    /**
     * Old and New entity fields are stored as a JSON String so that we
     * can capture all the data in the event that Event handling fails.
     * If the event is successfully processed, these fields are nullified
     * so that we do not consume disk space.
     */
    @Column(columnDefinition = "mediumtext")
    private String oldEntity;

    @Column(columnDefinition = "mediumtext")
    private String newEntity;

    public Event() {
    }

    public Event(String messageId, String type, Event.Status status, String target, String targetName,
        String principal, String ownerId, String consumerId,
        String entityId, String oldEntity, String newEntity,
        String referenceId, String referenceType, Date timestamp) {
        this.messageId = messageId;
        this.type = type;
        this.status = status;
        this.target = target;
        this.targetName = targetName;

        this.principal = principal;
        this.ownerId = ownerId;

        this.entityId = entityId;
        this.oldEntity = oldEntity;
        this.newEntity = newEntity;
        this.consumerId = consumerId;
        this.referenceId = referenceId;
        this.referenceType = referenceType;

        this.timestamp = timestamp;
    }


    /**
     * @return The gutterball assigned ID.
     */
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getMessageId() {
        return messageId;
    }

    public void setMessageId(String messageId) {
        this.messageId = messageId;
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

    public String getTargetName() {
        return targetName;
    }

    public void setTargetName(String targetName) {
        this.targetName = targetName;
    }

    public String getPrincipal() {
        return principal;
    }

    public void setPrincipal(String principal) {
        this.principal = principal;
    }

    public Date getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Date timestamp) {
        this.timestamp = timestamp;
    }

    public String getEntityId() {
        return entityId;
    }

    public void setEntityId(String entityId) {
        this.entityId = entityId;
    }

    public String getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(String ownerId) {
        this.ownerId = ownerId;
    }

    public String getConsumerId() {
        return consumerId;
    }

    public void setConsumerId(String consumerId) {
        this.consumerId = consumerId;
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

    public String toString() {
        return "Event<id=" + id + ", timestamp=" + timestamp + ", target=" +
                target + ", type=" + type + ">";
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public void clearJsonFields() {
        if (!Event.Status.RECEIVED.equals(this.status)) {
            this.newEntity = null;
            this.oldEntity = null;
        }
    }

    // When an event is created or updated, only store the new/oldentity
    // JSON if the event is in the RECEIVED state.
    @PrePersist
    public void onCreate() {
        clearJsonFields();
    }

    @PreUpdate
    public void onUpdate() {
        clearJsonFields();
    }

}
