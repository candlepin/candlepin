package org.candlepin.gutterball.model.jpa;

import org.hibernate.annotations.GenericGenerator;

import java.util.Date;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * Consumer master record to store created/deleted/owner info
 * to narrow our search space
 *
 * We should only add info here that cannot be modified
 */
@XmlRootElement
@XmlAccessorType(XmlAccessType.PROPERTY)
@Entity
@Table(name = "gb_consumer_state")
public class ConsumerState {

    @Id
    @GeneratedValue(generator = "system-uuid")
    @GenericGenerator(name = "system-uuid", strategy = "uuid")
    @Column(length = 32)
    @NotNull
    private String id;

    @Column(nullable = false, unique = true)
    @Size(max = 255)
    @NotNull
    private String uuid;

    @Column(nullable = false)
    @Size(max = 255)
    @NotNull
    private String ownerKey;

    @XmlElement
    @Column(nullable = false, unique = false)
    private Date created;

    @XmlElement
    @Column(nullable = true, unique = false)
    private Date deleted;

    public ConsumerState() {

    }

    public ConsumerState(String uuid, String ownerKey, Date created) {
        this.uuid = uuid;
        this.ownerKey = ownerKey;
        this.created = created;
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

    public String getOwnerKey() {
        return ownerKey;
    }

    public void setOwnerKey(String ownerKey) {
        this.ownerKey = ownerKey;
    }

    public Date getCreated() {
        return created;
    }

    public void setCreated(Date created) {
        this.created = created;
    }

    public Date getDeleted() {
        return deleted;
    }

    public void setDeleted(Date deleted) {
        this.deleted = deleted;
    }

}
