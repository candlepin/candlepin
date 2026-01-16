/*
 * Copyright (c) 2009 - 2024 Red Hat, Inc.
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
package org.candlepin.service.model;

import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerCloudData;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.util.Date;
import java.util.List;


/**
 * An event where a cloud consumer has updated their last check-in time stamp.
 */
public final class CloudCheckInEvent implements AdapterEvent {

    private static final String SYSTEM_UUID_KEY = "systemUuid";
    private static final String CHECK_IN_KEY = "checkIn";
    private static final String PROVIDER_KEY = "cloudProviderId";
    private static final String ACCOUNT_KEY = "cloudAccountId";
    private static final String CLOUD_OFFERINGS_KEY = "cloudOfferingIds";

    private String consumerUuid;
    private Date checkIn;
    private String cloudProviderId;
    private String cloudAccountId;
    private List<String> cloudOfferingIds;

    private String eventBody;

    /**
     * Creates a new CloudCheckInEvent instance using the provided {@link ConsumerCloudData}.
     *
     * @param cloudData
     *  the consumer cloud data used to populate the event
     *
     * @param mapper
     *  the object mapper used to serialize the event body
     *
     * @throws IllegalArgumentException
     *  if the provided object mapper, consumer cloud data, or consumer in the consumer cloud data is null
     *
     * @throws IllegalStateException
     *  if the consumer cloud data has no cloud provider shortname, cloud account ID, consumer, or has a
     *  consumer that does not have a UUID, or last check in date
     *
     * @throws RuntimeException
     *  if unable to serialize the body of the event
     */
    public CloudCheckInEvent(ConsumerCloudData cloudData, ObjectMapper mapper) {
        if (cloudData == null) {
            throw new IllegalArgumentException("consumer cloud data is null");
        }

        if (mapper == null) {
            throw new IllegalArgumentException("object mapper is null");
        }

        Consumer consumer = cloudData.getConsumer();
        if (consumer == null) {
            throw new IllegalArgumentException("null consumer in consumer cloud data");
        }

        String consumerUuid = consumer.getUuid();
        if (consumerUuid == null || consumerUuid.isBlank()) {
            throw new IllegalStateException("consumer UUID is null or blank");
        }

        Date checkIn = consumer.getLastCheckin();
        if (checkIn == null) {
            throw new IllegalStateException("last check-in is null");
        }

        String cloudProviderId = cloudData.getCloudProviderShortName();
        if (cloudProviderId == null || cloudProviderId.isBlank()) {
            throw new IllegalStateException("cloud provider shortname is null or blank");
        }

        this.consumerUuid = consumerUuid;
        this.checkIn = checkIn;
        this.cloudProviderId = cloudProviderId;
        this.cloudAccountId = cloudData.getCloudAccountId();
        this.cloudOfferingIds = cloudData.getCloudOfferingIds();

        eventBody = getSerializedBody(mapper);
    }

    /**
     * @return the UUID of the consumer that has had an update to the last check-in date
     */
    public String getConsumerUuid() {
        return consumerUuid;
    }

    /**
     * @return the check-in date for this event
     */
    public Date getCheckIn() {
        return checkIn;
    }

    /**
     * @return the cloud provider's short name that the consumer belongs to
     */
    public String getCloudProviderId() {
        return cloudProviderId;
    }

    /**
     * @return the cloud account ID that the consumer belongs to
     */
    public String getCloudAccountId() {
        return cloudAccountId;
    }

    /**
     * @return the cloud offerings for the consumer that has had an update to the last check-in date
     */
    public List<String> getCloudOfferingIds() {
        return cloudOfferingIds;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getBody() {
        return eventBody;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public SerializationType getSerializationType() {
        return SerializationType.JSON;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }

        if (!(obj instanceof CloudCheckInEvent)) {
            return false;
        }

        CloudCheckInEvent that = (CloudCheckInEvent) obj;

        EqualsBuilder builder = new EqualsBuilder()
            .append(this.getConsumerUuid(), that.getConsumerUuid())
            .append(this.getCheckIn(), that.getCheckIn())
            .append(this.getCloudProviderId(), that.getCloudProviderId())
            .append(this.getCloudAccountId(), that.getCloudAccountId())
            .append(this.getCloudOfferingIds(), that.getCloudOfferingIds());

        return builder.isEquals();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        HashCodeBuilder builder = new HashCodeBuilder(37, 7)
            .append(this.getConsumerUuid())
            .append(this.getCheckIn())
            .append(this.getCloudProviderId())
            .append(this.getCloudAccountId())
            .append(this.getCloudOfferingIds());

        return builder.toHashCode();
    }

    private String getSerializedBody(ObjectMapper mapper) {
        ArrayNode arrayNode = mapper.createArrayNode();
        for (String offer : cloudOfferingIds) {
            arrayNode.add(offer);
        }

        ObjectNode rootNode = mapper.createObjectNode()
            .put(SYSTEM_UUID_KEY, consumerUuid)
            .put(CHECK_IN_KEY, checkIn.toString())
            .put(PROVIDER_KEY, cloudProviderId)
            .put(ACCOUNT_KEY, cloudAccountId)
            .putPOJO(CLOUD_OFFERINGS_KEY, arrayNode);

        return mapper.writeValueAsString(rootNode);
    }
}
