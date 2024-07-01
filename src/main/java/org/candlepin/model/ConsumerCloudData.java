package org.candlepin.model;

import java.io.Serializable;
import java.util.Collection;
import java.util.List;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

import org.candlepin.util.Util;

@Entity
@Table(name = ConsumerCloudData.DB_TABLE)
public class ConsumerCloudData extends AbstractHibernateObject<ConsumerCloudData> {
    
    public static final String DB_TABLE = "cp_consumer_cloud_data";

    @Id
    @Column(name = "consumer_id", length = 32)
    @NotNull
    private String consumerId;

    @Column(name = "cloud_provider_short_name")
    @Size(max = 15)
    @NotNull
    private String cloudProviderShortName;

    @Column(name = "cloud_account_id")
    @Size(max = 255)
    @NotNull
    private String cloudAccountId;

    @Column(name = "cloud_instance_id")
    @Size(max = 170)
    @NotNull
    private String cloudInstanceId;

    @Column(name = "cloud_offering_ids")
    @Size(max = 255)
    @NotNull
    private String cloudOfferingIds;

    public String getConsumerId() {
        return consumerId;
    }

    public ConsumerCloudData setConsumerId(String consumerId) {
        this.consumerId = consumerId;
        return this;
    }

    public String getCloudProviderShortName() {
        return cloudProviderShortName;
    }

    public ConsumerCloudData setCloudProviderShortName(String cloudProviderShortName) {
        this.cloudProviderShortName = cloudProviderShortName;
        return this;
    }

    public String getCloudAccountId() {
        return cloudAccountId;
    }

    public ConsumerCloudData setCloudAccountId(String cloudAccountId) {
        this.cloudAccountId = cloudAccountId;
        return this;
    }

    public String getCloudInstanceId() {
        return cloudInstanceId;
    }

    public ConsumerCloudData setCloudInstanceId(String cloudInstanceId) {
        this.cloudInstanceId = cloudInstanceId;
        return this;
    }

    public List<String> getCloudOfferingIds() {
        return Util.toList(cloudOfferingIds);
    }

    public ConsumerCloudData setCloudOfferingIds(Collection<String> offeringIds) {
        if (offeringIds == null || offeringIds.isEmpty()) {
            return this;
        }

        String combined = "";
        for (String id : offeringIds) {
            combined = combined + "," + id ;
        }

        combined = combined.replaceFirst(",", "");

        this.cloudOfferingIds = combined;
        return this;
    }

    @Override
    public Serializable getId() {
        return consumerId;
    }

}
