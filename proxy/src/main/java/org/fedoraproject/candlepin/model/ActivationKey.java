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
package org.fedoraproject.candlepin.model;

import java.util.ArrayList;
import java.util.List;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.JoinTable;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;
import javax.persistence.Table;
import javax.xml.bind.annotation.XmlRootElement;

import org.hibernate.annotations.ForeignKey;
import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.Index;

/**
 * SubscriptionToken
 */
@XmlRootElement
@Entity
@Table(name = "cp_activation_key")
public class ActivationKey extends AbstractHibernateObject {

    @Id
    @GeneratedValue(generator = "system-uuid")
    @GenericGenerator(name = "system-uuid", strategy = "uuid")
    @Column(length = 32)
    private String id;
    
    @Column(nullable = false)
    private String name;
    
    @Column(nullable = false)
    private Boolean autosubscribe = false; 
    
    @ManyToOne
    @ForeignKey(name = "fk_activation_key_owner")
    @JoinColumn(nullable = false)
    @Index(name = "cp_activation_key_owner_fk_idx")
    private Owner owner;
    
    @OneToMany
    @JoinTable(name = "cp_activationkey_pool",
        joinColumns = @JoinColumn(name = "key_id"),
        inverseJoinColumns = @JoinColumn(name = "pool_id")
    )
    private List<Pool> pools = new ArrayList<Pool>();
    
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
     * @return the autosubscribe
     */
    public Boolean getAutosubscribe() {
        return autosubscribe;
    }

    /**
     * @param autosubscribe the autosubscribe to set
     */
    public void setAutosubscribe(Boolean autosubscribe) {
        this.autosubscribe = autosubscribe;
    }

    /**
     * @return the pool
     */
    public List<Pool> getPools() {
        return pools;
    }

    /**
     * @param pools the pool to set
     */
    public void setPools(List<Pool> pools) {
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
}
