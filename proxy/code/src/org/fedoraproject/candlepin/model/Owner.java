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

import java.util.HashSet;
import java.util.Set;

import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.OneToMany;
import javax.persistence.Table;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;

import org.hibernate.annotations.ForeignKey;


/**
 * Represents the owner of entitlements.
 * 
 * This is akin to an organization, whereas a User is an individual account within that
 * organization.
 */
@XmlRootElement
@XmlAccessorType(XmlAccessType.PROPERTY)
@Entity
@Table(name = "cp_owner")
public class Owner {
    
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;
    
    @Column(nullable = false)
    private String name;

    @OneToMany(mappedBy = "owner", targetEntity = Consumer.class)
    private Set<Consumer> consumers;
    
    // EntitlementPool is the owning side of this relationship.
    @OneToMany(mappedBy = "owner", targetEntity = EntitlementPool.class)
    @ForeignKey(name = "fk_user_owner_id")
    private Set<EntitlementPool> entitlementPools;
    
    @OneToMany(mappedBy = "owner", targetEntity = User.class, cascade = {CascadeType.PERSIST, CascadeType.MERGE, CascadeType.REMOVE})
    private Set<User> users;
    
    @OneToMany(mappedBy = "owner", targetEntity = Certificate.class)
    private Set<Certificate> certificates;
    
    
    /**
     * Default constructor
     */
    public Owner() {
        consumers = new HashSet<Consumer>();
        entitlementPools = new HashSet<EntitlementPool>();
        users = new HashSet<User>();
        certificates = new HashSet<Certificate>();
    }
    
    /**
     * Constructor with required parameters.
     * 
     * @param name Owner's name.
     */
    public Owner(String nameIn) {
        this.name = nameIn;
        
        consumers = new HashSet<Consumer>();
        entitlementPools = new HashSet<EntitlementPool>();
        users = new HashSet<User>();
        certificates = new HashSet<Certificate>();
    }
    
    /**
     * @return the id
     */
    public Long getId() {
        return id;
    }

    /**
     * @param id the id to set
     */
    public void setId(Long id) {
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
     * @return the consumers
     */
    public Set<Consumer> getConsumers() {
        return consumers;
    }
    /**
     * @param consumers the consumers to set
     */
    public void setConsumers(Set<Consumer> consumers) {
        this.consumers = consumers;
    }
    /**
     * @return the entitlementPools
     */
    public Set<EntitlementPool> getEntitlementPools() {
        return entitlementPools;
    }
    /**
     * @param entitlementPools the entitlementPools to set
     */
    public void setEntitlementPools(Set<EntitlementPool> entitlementPools) {
        this.entitlementPools = entitlementPools;
    }
    /**
     * @return the users
     */
    public Set<User> getUsers() {
        return users;
    }
    /**
     * @param users the users to set
     */
    public void setUsers(Set<User> users) {
        this.users = users;
    }
    
    /**
     * Add a user.
     * @param u to add to this org.
     */
    public void addUser(User u) {
        u.setOwner(this);
        if (this.users ==  null) {
            this.users = new HashSet<User>();
        }
        this.users.add(u);
    }

    /**
     * Add a consumer to this owner
     * @param c consumer for this owner.
     */
    public void addConsumer(Consumer c) {
        c.setOwner(this);
        this.consumers.add(c);
        
    }

    /**
     * add owner to the pool, and reference to the pool.
     * @param pool EntitlementPool for this owner.
     */
    public void addEntitlementPool(EntitlementPool pool) {
        pool.setOwner(this);
        if (this.entitlementPools ==  null) {
            this.entitlementPools = new HashSet<EntitlementPool>();
        }
        this.entitlementPools.add(pool);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return "Owner [name = " + getName() + ", id = " +
            getId() + "]";
    }
}
