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
import org.candlepin.dto.api.v1.DeletedConsumerDTO;
import org.candlepin.model.CandlepinQuery;
import org.candlepin.model.DeletedConsumerCurator;
import org.candlepin.resource.util.ResourceDateParser;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import io.swagger.annotations.Authorization;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;

/**
 * DeletedConsumerResource
 */
@Component
@Transactional
@Path("/deleted_consumers")
@Api(value = "deleted_consumers", authorizations = { @Authorization("basic") })
public class DeletedConsumerResource {
    private DeletedConsumerCurator deletedConsumerCurator;
    private ModelTranslator translator;

    //@Inject
    @Autowired
    public DeletedConsumerResource(DeletedConsumerCurator deletedConsumerCurator,
        ModelTranslator translator) {

        this.deletedConsumerCurator = deletedConsumerCurator;
        this.translator = translator;
    }

    @ApiOperation(
        notes = "Retrieves a list of Deleted Consumers By deletion date or all. " +
        "List returned is the deleted Consumers.",
        value = "listByDate", response = DeletedConsumerDTO.class, responseContainer = "list")
    @ApiResponses({ @ApiResponse(code = 400, message = ""), @ApiResponse(code = 404, message = "") })
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public CandlepinQuery<DeletedConsumerDTO> listByDate(@QueryParam("date") String dateStr) {
        return this.translator.translateQuery(dateStr != null ?
            this.deletedConsumerCurator.findByDate(ResourceDateParser.parseDateString(dateStr)) :
            this.deletedConsumerCurator.listAll(),
            DeletedConsumerDTO.class);
    }
}
