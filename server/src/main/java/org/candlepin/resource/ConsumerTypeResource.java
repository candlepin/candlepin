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

import org.candlepin.common.exceptions.BadRequestException;
import org.candlepin.common.exceptions.NotFoundException;
import org.candlepin.model.ConsumerType;
import org.candlepin.model.ConsumerTypeCurator;

import com.google.inject.Inject;

import org.jboss.resteasy.annotations.providers.jaxb.Wrapped;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xnap.commons.i18n.I18n;

import java.util.List;

import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;

/**
 * Access Path for consumer types
 */
@Path("/consumertypes")
@Api("consumertypes")
public class ConsumerTypeResource {
    private static Logger log = LoggerFactory.getLogger(ConsumerTypeResource.class);
    private ConsumerTypeCurator consumerTypeCurator;
    private I18n i18n;

    @Inject
    public ConsumerTypeResource(ConsumerTypeCurator consumerTypeCurator, I18n i18n) {
        this.consumerTypeCurator = consumerTypeCurator;
        this.i18n = i18n;
    }

    @ApiOperation(notes = "Retrieves a list of Consumer Types", value = "list")
    @GET
    @Produces({MediaType.APPLICATION_JSON })
    @Wrapped(element = "consumertypes")
    public List<ConsumerType> list() {
        return consumerTypeCurator.listAll();
    }

    @ApiOperation(notes = "Retrieves a single Consumer Type", value = "getConsumerType")
    @ApiResponses({ @ApiResponse(code = 404, message = "") })
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/{id}")
    public ConsumerType getConsumerType(@PathParam("id") String id) {
        ConsumerType toReturn = consumerTypeCurator.find(id);

        if (toReturn != null) {
            return toReturn;
        }

        throw new NotFoundException(
            i18n.tr("Unit type with id ''{0}'' could not be found.", id)
        );
    }

    @ApiOperation(notes = "Creates a Consumer Type", value = "create")
    @ApiResponses({ @ApiResponse(code = 400, message = "") })
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public ConsumerType create(ConsumerType in) throws BadRequestException {
        try {
            ConsumerType toReturn = consumerTypeCurator.create(in);
            return toReturn;
        }
        catch (Exception e) {
            log.error("Problem creating unit type:", e);
            throw new BadRequestException(
                i18n.tr("Problem creating unit type: {0}", in));
        }
    }

    @ApiOperation(notes = "Updates a Consumer Type", value = "update")
    @ApiResponses({ @ApiResponse(code = 400, message = "") })
    @PUT
    @Path("/{id}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public ConsumerType update(ConsumerType in) throws BadRequestException {
        ConsumerType type = consumerTypeCurator.find(in.getId());

        if (type == null) {
            throw new BadRequestException(
                i18n.tr("Unit type with label {0} could not be found.", in.getId()));
        }

        consumerTypeCurator.merge(in);
        return in;
    }

    @ApiOperation(notes = "Removes a Consumer Type", value = "deleteConsumerType")
    @DELETE
    @Path("/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    public void deleteConsumerType(@PathParam("id") String id) {
        ConsumerType type = consumerTypeCurator.find(id);

        if (type == null) {
            throw new BadRequestException(
                i18n.tr("Unit type with id {0} could not be found.", id));
        }

        consumerTypeCurator.delete(type);
    }
}
