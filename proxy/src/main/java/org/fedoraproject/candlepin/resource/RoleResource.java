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
import java.util.List;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import org.fedoraproject.candlepin.model.Role;
import org.fedoraproject.candlepin.service.UserServiceAdapter;
import org.jboss.resteasy.annotations.providers.jaxb.Wrapped;

/**
 *
 */
@Path("/roles")
public class RoleResource {

    private UserServiceAdapter userService;

    @Inject
    public RoleResource(UserServiceAdapter userService) {
        this.userService = userService;
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    public void createRole(Role role) {
        this.userService.createRole(role);
    }

//    @GET
//    @Path("{name}")
//    @Produces(MediaType.APPLICATION_JSON)
//    public Role getRole(String name) {
//        return roleCurator.lookupByName(name);
//    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Wrapped(element = "roles")
    public List<Role> getRoles() {
        // TODO:  Add in filter options
        return userService.listRoles();
    }
    
}
