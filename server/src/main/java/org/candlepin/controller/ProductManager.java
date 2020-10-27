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

import org.candlepin.model.Branding;
import org.candlepin.model.Content;
import org.candlepin.model.Owner;
import org.candlepin.model.OwnerContentCurator;
import org.candlepin.model.OwnerProductCurator;
import org.candlepin.model.Product;
import org.candlepin.model.ProductContent;
import org.candlepin.model.ProductCurator;
import org.candlepin.service.model.BrandingInfo;
import org.candlepin.service.model.ContentInfo;
import org.candlepin.service.model.ProductContentInfo;
import org.candlepin.service.model.ProductInfo;
import org.candlepin.util.Util;

import com.google.inject.Inject;
import com.google.inject.persist.Transactional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
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
    private static final Logger log = LoggerFactory.getLogger(ProductManager.class);

    private final EntitlementCertificateGenerator entitlementCertGenerator;
    private final OwnerContentCurator ownerContentCurator;
    private final OwnerProductCurator ownerProductCurator;
    private final ProductCurator productCurator;

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
     * Resolves all of the products referenced by the given product info, returning a map that
     * contains all resolved product entities mapped by product ID. If a product reference cannot
     * be resolved, this method throws an exception.
     *
     * @param pinfo
     *  the product info for which to resolve product references
     *
     * @throws IllegalArgumentException
     *  if the product provided references a product which cannot be resolved
     *
     * @return
     *  a map containing all of the resolved products referenced, mapped by product ID
     */
    private Map<String, Product> resolveProductRefs(Owner owner, ProductInfo pinfo) {
        Set<String> pids = new HashSet<>();
        Map<String, Product> output;

        ProductInfo derived = pinfo.getDerivedProduct();
        if (derived != null) {
            if (derived.getId() == null || derived.getId().isEmpty()) {
                // TODO: Make this a custom exception. MalformedChildReferenceException, perhaps?
                throw new IllegalArgumentException("product reference lacks a valid ID");
            }

            pids.add(derived.getId());
        }

        Collection<? extends ProductInfo> provided = pinfo.getProvidedProducts();
        if (provided != null) {
            for (ProductInfo pp : provided) {
                if (pp == null) {
                    // TODO: Make this a custom exception. MalformedChildReferenceException, perhaps?
                    throw new IllegalArgumentException("product contains null provided product reference");
                }

                if (pp.getId() == null || pp.getId().isEmpty()) {
                    // TODO: Make this a custom exception. MalformedChildReferenceException, perhaps?
                    throw new IllegalArgumentException("product references a product that lacks a valid ID");
                }

                pids.add(pp.getId());
            }
        }

        if (!pids.isEmpty()) {
            output = this.ownerProductCurator.getProductsByIds(owner, pids).list().stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));

            pids.removeAll(output.keySet());
            if (!pids.isEmpty()) {
                // TODO: Make this a custom exception. MalformedChildReferenceException, perhaps?
                throw new IllegalArgumentException(
                    "product references one or more non-existent products: " + pids);
            }
        }
        else {
            output = new HashMap<>();
        }

        return output;
    }

    /**
     * Resolves all of the products referenced by the given product info, returning a map that
     * contains all resolved product entities mapped by product ID. If a product reference cannot
     * be resolved, this method throws an exception.
     *
     * @param pinfo
     *  the product info for which to resolve product references
     *
     * @throws IllegalArgumentException
     *  if the product provided references a product which cannot be resolved
     *
     * @return
     *  a map containing all of the resolved products referenced, mapped by product ID
     */
    private Map<String, Content> resolveContentRefs(Owner owner, ProductInfo pinfo) {
        Set<String> cids = new HashSet<>();
        Map<String, Content> output;

        Collection<? extends ProductContentInfo> productContent = pinfo.getProductContent();
        if (productContent != null) {
            for (ProductContentInfo pcinfo : productContent) {
                ContentInfo cinfo = pcinfo != null ? pcinfo.getContent() : null;

                if (cinfo == null) {
                    // TODO: Make this a custom exception. MalformedChildReferenceException, perhaps?
                    throw new IllegalArgumentException("product content reference lacks content details");
                }

                if (cinfo.getId() == null || cinfo.getId().isEmpty()) {
                    // TODO: Make this a custom exception. MalformedChildReferenceException, perhaps?
                    throw new IllegalArgumentException("content reference lacks a valid ID");
                }

                cids.add(cinfo.getId());
            }
        }

        if (!cids.isEmpty()) {
            output = this.ownerContentCurator.getContentByIds(owner, cids).list().stream()
                .collect(Collectors.toMap(c -> c.getId(), Function.identity()));

            cids.removeAll(output.keySet());
            if (!cids.isEmpty()) {
                // TODO: Make this a custom exception. MalformedChildReferenceException, perhaps?
                throw new IllegalArgumentException(
                    "product references one or more content which do not exist: " + cids);
            }
        }
        else {
            output = new HashMap<>();
        }

        return output;
    }

    /**
     * Creates a new Product instance using the given product data.
     *
     * @param owner
     *  the owner for which to create the new product
     *
     * @param productData
     *  a ProductInfo instance containing the data for the new product
     *
     * @throws IllegalArgumentException
     *  if owner is null, productData is null, or productData lacks required information
     *
     * @throws IllegalStateException
     *  if a product instance already exists for the product ID specified in productData
     *
     * @return
     *  the new created Product instance
     */
    @Transactional
    public Product createProduct(Owner owner, ProductInfo productData) {
        if (owner == null) {
            throw new IllegalArgumentException("owner is null");
        }

        if (productData == null) {
            throw new IllegalArgumentException("productData is null");
        }

        if (productData.getId() == null || productData.getName() == null) {
            throw new IllegalArgumentException("productData is incomplete");
        }

        if (this.ownerProductCurator.productExists(owner, productData.getId())) {
            throw new IllegalStateException("product has already been created: " + productData.getId());
        }

        Map<String, Product> productMap = this.resolveProductRefs(owner, productData);
        Map<String, Content> contentMap = this.resolveContentRefs(owner, productData);

        log.debug("Creating new product for org: {}, {}", productData, owner);

        Product entity = new Product()
            .setId(productData.getId());

        applyProductChanges(entity, productData, productMap, contentMap);

        // Check if we have an alternate version we can use instead.
        List<Product> alternateVersions = this.ownerProductCurator.getProductsByVersions(
            owner, Collections.singletonMap(entity.getId(), entity.getEntityVersion()))
            .get(entity.getId());

        if (alternateVersions != null) {
            log.debug("Checking {} alternate product versions", alternateVersions.size());

            for (Product alt : alternateVersions) {
                if (alt.equals(entity)) {
                    log.debug("Converging with existing product version: {} => {}", entity, alt);

                    // If we're "creating" a product, we shouldn't have any other object references to
                    // update for this product. Instead, we'll just add the new owner to the product.
                    this.ownerProductCurator.mapProductToOwner(alt, owner);
                    return alt;
                }
            }
        }

        log.debug("Creating new product instance: {}", entity);
        entity = this.productCurator.create(entity);
        this.ownerProductCurator.mapProductToOwner(entity, owner);

        return entity;
    }

    /**
     * Updates product with the ID specified in the given product data, optionally regenerating
     * certificates of entitlements affected by the product update.
     *
     * @param owner
     *  the owner for which to update the specified product
     *
     * @param productData
     *  the product data to use to update the specified product
     *
     * @param regenCerts
     *  whether or not certificates for affected entitlements should be regenerated after updating
     *  the specified product
     *
     * @throws IllegalArgumentException
     *  if owner is null, productData is null, or productData is missing required information
     *
     * @throws IllegalStateException
     *  if the product specified by the product data does not yet exist for the given owner
     *
     * @return
     *  the updated Product instance
     */
    @Transactional
    public Product updateProduct(Owner owner, ProductInfo productData, boolean regenCerts) {
        if (owner == null) {
            throw new IllegalArgumentException("owner is null");
        }

        if (productData == null) {
            throw new IllegalArgumentException("productData is null");
        }

        if (productData.getId() == null) {
            throw new IllegalArgumentException("productData is incomplete");
        }

        // Resolve the entity to ensure we're working with the merged entity, and to ensure it's
        // already been created.
        Product entity = this.ownerProductCurator.getProductById(owner, productData.getId());

        if (entity == null) {
            // If we're doing an exclusive update, this should be an error condition
            throw new IllegalStateException("Product has not yet been created");
        }

        // Make sure we have an actual change to apply
        if (!isChangedBy(entity, productData)) {
            return entity;
        }

        Map<String, Product> productMap = this.resolveProductRefs(owner, productData);
        Map<String, Content> contentMap = this.resolveContentRefs(owner, productData);

        log.debug("Applying product update for org: {} => {}, {}", productData, entity, owner);

        Product updated = applyProductChanges((Product) entity.clone(), productData, productMap, contentMap)
            .setUuid(null);

        // Grab a list of products that are using this product. Due to versioning restrictions,
        // we'll need to update these manually as a recursive step. We'll come back to these later.
        Collection<Product> affectedProducts = this.ownerProductCurator
            .getProductsReferencingProduct(owner.getId(), entity.getUuid());

        // Check for newer versions of the same product. We want to try to dedupe as much data as we
        // can, and if we have a newer version of the product (which matches the version provided by
        // the caller), we can just point the given orgs to the new product instead of giving them
        // their own version.
        // This is probably going to be a very expensive operation, though.
        List<Product> alternateVersions = this.ownerProductCurator.getProductsByVersions(
            owner, Collections.singletonMap(updated.getId(), updated.getEntityVersion()))
            .get(updated.getId());

        if (alternateVersions != null) {
            log.debug("Checking {} alternate product versions", alternateVersions.size());

            for (Product alt : alternateVersions) {
                if (alt.equals(updated)) {
                    log.debug("Converging with existing product version: {} => {}", updated, alt);

                    this.ownerProductCurator.updateOwnerProductReferences(owner,
                        Collections.singletonMap(entity.getUuid(), alt.getUuid()));

                    updated = alt;
                }
            }
        }

        if (updated.getUuid() == null) {
            log.debug("Creating new product instance and applying update: {}", updated);
            updated = this.productCurator.create(updated);

            this.ownerProductCurator.updateOwnerProductReferences(owner,
                Collections.singletonMap(entity.getUuid(), updated.getUuid()));
        }

        if (regenCerts) {
            this.entitlementCertGenerator.regenerateCertificatesOf(owner, updated.getId(), true);
        }

        // Update affected products recursively
        if (!affectedProducts.isEmpty()) {
            log.debug("Updating {} products affected by product update", affectedProducts.size());

            for (Product affected : affectedProducts) {
                this.updateChildrenReferences(owner, affected, regenCerts);
            }
        }

        return updated;
    }

    /**
     * Updates product with the ID specified in the given product data, regenerating certificates
     * of entitlements affected by the product update.
     *
     * @param owner
     *  the owner for which to update the specified product
     *
     * @param productData
     *  the product data to use to update the specified product
     *
     * @throws IllegalArgumentException
     *  if owner is null, productData is null, or productData is missing required information
     *
     * @throws IllegalStateException
     *  if the product specified by the product data does not yet exist for the given owner
     *
     * @return
     *  the updated Product instance
     */
    public Product updateProduct(Owner owner, ProductInfo productData) {
        return this.updateProduct(owner, productData, true);
    }

    /**
     * Updates the children references on this product to point to the products and content
     * currently mapped to the given owner.
     *
     * @param owner
     *  the owner for which to update the product's children references
     *
     * @param entity
     *  the Product entity for which to update children references
     *
     * @param regenCerts
     *  whether or not certificates for affected entitlements should be regenerated after updating
     *  the specified product
     *
     * @return
     *  the updated Product instance
     */
    @Transactional
    public Product updateChildrenReferences(Owner owner, Product entity, boolean regenCerts) {
        if (owner == null) {
            throw new IllegalArgumentException("owner is null");
        }

        if (entity == null) {
            throw new IllegalArgumentException("entity is null");
        }

        Map<String, Product> productMap = this.resolveProductRefs(owner, entity);
        Map<String, Content> contentMap = this.resolveContentRefs(owner, entity);

        log.debug("Updating children references on product for org: {}, {}", entity, owner);

        Product updated = ((Product) entity.clone())
            .setUuid(null);

        // Update references using our entity as the update for select fields
        applyProductContentChanges(updated, entity, contentMap);
        applyDerivedProductChanges(updated, entity, productMap);
        applyProvidedProductChanges(updated, entity, productMap);

        // Grab a list of products that are using this product. Due to versioning restrictions,
        // we'll need to update these manually as a recursive step. We'll come back to these later.
        Collection<Product> affectedProducts = this.ownerProductCurator
            .getProductsReferencingProduct(owner.getId(), entity.getUuid());

        // Check for newer versions of the same product. We want to try to dedupe as much data as we
        // can, and if we have a newer version of the product (which matches the version provided by
        // the caller), we can just point the given orgs to the new product instead of giving them
        // their own version.
        // This is probably going to be a very expensive operation, though.
        List<Product> alternateVersions = this.ownerProductCurator.getProductsByVersions(
            owner, Collections.singletonMap(updated.getId(), updated.getEntityVersion()))
            .get(updated.getId());

        if (alternateVersions != null) {
            log.debug("Checking {} alternate product versions", alternateVersions.size());

            for (Product alt : alternateVersions) {
                if (alt.equals(updated)) {
                    log.debug("Converging with existing product: {} => {}", updated, alt);

                    this.ownerProductCurator.updateOwnerProductReferences(owner,
                        Collections.singletonMap(entity.getUuid(), alt.getUuid()));

                    updated = alt;
                }
            }
        }

        if (updated.getUuid() == null) {
            log.debug("Creating new product instance and updating child references: {}", updated);
            updated = this.productCurator.create(updated);

            this.ownerProductCurator.updateOwnerProductReferences(owner,
                Collections.singletonMap(entity.getUuid(), updated.getUuid()));
        }

        if (regenCerts) {
            this.entitlementCertGenerator.regenerateCertificatesOf(owner, updated.getId(), true);
        }

        // Update affected products recursively
        if (!affectedProducts.isEmpty()) {
            log.debug("Updating {} products affected by child reference update", affectedProducts.size());

            for (Product affected : affectedProducts) {
                this.updateChildrenReferences(owner, affected, regenCerts);
            }
        }

        return updated;
    }

    /**
     * Removes the product specified by the provided product ID from the given owner, optionally
     * regenerating certificates of affected entitlements.
     *
     * @param owner
     *  the owner for which to remove the specified product
     *
     * @param productId
     *  the Red Hat product ID of the product to remove
     *
     * @throws IllegalArgumentException
     *  if owner is null, or productId is null
     *
     * @throws IllegalStateException
     *  if a product with the given ID has not yet been created for the specified owner
     *
     * @return
     *  the Product instance removed from the given owner
     */
    @Transactional
    public Product removeProduct(Owner owner, String productId) {
        if (owner == null) {
            throw new IllegalArgumentException("owner is null");
        }

        if (productId == null) {
            throw new IllegalArgumentException("productId is null");
        }

        // Make sure the entity actually exists to be removed
        Product entity = this.ownerProductCurator.getProductById(owner, productId);
        if (entity == null) {
            throw new IllegalStateException("Product has not yet been created");
        }

        // Make sure the product isn't referenced by a pool or other product
        if (this.productCurator.productHasSubscriptions(owner, entity)) {
            throw new IllegalStateException("Product is referenced by one or more subscriptions: " + entity);
        }

        if (this.productCurator.productHasParentProducts(owner, entity)) {
            throw new IllegalStateException("Product is referenced by one or more parent products: " +
                entity);
        }

        // Validation checks passed, remove the reference to it
        log.debug("Removing product for org: {}, {}", entity, owner);
        this.ownerProductCurator.removeOwnerProductReferences(owner, Collections.singleton(entity.getUuid()));

        return entity;
    }

    /**
     * Removes the product specified by the provided product data from the given owner, optionally
     * regenerating certificates of affected entitlements.
     *
     * @param owner
     *  the owner for which to remove the specified product
     *
     * @param productData
     *  the product data containing the Red Hat ID of the product to remove
     *
     * @throws IllegalArgumentException
     *  if owner is null, or productData is null
     *
     * @throws IllegalStateException
     *  if a product with the given ID has not yet been created for the specified owner
     *
     * @return
     *  the Product instance removed from the given owner
     */
    public Product removeProduct(Owner owner, ProductInfo productData) {
        if (productData == null) {
            throw new IllegalArgumentException("productData is null");
        }

        return this.removeProduct(owner, productData.getId());
    }

    /**
     * Tests if the given product entity would be changed by the collection of updates captured by
     * the specified product info container, ignoring any identifier fields.
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
     *  the product info container to test for changes
     *
     * @throws IllegalArgumentException
     *  if either the entity or update parameters are null
     *
     * @return
     *  true if the entity would be changed by the provided update; false otherwise
     */
    public static boolean isChangedBy(Product entity, ProductInfo update) {
        if (entity == null) {
            throw new IllegalArgumentException("entity is null");
        }

        if (update == null) {
            throw new IllegalArgumentException("update is null");
        }

        // Basic stuff
        if (update.getName() != null && !update.getName().equals(entity.getName())) {
            return true;
        }

        if (update.getMultiplier() != null && !update.getMultiplier().equals(entity.getMultiplier())) {
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

        Collection<String> dependentProductIds = update.getDependentProductIds();
        if (dependentProductIds != null &&
            !Util.collectionsAreEqual(entity.getDependentProductIds(), dependentProductIds)) {
            return true;
        }

        // Complex stuff
        // product content
        if (update.getProductContent() != null) {
            Map<String, Boolean> entityContentMap = entity.getProductContent().stream()
                .filter(pc -> pc != null && pc.getContent() != null)
                .collect(Collectors.toMap(pc -> pc.getContent().getId(), pc -> pc.isEnabled()));

            Map<String, Boolean> updateContentMap = update.getProductContent().stream()
                .filter(pc -> pc != null && pc.getContent() != null)
                .collect(Collectors.toMap(pc -> pc.getContent().getId(), pc -> pc.isEnabled()));

            if (!entityContentMap.equals(updateContentMap)) {
                return true;
            }
        }

        // derived product
        ProductInfo derivedProduct = update.getDerivedProduct();
        Product existingDerived = entity.getDerivedProduct();

        if (derivedProduct != null) {
            if (existingDerived == null || !existingDerived.getId().equals(derivedProduct.getId())) {
                return true;
            }
        }
        else if (existingDerived != null) {
            return true;
        }

        // provided products
        Collection<? extends ProductInfo> updateProvidedProducts = update.getProvidedProducts();
        if (updateProvidedProducts != null) {
            Set<String> entityProvidedPids = entity.getProvidedProducts().stream()
                .filter(pp -> pp != null)
                .map(Product::getId)
                .collect(Collectors.toSet());

            Set<String> updateProvidedPids = updateProvidedProducts.stream()
                .filter(pp -> pp != null)
                .map(ProductInfo::getId)
                .collect(Collectors.toSet());

            if (!entityProvidedPids.equals(updateProvidedPids)) {
                return true;
            }
        }

        // branding
        Collection<? extends BrandingInfo> updateBranding = update.getBranding();
        if (updateBranding != null) {
            Function<BrandingInfo, String> keybuilder = (brand) -> {
                return new StringBuilder()
                    .append(brand.getProductId())
                    .append(brand.getType())
                    .append(brand.getName())
                    .toString();
            };

            Set<String> entityBrandingKeys = entity.getBranding().stream()
                .filter(brand -> brand != null)
                .map(keybuilder)
                .collect(Collectors.toSet());

            Set<String> updateBrandingKeys = updateBranding.stream()
                .filter(brand -> brand != null)
                .map(keybuilder)
                .collect(Collectors.toSet());

            if (!entityBrandingKeys.equals(updateBrandingKeys)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Applies changes from the given ProductInfo instance to the specified Product entity.
     *
     * @param entity
     *  the Product entity to update
     *
     * @param update
     *  the ProductInfo instance containing the data with which to update the entity
     *
     * @param productMap
     *  a mapping of Red Hat product IDs to product instances to use for child product resolution
     *
     * @param contentMap
     *  a mapping of Red Hat content IDs to content instances to use for child content resolution
     *
     * @return
     *  the updated Product entity
     */
    private static Product applyProductChanges(Product entity, ProductInfo update,
        Map<String, Product> productMap, Map<String, Content> contentMap) {

        // Base attributes which require no special processing
        if (update.getName() != null) {
            entity.setName(update.getName());
        }

        if (update.getMultiplier() != null) {
            entity.setMultiplier(update.getMultiplier());
        }

        if (update.getAttributes() != null) {
            entity.setAttributes(update.getAttributes());
        }

        if (update.getDependentProductIds() != null) {
            entity.setDependentProductIds(update.getDependentProductIds());
        }

        // Complicated stuff
        applyProductContentChanges(entity, update, contentMap);
        applyDerivedProductChanges(entity, update, productMap);
        applyProvidedProductChanges(entity, update, productMap);
        applyBrandingChanges(entity, update);

        return entity;
    }

    /**
     * Applies product content changes from the given ProductInfo instance to the specified Product
     * entity.
     *
     * @param entity
     *  the Product entity to update
     *
     * @param update
     *  the ProductInfo instance containing the data with which to update the entity
     *
     * @param contentMap
     *  a mapping of Red Hat content IDs to content instances to use for content resolution
     */
    private static void applyProductContentChanges(Product entity, ProductInfo update,
        Map<String, Content> contentMap) {

        Collection<? extends ProductContentInfo> updateProductContent = update.getProductContent();
        if (updateProductContent != null) {
            Map<String, ProductContent> productContentMap = new HashMap<>();

            for (ProductContentInfo pcinfo : updateProductContent) {
                if (pcinfo == null) {
                    // This shouldn't ever happen, since we've already validated the references prior
                    throw new IllegalStateException("product contains a null product-content reference");
                }

                ContentInfo cinfo = pcinfo.getContent();
                if (cinfo == null || cinfo.getId() == null) {
                    // This shouldn't ever happen, since we've already validated the references prior
                    throw new IllegalStateException(
                        "product contains an incomplete product-content reference: " + pcinfo);
                }

                Content resolved = contentMap.get(cinfo.getId());
                if (resolved == null) {
                    // This shouldn't ever happen, since we've already validated the references prior
                    throw new IllegalStateException("cannot resolve content reference: " + cinfo);
                }

                boolean enabled = pcinfo.isEnabled() != null ? pcinfo.isEnabled() : false;
                productContentMap.put(resolved.getId(), new ProductContent(entity, resolved, enabled));

            }

            entity.setProductContent(productContentMap.values());
        }
    }

    /**
     * Applies provided product changes from the given ProductInfo instance to the specified Product
     * entity.
     *
     * @param entity
     *  the Product entity to update
     *
     * @param update
     *  the ProductInfo instance containing the data with which to update the entity
     *
     * @param productMap
     *  a mapping of Red Hat product IDs to product instances to use for provided product resolution
     */
    private static void applyProvidedProductChanges(Product entity, ProductInfo update,
        Map<String, Product> productMap) {

        Collection<? extends ProductInfo> providedProducts = update.getProvidedProducts();
        if (providedProducts != null) {
            Set<Product> updatedProvidedProducts = new HashSet<>();

            for (ProductInfo provided : providedProducts) {
                if (provided == null) {
                    throw new IllegalStateException("product contains null provided product reference");
                }

                Product resolved = productMap.get(provided.getId());

                if (resolved == null) {
                    // This shouldn't ever happen, since we've already validated the references prior
                    throw new IllegalStateException("unable to resolve product reference: " + provided);
                }

                updatedProvidedProducts.add(resolved);
            }

            entity.setProvidedProducts(updatedProvidedProducts);
        }
    }

    /**
     * Applies derived product changes from the given ProductInfo instance to the specified Product
     * entity.
     *
     * @param entity
     *  the Product entity to update
     *
     * @param update
     *  the ProductInfo instance containing the data with which to update the entity
     *
     * @param productMap
     *  a mapping of Red Hat product IDs to product instances to use for derived product resolution
     */
    private static void applyDerivedProductChanges(Product entity, ProductInfo update,
        Map<String, Product> productMap) {

        // Impl note:
        // Singular entity references deviate from the normal "null = no change" semantics and
        // fall back to the "null = no ref" behavior. As such, we will always perform a change
        // for this field
        ProductInfo derived = update.getDerivedProduct();

        if (derived != null) {
            Product resolved = productMap.get(derived.getId());

            if (resolved == null) {
                // This shouldn't ever happen, since we've already validated the references prior
                throw new IllegalStateException("unable to resolve product reference: " + derived);
            }

            entity.setDerivedProduct(resolved);
        }
        else {
            entity.setDerivedProduct(null);
        }
    }

    /**
     * Applies branding changes from the given ProductInfo instance to the specified Product
     * entity.
     *
     * @param entity
     *  the Product entity to update
     *
     * @param update
     *  the ProductInfo instance containing the data with which to update the entity
     */
    private static void applyBrandingChanges(Product entity, ProductInfo update) {
        Collection<? extends BrandingInfo> branding = update.getBranding();
        if (branding != null) {
            Set<Branding> resolved = new HashSet<>();

            if (!branding.isEmpty()) {
                for (BrandingInfo binfo : branding) {
                    if (binfo == null) {
                        continue;
                    }

                    resolved.add(new Branding(
                        entity,
                        binfo.getProductId(),
                        binfo.getName(),
                        binfo.getType()));
                }
            }

            entity.setBranding(resolved);
        }
    }

}
