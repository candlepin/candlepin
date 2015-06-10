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

import org.candlepin.model.Branding;
import org.candlepin.model.Cdn;
import org.candlepin.model.CdnCurator;
import org.candlepin.model.CertificateSerial;
import org.candlepin.model.CertificateSerialCurator;
import org.candlepin.model.Entitlement;
import org.candlepin.model.EntitlementCertificate;
import org.candlepin.model.Owner;
import org.candlepin.model.Product;
import org.candlepin.model.ProvidedProduct;
import org.candlepin.model.SubscriptionsCertificate;
import org.candlepin.model.dto.Subscription;
import org.candlepin.util.Util;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xnap.commons.i18n.I18n;

import java.io.IOException;
import java.io.Reader;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * EntitlementImporter - turn an upstream Entitlement into a local subscription
 */
public class EntitlementImporter {
    private static Logger log = LoggerFactory.getLogger(EntitlementImporter.class);

    private CertificateSerialCurator csCurator;
    private CdnCurator cdnCurator;
    private I18n i18n;

    public EntitlementImporter(CertificateSerialCurator csCurator,
        CdnCurator cdnCurator, I18n i18n) {

        this.csCurator = csCurator;
        this.cdnCurator = cdnCurator;
        this.i18n = i18n;
    }

    public Subscription importObject(ObjectMapper mapper, Reader reader, Owner owner,
        Map<String, Product> productsById, ConsumerDto consumer, Meta meta)
        throws IOException, SyncDataFormatException {

        Entitlement entitlement = mapper.readValue(reader, Entitlement.class);
        Subscription subscription = new Subscription();

        log.debug("Building subscription for owner: {}", owner);
        log.debug("Using pool from entitlement: {}", entitlement.getPool());
        log.debug("Pool derivedProduct: {}", entitlement.getPool().getDerivedProduct());

        // Now that we no longer store Subscriptions in the on-site database, we need to
        // manually give the subscription a downstream ID. Note that this may later be
        // overwritten by reconciliation code if it determines this Subscription
        // should replace and existing one.
        subscription.setId(Util.generateDbUUID());

        subscription.setUpstreamPoolId(entitlement.getPool().getId());
        subscription.setUpstreamEntitlementId(entitlement.getId());
        subscription.setUpstreamConsumerId(consumer.getUuid());

        subscription.setOwner(owner);

        subscription.setStartDate(entitlement.getStartDate());
        subscription.setEndDate(entitlement.getEndDate());

        subscription.setAccountNumber(entitlement.getPool().getAccountNumber());
        subscription.setContractNumber(entitlement.getPool().getContractNumber());
        subscription.setOrderNumber(entitlement.getPool().getOrderNumber());

        subscription.setQuantity(entitlement.getQuantity().longValue());

        for (Branding b : entitlement.getPool().getBranding()) {
            subscription.getBranding().add(new Branding(b.getProductId(), b.getType(),
                b.getName()));
        }

        subscription.setProduct(findProduct(productsById, entitlement.getProductId()));
        String cdnLabel = meta.getCdnLabel();
        if (!StringUtils.isBlank(cdnLabel)) {
            Cdn cdn = cdnCurator.lookupByLabel(cdnLabel);
            if (cdn != null) {
                subscription.setCdn(cdn);
            }
        }

        Set<Product> products = new HashSet<Product>();
        for (Product providedProduct : entitlement.getPool().getProvidedProducts()) {
            products.add(findProduct(productsById, providedProduct.getId()));
        }

        subscription.setProvidedProducts(products);

        // Add any sub product data to the subscription.
        if (entitlement.getPool().getDerivedProduct() != null) {
            subscription.setDerivedProduct(findProduct(
                productsById, entitlement.getPool().getDerivedProduct().getId()
            ));
        }

        Set<Product> subProvProds = new HashSet<Product>();
        for (Product subProvProd : entitlement.getPool().getDerivedProvidedProducts()) {
            subProvProds.add(findProduct(productsById, subProvProd.getId()));
        }
        entitlement.getPool().setDerivedProvidedProducts(null);

        for (ProvidedProduct pp : entitlement.getPool().getDerivedProvidedProductDtos()) {
            subProvProds.add(this.findProduct(productsById, pp.getProductId()));
        }
        entitlement.getPool().setDerivedProvidedProductDtos(null);

        log.debug("Entitlement has {} dpp", entitlement.getPool().getDerivedProvidedProducts().size());
        log.debug("Entitlement has {} dpp-dto", entitlement.getPool().getDerivedProvidedProductDtos().size());
        log.debug("Subscription has {} derived provided products.", subProvProds.size());

        subscription.setDerivedProvidedProducts(subProvProds);

        Set<EntitlementCertificate> certs = entitlement.getCertificates();

        // subscriptions have one cert
        int entcnt = 0;
        for (EntitlementCertificate cert : certs) {
            entcnt++;
            CertificateSerial cs = new CertificateSerial();
            cs.setCollected(cert.getSerial().isCollected());
            cs.setExpiration(cert.getSerial().getExpiration());
            cs.setUpdated(cert.getSerial().getUpdated());
            cs.setCreated(cert.getSerial().getCreated());
            csCurator.create(cs);
            SubscriptionsCertificate sc = new SubscriptionsCertificate();
            sc.setKey(cert.getKey());
            sc.setCertAsBytes(cert.getCertAsBytes());
            sc.setSerial(cs);
            subscription.setCertificate(sc);
        }

        if (entcnt > 1) {
            log.error("More than one entitlement cert found for subscription");
        }

        return subscription;
    }

    private Product findProduct(Map<String, Product> productsById, String productId)
        throws SyncDataFormatException {

        Product product = productsById.get(productId);
        if (product == null) {
            throw new SyncDataFormatException(i18n.tr("Unable to find product with ID: {0}", productId));
        }

        return product;
    }

}
