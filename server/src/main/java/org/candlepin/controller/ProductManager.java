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
import java.util.LinkedList;
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

        Product existing = this.ownerProductCurator.getProductById(owner, entity.getId());

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

        // TODO:
        // We, currently, do not trigger a refresh after updating a product. At present this is an
        // exercise left to the caller, but perhaps we should be doing that here automatically?

        // This has to fetch a new instance, or we'll be unable to compare the objects
        Product existing = this.ownerProductCurator.getProductById(owner.getId(), entity.getId());

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

        log.debug("Checking {} alternate product versions", alternateVersions.size());
        for (Product alt : alternateVersions) {
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
        //
        // NOTE: This won't revert changes made to collections which have automatic orphan removal.
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
    public void removeProduct(Product entity, Owner owner) {
        log.debug("Removing product from owner: {}, {}", entity, owner);

        if (entity == null) {
            throw new NullPointerException("entity");
        }

        // This has to fetch a new instance, or we'll be unable to compare the objects
        Product existing = this.ownerProductCurator.getProductById(owner, entity.getId());

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


    // TODO:
    // Not sure if we'll need this or not. Don't feel like writing a test for it at the moment, so
    // I'm leaving it disabled until we have a need for it. -C
    // public Product addContentToProduct(Product product, Collection<Content> content, boolean enabled,
    //     Owner owner, boolean regenerateEntitlementCerts) {

    //     Collection<ProductContent> productContent = new LinkedList<ProductContent>();

    //     for (Content current : content) {
    //         productContent.add(new ProductContent(product, current, enabled));
    //     }

    //     return this.addContentToProduct(product, productContent, owner, regenerateEntitlementCerts);
    // }

    /**
     * Adds the specified content to the product, effective for the given owner. If the product is
     * already mapped to one of the content instances provided, the mapping will be updated to
     * reflect the configuration of the mapping provided.
     *
     * @param product
     *  The product to which content should be added
     *
     * @param content
     *  A collection of ProductContent instances referencing the content to add to the product
     *
     * @param owner
     *  The owner of the product to update
     *
     * @param regenerateEntitlementCerts
     *  Whether or not changes made to the product should trigger the regeneration of entitlement
     *  certificates for affected consumers
     *
     * @return
     *  the updated product entity, or a new product entity
     */
    public Product addContentToProduct(Product product, Collection<ProductContent> content, Owner owner,
        boolean regenerateEntitlementCerts) {

        if (this.ownerProductCurator.isProductMappedToOwner(product, owner)) {
            boolean changed = false;
            Collection<ProductContent> original = new LinkedList<ProductContent>(product.getProductContent());
            Collection<ProductContent> modified = new LinkedList<ProductContent>();

            for (ProductContent pc : content) {
                ProductContent current = null;
                Content lcref = pc.getContent();

                for (ProductContent test : original) {
                    Content rcref = test.getContent();

                    // Check if the two objects are referencing the same content...
                    boolean cequal = (lcref == rcref || (lcref != null && rcref != null &&
                        lcref.getId() != null && lcref.getId().equals(rcref.getId())));

                    if (cequal) {
                        current = test;
                        break;
                    }
                }

                if (current == null) {
                    pc.setProduct(product);
                    modified.add(pc);

                    changed = true;
                }
                else if (current.isEnabled() != pc.isEnabled() || !current.getContent().equals(lcref)) {
                    // If the enabled state or content state differs, update them.
                    // Note that we're assuming the products are already equal here.
                    pc.setProduct(product);
                    modified.add(pc);

                    changed = true;
                }
                else {
                    modified.add(current);
                }
            }

            if (changed) {
                product.setProductContent(modified);
                Product updated = this.updateProduct(product, owner, regenerateEntitlementCerts);

                // If the product instance changed as a result of a call to update product, we
                // need to restore the content set so we don't erroneously affect other owners
                // using the old instance.
                if (product != updated) {
                    product.setProductContent(original);
                }

                return updated;
            }
        }

        return product;
    }

    // TODO:
    // Not sure if needed; also, likely broken. This should probably be axed before merging.
    // public Product setProductContent(Product product, Collection<Content> content, Owner owner,
    //     boolean regenerateEntitlementCerts) {

    //     if (this.ownerProductCurator.isProductMappedToOwner(product, owner)) {
    //         Collection<ProductContent> original = product.getProductContent();

    //         product.setContent(content);
    //         Product updated = this.updateProduct(product, owner, regenerateEntitlementCerts);

    //         if (product != updated) {
    //             // We got a different instance out, which likely means a convergence or divergence.
    //             // Restore the original content on our input product so we don't accidentally apply
    //             // the change to every owner using it.
    //             product.setProductContent(original);
    //         }

    //         return updated;
    //     }

    //     return product;
    // }

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
                Product updated = this.updateProduct(product, owner, regenerateEntitlementCerts);

                // Impl note:
                // The product changed, but it's likely orphan removal botched our content set for
                // the base product. We should fix that here. If Hibernate ever stops using goofy
                // timing for when it performs orphan removal, this can be removed.
                if (product != updated) {
                    product.getProductContent().addAll(remove);
                }

                return updated;
            }
        }

        return product;
    }
}
