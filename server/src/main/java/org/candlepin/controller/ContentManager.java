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
package org.candlepin.controller;

import org.candlepin.model.Content;
import org.candlepin.model.ContentCurator;
import org.candlepin.model.Owner;
import org.candlepin.model.OwnerContentCurator;
import org.candlepin.model.Product;
import org.candlepin.model.ProductCurator;
import org.candlepin.model.ResultIterator;
import org.candlepin.model.dto.ContentData;
import org.candlepin.model.dto.ProductData;
import org.candlepin.model.dto.ProductContentData;

import com.google.inject.Inject;
import com.google.inject.persist.Transactional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;



/**
 * The ContentManager class provides methods for creating, updating and removing content instances
 * which also perform the cleanup and general maintenance necessary to keep content state in sync
 * with other objects which reference them.
 * <p></p>
 * The methods provided by this class are the prefered methods to use for CRUD operations on
 * content, to ensure content versioning and linking is handled properly.
 */
public class ContentManager {
    private static Logger log = LoggerFactory.getLogger(ContentManager.class);

    private ContentCurator contentCurator;
    private EntitlementCertificateGenerator entitlementCertGenerator;
    private OwnerContentCurator ownerContentCurator;
    private ProductCurator productCurator;
    private ProductManager productManager;

    @Inject
    public ContentManager(
        ContentCurator contentCurator, EntitlementCertificateGenerator entitlementCertGenerator,
        OwnerContentCurator ownerContentCurator, ProductCurator productCurator,
        ProductManager productManager) {

        this.contentCurator = contentCurator;
        this.entitlementCertGenerator = entitlementCertGenerator;
        this.ownerContentCurator = ownerContentCurator;
        this.productCurator = productCurator;
        this.productManager = productManager;
    }

    /**
     * Creates a new Content for the given owner, using the data in the provided DTO.
     *
     * @param contentData
     *  A content DTO representing the content to create
     *
     * @param owner
     *  The owner for which to create the content
     *
     * @throws IllegalArgumentException
     *  if contentData is null or incomplete, or owner is null
     *
     * @return
     *  a new Content instance representing the specified content for the given owner
     */
    public Content createContent(ContentData contentData, Owner owner) {
        if (contentData == null) {
            throw new IllegalArgumentException("contentData is null");
        }

        if (contentData.getId() == null || contentData.getType() == null || contentData.getLabel() == null ||
            contentData.getName() == null || contentData.getVendor() == null) {

            throw new IllegalArgumentException("contentData is incomplete");
        }

        // TODO: more validation here...?

        Content entity = new Content(contentData.getId());
        this.applyContentChanges(entity, contentData, owner);

        return this.createContent(entity, owner);

    }

    /**
     * Creates a new Content for the given owner, potentially using a different version than the
     * entity provided if a matching entity has already been registered for another owner.
     *
     * @param entity
     *  A Content instance representing the content to create
     *
     * @param owner
     *  The owner for which to create the content
     *
     * @throws IllegalStateException
     *  if this method is called with an entity that already exists in the backing database for the
     *  given owner
     *
     * @throws NullPointerException
     *  if entity or owner is null
     *
     * @return
     *  a new Content instance representing the specified content for the given owner
     */
    @Transactional
    public Content createContent(Content entity, Owner owner) {
        if (entity == null) {
            throw new IllegalArgumentException("entity is null");
        }

        if (owner == null) {
            throw new IllegalArgumentException("owner is null");
        }

        log.debug("Creating new content for org: {}, {}", entity, owner);

        Content existing = this.ownerContentCurator.getContentById(owner, entity.getId());

        // TODO: FIXME:
        // There's a bug here where if changes are applied to an entity's collections, and then
        // this method is called, the check below will trigger an illegal state exception, but
        // Hibernate's helpful nature will have persisted the changes during the lookup above.
        // This needs to be re-written to use DTOs as the primary source of entity creation, rather
        // than a bolted-on utility method.

        if (existing != null) {
            // If we're doing an exclusive creation, this should be an error condition
            throw new IllegalStateException("Content has already been created");
        }

        // Check if we have an alternate version we can use instead.
        List<Content> alternateVersions = this.contentCurator
            .getContentByVersion(entity.getId(), entity.getEntityVersion())
            .list();

        log.debug("Checking {} alternate content versions", alternateVersions.size());
        for (Content alt : alternateVersions) {
            if (alt.equals(entity)) {
                // If we're "creating" a content, we shouldn't have any other object references to
                // update for this content. Instead, we'll just add the new owner to the content.
                this.ownerContentCurator.mapContentToOwner(alt, owner);
                return alt;
            }
        }

        entity = this.contentCurator.create(entity);
        this.ownerContentCurator.mapContentToOwner(entity, owner);

        return entity;
    }

    /**
     * Updates the specified content instance, creating a new version of the content as necessary.
     * The content instance returned by this method is not guaranteed to be the same instance passed
     * in. As such, once this method has been called, callers should only use the instance output by
     * this method.
     *
     * @param entity
     *  The content entity to update
     *
     * @param owner
     *  The owner for which to update the content
     *
     * @param regenerateEntitlementCerts
     *  Whether or not changes made to the content should trigger the regeneration of entitlement
     *  certificates for affected consumers
     *
     * @throws IllegalStateException
     *  if this method is called with an entity does not exist in the backing database for the given
     *  owner
     *
     * @throws IllegalArgumentException
     *  if either the provided content entity or owner are null
     *
     * @return
     *  the updated content entity, or a new content entity
     */
    @Transactional
    public Content updateContent(Content entity, ContentData update, Owner owner,
        boolean regenerateEntitlementCerts) {

        if (entity == null) {
            throw new IllegalArgumentException("entity is null");
        }

        if (update == null) {
            throw new IllegalArgumentException("update is null");
        }

        if (owner == null) {
            throw new IllegalArgumentException("owner is null");
        }

        log.debug("Applying content update for org: {}, {}", entity, owner);

        // Resolve the entity to ensure we're working with the merged entity, and to ensure it's
        // already been created.
        entity = this.ownerContentCurator.getContentById(owner, entity.getId());

        if (entity == null) {
            // If we're doing an exclusive update, this should be an error condition
            throw new IllegalStateException("Content has not yet been created");
        }

        // Make sure we actually have a change to apply
        if (!entity.isChangedBy(update)) {
            return entity;
        }

        Content updated = this.applyContentChanges((Content) entity.clone(), update, owner);

        List<Content> alternateVersions = this.contentCurator
            .getContentByVersion(update.getId(), updated.getEntityVersion())
            .list();

        log.debug("Checking {} alternate content versions", alternateVersions.size());
        for (Content alt : alternateVersions) {
            if (alt.equals(updated)) {
                log.debug("Merging content with existing version: {} => {}", updated, alt);

                // Make sure every product using the old version/entity are updated to use the new one
                ResultIterator<Product> affectedProducts = this.productCurator
                    .getProductsWithContent(owner, Arrays.asList(updated.getId()))
                    .iterate();

                try {
                    List<Owner> owners = Arrays.asList(owner);
                    updated = this.ownerContentCurator.updateOwnerContentReferences(updated, alt, owners);

                    // Impl note:
                    // This block is a consequence of products and contents not being strongly related.
                    log.debug("Updating affected products");

                    ContentData cdata = updated.toDTO();

                    while (affectedProducts.hasNext()) {
                        Product product = affectedProducts.next();
                        ProductData pdata = product.toDTO();

                        log.debug("Updating affected product: {}", product);

                        // We're taking advantage of the mutable nature of our joining objects.
                        // Probably not the best idea for long-term maintenance, but it works for now.
                        ProductContentData pcd = pdata.getProductContent(updated.getId());
                        if (pcd != null) {
                            pcd.setContent(cdata);

                            // Impl note: This should also take care of our entitlement cert regeneration
                            this.productManager.updateProduct(
                                product, pdata, owner, regenerateEntitlementCerts);
                        }
                    }
                }
                finally {
                    affectedProducts.close();
                }

                return updated;
            }
        }

        // No alternate versions with which to converge. Check if we can do an in-place update instead
        if (this.ownerContentCurator.getOwnerCount(updated) < 2) {
            log.debug("Applying in-place update to content: {}", updated);

            updated = this.contentCurator.merge(this.applyContentChanges(entity, update, owner));

            if (regenerateEntitlementCerts) {
                // Every owner with a pool using any of the affected products needs an update.
                List<Product> affectedProducts = this.productCurator
                    .getProductsWithContent(Arrays.asList(updated.getUuid()))
                    .list();

                this.entitlementCertGenerator.regenerateCertificatesOf(
                    Arrays.asList(owner), affectedProducts, true
                );
            }

            return updated;
        }


        log.debug("Forking content and applying update: {}", updated);

        // Clear the UUID so Hibernate doesn't think our copy is a detached entity
        updated.setUuid(null);

        // Get products that currently use this content...
        ResultIterator<Product> affectedProducts = this.productCurator
            .getProductsWithContent(owner, Arrays.asList(updated.getId()))
            .iterate();

        try {
            updated = this.contentCurator.create(updated);
            updated = this.ownerContentCurator.updateOwnerContentReferences(
                entity, updated, Arrays.asList(owner)
            );

            // Impl note:
            // This block is a consequence of products and contents not being strongly related.
            log.debug("Updating affected products");

            ContentData cdata = updated.toDTO();

            while (affectedProducts.hasNext()) {
                Product product = affectedProducts.next();
                ProductData pdata = product.toDTO();

                log.debug("Updating affected product: {}", product);

                ProductContentData pcd = pdata.getProductContent(updated.getId());
                if (pcd != null) {
                    pcd.setContent(cdata);

                    // Impl note: This should also take care of our entitlement cert regeneration
                    this.productManager.updateProduct(product, pdata, owner, regenerateEntitlementCerts);
                }
            }
        }
        finally {
            affectedProducts.close();
        }

        return updated;
    }

    /**
     * Removes the specified content from the given owner, deleting the content instance if able.
     *
     * @param entity
     *  The content entity to remove
     *
     * @param owner
     *  The owner for which to remove the content
     *
     * @param regenerateEntitlementCerts
     *  Whether or not changes made to the content should trigger the regeneration of entitlement
     *  certificates for affected consumers
     *
     * @throws IllegalStateException
     *  if this method is called with an entity does not exist in the backing database for the given
     *  owner
     *
     * @throws IllegalArgumentException
     *  if entity or owner is null
     *
     * @return
     *  a list of products affected by the removal of the given content
     */
    @Transactional
    public List<Product> removeContent(Content entity, Owner owner, boolean regenerateEntitlementCerts) {
        if (entity == null) {
            throw new IllegalArgumentException("entity is null");
        }

        if (owner == null) {
            throw new IllegalArgumentException("owner is null");
        }

        log.debug("Removing content from owner: {}, {}", entity, owner);

        // This has to fetch a new instance, or we'll be unable to compare the objects
        Content existing = this.ownerContentCurator.getContentById(owner, entity.getId());

        if (existing == null) {
            // If we're doing an exclusive update, this should be an error condition
            throw new IllegalStateException("Content has not yet been created");
        }

        List<Product> affectedProducts = this.productCurator
            .getProductsWithContent(owner, Arrays.asList(existing.getId()))
            .list();

        // Update affected products and regenerate their certs
        List<Product> updatedAffectedProducts = new LinkedList<Product>();
        List<Content> contentList = Arrays.asList(existing);

        log.debug("Updating {} affected products", affectedProducts.size());
        for (Product product : affectedProducts) {
            log.debug("Updating affected product: {}", product);
            product = this.productManager.removeProductContent(
                product, contentList, owner, regenerateEntitlementCerts
            );

            updatedAffectedProducts.add(product);
        }

        this.ownerContentCurator.removeOwnerFromContent(existing, owner);
        if (this.ownerContentCurator.getOwnerCount(existing) == 0) {
            // No one is using this product anymore; delete the entity
            this.contentCurator.delete(existing);
        }
        else {
            // Clean up any dangling references to content
            this.ownerContentCurator.removeOwnerContentReferences(existing, Arrays.asList(owner));
        }

        return updatedAffectedProducts;
    }

    /**
     * Applies the changes from the given DTO to the specified entity
     *
     * @param entity
     *  The entity to modify
     *
     * @param update
     *  The DTO containing the modifications to apply
     *
     * @param owner
     *  An owner to use for resolving entity references
     *
     * @throws IllegalArgumentException
     *  if entity, update or owner is null
     *
     * @return
     *  The updated product entity
     */
    private Content applyContentChanges(Content entity, ContentData update, Owner owner) {
        if (entity == null) {
            throw new IllegalArgumentException("entity is null");
        }

        if (update == null) {
            throw new IllegalArgumentException("update is null");
        }

        if (owner == null) {
            throw new IllegalArgumentException("owner is null");
        }

        if (update.getType() != null) {
            entity.setType(update.getType());
        }

        if (update.getLabel() != null) {
            entity.setLabel(update.getLabel());
        }

        if (update.getName() != null) {
            entity.setName(update.getName());
        }

        if (update.getVendor() != null) {
            entity.setVendor(update.getVendor());
        }

        if (update.getContentUrl() != null) {
            entity.setContentUrl(update.getContentUrl());
        }

        if (update.getRequiredTags() != null) {
            entity.setRequiredTags(update.getRequiredTags());
        }

        if (update.getReleaseVersion() != null) {
            entity.setReleaseVersion(update.getReleaseVersion());
        }

        if (update.getGpgUrl() != null) {
            entity.setGpgUrl(update.getGpgUrl());
        }

        if (update.getMetadataExpire() != null) {
            entity.setMetadataExpire(update.getMetadataExpire());
        }

        if (update.getModifiedProductIds() != null) {
            entity.setModifiedProductIds(update.getModifiedProductIds());
        }

        if (update.getArches() != null) {
            entity.setArches(update.getArches());
        }

        if (update.isLocked() != null) {
            entity.setLocked(update.isLocked());
        }

        return entity;
    }

}
