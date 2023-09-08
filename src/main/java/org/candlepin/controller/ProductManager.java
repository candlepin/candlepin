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

import org.candlepin.model.Branding;
import org.candlepin.model.Content;
import org.candlepin.model.ContentCurator;
import org.candlepin.model.Owner;
import org.candlepin.model.Product;
import org.candlepin.model.ProductContent;
import org.candlepin.model.ProductCurator;
import org.candlepin.service.model.BrandingInfo;
import org.candlepin.service.model.ContentInfo;
import org.candlepin.service.model.ProductContentInfo;
import org.candlepin.service.model.ProductInfo;
import org.candlepin.util.Util;

import com.google.inject.persist.Transactional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import javax.inject.Inject;
import javax.persistence.LockModeType;



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

    /** Name of the system lock used by various product operations */
    public static final String SYSTEM_LOCK = "products";

    private final ContentAccessManager contentAccessManager;
    private final EntitlementCertificateGenerator entitlementCertGenerator;

    private final ProductCurator productCurator;
    private final ContentCurator contentCurator;

    @Inject
    public ProductManager(ContentAccessManager contentAccessManager,
        EntitlementCertificateGenerator entitlementCertGenerator, ProductCurator productCurator,
        ContentCurator contentCurator) {

        this.contentAccessManager = Objects.requireNonNull(contentAccessManager);
        this.entitlementCertGenerator = Objects.requireNonNull(entitlementCertGenerator);

        this.productCurator = Objects.requireNonNull(productCurator);
        this.contentCurator = Objects.requireNonNull(contentCurator);
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
    private Map<String, Product> resolveProductRefs(ProductInfo pinfo) {
        Set<String> pids = new HashSet<>();
        Map<String, Product> output;

        ProductInfo derived = pinfo.getDerivedProduct();
        if (derived != null) {
            if (derived.getId() == null || derived.getId().isEmpty()) {
                throw new MalformedEntityReferenceException("product reference lacks a valid ID");
            }

            pids.add(derived.getId());
        }

        Collection<? extends ProductInfo> provided = pinfo.getProvidedProducts();
        if (provided != null) {
            for (ProductInfo pp : provided) {
                if (pp == null) {
                    throw new MalformedEntityReferenceException(
                        "product contains null provided product reference");
                }

                if (pp.getId() == null || pp.getId().isEmpty()) {
                    throw new MalformedEntityReferenceException(
                        "product references a product that lacks a valid ID");
                }

                pids.add(pp.getId());
            }
        }

        if (!pids.isEmpty()) {
            output = this.productCurator.getProductsByIds(pids);

            pids.removeAll(output.keySet());
            if (!pids.isEmpty()) {
                throw new MalformedEntityReferenceException(
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
     * @param owner
     *  the owner of the product whose content is being resolved
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
    private Map<String, Content> resolveContentRefs(ProductInfo pinfo) {
        Set<String> cids = new HashSet<>();
        Map<String, Content> output = new HashMap<>();

        Collection<? extends ProductContentInfo> productContent = pinfo.getProductContent();
        if (productContent != null) {
            for (ProductContentInfo pcinfo : productContent) {
                ContentInfo cinfo = pcinfo != null ? pcinfo.getContent() : null;

                if (cinfo == null) {
                    throw new MalformedEntityReferenceException(
                        "product content reference lacks content details");
                }

                if (cinfo.getId() == null || cinfo.getId().isEmpty()) {
                    throw new MalformedEntityReferenceException(
                        "content reference lacks a valid ID");
                }

                cids.add(cinfo.getId());
            }
        }

        if (!cids.isEmpty()) {
            output = this.contentCurator.getContentsByIds(cids);

            cids.removeAll(output.keySet());
            if (!cids.isEmpty()) {
                throw new MalformedEntityReferenceException(
                    "product references one or more content repos which do not exist: " + cids);
            }
        }

        return output;
    }

    /**
     * Creates a new Product instance using the given product data.
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
    public Product createProduct(ProductInfo productData) {
        if (productData == null) {
            throw new IllegalArgumentException("productData is null");
        }

        if (productData.getId() == null || productData.getName() == null) {
            throw new IllegalArgumentException("productData is incomplete");
        }

        if (this.productCurator.productExistsById(productData.getId())) {
            throw new IllegalStateException("product already exists: " + productData.getId());
        }

        Map<String, Product> productMap = this.resolveProductRefs(productData);
        Map<String, Content> contentMap = this.resolveContentRefs(productData);

        log.debug("Creating new product: {}", productData);

        Product entity = new Product()
            .setId(productData.getId());

        this.applyProductChanges(entity, productData, productMap, contentMap);

        log.debug("Creating new product instance: {}", entity);
        entity = this.productCurator.create(entity);

        // TODO: FIXME: uh oh...
        // log.debug("Synchronizing last content update for affected orgs...");
        // this.contentAccessManager.syncOwnerLastContentUpdate(owner);

        return entity;
    }

    /**
     * Updates product with the ID specified in the given product data, optionally regenerating
     * certificates of entitlements affected by the product update.
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
    public Product updateProduct(Product entity, ProductInfo productData, boolean regenCerts) {
        if (entity == null) {
            throw new IllegalArgumentException("entity is null");
        }

        if (productData == null) {
            throw new IllegalArgumentException("productData is null");
        }

        // this.ownerProductCurator.getSystemLock(SYSTEM_LOCK, LockModeType.PESSIMISTIC_WRITE);

        // Make sure we have an actual change to apply
        if (!isChangedBy(entity, productData)) {
            return entity;
        }

        Map<String, Product> productMap = this.resolveProductRefs(productData);
        Map<String, Content> contentMap = this.resolveContentRefs(productData);

        log.debug("Applying product update: {} => {}", productData, entity);
        this.applyProductChanges(entity, productData, productMap, contentMap);

        log.debug("Updating product instance: {}", entity);
        entity = this.productCurator.merge(entity);

        if (regenCerts) {
            // TODO: FIXME: This could be very far reaching...
            this.entitlementCertGenerator.regenerateCertificatesForProduct(entity, true);
        }

        // TODO: FIXME: uh oh...
        // log.debug("Synchronizing last content update for affected orgs...");
        // this.contentAccessManager.syncOwnerLastContentUpdate(owner);

        return entity;
    }

    /**
     * Updates product with the ID specified in the given product data, regenerating certificates
     * of entitlements affected by the product update.
     *
     * @param productData
     *  the product data to use to update the specified product
     *
     * @throws IllegalArgumentException
     *  if productData is null, or productData is missing required information
     *
     * @throws IllegalStateException
     *  if the product specified by the product data does not yet exist
     *
     * @return
     *  the updated Product instance
     */
    public Product updateProduct(Product entity, ProductInfo productData) {
        return this.updateProduct(entity, productData, true);
    }

    /**
     * Removes the product specified by the provided product ID, optionally regenerating
     * certificates of affected entitlements.
     *
     * @param productId
     *  the Red Hat product ID of the product to remove
     *
     * @throws IllegalArgumentException
     *  if or productId is null
     *
     * @throws IllegalStateException
     *  if a product with the given ID has not yet been created
     *
     * @return
     *  the Product instance removed
     */
    @Transactional
    public Product deleteProduct(Product entity) {
        if (entity == null) {
            throw new IllegalArgumentException("entity is null");
        }

        // Resolve the entity to the actual entity to (a) verify it exists and (b) ensure we're
        // passing a managed entity to the curator
        String entityId = entity.getId();
        entity = this.productCurator.getProductById(entityId);
        if (entity == null) {
            throw new IllegalStateException("No such product entity exists: " + entityId);
        }

        // this.ownerProductCurator.getSystemLock(SYSTEM_LOCK, LockModeType.PESSIMISTIC_WRITE);

        // Make sure the product isn't referenced by a pool or other product
        if (this.productCurator.productHasSubscriptions(entity)) {
            throw new IllegalStateException("Product is referenced by one or more subscriptions: " + entity);
        }

        if (this.productCurator.productHasParentProducts(entity)) {
            throw new IllegalStateException("Product is referenced by one or more parent products: " +
                entity);
        }

        // Validation checks passed, remove the reference to it
        log.debug("Deleting product: {}", entity);
        this.productCurator.delete(entity);

        // TODO: FIXME: get a list of affected orgs and sync owner content update for the affected orgs
        // log.debug("Synchronizing last content update for org: {}", owner);
        // this.contentAccessManager.syncOwnerLastContentUpdate(owner);

        return entity;
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
                .filter(Objects::nonNull)
                .map(Product::getId)
                .collect(Collectors.toSet());

            Set<String> updateProvidedPids = updateProvidedProducts.stream()
                .filter(Objects::nonNull)
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
                .filter(Objects::nonNull)
                .map(keybuilder)
                .collect(Collectors.toSet());

            Set<String> updateBrandingKeys = updateBranding.stream()
                .filter(Objects::nonNull)
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

        if (update.getDependentProductIds() != null) {
            entity.setDependentProductIds(update.getDependentProductIds());
        }

        // Complicated stuff
        applyProductAttributeChanges(entity, update);
        applyProductContentChanges(entity, update, contentMap);
        applyDerivedProductChanges(entity, update, productMap);
        applyProvidedProductChanges(entity, update, productMap);
        applyBrandingChanges(entity, update);

        return entity;
    }

    /**
     * Applies product attributes changes from the given ProductInfo instance to the specified
     * Product entity. Attributes which have null values will be silently discarded from the
     * updated attributes.
     *
     * @param entity
     *  the Product entity to update
     *
     * @param update
     *  the ProductInfo instance containing the data with which to update the entity
     */
    private static void applyProductAttributeChanges(Product entity, ProductInfo update) {
        Map<String, String> incoming = update.getAttributes();
        if (incoming != null) {
            Map<String, String> filtered = new HashMap<>();

            incoming.forEach((k, v) -> {
                if (v != null) {
                    filtered.put(k, v);
                }
            });

            entity.setAttributes(filtered);
        }
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
