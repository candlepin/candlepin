/*
 * Copyright (c) 2009 - 2023 Red Hat, Inc.
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

import com.google.inject.persist.Transactional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import javax.inject.Inject;
import javax.persistence.LockModeType;



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

    /** Name of the system lock used by various content operations */
    public static final String SYSTEM_LOCK = "content";

    private final ContentAccessManager contentAccessManager;
    private final EntitlementCertificateGenerator entitlementCertGenerator;

    private final ContentCurator contentCurator;
    private final OwnerContentCurator ownerContentCurator;
    private final OwnerProductCurator ownerProductCurator;

    @Inject
    public ContentManager(ContentAccessManager contentAccessManager,
        EntitlementCertificateGenerator entitlementCertGenerator, ContentCurator contentCurator,
        OwnerContentCurator ownerContentCurator, OwnerProductCurator ownerProductCurator) {

        this.contentAccessManager = Objects.requireNonNull(contentAccessManager);
        this.entitlementCertGenerator = Objects.requireNonNull(entitlementCertGenerator);

        this.contentCurator = Objects.requireNonNull(contentCurator);
        this.ownerContentCurator = Objects.requireNonNull(ownerContentCurator);
        this.ownerProductCurator = Objects.requireNonNull(ownerProductCurator);
    }

    /**
     * Creates a new Content instance using the given content data.
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
    public Content createContent(ContentInfo contentData) {
        if (contentData == null) {
            throw new IllegalArgumentException("contentData is null");
        }

        if (contentData.getId() == null || contentData.getType() == null || contentData.getLabel() == null ||
            contentData.getName() == null || contentData.getVendor() == null) {
            throw new IllegalArgumentException("contentData is incomplete");
        }

        if (this.contentCurator.contentExistsById(contentData.getId())) {
            throw new IllegalStateException("content already exists: " + contentData.getId());
        }

        log.debug("Creating new content: {}", contentData);

        Content entity = new Content()
            .setId(contentData.getId());

        this.applyContentChanges(entity, contentData);

        log.debug("Creating new content instance: {}", entity);
        entity = this.contentCurator.create(entity);

        // TODO: FIXME: uh oh...
        // log.debug("Synchronizing last content update for affected orgs...");
        // this.contentAccessManager.syncOwnerLastContentUpdate(owner);

        return entity;
    }

    /**
     * Updates content with the ID specified in the given content data, optionally regenerating
     * certificates of entitlements affected by the content update.
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
    public Content updateContent(Content entity, ContentInfo contentData, boolean regenCerts) {
        if (entity == null) {
            throw new IllegalArgumentException("entity is null");
        }

        if (contentData == null) {
            throw new IllegalArgumentException("contentData is null");
        }

        // this.ownerContentCurator.getSystemLock(SYSTEM_LOCK, LockModeType.PESSIMISTIC_WRITE);

        // Make sure we have an actual change to apply
        if (!isChangedBy(entity, contentData)) {
            return entity;
        }

        log.debug("Applying content update: {} => {}", contentData, entity);
        this.applyContentChanges(entity, contentData);

        log.debug("Updating content instance: {}", entity);
        entity = this.contentCurator.merge(entity);

        if (regenCerts) {
            // TODO: FIXME: This could be very far reaching...
            List<String> affectedProductIds = this.contentCurator
                .getProductIdsReferencingContentById(entity.getId());

            this.entitlementCertGenerator.regenerateCertificatesForProducts(affectedProductIds, true);
        }

        // TODO: FIXME: uh oh...
        // log.debug("Synchronizing last content update for affected orgs...");
        // this.contentAccessManager.syncOwnerLastContentUpdate(owner);

        return entity;
    }

    /**
     * Updates content with the ID specified in the given content data, regenerating certificates
     * of entitlements affected by the content update.
     *
     * @param contentData
     *  the content data to use to update the specified content
     *
     * @throws IllegalArgumentException
     *  if contentData is null, or contentData is missing required information
     *
     * @throws IllegalStateException
     *  if the content specified by the content data does not yet exist
     *
     * @return
     *  the updated Content instance
     */
    public Content updateContent(Content entity, ContentInfo contentData) {
        return this.updateContent(entity, contentData, true);
    }

    /**
     * Removes the content specified by the provided content ID, optionally regenerating
     * certificates of affected entitlements.
     *
     * @param contentId
     *  the Red Hat content ID of the content to remove
     *
     * @throws IllegalArgumentException
     *  if or contentId is null
     *
     * @throws IllegalStateException
     *  if a content with the given ID has not yet been created
     *
     * @return
     *  the Content instance removed
     */
    @Transactional
    public Content deleteContent(Content entity) {
        if (entity == null) {
            throw new IllegalArgumentException("entity is null");
        }

        // Resolve the entity to the actual entity to (a) verify it exists and (b) ensure we're
        // passing a managed entity to the curator
        String entityId = entity.getId();
        entity = this.contentCurator.getContentById(entityId);
        if (entity == null) {
            throw new IllegalStateException("No such content entity exists: " + entityId);
        }

        // this.ownerContentCurator.getSystemLock(SYSTEM_LOCK, LockModeType.PESSIMISTIC_WRITE);

        // Make sure the content isn't referenced by any products
        if (this.contentCurator.contentIsReferencedByProducts(entity)) {
            throw new IllegalStateException("Content is referenced by one or more products: " + entity);
        }

        // Validation checks passed, remove the reference to it
        log.debug("Deleting content: {}", entity);
        this.contentCurator.delete(entity);

        // TODO: FIXME: get a list of affected orgs and sync owner content update for the affected orgs
        // log.debug("Synchronizing last content update for org: {}", owner);
        // this.contentAccessManager.syncOwnerLastContentUpdate(owner);

        return entity;
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
        if (hasValueChanged(entity.getId(), update.getId(), false)) {
            return true;
        }

        if (hasValueChanged(entity.getType(), update.getType(), false)) {
            return true;
        }

        if (hasValueChanged(entity.getLabel(), update.getLabel(), false)) {
            return true;
        }

        if (hasValueChanged(entity.getName(), update.getName(), false)) {
            return true;
        }

        if (hasValueChanged(entity.getVendor(), update.getVendor(), false)) {
            return true;
        }

        if (hasValueChanged(entity.getGpgUrl(), update.getGpgUrl(), false)) {
            return true;
        }

        if (update.getMetadataExpiration() != null &&
            !update.getMetadataExpiration().equals(entity.getMetadataExpiration())) {

            return true;
        }

        // These values historically store and treat nulls as interchangeable. As a result, we need
        // to treat an inbound empty values as equivalent to a local null value
        if (hasValueChanged(entity.getArches(), update.getArches(), true)) {
            return true;
        }

        if (hasValueChanged(entity.getContentUrl(), update.getContentUrl(), true)) {
            return true;
        }

        if (hasValueChanged(entity.getGpgUrl(), update.getGpgUrl(), true)) {
            return true;
        }

        if (hasValueChanged(entity.getReleaseVersion(), update.getReleaseVersion(), true)) {
            return true;
        }

        if (hasValueChanged(entity.getRequiredTags(), update.getRequiredTags(), true)) {
            return true;
        }

        // Collections require special consideration as always
        Collection<String> requiredProductIds = update.getRequiredProductIds();

        if (requiredProductIds != null &&
            !Util.collectionsAreEqual(entity.getModifiedProductIds(), requiredProductIds)) {

            return true;
        }

        return false;
    }

    /**
     * Checks if the given incoming value would cause a change to the existing value. Typically this
     * means that the incoming value is not null, and not equal to the existing value; however, if
     * the treatNullAsEmpty option is set, this method will also treat an existing null value the
     * same as an incoming empty value to deal with fields that treat null and empty as the same
     * value at the logic or database layers.
     *
     * @param existing
     *  the existing value to check
     *
     * @param incoming
     *  the new incoming value to test
     *
     * @param treatNullAsEmpty
     *  whether or not to treat an existing null value the same as an incoming empty string
     *
     * @return
     *  true if the incoming value would change the existing value, false otherwise
     */
    private static boolean hasValueChanged(String existing, String incoming, boolean treatNullAsEmpty) {
        return incoming != null &&
            !(incoming.equals(existing) || (treatNullAsEmpty && existing == null && incoming.isEmpty()));
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
