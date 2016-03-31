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

import org.candlepin.common.config.Configuration;
import org.candlepin.config.ConfigProperties;
import org.candlepin.model.Content;
import org.candlepin.model.ContentCurator;
import org.candlepin.model.Owner;
import org.candlepin.model.Product;
import org.candlepin.model.ProductContent;
import org.candlepin.model.ProductCurator;

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
 * <p/>
 * The methods provided by this class are the prefered methods to use for CRUD operations on
 * content, to ensure content versioning and linking is handled properly.
 */
public class ContentManager {
    private static Logger log = LoggerFactory.getLogger(ContentManager.class);

    private ContentCurator contentCurator;
    private ProductCurator productCurator;
    private ProductManager productManager;
    private EntitlementCertificateGenerator entitlementCertGenerator;

    /** Whether or not all content updates should occur in place (ie: global products/content) */
    private boolean updateInPlace;

    @Inject
    public ContentManager(ContentCurator contentCurator, ProductCurator productCurator,
        ProductManager productManager, EntitlementCertificateGenerator entitlementCertGenerator,
        Configuration config) {

        this.contentCurator = contentCurator;
        this.productCurator = productCurator;
        this.productManager = productManager;
        this.entitlementCertGenerator = entitlementCertGenerator;

        this.updateInPlace = !config.getBoolean(ConfigProperties.PER_ORG_PRODUCTS, true);
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
     *  if the provided content entity is null
     *
     * @return
     *  a new Content instance representing the specified content for the given owner
     */
    @Transactional
    public Content createContent(Content entity, Owner owner) {
        log.debug("Creating new content for org: {}, {}", entity, owner);

        if (entity == null) {
            throw new NullPointerException("entity");
        }

        Content existing = this.contentCurator.lookupById(owner, entity.getId());

        if (existing != null) {
            // If we're doing an exclusive creation, this should be an error condition
            throw new IllegalStateException("Content has already been created");
        }

        // Check if we have an alternate version we can use instead.

        // TODO: Not sure if we really even need the version check. If we have any other matching
        // content, we should probably use it -- regardless of the actual version value.
        List<Content> alternateVersions = this.contentCurator.getContentByVersion(
            entity.getId(), entity.hashCode()
        );

        for (Content alt : alternateVersions) {
            if (alt.equals(entity)) {
                // If we're "creating" a content, we shouldn't have any other object references to
                // update for this content. Instead, we'll just add the new owner to the content.
                alt.addOwner(owner);

                return this.contentCurator.merge(alt);
            }
        }

        entity.addOwner(owner);
        return this.contentCurator.create(entity);
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
     * @throws NullPointerException
     *  if the provided content entity is null
     *
     * @return
     *  the updated content entity, or a new content entity
     */
    @Transactional
    public Content updateContent(Content entity, Owner owner, boolean regenerateEntitlementCerts) {
        log.debug("Applying content update for org: {}, {}", entity, owner);

        if (entity == null) {
            throw new NullPointerException("entity");
        }

        // This has to fetch a new instance, or we'll be unable to compare the objects
        Content existing = this.contentCurator.lookupById(owner, entity.getId());

        if (existing == null) {
            // If we're doing an exclusive update, this should be an error condition
            throw new IllegalStateException("Content has not yet been created");
        }

        if (existing == entity) {
            // Nothing to do, really. The caller likely intends for the changes to be persisted, so
            // we can do that for them.
            return this.contentCurator.merge(existing);
        }

        // Check for newer versions of the same content. We want to try to dedupe as much data as we
        // can, and if we have a newer version of the content (which matches the version provided by
        // the caller), we can just point the given orgs to the new content instead of giving them
        // their own version.
        // This is probably going to be a very expensive operation, though.
        List<Content> alternateVersions = this.contentCurator.getContentByVersion(
            entity.getId(), entity.hashCode()
        );

        for (Content alt : alternateVersions) {
            if (alt.equals(entity)) {
                entity = this.contentCurator.updateOwnerContentReferences(
                    existing, alt, Arrays.asList(owner)
                );

                if (regenerateEntitlementCerts) {
                    // TODO:
                    // regenerate entitlements for affected products for the given org
                }

                return entity;
            }
        }

        // Make sure we actually have something to update.
        if (!existing.equals(entity)) {
            // If we're making the update for every owner using the content, don't bother creating
            // a new version -- just do a raw update.
            if (this.updateInPlace || existing.getOwners().size() == 1) {
                // The org receiving the update is the only org using it. We can do an in-place
                // update here.
                existing.merge(entity);
                entity = existing;

                this.contentCurator.merge(entity);

                if (regenerateEntitlementCerts) {
                    List<Product> affectedProducts = this.productCurator.getProductsWithContent(
                        owner, Arrays.asList(existing.getId())
                    );

                    // TODO:
                    // regenerate entitlements of all products using this content for all owners here.
                }
            }
            else {
                List<Owner> owners = Arrays.asList(owner);

                // This org isn't the only org using the content. We need to create a new content
                // instance and move the org over to the new content.
                Content copy = (Content) entity.clone();

                // Clear the UUID so Hibernate doesn't think our copy is a detached entity
                copy.setUuid(null);

                // Get products that currently use this content...
                List<Product> affectedProducts = this.productCurator.getProductsWithContent(
                    owner, Arrays.asList(existing.getId())
                );

                // Set the owner so when we create it, we don't end up with duplicate keys...
                existing.removeOwner(owner);
                copy.setOwners(owners);

                this.contentCurator.merge(existing);
                copy = this.contentCurator.create(copy);

                // Update the products using this content so they are regenerated using the new
                // content
                for (Product product : affectedProducts) {
                    product = (Product) product.clone();

                    for (ProductContent pc : product.getProductContent()) {
                        if (existing == pc.getContent() || existing.equals(pc.getContent())) {
                            pc.setContent(copy);
                        }
                    }

                    this.productManager.updateProduct(product, owner, regenerateEntitlementCerts);
                }

                entity = this.contentCurator.updateOwnerContentReferences(existing, copy, owners);

                // TODO:
                // regenerate entitlements for all affected products only for the given org
            }
        }

        return entity;
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
     * @throws NullPointerException
     *  if the provided content entity is null
     *
     * @return
     *  a list of products affected by the removal of the given content
     */
    @Transactional
    public List<Product> removeContent(Content entity, Owner owner, boolean regenerateEntitlementCerts) {
        log.debug("Removing content for org: {}, {}", entity, owner);

        if (entity == null) {
            throw new NullPointerException("entity");
        }

        // This has to fetch a new instance, or we'll be unable to compare the objects
        Content existing = this.contentCurator.lookupById(owner, entity.getId());

        if (existing == null) {
            // If we're doing an exclusive update, this should be an error condition
            throw new IllegalStateException("Content has not yet been created");
        }

        List<Product> affectedProducts =
            this.productCurator.getProductsWithContent(owner, Arrays.asList(existing.getId()));

        existing.removeOwner(owner);
        if (existing.getOwners().size() == 0) {
            this.contentCurator.delete(existing);
        }

        // Clean up any dangling references to content
        this.contentCurator.removeOwnerContentReferences(existing, Arrays.asList(owner));

        // Update affected products and regenerate their certs
        List<Product> updatedAffectedProducts = new LinkedList<Product>();
        List<Content> contentList = Arrays.asList(existing);

        for (Product product : affectedProducts) {
            product = this.productManager.removeProductContent(
                product, contentList, owner, regenerateEntitlementCerts
            );

            updatedAffectedProducts.add(product);
        }

        return updatedAffectedProducts;
    }

}
