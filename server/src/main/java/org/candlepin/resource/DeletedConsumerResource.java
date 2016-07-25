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

import org.candlepin.model.DeletedConsumer;
import org.candlepin.model.DeletedConsumerCurator;
import org.candlepin.model.ResultIterator;
import org.candlepin.resource.util.ResourceDateParser;
import org.candlepin.resteasy.IterableStreamingOutputFactory;

import com.google.inject.Inject;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;



/**
 * DeletedConsumerResource
 */
@Path("/deleted_consumers")
@Api("deleted_consumers")
public class DeletedConsumerResource {
    private DeletedConsumerCurator deletedConsumerCurator;
    private IterableStreamingOutputFactory isoFactory;

    @Inject
    public DeletedConsumerResource(DeletedConsumerCurator deletedConsumerCurator,
        IterableStreamingOutputFactory isoFactory) {

        this.deletedConsumerCurator = deletedConsumerCurator;
        this.isoFactory = isoFactory;
    }

    @ApiOperation(
        notes = "Retrieves a list of Deleted Consumers By deletion date or all. " +
        "List returned is the deleted Consumers.",
        value = "listByDate")
    @ApiResponses({ @ApiResponse(code = 400, message = ""), @ApiResponse(code = 404, message = "") })
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response listByDate(@QueryParam("date") String dateStr) {
        ResultIterator<DeletedConsumer> iterator = dateStr != null ?
            this.deletedConsumerCurator.findByDate(ResourceDateParser.parseDateString(dateStr)).iterate() :
            this.deletedConsumerCurator.listAll().iterate();

        return Response.ok(this.isoFactory.create(iterator)).build();
    }
}
