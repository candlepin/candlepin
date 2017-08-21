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

import org.candlepin.dto.ModelTranslator;
import org.candlepin.dto.api.v1.ContentDTO;
import org.candlepin.dto.api.v1.ProductDTO;
import org.candlepin.dto.api.v1.ProductDTO.ProductContentDTO;
import org.candlepin.model.Content;
import org.candlepin.model.Owner;
import org.candlepin.model.OwnerContentCurator;
import org.candlepin.model.OwnerProduct;
import org.candlepin.model.OwnerProductCurator;
import org.candlepin.model.Product;
import org.candlepin.model.ProductContent;
import org.candlepin.model.ProductCurator;
import org.candlepin.model.dto.ContentData;
import org.candlepin.model.dto.ProductData;
import org.candlepin.util.Traceable;
import org.candlepin.util.TraceableParam;
import org.candlepin.model.dto.ProductContentData;
import org.candlepin.util.Util;

import com.google.inject.Inject;
import com.google.inject.persist.Transactional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;



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
    private ModelTranslator modelTranslator;

    @Inject
    public ProductManager(EntitlementCertificateGenerator entitlementCertGenerator,
        OwnerContentCurator ownerContentCurator, OwnerProductCurator ownerProductCurator,
        ProductCurator productCurator, ModelTranslator modelTranslator) {

        this.entitlementCertGenerator = entitlementCertGenerator;
        this.ownerContentCurator = ownerContentCurator;
        this.ownerProductCurator = ownerProductCurator;
        this.productCurator = productCurator;
        this.modelTranslator = modelTranslator;
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
            owner, Collections.<String, Integer>singletonMap(entity.getId(), entity.getEntityVersion()))
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
     * Shim for updateProduct; converts the provided ProductData to a ProductDTO instance.
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
    public Product updateProduct(ProductData update, Owner owner, boolean regenerateEntitlementCerts) {
        ProductDTO dto = this.modelTranslator.translate(update, ProductDTO.class);
        return this.updateProduct(dto, owner, regenerateEntitlementCerts);
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
        if (!this.isChangedBy(entity, update)) {
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
            owner, Collections.<String, Integer>singletonMap(updated.getId(), updated.getEntityVersion()))
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
     * Creates or updates products from the given products DTOs, using the provided content for
     * content lookup and resolution.
     * <p></p>
     * The product DTOs provided in the given map should be mapped by the product's Red Hat ID. If
     * the mappings are incorrect or inconsistent, the result of this method is undefined.
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
    public ImportResult<Product> importProducts(@TraceableParam("owner") Owner owner,
        Map<String, ProductData> productData, Map<String, Content> importedContent) {

        if (owner == null) {
            throw new IllegalArgumentException("owner is null");
        }

        ImportResult<Product> importResult = new ImportResult<Product>();

        if (productData == null || productData.isEmpty()) {
            // Nothing to import
            return importResult;
        }

        Map<String, Product> skippedProducts = importResult.getSkippedEntities();
        Map<String, Product> createdProducts = importResult.getCreatedEntities();
        Map<String, Product> updatedProducts = importResult.getUpdatedEntities();

        Map<String, Integer> productVersions = new HashMap<String, Integer>();
        Map<String, Product> sourceProducts = new HashMap<String, Product>();
        Map<String, List<Product>> existingVersions = new HashMap<String, List<Product>>();
        List<OwnerProduct> ownerProductBuffer = new LinkedList<OwnerProduct>();

        // - Divide imported products into sets of updates and creates
        log.debug("Fetching existing products for update...");
        for (Product product : this.ownerProductCurator.getProductsByIds(owner, productData.keySet())) {
            ProductData update = productData.get(product.getId());

            if (!this.isChangedBy(product, update)) {
                // This product won't be changing, so we'll just pretend it's not being imported at all
                skippedProducts.put(product.getId(), product);
                continue;
            }

            sourceProducts.put(product.getId(), product);
            product = this.applyProductChanges((Product) product.clone(), update, importedContent);

            updatedProducts.put(product.getId(), product);
            productVersions.put(product.getId(), product.getEntityVersion());
        }

        log.debug("Validating new products...");
        for (ProductData update : productData.values()) {
            if (!skippedProducts.containsKey(update.getId()) &&
                !updatedProducts.containsKey(update.getId())) {

                // Ensure the product is minimally populated
                if (update.getId() == null || update.getName() == null) {
                    throw new IllegalStateException("Product data is incomplete: " + update);
                }

                Product product = new Product(update.getId(), update.getName());

                // TODO: Remove this shim and stop using DTOs in this class
                product = this.applyProductChanges(product, update, importedContent);

                createdProducts.put(product.getId(), product);
                productVersions.put(product.getId(), product.getEntityVersion());
            }
        }

        log.debug("Checking for existing product versions...");
        for (Product alt : this.ownerProductCurator.getProductsByVersions(owner, productVersions)) {
            List<Product> alternates = existingVersions.get(alt.getId());
            if (alternates == null) {
                alternates = new LinkedList<Product>();
                existingVersions.put(alt.getId(), alternates);
            }

            alternates.add(alt);
        }

        productVersions.clear();
        productVersions = null;

        // We're about to start modifying the maps, so we need to clone the created set before we
        // start adding the update forks to it.
        Map<String, Product> stagedEntities = new HashMap<String, Product>(createdProducts);

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
        Map<String, String> productUuidMap = new HashMap<String, String>();
        for (Product update : updatedProducts.values()) {
            Product source = sourceProducts.get(update.getId());

            productUuidMap.put(source.getUuid(), update.getUuid());
        }

        this.ownerProductCurator.updateOwnerProductReferences(owner, productUuidMap);

        // Return
        return importResult;
    }

    /**
     * Removes the specified product from the given owner. If the product is in use by multiple
     * owners, the product will not actually be deleted, but, instead, will simply by removed from
     * the given owner's visibility.
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
     * Removes all products from the specified owner. Products which are shared will have any
     * references to the owner removed, while unshared products will be deleted entirely.
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
     * Removes the specified products from the given owner. Products which are shared will have any
     * references to the owner removed, while unshared products will be deleted entirely.
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
            Map<String, Product> productMap = new HashMap<String, Product>();
            for (Product product : products) {
                productMap.put(product.getUuid(), product);
            }

            this.removeProductsByUuids(owner, productMap.keySet());
        }
    }

    /**
     * Removes the specified products from the given owner. Products which are shared will have any
     * references to the owner removed, while unshared products will be deleted entirely.
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
     * @param content
     *  A mapping of Red Hat content ID to content entities to use for content resolution
     *
     * @throws IllegalArgumentException
     *  if entity, update or owner is null
     *
     * @return
     *  The updated product entity
     */
    private Product applyProductChanges(Product entity, ProductDTO update, Owner owner) {
        if (owner == null) {
            throw new IllegalArgumentException("owner is null");
        }

        Map<String, Content> contentMap = new HashMap<String, Content>();

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

        return this.applyProductChanges(entity, update, contentMap);
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
     * @param content
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
            Collection<ProductContent> productContent = new LinkedList<ProductContent>();

            // Sort the existing ProductContent so we aren't iterating on it several times.
            // TODO: Remove this if/when product content is stored as a map on products.
            Map<String, ProductContent> existingLinks = new HashMap<String, ProductContent>();
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

        if (update.isLocked() != null) {
            entity.setLocked(update.isLocked());
        }

        return entity;
    }


    /**
     * Determines whether or not this entity would be changed if the given DTO were applied to this
     * object.
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
    private boolean isChangedBy(Product entity, ProductDTO dto) {
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

        if (dto.isLocked() != null && !dto.isLocked().equals(entity.isLocked())) {
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
            Comparator comparator = new Comparator() {
                public int compare(Object lhs, Object rhs) {
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
                                return update.isEnabled() != null &&
                                    update.isEnabled().equals(existing.isEnabled()) ? 0 : 1;
                            }
                        }
                    }

                    return 1;
                }
            };

            if (!Util.collectionsAreEqual((Collection) entity.getProductContent(),
                (Collection) productContent)) {

                return true;
            }
        }

        return false;
    }

    /**
     * Determines whether or not this entity would be changed if the given DTO were applied to this
     * object.
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
    private boolean isChangedBy(Product entity, ProductData dto) {
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

        if (dto.isLocked() != null && !dto.isLocked().equals(entity.isLocked())) {
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

        Collection<ProductContentData> productContent = dto.getProductContent();
        if (productContent != null) {
            Comparator comparator = new Comparator() {
                public int compare(Object lhs, Object rhs) {
                    ProductContent existing = (ProductContent) lhs;
                    ProductContentData update = (ProductContentData) rhs;

                    if (existing != null && update != null) {
                        Content content = existing.getContent();
                        ContentData cdto = update.getContent();

                        if (content != null && cdto != null) {
                            if (cdto.getUuid() != null ?
                                cdto.getUuid().equals(content.getUuid()) :
                                (cdto.getId() != null && cdto.getId().equals(content.getId()))) {
                                // At this point, we've either matched the UUIDs (which means we're
                                // referencing identical products) or the UUID isn't present on the DTO, but
                                // the IDs match (which means we're pointing toward the same product).
                                return update.isEnabled() != null &&
                                    update.isEnabled().equals(existing.isEnabled()) ? 0 : 1;
                            }
                        }
                    }

                    return 1;
                }
            };

            if (!Util.collectionsAreEqual((Collection) entity.getProductContent(),
                (Collection) productContent)) {

                return true;
            }
        }

        return false;
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
     * @param content
     *  A mapping of Red Hat content ID to content entities to use for content resolution
     *
     * @throws IllegalArgumentException
     *  if entity, update or owner is null
     *
     * @return
     *  The updated product entity
     */
    private Product applyProductChanges(Product entity, ProductData update, Map<String, Content> contentMap) {
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
            Collection<ProductContent> productContent = new LinkedList<ProductContent>();

            // Sort the existing ProductContent so we aren't iterating on it several times.
            // TODO: Remove this if/when product content is stored as a map on products.
            Map<String, ProductContent> existingLinks = new HashMap<String, ProductContent>();
            for (ProductContent pc : entity.getProductContent()) {
                existingLinks.put(pc.getContent().getId(), pc);
            }

            // Actually process our list of content...
            for (ProductContentData pcd : update.getProductContent()) {
                if (pcd == null) {
                    throw new IllegalStateException("Product data contains a null product-content mapping: " +
                        update);
                }

                ContentData cdto = pcd.getContent();
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

        if (update.isLocked() != null) {
            entity.setLocked(update.isLocked());
        }

        return entity;
    }

}
