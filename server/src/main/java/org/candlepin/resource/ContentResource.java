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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import org.candlepin.controller.PoolManager;
import org.candlepin.exceptions.BadRequestException;
import org.candlepin.exceptions.NotFoundException;
import org.candlepin.model.Content;
import org.candlepin.model.ContentCurator;
import org.candlepin.model.EnvironmentContent;
import org.candlepin.model.EnvironmentContentCurator;
import org.candlepin.service.ProductServiceAdapter;
import org.candlepin.service.UniqueIdGenerator;
import org.xnap.commons.i18n.I18n;

import com.google.inject.Inject;
/**
 * ContentResource
 */

@Path("/content")
public class ContentResource {

    private ContentCurator contentCurator;
    private I18n i18n;
    private UniqueIdGenerator idGenerator;
    private EnvironmentContentCurator envContentCurator;
    private PoolManager poolManager;
    private ProductServiceAdapter productAdapter;

    @Inject
    public ContentResource(ContentCurator contentCurator, I18n i18n,
        UniqueIdGenerator idGenerator, EnvironmentContentCurator envContentCurator,
        PoolManager poolManager, ProductServiceAdapter productAdapter) {
        this.i18n = i18n;
        this.contentCurator = contentCurator;
        this.idGenerator = idGenerator;
        this.envContentCurator = envContentCurator;
        this.poolManager = poolManager;
        this.productAdapter = productAdapter;
    }

    /**
     * Retrieves list of Content
     *
     * @return a list of Content objects
     * @httpcode 200
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public List<Content> list() {
        return contentCurator.listAll();
    }

    /**
     * Retrieves a single Content
     * <p>
     * <pre>
     * {
     *   "id" : "database_id",
     *   "type" : "yum",
     *   "label" : "content_label",
     *   "name" : "content_name",
     *   "vendor" : "test-vendor",
     *   "contentUrl" : "/foo/path/always",
     *   "requiredTags" : "TAG1,TAG2",
     *   "releaseVer" : null,
     *   "gpgUrl" : "/foo/path/always/gpg",
     *   "metadataExpire" : null,
     *   "modifiedProductIds" : [ ],
     *   "arches" : null,
     *   "created" : [date],
     *   "updated" : [date]
     * }
     * </pre>
     *
     * @return a Content object
     * @httpcode 400
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/{content_id}")
    public Content getContent(@PathParam("content_id") String contentId) {
        Content content = contentCurator.find(contentId);

        if (content == null) {
            throw new BadRequestException(
                i18n.tr("Content with id {0} could not be found.", contentId));
        }

        return content;
    }

    /**
     * Creates a Content
     *
     * @return a Content object
     * @httpcode 200
     */
    @POST
    @Produces(MediaType.APPLICATION_JSON)
    public Content createContent(Content content) {
        // FIXME: check if arches have changed
        if (content.getId() == null || content.getId().trim().length() == 0) {
            content.setId(idGenerator.generateId());
            return contentCurator.create(content);
        }

        Content lookedUp  = contentCurator.find(content.getId());
        if (lookedUp != null) {
            return lookedUp;
        }

        return contentCurator.create(content);
    }

    /**
     * Creates Contents in bulk
     *
     * @return a list of Content objects
     * @httpcode 200
     */
    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/batch")
    public List<Content> createBatchContent(List<Content> contents) {
        List<Content> result = new ArrayList<Content>();
        for (Content content : contents) {
            Content lookedUp = contentCurator.find(content.getId());
            if (lookedUp != null) {
                content.setId(lookedUp.getId());
                result.add(contentCurator.merge(content));
            }
            else {
                result.add(contentCurator.create(content));
            }
        }
        return result;
    }

    /**
     * Updates a Content
     *
     * @param contentId
     * @param changes
     * @return a Content object
     */
    @PUT
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/{content_id}")
    public Content updateContent(@PathParam("content_id") String contentId,
            Content changes) {
        Content lookedUp  = contentCurator.find(contentId);
        if (lookedUp == null) {
            throw new NotFoundException(
                i18n.tr("Content with id {0} could not be found.", contentId));
        }

        // FIXME: needs arches handled as well?
        changes.setId(contentId);
        Content updated = contentCurator.createOrUpdate(changes);
        // require regeneration of entitlement certificates of affected consumers
        Set<String> affectedProducts =
            productAdapter.getProductsWithContent(setFrom(contentId));
        for (String productId : affectedProducts) {
            poolManager.regenerateCertificatesOf(productId, true);
        }

        return updated;
    }

    private <T> Set<T> setFrom(T anElement) {
        Set<T> toReturn = new HashSet<T>();
        toReturn.add(anElement);
        return toReturn;
    }

    /**
     * Deletes a Content
     *
     * @httpcode 200
     */
    @DELETE
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/{content_id}")
    public void remove(@PathParam("content_id") String cid) {
        Set<String> affectedProducts = productAdapter.getProductsWithContent(setFrom(cid));
        Content nuke = getContent(cid);
        contentCurator.delete(nuke);

        // Clean up any dangling environment content:
        for (EnvironmentContent ec : envContentCurator.lookupByContent(cid)) {
            envContentCurator.delete(ec);
        }
        // Regenerate affected products
        for (String productId : affectedProducts) {
            poolManager.regenerateCertificatesOf(productId, true);
        }
    }
}
