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

import org.candlepin.auth.Principal;
import org.candlepin.auth.SystemPrincipal;
import org.candlepin.auth.interceptor.SecurityHole;
import org.candlepin.model.User;
import org.candlepin.model.UserCurator;
import org.candlepin.service.UserServiceAdapter;
import org.candlepin.service.impl.DefaultUserServiceAdapter;

import com.google.inject.Inject;

import org.jboss.resteasy.spi.ResteasyProviderFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

/**
 * Candlepin server administration REST calls.
 */
@Path("/admin")
public class AdminResource {

    private static Logger log = LoggerFactory.getLogger(AdminResource.class);

    private UserServiceAdapter userService;
    private UserCurator userCurator;

    @Inject
    public AdminResource(UserServiceAdapter userService, UserCurator userCurator) {
        this.userService = userService;
        this.userCurator = userCurator;
    }

    /**
     * Initializes the Candlepin database
     * <p>
     * Currently this just creates the admin user for standalone deployments using the
     * default user service adapter. It must be called once after candlepin is installed,
     * repeat calls are not required, but will be harmless.
     * <p>
     * The String returned is the description if the db was or already is initialized.
     *
     * @return a String object
     * @httpcode 200
     */
    @GET
    @Produces({MediaType.TEXT_PLAIN})
    @Path("init")
    @SecurityHole(noAuth = true)
    public String initialize() {
        log.debug("Called initialize()");

        log.info("Initializing Candlepin database.");

        // All we really need to do here is create the initial admin user, if we're using
        // the default user service adapter, and no other users exist already:
        if (userService instanceof DefaultUserServiceAdapter &&
            userCurator.getUserCount() == 0) {
            // Push the system principal so we can create all these entries as a
            // superuser:
            ResteasyProviderFactory.pushContext(Principal.class, new SystemPrincipal());

            log.info("Creating default super admin.");
            User defaultAdmin = new User("admin", "admin", true);
            userService.createUser(defaultAdmin);
            return "Initialized!";
        }
        else {
            // Any other user service adapter and we really have nothing to do:
            return "Already initialized.";
        }
    }
}
