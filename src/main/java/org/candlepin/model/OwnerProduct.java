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

import java.io.Serializable;
import java.time.Instant;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.IdClass;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.PrePersist;
import javax.persistence.PreUpdate;
import javax.persistence.Table;
import javax.xml.bind.annotation.XmlRootElement;



/**
 * Represents the join table between Product and Owner.
 *
 * This class uses composite primary key from the two
 * entities. This strategy has been chosen so that
 * the current Candlepin schema doesn't change. However,
 * should we encounter any problems with this design,
 * there is nothing that stops us from using standard
 * uuid for the link.
 */
@XmlRootElement
@Entity
@Table(name = OwnerProduct.DB_TABLE)
@IdClass(OwnerProductKey.class)
public class OwnerProduct implements Persisted, Serializable {

    /** Name of the table backing this object in the database */
    public static final String DB_TABLE = "cp2_owner_products";
    private static final long serialVersionUID = -7059065874812188165L;

    /**
     * This class already maps the foreign keys.
     * Because of that we need to disallow
     * Hibernate to update database based on
     * the owner and product fields.
     */
    @ManyToOne
    @JoinColumn(updatable = false, insertable = false)
    private Owner owner;

    @ManyToOne
    @JoinColumn(updatable = false, insertable = false)
    private Product product;

    @Id
    @Column(name = "owner_id")
    private String ownerId;

    @Id
    @Column(name = "product_id")
    private String productId;

    @Column(name = "product_uuid")
    private String productUuid;

    /** The date at which the product became orphaned (unused/flagged for deletion) within the org */
    @Column(name = "orphaned_date")
    private Instant orphanedDate;

    public OwnerProduct() {
        // Intentionally left empty
    }

    public OwnerProduct(Owner owner, Product product) {
        this.setOwner(owner);
        this.setProduct(product);
    }

    @Override
    public Serializable getId() {
        this.applyObjectIds();
        return new OwnerProductKey(this.ownerId, this.productUuid);
    }

    public Owner getOwner() {
        return owner;
    }

    public OwnerProduct setOwner(Owner owner) {
        this.owner = owner;
        return this;
    }

    public Product getProduct() {
        return product;
    }

    public OwnerProduct setProduct(Product product) {
        this.product = product;
        return this;
    }

    /**
     * Fetches the instant at which this product has been detected as orphaned (or unused) within
     * the organization represented by this OwnerProduct linkage. If the product is still in use or
     * otherwise hasn't been detected as an orphan, this method will return null.
     *
     * @return
     *  the instant the product has been orphaned within the organization, or null if the product
     *  has not yet been flagged for deletion.
     */
    public Instant getOrphanedDate() {
        return this.orphanedDate;
    }

    /**
     * Sets or clears the date this product has been orphaned within the organization represented by
     * this OwnerProduct linkage. If the incoming date is null, any existing date will be cleared.
     *
     * @param date
     *  the date at which this product has been orphaned within the organization, or null to clear
     *  any existing orphan date
     *
     * @return
     *  a reference to this OwnerProduct instance
     */
    public OwnerProduct setOrphanedDate(Instant date) {
        this.orphanedDate = date;
        return this;
    }

    @Override
    public String toString() {
        return String.format("OwnerProduct [%s => %s]", this.getOwner(), this.getProduct());
    }

    /**
     * Sets the database object IDs this join object uses to link owners to content. If either the
     * owner or content are not present or have not been persisted with a valid ID or UUID, this
     * method will throw an IllegalStateException.
     *
     * @throws IllegalStateException
     *  if either owner or content are null or unpersisted
     */
    protected void applyObjectIds() {
        if (this.owner == null) {
            throw new IllegalStateException("An owner must be specified to link products");
        }

        if (this.owner.getId() == null) {
            throw new IllegalStateException(
                "Owner must be persisted before it can be linked to products"
            );
        }

        if (this.product == null) {
            throw new IllegalStateException("A product must be specified to link an owner");
        }

        if (this.product.getUuid() == null) {
            throw new IllegalStateException(
                "Product must be persisted before it can be linked to an owner"
            );
        }

        if (this.product.getId() == null) {
            throw new IllegalStateException("Product must have a value for the Id field");
        }

        this.ownerId = owner.getId();
        this.productId = product.getId();
        this.productUuid = product.getUuid();
    }

    @PrePersist
    protected void onCreate() {
        this.applyObjectIds();
    }

    @PreUpdate
    protected void onUpdate() {
        this.applyObjectIds();
    }
}
