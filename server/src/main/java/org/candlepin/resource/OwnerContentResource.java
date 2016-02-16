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

import org.candlepin.common.exceptions.NotFoundException;
import org.candlepin.controller.PoolManager;
import org.candlepin.model.Content;
import org.candlepin.model.ContentCurator;
import org.candlepin.model.EnvironmentContent;
import org.candlepin.model.EnvironmentContentCurator;
import org.candlepin.model.Owner;
import org.candlepin.model.OwnerCurator;
import org.candlepin.model.Product;
import org.candlepin.model.ProductCurator;
import org.candlepin.service.UniqueIdGenerator;

import com.google.inject.Inject;

import org.xnap.commons.i18n.I18n;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

/**
 * OwnerContentResource
 *
 * Manage the content that exists in an organization.
 */
@Path("/owners/{owner_key}/content")
public class OwnerContentResource {

    private ContentCurator contentCurator;
    private I18n i18n;
    private UniqueIdGenerator idGenerator;
    private EnvironmentContentCurator envContentCurator;
    private OwnerCurator ownerCurator;
    private PoolManager poolManager;
    private ProductCurator productCurator;

    @Inject
    public OwnerContentResource(ContentCurator contentCurator, I18n i18n, UniqueIdGenerator idGenerator,
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

    /**
     * Retrieves list of Content
     *
     * @return a list of Content objects
     * @httpcode 200
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public List<Content> list(@PathParam("owner_key") String ownerKey) {
        Owner owner = this.getOwnerByKey(ownerKey);
        return contentCurator.listByOwner(owner);
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
    public Content getContent(@PathParam("owner_key") String ownerKey,
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
    private Content createContentImpl(Owner owner, Content content) {
        // TODO: check if arches have changed ??

        content.setOwner(owner);

        if (content.getId() == null || content.getId().trim().length() == 0) {
            content.setId(this.idGenerator.generateId());
            content = this.contentCurator.create(content);
        }
        else {
            Content lookedUp = this.contentCurator.lookupById(owner, content.getId());

            if (lookedUp != null) {
                content.setId(lookedUp.getId());
                content = this.contentCurator.merge(content);
            }
            else {
                content = this.contentCurator.create(content);
            }
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
    public Content createContent(@PathParam("owner_key") String ownerKey, Content content) {
        Owner owner = this.getOwnerByKey(ownerKey);
        return this.createContentImpl(owner, content);
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
    public List<Content> createBatchContent(@PathParam("owner_key") String ownerKey,
        List<Content> contents) {

        List<Content> result = new ArrayList<Content>();
        Owner owner = this.getOwnerByKey(ownerKey);

        for (Content content : contents) {
            result.add(this.createContentImpl(owner, content));
        }

        return result;
    }

    /**
     * Updates a Content
     *
     * @param contentId
     * @param content
     * @return a Content object
     */
    @PUT
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/{content_id}")
    public Content updateContent(@PathParam("owner_key") String ownerKey,
                                 @PathParam("content_id") String contentId,
                                 Content content) {

        Content lookedUp  = this.getContent(ownerKey, contentId);
        Owner owner = lookedUp.getOwner();

        // FIXME: needs arches handled as well?
        content.setId(contentId);
        content.setOwner(owner);
        content = this.contentCurator.createOrUpdate(content);

        // require regeneration of entitlement certificates of affected consumers
        List<Product> affectedProducts =
            this.productCurator.getProductsWithContent(owner, Arrays.asList(contentId));

        for (Product product : affectedProducts) {
            poolManager.regenerateCertificatesOf(
                owner, product.getId(), true
            );
        }

        return content;
    }

    /**
     * Deletes a Content
     *
     * @httpcode 200
     */
    @DELETE
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/{content_id}")
    public void remove(@PathParam("owner_key") String ownerKey,
                       @PathParam("content_id") String contentId) {

        Content nuke = this.getContent(ownerKey, contentId);
        Owner owner = nuke.getOwner();

        List<Product> affectedProducts =
            this.productCurator.getProductsWithContent(owner, Arrays.asList(contentId));

        this.contentCurator.delete(nuke);

        // Clean up any dangling environment content:
        for (EnvironmentContent ec : envContentCurator.lookupByContent(owner, contentId)) {
            envContentCurator.delete(ec);
        }

        // Regenerate affected products
        for (Product product : affectedProducts) {

            // PER-ORG PRODUCT VERSIONING TODO:
            // This should cause a new product version for the specified owner, rather than patching
            // the existing owner.
            poolManager.regenerateCertificatesOf(
                owner, product.getId(), true
            );
        }
    }
}
