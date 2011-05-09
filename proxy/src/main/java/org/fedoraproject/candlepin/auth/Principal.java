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
package org.fedoraproject.candlepin.auth;

import org.fedoraproject.candlepin.model.Owner;
import org.fedoraproject.candlepin.util.Util;

import java.io.Serializable;
import java.util.LinkedList;
import java.util.List;

/**
 * An entity interacting with Candlepin
 */
public abstract class Principal implements Serializable {

    private Owner owner;
    private List<Role> roles;     

    public Principal(Owner owner, List<Role> roles) {
        this.owner = owner;
        this.roles = roles;
        if (roles == null) {
            this.roles = new LinkedList<Role>();
        }
    }

    public Owner getOwner() {
        return owner;
    }

    public void setOwner(Owner owner) {
        this.owner = owner;
    }

    public List<Role> getRoles() {
        return roles;
    }
    
    public Boolean hasRole(Role role) {
        return roles.contains(role);
    }

    public boolean isConsumer() {
        return false;
    }
    
    public String getType() {
        return "principal";
    }
    
    public String getPrincipalName() {
        return "";
    }
    
    public PrincipalData getData() {
        String ownerId = getOwner() != null ? getOwner().getId() : null;
        PrincipalData data = new PrincipalData(ownerId, getRoles(),
            this.getType(), this.getPrincipalName());
        
        return data;
    }
    
    public String toString() {
        return Util.toJson(this.getData());
    }
}
