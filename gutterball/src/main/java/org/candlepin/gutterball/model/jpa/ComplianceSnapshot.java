package org.candlepin.gutterball.model.jpa;

import org.hibernate.annotations.Cascade;
import org.hibernate.annotations.GenericGenerator;

import java.util.Date;
import java.util.HashSet;
import java.util.Set;

import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.OneToMany;
import javax.persistence.OneToOne;
import javax.persistence.Table;
import javax.validation.constraints.NotNull;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement
@XmlAccessorType(XmlAccessType.PROPERTY)
@Entity
@Table(name = "gb_compliance_snapshot")
public class ComplianceSnapshot {

    @Id
    @GeneratedValue(generator = "system-uuid")
    @GenericGenerator(name = "system-uuid", strategy = "uuid")
    @Column(length = 32)
    @NotNull
    private String id;

    @XmlElement
    @Column(nullable = false, unique = false)
    private Date date;

    @OneToOne(mappedBy = "complianceSnapshot", targetEntity = ConsumerSnapshot.class)
    @Cascade({org.hibernate.annotations.CascadeType.ALL,
        org.hibernate.annotations.CascadeType.DELETE_ORPHAN})
    @NotNull
    private ConsumerSnapshot consumerSnapshot;

    @OneToOne(mappedBy = "complianceSnapshot", targetEntity = ComplianceStatusSnapshot.class)
    @Cascade({org.hibernate.annotations.CascadeType.ALL,
        org.hibernate.annotations.CascadeType.DELETE_ORPHAN})
    @NotNull
    private ComplianceStatusSnapshot complianceStatusSnapshot;

    @OneToMany(cascade = CascadeType.ALL, mappedBy = "complianceSnapshot", fetch = FetchType.LAZY)
    private Set<EntitlementSnapshot> entitlementSnapshots;

    public ComplianceSnapshot() {
        this.entitlementSnapshots = new HashSet<EntitlementSnapshot>();
    }

    public ComplianceSnapshot(Date date, ConsumerSnapshot consumerSnapshot,
            ComplianceStatusSnapshot statusSnapshot) {
        this();
        this.date = date;
        setConsumerSnapshot(consumerSnapshot);
        setComplianceStatusSnapshot(statusSnapshot);
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Date getDate() {
        return date;
    }

    public void setDate(Date date) {
        this.date = date;
    }

    public ConsumerSnapshot getConsumerSnapshot() {
        return consumerSnapshot;
    }

    public void setConsumerSnapshot(ConsumerSnapshot consumerSnapshot) {
        this.consumerSnapshot = consumerSnapshot;
        this.consumerSnapshot.setComplianceSnapshot(this);
    }

    public ComplianceStatusSnapshot getComplianceStatusSnapshot() {
        return complianceStatusSnapshot;
    }

    public void setComplianceStatusSnapshot(
            ComplianceStatusSnapshot complianceStatusSnapshot) {
        this.complianceStatusSnapshot = complianceStatusSnapshot;
        this.complianceStatusSnapshot.setComplianceSnapshot(this);
    }

    public Set<EntitlementSnapshot> getEntitlementSnapshots() {
        return entitlementSnapshots;
    }

    public void setEntitlementSnapshots(
            Set<EntitlementSnapshot> entitlementSnapshots) {
        this.entitlementSnapshots = entitlementSnapshots;

        for (EntitlementSnapshot snapshot : this.entitlementSnapshots) {
            snapshot.setComplianceSnapshot(this);
        }
    }

    public void addEntitlementSnapshot(EntitlementSnapshot snapshot) {
        snapshot.setComplianceSnapshot(this);
        this.entitlementSnapshots.add(snapshot);
    }

}
