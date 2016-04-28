/**
 * Copyright (c) 2009 - 2012 Red Hat, Inc.
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
package org.candlepin.resource;

import org.candlepin.auth.Access;
import org.candlepin.common.exceptions.NotFoundException;
import org.candlepin.model.Owner;
import org.candlepin.model.OwnerCurator;
import org.candlepin.model.PermissionBlueprint;
import org.candlepin.model.PermissionBlueprintCurator;
import org.candlepin.model.Role;
import org.candlepin.model.User;
import org.candlepin.service.UserServiceAdapter;

import com.google.inject.Inject;

import org.jboss.resteasy.annotations.providers.jaxb.Wrapped;
import org.jboss.resteasy.spi.BadRequestException;
import org.xnap.commons.i18n.I18n;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;

/**
 *
 */
@Path("/roles")
@Api("roles")
public class RoleResource {

    private UserServiceAdapter userService;
    private OwnerCurator ownerCurator;
    private PermissionBlueprintCurator permissionCurator;
    private I18n i18n;

    @Inject
    public RoleResource(UserServiceAdapter userService, OwnerCurator ownerCurator,
        PermissionBlueprintCurator permCurator, I18n i18n) {
        this.userService = userService;
        this.ownerCurator = ownerCurator;
        this.i18n = i18n;
        this.permissionCurator = permCurator;
    }

    @ApiOperation(notes = "Creates a Role", value = "createRole")
    @ApiResponses({ @ApiResponse(code = 404, message = "") })
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Role createRole(Role role) {

        // Attach actual owner objects to each incoming permission:
        for (PermissionBlueprint p : role.getPermissions()) {
            Owner temp = p.getOwner();
            Owner actual = ownerCurator.lookupByKey(temp.getKey());
            if (actual == null) {
                throw new NotFoundException(i18n.tr("No such owner: {0}", temp.getKey()));
            }
            p.setOwner(actual);
        }

        Role r = this.userService.createRole(role);
        return r;
    }

    @ApiOperation(notes = "Updates a Role.  To avoid race conditions, we do not support " +
        "updating the user or permission collections. Currently this call will only update " +
        "the role name. See the specific nested POST/DELETE calls for modifying users and" +
        " permissions.", value = "updateRole")
    @ApiResponses({ @ApiResponse(code = 404, message = "") })
    @PUT
    @Path("{role_id}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Role updateRole(@PathParam("role_id") String roleId, Role role) {
        //Only operate here if you only have 1 ID to pull,
        //but if the user passes in an ID in the body of the JSON
        //and that ID is NOT equal to what the ID in the URL is, then throw an error
        if (role.getId() != null && !roleId.equals(role.getId())) {
            throw new BadRequestException(i18n.tr("Role ID does not match path."));
        }
        Role existingRole = lookupRole(roleId);
        existingRole.setName(role.getName());
        return this.userService.updateRole(existingRole);
    }

    @ApiOperation(notes = "Adds a Permission to a Role. Returns the updated Role.",
        value = "addRolePermission")
    @ApiResponses({ @ApiResponse(code = 404, message = ""), @ApiResponse(code = 400, message = "") })
    @POST
    @Path("{role_id}/permissions")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Role addRolePermission(@PathParam("role_id") String roleId,
        PermissionBlueprint permission) {

        Role existingRole = lookupRole(roleId);

        // Don't allow NONE permissions to be created, this is currently just for
        // internal use:
        if (permission.getAccess().equals(Access.NONE)) {
            throw new BadRequestException(i18n.tr("Access type NONE not supported."));
        }

        // Attach actual owner objects to each incoming permission:
        Owner temp = permission.getOwner();
        Owner real = ownerCurator.lookupByKey(temp.getKey());
        permission.setOwner(real);
        existingRole.addPermission(permission);

        Role r = this.userService.updateRole(existingRole);
        return r;
    }

    @ApiOperation(notes = "Removes a Permission from a Role. Returns the updated Role.",
        value = "removeRolePermission")
    @ApiResponses({ @ApiResponse(code = 404, message = "") })    @DELETE
    @Path("{role_id}/permissions/{perm_id}")
    @Produces(MediaType.APPLICATION_JSON)
    public Role removeRolePermission(@PathParam("role_id") String roleId,
        @PathParam("perm_id") String permissionId) {

        Role existingRole = lookupRole(roleId);
        Set<PermissionBlueprint> picks = new HashSet<PermissionBlueprint>();
        boolean found = true;
        PermissionBlueprint toRemove = null;
        for (PermissionBlueprint op : existingRole.getPermissions()) {
            if (!op.getId().equals(permissionId)) {
                picks.add(op);
            }
            else {
                found = true;
                toRemove = op;
            }

        }
        if (!found) {
            throw new NotFoundException(i18n.tr("No such permission: {0} in role: {1}",
                permissionId, roleId));
        }

        existingRole.setPermissions(picks);
        Role r = this.userService.updateRole(existingRole);
        toRemove.setOwner(null);
        permissionCurator.delete(toRemove);
        return r;
    }

    private Role lookupRole(String roleId) {
        Role role = userService.getRole(roleId);
        if (role == null) {
            throw new NotFoundException(i18n.tr("No such role: {0}", roleId));
        }
        return role;
    }

    private User lookupUser(String username) {
        User user = userService.findByLogin(username);
        if (user == null) {
            throw new NotFoundException(i18n.tr("No such user: {0}", username));
        }
        return user;
    }

    @ApiOperation(notes = "Retrieves a single Role", value = "getRole")
    @GET
    @Path("{role_id}")
    @Produces(MediaType.APPLICATION_JSON)
    public Role getRole(@PathParam("role_id") String roleId) {
        return lookupRole(roleId);
    }

    @ApiOperation(notes = "Removes a Role", value = "deleteRole")
    @DELETE
    @Path("/{role_id}")
    @Produces(MediaType.WILDCARD)
    public void deleteRole(@PathParam("role_id") String roleId) {
        this.userService.deleteRole(roleId);
    }

    @ApiOperation(notes = "Adds a User to a Role", value = "addUser")
    @ApiResponses({ @ApiResponse(code = 404, message = "") })
    @POST
    @Path("/{role_id}/users/{username}")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.WILDCARD)
    public Role addUser(@PathParam("role_id") String roleId,
        @PathParam("username") String username) {
        Role role = lookupRole(roleId);
        User user = lookupUser(username);
        userService.addUserToRole(role, user);
        return role;
    }

    @ApiOperation(notes = "Removes a User from a Role", value = "deleteUser")
    @ApiResponses({ @ApiResponse(code = 404, message = "") })
    @DELETE
    @Path("/{role_id}/users/{username}")
    @Produces(MediaType.APPLICATION_JSON)
    public Role deleteUser(@PathParam("role_id") String roleId,
        @PathParam("username") String username) {
        Role role = lookupRole(roleId);
        User user = lookupUser(username);
        userService.removeUserFromRole(role, user);
        return role;
    }

    @ApiOperation(notes = "Retrieves a list of Roles", value = "getRoles")
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Wrapped(element = "roles")
    public List<Role> getRoles() {
        // TODO:  Add in filter options
        return userService.listRoles();
    }

}
