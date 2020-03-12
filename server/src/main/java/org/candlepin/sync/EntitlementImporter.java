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
package org.candlepin.sync;

import org.candlepin.dto.ModelTranslator;
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
import org.candlepin.model.CertificateSerialCurator;
import org.candlepin.model.EntitlementCurator;
import org.candlepin.model.Owner;
import org.candlepin.model.ProductCurator;
import org.candlepin.util.Util;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xnap.commons.i18n.I18n;

import java.io.IOException;
import java.io.Reader;
import java.util.Collection;
import java.util.Map;



/**
 * EntitlementImporter - turn an upstream Entitlement into a local subscription
 */
public class EntitlementImporter {
    private static Logger log = LoggerFactory.getLogger(EntitlementImporter.class);

    private CertificateSerialCurator csCurator;
    private CdnCurator cdnCurator;
    private I18n i18n;
    private ProductCurator productCurator;
    private EntitlementCurator entitlementCurator;
    private ModelTranslator translator;

    public EntitlementImporter(CertificateSerialCurator csCurator, CdnCurator cdnCurator, I18n i18n,
        ProductCurator productCurator, EntitlementCurator entitlementCurator, ModelTranslator translator) {

        this.csCurator = csCurator;
        this.cdnCurator = cdnCurator;
        this.i18n = i18n;
        this.productCurator = productCurator;
        this.entitlementCurator = entitlementCurator;
        this.translator = translator;
    }

    public SubscriptionDTO importObject(ObjectMapper mapper, Reader reader, Owner owner,
        Map<String, ProductDTO> productsById, String consumerUuid, Meta meta)
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

        this.associateProducts(productsById, entitlement.getPool(), subscription);

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

    /*
     * Transfer associations to provided and derived provided products over to the
     * subscription.
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
     * @param productsById
     *  A mapping of product IDs to product DTOs being present in the imported manifest
     *
     * @param upstreamPoolDTO
     *  A PoolDTO instance representing the upstream pool containing the collections of provided
     *  products and derived provided product references to set on the subscription
     *
     * @param subscription
     *  The SubscriptionDTO instance to update
     */
    public void associateProducts(Map<String, ProductDTO> productsById, PoolDTO upstreamPoolDTO,
        SubscriptionDTO subscription) throws SyncDataFormatException {
        // Product
        ProductDTO productDTO = this.findProduct(productsById, upstreamPoolDTO.getProductId());
        subscription.setProduct(productDTO);

        if (upstreamPoolDTO.getBranding() != null) {
            productDTO.setBranding(upstreamPoolDTO.getBranding());
        }

        for (ProvidedProductDTO pp : upstreamPoolDTO.getProvidedProducts()) {
            ProductDTO product = this.findProduct(productsById, pp.getProductId());

            if (product != null) {
                productDTO.addProvidedProduct(product);
            }
        }

        // Derived product
        if (upstreamPoolDTO.getDerivedProductId() != null) {
            productDTO = this.findProduct(productsById, upstreamPoolDTO.getDerivedProductId());
            subscription.setDerivedProduct(productDTO);

            for (ProvidedProductDTO pp : upstreamPoolDTO.getDerivedProvidedProducts()) {
                ProductDTO product = this.findProduct(productsById, pp.getProductId());

                if (product != null) {
                    productDTO.addProvidedProduct(product);
                }
            }
        }

    }

    private ProductDTO findProduct(Map<String, ProductDTO> productsById, String productId)
        throws SyncDataFormatException {

        ProductDTO product = productsById.get(productId);
        if (product == null) {
            throw new SyncDataFormatException(i18n.tr("Unable to find product with ID: {0}", productId));
        }

        return product;
    }
}
