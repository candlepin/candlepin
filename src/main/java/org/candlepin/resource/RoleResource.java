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
import org.candlepin.exceptions.NotFoundException;
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

/**
 *
 */
@Path("/roles")
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

    /**
     * Creates a Role
     *
     * @return a Role object
     * @httpcode 404
     * @httpcode 200
     */
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

    /**
     * Updates a Role
     * <p>
     * To avoid race conditions, we do not support updating the user or permission
     * collections. Currently this call will only update the role name.
     * <p>
     * See the specific nested POST/DELETE calls for modifying users and permissions.
     *
     * @return a Role object
     * @httpcode 404
     * @httpcode 200
     */
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

    /**
     * Adds a Permission to a Role
     * <p>
     * Returns the updated Role.
     *
     * @return a Role object
     * @httpcode 404
     * @httpcode 400
     * @httpcode 200
     */
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

    /**
     * Removes a Permission from a Role
     * <p>
     * Returns the updated Role.
     *
     * @return a Role object
     * @httpcode 404
     * @httpcode 200
     */
    @DELETE
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

    /**
     * Retrieves a single Role
     * <p>
     * <pre>
     * {
     *     "id" : "database_id",
     *     "owner" : {},
     *     "access" : "READ_ONLY",
     *     "type" : "OWNER",
     *     "created" : [date],
     *     "updated" : [date]
     * }
     * </pre>
     *
     * @return a Role object
     * @httpcode 200
     */
    @GET
    @Path("{role_id}")
    @Produces(MediaType.APPLICATION_JSON)
    public Role getRole(@PathParam("role_id") String roleId) {
        return lookupRole(roleId);
    }

    /**
     * Removes a Role
     *
     * @httpcode 200
     */
    @DELETE
    @Path("/{role_id}")
    public void deleteRole(@PathParam("role_id") String roleId) {
        this.userService.deleteRole(roleId);
    }

    /**
     * Adds a User to a Role
     *
     * @return a Role object
     * @httpcode 404
     * @httpcode 200
     */
    @POST
    @Path("/{role_id}/users/{username}")
    @Produces(MediaType.APPLICATION_JSON)
    public Role addUser(@PathParam("role_id") String roleId,
        @PathParam("username") String username) {
        Role role = lookupRole(roleId);
        User user = lookupUser(username);
        userService.addUserToRole(role, user);
        return role;
    }

    /**
     * Removes a User from a Role
     *
     * @return a Role object
     * @httpcode 404
     */
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

    /**
     * Retrieves a list of Roles
     *
     * @return a list of Role objects
     * @httpcode 200
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Wrapped(element = "roles")
    public List<Role> getRoles() {
        // TODO:  Add in filter options
        return userService.listRoles();
    }

}
