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

import org.candlepin.dto.api.v1.BrandingDTO;
import org.candlepin.dto.api.v1.ContentDTO;
import org.candlepin.dto.api.v1.ProductDTO;
import org.candlepin.dto.api.v1.ProductDTO.ProductContentDTO;
import org.candlepin.model.Branding;
import org.candlepin.model.Content;
import org.candlepin.model.Owner;
import org.candlepin.model.OwnerContentCurator;
import org.candlepin.model.OwnerProduct;
import org.candlepin.model.OwnerProductCurator;
import org.candlepin.model.Product;
import org.candlepin.model.ProductContent;
import org.candlepin.model.ProductCurator;
import org.candlepin.service.model.BrandingInfo;
import org.candlepin.service.model.ContentInfo;
import org.candlepin.service.model.ProductContentInfo;
import org.candlepin.service.model.ProductInfo;
import org.candlepin.util.Traceable;
import org.candlepin.util.TraceableParam;
import org.candlepin.util.Util;

import com.google.inject.Inject;
import com.google.inject.persist.Transactional;

import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;


/**
 * The ProductManager class provides methods for creating, updating and removing product instances
 * which also perform the cleanup and general maintenance necessary to keep product state in sync
 * with other objects which reference them.
 * <p></p>
 * The methods provided by this class are the prefered methods to use for CRUD operations on
 * products, to ensure product versioning and linking is handled properly.
 */
public class ProductManager {
    private static Logger log = LoggerFactory.getLogger(ProductManager.class);

    private EntitlementCertificateGenerator entitlementCertGenerator;
    private OwnerContentCurator ownerContentCurator;
    private OwnerProductCurator ownerProductCurator;
    private ProductCurator productCurator;

    @Inject
    public ProductManager(EntitlementCertificateGenerator entitlementCertGenerator,
        OwnerContentCurator ownerContentCurator, OwnerProductCurator ownerProductCurator,
        ProductCurator productCurator) {

        this.entitlementCertGenerator = entitlementCertGenerator;
        this.ownerContentCurator = ownerContentCurator;
        this.ownerProductCurator = ownerProductCurator;
        this.productCurator = productCurator;
    }

    /**
     * Creates a new Product for the given owner, potentially using a different version than the
     * entity provided if a matching entity has already been registered for another owner.
     *
     * @param dto
     *  A product DTO instance representing the product to create
     *
     * @param owner
     *  The owner for which to create the product
     *
     * @throws IllegalArgumentException
     *  if dto is null or incomplete, or owner is null
     *
     * @return
     *  a new Product instance representing the specified product for the given owner
     */
    public Product createProduct(ProductDTO dto, Owner owner) {
        if (dto == null) {
            throw new IllegalArgumentException("dto is null");
        }

        if (owner == null) {
            throw new IllegalArgumentException("owner is null");
        }

        if (dto.getId() == null || dto.getName() == null) {
            throw new IllegalArgumentException("dto is incomplete");
        }

        if (this.ownerProductCurator.productExists(owner, dto.getId())) {
            throw new IllegalStateException("product has already been created: " + dto.getId());
        }

        // TODO: More DTO validation here...?

        Product entity = new Product(dto.getId(), dto.getName());
        this.applyProductChanges(entity, dto, owner);

        log.debug("Creating new product for org: {}, {}", entity, owner);

        // Check if we have an alternate version we can use instead.
        List<Product> alternateVersions = this.ownerProductCurator.getProductsByVersions(
            owner, Collections.singletonMap(entity.getId(), entity.getEntityVersion()))
            .list();

        for (Product alt : alternateVersions) {
            if (alt.equals(entity)) {
                // If we're "creating" a product, we shouldn't have any other object references to
                // update for this product. Instead, we'll just add the new owner to the product.
                this.ownerProductCurator.mapProductToOwner(alt, owner);
                return alt;
            }
        }

        entity = this.productCurator.create(entity);
        this.ownerProductCurator.mapProductToOwner(entity, owner);

        return entity;
    }

    /**
     * Updates the product entity represented by the given DTO with the changes provided by the
     * DTO.
     *
     * @param update
     *  A product DTO representing the product to update and the updates to apply
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
     *  if update or owner is null
     *
     * @return
     *  the updated product entity
     */
    @Transactional
    public Product updateProduct(ProductDTO update, Owner owner, boolean regenerateEntitlementCerts) {
        // TODO: FIXME: please change this to stop requiring DTOs. It's so painful to use.

        if (update == null) {
            throw new IllegalArgumentException("update is null");
        }

        if (update.getId() == null) {
            throw new IllegalArgumentException("update is incomplete");
        }

        if (owner == null) {
            throw new IllegalArgumentException("owner is null");
        }

        // Resolve the entity to ensure we're working with the merged entity, and to ensure it's
        // already been created.
        Product entity = this.ownerProductCurator.getProductById(owner, update.getId());

        if (entity == null) {
            // If we're doing an exclusive update, this should be an error condition
            throw new IllegalStateException("Product has not yet been created");
        }

        // Make sure we actually have a change to apply
        if (!isChangedBy(entity, update)) {
            return entity;
        }

        log.debug("Applying product update for org: {} => {}, {}", update, entity, owner);
        Product updated = this.applyProductChanges((Product) entity.clone(), update, owner);

        // Check for newer versions of the same product. We want to try to dedupe as much data as we
        // can, and if we have a newer version of the product (which matches the version provided by
        // the caller), we can just point the given orgs to the new product instead of giving them
        // their own version.
        // This is probably going to be a very expensive operation, though.
        List<Product> alternateVersions = this.ownerProductCurator.getProductsByVersions(
            owner, Collections.singletonMap(updated.getId(), updated.getEntityVersion()))
            .list();

        log.debug("Checking {} alternate product versions", alternateVersions.size());
        for (Product alt : alternateVersions) {
            if (alt.equals(updated)) {
                log.debug("Converging product with existing: {} => {}", updated, alt);

                this.ownerProductCurator.updateOwnerProductReferences(owner,
                    Collections.<String, String>singletonMap(entity.getUuid(), alt.getUuid()));

                if (regenerateEntitlementCerts) {
                    this.entitlementCertGenerator.regenerateCertificatesOf(
                        Arrays.asList(owner), Arrays.asList(alt), true);
                }

                return alt;
            }
        }

        // Temporarily (?) disabled. If we ever move to clustered caching (rather than per-instance
        // caching, this branch should be re-enabled.
        /*
        // No alternate versions with which to converge. Check if we can do an in-place update instead
        if (this.ownerProductCurator.getOwnerCount(updated) < 2) {
            log.debug("Applying in-place update to product: {}", updated);

            updated = this.productCurator.merge(this.applyProductChanges(entity, update, owner));

            if (regenerateEntitlementCerts) {
                this.entitlementCertGenerator.regenerateCertificatesOf(
                    Arrays.asList(owner), Arrays.asList(updated), true
                );
            }

            return updated;
        }
        */

        // Product is shared by multiple owners; we have to diverge here
        log.debug("Forking product and applying update: {}", updated);

        // Clear the UUID so Hibernate doesn't think our copy is a detached entity
        updated.setUuid(null);
        updated = this.productCurator.create(updated);

        this.ownerProductCurator.updateOwnerProductReferences(owner,
            Collections.<String, String>singletonMap(entity.getUuid(), updated.getUuid()));

        if (regenerateEntitlementCerts) {
            this.entitlementCertGenerator.regenerateCertificatesOf(
                Arrays.asList(owner), Arrays.asList(updated), true);
        }

        return updated;
    }

    /**
     * Creates or updates products from the given product info, using the provided content for
     * content lookup and resolution.
     * <p></p>
     * The product info provided in the given map should be mapped by the product's Red Hat ID. If
     * the mappings are incorrect or inconsistent, the result of this method is undefined.
     *
     * @deprecated
     *  This method has been replaced by the RefreshWorker and its various components. New code
     *  should avoid using this method if at all possible, instead opting to use the RefreshWorker.
     *
     * @param owner
     *  The owner for which to import the given product
     *
     * @param productData
     *  A mapping of Red Hat product ID to product DTOs to import
     *
     * @param importedContent
     *  A mapping of Red Hat content ID to content instances to use to lookup and resolve content
     *  references on the provided product DTOs.
     *
     * @return
     *  A mapping of Red Hat content ID to content entities representing the imported content
     */
    @Transactional
    @Traceable
    @Deprecated
    public ImportResult<Product> importProducts(@TraceableParam("owner") Owner owner,
        Map<String, ? extends ProductInfo> productData, Map<String, Content> importedContent) {

        // TODO:
        // This method currently uses a bunch of copying of data to get around an "issue" with
        // Hibernate auto-committing changes to an entity before we're necessarily ready to do so.
        // This is something that can be configured, but is likely behavior we expect elsewhere. As
        // such, if that were ever to be evaluated/changed, this method should be updated to no
        // longer perform a bunch of unnecessary duplication of product instances.

        if (owner == null) {
            throw new IllegalArgumentException("owner is null");
        }

        if (productData == null || productData.isEmpty()) {
            // Nothing to import
            return new ImportResult<>();
        }

        Map<String, ProductInfo> productWithNoProvidedProducts = new HashMap<>();
        Map<String, ProductInfo> productWithProvidedProducts = new HashMap<>();

        for (ProductInfo pinfo : productData.values()) {
            if (pinfo.getProvidedProducts() != null) {
                if (!pinfo.getProvidedProducts().isEmpty()) {
                    productWithProvidedProducts.put(pinfo.getId(), pinfo);
                }
                else {
                    productWithNoProvidedProducts.put(pinfo.getId(), pinfo);
                }
            }
            else {
                productWithNoProvidedProducts.put(pinfo.getId(), pinfo);
            }
        }

        ImportResult<Product> importResultR1 = processProductImport(owner,
            productWithNoProvidedProducts, importedContent);
        ImportResult<Product> importResultR2 = processProductImport(owner, productWithProvidedProducts,
            importedContent);

        return mergedResultSet(importResultR1, importResultR2);
    }

    /**
     * Creates or updates products from the given product info, using the provided content for
     * content lookup and resolution.
     * <p></p>
     * The product info provided in the given map should be mapped by the product's Red Hat ID. If
     * the mappings are incorrect or inconsistent, the result of this method is undefined.
     *
     * @deprecated
     *  This method has been replaced by the RefreshWorker and its various components. New code
     *  should avoid using this method if at all possible, instead opting to use the RefreshWorker.
     *
     * @param owner
     *  The owner for which to import the given product
     *
     * @param productData
     *  A mapping of Red Hat product ID to product DTOs to import
     *
     * @param importedContent
     *  A mapping of Red Hat content ID to content instances to use to lookup and resolve content
     *  references on the provided product DTOs.
     *
     * @return
     *  A mapping of Red Hat content ID to content entities representing the imported content
     */
    @Traceable
    @Deprecated
    public ImportResult<Product> processProductImport(@TraceableParam("owner") Owner owner,
        Map<String, ? extends ProductInfo> productData, Map<String, Content> importedContent) {

        ImportResult<Product> importResult = new ImportResult<>();
        Map<String, Product> skippedProducts = importResult.getSkippedEntities();
        Map<String, Product> createdProducts = importResult.getCreatedEntities();
        Map<String, Product> updatedProducts = importResult.getUpdatedEntities();

        Map<String, Integer> productVersions = new HashMap<>();
        Map<String, Product> sourceProducts = new HashMap<>();
        Map<String, List<Product>> existingVersions = new HashMap<>();
        List<OwnerProduct> ownerProductBuffer = new LinkedList<>();

        // - Divide imported products into sets of updates and creates
        log.debug("Fetching existing products for update...");
        for (Product product : this.ownerProductCurator.getProductsByIds(owner, productData.keySet())) {
            ProductInfo update = productData.get(product.getId());

            if (product.isLocked() && !isChangedBy(product, update)) {
                // This product won't be changing, so we'll just pretend it's not being imported at all
                skippedProducts.put(product.getId(), product);
                continue;
            }

            sourceProducts.put(product.getId(), product);
            product = this.applyProductChanges((Product) product.clone(), update, importedContent);
            product = this.applyProvidedProductChanges(product, update, owner);
            // Prevent this product from being changed by our API
            product.setLocked(true);

            updatedProducts.put(product.getId(), product);
            productVersions.put(product.getId(), product.getEntityVersion());
        }

        log.debug("Validating new products...");
        for (ProductInfo update : productData.values()) {
            if (!skippedProducts.containsKey(update.getId()) &&
                !updatedProducts.containsKey(update.getId())) {

                // Ensure the product is minimally populated
                if (update.getId() == null || update.getName() == null) {
                    throw new IllegalStateException("Product data is incomplete: " + update);
                }

                Product product = new Product(update.getId(), update.getName());
                product = this.applyProductChanges(product, update, importedContent);
                product = this.applyProvidedProductChanges(product, update, owner);
                // Prevent this product from being changed by our API
                product.setLocked(true);

                createdProducts.put(product.getId(), product);
                productVersions.put(product.getId(), product.getEntityVersion());
            }
        }

        log.debug("Checking for existing product versions...");
        for (Product alt : this.ownerProductCurator.getProductsByVersions(owner, productVersions)) {
            List<Product> alternates = existingVersions.get(alt.getId());
            if (alternates == null) {
                alternates = new LinkedList<>();
                existingVersions.put(alt.getId(), alternates);
            }

            alternates.add(alt);
        }

        productVersions.clear();
        productVersions = null;

        // We're about to start modifying the maps, so we need to clone the created set before we
        // start adding the update forks to it.
        Map<String, Product> stagedEntities = new HashMap<>(createdProducts);

        // Process the created group...
        // Check our created set for existing versions:
        //  - If there's an existing version, we'll remove the staged entity from the creation
        //    set, and stage an owner-product mapping for the existing version
        //  - Otherwise, we'll stage the new entity for persistence by leaving it in the created
        //    set, and stage an owner-product mapping to the new entity
        Iterator<Product> iterator = stagedEntities.values().iterator();
        createdProductLoop: while (iterator.hasNext()) {
            Product created = iterator.next();
            List<Product> alternates = existingVersions.get(created.getId());

            if (alternates != null) {
                for (Product alt : alternates) {
                    if (created.equals(alt)) {
                        ownerProductBuffer.add(new OwnerProduct(owner, alt));
                        createdProducts.put(alt.getId(), alt);
                        iterator.remove();

                        continue createdProductLoop;
                    }
                }
            }

            ownerProductBuffer.add(new OwnerProduct(owner, created));
        }

        // Process the updated group...
        // Check our updated set for existing versions:
        //  - If there's an existing versions, we'll update the update set to point to the existing
        //    version
        //  - Otherwise, we need to stage the updated entity for persistence
        updatedProductLoop: for (Map.Entry<String, Product> entry : updatedProducts.entrySet()) {
            Product updated = entry.getValue();
            List<Product> alternates = existingVersions.get(updated.getId());

            if (alternates != null) {
                for (Product alt : alternates) {
                    if (updated.equals(alt)) {
                        updated = alt;
                        entry.setValue(alt);

                        continue updatedProductLoop;
                    }
                }
            }

            // We need to stage the updated entity for persistence. We'll reuse the now-empty
            // createdProducts map for this.
            updated.setUuid(null);
            stagedEntities.put(updated.getId(), updated);
        }

        // Persist our staged entities
        // We probably don't want to evict the products yet, as they'll appear as unmanaged if
        // they're used later. However, the join objects can be evicted safely since they're only
        // really used here.
        log.debug("Persisting product changes...");
        this.productCurator.saveAll(stagedEntities.values(), true, false);
        this.ownerProductCurator.saveAll(ownerProductBuffer, true, true);

        // Perform bulk reference update
        Map<String, String> productUuidMap = new HashMap<>();
        for (Product update : updatedProducts.values()) {
            Product source = sourceProducts.get(update.getId());

            productUuidMap.put(source.getUuid(), update.getUuid());
        }

        this.ownerProductCurator.updateOwnerProductReferences(owner, productUuidMap);

        // Return
        return importResult;
    }

    /**
     * A utility method to merge imported result of products.
     *
     * @param resultSetR1
     *  Imported Result set of Products.
     *
     * @param resultSetR2
     *  Imported Result set of Products.
     *
     * @return
     *  Merged imported Result set of Products.
     */
    private ImportResult<Product> mergedResultSet(ImportResult<Product> resultSetR1,
        ImportResult<Product> resultSetR2) {
        resultSetR1.getCreatedEntities().putAll(resultSetR2.getCreatedEntities());
        resultSetR1.getUpdatedEntities().putAll(resultSetR2.getUpdatedEntities());
        resultSetR1.getSkippedEntities().putAll(resultSetR2.getSkippedEntities());
        return resultSetR1;
    }

    /**
     * Removes the specified product from the given owner. If the product is in use by multiple
     * owners, the product will not actually be deleted, but, instead, will simply by removed from
     * the given owner's visibility.
     *
     * If the product is/ends up not being shared by any owners, then it will be removed by the
     * {@link OrphanCleanupJob} which runs periodically.
     *
     * @param owner
     *  The owner for which to remove the product
     *
     * @param entity
     *  The product entity to remove
     *
     * @throws IllegalStateException
     *  if this method is called with an entity does not exist in the backing database for the given
     *  owner, or if the product is currently in use by one or more subscriptions/pools
     *
     * @throws IllegalArgumentException
     *  if entity or owner is null
     */
    public void removeProduct(Owner owner, Product entity) {
        if (owner == null) {
            throw new IllegalArgumentException("owner is null");
        }

        if (entity == null) {
            throw new IllegalArgumentException("entity is null");
        }

        // This has to fetch a new instance, or we'll be unable to compare the objects
        Product existing = this.ownerProductCurator.getProductById(owner, entity.getId());
        if (existing == null) {
            // If we're doing an exclusive update, this should be an error condition
            throw new IllegalStateException("Product has not yet been created");
        }



        this.removeProductsByUuids(owner, Arrays.asList(existing.getUuid()));
    }

    /**
     * Removes all products from the specified owner.
     *
     * Products will have any references to the owner removed. Unreferenced products are not removed by
     * this method, but by the {@link OrphanCleanupJob} which runs periodically.
     *
     * @param owner
     *  The owner from which to remove all products
     *
     * @throws IllegalArgumentException
     *  if owner is null
     */
    @Transactional
    public void removeAllProducts(Owner owner) {
        this.removeProductsByUuids(owner, this.ownerProductCurator.getProductUuidsByOwner(owner));
    }

    /**
     * Removes the specified products from the given owner.
     *
     * Products will have any references to the owner removed. Unreferenced products are not removed by
     * this method, but by the {@link OrphanCleanupJob} which runs periodically.
     *
     * @param owner
     *  The owner from which to remove products
     *
     * @param products
     *  A collection of products to remove from the owner
     *
     * @throws IllegalArgumentException
     *  if owner is null
     *
     * @throws IllegalStateException
     *  if any of the given products are still associated with one or more subscriptions
     */
    public void removeProducts(Owner owner, Collection<Product> products) {
        if (products != null && !products.isEmpty()) {
            Map<String, Product> productMap = new HashMap<>();
            for (Product product : products) {
                productMap.put(product.getUuid(), product);
            }

            this.removeProductsByUuids(owner, productMap.keySet());
        }
    }

    /**
     * Removes the specified products from the given owner.
     *
     * Products will have any references to the owner removed. Unreferenced products are not removed by
     * this method, but by the {@link OrphanCleanupJob} which runs periodically.
     *
     * @param owner
     *  The owner from which to remove products
     *
     * @param productUuids
     *  A collection of product UUIDs representing the products to remove from the owner
     *
     * @throws IllegalArgumentException
     *  if owner is null
     *
     * @throws IllegalStateException
     *  if any of the given products are still associated with one or more subscriptions
     */
    public void removeProductsByUuids(Owner owner, Collection<String> productUuids) {
        if (owner == null) {
            throw new IllegalArgumentException("owner is null");
        }

        if (productUuids != null && !productUuids.isEmpty()) {
            // Verify that we don't remove any products if they are in use within the org
            Set<Pair<String, String>> poolRefs = this.productCurator
                .getProductsWithPools(owner.getId(), productUuids);

            if (poolRefs != null && !poolRefs.isEmpty()) {
                throw new IllegalStateException(
                    "One or more products are currently used by one or more pools");
            }

            // Remove owner references to all the products. This will leave the products orphaned,
            // to be eventually deleted by the orphan removal job
            this.ownerProductCurator.removeOwnerProductReferences(owner, productUuids);
        }
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
     *  If entity, update or owner is null
     *
     * @return
     *  The updated product entity
     */
    private Product applyProductChanges(Product entity, ProductDTO update, Owner owner) {
        if (owner == null) {
            throw new IllegalArgumentException("owner is null");
        }

        Map<String, Content> contentMap = new HashMap<>();

        if (update.getProductContent() != null) {
            // Grab all of the content objects at once so we don't hit the DB a few thousand times
            // per update
            for (ProductContentDTO pcd : update.getProductContent()) {
                if (pcd == null || pcd.getContent() == null || pcd.getContent().getId() == null) {
                    throw new IllegalStateException("product content is null or incomplete");
                }

                // We'll populate the map later...
                contentMap.put(pcd.getContent().getId(), null);
            }

            for (Content content : this.ownerContentCurator.getContentByIds(owner, contentMap.keySet())) {
                contentMap.put(content.getId(), content);
            }
        }

        this.applyProvidedProductChanges(entity, update, owner);

        return this.applyProductChanges(entity, update, contentMap);
    }

    /**
     * Applies the changes related to provided products from the given DTO to the specified entity.
     *
     * @param entity
     *  The entity to modify.
     *
     * @param update
     *  The DTO containing the modifications to apply.
     *
     * @param owner
     *  An owner to use for resolving entity references.
     *
     * @return
     *  The updated product entity.
     */
    private Product applyProvidedProductChanges(Product entity, ProductDTO update, Owner owner) {
        if (update.getProvidedProducts() != null) {
            if (update.getProvidedProducts().isEmpty()) {
                entity.getProvidedProducts().clear();
            }
            else {
                entity.getProvidedProducts().clear();
                for (ProductDTO providedProductDTO : update.getProvidedProducts()) {
                    if (providedProductDTO != null && providedProductDTO.getId() != null) {
                        Product newProd = this.ownerProductCurator.getProductById(owner,
                            providedProductDTO.getId());

                        if (newProd != null) {
                            entity.addProvidedProduct(newProd);
                        }
                    }
                }
            }
        }

        return entity;
    }

    /**
     * Applies the changes related to provided products from the given product Info to the specified entity.
     *
     * @param entity
     *  The entity to modify.
     *
     * @param update
     *  The interface containing the modifications to apply.
     *
     * @param owner
     *  An owner to use for resolving entity references.
     *
     * @return
     *  The updated product entity.
     */
    private Product applyProvidedProductChanges(Product entity, ProductInfo update, Owner owner) {
        if (update.getProvidedProducts() != null) {
            if (update.getProvidedProducts().isEmpty()) {
                entity.getProvidedProducts().clear();
            }
            else {
                entity.getProvidedProducts().clear();
                for (ProductInfo providedProductDTO : update.getProvidedProducts()) {
                    if (providedProductDTO != null && providedProductDTO.getId() != null) {
                        Product newProd = this.ownerProductCurator.getProductById(owner,
                            providedProductDTO.getId());

                        if (newProd != null) {
                            entity.addProvidedProduct(newProd);
                        }
                    }
                }
            }
        }

        return entity;
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
     * @param contentMap
     *  A mapping of Red Hat content ID to content entities to use for content resolution
     *
     * @throws IllegalArgumentException
     *  if entity, update or owner is null
     *
     * @return
     *  The updated product entity
     */
    private Product applyProductChanges(Product entity, ProductDTO update, Map<String, Content> contentMap) {
        // TODO:
        // Eventually content should be considered a property of products (ala attributes), so we
        // don't have to do this annoying, nested projection and owner passing. Also, it would
        // solve the issue of forcing content to have only one instance per owner and this logic
        // could live in Product, where it belongs.

        if (entity == null) {
            throw new IllegalArgumentException("entity is null");
        }

        if (update == null) {
            throw new IllegalArgumentException("update is null");
        }

        if (contentMap == null) {
            throw new IllegalArgumentException("contentMap is null");
        }

        if (update.getName() != null) {
            entity.setName(update.getName());
        }

        if (update.getMultiplier() != null) {
            entity.setMultiplier(update.getMultiplier());
        }

        if (update.getAttributes() != null) {
            entity.setAttributes(update.getAttributes());
        }

        if (update.getProductContent() != null) {
            Collection<ProductContent> productContent = new LinkedList<>();

            // Sort the existing ProductContent so we aren't iterating on it several times.
            // TODO: Remove this if/when product content is stored as a map on products.
            Map<String, ProductContent> existingLinks = new HashMap<>();
            for (ProductContent pc : entity.getProductContent()) {
                existingLinks.put(pc.getContent().getId(), pc);
            }

            // Actually process our list of content...
            for (ProductContentDTO pcd : update.getProductContent()) {
                if (pcd == null) {
                    throw new IllegalStateException("Product data contains a null product-content mapping: " +
                        update);
                }

                ContentDTO cdto = pcd.getContent();

                if (cdto == null || cdto.getId() == null) {
                    // This should only happen if something alters a content dto object after
                    // adding it to our link object. This is very bad.
                    throw new IllegalStateException("Product data contains an incomplete product-content " +
                        "mapping: " + update);
                }

                ProductContent existingLink = existingLinks.get(cdto.getId());
                Content content = contentMap.get(cdto.getId());

                if (content == null) {
                    // Content doesn't exist yet -- it should have been created already
                    throw new IllegalStateException("product references content which does not exist: " +
                        cdto);
                }

                if (existingLink == null) {
                    existingLink = new ProductContent(
                        entity, content, pcd.isEnabled() != null ? pcd.isEnabled() : false);
                }
                else {
                    // Build a new content link based on the original but check for changes to the enabled
                    // state. This is because ProductContent is now immutable so we need a new entity
                    // regardless of how little has changed.
                    existingLink = new ProductContent(entity, content, existingLink.isEnabled());
                    if (pcd.isEnabled() != null) {
                        existingLink.setEnabled(pcd.isEnabled());
                    }
                }

                productContent.add(existingLink);
            }

            entity.setProductContent(productContent);
        }

        if (update.getDependentProductIds() != null) {
            entity.setDependentProductIds(update.getDependentProductIds());
        }

        if (update.getBranding() != null) {
            if (update.getBranding().isEmpty()) {
                entity.setBranding(Collections.emptySet());
            }
            else {
                Set<Branding> branding = new HashSet<>();
                for (BrandingDTO brandingDTO : update.getBranding()) {
                    if (brandingDTO != null) {
                        branding.add(new Branding(
                            entity,
                            brandingDTO.getProductId(),
                            brandingDTO.getName(),
                            brandingDTO.getType()
                        ));
                    }
                }
                entity.setBranding(branding);
            }
        }

        return entity;
    }


    /**
     * Determines whether or not this entity would be changed if the given DTO were applied to this
     * object.
     *
     * @param entity
     *  The product entity that would be changed
     *
     * @param dto
     *  The product DTO to check for changes
     *
     * @throws IllegalArgumentException
     *  if dto is null
     *
     * @return
     *  true if this product would be changed by the given DTO; false otherwise
     */
    public static boolean isChangedBy(Product entity, ProductDTO dto) {
        // Check simple properties first
        if (dto.getId() != null && !dto.getId().equals(entity.getId())) {
            return true;
        }

        if (dto.getName() != null && !dto.getName().equals(entity.getName())) {
            return true;
        }

        if (dto.getMultiplier() != null && !dto.getMultiplier().equals(entity.getMultiplier())) {
            return true;
        }

        Collection<String> dependentProductIds = dto.getDependentProductIds();
        if (dependentProductIds != null &&
            !Util.collectionsAreEqual(entity.getDependentProductIds(), dependentProductIds)) {

            return true;
        }

        // Impl note:
        // Depending on how strict we are regarding case-sensitivity and value-representation,
        // this may get us in to trouble. We may need to iterate through the attributes, performing
        // case-insensitive key/value comparison and similiarities (i.e. management_enabled: 1 is
        // functionally identical to Management_Enabled: true, but it will be detected as a change
        // in attributes.
        Map<String, String> attributes = dto.getAttributes();
        if (attributes != null && !attributes.equals(entity.getAttributes())) {
            return true;
        }

        Collection<ProductContentDTO> productContent = dto.getProductContent();
        if (productContent != null) {
            Comparator comparator = (lhs, rhs) -> {
                ProductContent existing = (ProductContent) lhs;
                ProductContentDTO update = (ProductContentDTO) rhs;

                if (existing != null && update != null) {
                    Content content = existing.getContent();
                    ContentDTO cdto = update.getContent();

                    if (content != null && cdto != null) {
                        if (cdto.getUuid() != null ?
                            cdto.getUuid().equals(content.getUuid()) :
                            (cdto.getId() != null && cdto.getId().equals(content.getId()))) {
                            // At this point, we've either matched the UUIDs (which means we're
                            // referencing identical products) or the UUID isn't present on the DTO, but
                            // the IDs match (which means we're pointing toward the same product).

                            return (update.isEnabled() != null &&
                                !update.isEnabled().equals(existing.isEnabled())) ||
                                ContentManager.isChangedBy(content, cdto) ? 1 : 0;
                        }
                    }
                }

                return 1;
            };

            if (!Util.collectionsAreEqual((Collection) entity.getProductContent(),
                (Collection) productContent, comparator)) {

                return true;
            }
        }

        Collection<BrandingDTO> brandingDTOs = dto.getBranding();
        if (brandingDTOs != null) {
            Comparator<BrandingInfo> comparator = BrandingInfo.getBrandingInfoComparator();
            if (!Util.collectionsAreEqual((Collection) entity.getBranding(), (Collection) brandingDTOs,
                comparator)) {
                return true;
            }
        }

        Collection<ProductDTO> providedProducts = dto.getProvidedProducts();

        if (providedProducts != null) {
            // Quick Id Check
            if (!Util.collectionsAreEqual(entity.getProvidedProducts().stream()
                .map(Product::getId)
                .collect(Collectors.toSet()),
                providedProducts.stream()
                .map(ProductDTO::getId)
                .collect(Collectors.toSet()))) {
                return true;
            }

            Comparator productComparator = new Comparator() {
                public int compare(Object lhs, Object rhs) {
                    Product existing = (Product) lhs;
                    ProductDTO update = (ProductDTO) rhs;

                    if (existing != null && update != null) {
                        if (existing.getId().equals(update.getId())) {
                            return ProductManager.isChangedBy(existing, update) ? 1 : 0;
                        }
                    }

                    return 1;
                }
            };

            if (!Util.collectionsAreEqual((Collection) entity.getProvidedProducts(),
                (Collection) dto.getProvidedProducts(), productComparator)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Determines whether or not this entity would be changed if the given update were applied to
     * this object.
     *
     * @param entity
     *  The product entity that would be changed
     *
     * @param update
     *  The product info to check for changes
     *
     * @throws IllegalArgumentException
     *  if update is null
     *
     * @return
     *  true if this product would be changed by the given product info; false otherwise
     */
    public static boolean isChangedBy(ProductInfo entity, ProductInfo update) {

        // Check simple properties first
        if (update.getId() != null && !update.getId().equals(entity.getId())) {
            return true;
        }

        if (update.getName() != null && !update.getName().equals(entity.getName())) {
            return true;
        }

        if (update.getMultiplier() != null && !update.getMultiplier().equals(entity.getMultiplier())) {
            return true;
        }

        Collection<String> dependentProductIds = update.getDependentProductIds();
        if (dependentProductIds != null &&
            !Util.collectionsAreEqual(entity.getDependentProductIds(), dependentProductIds)) {

            return true;
        }

        // Impl note:
        // Depending on how strict we are regarding case-sensitivity and value-representation,
        // this may get us in to trouble. We may need to iterate through the attributes, performing
        // case-insensitive key/value comparison and similiarities (i.e. management_enabled: 1 is
        // functionally identical to Management_Enabled: true, but it will be detected as a change
        // in attributes.
        Map<String, String> attributes = update.getAttributes();
        if (attributes != null && !attributes.equals(entity.getAttributes())) {
            return true;
        }

        Collection<? extends ProductContentInfo> productContent = update.getProductContent();
        if (productContent != null) {
            Comparator comparator = new Comparator() {
                public int compare(Object lhs, Object rhs) {
                    ProductContentInfo existing = (ProductContentInfo) lhs;
                    ProductContentInfo update = (ProductContentInfo) rhs;

                    if (existing != null && update != null) {
                        ContentInfo content = existing.getContent();
                        ContentInfo cdto = update.getContent();

                        if (content != null && cdto != null) {
                            if (cdto.getId() != null && cdto.getId().equals(content.getId())) {
                                // At this point, we've matched content IDs, which means we're pointing
                                // toward the same content.

                                return (update.isEnabled() != null &&
                                    !update.isEnabled().equals(existing.isEnabled())) ||
                                    ContentManager.isChangedBy(content, cdto) ? 1 : 0;
                            }
                        }
                    }

                    return 1;
                }
            };

            if (!Util.collectionsAreEqual((Collection) entity.getProductContent(),
                (Collection) productContent, comparator)) {

                return true;
            }
        }

        if (update.getBranding() != null) {
            Comparator<BrandingInfo> comparator = BrandingInfo.getBrandingInfoComparator();
            if (!Util.collectionsAreEqual((Collection) entity.getBranding(),
                (Collection) update.getBranding(), comparator)) {
                return true;
            }
        }

        if (update.getProvidedProducts() != null) {
            Comparator<ProductInfo> productComparator = new Comparator<ProductInfo>() {
                public int compare(ProductInfo lhs, ProductInfo rhs) {
                    if (lhs != null && rhs != null) {
                        if (lhs.getId().equals(rhs.getId())) {
                            return ProductManager.isChangedBy(lhs, rhs) ? 1 : 0;
                        }
                    }

                    return 1;
                }
            };

            if (!Util.collectionsAreEqual((Collection<ProductInfo>) entity.getProvidedProducts(),
                (Collection<ProductInfo>) update.getProvidedProducts(), productComparator)) {

                return true;
            }
        }

        return false;
    }

    /**
     * Applies the changes from the given product info to the specified entity
     *
     * @param entity
     *  The entity to modify
     *
     * @param update
     *  The ProductInfo containing the modifications to apply
     *
     * @param contentMap
     *  A mapping of Red Hat content ID to content entities to use for content resolution
     *
     * @throws IllegalArgumentException
     *  if entity, update or owner is null
     *
     * @return
     *  The updated product entity
     */
    private Product applyProductChanges(Product entity, ProductInfo update, Map<String, Content> contentMap) {

        // TODO:
        // Eventually content should be considered a property of products (ala attributes), so we
        // don't have to do this annoying, nested projection and owner passing. Also, it would
        // solve the issue of forcing content to have only one instance per owner and this logic
        // could live in Product, where it belongs.

        if (entity == null) {
            throw new IllegalArgumentException("entity is null");
        }

        if (update == null) {
            throw new IllegalArgumentException("update is null");
        }

        if (contentMap == null) {
            throw new IllegalArgumentException("contentMap is null");
        }

        if (update.getName() != null) {
            entity.setName(update.getName());
        }

        if (update.getMultiplier() != null) {
            entity.setMultiplier(update.getMultiplier());
        }

        if (update.getAttributes() != null) {
            entity.setAttributes(update.getAttributes());
        }

        if (update.getProductContent() != null) {
            Collection<ProductContent> productContent = new LinkedList<>();

            // Sort the existing ProductContent so we aren't iterating on it several times.
            // TODO: Remove this if/when product content is stored as a map on products.
            Map<String, ProductContent> existingLinks = new HashMap<>();
            for (ProductContent pc : entity.getProductContent()) {
                existingLinks.put(pc.getContent().getId(), pc);
            }

            // Actually process our list of content...
            for (ProductContentInfo pcd : update.getProductContent()) {
                if (pcd == null) {
                    throw new IllegalStateException(
                        "Product data contains a null product-content mapping: " + update);
                }

                ContentInfo cdto = pcd.getContent();
                if (cdto == null || cdto.getId() == null) {
                    // This should only happen if something alters a content dto object after
                    // adding it to our link object. This is very bad.
                    throw new IllegalStateException(
                        "Product data contains an incomplete product-content mapping: " + update);
                }

                ProductContent existingLink = existingLinks.get(cdto.getId());
                Content content = contentMap.get(cdto.getId());

                if (content == null) {
                    // Content doesn't exist yet -- it should have been created already
                    throw new IllegalStateException(
                        "product references content which does not exist: " + cdto);
                }

                if (existingLink == null) {
                    existingLink = new ProductContent(
                        entity, content, pcd.isEnabled() != null ? pcd.isEnabled() : false);
                }
                else {
                    existingLink.setContent(content);

                    if (pcd.isEnabled() != null) {
                        existingLink.setEnabled(pcd.isEnabled());
                    }
                }

                productContent.add(existingLink);
            }

            entity.setProductContent(productContent);
        }

        if (update.getDependentProductIds() != null) {
            entity.setDependentProductIds(update.getDependentProductIds());
        }

        if (update.getBranding() != null) {
            if (update.getBranding().isEmpty()) {
                entity.setBranding(Collections.emptySet());
            }
            else {
                Set<Branding> branding = new HashSet<>();
                for (BrandingInfo brandingInfo : update.getBranding()) {
                    if (brandingInfo != null) {
                        branding.add(new Branding(
                            entity,
                            brandingInfo.getProductId(),
                            brandingInfo.getName(),
                            brandingInfo.getType()
                        ));
                    }
                }
                entity.setBranding(branding);
            }
        }

        return entity;
    }

}
