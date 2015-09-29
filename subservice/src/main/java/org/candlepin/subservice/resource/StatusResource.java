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
package org.candlepin.subservice.resource;

import org.candlepin.subservice.servlet.SubserviceContextListener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

/**
 * Resource to check if subservice is alive,
 * can be replaced later to respond with a status similar to candlepin if needed.
 */
@Path("alive")
public class StatusResource {
    private static Logger log = LoggerFactory.getLogger(SubserviceContextListener.class);

    @GET
    @Produces({ MediaType.APPLICATION_JSON })
    public Boolean getStatus() {
        log.debug("getStatus");
        return true;
    }
}
