/**
 * Copyright (c) 2009 - 2012 Red Hat, Inc.
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
package org.candlepin.model.activationkeys;

import org.candlepin.model.AbstractHibernateObject;
import org.candlepin.model.OrgProduct;

import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.Index;

import java.io.Serializable;
import java.util.Date;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.PrePersist;
import javax.persistence.PreUpdate;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;
import javax.xml.bind.annotation.XmlType;

import com.fasterxml.jackson.annotation.JsonFilter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;



/**
 * SubscriptionToken
 */
@Entity
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonFilter("DefaultFilter")
@XmlAccessorType(XmlAccessType.PROPERTY)
@XmlRootElement
@XmlType(name = "CandlepinObject")
@Table(name = "cp_org_activationkey_product")
public class OrgActivationKeyProduct implements Persisted, Serializable {
    public static final String DEFAULT_SORT_FIELD = "created";

    @ManyToOne
    @JoinColumn(name="id", nullable = false)
    @NotNull
    private ActivationKey key;

    @ManyToOne
    @JoinColumn(name="id", nullable = false)
    @NotNull
    private OrgProduct product;

    private Date created;
    private Date updated;

    public OrgActivationKeyProduct() {
    }

    public OrgActivationKeyProduct(ActivationKey key, OrgProduct product) {
        this.key = key;
        this.product = product;
    }

    /**
     * @return the key
     */
    @XmlTransient
    public ActivationKey getKey() {
        return key;
    }

    /**
     * @param key the key to set
     */
    public void setKeyId(ActivationKey key) {
        this.key = key;
    }

    /**
     * @return the Product
     */
    public OrgProduct getProduct() {
        return product;
    }

    /**
     * @param product the Product Id to set
     */
    public void setProduct(OrgProduct product) {
        this.product = product;
    }

    @Override
    public String toString() {
        return "Activation key: " + this.getKey().getName() + ", Product: " +
                this.getProduct().getName();
    }

    @PrePersist
    protected void onCreate() {
        Date now = new Date();

        setCreated(now);
        setUpdated(now);
    }

    @PreUpdate
    protected void onUpdate() {
        setUpdated(new Date());
    }

    @XmlElement
    @Column(nullable = false, unique = false)
    public Date getCreated() {
        return created;
    }

    public void setCreated(Date created) {
        this.created = created;
    }

    @XmlElement
    @Column(nullable = false, unique = false)
    public Date getUpdated() {
        return updated;
    }

    public void setUpdated(Date updated) {
        this.updated = updated;
    }
}
