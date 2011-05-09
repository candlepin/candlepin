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

import java.util.List;

/**
 * PrincipalData is a DTO for principal information until we move to 
 * Jackson 1.7 where were can store real JSON in strings.
 */
public class PrincipalData {

    private String ownerId;
    private List<Role> roles;
    private String type;
    private String name;
    
    /**
     * @param ownerId
     * @param roles
     * @param type
     * @param name
     */
    public PrincipalData(String ownerId, List<Role> roles, String type,
        String name) {
        super();
        this.ownerId = ownerId;
        this.roles = roles;
        this.type = type;
        this.name = name;
    }
    
    public PrincipalData() {
    }
    
    /**
     * @return the ownerId
     */
    public String getOwnerId() {
        return ownerId;
    }
    /**
     * @param ownerId the ownerId to set
     */
    public void setOwnerId(String ownerId) {
        this.ownerId = ownerId;
    }
    /**
     * @return the roles
     */
    public List<Role> getRoles() {
        return roles;
    }
    /**
     * @param roles the roles to set
     */
    public void setRoles(List<Role> roles) {
        this.roles = roles;
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
