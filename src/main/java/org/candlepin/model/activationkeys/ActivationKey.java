/*
 * Copyright (c) 2009 - 2023 Red Hat, Inc.
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
import org.candlepin.model.Eventful;
import org.candlepin.model.Named;
import org.candlepin.model.Owned;
import org.candlepin.model.Owner;
import org.candlepin.model.Pool;
import org.candlepin.model.Product;
import org.candlepin.model.Release;
import org.candlepin.util.Util;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.hibernate.annotations.Cascade;
import org.hibernate.annotations.CascadeType;
import org.hibernate.annotations.GenericGenerator;

import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import javax.persistence.CollectionTable;
import javax.persistence.Column;
import javax.persistence.ElementCollection;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
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

    @Column(name = "owner_id", nullable = false)
    private String ownerId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id", insertable = false, updatable = false)
    private Owner owner;

    @OneToMany(mappedBy = "activationKey")
    @Cascade({CascadeType.ALL, CascadeType.DELETE_ORPHAN})
    private Set<ActivationKeyPool> pools = new HashSet<>();

    @ElementCollection
    @CollectionTable(name = "cp_activation_key_products", joinColumns = @JoinColumn(name = "key_id"))
    @Column(name = "product_id")
    private Set<String> productIds = new HashSet<>();

    @OneToMany(targetEntity = ActivationKeyContentOverride.class, mappedBy = "key")
    @Cascade({CascadeType.ALL, CascadeType.DELETE_ORPHAN})
    private Set<ActivationKeyContentOverride> contentOverrides = new HashSet<>();

    @Column(length = RELEASE_VERSION_LENGTH, nullable =  true)
    @Size(max = RELEASE_VERSION_LENGTH)
    private String releaseVer;

    @Column(length = 255, nullable =  true)
    @Size(max = 255)
    private String serviceLevel;

    @Column(name = "sp_usage", length = 255, nullable =  true)
    @Size(max = 255)
    private String usage;

    @Column(name = "sp_role", length = 255, nullable =  true)
    @Size(max = 255)
    private String role;

    @ElementCollection
    @CollectionTable(name = "cp_act_key_sp_add_on", joinColumns = @JoinColumn(name = "activation_key_id"))
    @Column(name = "add_on")
    private Set<String> addOns = new HashSet<>();

    // must allow null state to determine if an update intended to alter
    @Column(name = "auto_attach")
    private Boolean autoAttach;

    public ActivationKey() {
        // Intentionally left empty
    }

    public ActivationKey(String name, Owner owner) {
        this.setName(name);

        if (owner != null) {
            this.setOwner(owner);
        }
    }

    public ActivationKey(String name, Owner owner, String description) {
        this(name, owner);

        this.setDescription(description);
    }

    public String getId() {
        return this.id;
    }

    public ActivationKey setId(String id) {
        this.id = id;
        return this;
    }

    public String getName() {
        return name;
    }

    public ActivationKey setName(String name) {
        this.name = name;
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getOwnerId() {
        return this.ownerId;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getOwnerKey() {
        Owner owner = this.getOwner();
        return owner == null ? null : owner.getOwnerKey();
    }

    /**
     * Fetches the owner of this activation key, if the owner ID is set. This may perform a lazy
     * lookup of the owner, and should generally be avoided if the owner ID is sufficient.
     *
     * @return
     *  The owner of this key, if the owner ID is populated; null otherwise.
     */
    public Owner getOwner() {
        return this.owner;
    }

    public ActivationKey setOwner(Owner owner) {
        if (owner == null || owner.getId() == null) {
            throw new IllegalArgumentException("owner is null or lacks an ID");
        }

        this.owner = owner;
        this.ownerId = owner.getId();

        return this;
    }

    public Set<ActivationKeyPool> getPools() {
        return this.pools;
    }

    public ActivationKey setPools(Collection<ActivationKeyPool> pools) {
        this.pools.clear();

        if (pools != null) {
            this.pools.addAll(pools);
        }

        return this;
    }

    public ActivationKey addPool(Pool pool, Long quantity) {
        ActivationKeyPool akpool = new ActivationKeyPool()
            .setKey(this)
            .setPool(pool)
            .setQuantity(quantity);

        this.pools.add(akpool);
        return this;
    }

    public boolean removePool(Pool pool) {
        boolean found = false;

        if (pool != null && pool.getId() != null) {
            String poolId = pool.getId();
            Iterator<ActivationKeyPool> iterator = this.pools.iterator();

            while (iterator.hasNext()) {
                ActivationKeyPool akp = iterator.next();
                if (poolId.equals(akp.getPoolId())) {
                    iterator.remove();
                    found = true;
                }
            }
        }

        return found;
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

        return this.pools.stream()
            .map(ActivationKeyPool::getPoolId)
            .anyMatch(pid -> pid.equals(pool.getId()));
    }

    public Set<String> getProductIds() {
        return this.productIds;
    }

    public ActivationKey setProductIds(Collection<String> productIds) {
        this.productIds.clear();

        if (productIds != null) {
            this.productIds.addAll(productIds);
        }

        return this;
    }

    /**
     * Adds the specified product ID to this activation key. If the productId is null or empty, this
     * method throws an exception. If the product ID had already been added to this activation key, this
     * method returns false.
     *
     * @param productId
     *  the Red Hat ID of the product to add to this activation key; cannot be null or empty
     *
     * @throws IllegalArgumentException
     *  if productId is null or empty
     *
     * @return
     *  true if the product ID was added successfully; false otherwise
     */
    public boolean addProductId(String productId) {
        if (productId == null || productId.isEmpty()) {
            throw new IllegalArgumentException("productId is null or empty");
        }

        return this.productIds.add(productId);
    }

    /**
     * Adds the specified product to this activation key, using its Red Hat product ID. If the product
     * is null, or lacks a product ID, this method throws an exception. If the product had already been
     * added to this activation key, this method returns false.
     *
     * @param product
     *  the product to add to this activation key; cannot be null and must have a valid product ID
     *
     * @throws IllegalArgumentException
     *  if product is null or lacks a valid Red Hat product ID
     *
     * @return
     *  true if the product was added successfully; false otherwise
     */
    public boolean addProduct(Product product) {
        if (product == null) {
            throw new IllegalArgumentException("product is null");
        }

        return this.addProductId(product.getId());
    }

    public boolean removeProductId(String productId) {
        return this.productIds.remove(productId);
    }

    public boolean removeProduct(Product product) {
        return product != null && this.removeProductId(product.getId());
    }

    public boolean hasProductId(String productId) {
        return this.productIds.contains(productId);
    }

    /**
     * Checks if the specified product has been added to this activation key.
     *
     * @param product
     *  The product to check
     *
     * @return
     *  true if the product has been added to this activation key; false otherwise.
     */
    public boolean hasProduct(Product product) {
        return product != null && this.hasProductId(product.getId());
    }

    public Set<ActivationKeyContentOverride> getContentOverrides() {
        return contentOverrides;
    }

    public ActivationKey setContentOverrides(Set<ActivationKeyContentOverride> contentOverrides) {
        this.contentOverrides.clear();
        this.addContentOverrides(contentOverrides);

        return this;
    }

    public ActivationKey addContentOverride(ActivationKeyContentOverride override) {
        this.addOrUpdate(override);
        return this;
    }

    public ActivationKey addContentOverrides(Collection<ActivationKeyContentOverride> overrides) {
        for (ActivationKeyContentOverride newOverride : overrides) {
            this.addContentOverride(newOverride);
        }

        return this;
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

    public ActivationKey removeAllContentOverrides() {
        this.contentOverrides.clear();
        return this;
    }

    public Release getReleaseVer() {
        return new Release(releaseVer);
    }

    public ActivationKey setReleaseVer(Release releaseVer) {
        this.releaseVer = releaseVer.getReleaseVer();
        return this;
    }

    public String getServiceLevel() {
        return serviceLevel;
    }

    public ActivationKey setServiceLevel(String serviceLevel) {
        this.serviceLevel = serviceLevel;
        return this;
    }

    public String getUsage() {
        return usage;
    }

    public ActivationKey setUsage(String usage) {
        this.usage = usage;
        return this;
    }

    public String getRole() {
        return role;
    }

    public ActivationKey setRole(String role) {
        this.role = role;
        return this;
    }

    public Set<String> getAddOns() {
        return addOns;
    }

    public ActivationKey setAddOns(Set<String> addOns) {
        this.addOns = addOns;
        return this;
    }

    public String getDescription() {
        return description;
    }

    public ActivationKey setDescription(String description) {
        this.description = description;
        return this;
    }

    private ActivationKey addOrUpdate(ActivationKeyContentOverride override) {
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

        return this;
    }

    public ActivationKey setAutoAttach(Boolean autoAttach) {
        this.autoAttach = autoAttach;
        return this;
    }

    public Boolean isAutoAttach() {
        return autoAttach;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        return new HashCodeBuilder(7, 17)
            .append(this.id)
            .toHashCode();
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

            equals = new EqualsBuilder()
                .append(this.getId(), that.getId())
                .append(this.getName(), that.getName())
                .append(this.getDescription(), that.getDescription())
                .append(this.getOwnerId(), that.getOwnerId())
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

            equals = equals && Util.collectionsAreEqual(this.getProductIds(), that.getProductIds());

            return equals;
        }

        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return String.format("ActivationKey [id: %s, name: %s, description: %s]",
            this.getId(), this.getName(), this.getDescription());
    }

}
