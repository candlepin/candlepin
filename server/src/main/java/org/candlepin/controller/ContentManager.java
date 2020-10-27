/**
 * Copyright (c) 2009 - 2016 Red Hat, Inc.
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
import org.candlepin.model.OwnerProductCurator;
import org.candlepin.model.Product;
import org.candlepin.model.ProductContent;
import org.candlepin.service.model.ContentInfo;
import org.candlepin.service.model.ProductInfo;
import org.candlepin.util.Util;

import com.google.inject.Inject;
import com.google.inject.persist.Transactional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;



/**
 * The ContentManager class provides methods for creating, updating and removing content instances
 * which also perform the cleanup and general maintenance necessary to keep content state in sync
 * with other objects which reference them.
 * <p></p>
 * The methods provided by this class are the prefered methods to use for CRUD operations on
 * content, to ensure content versioning and linking is handled properly.
 */
public class ContentManager {
    private static final Logger log = LoggerFactory.getLogger(ContentManager.class);

    private ProductManager productManager;

    private ContentCurator contentCurator;
    private OwnerContentCurator ownerContentCurator;
    private OwnerProductCurator ownerProductCurator;

    @Inject
    public ContentManager(ProductManager productManager, ContentCurator contentCurator,
        OwnerContentCurator ownerContentCurator, OwnerProductCurator ownerProductCurator) {

        this.productManager = Objects.requireNonNull(productManager);

        this.contentCurator = Objects.requireNonNull(contentCurator);
        this.ownerContentCurator = Objects.requireNonNull(ownerContentCurator);
        this.ownerProductCurator = Objects.requireNonNull(ownerProductCurator);
    }

    /**
     * Builds a ProductInfo instance which does not contain a reference to the specified removed
     * content ID. This can be passed to the ProductManager to update an affected product by
     * removing the reference to the removed content.
     *
     * @param entity
     *  the base product entity to use for creating the update
     *
     * @param removedContentId
     *  the Red Hat content ID of the content to remove from the given product
     *
     * @return
     *  a ProductInfo instance which does not contain a reference to the removed content
     */
    private ProductInfo buildProductInfoForContentRemoval(Product entity, String removedContentId) {
        Product output = new Product()
            .setId(entity.getId());

        for (ProductContent pc : entity.getProductContent()) {
            Content referent = pc.getContent();

            if (!removedContentId.equals(referent.getId())) {
                output.addContent(new Content().setId(referent.getId()), pc.isEnabled());
            }
        }

        return output;
    }

    /**
     * Creates a new Content instance using the given content data.
     *
     * @param owner
     *  the owner for which to create the new content
     *
     * @param contentData
     *  a ContentInfo instance containing the data for the new content
     *
     * @throws IllegalArgumentException
     *  if owner is null, contentData is null, or contentData lacks required information
     *
     * @throws IllegalStateException
     *  if a content instance already exists for the content ID specified in contentData
     *
     * @return
     *  the new created Content instance
     */
    @Transactional
    public Content createContent(Owner owner, ContentInfo contentData) {
        if (owner == null) {
            throw new IllegalArgumentException("owner is null");
        }

        if (contentData == null) {
            throw new IllegalArgumentException("contentData is null");
        }

        if (contentData.getId() == null || contentData.getType() == null || contentData.getLabel() == null ||
            contentData.getName() == null || contentData.getVendor() == null) {
            throw new IllegalArgumentException("contentData is incomplete");
        }

        if (this.ownerContentCurator.contentExists(owner, contentData.getId())) {
            throw new IllegalStateException("content has already been created: " + contentData.getId());
        }

        log.debug("Creating new content for org: {}, {}", contentData, owner);

        Content entity = new Content()
            .setId(contentData.getId());

        applyContentChanges(entity, contentData);

        // Check if we have an alternate version we can use instead.
        List<Content> alternateVersions = this.ownerContentCurator.getContentByVersions(
            owner, Collections.<String, Integer>singletonMap(entity.getId(), entity.getEntityVersion()))
            .get(entity.getId());

        if (alternateVersions != null) {
            log.debug("Checking {} alternate content versions", alternateVersions.size());

            for (Content alt : alternateVersions) {
                if (alt.equals(entity)) {
                    log.debug("Converging content with existing version: {} => {}", entity, alt);

                    // If we're "creating" a content, we shouldn't have any other object references to
                    // update for this content. Instead, we'll just add the new owner to the content.
                    this.ownerContentCurator.mapContentToOwner(alt, owner);
                    return alt;
                }
            }
        }

        log.debug("Creating new content instance: {}", entity);

        entity = this.contentCurator.create(entity);
        this.ownerContentCurator.mapContentToOwner(entity, owner);

        return entity;
    }

    /**
     * Updates content with the ID specified in the given content data, optionally regenerating
     * certificates of entitlements affected by the content update.
     *
     * @param owner
     *  the owner for which to update the specified content
     *
     * @param contentData
     *  the content data to use to update the specified content
     *
     * @param regenCerts
     *  whether or not certificates for affected entitlements should be regenerated after updating
     *  the specified content
     *
     * @throws IllegalArgumentException
     *  if owner is null, contentData is null, or contentData is missing required information
     *
     * @throws IllegalStateException
     *  if the content specified by the content data does not yet exist for the given owner
     *
     * @return
     *  the updated Content instance
     */
    @Transactional
    public Content updateContent(Owner owner, ContentInfo contentData, boolean regenCerts) {
        if (owner == null) {
            throw new IllegalArgumentException("owner is null");
        }

        if (contentData == null) {
            throw new IllegalArgumentException("contentData is null");
        }

        if (contentData.getId() == null) {
            throw new IllegalArgumentException("contentData is incomplete");
        }

        // Resolve the entity to ensure we're working with the merged entity, and to ensure it's
        // already been created.
        Content entity = this.ownerContentCurator.getContentById(owner, contentData.getId());

        if (entity == null) {
            // If we're doing an exclusive update, this should be an error condition
            throw new IllegalStateException("Content has not yet been created");
        }

        // Make sure we have an actual change to apply
        if (!isChangedBy(entity, contentData)) {
            return entity;
        }

        log.debug("Applying content update for org: {} => {}, {}", contentData, entity, owner);

        Content updated = applyContentChanges((Content) entity.clone(), contentData)
            .setUuid(null);

        // Grab a list of products that are using this content. Due to versioning restrictions,
        // we'll need to update these manually as a recursive step. We'll come back to these later.
        Collection<Product> affectedProducts = this.ownerProductCurator
            .getProductsReferencingContent(owner.getId(), entity.getUuid());

        // Check for newer versions of the same content. We want to try to dedupe as much data as we
        // can, and if we have a newer version of the content (which matches the version provided by
        // the caller), we can just point the given orgs to the new content instead of giving them
        // their own version.
        // This is probably going to be a very expensive operation, though.
        List<Content> alternateVersions = this.ownerContentCurator.getContentByVersions(owner,
            Collections.<String, Integer>singletonMap(updated.getId(), updated.getEntityVersion()))
            .get(updated.getId());

        if (alternateVersions != null) {
            log.debug("Checking {} alternate content versions", alternateVersions.size());

            for (Content alt : alternateVersions) {
                if (alt.equals(updated)) {
                    log.debug("Converging content with existing version: {} => {}", updated, alt);

                    this.ownerContentCurator.updateOwnerContentReferences(owner,
                        Collections.singletonMap(entity.getUuid(), alt.getUuid()));

                    updated = alt;
                }
            }
        }

        if (updated.getUuid() == null) {
            log.debug("Creating new content instance and applying update: {}", updated);
            updated = this.contentCurator.create(updated);

            this.ownerContentCurator.updateOwnerContentReferences(owner,
                Collections.singletonMap(entity.getUuid(), updated.getUuid()));
        }

        if (affectedProducts.size() > 0) {
            log.debug("Updating {} products affected by content update", affectedProducts.size());

            for (Product affected : affectedProducts) {
                this.productManager.updateChildrenReferences(owner, affected, regenCerts);
            }
        }

        return updated;
    }

    /**
     * Updates content with the ID specified in the given content data, regenerating certificates
     * of entitlements affected by the content update.
     *
     * @param owner
     *  the owner for which to update the specified content
     *
     * @param contentData
     *  the content data to use to update the specified content
     *
     * @throws IllegalArgumentException
     *  if owner is null, contentData is null, or contentData is missing required information
     *
     * @throws IllegalStateException
     *  if the content specified by the content data does not yet exist for the given owner
     *
     * @return
     *  the updated Content instance
     */
    public Content updateContent(Owner owner, Content contentData) {
        return this.updateContent(owner, contentData, true);
    }

    /**
     * Removes the content specified by the provided content ID from the given owner, optionally
     * regenerating certificates of affected entitlements.
     *
     * @param owner
     *  the owner for which to remove the specified content
     *
     * @param contentId
     *  the Red Hat content ID of the content to remove
     *
     * @param regenCerts
     *  whether or not certificates for affected entitlements should be regenerated after removing
     *  the specified content
     *
     * @throws IllegalArgumentException
     *  if owner is null, or contentId is null
     *
     * @throws IllegalStateException
     *  if a content with the given ID has not yet been created for the specified owner
     *
     * @return
     *  the Content instance removed from the given owner
     */
    @Transactional
    public Content removeContent(Owner owner, String contentId, boolean regenCerts) {
        if (owner == null) {
            throw new IllegalArgumentException("owner is null");
        }

        if (contentId == null) {
            throw new IllegalArgumentException("contentId is null");
        }

        Content entity = this.ownerContentCurator.getContentById(owner, contentId);
        if (entity == null) {
            throw new IllegalStateException("Content has not yet been created");
        }

        // Grab a list of products that are using this content. Due to versioning restrictions,
        // we'll need to update these manually as a recursive step. We'll come back to these later.
        Collection<Product> affectedProducts = this.ownerProductCurator
            .getProductsReferencingContent(owner.getId(), entity.getUuid());

        // Validation checks passed, remove the reference to it
        log.debug("Removing content for org: {}, {}", entity, owner);
        this.ownerContentCurator.removeOwnerContentReferences(owner, Collections.singleton(entity.getUuid()));

        // Update affected products
        if (affectedProducts.size() > 0) {
            log.debug("Updating {} products affected by content removal", affectedProducts.size());

            for (Product affected : affectedProducts) {
                ProductInfo update = this.buildProductInfoForContentRemoval(affected, contentId);
                this.productManager.updateProduct(owner, update, regenCerts);
            }
        }

        return entity;
    }

    /**
     * Removes the content specified by the provided content data from the given owner, optionally
     * regenerating certificates of affected entitlements.
     *
     * @param owner
     *  the owner for which to remove the specified content
     *
     * @param contentData
     *  the content data containing the Red Hat ID of the content to remove
     *
     * @param regenCerts
     *  whether or not certificates for affected entitlements should be regenerated after removing
     *  the specified content
     *
     * @throws IllegalArgumentException
     *  if owner is null, or contentData is null
     *
     * @throws IllegalStateException
     *  if a content with the given ID has not yet been created for the specified owner
     *
     * @return
     *  the Content instance removed from the given owner
     */
    public Content removeContent(Owner owner, ContentInfo contentData, boolean regenCerts) {
        if (contentData == null) {
            throw new IllegalArgumentException("contentData is null");
        }

        return this.removeContent(owner, contentData.getId(), regenCerts);
    }

    /**
     * Tests if the given content entity would be changed by the collection of updates captured by
     * the specified content info container, ignoring any identifier fields.
     * <p></p>
     * <strong>Note:</strong> This function will attempt to return early, only testing as many
     * fields as strictly necessary to determine whether or not the update contains a change to the
     * base entity. For instance, if the first field tested contains a difference, no further fields
     * will be tested.<br/>
     * Additionally, children entities are checked by testing only whether or not the reference
     * would change after entity resolution; the children themselves are not tested for equality.
     *
     * @param entity
     *  the existing entity to examine
     *
     * @param update
     *  the content info container to test for changes
     *
     * @throws IllegalArgumentException
     *  if either the entity or update parameters are null
     *
     * @return
     *  true if the entity would be changed by the provided update; false otherwise
     */
    public static boolean isChangedBy(Content entity, ContentInfo update) {
        if (entity == null) {
            throw new IllegalArgumentException("entity is null");
        }

        if (update == null) {
            throw new IllegalArgumentException("update is null");
        }

        // Field checks
        if (update.getId() != null && !update.getId().equals(entity.getId())) {
            return true;
        }

        if (update.getType() != null && !update.getType().equals(entity.getType())) {
            return true;
        }

        if (update.getLabel() != null && !update.getLabel().equals(entity.getLabel())) {
            return true;
        }

        if (update.getName() != null && !update.getName().equals(entity.getName())) {
            return true;
        }

        if (update.getVendor() != null && !update.getVendor().equals(entity.getVendor())) {
            return true;
        }

        if (update.getContentUrl() != null && !update.getContentUrl().equals(entity.getContentUrl())) {
            return true;
        }

        if (update.getRequiredTags() != null && !update.getRequiredTags().equals(entity.getRequiredTags())) {
            return true;
        }

        if (update.getReleaseVersion() != null &&
            !update.getReleaseVersion().equals(entity.getReleaseVersion())) {

            return true;
        }

        if (update.getGpgUrl() != null && !update.getGpgUrl().equals(entity.getGpgUrl())) {
            return true;
        }

        if (update.getMetadataExpiration() != null &&
            !update.getMetadataExpiration().equals(entity.getMetadataExpiration())) {

            return true;
        }

        if (update.getArches() != null && !update.getArches().equals(entity.getArches())) {
            return true;
        }

        Collection<String> requiredProductIds = update.getRequiredProductIds();
        if (requiredProductIds != null &&
            !Util.collectionsAreEqual(entity.getModifiedProductIds(), requiredProductIds)) {

            return true;
        }

        return false;
    }

    /**
     * Applies changes from the given ContentInfo instance to the specified Content entity.
     *
     * @param entity
     *  the Content entity to update
     *
     * @param update
     *  the ContentInfo instance containing the data with which to update the entity
     *
     * @return
     *  the updated Content entity
     */
    private static Content applyContentChanges(Content entity, ContentInfo update) {
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

        if (update.getMetadataExpiration() != null) {
            entity.setMetadataExpiration(update.getMetadataExpiration());
        }

        if (update.getRequiredProductIds() != null) {
            entity.setModifiedProductIds(update.getRequiredProductIds());
        }

        if (update.getArches() != null) {
            entity.setArches(update.getArches());
        }

        return entity;
    }
}
