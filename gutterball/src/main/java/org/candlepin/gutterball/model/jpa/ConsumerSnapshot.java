package org.candlepin.gutterball.model.jpa;

import org.hibernate.annotations.Cascade;
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
@Table(name = "gb_consumer_snapshot")
public class ConsumerSnapshot {

    @Id
    @GeneratedValue(generator = "system-uuid")
    @GenericGenerator(name = "system-uuid", strategy = "uuid")
    @Column(length = 32)
    @NotNull
    private String id;

    @Column(nullable = false)
    @Size(max = 255)
    @NotNull
    private String uuid;

    @OneToOne(fetch = FetchType.LAZY)
    @ForeignKey(name = "fk_compliance_snapshot")
    @JoinColumn(nullable = false)
    @Index(name = "cp_compliance_snapshot_fk_idx")
    @NotNull
    private ComplianceSnapshot complianceSnapshot;

    @OneToOne(mappedBy = "consumerSnapshot", targetEntity = OwnerSnapshot.class)
    @Cascade({org.hibernate.annotations.CascadeType.ALL,
        org.hibernate.annotations.CascadeType.DELETE_ORPHAN})
    @NotNull
    private OwnerSnapshot ownerSnapshot;

    public ConsumerSnapshot() {
    }

    public ConsumerSnapshot(String uuid, OwnerSnapshot ownerSnapshot) {
        this.uuid = uuid;
        setOwnerSnapshot(ownerSnapshot);
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getUuid() {
        return uuid;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
    }

    public ComplianceSnapshot getComplianceSnapshot() {
        return complianceSnapshot;
    }

    public void setComplianceSnapshot(ComplianceSnapshot complianceSnapshot) {
        this.complianceSnapshot = complianceSnapshot;
    }

    public OwnerSnapshot getOwnerSnapshot() {
        return ownerSnapshot;
    }

    public void setOwnerSnapshot(OwnerSnapshot ownerSnapshot) {
        this.ownerSnapshot = ownerSnapshot;
        this.ownerSnapshot.setConsumerSnapshot(this);
    }

}
