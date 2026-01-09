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
package org.candlepin.sync;

import org.candlepin.dto.ModelTranslator;
import org.candlepin.dto.manifest.v1.BrandingDTO;
import org.candlepin.dto.manifest.v1.CdnDTO;
import org.candlepin.dto.manifest.v1.CertificateDTO;
import org.candlepin.dto.manifest.v1.EntitlementDTO;
import org.candlepin.dto.manifest.v1.OwnerDTO;
import org.candlepin.dto.manifest.v1.PoolDTO;
import org.candlepin.dto.manifest.v1.PoolDTO.ProvidedProductDTO;
import org.candlepin.dto.manifest.v1.ProductDTO;
import org.candlepin.dto.manifest.v1.SubscriptionDTO;
import org.candlepin.model.Cdn;
import org.candlepin.model.CdnCurator;
import org.candlepin.model.Owner;
import org.candlepin.util.Util;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xnap.commons.i18n.I18n;

import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.Reader;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;


// TODO: Refactor this class (and the entire importer) to better isolate the various responsibilities
// these objects could and *should* have.

/**
 * EntitlementImporter - turn an upstream Entitlement into a local subscription
 */
public class EntitlementImporter {
    private static final Logger log = LoggerFactory.getLogger(EntitlementImporter.class);

    private final CdnCurator cdnCurator;
    private final I18n i18n;
    private final ModelTranslator translator;
    private final Map<String, ProductDTO> productMap;


    public EntitlementImporter(CdnCurator cdnCurator, I18n i18n, ModelTranslator translator,
        Map<String, ProductDTO> productMap) {

        this.cdnCurator = Objects.requireNonNull(cdnCurator);
        this.i18n = Objects.requireNonNull(i18n);
        this.translator = Objects.requireNonNull(translator);
        this.productMap = Objects.requireNonNull(productMap);
    }

    /**
     * Fetches the given product ID from the mapping of imported products backing this entitlement
     * importer. If a product for the given ID cannot be found, this method throws an exception.
     *
     * @param productId
     *  the ID of the product to fetch
     *
     * @throws SyncDataFormatException
     *  if a product for the given ID cannot be found
     *
     * @return
     *  the imported product for the given product ID
     */
    private ProductDTO getImportedProduct(String productId) throws SyncDataFormatException {
        ProductDTO product = this.productMap.get(productId);
        if (product == null) {
            throw new SyncDataFormatException(i18n.tr("Unable to find product with ID: {0}", productId));
        }

        return product;
    }

    /**
     * Merges any branding data on the upstream pool with any branding information on the product.
     * If the collection of branding data is null or empty, this method silently returns.
     *
     * @param product
     *  the product with which to merge any provided branding data
     *
     * @param branding
     *  a collection of branding DTOs to merge onto the product; may be null
     */
    private void mergeBranding(ProductDTO product, Collection<BrandingDTO> branding) {
        if (branding == null || branding.isEmpty()) {
            return;
        }

        for (BrandingDTO bdto : branding) {
            product.addBranding(bdto);
        }
    }

    /**
     * Resolves and merges the given provided product DTOs onto the specified product. The merged
     * provided products will be a union of all existing provided products on the product itself,
     * and those resolved from the given collection of provided product DTOs. In the event of a
     * collision, the provided product already on the product will be retained. If the given
     * collection of provided product DTOs is null or empty, this method silently returns.
     *
     * @param product
     *  the product to receive the additional provided products
     *
     * @param providedProductDTOs
     *  a collection of provided product DTOs to merge onto the product; may be null
     */
    private void mergeProvidedProducts(ProductDTO product,
        Collection<ProvidedProductDTO> providedProductDTOs) throws SyncDataFormatException {

        if (providedProductDTOs == null || providedProductDTOs.isEmpty()) {
            return;
        }

        Collection<ProductDTO> currentProvidedProducts = product.getProvidedProducts();
        Stream<ProductDTO> cppstream = currentProvidedProducts != null ?
            currentProvidedProducts.stream() :
            Stream.<ProductDTO>empty();

        Map<String, ProductDTO> cppmap = cppstream
            .collect(Collectors.toMap(ProductDTO::getId, Function.identity()));

        boolean updated = false;

        for (ProvidedProductDTO ppdto : providedProductDTOs) {
            // Impl note: It'd be nice to throw a lambda at this via computeIfAbsent, but our checked
            // exceptions get in the way.
            if (!cppmap.containsKey(ppdto.getProductId())) {
                log.warn("Product {} does not contain provided product {} from pool data; merging data...",
                    product.getId(), ppdto.getProductId());

                ProductDTO resolved = this.getImportedProduct(ppdto.getProductId());
                cppmap.put(resolved.getId(), resolved);
                updated = true;
            }
        }

        // Update product if the mappings changed
        if (updated) {
            product.setProvidedProducts(cppmap.values());
        }
    }

    private void mergeDerivedProductData(ProductDTO product, PoolDTO pool) throws SyncDataFormatException {
        String derivedProductId = pool.getDerivedProductId();
        if (derivedProductId == null) {
            return;
        }

        ProductDTO derived = product.getDerivedProduct();
        if (derived == null) {
            log.warn("Upstream pool {} defines derived product information not present on product: {}",
                pool.getId(), product);

            log.warn("Adding pool derived product to subscription product: {}", product);

            derived = this.getImportedProduct(derivedProductId);
            product.setDerivedProduct(derived);
        }
        else if (!derivedProductId.equals(derived.getId())) {
            log.warn("Upstream pool {} defines derived product information that differs from its product: " +
                "{} => {} != {}", pool.getId(), product, derived.getId(), derivedProductId);
            log.warn("Ignoring pool derived product");
        }

        this.mergeProvidedProducts(derived, pool.getDerivedProvidedProducts());
    }

    /**
     * Merges product data that may exist in on the product and on the pool.
     * <p></p>
     * Due to changes in Candlepin's core structure over the years, some of the data in the manifest
     * may not be exactly where it should be, or may still be in the old location depending on the
     * age of the manifest itself. For example, prior to Candlepin 4.0, branding and derived or
     * provided product data was stored on the pool, but after 4.0, this all exists on the product.
     * <p></p>
     * Additionally, prior to Candlepin 2.0, the product reference was not a complete object, but
     * was only the product ID. With such manifests, we need to resolve the product ID, and then
     * replace it with the full object.
     * <p></p>
     * To facilitate all these structures, branding and provided products that exist on the upstream
     * pool will be added to the product, without removing those that may already exist on it. This
     * may cause some collisions in terms of branding, and some pools to provide more content than
     * intended, but it minimizes the probability that two pools with different provided products
     * will end up a state where they *don't* provide content they're intended to.
     * <p></p>
     * However, a product can only have a single derived product, so in the event multiple pools
     * using a given product have different derived product definitions, the pool's definition will
     * only be used if the product does not already have a derived product defined. The derived
     * provided products, though, will be added to whatever derived product happens to be there.
     * <p></p>
     * Notes from previous implementations:
     *
     * WARNING: This is a bit tricky due to backward compatibility issues. Prior to
     * candlepin 2.0, pools serialized a collection of disjoint provided product info,
     * an object with just a product ID and name, not directly linked to anything in the db.
     *
     * In candlepin 2.0 we have referential integrity and links to full product objects,
     * but we need to maintain both API and import compatibility with old manifests and
     * servers that may import new manifests.
     *
     * To do this, we serialize the providedProductDtos and derivedProvidedProductDtos
     * collections on pool which keeps the API/manifest JSON identical to what it was
     * before. On import we load into these transient collections, and here we transfer
     * to the actual persisted location.
     *
     * @param subscription
     *  the subscription object to receive the merged product data
     *
     * @param upstreamPool
     *  the upstream entitlement pool which may contain product information
     */
    private void associateProducts(SubscriptionDTO subscription, PoolDTO upstreamPool)
        throws SyncDataFormatException {

        ProductDTO product = this.getImportedProduct(upstreamPool.getProductId());

        this.mergeBranding(product, upstreamPool.getBranding());
        this.mergeProvidedProducts(product, upstreamPool.getProvidedProducts());
        this.mergeDerivedProductData(product, upstreamPool);

        subscription.setProduct(product);
    }

    public SubscriptionDTO importObject(ObjectMapper mapper, Reader reader, Owner owner, String consumerUuid,
        Meta meta)
        throws IOException, SyncDataFormatException {

        EntitlementDTO entitlement = mapper.readValue(reader, EntitlementDTO.class);

        SubscriptionDTO subscription = new SubscriptionDTO();

        log.debug("Building subscription for owner: {}", owner);
        log.debug("Using pool from entitlement: {}", entitlement.getPool());

        // Now that we no longer store Subscriptions in the on-site database, we need to
        // manually give the subscription a downstream ID. Note that this may later be
        // overwritten by reconciliation code if it determines this Subscription
        // should replace and existing one.
        subscription.setId(Util.generateDbUUID());

        subscription.setUpstreamPoolId(entitlement.getPool().getId());
        subscription.setUpstreamEntitlementId(entitlement.getId());
        subscription.setUpstreamConsumerId(consumerUuid);

        subscription.setOwner(this.translator.translate(owner, OwnerDTO.class));

        subscription.setStartDate(entitlement.getStartDate());
        subscription.setEndDate(entitlement.getEndDate());

        subscription.setAccountNumber(entitlement.getPool().getAccountNumber());
        subscription.setContractNumber(entitlement.getPool().getContractNumber());
        subscription.setOrderNumber(entitlement.getPool().getOrderNumber());

        subscription.setQuantity(entitlement.getQuantity().longValue());

        // This is a bit of an odd duck. We shouldn't be checking this here, but instead at the point
        // where we actually import it for use.
        String cdnLabel = meta.getCdnLabel();
        if (!StringUtils.isBlank(cdnLabel)) {
            Cdn cdn = cdnCurator.getByLabel(cdnLabel);
            if (cdn != null) {
                subscription.setCdn(this.translator.translate(cdn, CdnDTO.class));
            }
        }

        this.associateProducts(subscription, entitlement.getPool());

        Collection<CertificateDTO> certs = entitlement.getCertificates();
        if (certs != null && !certs.isEmpty()) {
            if (certs.size() > 1) {
                log.error("More than one entitlement certificate found for subscription; using first: {}",
                    subscription);
            }

            subscription.setCertificate(certs.iterator().next());
        }
        else {
            log.error("No entitlement certificate found for subscription: {}", subscription);
        }

        return subscription;
    }

}
