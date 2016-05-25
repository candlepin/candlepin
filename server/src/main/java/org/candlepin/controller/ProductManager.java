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
import org.candlepin.model.Content;
import org.candlepin.model.Owner;
import org.candlepin.model.OwnerProductCurator;
import org.candlepin.model.Product;
import org.candlepin.model.ProductContent;
import org.candlepin.model.ProductCurator;

import com.google.inject.Inject;
import com.google.inject.persist.Transactional;

import org.hibernate.Session;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;



/**
 * The ProductManager class provides methods for creating, updating and removing product instances
 * which also perform the cleanup and general maintenance necessary to keep product state in sync
 * with other objects which reference them.
 * <p></p>
 * The methods provided by this class are the prefered methods to use for CRUD operations on
 * products, to ensure product versioning and linking is handled properly.
 */
public class ProductManager {
    public static Logger log = LoggerFactory.getLogger(ProductManager.class);

    private ProductCurator productCurator;
    private OwnerProductCurator ownerProductCurator;
    private EntitlementCertificateGenerator entitlementCertGenerator;

    @Inject
    public ProductManager(ProductCurator productCurator, OwnerProductCurator ownerProductCurator,
        EntitlementCertificateGenerator entitlementCertGenerator, Configuration config) {

        this.productCurator = productCurator;
        this.ownerProductCurator = ownerProductCurator;
        this.entitlementCertGenerator = entitlementCertGenerator;
    }

    /**
     * Creates a new Product for the given owner, potentially using a different version than the
     * entity provided if a matching entity has already been registered for another owner.
     *
     * @param entity
     *  A Product instance representing the product to create
     *
     * @param owner
     *  The owner for which to create the product
     *
     * @throws IllegalStateException
     *  if this method is called with an entity already exists in the backing database for the given
     *  owner
     *
     * @throws NullPointerException
     *  if the provided product entity is null
     *
     * @return
     *  a new Product instance representing the specified product for the given owner
     */
    @Transactional
    public Product createProduct(Product entity, Owner owner) {
        log.debug("Creating new product for org: {}, {}", entity, owner);

        Product existing = this.productCurator.lookupById(owner, entity.getId());

        if (existing != null) {
            // If we're doing an exclusive creation, this should be an error condition
            throw new IllegalStateException("Product has already been created");
        }

        // Check if we have an alternate version we can use instead.
        List<Product> alternateVersions = this.productCurator
            .getProductsByVersion(entity.getId(), entity.hashCode())
            .list();

        for (Product alt : alternateVersions) {
            if (alt.equals(entity)) {
                // If we're "creating" a product, we shouldn't have any other object references to
                // update for this product. Instead, we'll just add the new owner to the product.
                this.ownerProductCurator.mapProductToOwner(alt, owner);
                return alt;
            }
        }

        // No other owners have matching version of this product. Since it's net new, we set the
        // owners explicitly to the owner given to ensure we don't accidentally clobber other owner
        // mappings
        entity = this.productCurator.create(entity);
        this.ownerProductCurator.mapProductToOwner(entity, owner);

        return entity;
    }

    /**
     * Updates the specified product instance, creating a new version of the product as necessary.
     * The product instance returned by this method is not guaranteed to be the same instance passed
     * in. As such, once this method has been called, callers should only use the instance output by
     * this method.
     *
     * @param entity
     *  The product entity to update
     *
     * @param owner
     *  The owner for which to update the product
     *
     * @param regenerateEntitlementCerts
     *  Whether or not changes made to the product should trigger the regeneration of entitlement
     *  certificates for affected consumers
     *
     * @throws IllegalStateException
     *  if this method is called with an entity does not exist in the backing database for the given
     *  owner
     *
     * @throws IllegalArgumentException
     *  if either the provided product entity or owner are null
     *
     * @return
     *  the updated product entity, or a new product entity
     */
    @Transactional
    public Product updateProduct(Product entity, Owner owner, boolean regenerateEntitlementCerts) {
        log.debug("Applying product update for org: {}, {}", entity, owner);

        if (entity == null) {
            throw new IllegalArgumentException("entity");
        }

        if (owner == null) {
            throw new IllegalArgumentException("owner");
        }

        // This has to fetch a new instance, or we'll be unable to compare the objects
        Product existing = this.productCurator
            .fetchById(owner.getId(), entity.getId())
            .uniqueResult();

        if (existing == null) {
            // If we're doing an exclusive update, this should be an error condition
            throw new IllegalStateException("Product has not yet been created");
        }

        if (existing != entity) {
            // We're operating on a copy of the entity. We'll merge the changes into the existing
            // entity and then continue working with that.
            existing.merge(entity);
        }

        // Check for newer versions of the same product. We want to try to dedupe as much data as we
        // can, and if we have a newer version of the product (which matches the version provided by
        // the caller), we can just point the given orgs to the new product instead of giving them
        // their own version.
        // This is probably going to be a very expensive operation, though.
        List<Product> alternateVersions = this.productCurator
            .getProductsByVersion(existing.getId(), existing.hashCode())
            .list();

        log.debug("Checking {} alternate versions", alternateVersions.size());
        for (Product alt : alternateVersions) {
            log.debug("Checking alternate version: {}", alt);

            // Skip ourselves if we happen across it
            if (alt != existing && alt.equals(existing)) {
                log.debug("Converging product with existing: {} => {}", existing, alt);

                List<Owner> owners = Arrays.asList(owner);

                existing = this.ownerProductCurator.updateOwnerProductReferences(existing, alt, owners);

                if (regenerateEntitlementCerts) {
                    this.entitlementCertGenerator.regenerateCertificatesOf(
                        owners, Arrays.asList(existing), true
                    );
                }

                return existing;
            }
            else {
                if (alt == existing) {
                    log.debug("Found own product; skipping");
                } else if (!alt.equals(existing)) {
                    log.debug("Products are not actually equal");
                } else {
                    log.debug("Something else entirely happened here");
                }
            }
        }

        // No alternate versions with which to converge. Check if we can do an in-place update instead
        if (this.ownerProductCurator.getOwnerCount(existing) == 1) {
            log.debug("Applying in-place update to product: {}", existing);

            this.productCurator.merge(existing);

            if (regenerateEntitlementCerts) {
                this.entitlementCertGenerator.regenerateCertificatesOf(Arrays.asList(existing), true);
            }

            return existing;
        }

        // Product is shared by multiple owners; we have to diverge here
        log.debug("Forking product and applying update: {}", existing);

        // This org isn't the only org using the product. We need to create a new product
        // instance and move the org over to the new product.
        List<Owner> owners = Arrays.asList(owner);
        Product copy = (Product) existing.clone();

        // Now that we have a copy with the pending changes, refresh the existing version so we
        // don't apply those changes for other orgs
        this.productCurator.refresh(existing);

        // Clear the UUID so Hibernate doesn't think our copy is a detached entity
        copy.setUuid(null);

        copy = this.productCurator.create(copy);
        copy = this.ownerProductCurator.updateOwnerProductReferences(existing, copy, owners);

        if (regenerateEntitlementCerts) {
            this.entitlementCertGenerator.regenerateCertificatesOf(
                owners, Arrays.asList(copy), true
            );
        }

        return copy;
    }

    /**
     * Removes the specified product from the given owner. If the product is in use by multiple
     * owners, the product will not actually be deleted, but, instead, will simply by removed from
     * the given owner's visibility.
     *
     * @param entity
     *  The product entity to remove
     *
     * @param owner
     *  The owner for which to remove the product
     *
     * @throws IllegalStateException
     *  if this method is called with an entity does not exist in the backing database for the given
     *  owner, or if the product is currently in use by one or more subscriptions/pools
     *
     * @throws NullPointerException
     *  if the provided product entity is null
     */
    @Transactional
    public void removeProduct(Product entity, Owner owner) {
        log.debug("Removing product from owner: {}, {}", entity, owner);

        if (entity == null) {
            throw new NullPointerException("entity");
        }

        // This has to fetch a new instance, or we'll be unable to compare the objects
        Product existing = this.productCurator.lookupById(owner, entity.getId());

        if (existing == null) {
            // If we're doing an exclusive update, this should be an error condition
            throw new IllegalStateException("Product has not yet been created");
        }

        if (this.productCurator.productHasSubscriptions(existing, owner)) {
            throw new IllegalStateException("Product is currently in use by one or more pools");
        }

        this.ownerProductCurator.removeOwnerFromProduct(existing, owner);
        if (this.ownerProductCurator.getOwnerCount(existing) == 0) {
            // No one is using this product anymore; delete the entity
            this.productCurator.delete(existing);
        }
        else {
            // Clean up any dangling references to content
            this.ownerProductCurator.removeOwnerProductReferences(existing, Arrays.asList(owner));
        }
    }

    /**
     * Removes the specified content from the given product for a single owner. The changes made to
     * the product may result in the convergence or divergence of product versions.
     *
     * @param product
     *  the product from which to remove content
     *
     * @param content
     *  the content to remove
     *
     * @param owner
     *  the owner for which the change should take effect
     *
     * @param regenerateEntitlementCerts
     *  Whether or not changes made to the product should trigger the regeneration of entitlement
     *  certificates for affected consumers
     *
     * @return
     *  the updated product instance
     */
    @Transactional
    public Product removeProductContent(Product product, Collection<Content> content, Owner owner,
        boolean regenerateEntitlementCerts) {

        Set<ProductContent> remove = new HashSet<ProductContent>();

        if (this.ownerProductCurator.isProductMappedToOwner(product, owner)) {
            for (Content test : content) {
                for (ProductContent pc : product.getProductContent()) {
                    if (test == pc.getContent() || test.equals(pc.getContent())) {
                        remove.add(pc);
                    }
                }
            }

            if (remove.size() > 0) {
                product.getProductContent().removeAll(remove);
                return this.updateProduct(product, owner, regenerateEntitlementCerts);
            }
        }

        return product;
    }

}
