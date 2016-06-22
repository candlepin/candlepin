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

import org.candlepin.auth.Verify;
import org.candlepin.common.exceptions.ForbiddenException;
import org.candlepin.common.exceptions.NotFoundException;
import org.candlepin.controller.ContentManager;
import org.candlepin.controller.PoolManager;
import org.candlepin.model.Content;
import org.candlepin.model.ContentCurator;
import org.candlepin.model.EnvironmentContentCurator;
import org.candlepin.model.Owner;
import org.candlepin.model.OwnerContentCurator;
import org.candlepin.model.OwnerCurator;
import org.candlepin.model.ProductCurator;
import org.candlepin.model.dto.ContentData;
import org.candlepin.service.UniqueIdGenerator;

import com.google.inject.Inject;
import com.google.inject.persist.Transactional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xnap.commons.i18n.I18n;

import java.util.Collection;
import java.util.List;
import java.util.LinkedList;

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
 * OwnerContentResource
 *
 * Manage the content that exists in an organization.
 */
@Path("/owners/{owner_key}/content")
@Api("owners")
public class OwnerContentResource {
    private static Logger log = LoggerFactory.getLogger(OwnerContentResource.class);

    private ContentCurator contentCurator;
    private ContentManager contentManager;
    private EnvironmentContentCurator envContentCurator;
    private I18n i18n;
    private OwnerCurator ownerCurator;
    private OwnerContentCurator ownerContentCurator;
    private PoolManager poolManager;
    private ProductCurator productCurator;
    private UniqueIdGenerator idGenerator;

    @Inject
    public OwnerContentResource(ContentCurator contentCurator, ContentManager contentManager,
        EnvironmentContentCurator envContentCurator, I18n i18n, OwnerCurator ownerCurator,
        OwnerContentCurator ownerContentCurator, PoolManager poolManager, ProductCurator productCurator,
        UniqueIdGenerator idGenerator) {

        this.contentCurator = contentCurator;
        this.contentManager = contentManager;
        this.envContentCurator = envContentCurator;
        this.i18n = i18n;
        this.ownerCurator = ownerCurator;
        this.ownerContentCurator = ownerContentCurator;
        this.poolManager = poolManager;
        this.productCurator = productCurator;
        this.idGenerator = idGenerator;
    }

    /**
     * Retrieves an Owner instance for the owner with the specified key/account. If a matching owner
     * could not be found, this method throws an exception.
     *
     * @param key
     *  The key for the owner to retrieve
     *
     * @throws NotFoundException
     *  if an owner could not be found for the specified key.
     *
     * @return
     *  the Owner instance for the owner with the specified key.
     */
    protected Owner getOwnerByKey(String key) {
        Owner owner = this.ownerCurator.lookupByKey(key);

        if (owner == null) {
            throw new NotFoundException(i18n.tr("Owner with key \"{0}\" was not found.", key));
        }

        return owner;
    }

    @ApiOperation(notes = "Retrieves list of Content", value = "list")
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public List<Content> list(@Verify(Owner.class) @PathParam("owner_key") String ownerKey) {
        Owner owner = this.getOwnerByKey(ownerKey);
        return contentCurator.listByOwner(owner);
    }

    @ApiOperation(notes = "Retrieves a single Content", value = "getContent")
    @ApiResponses({ @ApiResponse(code = 400, message = "") })
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/{content_id}")
    public Content getContent(
        @Verify(Owner.class) @PathParam("owner_key") String ownerKey,
        @PathParam("content_id") String contentId) {

        Owner owner = this.getOwnerByKey(ownerKey);
        Content content = this.contentCurator.lookupById(owner, contentId);

        if (content == null) {
            throw new NotFoundException(
                i18n.tr("Content with ID \"{0}\" could not be found.", contentId)
            );
        }

        return content;
    }

    /**
     * Creates or merges the given Content object.
     *
     * @param owner
     *  The owner for which to create the new content
     *
     * @param content
     *  The content to create or merge
     *
     * @return
     *  the newly created and/or merged Content object.
     */
    private Content createContentImpl(Owner owner, ContentData content) {
        // TODO: check if arches have changed ??

        Content entity = null;

        if (content.getId() == null || content.getId().trim().length() == 0) {
            content.setId(this.idGenerator.generateId());

            entity = this.contentManager.createContent(content, owner);
        }
        else {
            Content existing = this.ownerContentCurator.getContentById(owner, content.getId());

            if (existing != null) {
                entity = this.contentManager.updateContent(existing, content, owner, true);
            }
            else {
                entity = this.contentManager.createContent(content, owner);
            }
        }

        return entity;
    }

    @ApiOperation(notes = "Creates a Content", value = "createContent")
    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public ContentData createContent(@PathParam("owner_key") String ownerKey,
        ContentData content) {

        Owner owner = this.getOwnerByKey(ownerKey);
        Content entity = this.createContentImpl(owner, content);

        return entity.toDTO();
    }

    @ApiOperation(notes = "Creates Contents in bulk", value = "createBatchContent")
    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    @Path("/batch")
    @Transactional
    public Collection<ContentData> createBatchContent(@PathParam("owner_key") String ownerKey,
        List<ContentData> contents) {

        Collection<ContentData> result = new LinkedList<ContentData>();
        Owner owner = this.getOwnerByKey(ownerKey);

        for (ContentData content : contents) {
            Content entity = this.createContentImpl(owner, content);

            result.add(entity.toDTO());
        }

        return result;
    }

    @ApiOperation(notes = "Updates a Content", value = "updateContent")
    @PUT
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    @Path("/{content_id}")
    public ContentData updateContent(@PathParam("owner_key") String ownerKey,
        @PathParam("content_id") String contentId,
        ContentData content) {

        Owner owner = this.getOwnerByKey(ownerKey);
        Content existing  = this.getContent(ownerKey, contentId);

        if (existing.isLocked()) {
            throw new ForbiddenException(i18n.tr("content \"{0}\" is locked", content.getId()));
        }

        existing = this.contentManager.updateContent(existing, content, owner, true);
        return existing.toDTO();
    }

    @ApiOperation(notes = "Deletes a Content", value = "remove")
    @DELETE
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/{content_id}")
    public void remove(@PathParam("owner_key") String ownerKey,
        @PathParam("content_id") String contentId) {

        Owner owner = this.getOwnerByKey(ownerKey);
        Content content = this.getContent(ownerKey, contentId);

        if (content.isLocked()) {
            throw new ForbiddenException(i18n.tr("content \"{0}\" is locked", content.getId()));
        }

        this.contentManager.removeContent(content, owner, true);
    }
}
