/*
 * Copyright (c) 2009 - 2023 Red Hat, Inc.
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

import java.util.Collection;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.OneToOne;
import javax.persistence.Table;
import javax.validation.constraints.NotNull;



/**
 * An anonymous cloud consumer is an entity that does not have an owner, but is given temporary
 * content access until it is claimed by an owner.
 */
@Entity
@Table(name = AnonymousCloudConsumer.DB_TABLE)
public class AnonymousCloudConsumer extends AbstractHibernateObject<AnonymousCloudConsumer> {

    /** Name of the table backing this object in the database */
    public static final String DB_TABLE = "cp_anonymous_cloud_consumers";

    /** Max length for a value in the ID field */
    public static final int ID_MAX_LENGTH = 32;
    /** Max length for a value in the UUID field */
    public static final int UUID_MAX_LENGTH = 36;
    /** Max length for a value in the cloud account ID field */
    public static final int CLOUD_ACCOUNT_ID_MAX_LENGTH = 255;
    /** Max length for a value in the cloud instance ID field */
    public static final int CLOUD_INSTANCE_ID_MAX_LENGTH = 170;
    /** Max length for a value in the cloud offering ID field */
    public static final int CLOUD_OFFERING_ID_MAX_LENGTH = 170;
    /** Max length for a value in the cloud provider short name field */
    public static final int CLOUD_PROVIDER_SHORT_NAME_MAX_LENGTH = 15;

    @Id
    @GeneratedValue(generator = "system-uuid")
    @GenericGenerator(name = "system-uuid", strategy = "uuid")
    @NotNull
    private String id;

    @Column(unique = true)
    @NotNull
    private String uuid;

    @Column(name = "cloud_account_id")
    @NotNull
    private String cloudAccountId;

    @Column(name = "cloud_instance_id")
    @NotNull
    private String cloudInstanceId;

    @Column(name = "cloud_offering_id")
    @NotNull
    private String cloudOfferingId;

    @Column(name = "cloud_provider_short_name")
    @NotNull
    private String cloudProviderShortName;

    @Column(name = "product_ids")
    @NotNull
    private String productIds;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cont_acc_cert_id")
    private AnonymousContentAccessCertificate contentAccessCert;

    public AnonymousCloudConsumer() {
        this.uuid = Util.generateUUID();
    }

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
     * @return a reference to this AnonymousCloudConsumer instance
     */
    public AnonymousCloudConsumer setId(String id) {
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
     * @return the UUID for this AnonymousCloudConsumer
     */
    public String getUuid() {
        return this.uuid;
    }

    /**
     * @param uuid
     *  the UUID to set
     *
     * @return a reference to this AnonymousCloudConsumer instance
     */
    public AnonymousCloudConsumer setUuid(String uuid) {
        if (uuid == null || uuid.isBlank()) {
            throw new IllegalArgumentException("UUID is null or blank");
        }

        if (uuid.length() > UUID_MAX_LENGTH) {
            throw new IllegalArgumentException("UUID exceeds the max length");
        }

        this.uuid = uuid;
        return this;
    }

    /**
     * @return the cloud instance ID for this anonymous cloud consumer
     */
    public String getCloudInstanceId() {
        return this.cloudInstanceId;
    }

    /**
     * @param cloudInstanceId
     *  the cloud instance ID to set
     *
     * @return a reference to this AnonymousCloudConsumer instance
     */
    public AnonymousCloudConsumer setCloudInstanceId(String cloudInstanceId) {
        if (cloudInstanceId == null) {
            throw new IllegalArgumentException("cloudInstanceId is null");
        }

        if (cloudInstanceId.length() > CLOUD_INSTANCE_ID_MAX_LENGTH) {
            throw new IllegalArgumentException("cloudInstanceId exceeds the max length");
        }

        this.cloudInstanceId = cloudInstanceId;
        return this;
    }

    /**
     * @return the cloud offering ID for this anonymous cloud consumer
     */
    public String getCloudOfferingId() {
        return this.cloudOfferingId;
    }

    /**
     * @param cloudOfferingId
     *  the cloud offering ID to set
     *
     * @return a reference to this AnonymousCloudConsumer instance
     */
    public AnonymousCloudConsumer setCloudOfferingId(String cloudOfferingId) {
        if (cloudOfferingId == null) {
            throw new IllegalArgumentException("cloudOfferingId is null");
        }

        if (cloudOfferingId.length() > CLOUD_OFFERING_ID_MAX_LENGTH) {
            throw new IllegalArgumentException("cloudOfferingId exceeds the max length");
        }

        this.cloudOfferingId = cloudOfferingId;
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
     * @return a reference to this AnonymousCloudConsumer instance
     */
    public AnonymousCloudConsumer setCloudAccountId(String cloudAccountId) {
        if (cloudAccountId == null) {
            throw new IllegalArgumentException("cloudAccountId is null");
        }

        if (cloudAccountId.length() > CLOUD_ACCOUNT_ID_MAX_LENGTH) {
            throw new IllegalArgumentException("cloudAccountId exceeds the max length");
        }

        this.cloudAccountId = cloudAccountId;
        return this;
    }

    /**
     * @return the cloud provider short name for this anonymous cloud consumer
     */
    public String getCloudProviderShortName() {
        return this.cloudProviderShortName;
    }

    /**
     * @param cloudProviderShortName
     *  the cloud provider short name to set
     *
     * @return a reference to this AnonymousCloudConsumer instance
     */
    public AnonymousCloudConsumer setCloudProviderShortName(String cloudProviderShortName) {
        if (cloudProviderShortName == null) {
            throw new IllegalArgumentException("cloudProviderShortName is null");
        }

        if (cloudProviderShortName.length() > CLOUD_PROVIDER_SHORT_NAME_MAX_LENGTH) {
            throw new IllegalArgumentException("cloudProviderShortName exceeds the max length");
        }

        this.cloudProviderShortName = cloudProviderShortName;
        return this;
    }

    /**
     * @return the product IDs for this anonymous cloud consumer
     */
    public Set<String> getProductIds() {
        Set<String> productIdsSet = Set.copyOf(Util.toList(this.productIds));

        productIdsSet = productIdsSet.stream()
            .filter(str -> str != null && !str.isBlank())
            .collect(Collectors.toSet());

        return productIdsSet;
    }

    /**
     * @param productIds
     *  the product IDs to set for this anonymous cloud consumer
     *
     * @return a reference to this AnonymousCloudConsumer instance
     */
    public AnonymousCloudConsumer setProductIds(Collection<String> productIds) {
        if (productIds == null) {
            throw new IllegalArgumentException("productIds is null");
        }

        if (productIds.isEmpty()) {
            throw new IllegalArgumentException("productIds is empty");
        }

        // filter null and blank strings and collect as set to remove duplicates
        Set<String> productSet = productIds.stream()
            .filter(str -> str != null && !str.isBlank())
            .collect(Collectors.toSet());

        if (productSet.isEmpty()) {
            throw new IllegalArgumentException("productIds is empty after removing null and blank entries");
        }

        this.productIds = String.join(",", productSet);
        return this;
    }

    /**
     * @return the anonymous content access certificate for this anonymous cloud consumer
     */
    public AnonymousContentAccessCertificate getContentAccessCert() {
        return contentAccessCert;
    }

    /**
     * @param contentAccessCert
     *  the anonymous content access certificate to set for this anonymous cloud consumer
     *
     * @return a reference to this AnonymousCloudConsumer instance
     */
    public AnonymousCloudConsumer setContentAccessCert(AnonymousContentAccessCertificate contentAccessCert) {
        this.contentAccessCert = contentAccessCert;
        return this;
    }

    @Override
    public String toString() {
        return String.format(
            "Anonymous Consumer [id: %s, uuid: %s, cloudAccountId: %s, cloudProviderShortName: %s]",
            this.getId(), this.getUuid(), this.getCloudAccountId(), this.getCloudProviderShortName());
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }

        if (!(obj instanceof AnonymousCloudConsumer)) {
            return false;
        }

        return Objects.equals(this.getUuid(), ((AnonymousCloudConsumer) obj).getUuid());
    }

    @Override
    public int hashCode() {
        return this.getUuid() != null ? this.getUuid().hashCode() : 0;
    }

}
