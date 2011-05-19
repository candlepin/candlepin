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
package org.fedoraproject.candlepin.resource;

import com.google.inject.Inject;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import org.fedoraproject.candlepin.model.Permission;
import org.fedoraproject.candlepin.model.PermissionCurator;
import org.fedoraproject.candlepin.model.Role;
import org.fedoraproject.candlepin.model.RoleCurator;

/**
 *
 */
@Path("/roles")
public class RoleResource {

    private RoleCurator roleCurator;
    private PermissionCurator permissionCurator;

    @Inject
    public RoleResource(RoleCurator roleCurator, PermissionCurator permissionCurator) {
        this.roleCurator = roleCurator;
        this.permissionCurator = permissionCurator;
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    public void createRole(Role role) {
        Set<Permission> actualPermissions = new HashSet<Permission>();

        for (Permission permission : role.getPermissions()) {
            actualPermissions.add(this.permissionCurator.findOrCreate(
                    permission.getOwner(), permission.getVerb()));
        }

        role.setPermissions(actualPermissions);
        this.roleCurator.create(role);
    }

    @GET
    @Path("{name}")
    @Produces(MediaType.APPLICATION_JSON)
    public Role getRole(String name) {
        return roleCurator.lookupByName(name);
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public List<Role> getRoles() {
        // TODO:  Add in filter options
        return roleCurator.listAll();
    }
    
}
