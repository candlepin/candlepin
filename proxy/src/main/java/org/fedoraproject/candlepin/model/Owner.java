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

import java.util.LinkedList;
import java.util.List;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * Represents the owner of entitlements
 */
@XmlRootElement
@XmlAccessorType(XmlAccessType.PROPERTY)
public class Owner extends BaseModel {
    
    private List<Consumer> consumers;
    private List<EntitlementPool> entitlementPools;
    private List<User> users;
    
    /**
     * @param uuid unique id for the owner
     */
    public Owner(String uuid) {
        super(uuid);
    }
    
    /**
     * Default
     */
    public Owner() {
    }
    
    /**
     * @return the consumers
     */
    public List<Consumer> getConsumers() {
        return consumers;
    }
    /**
     * @param consumers the consumers to set
     */
    public void setConsumers(List<Consumer> consumers) {
        this.consumers = consumers;
    }
    /**
     * @return the entitlementPools
     */
    public List<EntitlementPool> getEntitlementPools() {
        return entitlementPools;
    }
    /**
     * @param entitlementPools the entitlementPools to set
     */
    public void setEntitlementPools(List<EntitlementPool> entitlementPools) {
        this.entitlementPools = entitlementPools;
    }
    /**
     * @return the users
     */
    public List<User> getUsers() {
        return users;
    }
    /**
     * @param users the users to set
     */
    public void setUsers(List<User> users) {
        this.users = users;
    }
    
    /**
     * Add a user.
     * @param u to add to this org.
     */
    public void addUser(User u) {
        u.setOwner(this);
        if (this.users == null) {
            this.users = new LinkedList<User>();
        }
        this.users.add(u);
    }

    /**
     * Add a consumer to this owner
     * @param c consumer for this owner.
     */
    public void addConsumer(Consumer c) {
        c.setOwner(this);
        if (this.consumers == null) {
            this.consumers = new LinkedList<Consumer>();
        }
        this.consumers.add(c);
        
    }

    /**
     * add owner to the pool, and reference to the pool.
     * @param pool EntitlementPool for this owner.
     */
    public void addEntitlementPool(EntitlementPool pool) {
        pool.setOwner(this);
        if (this.entitlementPools == null) {
            this.entitlementPools = new LinkedList<EntitlementPool>();
        }
        this.entitlementPools.add(pool);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return "Owner [getName()=" + getName() + ", getUuid()=" +
            getUuid() + "]";
    }
}
