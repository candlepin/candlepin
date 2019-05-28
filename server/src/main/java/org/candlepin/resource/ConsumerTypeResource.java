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

import org.candlepin.dto.ModelTranslator;
import org.candlepin.dto.api.v1.ConsumerTypeDTO;
import org.candlepin.common.exceptions.BadRequestException;
import org.candlepin.common.exceptions.NotFoundException;
import org.candlepin.model.CandlepinQuery;
import org.candlepin.model.ConsumerType;
import org.candlepin.model.ConsumerTypeCurator;

import com.google.inject.Inject;

import org.jboss.resteasy.annotations.providers.jaxb.Wrapped;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xnap.commons.i18n.I18n;

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
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import io.swagger.annotations.Authorization;



/**
 * Access Path for consumer types
 */
@Path("/consumertypes")
@Api(value = "consumertypes", authorizations = { @Authorization("basic") })
public class ConsumerTypeResource {
    private static Logger log = LoggerFactory.getLogger(ConsumerTypeResource.class);

    private ConsumerTypeCurator consumerTypeCurator;
    private I18n i18n;
    private ModelTranslator translator;

    @Inject
    public ConsumerTypeResource(ConsumerTypeCurator consumerTypeCurator, I18n i18n,
        ModelTranslator translator) {

        this.consumerTypeCurator = consumerTypeCurator;
        this.i18n = i18n;
        this.translator = translator;
    }

    /**
     * Populates the specified entity with data from the provided DTO.
     *
     * @param entity
     *  The entity instance to populate
     *
     * @param dto
     *  The DTO containing the data with which to populate the entity
     *
     * @throws IllegalArgumentException
     *  if either entity or dto are null
     */
    protected void populateEntity(ConsumerType entity, ConsumerTypeDTO dto) {
        if (entity == null) {
            throw new IllegalArgumentException("entity is null");
        }

        if (dto == null) {
            throw new IllegalArgumentException("dto is null");
        }

        if (dto.getLabel() != null) {
            entity.setLabel(dto.getLabel());
        }

        if (dto.isManifest() != null) {
            entity.setManifest(dto.isManifest());
        }
    }

    @ApiOperation(notes = "Retrieves a list of Consumer Types", value = "listConsumerType",
        nickname = "listConsumerType", response = ConsumerTypeDTO.class, responseContainer = "list")
    @GET
    @Produces({ MediaType.APPLICATION_JSON })
    @Wrapped(element = "consumertypes")
    public CandlepinQuery<ConsumerTypeDTO> list() {
        CandlepinQuery<ConsumerType> query = this.consumerTypeCurator.listAll();
        return this.translator.translateQuery(query, ConsumerTypeDTO.class);
    }

    @ApiOperation(notes = "Retrieves a single Consumer Type", value = "getConsumerType")
    @ApiResponses({ @ApiResponse(code = 404, message = "") })
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/{id}")
    public ConsumerTypeDTO getConsumerType(@PathParam("id") String id) {
        ConsumerType type = consumerTypeCurator.get(id);

        if (type == null) {
            throw new NotFoundException(i18n.tr("Unit type with id \"{0}\" could not be found.", id));
        }

        return this.translator.translate(type, ConsumerTypeDTO.class);
    }

    @ApiOperation(notes = "Creates a Consumer Type", value = "createConsumerType",
        nickname = "createConsumerType")
    @ApiResponses({ @ApiResponse(code = 400, message = "") })
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public ConsumerTypeDTO create(
        @ApiParam(name = "consumerType", required = true) ConsumerTypeDTO dto) throws BadRequestException {
        try {
            ConsumerType type = new ConsumerType();

            this.populateEntity(type, dto);
            type = consumerTypeCurator.create(type);
            return this.translator.translate(type, ConsumerTypeDTO.class);
        }
        catch (Exception e) {
            log.error("Problem creating unit type: ", e);
            throw new BadRequestException(i18n.tr("Problem creating unit type: {0}", dto));
        }
    }

    @ApiOperation(notes = "Updates a Consumer Type", value = "updateConsumerType",
        nickname = "updateConsumerType")
    @ApiResponses({ @ApiResponse(code = 400, message = "") })
    @PUT
    @Path("/{id}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public ConsumerTypeDTO update(
        @ApiParam(name = "consumerType", required = true) ConsumerTypeDTO dto) throws BadRequestException {
        ConsumerType type = consumerTypeCurator.get(dto.getId());

        if (type == null) {
            throw new NotFoundException(i18n.tr("Unit type with label {0} could not be found.", dto.getId()));
        }

        this.populateEntity(type, dto);
        type = consumerTypeCurator.merge(type);
        return this.translator.translate(type, ConsumerTypeDTO.class);
    }

    @ApiOperation(notes = "Removes a Consumer Type", value = "deleteConsumerType")
    @DELETE
    @Path("/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    public void deleteConsumerType(@PathParam("id") String id) {
        ConsumerType type = consumerTypeCurator.get(id);

        if (type == null) {
            throw new NotFoundException(i18n.tr("Unit type with id {0} could not be found.", id));
        }

        consumerTypeCurator.delete(type);
    }
}
