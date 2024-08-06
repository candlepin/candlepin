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
    /** Max length for a value in the UUID field */
    public static final int UUID_MAX_LENGTH = 36;
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
     * @return the list of cloud offering IDs for this cloud consumer
     */
    public List<String> getCloudOfferingIds() {
        if (this.cloudOfferingIds == null) {
            return new ArrayList<>();
        }
        return Util.toList(this.cloudOfferingIds);
    }

    /**
     * @param cloudOfferingIds
     *  the cloud offering IDs to set
     *
     * @return a reference to this ConsumerCloudData instance
     */
    public ConsumerCloudData setCloudOfferingIds(String... cloudOfferingIds) {
        this.setCloudOfferingIds(Arrays.asList(cloudOfferingIds));
        return this;
    }

    /**
     * @param cloudOfferingIds
     *  collection of the cloud offering IDs to set
     *
     * @return a reference to this ConsumerCloudData instance
     */
    public ConsumerCloudData setCloudOfferingIds(Collection<String> cloudOfferingIds) {
        String joinedCloudOfferingIds = String.join(",", cloudOfferingIds);

        String combinedCloudOfferingIds;
        if (this.cloudOfferingIds == null || this.cloudOfferingIds.isEmpty()) {
            combinedCloudOfferingIds = joinedCloudOfferingIds;
        }
        else {
            combinedCloudOfferingIds = this.cloudOfferingIds + "," + joinedCloudOfferingIds;
        }

        if (combinedCloudOfferingIds.length() > CLOUD_OFFERING_ID_MAX_LENGTH) {
            throw new IllegalArgumentException("cloudOfferingId exceeds the max length");
        }

        this.cloudOfferingIds = combinedCloudOfferingIds;
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
