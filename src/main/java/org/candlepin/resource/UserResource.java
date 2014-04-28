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

import org.candlepin.auth.Access;
import org.candlepin.auth.Principal;
import org.candlepin.auth.interceptor.Verify;
import org.candlepin.exceptions.ConflictException;
import org.candlepin.exceptions.GoneException;
import org.candlepin.exceptions.NotFoundException;
import org.candlepin.model.Owner;
import org.candlepin.model.OwnerCurator;
import org.candlepin.model.Role;
import org.candlepin.model.User;
import org.candlepin.service.UserServiceAdapter;
import org.xnap.commons.i18n.I18n;

import com.google.inject.Inject;

/**
 * UserResource
 */
@Path("/users")
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

    /**
     * Retrieves a list of Users
     *
     * @return a list of User objects
     * @httpcode 200
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public List<User> list() {
        return userService.listUsers();
    }

    /**
     * Retrieves a single User
     * <p>
     * <pre>
     * {
     *   "id" : "database_id",
     *   "username" : "user_name",
     *   "hashedPassword" : "05557a2aaec7cb676df574d2eb080691949a6752",
     *   "superAdmin" : false,
     *   "created" : [date],
     *   "updated" : [date]
     * }
     * </pre>
     *
     * @return a User object
     * @httpcode 200
     */
    @GET
    @Path("/{username}")
    @Produces(MediaType.APPLICATION_JSON)
    public User getUserInfo(@PathParam("username")
        @Verify(User.class) String username) {
        return userService.findByLogin(username);
    }

    /**
     * Retrieves a list of Roles by User
     *
     * @return a list of Role objects
     * @httpcode 200
     */
    /*
     * getUserRoles will only return roles for one user. If you want a
     * full view of a role, use /roles/ instead.
     */
    @GET
    @Path("/{username}/roles")
    @Produces(MediaType.APPLICATION_JSON)
    public List<Role> getUserRoles(@PathParam("username")
        @Verify(User.class) String username) {
        User myUser = userService.findByLogin(username);
        List<Role> roles = new LinkedList<Role>(myUser.getRoles());
        Set<User> s = new HashSet<User>();
        s.add(myUser);
        for (Role r : roles) {
            r.setUsers(s);
        }
        return roles;
    }

    /**
     * Creates a User
     *
     * @return a User object
     * @httpcode 200
     */
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public User createUser(User user) {
        if (userService.findByLogin(user.getUsername()) != null) {
            throw new ConflictException("user " + user.getUsername() + " already exists");
        }
        return userService.createUser(user);
    }

    /**
     * Updates a User
     *
     * @return a User object
     * @httpcode 404
     * @httpcode 200
     */
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


    /**
     * Removes a User
     *
     * @httpcode 410
     * @httpcode 200
     */
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

    /**
     * Retrieve a list of Owners by User
     * <p>
     * Owners for which this User has admin rights.
     *
     * @return a list of Owner objects
     * @httpcode 200
     */
    // TODO: should probably accept an access level query param someday
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
            for (Owner o : user.getOwners(Access.ALL)) {
                owners.add(o);
            }
        }
        return owners;
    }

}
