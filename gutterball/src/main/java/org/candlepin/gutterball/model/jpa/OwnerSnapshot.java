package org.candlepin.gutterball.model.jpa;

import org.hibernate.annotations.ForeignKey;
import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.Index;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.OneToOne;
import javax.persistence.Table;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement
@XmlAccessorType(XmlAccessType.PROPERTY)
@Entity
@Table(name = "gb_owner_snapshot")
public class OwnerSnapshot {

    @Id
    @GeneratedValue(generator = "system-uuid")
    @GenericGenerator(name = "system-uuid", strategy = "uuid")
    @Column(length = 32)
    @NotNull
    private String id;

    @OneToOne(fetch = FetchType.LAZY)
    @ForeignKey(name = "fk_consumer_snapshot")
    @JoinColumn(nullable = false)
    @Index(name = "cp_consumer_snapshot_fk_idx")
    @NotNull
    private ConsumerSnapshot consumerSnapshot;

    @Column(name = "account", nullable = false)
    @Size(max = 255)
    @NotNull
    private String key;

    @Column(nullable = false)
    @Size(max = 255)
    @NotNull
    private String displayName;

    public OwnerSnapshot() {

    }

    public OwnerSnapshot(String key, String displayName) {
        this.key = key;
        this.displayName = displayName;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public ConsumerSnapshot getConsumerSnapshot() {
        return consumerSnapshot;
    }

    public void setConsumerSnapshot(ConsumerSnapshot consumerSnapshot) {
        this.consumerSnapshot = consumerSnapshot;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

}
