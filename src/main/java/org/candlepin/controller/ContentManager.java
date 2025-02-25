/*
 * Copyright (c) 2009 - 2025 Red Hat, Inc.
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
import org.candlepin.model.EnvironmentCurator;
import org.candlepin.model.Owner;
import org.candlepin.model.Product;
import org.candlepin.model.ProductCurator;
import org.candlepin.service.model.ContentInfo;
import org.candlepin.util.Util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

import javax.inject.Inject;



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

    private final ContentAccessManager contentAccessManager;
    private final EntitlementCertificateService entitlementCertificateService;

    private final ProductCurator productCurator;
    private final ContentCurator contentCurator;
    private final EnvironmentCurator environmentCurator;

    @Inject
    public ContentManager(ContentAccessManager contentAccessManager,
        EntitlementCertificateService entitlementCertificateService, ProductCurator productCurator,
        ContentCurator contentCurator, EnvironmentCurator environmentCurator) {

        this.contentAccessManager = Objects.requireNonNull(contentAccessManager);
        this.entitlementCertificateService = Objects.requireNonNull(entitlementCertificateService);

        this.productCurator = Objects.requireNonNull(productCurator);
        this.contentCurator = Objects.requireNonNull(contentCurator);
        this.environmentCurator = Objects.requireNonNull(environmentCurator);
    }

    /**
     * Creates a new content from the given content data in the namespace of the given organization.
     * The content data must have all of the required fields set.
     *
     * @param owner
     *  the organization in which to create the new content
     *
     * @param cinfo
     *  the content data to use to create the content
     *
     * @throws IllegalArgumentException
     *  if cinfo is null
     *
     * @throws IllegalStateException
     *  if creating the new content would cause a collision with an existing content
     *
     * @return
     *  the newly created and persisted content instance
     */
    public Content createContent(Owner owner, ContentInfo cinfo) {
        if (owner == null) {
            throw new IllegalArgumentException("owner is null");
        }

        if (cinfo == null) {
            throw new IllegalArgumentException("cinfo is null");
        }

        if (cinfo.getId() == null || cinfo.getType() == null || cinfo.getLabel() == null ||
            cinfo.getName() == null || cinfo.getVendor() == null) {

            throw new IllegalArgumentException("cinfo is incomplete");
        }

        String namespace = owner.getKey();

        if (this.contentCurator.resolveContentId(namespace, cinfo.getId()) != null) {
            String errmsg = String.format("a content with ID \"%s\" already exists within the context of " +
                "namespace \"%s\"", cinfo.getId(), namespace);

            throw new IllegalStateException(errmsg);
        }

        log.debug("Creating new content in namespace {}: {}", namespace, cinfo);

        Content entity = new Content(cinfo.getId())
            .setNamespace(namespace);

        entity = applyContentChanges(entity, cinfo);

        log.debug("Creating new content instance: {}", entity);
        entity = this.contentCurator.create(entity);

        log.debug("Synchronizing last content update for org: {}", owner);
        owner.syncLastContentUpdate();

        return entity;
    }

    /**
     * Updates the specified content with the given content data, optionally regenerating
     * certificates of entitlements for any products affected by this update. The content data need
     * not be complete, but any references to children entities must be valid references to entities
     * present in the target content's namespace.
     * <p></p>
     * Note that this method is not able to update the target content's UUID, ID, or namespace.
     *
     * @param owner
     *  the organization making the change to the content
     *
     * @param content
     *  the content to update
     *
     * @param cinfo
     *  the content data to use to update the content
     *
     * @param regenCerts
     *  whether or not to regenerate certificates of entitlements for any products affected by an
     *  update to the content
     *
     * @throws IllegalArgumentException
     *  if the owner, target content, or content data is null
     *
     * @throws IllegalStateException
     *  if the given content instance is not an existing, managed entity
     *
     * @return
     *  the updated content instance
     */
    public Content updateContent(Owner owner, Content content, ContentInfo cinfo, boolean regenCerts) {
        if (content == null) {
            throw new IllegalArgumentException("content is null");
        }

        if (cinfo == null) {
            throw new IllegalArgumentException("cinfo is null");
        }

        if (!this.contentCurator.getEntityManager().contains(content)) {
            throw new IllegalStateException("content is not a managed entity");
        }

        String namespace = content.getNamespace();

        // If the namespace doesn't match the org's, that's bad; disallow that.
        if (namespace == null || !namespace.equals(owner.getKey())) {
            throw new IllegalStateException("content namespace does not match org's namespace");
        }

        if (isChangedBy(content, cinfo)) {
            content = applyContentChanges(content, cinfo);

            log.debug("Persisting updated content in namespace {}: {}", namespace, content);
            content = this.contentCurator.merge(content);

            log.debug("Synchronizing last content update for org: {}", owner);
            owner.syncLastContentUpdate();

            List<Product> affectedProducts = regenCerts ?
                this.productCurator.getProductsReferencingContent(content.getUuid()) :
                List.of();

            if (!affectedProducts.isEmpty()) {
                log.debug("Flagging entitlement certificates of {} affected product(s) for regeneration",
                    affectedProducts.size());

                this.entitlementCertificateService
                    .regenerateCertificatesOf(List.of(owner), affectedProducts, true);
            }
        }

        return content;
    }

    /**
     * Removes the specified unreferenced content from its namespace. If the content is still in use by
     * one or more products, this method throws an exception.
     *
     * @param owner
     *  the organization removing the content
     *
     * @param content
     *  the content to remove
     *
     * @throws IllegalArgumentException
     *  if the owner, or the target content is null
     *
     * @throws IllegalStateException
     *  if the given content instance is not an existing, managed entity, is not in the namespace of the
     *  given organization, or the content is still in use by one or more products
     *
     * @return
     *  the removed content entity
     */
    public Content removeContent(Owner owner, Content content) {
        if (owner == null) {
            throw new IllegalArgumentException("owner is null");
        }

        if (content == null) {
            throw new IllegalArgumentException("content is null");
        }

        if (!this.contentCurator.getEntityManager().contains(content)) {
            throw new IllegalStateException("content is not a managed entity");
        }

        if (this.contentCurator.contentHasParentProducts(content)) {
            throw new IllegalStateException("Content is referenced by one or more parent products: " +
                content);
        }

        String namespace = content.getNamespace();

        // If the namespace doesn't match the org's, that's bad; disallow that.
        if (namespace == null || !namespace.equals(owner.getKey())) {
            throw new IllegalStateException("content namespace does not match org's namespace");
        }

        // Future fun time: What happens if namespaces are no longer 1:1 with org? Answer: We'll get
        // indeterministic behavior with this removal.
        log.debug("Removing environment content references from namespace: {}, {}", namespace, content);
        this.environmentCurator.removeEnvironmentContentReferences(owner, List.of(content.getId()));

        // Validation checks passed, remove the reference to it
        log.debug("Removing content from namespace: {}, {}", namespace, content);
        this.contentCurator.delete(content);

        log.debug("Synchronizing last content update for org: {}", owner);
        owner.syncLastContentUpdate();

        return content;
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

        if (update.getMetadataExpiration() != null &&
            !update.getMetadataExpiration().equals(entity.getMetadataExpiration())) {

            return true;
        }

        // These values historically treat nulls and empty strings as interchangeable, but store
        // them as nulls. As a result, we need to treat an inbound empty values as equivalent to a
        // local null value.
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
