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

import org.candlepin.audit.EventSink;
import org.candlepin.auth.Principal;
import org.candlepin.auth.SystemPrincipal;
import org.candlepin.common.auth.SecurityHole;
import org.candlepin.dto.api.v1.QueueStatus;
import org.candlepin.model.User;
import org.candlepin.model.UserCurator;
import org.candlepin.service.UserServiceAdapter;
import org.candlepin.service.impl.DefaultUserServiceAdapter;

import com.google.inject.Inject;

import org.jboss.resteasy.core.ResteasyContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Candlepin server administration REST calls.
 */
public class AdminResource implements AdminApi {

    private static Logger log = LoggerFactory.getLogger(AdminResource.class);

    private UserServiceAdapter userService;
    private UserCurator userCurator;
    private EventSink sink;

    @Inject
    public AdminResource(UserServiceAdapter userService, UserCurator userCurator,
        EventSink dispatcher) {
        this.userService = userService;
        this.userCurator = userCurator;
        this.sink = dispatcher;
    }

    @Override
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
            ResteasyContext.pushContext(Principal.class, new SystemPrincipal());

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

    @Override
    public List<QueueStatus> getQueueStats() {
        return sink.getQueueInfo();
    }
}
