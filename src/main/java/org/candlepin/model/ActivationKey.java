/**
 * Copyright (c) 2009 Red Hat, Inc.
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

import org.hibernate.annotations.ForeignKey;
import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.Index;

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
import javax.persistence.UniqueConstraint;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;

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

    @Id
    @GeneratedValue(generator = "system-uuid")
    @GenericGenerator(name = "system-uuid", strategy = "uuid")
    @Column(length = 32)
    private String id;

    @Column(nullable = false)
    private String name;

    @ManyToOne
    @ForeignKey(name = "fk_activation_key_owner")
    @JoinColumn(nullable = false)
    @Index(name = "cp_activation_key_owner_fk_idx")
    private Owner owner;

    @OneToMany(cascade = CascadeType.ALL, mappedBy = "key")
    @org.hibernate.annotations.Cascade(org.hibernate.annotations.CascadeType.DELETE_ORPHAN)
    private Set<ActivationKeyPool> pools = new HashSet<ActivationKeyPool>();

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

    public void addPool(Pool pool, long quantity) {
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
}
