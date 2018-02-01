/**
 * Copyright (c) 2009 - 2017 Red Hat, Inc.
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
package org.candlepin.dto.api.v1;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.annotations.ApiModel;
import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.commons.lang.builder.HashCodeBuilder;
import org.candlepin.dto.CandlepinDTO;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;
import java.util.Date;

/**
 * A DTO representation of the Event entity
 */
@ApiModel(parent = CandlepinDTO.class, description = "DTO representing an event")
@XmlRootElement(namespace = "http://fedorahosted.org/candlepin/Event")
@XmlAccessorType(XmlAccessType.PROPERTY)
public class EventDTO extends CandlepinDTO<EventDTO> {
    public static final long serialVersionUID = 1L;


    /**
     * Internal DTO object for PrincipalData.
     */
    public static class PrincipalDataDTO {
        private final String type;
        private final String name;

        @JsonCreator
        public PrincipalDataDTO(
            @JsonProperty("type") String type,
            @JsonProperty("name") String name) {
            if (type == null || type.isEmpty()) {
                throw new IllegalArgumentException("type is null or empty");
            }

            if (name == null || name.isEmpty()) {
                throw new IllegalArgumentException("name is null or empty");
            }

            this.type = type;
            this.name = name;
        }

        public String getType() {
            return type;
        }

        public String getName() {
            return name;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this) {
                return true;
            }

            if (obj instanceof PrincipalDataDTO) {
                PrincipalDataDTO that = (PrincipalDataDTO) obj;

                EqualsBuilder builder = new EqualsBuilder()
                    .append(this.getType(), that.getType())
                    .append(this.getName(), that.getName());

                return builder.isEquals();
            }

            return false;
        }

        @Override
        public int hashCode() {
            HashCodeBuilder builder = new HashCodeBuilder(37, 7)
                .append(this.getType())
                .append(this.getName());

            return builder.toHashCode();
        }
    }

    private String id;
    private String targetName;
    private String principalStore;
    private PrincipalDataDTO principal;
    private Date timestamp;
    private String entityId;
    private String ownerId;
    private String consumerId;
    private String referenceId;
    private String eventData;
    private String messageText;
    private String type;
    private String target;
    private String referenceType;

    /**
     * Initializes a new EventDTO instance with null values.
     */
    public EventDTO() {
        // Intentionally left empty
    }

    /**
     * Initializes a new EventDTO instance which is a shallow copy of the provided
     * source entity.
     *
     * @param source
     *  The source entity to copy
     */
    public EventDTO(EventDTO source) {
        super(source);
    }

    /**
     * Retrieves the id field of this EventDTO object.
     *
     * @return the id field of this EventDTO object.
     */
    public String getId() {
        return id;
    }

    /**
     * Sets the id to set on this EventDTO object.
     *
     * @param id the id to set on this EventDTO object.
     *
     * @return a reference to this DTO object.
     */
    public EventDTO setId(String id) {
        this.id = id;
        return this;
    }

    /**
     * Retrieves the target name of this EventDTO object.
     *
     * @return the target name of this EventDTO object.
     */
    public String getTargetName() {
        return targetName;
    }

    /**
     * Sets the target name of this EventDTO object.
     *
     * @param targetName the target name of this EventDTO object.
     *
     * @return a reference to this DTO object.
     */
    public EventDTO setTargetName(String targetName) {
        this.targetName = targetName;
        return this;
    }

    /**
     * Retrieves the principal store of this EventDTO object.
     *
     * @return the principal store of this EventDTO object.
     */
    @JsonIgnore
    public String getPrincipalStore() {
        return principalStore;
    }

    /**
     * Sets the principal store of this EventDTO object.
     *
     * @param principalStore the principal store of this EventDTO object.
     *
     * @return a reference to this DTO object.
     */
    @JsonProperty
    public EventDTO setPrincipalStore(String principalStore) {
        this.principalStore = principalStore;
        return this;
    }

    /**
     * Retrieves the principal of this EventDTO object.
     *
     * @return the principal of this EventDTO object.
     */
    public PrincipalDataDTO getPrincipal() {
        return principal;
    }

    /**
     * Sets the principal of this EventDTO object.
     *
     * @param principal the principal of this EventDTO object.
     *
     * @return a reference to this DTO object.
     */
    public EventDTO setPrincipal(PrincipalDataDTO principal) {
        this.principal = principal;
        return this;
    }

    /**
     * Retrieves the timestamp of this EventDTO object.
     *
     * @return the timestamp of this EventDTO object.
     */
    public Date getTimestamp() {
        return timestamp;
    }

    /**
     * Sets the timestamp of this EventDTO object.
     *
     * @param timestamp the timestamp of this EventDTO object.
     *
     * @return a reference to this DTO object.
     */
    public EventDTO setTimestamp(Date timestamp) {
        this.timestamp = timestamp;
        return this;
    }

    /**
     * Retrieves the entity id of this EventDTO object.
     *
     * @return the entity id of this EventDTO object.
     */
    public String getEntityId() {
        return entityId;
    }

    /**
     * Sets the entity id of this EventDTO object.
     *
     * @param entityId the entity id of this EventDTO object.
     *
     * @return a reference to this DTO object.
     */
    public EventDTO setEntityId(String entityId) {
        this.entityId = entityId;
        return this;
    }

    /**
     * Retrieves the owner id of this EventDTO object.
     *
     * @return the owner id of this EventDTO object.
     */
    public String getOwnerId() {
        return ownerId;
    }

    /**
     * Sets the owner id of this EventDTO object.
     *
     * @param ownerId the owner id of this EventDTO object.
     *
     * @return a reference to this DTO object.
     */
    public EventDTO setOwnerId(String ownerId) {
        this.ownerId = ownerId;
        return this;
    }

    /**
     * Retrieves the consumer id of this EventDTO object.
     *
     * @return the consumer id of this EventDTO object.
     */
    public String getConsumerId() {
        return consumerId;
    }

    /**
     * Sets the consumer id of this EventDTO object.
     *
     * @param consumerId the consumer id of this EventDTO object.
     *
     * @return a reference to this DTO object.
     */
    public EventDTO setConsumerId(String consumerId) {
        this.consumerId = consumerId;
        return this;
    }

    /**
     * Retrieves the reference id of this EventDTO object.
     *
     * @return the reference id of this EventDTO object.
     */
    public String getReferenceId() {
        return referenceId;
    }

    /**
     * Sets the reference id of this EventDTO object.
     *
     * @param referenceId the reference id of this EventDTO object.
     *
     * @return a reference to this DTO object.
     */
    public EventDTO setReferenceId(String referenceId) {
        this.referenceId = referenceId;
        return this;
    }

    /**
     * Retrieves the event data of this EventDTO object.
     *
     * @return the event data of this EventDTO object.
     */
    @JsonIgnore
    public String getEventData() {
        return eventData;
    }

    /**
     * Sets the event data of this EventDTO object.
     *
     * @param eventData the event data of this EventDTO object.
     *
     * @return a reference to this DTO object.
     */
    @JsonProperty
    public EventDTO setEventData(String eventData) {
        this.eventData = eventData;
        return this;
    }

    /**
     * Retrieves the message text of this EventDTO object.
     *
     * @return the message text of this EventDTO object.
     */
    public String getMessageText() {
        return messageText;
    }

    /**
     * Sets the message text of this EventDTO object.
     *
     * @param messageText the message text of this EventDTO object.
     *
     * @return a reference to this DTO object.
     */
    public EventDTO setMessageText(String messageText) {
        this.messageText = messageText;
        return this;
    }

    /**
     * Retrieves the type of this EventDTO object.
     *
     * @return the type of this EventDTO object.
     */
    public String getType() {
        return type;
    }

    /**
     * Sets the type of this EventDTO object.
     *
     * @param type the type of this EventDTO object.
     *
     * @return a reference to this DTO object.
     */
    public EventDTO setType(String type) {
        this.type = type;
        return this;
    }

    /**
     * Retrieves the target of this EventDTO object.
     *
     * @return the target of this EventDTO object.
     */
    public String getTarget() {
        return target;
    }

    /**
     * Sets the target of this EventDTO object.
     *
     * @param target the target of this EventDTO object.
     *
     * @return a reference to this DTO object.
     */
    public EventDTO setTarget(String target) {
        this.target = target;
        return this;
    }

    /**
     * Retrieves the reference type of this EventDTO object.
     *
     * @return the reference type of this EventDTO object.
     */
    public String getReferenceType() {
        return referenceType;
    }

    /**
     * Sets the reference type of this EventDTO object.
     *
     * @param referenceType the reference type of this EventDTO object.
     *
     * @return a reference to this DTO object.
     */
    public EventDTO setReferenceType(String referenceType) {
        this.referenceType = referenceType;
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return String.format("EventDTO [id: %s, target: %s, type: %s, timestamp: %s, entity id: %s]",
            this.getId(), this.getTarget(), this.getType(), this.getTimestamp(), this.getEntityId());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }

        if (obj instanceof EventDTO) {
            EventDTO that = (EventDTO) obj;

            EqualsBuilder builder = new EqualsBuilder()
                .append(this.getId(), that.getId())
                .append(this.getTargetName(), that.getTargetName())
                .append(this.getConsumerId(), that.getConsumerId())
                .append(this.getEntityId(), that.getEntityId())
                .append(this.getMessageText(), that.getMessageText())
                .append(this.getOwnerId(), that.getOwnerId())
                .append(this.getPrincipalStore(), that.getPrincipalStore())
                .append(this.getPrincipal(), that.getPrincipal())
                .append(this.getReferenceId(), that.getReferenceId())
                .append(this.getTimestamp(), that.getTimestamp())
                .append(this.getType(), that.getType())
                .append(this.getTarget(), that.getTarget())
                .append(this.getReferenceType(), that.getReferenceType())
                .append(this.getEventData(), that.getEventData());

            return builder.isEquals();
        }

        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        HashCodeBuilder builder = new HashCodeBuilder(37, 7)
            .append(this.getId())
            .append(this.getTargetName())
            .append(this.getConsumerId())
            .append(this.getEntityId())
            .append(this.getMessageText())
            .append(this.getOwnerId())
            .append(this.getPrincipalStore())
            .append(this.getPrincipal())
            .append(this.getReferenceId())
            .append(this.getTimestamp())
            .append(this.getType())
            .append(this.getTarget())
            .append(this.getReferenceType())
            .append(this.getEventData());

        return builder.toHashCode();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public EventDTO clone() {
        EventDTO copy = super.clone();
        copy.timestamp = this.timestamp != null ? (Date) this.timestamp.clone() : null;

        return copy;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public EventDTO populate(EventDTO source) {
        super.populate(source);

        this.setId(source.getId())
            .setTargetName(source.getTargetName())
            .setConsumerId(source.getConsumerId())
            .setEntityId(source.getEntityId())
            .setMessageText(source.getMessageText())
            .setOwnerId(source.getOwnerId())
            .setPrincipalStore(source.getPrincipalStore())
            .setPrincipal(source.getPrincipal())
            .setReferenceId(source.getReferenceId())
            .setTimestamp(source.getTimestamp())
            .setType(source.getType())
            .setTarget(source.getTarget())
            .setReferenceType(source.getReferenceType())
            .setEventData(source.getEventData());

        return this;
    }
}
