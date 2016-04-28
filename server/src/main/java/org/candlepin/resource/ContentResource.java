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
/**
 * ContentResource
 */

@Path("/content")
@Api("content")
public class ContentResource {

    private ContentCurator contentCurator;
    private I18n i18n;
    private UniqueIdGenerator idGenerator;
    private EnvironmentContentCurator envContentCurator;
    private PoolManager poolManager;
    private ProductCurator productCurator;
    private OwnerCurator ownerCurator;

    @Inject
    public ContentResource(ContentCurator contentCurator, I18n i18n, UniqueIdGenerator idGenerator,
        EnvironmentContentCurator envContentCurator, PoolManager poolManager,
        ProductCurator productCurator, OwnerCurator ownerCurator) {

        this.i18n = i18n;
        this.contentCurator = contentCurator;
        this.idGenerator = idGenerator;
        this.envContentCurator = envContentCurator;
        this.poolManager = poolManager;
        this.productCurator = productCurator;
        this.ownerCurator = ownerCurator;
    }

    @ApiOperation(notes = "Retrieves list of Content", value = "list")
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public List<Content> list() {
        return contentCurator.listAll();
    }

    @ApiOperation(notes = "Retrieves a single Content", value = "getContent")
    @ApiResponses({ @ApiResponse(code = 400, message = "") })
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/{content_uuid}")
    public Content getContent(@PathParam("content_uuid") String contentUuid) {
        Content content = this.contentCurator.lookupByUuid(contentUuid);

        if (content == null) {
            throw new NotFoundException(
                i18n.tr("Content with UUID \"{0}\" could not be found.", contentUuid)
            );
        }

        return content;
    }

    @ApiOperation(notes = "Creates a Content", value = "createContent")
    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public Content createContent(Content content) {
        throw new BadRequestException(this.i18n.tr(
            "Organization-agnostic content write operations are not supported."
        ));
    }

    @ApiOperation(notes = "Creates Contents in bulk", value = "createBatchContent")
    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    @Path("/batch")
    public List<Content> createBatchContent(List<Content> contents) {
        throw new BadRequestException(this.i18n.tr(
            "Organization-agnostic content write operations are not supported."
        ));
    }

    @ApiOperation(notes = "Updates a Content", value = "updateContent")
    @PUT
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    @Path("/{content_uuid}")
    public Content updateContent(@PathParam("content_uuid") String contentUuid, Content changes) {
        throw new BadRequestException(this.i18n.tr(
            "Organization-agnostic content write operations are not supported."
        ));
    }

    @ApiOperation(notes = "Deletes a Content", value = "remove")
    @DELETE
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/{content_uuid}")
    public void remove(@PathParam("content_uuid") String contentUuid) {
        throw new BadRequestException(this.i18n.tr(
            "Organization-agnostic content write operations are not supported."
        ));
    }
}
