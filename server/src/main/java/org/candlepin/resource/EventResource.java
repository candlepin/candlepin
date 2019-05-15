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

import org.candlepin.audit.Event;

import com.google.inject.Inject;

import org.candlepin.common.exceptions.NotFoundException;
import org.xnap.commons.i18n.I18n;

import java.util.Collections;
import java.util.List;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import io.swagger.annotations.Authorization;

/**
 * Candlepin Events Resource
 */
@Path("/events")
@Api(value = "events", authorizations = { @Authorization("basic") })
public class EventResource {

    private I18n i18n;

    @Inject
    public EventResource(I18n i18n) {
        this.i18n = i18n;
    }

    /**
     * Retrieves a list of Events.
     *
     * @deprecated Event persistence/retrieval is being phased out. This endpoint currently returns an
     * empty list of events, and will be completely removed on the next major release.
     *
     * @return an empty list
     */
    @Deprecated
    @ApiOperation(
        notes = "Retrieves a list of Events. DEPRECATED: Event persistence/retrieval is being phased out. " +
        "This endpoint currently returns an empty list of events, and will be completely removed " +
        "on the next major release.",
        value = "listEvents")
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public List<Event> listEvents() {
        return Collections.emptyList();
    }

    /**
     * Retrieves a single Event.
     *
     * @param uuid an event uuid
     *
     * @deprecated Event persistence/retrieval is being phased out. This endpoint currently always
     * returns a 404 code, and will be removed on the next major release.
     *
     * @return Will not return anything. Instead, throws a {@link NotFoundException}
     */
    @Deprecated
    @ApiOperation(
        notes = "Retrieves a single Event. DEPRECATED: Event persistence/retrieval is being phased out. " +
        "This endpoint currently always returns a 400 code, and will be removed " +
        "on the next major release.",
        value = "getEvent")
    @ApiResponses({ @ApiResponse(
        code = 404,
        message = "This API endpoint is deprecated, and will be removed on the next major release") })
    @GET
    @Path("{uuid}")
    @Produces(MediaType.APPLICATION_JSON)
    public Event getEvent(@PathParam("uuid") String uuid) {
        throw new NotFoundException(i18n.tr("This API endpoint is deprecated, and will be" +
            " removed on the next major release.", uuid));
    }
}
