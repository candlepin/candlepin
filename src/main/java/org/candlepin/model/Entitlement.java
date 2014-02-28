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
package org.candlepin.model;

import org.candlepin.jackson.HateoasInclude;

import com.fasterxml.jackson.annotation.JsonFilter;

import org.hibernate.annotations.ForeignKey;
import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.Index;

import java.util.Date;
import java.util.HashSet;
import java.util.Set;

import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;
import javax.persistence.Table;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;



/**
 * Entitlements are documents either signed XML or other certificate which
 * control what a particular Consumer can use. There are a number of types
 * of Entitlements:
 *
 *  1. Quantity Limited (physical & virtual)
 *  2. Version Limited
 *  3. Hardware Limited (i.e # of sockets, # of cores, etc)
 *  4. Functional Limited (i.e. Update, Management, Provisioning, etc)
 *  5. Site License
 *  6. Floating License
 *  7. Value-Based or "Metered" (i.e. per unit of time, per hardware
 *     consumption, etc)
 *  8. Draw-Down (i.e. 100 hours or training classes to be consumed over
 *     some period of time or limited number of support calls)
 */
@XmlRootElement
@XmlAccessorType(XmlAccessType.PROPERTY)
@Entity
@Table(name = "cp_entitlement")
@JsonFilter("EntitlementFilter")
public class Entitlement extends AbstractHibernateObject implements Linkable, Owned {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(generator = "system-uuid")
    @GenericGenerator(name = "system-uuid", strategy = "uuid2")
    @Column(length = 37)
    private String id;

    @ManyToOne
    @ForeignKey(name = "fk_entitlement_owner")
    @JoinColumn(nullable = false)
    @Index(name = "cp_entitlement_owner_fk_idx")
    private Owner owner;

    @ManyToOne
    @ForeignKey(name = "fk_entitlement_consumer")
    @JoinColumn(nullable = false)
    @Index(name = "cp_entitlement_consumer_fk_idx")
    private Consumer consumer;

    @ManyToOne
    @ForeignKey(name = "fk_entitlement_pool")
    @JoinColumn(nullable = true)
    @Index(name = "cp_entitlement_pool_fk_idx")
    private Pool pool;

    // Not positive this should be mapped here, not all entitlements will have
    // certificates.
    @OneToMany(mappedBy = "entitlement", cascade = CascadeType.ALL)
    private Set<EntitlementCertificate> certificates =
        new HashSet<EntitlementCertificate>();

    private Integer quantity;

    private boolean dirty = false;

    /**
     * default ctor
     */
    public Entitlement() {
    }

    /**
     * @return the id
     */
    @Override
    @HateoasInclude
    public String getId() {
        return id;
    }

    /**
     * @param id the id to set
     */
    public void setId(String id) {
        this.id = id;
    }

    /**
     * ctor
     * @param poolIn pool associated with the entitlement
     * @param consumerIn consumer associated with the entitlement
     */
    public Entitlement(Pool poolIn, Consumer consumerIn, Integer quantityIn) {
        pool = poolIn;
        owner = consumerIn.getOwner();
        consumer = consumerIn;
        quantity = quantityIn == null || quantityIn.intValue() < 1 ?
            1 : quantityIn;
    }

    /**
     * @return the owner
     */
    @XmlTransient
    public Owner getOwner() {
        return owner;
    }

    /**
     * @param ownerIn the owner to set
     */
    public void setOwner(Owner ownerIn) {
        this.owner = ownerIn;
    }

    /**
     * @return Returns the product.
     */
    @XmlTransient
    public String getProductId() {
        if (this.pool != null) {
            return this.pool.getProductId();
        }
        return null;
    }

    /**
     * @return Returns the pool.
     */
    public Pool getPool() {
        return pool;
    }

    /**
     * @param poolIn The pool to set.
     */
    public void setPool(Pool poolIn) {
        pool = poolIn;
    }

    /**
     * @return Returns the startDate.
     */
    public Date getStartDate() {
        if (pool == null) {
            return null;
        }
        return pool.getStartDate();
    }

    public void setStartDate(Date date) {
        // Only for serialization, start date lives on pool now.
    }

    /**
     * @return Returns the endDate.
     */
    public Date getEndDate() {
        if (pool == null) {
            return null;
        }
        return pool.getEndDate();
    }

    public void setEndDate(Date date) {
        // Only for serialization, end date lives on pool now.
    }

    /**
     * @return return the associated Consumer
     */
    public Consumer getConsumer() {
        return consumer;
    }

    /**
     * associates the given consumer with this entitlement.
     * @param consumer consumer to associate.
     */
    public void setConsumer(Consumer consumer) {
        this.consumer = consumer;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }

    public Set<EntitlementCertificate> getCertificates() {
        return certificates;
    }

    public void setCertificates(Set<EntitlementCertificate> certificates) {
        this.certificates = certificates;
    }

    public void addCertificate(EntitlementCertificate certificate) {
        certificate.setEntitlement(this);
        certificates.add(certificate);
    }

    public String toString() {
        return "Entitlement[id=" + getId() + ", product=" + getProductId() +
            ", consumer= " + (consumer == null ? "null" : consumer.getUuid()) + "]";
    }

    @HateoasInclude
    public String getHref() {
        return "/entitlements/" + getId();
    }

    @Override
    public void setHref(String href) {
        /*
         * No-op, here to aid with updating objects which have nested objects that were
         * originally sent down to the client in HATEOAS form.
         */
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }

        if (!(obj instanceof Entitlement)) {
            return false;
        }

        Entitlement e = (Entitlement) obj;

        return (id == null ? e.id == null : id.equals(e.getId()));
    }

    @Override
    public int hashCode() {
        int result = 17;
        if (null != id) {
            result = 37 * result + id.hashCode();
        }
        return result;
    }

    @XmlTransient
    public boolean getDirty() {
        return dirty;
    }

    public void setDirty(boolean dirty) {
        this.dirty = dirty;
    }

    @XmlTransient
    public boolean isValidOnDate(Date d) {
        return d.after(this.getStartDate()) && d.before(this.getEndDate());
    }

    @XmlTransient
    public boolean isValid() {
        return this.isValidOnDate(new Date());
    }
}
