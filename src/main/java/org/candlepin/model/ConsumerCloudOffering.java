package org.candlepin.model;

import org.hibernate.annotations.GenericGenerator;

import java.io.Serializable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;


@Entity
@Table(name = ConsumerCloudOffering.DB_TABLE)
public class ConsumerCloudOffering extends AbstractHibernateObject<ConsumerCloudOffering> {

    public static final String DB_TABLE = "cp_consumer_cloud_offerings";

    @Id
    @GeneratedValue(generator = "system-uuid")
    @GenericGenerator(name = "system-uuid", strategy = "uuid")
    @NotNull
    private String id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(nullable = false, unique = true)
    @NotNull
    private Consumer consumer;

    @Column(name = "offering_id")
    @NotNull
    private String offeringId;

    @Column(name = "cloud_provider_short_name")
    @NotNull
    private String cloudProviderShortName;

    @Override
    public Serializable getId() {
        return this.id;
    }

    public Consumer getConsumer() {
        return consumer;
    }

    public ConsumerCloudOffering setConsumer(Consumer consumer) {
        this.consumer = consumer;
        return this;
    }

    public String getOfferingId() {
        return this.offeringId;
    }

    public ConsumerCloudOffering setOfferingId(String offeringId) {
        this.offeringId = offeringId;
        return this;
    }

    public String getCloudProviderShortName() {
        return this.cloudProviderShortName;
    }

    public ConsumerCloudOffering setCloudProviderShortName(String cloudProviderShortName) {
        if (cloudProviderShortName == null || cloudProviderShortName.isBlank()) {
            throw new IllegalArgumentException("cloudProviderShortName is null or empty");
        }

        // if (cloudProviderShortName.length() > CLOUD_PROVIDER_MAX_LENGTH) {
        //     throw new IllegalArgumentException("cloudProviderShortName exceeds the max length");
        // }

        this.cloudProviderShortName = cloudProviderShortName;
        return this;
    }
}
