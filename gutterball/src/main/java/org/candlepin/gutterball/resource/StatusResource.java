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
package org.candlepin.gutterball.resource;

import org.candlepin.common.auth.SecurityHole;
import org.candlepin.gutterball.model.Status;

import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

/**
 * Resource to respond with Gutterball's status.
 */
@Path("status")
public class StatusResource {

    private Status status;

    @Inject
    public StatusResource(Status status) {
        this.status = status;
    }

    @GET
    @Produces({ MediaType.APPLICATION_JSON })
    @SecurityHole(anon = true)
    public Status getStatus() {
        return status;
    }
}
