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

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;

import org.candlepin.model.AbstractHibernateObject;
import org.candlepin.model.Owned;
import org.candlepin.model.Owner;
import org.candlepin.model.Pool;
import org.candlepin.model.Release;
import org.hibernate.annotations.Cascade;
import org.hibernate.annotations.ForeignKey;
import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.Index;

/**
 * ActivationKey
 */
@XmlRootElement
@XmlAccessorType(XmlAccessType.PROPERTY)
@Entity
@Table(name = "cp_activation_key",
    uniqueConstraints = {@UniqueConstraint(columnNames = {"name", "owner_id"})}
)
public class ActivationKey extends AbstractHibernateObject implements Owned {

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

    @ManyToOne
    @ForeignKey(name = "fk_activation_key_owner")
    @JoinColumn(nullable = false)
    @Index(name = "cp_activation_key_owner_fk_idx")
    @NotNull
    private Owner owner;

    @OneToMany(mappedBy = "key")
    @Cascade({org.hibernate.annotations.CascadeType.ALL,
        org.hibernate.annotations.CascadeType.DELETE_ORPHAN})
    private Set<ActivationKeyPool> pools = new HashSet<ActivationKeyPool>();

    @OneToMany(targetEntity = ActivationKeyContentOverride.class, mappedBy = "key")
    @Cascade({org.hibernate.annotations.CascadeType.ALL,
        org.hibernate.annotations.CascadeType.DELETE_ORPHAN})
    private Set<ActivationKeyContentOverride> contentOverrides =
        new HashSet<ActivationKeyContentOverride>();

    @Column(length = RELEASE_VERSION_LENGTH, nullable =  true)
    @Size(max = RELEASE_VERSION_LENGTH)
    private String releaseVer;

    @Column(length = 255, nullable =  true)
    @Size(max = 255)
    private String serviceLevel;

    public ActivationKey() {
    }

    public ActivationKey(String name, Owner owner) {
        this.name = name;
        this.owner = owner;
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
     * @return the pool
     */
    public Set<ActivationKeyPool> getPools() {
        return pools;
    }

    /**
     * @param pools the pool to set
     */
    public void setPools(Set<ActivationKeyPool> pools) {
        this.pools = pools;
    }

    /**
     * @return the owner
     */
    public Owner getOwner() {
        return owner;
    }

    /**
     * @param owner the owner to set
     */
    public void setOwner(Owner owner) {
        this.owner = owner;
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
     * @return the contentOverrides
     */
    public Set<ActivationKeyContentOverride> getContentOverrides() {
        return contentOverrides;
    }

    /**
     * @param contentOverrides the contentOverrides to set
     */
    public void setContentOverrides(
            Set<ActivationKeyContentOverride> contentOverrides) {
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
}
