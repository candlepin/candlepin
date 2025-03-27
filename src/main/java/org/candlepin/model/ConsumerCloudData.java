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
package org.candlepin.model;

import org.candlepin.util.Util;

import org.hibernate.annotations.GenericGenerator;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.OneToOne;
import javax.persistence.Table;
import javax.validation.constraints.NotNull;



@Entity
@Table(name = ConsumerCloudData.DB_TABLE)
public class ConsumerCloudData extends AbstractHibernateObject<ConsumerCloudData> {

    /** Name of the table backing this object in the database */
    public static final String DB_TABLE = "cp_consumer_cloud_data";

    /** Max length for a value in the ID field */
    public static final int ID_MAX_LENGTH = 32;
    /** Max length for a value in the cloud account ID field */
    public static final int CLOUD_ACCOUNT_ID_MAX_LENGTH = 255;
    /** Max length for a value in the cloud instance ID field */
    public static final int CLOUD_INSTANCE_ID_MAX_LENGTH = 170;
    /** Max length for a value in the cloud offering ID field */
    public static final int CLOUD_OFFERING_ID_MAX_LENGTH = 255;
    /** Max length for a value in the cloud provider shortname field */
    public static final int CLOUD_PROVIDER_MAX_LENGTH = 15;

    @Id
    @GeneratedValue(generator = "system-uuid")
    @GenericGenerator(name = "system-uuid", strategy = "uuid")
    @NotNull
    private String id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(nullable = false, unique = true)
    @NotNull
    private Consumer consumer;

    @Column(name = "cloud_provider_short_name")
    @NotNull
    private String cloudProviderShortName;

    @Column(name = "cloud_account_id")
    private String cloudAccountId;

    @Column(name = "cloud_instance_id")
    private String cloudInstanceId;

    @Column(name = "cloud_offering_ids")
    private String cloudOfferingIds;

    /**
     * {@inheritDoc}
     */
    @Override
    public String getId() {
        return this.id;
    }

    /**
     * @return the cloud provider short name for this cloud consumer
     */
    public String getCloudProviderShortName() {
        return this.cloudProviderShortName;
    }

    /**
     * @param cloudProviderShortName
     *  the cloud provider short name to set
     *
     * @return a reference to this ConsumerCloudData instance
     */
    public ConsumerCloudData setCloudProviderShortName(String cloudProviderShortName) {
        if (cloudProviderShortName == null || cloudProviderShortName.isBlank()) {
            throw new IllegalArgumentException("cloudProviderShortName is null or empty");
        }

        if (cloudProviderShortName.length() > CLOUD_PROVIDER_MAX_LENGTH) {
            throw new IllegalArgumentException("cloudProviderShortName exceeds the max length");
        }

        this.cloudProviderShortName = cloudProviderShortName;
        return this;
    }

    /**
     * @return the cloud account ID for this anonymous cloud consumer
     */
    public String getCloudAccountId() {
        return this.cloudAccountId;
    }

    /**
     * @param cloudAccountId
     *  the cloud account ID to set
     *
     * @return a reference to this ConsumerCloudData instance
     */
    public ConsumerCloudData setCloudAccountId(String cloudAccountId) {
        if (cloudAccountId != null && cloudAccountId.length() > CLOUD_ACCOUNT_ID_MAX_LENGTH) {
            throw new IllegalArgumentException("cloudAccountId exceeds the max length");
        }

        this.cloudAccountId = cloudAccountId;
        return this;
    }

    /**
     * @return the cloud instance ID for this cloud consumer
     */
    public String getCloudInstanceId() {
        return this.cloudInstanceId;
    }

    /**
     * @param cloudInstanceId
     *  the cloud instance ID to set
     *
     * @return a reference to this ConsumerCloudData instance
     */
    public ConsumerCloudData setCloudInstanceId(String cloudInstanceId) {
        if (cloudInstanceId != null && cloudInstanceId.length() > CLOUD_INSTANCE_ID_MAX_LENGTH) {
            throw new IllegalArgumentException("cloudInstanceId exceeds the max length");
        }

        this.cloudInstanceId = cloudInstanceId;
        return this;
    }

    /**
     * Retrieves the list of cloud offering IDs associated with this cloud consumer.
     *
     * <p>This method parses the internal comma-separated string of cloud offering IDs
     * and returns them as a list of individual IDs. It handles redundant whitespace,
     * repeated commas, and ensures that the returned list does not contain empty strings.</p>
     *
     * <p>If there are no cloud offering IDs set (i.e., the internal string is {@code null} or blank),
     * this method returns an empty list.</p>
     *
     * <p><strong>Note:</strong> The returned list is a fixed-size list backed by an array.
     * Modifications to the list (such as adding or removing elements) are not supported and
     * will result in an {@code UnsupportedOperationException}. However, you can modify the elements
     * within the list if needed.</p>
     *
     * @return a list of cloud offering IDs; never {@code null}, possibly empty
     */
    public List<String> getCloudOfferingIds() {
        // Convert our internal comma-delimited string of offering IDs to a (likely) immutable list.
        return Util.toList(this.cloudOfferingIds);
    }

    /**
     * Adds the specified cloud offering IDs to the current list of offering IDs.
     *
     * <p>This method accepts a variable number of cloud offering ID strings, which are appended
     * to the existing list of cloud offering IDs in this {@code ConsumerCloudData} instance.
     * The IDs are stored internally as a comma-separated string.</p>
     *
     * @param cloudOfferingIds
     *  the cloud offering IDs to be added; must not be {@code null}; if there is empty list the attribute
     *  remains unchanged
     *
     * @throws IllegalArgumentException
     *  if the combined cloud offering IDs exceeds the maximum allowed length of 255 characters
     *
     * @return a reference to this {@code ConsumerCloudData} instance
     */
    public ConsumerCloudData addCloudOfferingIds(String... cloudOfferingIds) {
        return this.addCloudOfferingIds(cloudOfferingIds != null ? Arrays.asList(cloudOfferingIds) : null);
    }

    /**
     * Adds the specified collection of cloud offering IDs to the current list of offering IDs.
     *
     * <p>This method accepts a collection of cloud offering ID strings, which are appended
     * to the existing list of cloud offering IDs in this {@code ConsumerCloudData} instance.
     * The IDs are stored internally as a comma-separated string.</p>
     *
     * @param cloudOfferingIds
     *  the cloud offering IDs to be added; must not be {@code null}; if there is empty list the attribute
     *  remains unchanged
     *
     * @return a reference to this {@code ConsumerCloudData} instance
     *
     * @throws IllegalArgumentException
     *  if the combined cloud offering IDs exceeds the maximum allowed length of 255 characters
     */
    public ConsumerCloudData addCloudOfferingIds(Collection<String> cloudOfferingIds) {
        if (cloudOfferingIds == null || cloudOfferingIds.isEmpty()) {
            return this;
        }

        Stream<String> stream = Stream.concat(
            Stream.ofNullable(this.cloudOfferingIds),
            cloudOfferingIds.stream());

        String joinedCloudOfferingIds = stream.filter(Objects::nonNull)
            .filter(elem -> !elem.isBlank())
            .collect(Collectors.joining(","));

        if (joinedCloudOfferingIds.length() > CLOUD_OFFERING_ID_MAX_LENGTH) {
            throw new IllegalArgumentException(
                "Combined cloudOfferingIds exceed the max length of 255 characters");
        }

        this.cloudOfferingIds = !joinedCloudOfferingIds.isEmpty() ? joinedCloudOfferingIds : null;
        return this;
    }

    /**
     * Sets the cloud offering IDs to the specified IDs, replacing any existing IDs.
     *
     * <p>This method replaces the current list of cloud offering IDs with the provided IDs.
     * The IDs are stored internally as a comma-separated string.</p>
     *
     * @param cloudOfferingIds
     *  the cloud offering IDs to be set; must not be {@code null}; if there is empty list it will store
     *  {@code null}
     *
     * @throws IllegalArgumentException
     *  if the combined cloud offering IDs exceeds the maximum allowed length of 255 characters
     *
     * @return a reference to this {@code ConsumerCloudData} instance
     */
    public ConsumerCloudData setCloudOfferingIds(String... cloudOfferingIds) {
        return this.setCloudOfferingIds(cloudOfferingIds != null ? Arrays.asList(cloudOfferingIds) : null);
    }

    /**
     * Sets the cloud offering IDs to the specified collection of IDs, replacing any existing IDs.
     *
     * <p>This method replaces the current list of cloud offering IDs with the provided collection.
     * The IDs are stored internally as a comma-separated string.</p>
     *
     * @param cloudOfferingIds
     *  the cloud offering IDs to be set; must not be {@code null}; if there is empty list it will store
     *  {@code null}
     *
     * @throws IllegalArgumentException
     *  if the combined cloud offering IDs exceeds the maximum allowed length of 255 characters
     *
     * @return a reference to this {@code ConsumerCloudData} instance
     */
    public ConsumerCloudData setCloudOfferingIds(Collection<String> cloudOfferingIds) {
        if (cloudOfferingIds == null || cloudOfferingIds.isEmpty()) {
            this.cloudOfferingIds = null;
            return this;
        }

        String joinedCloudOfferingIds = cloudOfferingIds.stream()
            .filter(Objects::nonNull)
            .filter(elem -> !elem.isBlank())
            .collect(Collectors.joining(","));

        if (joinedCloudOfferingIds.length() > CLOUD_OFFERING_ID_MAX_LENGTH) {
            throw new IllegalArgumentException(
                "Combined cloudOfferingIds exceed the max length of 255 characters");
        }

        this.cloudOfferingIds = !joinedCloudOfferingIds.isEmpty() ? joinedCloudOfferingIds : null;
        return this;
    }

    /**
     * @return consumer who owns cloud data
     */
    public Consumer getConsumer() {
        return consumer;
    }

    /**
     * @param consumer
     *  consumer for cloud data
     *
     * @return a reference to this ConsumerCloudData instance
     */
    public ConsumerCloudData setConsumer(Consumer consumer) {
        this.consumer = consumer;
        return this;
    }

    public void updateFrom(ConsumerCloudData other) {
        if (other == null) {
            return;
        }

        if (other.getCloudProviderShortName() != null &&
            !other.getCloudProviderShortName().equals(this.getCloudProviderShortName())) {
            this.setCloudProviderShortName(other.getCloudProviderShortName());
        }

        if (other.getCloudAccountId() != null &&
            !other.getCloudAccountId().equals(this.getCloudAccountId())) {
            this.setCloudAccountId(other.getCloudAccountId());
        }

        if (other.getCloudInstanceId() != null &&
            !other.getCloudInstanceId().equals(this.getCloudInstanceId())) {
            this.setCloudInstanceId(other.getCloudInstanceId());
        }

        if (other.getCloudOfferingIds() != null && !other.getCloudOfferingIds().isEmpty() &&
            !other.getCloudOfferingIds().equals(this.getCloudOfferingIds())) {
            this.setCloudOfferingIds(other.getCloudOfferingIds());
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return String.format("ConsumerCloudData [consumerUuid: %s, cloudProviderShortName: %s, " +
                "cloudAccountId: %s, cloudInstanceId: %s, cloudOfferingIds: %s]",
            this.getConsumer().getUuid(), this.getCloudProviderShortName(), this.getCloudAccountId(),
            this.getCloudInstanceId(), this.getCloudOfferingIds());
    }

}
