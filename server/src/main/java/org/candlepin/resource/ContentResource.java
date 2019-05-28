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
import org.candlepin.controller.PoolManager;
import org.candlepin.dto.ModelTranslator;
import org.candlepin.dto.api.v1.ContentDTO;
import org.candlepin.model.CandlepinQuery;
import org.candlepin.model.Content;
import org.candlepin.model.ContentCurator;
import org.candlepin.model.EnvironmentContentCurator;
import org.candlepin.model.OwnerCurator;
import org.candlepin.model.ProductCurator;
import org.candlepin.service.UniqueIdGenerator;

import com.google.inject.Inject;

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
import io.swagger.annotations.Authorization;

/**
 * ContentResource
 */
@Path("/content")
@Api(value = "content", authorizations = { @Authorization("basic") })
public class ContentResource {

    private ContentCurator contentCurator;
    private I18n i18n;
    private UniqueIdGenerator idGenerator;
    private EnvironmentContentCurator envContentCurator;
    private PoolManager poolManager;
    private ProductCurator productCurator;
    private OwnerCurator ownerCurator;
    private ModelTranslator modelTranslator;

    @Inject
    public ContentResource(ContentCurator contentCurator, I18n i18n, UniqueIdGenerator idGenerator,
        EnvironmentContentCurator envContentCurator, PoolManager poolManager,
        ProductCurator productCurator, OwnerCurator ownerCurator, ModelTranslator modelTranslator) {

        this.i18n = i18n;
        this.contentCurator = contentCurator;
        this.idGenerator = idGenerator;
        this.envContentCurator = envContentCurator;
        this.poolManager = poolManager;
        this.productCurator = productCurator;
        this.ownerCurator = ownerCurator;
        this.modelTranslator = modelTranslator;
    }

    @ApiOperation(notes = "Retrieves list of Content", value = "list", response = ContentDTO.class,
        responseContainer = "list", nickname = "listContent")
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public CandlepinQuery<ContentDTO> list() {
        return this.modelTranslator.translateQuery(this.contentCurator.listAll(), ContentDTO.class);
    }

    @ApiOperation(notes = "Retrieves a single Content", value = "getContent")
    @ApiResponses({ @ApiResponse(code = 400, message = "") })
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/{content_uuid}")
    public ContentDTO getContent(@PathParam("content_uuid") String contentUuid) {
        Content content = this.contentCurator.getByUuid(contentUuid);

        if (content == null) {
            throw new NotFoundException(
                i18n.tr("Content with UUID \"{0}\" could not be found.", contentUuid));
        }

        return this.modelTranslator.translate(content, ContentDTO.class);
    }

    @ApiOperation(notes = "Creates a Content", value = "createContent")
    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public ContentDTO createContent(ContentDTO content) {
        throw new BadRequestException(this.i18n.tr(
            "Organization-agnostic content write operations are not supported."));
    }

    @ApiOperation(notes = "Creates Contents in bulk", value = "createBatchContent")
    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    @Path("/batch")
    public Iterable<ContentDTO> createBatchContent(List<ContentDTO> contents) {
        throw new BadRequestException(this.i18n.tr(
            "Organization-agnostic content write operations are not supported."));
    }

    @ApiOperation(notes = "Updates a Content", value = "updateContent")
    @PUT
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    @Path("/{content_uuid}")
    public ContentDTO updateContent(@PathParam("content_uuid") String contentUuid, ContentDTO changes) {
        throw new BadRequestException(this.i18n.tr(
            "Organization-agnostic content write operations are not supported."));
    }

    @ApiOperation(notes = "Deletes a Content", value = "remove", nickname = "removeContent")
    @DELETE
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/{content_uuid}")
    public void remove(@PathParam("content_uuid") String contentUuid) {
        throw new BadRequestException(this.i18n.tr(
            "Organization-agnostic content write operations are not supported."));
    }
}
