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
import org.candlepin.auth.Principal;
import org.candlepin.auth.SubResource;
import org.candlepin.auth.Verify;
import org.candlepin.common.exceptions.ConflictException;
import org.candlepin.common.exceptions.GoneException;
import org.candlepin.common.exceptions.NotFoundException;
import org.candlepin.model.Owner;
import org.candlepin.model.OwnerCurator;
import org.candlepin.model.Role;
import org.candlepin.model.User;
import org.candlepin.service.UserServiceAdapter;

import com.google.inject.Inject;

import org.xnap.commons.i18n.I18n;

import java.util.HashSet;
import java.util.LinkedList;
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
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;

/**
 * UserResource
 */
@Path("/users")
@Api("users")
public class UserResource {

    private UserServiceAdapter userService;
    private I18n i18n;
    private OwnerCurator ownerCurator;

    @Inject
    public UserResource(UserServiceAdapter userService, I18n i18n,
        OwnerCurator ownerCurator) {
        this.userService = userService;
        this.i18n = i18n;
        this.ownerCurator = ownerCurator;
    }

    @ApiOperation(notes = "Retrieves a list of Users", value = "list")
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public List<User> list() {
        return userService.listUsers();
    }

    @ApiOperation(notes = "Retrieves a single User", value = "getUserInfo")
    @GET
    @Path("/{username}")
    @Produces(MediaType.APPLICATION_JSON)
    public User getUserInfo(@PathParam("username")
        @Verify(User.class) String username) {
        return userService.findByLogin(username);
    }

    /*
     * getUserRoles will only return roles for one user. If you want a
     * full view of a role, use /roles/ instead.
     */
    @ApiOperation(notes = "Retrieves a list of Roles by User", value = "getUserRoles")
    @GET
    @Path("/{username}/roles")
    @Produces(MediaType.APPLICATION_JSON)
    public List<Role> getUserRoles(@PathParam("username")
        @Verify(User.class) String username) {
        User myUser = userService.findByLogin(username);
        List<Role> roles = new LinkedList<Role>();
        Set<User> s = new HashSet<User>();
        s.add(myUser);
        for (Role r : myUser.getRoles()) {
            // Copy onto a detached role object so we can omit users list, which could
            // technically leak information here.
            Role copy = new Role(r.getName());
            copy.setId(r.getId());
            copy.setPermissions(r.getPermissions());
            copy.setUsers(s);
            roles.add(copy);
        }
        return roles;
    }

    @ApiOperation(notes = "Creates a User", value = "createUser")
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public User createUser(User user) {
        if (userService.findByLogin(user.getUsername()) != null) {
            throw new ConflictException("user " + user.getUsername() + " already exists");
        }
        return userService.createUser(user);
    }

    @ApiOperation(notes = "Updates a User", value = "updateUser")
    @ApiResponses({ @ApiResponse(code = 404, message = "") })
    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/{username}")
    public User updateUser(@PathParam("username")
        @Verify(User.class) String username,
        User user) {

        // Note, to change the username, the old username needs to be provided.
        if (userService.findByLogin(username) == null) {
            throw new NotFoundException(i18n.tr("User {0} does not exist", username));
        }
        return userService.updateUser(user);
    }


    @ApiOperation(notes = "Removes a User", value = "deleteUser")
    @ApiResponses({ @ApiResponse(code = 410, message = "") })
    @DELETE
    @Path("/{username}")
    @Produces(MediaType.APPLICATION_JSON)
    public void deleteUser(@PathParam("username") String username) {
        User user = userService.findByLogin(username);
        if (user == null) {
            throw new GoneException(i18n.tr("User {0} not found", username), username);
        }
        else {
            userService.deleteUser(user);
        }
    }

    @ApiOperation(notes = "Retrieve a list of owners the user can register systems to. " +
        "Previously this represented owners the user was an admin for. Because the " +
        "client uses this API call to list the owners a user can register to, when " +
        "we introduced 'my systems' administrator, we have to change its meaning to " +
        "listing the owners that can be registered to by default to maintain " +
        "compatability with released clients.", value = "listUsersOwners")
    // TODO: should probably accept access level and sub-resource query params someday
    @GET
    @Path("/{username}/owners")
    @Produces(MediaType.APPLICATION_JSON)
    public List<Owner> listUsersOwners(@PathParam("username") @Verify(User.class)
        String username,
        @Context Principal principal) {

        List<Owner> owners = new LinkedList<Owner>();
        User user = userService.findByLogin(username);
        if (user.isSuperAdmin()) {
            owners.addAll(ownerCurator.listAll());
        }
        else {
            for (Owner o : user.getOwners(SubResource.CONSUMERS, Access.CREATE)) {
                owners.add(o);
            }
        }
        return owners;
    }

}
