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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

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
     * @param id
     *  the database ID
     *
     * @return a reference to this ConsumerCloudData instance
     */
    public ConsumerCloudData setId(String id) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("ID is null or blank");
        }

        if (id.length() > ID_MAX_LENGTH) {
            throw new IllegalArgumentException("ID exceeds the max length");
        }

        this.id = id;
        return this;
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
        if (cloudProviderShortName == null) {
            throw new IllegalArgumentException("cloudProviderShortName is null");
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
        if (this.cloudOfferingIds == null) {
            return new ArrayList<>();
        }
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
     *         the cloud offering IDs to be added; must not be {@code null} or contain {@code null} elements;
     *         if there is empty list the attribute remains unchanged
     *
     * @return a reference to this {@code ConsumerCloudData} instance
     *
     * @throws IllegalArgumentException
     *         if {@code cloudOfferingIds} is {@code null} or contains {@code null} elements,
     *         or if the combined cloud offering IDs exceed the maximum allowed length of 255 characters
     */
    public ConsumerCloudData addCloudOfferingIds(String... cloudOfferingIds) {
        if (cloudOfferingIds == null) {
            throw new IllegalArgumentException("cloudOfferingIds is null");
        }

        this.addCloudOfferingIds(Arrays.asList(cloudOfferingIds));
        return this;
    }

    /**
     * Adds the specified collection of cloud offering IDs to the current list of offering IDs.
     *
     * <p>This method accepts a collection of cloud offering ID strings, which are appended
     * to the existing list of cloud offering IDs in this {@code ConsumerCloudData} instance.
     * The IDs are stored internally as a comma-separated string.</p>
     *
     * @param cloudOfferingIds
     *         the cloud offering IDs to be added; must not be {@code null},
     *         or contain {@code null} elements; if there is empty list the attribute remains unchanged
     *
     * @return a reference to this {@code ConsumerCloudData} instance
     *
     * @throws IllegalArgumentException
     *         if {@code cloudOfferingIds} is {@code null}, contains {@code null} elements,
     *         or if the combined cloud offering IDs exceed the maximum allowed length of 255 characters
     */
    public ConsumerCloudData addCloudOfferingIds(Collection<String> cloudOfferingIds) {
        if (cloudOfferingIds == null) {
            throw new IllegalArgumentException("cloudOfferingIds is null or empty");
        }

        if (cloudOfferingIds.isEmpty()) {
            return this;
        }

        for (String cloudOfferingId : cloudOfferingIds) {
            if (cloudOfferingId == null) {
                throw new IllegalArgumentException("cloudOfferingIds contains null element");
            }
        }

        String joinedCloudOfferingIds = String.join(",", cloudOfferingIds);

        String combinedCloudOfferingIds;
        if (this.cloudOfferingIds == null || this.cloudOfferingIds.isEmpty()) {
            combinedCloudOfferingIds = joinedCloudOfferingIds;
        }
        else {
            combinedCloudOfferingIds = this.cloudOfferingIds + "," + joinedCloudOfferingIds;
        }

        if (combinedCloudOfferingIds.length() > CLOUD_OFFERING_ID_MAX_LENGTH) {
            throw new IllegalArgumentException(
                "Combined cloudOfferingIds exceed the max length of 255 characters");
        }

        this.cloudOfferingIds = combinedCloudOfferingIds;
        return this;
    }

    /**
     * Sets the cloud offering IDs to the specified IDs, replacing any existing IDs.
     *
     * <p>This method replaces the current list of cloud offering IDs with the provided IDs.
     * The IDs are stored internally as a comma-separated string.</p>
     *
     * @param cloudOfferingIds
     *         the cloud offering IDs to be set; must not be {@code null} or contain {@code null} elements;
     *         if there is empty list it will store {@code null}
     *
     * @return a reference to this {@code ConsumerCloudData} instance
     *
     * @throws IllegalArgumentException
     *         if {@code cloudOfferingIds} is {@code null} or contains {@code null} elements,
     *         or if the combined cloud offering IDs exceed the maximum allowed length of 255 characters
     */
    public ConsumerCloudData setCloudOfferingIds(String... cloudOfferingIds) {
        if (cloudOfferingIds == null) {
            throw new IllegalArgumentException("cloudOfferingIds is null");
        }

        this.setCloudOfferingIds(Arrays.asList(cloudOfferingIds));
        return this;
    }

    /**
     * Sets the cloud offering IDs to the specified collection of IDs, replacing any existing IDs.
     *
     * <p>This method replaces the current list of cloud offering IDs with the provided collection.
     * The IDs are stored internally as a comma-separated string.</p>
     *
     * @param cloudOfferingIds
     *         the cloud offering IDs to be set; must not be {@code null},
     *         or contain {@code null} elements; if there is empty list it will store {@code null}
     *
     * @return a reference to this {@code ConsumerCloudData} instance
     *
     * @throws IllegalArgumentException
     *         if {@code cloudOfferingIds} is {@code null}, contains {@code null} elements,
     *         or if the combined cloud offering IDs exceed the maximum allowed length of 255 characters
     */
    public ConsumerCloudData setCloudOfferingIds(Collection<String> cloudOfferingIds) {
        if (cloudOfferingIds == null) {
            throw new IllegalArgumentException("cloudOfferingIds is null");
        }

        if (cloudOfferingIds.isEmpty()) {
            this.cloudOfferingIds = null;
            return this;
        }

        for (String cloudOfferingId : cloudOfferingIds) {
            if (cloudOfferingId == null) {
                throw new IllegalArgumentException("cloudOfferingIds contains null element");
            }
        }

        String joinedCloudOfferingIds = String.join(",", cloudOfferingIds);

        if (joinedCloudOfferingIds.length() > CLOUD_OFFERING_ID_MAX_LENGTH) {
            throw new IllegalArgumentException(
                "Combined cloudOfferingIds exceed the max length of 255 characters");
        }

        this.cloudOfferingIds = joinedCloudOfferingIds;
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

    @Override
    public String toString() {
        return String.format(
            "Consumer Cloud Data [id: %s, cloudAccountId: %s, cloudProviderShortName: %s]",
            this.getId(), this.getCloudAccountId(), this.getCloudProviderShortName());
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }

        if (!(obj instanceof ConsumerCloudData)) {
            return false;
        }

        return Objects.equals(this.getId(), ((ConsumerCloudData) obj).getId());
    }

    @Override
    public int hashCode() {
        return this.getId() != null ? this.getId().hashCode() : 0;
    }

}
