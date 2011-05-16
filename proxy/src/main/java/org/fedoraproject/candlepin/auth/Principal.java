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
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

/**
 * An entity interacting with Candlepin
 */
public abstract class Principal implements Serializable {

    private List<Owner> owners;
    private List<Role> roles;     

    public Principal(List<Owner> owners, List<Role> roles) {
        this.owners = owners;
        this.roles = roles;
        if (roles == null) {
            this.roles = new LinkedList<Role>();
        }
    }

    public List<Owner> getOwners() {
        return owners;
    }

    public void setOwners(List<Owner> owners) {
        this.owners = owners;
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
        List<String> ownerIds = new ArrayList<String>();
        for (Owner owner : getOwners()) {
            ownerIds.add(owner.getId());
        }
        
        PrincipalData data = new PrincipalData(ownerIds, getRoles(),
            this.getType(), this.getPrincipalName());
        
        return data;
    }

    @Override
    public String toString() {
        return Util.toJson(this.getData());
    }
}
