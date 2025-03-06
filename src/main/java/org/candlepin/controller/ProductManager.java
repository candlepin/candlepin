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

import org.candlepin.model.Branding;
import org.candlepin.model.Content;
import org.candlepin.model.ContentCurator;
import org.candlepin.model.Owner;
import org.candlepin.model.Product;
import org.candlepin.model.ProductContent;
import org.candlepin.model.ProductCurator;
import org.candlepin.model.activationkeys.ActivationKeyCurator;
import org.candlepin.service.model.BrandingInfo;
import org.candlepin.service.model.ContentInfo;
import org.candlepin.service.model.ProductContentInfo;
import org.candlepin.service.model.ProductInfo;
import org.candlepin.util.Util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
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

    private final ContentAccessManager contentAccessManager;
    private final EntitlementCertificateService entitlementCertService;
    private final ProductCurator productCurator;
    private final ContentCurator contentCurator;
    private final ActivationKeyCurator activationKeyCurator;

    @Inject
    public ProductManager(ContentAccessManager contentAccessManager,
        EntitlementCertificateService entitlementCertService, ProductCurator productCurator,
        ContentCurator contentCurator, ActivationKeyCurator activationKeyCurator) {

        this.contentAccessManager = Objects.requireNonNull(contentAccessManager);
        this.entitlementCertService = Objects.requireNonNull(entitlementCertService);
        this.productCurator = Objects.requireNonNull(productCurator);
        this.contentCurator = Objects.requireNonNull(contentCurator);
        this.activationKeyCurator = Objects.requireNonNull(activationKeyCurator);
    }

   /**
     * Resolves all of the products referenced by the given product data, returning a map that
     * contains all resolved product entities mapped by product ID. If a product reference cannot
     * be resolved, this method throws an exception.
     *
     * @param namespace
     *  the namespace in which to resolve the product references
     *
     * @param pinfo
     *  the product data for which to resolve product references
     *
     * @throws IllegalArgumentException
     *  if the product provided references a product which cannot be resolved
     *
     * @return
     *  a map containing all of the resolved products referenced, mapped by product ID
     */
    private Map<String, Product> resolveProductRefs(String namespace, ProductInfo pinfo) {
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
            output = this.productCurator.getProductsByIds(namespace, pids, LockModeType.PESSIMISTIC_WRITE);

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
     * Resolves all of the contents referenced by the given product data, returning a map that
     * contains all resolved content entities mapped by content ID. If a content reference cannot
     * be resolved, this method throws an exception.
     *
     * @param namespace
     *  the namespace in which to resolve the content references
     *
     * @param pinfo
     *  the product data for which to resolve content references
     *
     * @throws IllegalArgumentException
     *  if the content provided references a content which cannot be resolved
     *
     * @return
     *  a map containing all of the resolved contents referenced, mapped by content ID
     */
    private Map<String, Content> resolveContentRefs(String namespace, ProductInfo pinfo) {
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
            output = this.contentCurator.getContentsByIds(namespace, cids, LockModeType.PESSIMISTIC_WRITE);

            cids.removeAll(output.keySet());
            if (!cids.isEmpty()) {
                throw new MalformedEntityReferenceException(
                    "product references one or more content which do not exist: " + cids);
            }
        }

        return output;
    }

    /**
     * Creates a new product from the given product data in the namespace of the given organization.
     * The product data must have all of the required fields set, and must not reference another
     * entity outside of the organization's namespace.
     *
     * @param owner
     *  the organization in which to create the new product
     *
     * @param pinfo
     *  the product data to use to create the product
     *
     * @throws IllegalArgumentException
     *  if pinfo is null
     *
     * @throws IllegalStateException
     *  if creating the new product would cause a collision with an existing product
     *
     * @return
     *  the newly created and persisted product instance
     */
    public Product createProduct(Owner owner, ProductInfo pinfo) {
        if (owner == null) {
            throw new IllegalArgumentException("owner is null");
        }

        if (pinfo == null) {
            throw new IllegalArgumentException("pinfo is null");
        }

        if (pinfo.getId() == null || pinfo.getName() == null) {
            throw new IllegalArgumentException("product data is incomplete");
        }

        String namespace = owner.getKey();

        if (this.productCurator.resolveProductId(namespace, pinfo.getId()) != null) {
            String errmsg = String.format("a product with ID \"%s\" already exists within the context of " +
                "namespace \"%s\"", pinfo.getId(), namespace);

            throw new IllegalStateException(errmsg);
        }

        Map<String, Product> productMap = this.resolveProductRefs(namespace, pinfo);
        Map<String, Content> contentMap = this.resolveContentRefs(namespace, pinfo);

        log.debug("Creating new product in namespace {}: {}", namespace, pinfo);

        Product entity = new Product()
            .setNamespace(namespace)
            .setId(pinfo.getId());

        entity = applyProductChanges(entity, pinfo, productMap, contentMap);

        log.debug("Creating new product instance: {}", entity);
        entity = this.productCurator.create(entity);

        log.debug("Synchronizing last content update for org: {}", owner);
        owner.syncLastContentUpdate();

        return entity;
    }

    /**
     * Updates the specified product with the given product data. The product data need not be
     * complete, but any references to children entities must be valid references to entities
     * present in the target product's namespace.
     * <p></p>
     * Note that this method is not able to update the target product's UUID, ID, or namespace.
     *
     * @param owner
     *  the organization making the change to the product
     *
     * @param product
     *  the product to update
     *
     * @param pinfo
     *  the product data to use to update the product
     *
     * @param regenCerts
     *  whether or not to regenerate certificates for entitlements of pools using the updated
     *  product
     *
     * @throws IllegalArgumentException
     *  if the owner, target product, or product data is null
     *
     * @throws IllegalStateException
     *  if the given product instance is not an existing, managed entity
     *
     * @return
     *  the updated product instance
     */
    public Product updateProduct(Owner owner, Product product, ProductInfo pinfo, boolean regenCerts) {
        if (product == null) {
            throw new IllegalArgumentException("product is null");
        }

        if (pinfo == null) {
            throw new IllegalArgumentException("pinfo is null");
        }

        if (!this.productCurator.getEntityManager().contains(product)) {
            throw new IllegalStateException("product is not a managed entity");
        }

        String namespace = product.getNamespace();

        // If the namespace doesn't match the org's, that's bad; disallow that.
        if (namespace == null || !namespace.equals(owner.getKey())) {
            throw new IllegalStateException("product namespace does not match org's namespace");
        }

        Map<String, Product> productMap = this.resolveProductRefs(namespace, pinfo);
        Map<String, Content> contentMap = this.resolveContentRefs(namespace, pinfo);

        if (isChangedBy(product, pinfo)) {
            product = applyProductChanges(product, pinfo, productMap, contentMap);

            log.debug("Persisting updated product in namespace {}: {}", namespace, product);
            product = this.productCurator.create(product);

            log.debug("Synchronizing last content update for org: {}", owner);
            owner.syncLastContentUpdate();

            if (regenCerts) {
                log.debug("Flagging entitlement certificates of 1 affected product for regeneration");
                this.entitlementCertService.regenerateCertificatesOf(owner, product.getId(), true);
            }
        }

        return product;
    }

    /**
     * Removes the specified product from its namespace, optionally flagging entitlement
     * certificates of affected pools for eventual regeneration.
     *
     * @param owner
     *  the organization removing the product
     *
     * @param product
     *  the product to remove
     *
     * @throws IllegalArgumentException
     *  if the owner, or the target product is null
     *
     * @throws IllegalStateException
     *  if the given product instance is not an existing, managed entity, is not in the namespace of the
     *  given organization, or the product is still in use by one or more parent products or subscriptions
     *
     * @return
     *  the removed product entity
     */
    public Product removeProduct(Owner owner, Product product) {
        if (owner == null) {
            throw new IllegalArgumentException("owner is null");
        }

        if (product == null) {
            throw new IllegalArgumentException("product is null");
        }

        if (!this.productCurator.getEntityManager().contains(product)) {
            throw new IllegalStateException("product is not a managed entity");
        }

        // Make sure the product isn't referenced by a pool or other product
        if (this.productCurator.productHasParentSubscriptions(product)) {
            throw new IllegalStateException("Product is referenced by one or more subscriptions: " + product);
        }

        if (this.productCurator.productHasParentProducts(product)) {
            throw new IllegalStateException("Product is referenced by one or more parent products: " +
                product);
        }

        String namespace = product.getNamespace();

        // If the namespace doesn't match the org's, that's bad; disallow that.
        if (namespace == null || !namespace.equals(owner.getKey())) {
            throw new IllegalStateException("product namespace does not match org's namespace");
        }

        // Future fun time: What happens if namespaces are no longer 1:1 with org? Answer: We'll get
        // indeterministic behavior with this removal.
        log.debug("Removing activation key product references from namespace: {}, {}", namespace, product);
        this.activationKeyCurator.removeActivationKeyProductReferences(owner, List.of(product.getId()));

        // Validation checks passed, remove the reference to it
        log.debug("Removing product from namespace: {}, {}", namespace, product);
        this.productCurator.delete(product);

        log.debug("Synchronizing last content update for org: {}", owner);
        owner.syncLastContentUpdate();

        return product;
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
                .collect(Collectors.toMap(pc -> pc.getContent().getId(), pc -> pc.isEnabled(),
                    (pc1, pc2) -> pc2));

            Map<String, Boolean> updateContentMap = update.getProductContent().stream()
                .filter(pc -> pc != null && pc.getContent() != null)
                .collect(Collectors.toMap(pc -> pc.getContent().getId(), pc -> pc.isEnabled(),
                    (pc1, pc2) -> pc2));

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

                boolean enabled = pcinfo.isEnabled() != null && pcinfo.isEnabled();
                productContentMap.put(resolved.getId(), new ProductContent(resolved, enabled));
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
            Function<BrandingInfo, Branding> converter = (binfo) -> {
                return new Branding(entity, binfo.getProductId(), binfo.getName(), binfo.getType());
            };

            List<Branding> resolved = branding.stream()
                .filter(Objects::nonNull)
                .map(converter)
                .toList();

            entity.setBranding(resolved);
        }
    }

}
