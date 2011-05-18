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

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.fedoraproject.candlepin.model.Permission;

/**
 * PrincipalData is a DTO for principal information until we move to 
 * Jackson 1.7 where were can store real JSON in strings.
 */
public class PrincipalData {

    private Map<String, List<Verb>> ownerVerbs;  // Map of owner key -> roles
    private String type;
    private String name;
    
    /**
     * @param ownerId
     * @param roles
     * @param type
     * @param name
     */
    public PrincipalData(Collection<Permission> permissions, String type, String name) {
        super();
        
        // Maps owner to list of verbs. Accumulates across all permissions, many
        // of which could be for the same owner.
        this.ownerVerbs = new HashMap<String, List<Verb>>();

        for (Permission permission : permissions) {
            String ownerKey = permission.getOwner().getKey();
            if (!ownerVerbs.containsKey(ownerKey)) {
                this.ownerVerbs.put(ownerKey, new ArrayList<Verb>());
            }
            
            this.ownerVerbs.get(ownerKey).add(permission.getVerb());
        }
        this.type = type;
        this.name = name;
    }
    
    public PrincipalData() {
    }

    public Map<String, List<Verb>> getPermissions() {
        return ownerVerbs;
    }
    
    /**
     * @return the type
     */
    public String getType() {
        return type;
    }
    /**
     * @param type the type to set
     */
    public void setType(String type) {
        this.type = type;
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
}
