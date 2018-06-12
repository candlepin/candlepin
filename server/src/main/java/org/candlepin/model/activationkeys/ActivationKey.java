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

import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.commons.lang.builder.HashCodeBuilder;
import org.candlepin.model.AbstractHibernateObject;
import org.candlepin.model.Eventful;
import org.candlepin.model.Named;
import org.candlepin.model.Owned;
import org.candlepin.model.Owner;
import org.candlepin.model.Pool;
import org.candlepin.model.Product;
import org.candlepin.model.Release;

import org.candlepin.util.Util;
import org.hibernate.annotations.Cascade;
import org.hibernate.annotations.CascadeType;
import org.hibernate.annotations.GenericGenerator;

import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.JoinTable;
import javax.persistence.ManyToMany;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

/**
 * ActivationKey
 */
@Entity
@Table(name = ActivationKey.DB_TABLE,
    uniqueConstraints = {@UniqueConstraint(columnNames = {"name", "owner_id"})})
public class ActivationKey extends AbstractHibernateObject<ActivationKey> implements Owned, Named, Eventful {

    /** Name of the table backing this object in the database */
    public static final String DB_TABLE = "cp_activation_key";

    public static final int RELEASE_VERSION_LENGTH = 255;

    @Id
    @GeneratedValue(generator = "system-uuid")
    @GenericGenerator(name = "system-uuid", strategy = "uuid")
    @Column(length = 32)
    @NotNull
    private String id;

    @Column(nullable = false)
    @Size(max = 255)
    @NotNull
    private String name;

    @Column(nullable = true)
    @Size(max = 255)
    private String description;

    @ManyToOne
    @JoinColumn(nullable = false)
    @NotNull
    private Owner owner;

    @OneToMany(mappedBy = "key")
    @Cascade({CascadeType.ALL, CascadeType.DELETE_ORPHAN})
    private Set<ActivationKeyPool> pools = new HashSet<>();

    @ManyToMany
    @JoinTable(
        name = "cp2_activation_key_products",
        joinColumns = {@JoinColumn(name = "key_id")},
        inverseJoinColumns = {@JoinColumn(name = "product_uuid")})
    private Set<Product> products = new HashSet<>();

    @OneToMany(targetEntity = ActivationKeyContentOverride.class, mappedBy = "key")
    @Cascade({CascadeType.ALL, CascadeType.DELETE_ORPHAN})
    private Set<ActivationKeyContentOverride> contentOverrides = new HashSet<>();

    @Column(length = RELEASE_VERSION_LENGTH, nullable =  true)
    @Size(max = RELEASE_VERSION_LENGTH)
    private String releaseVer;

    @Column(length = 255, nullable =  true)
    @Size(max = 255)
    private String serviceLevel;

    // must allow null state to determine if an update intended to alter
    @Column(name = "auto_attach")
    private Boolean autoAttach;

    public ActivationKey() {
        // Intentionally left empty
    }

    public ActivationKey(String name, Owner owner) {
        this.name = name;
        this.owner = owner;
    }

    public ActivationKey(String name, Owner owner, String description) {
        this.name = name;
        this.owner = owner;
        this.description = description;
    }

    public String getId() {
        return this.id;
    }

    public void setId(String id) {
        this.id = id;
    }

    /**
     * @return the name
     */
    public String getName() {
        return name;
    }

    /**
     * @param name the name to set
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * @return the set of Pools
     */
    public Set<ActivationKeyPool> getPools() {
        return pools;
    }

    /**
     * @param pools the set of Pools to set
     */
    public void setPools(Set<ActivationKeyPool> pools) {
        this.pools = pools;
    }

    /**
     * @return the products
     */
    public Set<Product> getProducts() {
        return products;
    }

    /**
     * @param products the set of product to set
     */
    public void setProducts(Set<Product> products) {
        this.products = products;
    }

    /**
     * @return the owner
     */
    public Owner getOwner() {
        return owner;
    }

    /**
     * @return the owner Id of this Consumer.
     */
    @Override
    public String getOwnerId() {
        return (owner == null) ? null : owner.getId();
    }

    /**
     * @param owner the owner to set
     */
    public void setOwner(Owner owner) {
        this.owner = owner;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return String.format("ActivationKey [id: %s, name: %s, description: %s]",
                this.getId(), this.getName(), this.getDescription());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }

        boolean equals = false;

        if (obj instanceof ActivationKey && super.equals(obj)) {
            ActivationKey that = (ActivationKey) obj;

            // Pull the owner IDs, as we're not interested in verifying that the owners
            // themselves are equal; just so long as they point to the same owner.
            String thisOwnerId = this.getOwner() != null ? this.getOwner().getId() : null;
            String thatOwnerId = that.getOwner() != null ? that.getOwner().getId() : null;

            equals = new EqualsBuilder()
                .append(this.getId(), that.getId())
                .append(this.getName(), that.getName())
                .append(this.getDescription(), that.getDescription())
                .append(thisOwnerId, thatOwnerId)
                .append(this.getReleaseVer(), that.getReleaseVer())
                .append(this.getServiceLevel(), that.getServiceLevel())
                .append(this.isAutoAttach(), that.isAutoAttach())
                .isEquals();

            equals = equals && Util.collectionsAreEqual(this.getPools(), that.getPools(),
                new Comparator<ActivationKeyPool>() {
                    public int compare(ActivationKeyPool akp1, ActivationKeyPool akp2) {
                        return akp1 == akp2 || (akp1 != null && akp1.equals(akp2)) ? 0 : 1;
                    }
                });

            equals = equals && Util.collectionsAreEqual(this.getProducts(), that.getProducts(),
                new Comparator<Product>() {
                    public int compare(Product prod1, Product prod2) {
                        return prod1 == prod2 || (prod1 != null && prod1.equals(prod2)) ? 0 : 1;
                    }
                });

            return equals;
        }

        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        HashCodeBuilder builder = new HashCodeBuilder(7, 17)
            .append(this.id);
        return builder.toHashCode();
    }

    public void addProduct(Product product) {
        this.getProducts().add(product);
    }

    public void removeProduct(Product product) {
        for (Product candidate : this.getProducts()) {
            if (product.getId().equals(candidate.getId())) {
                this.getProducts().remove(candidate);
                break;
            }
        }
    }

    /**
     * Checks if the specified product has been added to this activation key.
     *
     * @param product
     *  The product to check
     *
     * @throws IllegalArgumentException
     *  if product is null
     *
     * @return
     *  true if the product has been added to this activation key; false otherwise.
     */
    public boolean hasProduct(Product product) {
        if (product == null) {
            throw new IllegalArgumentException("product is null");
        }

        for (Product candidate : this.getProducts()) {
            if (product.getId().equals(candidate.getId())) {
                return true;
            }
        }

        return false;
    }

    public void addPool(Pool pool, Long quantity) {
        ActivationKeyPool akp = new ActivationKeyPool(this, pool, quantity);
        this.getPools().add(akp);
    }

    public void removePool(Pool pool) {
        ActivationKeyPool toRemove = null;

        for (ActivationKeyPool akp : this.getPools()) {
            if (akp.getPool().getId().equals(pool.getId())) {
                toRemove = akp;
                break;
            }
        }

        this.getPools().remove(toRemove);
    }

    /**
     * Checks if the specified pool has been added to this activation key.
     *
     * @param pool
     *  The pool to check
     *
     * @throws IllegalArgumentException
     *  if pool is null
     *
     * @return
     *  true if the pool has been added to this activation key; false otherwise.
     */
    public boolean hasPool(Pool pool) {
        if (pool == null) {
            throw new IllegalArgumentException("pool is null");
        }

        for (ActivationKeyPool akp : this.getPools()) {
            if (akp.getPool().getId().equals(pool.getId())) {
                return true;
            }
        }

        return false;
    }

    /**
     * @return the contentOverrides
     */
    public Set<ActivationKeyContentOverride> getContentOverrides() {
        return contentOverrides;
    }

    /**
     * @param contentOverrides the contentOverrides to set
     */
    public void setContentOverrides(Set<ActivationKeyContentOverride> contentOverrides) {
        this.contentOverrides.clear();
        this.addContentOverrides(contentOverrides);
    }

    public void addContentOverride(ActivationKeyContentOverride override) {
        this.addOrUpdate(override);
    }

    public void addContentOverrides(Collection<ActivationKeyContentOverride> overrides) {
        for (ActivationKeyContentOverride newOverride : overrides) {
            this.addContentOverride(newOverride);
        }
    }

    public ActivationKeyContentOverride removeContentOverride(String overrideId) {
        ActivationKeyContentOverride toRemove = null;

        for (ActivationKeyContentOverride akco : this.getContentOverrides()) {
            if (akco.getId().equals(overrideId)) {
                toRemove = akco;
                break;
            }
        }

        if (toRemove != null) {
            this.getContentOverrides().remove(toRemove);
        }
        return toRemove;
    }

    public void removeAllContentOverrides() {
        this.contentOverrides.clear();
    }

    /**
     * @return the releaseVer
     */
    public Release getReleaseVer() {
        return new Release(releaseVer);
    }

    /**
     * @param releaseVer the releaseVer to set
     */
    public void setReleaseVer(Release releaseVer) {
        this.releaseVer = releaseVer.getReleaseVer();
    }

    /**
     * @return the service level
     */
    public String getServiceLevel() {
        return serviceLevel;
    }

    /**
     * @param serviceLevel the service level to set
     */
    public void setServiceLevel(String serviceLevel) {
        this.serviceLevel = serviceLevel;
    }

    /**
     * @return the description
     */
    public String getDescription() {
        return description;
    }

    /**
     * @param description
     */
    public void setDescription(String description) {
        this.description = description;
    }

    private void addOrUpdate(ActivationKeyContentOverride override) {
        boolean found = false;
        for (ActivationKeyContentOverride existing : this.getContentOverrides()) {
            if (existing.getContentLabel().equalsIgnoreCase(override.getContentLabel()) &&
                existing.getName().equalsIgnoreCase(override.getName())) {
                existing.setValue(override.getValue());
                found = true;
                break;
            }
        }

        if (!found) {
            override.setKey(this);
            this.getContentOverrides().add(override);
        }
    }

    public void setAutoAttach(Boolean autoAttach) {
        this.autoAttach = autoAttach;
    }

    public Boolean isAutoAttach() {
        return autoAttach;
    }

}
