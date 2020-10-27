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

import org.candlepin.common.jackson.HateoasInclude;

import com.fasterxml.jackson.annotation.JsonFilter;

import org.hibernate.annotations.BatchSize;

import java.util.Date;
import java.util.HashSet;
import java.util.Set;

import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;
import javax.persistence.Table;
import javax.persistence.Transient;
import javax.validation.constraints.NotNull;
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
@Table(name = Entitlement.DB_TABLE)
@JsonFilter("EntitlementFilter")
public class Entitlement extends AbstractHibernateObject<Entitlement>
    implements Linkable, Owned, Named, ConsumerProperty, Comparable<Entitlement>, Eventful {

    /** Name of the table backing this object in the database */
    public static final String DB_TABLE = "cp_entitlement";

    private static final long serialVersionUID = 1L;

    @Id
    @Column(length = 32)
    @NotNull
    private String id;

    @ManyToOne
    @JoinColumn(nullable = false)
    @NotNull
    private Owner owner;

    @ManyToOne
    @JoinColumn(nullable = false)
    @NotNull
    private Consumer consumer;

    @ManyToOne
    @JoinColumn(nullable = true)
    @NotNull
    private Pool pool;

    // Not positive this should be mapped here, not all entitlements will have
    // certificates.
    @OneToMany(mappedBy = "entitlement", cascade = CascadeType.ALL)
    @BatchSize(size = 100)
    private Set<EntitlementCertificate> certificates = new HashSet<>();


    private Integer quantity;

    private boolean dirty = false;

    // If the entitlement is created before it becomes active, we need to
    // rerun compliance once we hit the active date range.
    private boolean updatedOnStart = false;

    private Date endDateOverride;

    // We don't want to send entitlement delete events when the pool is
    //  entirely deleted
    @Transient
    private boolean deletedFromPool;

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
     * @param quantityIn entitlement quantity
     */
    public Entitlement(Pool poolIn, Consumer consumerIn, Owner owner, Integer quantityIn) {
        this(consumerIn, owner, quantityIn);
        pool = poolIn;
        updatedOnStart = poolIn.getStartDate().after(new Date());
    }

    /**
     * @param consumerIn consumer associated with the entitlement
     * @param ownerIn owner associated with the entitlement
     * @param quantityIn entitlement quantity
     */
    public Entitlement(Consumer consumerIn, Owner ownerIn, Integer quantityIn) {
        owner = ownerIn;
        consumer = consumerIn;
        quantity = quantityIn == null || quantityIn.intValue() < 1 ?
            1 : quantityIn;
        deletedFromPool = false;
    }

    /**
     * @return the owner
     */
    @XmlTransient
    public Owner getOwner() {
        return owner;
    }

    /**
     * @return the owner Id of this Entitlement.
     */
    @Override
    @XmlTransient
    public String getOwnerId() {
        return (owner == null) ? null : owner.getId();
    }

    /**
     * @param ownerIn the owner to set
     */
    public void setOwner(Owner ownerIn) {
        this.owner = ownerIn;
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
     * @return Returns the endDate. If an override is specified for this entitlement,
     * we return this value. If not we'll use the end date of the pool.
     */
    public Date getEndDate() {
        if (endDateOverride != null) {
            return endDateOverride;
        }

        if (pool == null) {
            return null;
        }

        return pool.getEndDate();
    }

    public void setEndDate(Date date) {
        // Only for serialization, end date lives on pool now.
    }


    /**
     * An optional end date override for this entitlement.
     *
     * Typically this is set to null, and the pool's end date is used. In some cases
     * we need to control the expiry of an entitlement separate from the pool.
     *
     * @return optional end date override for this entitlement.
     */
    @XmlTransient
    public Date getEndDateOverride() {
        return endDateOverride;
    }

    public void setEndDateOverride(Date endDateOverride) {
        this.endDateOverride = endDateOverride;
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
        this.certificates = new HashSet<>();

        if (certificates != null) {
            for (EntitlementCertificate cert : certificates) {
                this.addCertificate(cert);
            }
        }
    }

    public void addCertificate(EntitlementCertificate certificate) {
        if (certificate != null) {
            certificate.setEntitlement(this);
            certificates.add(certificate);
        }
    }

    public boolean removeCertificate(EntitlementCertificate certificate) {
        return this.certificates.remove(certificate);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Entitlement[id=").append(id);
        if (pool != null) {
            sb.append(", product=").append(pool.getProductId());
            sb.append(", pool=").append(pool.getId());
        }
        if (consumer != null) {
            sb.append(", consumer=").append(consumer.getUuid());
        }
        sb.append("]");
        return sb.toString();
    }

    @HateoasInclude
    public String getHref() {
        return "/entitlements/" + getId();
    }

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
    public boolean isDirty() {
        return dirty;
    }

    public void setDirty(boolean dirty) {
        this.dirty = dirty;
    }

    @XmlTransient
    public boolean isValidOnDate(Date d) {
        return (d.after(this.getStartDate()) || d.equals(this.getStartDate())) &&
            (d.before(this.getEndDate()) || d.equals(this.getEndDate()));
    }

    @XmlTransient
    public boolean isValid() {
        return this.isValidOnDate(new Date());
    }

    @Override
    public int compareTo(Entitlement other) {
        int compare = this.getPool().compareTo(other.getPool());
        if (compare == 0) {
            return (this.getId() == null ^ other.getId() == null) ?
                (this.getId() == null ? -1 : 1) :
                    this.getId() == other.getId() ? 0 :
                        this.getId().compareTo(other.getId());
        }
        return compare;
    }

    @Override
    @XmlTransient
    public String getName() {
        if (pool != null) {
            return pool.getProductName();
        }
        return null;
    }

    @XmlTransient
    public boolean isUpdatedOnStart() {
        return updatedOnStart;
    }

    public void setUpdatedOnStart(boolean updatedOnStart) {
        this.updatedOnStart = updatedOnStart;
    }

    @XmlTransient
    public boolean deletedFromPool() {
        return deletedFromPool;
    }

    public void setDeletedFromPool(boolean deletedFromPool) {
        this.deletedFromPool = deletedFromPool;
    }

}
