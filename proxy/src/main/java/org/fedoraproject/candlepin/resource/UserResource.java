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


import java.util.LinkedList;
import java.util.List;

import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;

import org.fedoraproject.candlepin.auth.Access;
import org.fedoraproject.candlepin.auth.Principal;
import org.fedoraproject.candlepin.auth.interceptor.Verify;
import org.fedoraproject.candlepin.exceptions.ConflictException;
import org.fedoraproject.candlepin.exceptions.GoneException;
import org.fedoraproject.candlepin.model.Owner;
import org.fedoraproject.candlepin.model.OwnerCurator;
import org.fedoraproject.candlepin.model.User;
import org.fedoraproject.candlepin.service.UserServiceAdapter;
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

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public List<User> list() {
        return userService.listUsers();
    }

    @GET
    @Path("/{username}")
    @Produces(MediaType.APPLICATION_JSON)
    public User getUserInfo(@PathParam("username")
        @Verify(User.class) String username) {
        return userService.findByLogin(username);
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public User createUser(User user) {
        if (userService.findByLogin(user.getUsername()) != null) {
            throw new ConflictException("user " + user.getUsername() + " already exists");
        }
        return userService.createUser(user);
    }

    @DELETE
    @Path("/{username}")
    @Produces(MediaType.APPLICATION_JSON)
    public void deleteUser(@PathParam("username") String username) {
        User user = userService.findByLogin(username);
        if (user == null) {
            throw new GoneException("user " + username + " not found");
        }
        else {
            userService.deleteUser(user);
        }
    }

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
            owners.addAll(user.getOwners(Access.ALL));
        }
        return owners;
    }

}
