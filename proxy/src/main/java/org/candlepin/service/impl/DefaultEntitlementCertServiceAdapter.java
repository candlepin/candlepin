/**
 * Copyright (c) 2009 Red Hat, Inc.
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
package org.candlepin.service.impl;

import java.io.IOException;
import java.math.BigInteger;
import java.net.URLEncoder;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.cert.X509Certificate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang.StringUtils;
import org.candlepin.model.CertificateSerial;
import org.candlepin.model.CertificateSerialCurator;
import org.candlepin.model.Entitlement;
import org.candlepin.model.EntitlementCertificate;
import org.candlepin.model.EntitlementCertificateCurator;
import org.candlepin.model.EntitlementCurator;
import org.candlepin.model.Environment;
import org.candlepin.model.EnvironmentContent;
import org.candlepin.model.KeyPairCurator;
import org.candlepin.model.Pool;
import org.candlepin.model.Product;
import org.candlepin.model.ProductContent;
import org.candlepin.model.ProvidedProduct;
import org.candlepin.model.Subscription;
import org.candlepin.pki.PKIUtility;
import org.candlepin.pki.X509ExtensionWrapper;
import org.candlepin.service.BaseEntitlementCertServiceAdapter;
import org.candlepin.service.ProductServiceAdapter;
import org.candlepin.util.X509ExtensionUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.collect.Collections2;
import com.google.inject.Inject;

/**
 * DefaultEntitlementCertServiceAdapter
 */
public class DefaultEntitlementCertServiceAdapter extends
    BaseEntitlementCertServiceAdapter {

    private PKIUtility pki;
    private X509ExtensionUtil extensionUtil;
    private KeyPairCurator keyPairCurator;
    private CertificateSerialCurator serialCurator;
    private ProductServiceAdapter productAdapter;
    private EntitlementCurator entCurator;

    private static Logger log =
        LoggerFactory.getLogger(DefaultEntitlementCertServiceAdapter.class);

    @Inject
    public DefaultEntitlementCertServiceAdapter(PKIUtility pki,
        X509ExtensionUtil extensionUtil,
        EntitlementCertificateCurator entCertCurator,
        KeyPairCurator keyPairCurator,
        CertificateSerialCurator serialCurator,
        ProductServiceAdapter productAdapter,
        EntitlementCurator entCurator) {

        this.pki = pki;
        this.extensionUtil = extensionUtil;
        this.entCertCurator = entCertCurator;
        this.keyPairCurator = keyPairCurator;
        this.serialCurator = serialCurator;
        this.productAdapter = productAdapter;
        this.entCurator = entCurator;
    }


    // NOTE: we use entitlement here, but it version does not...
    // NOTE: we can get consumer from entitlement.getConsumer()
    @Override
    public EntitlementCertificate generateEntitlementCert(Entitlement entitlement,
        Subscription sub, Product product)
        throws GeneralSecurityException, IOException {
        return generateEntitlementCert(entitlement, sub, product, false);
    }

    @Override
    public EntitlementCertificate generateUeberCert(Entitlement entitlement,
        Subscription sub, Product product)
        throws GeneralSecurityException, IOException {
        return generateEntitlementCert(entitlement, sub, product, true);
    }


    @Override
    public void revokeEntitlementCertificates(Entitlement e) {
        for (EntitlementCertificate cert : e.getCertificates()) {
            CertificateSerial serial = cert.getSerial();
            serial.setRevoked(true);

            this.serialCurator.merge(serial);
        }
    }

    private Set<Product> getProvidedProducts(Pool pool, Subscription sub) {
        Set<Product> providedProducts = new HashSet<Product>();
        if (sub != null) {
            providedProducts = sub.getProvidedProducts();
        }
        else {
            // If this pool doesn't have a subscription associated with it, we need to
            // lookup all the Product objects manually:
            for (ProvidedProduct providedProduct : pool.getProvidedProducts()) {
                providedProducts.add(
                    productAdapter.getProductById(providedProduct.getId()));
            }
        }
        return providedProducts;
    }

    /**
     * Scan the product content looking for any which modify some other product. If found
     * we must check that this consumer has another entitlement granting them access
     * to that modified product. If they do not, we should filter out this content.
     *
     * @param prod
     * @param ent
     * @return ProductContent to include in the certificate.
     */
    public Set<ProductContent> filterProductContent(Product prod, Entitlement ent) {
        Set<ProductContent> filtered = new HashSet<ProductContent>();

        for (ProductContent pc : prod.getProductContent()) {
            boolean include = true;
            if (pc.getContent().getModifiedProductIds().size() > 0) {
                include = false;
                Set<String> prodIds = pc.getContent().getModifiedProductIds();
                // If consumer has an entitlement to just one of the modified products,
                // we will include this content set:
                for (String prodId : prodIds) {
                    Set<Entitlement> entsProviding = entCurator.listProviding(
                        ent.getConsumer(), prodId, ent.getStartDate(), ent.getEndDate());
                    if (entsProviding.size() > 0) {
                        include = true;
                        break;
                    }
                }
            }

            if (include) {
                filtered.add(pc);
            }
            else {
                log.debug("No entitlements found for modified products.");
                log.debug("Skipping content set: " + pc.getContent());
            }
        }
        return filtered;
    }

    public X509Certificate createX509Certificate(Entitlement ent,
        Subscription sub, Product product, BigInteger serialNumber,
        KeyPair keyPair, boolean useContentPrefix)
        throws GeneralSecurityException, IOException {

        // oiduitl is busted at the moment, so do this manually
        Set<X509ExtensionWrapper> extensions = new LinkedHashSet<X509ExtensionWrapper>();
        Set<Product> products = new HashSet<Product>(getProvidedProducts(ent
            .getPool(), sub));
        products.add(product);

        // Build a set of all content IDs promoted to the consumer's environment so
        // we can determine if anything needs to be skipped:
        Map<String, EnvironmentContent> promotedContent =
            new HashMap<String, EnvironmentContent>();
        if (ent.getConsumer().getEnvironment() != null) {
            log.debug("Consumer has environment, checking for promoted content in: " +
                ent.getConsumer().getEnvironment());
            for (EnvironmentContent envContent :
                    ent.getConsumer().getEnvironment().getEnvironmentContent()) {
                log.debug("  promoted content: " + envContent.getContentId());
                promotedContent.put(envContent.getContentId(), envContent);
            }
        }

        String contentPrefix = null;
        if (useContentPrefix) {
            contentPrefix = ent.getOwner().getContentPrefix();
            Environment env = ent.getConsumer().getEnvironment();
            if (contentPrefix != null && !contentPrefix.equals("")) {
                if (env != null) {
                    contentPrefix = contentPrefix.replaceAll("\\$env", env.getName());
                }

                contentPrefix = this.cleanUpPrefix(contentPrefix);
            }
        }

        for (Product prod : Collections2
            .filter(products, PROD_FILTER_PREDICATE)) {
            extensions.addAll(extensionUtil.productExtensions(prod));
            extensions.addAll(extensionUtil.contentExtensions(
                filterProductContent(prod, ent),
                contentPrefix, promotedContent, ent.getConsumer()));
        }

        if (sub != null) {
            extensions.addAll(extensionUtil.subscriptionExtensions(sub, ent));
        }

        extensions.addAll(extensionUtil.entitlementExtensions(ent));
        extensions.addAll(extensionUtil.consumerExtensions(ent.getConsumer()));

        if (log.isDebugEnabled()) {
            for (X509ExtensionWrapper eWrapper : extensions) {
                log.debug(String.format("Extension %s with value %s",
                    eWrapper.getOid(), eWrapper.getValue()));
            }
        }
        X509Certificate x509Cert = this.pki.createX509Certificate(
            createDN(ent), extensions, sub.getStartDate(), ent.getEndDate(),
            keyPair, serialNumber, null);

        return x509Cert;
    }

    // Encode the entire prefix in case any part of it is not
    // URL friendly. Any $ is put back in order to preseve
    // the ability to pass $env to the client
    public String cleanUpPrefix(String contentPrefix) throws IOException {


        StringBuffer output = new StringBuffer("/");
        for (String part : contentPrefix.split("/")) {
            if (!part.equals("")) {
                output.append(URLEncoder.encode(part, "UTF-8"));
                output.append("/");
            }
        }
        return output.toString().replace("%24", "$");
    }

    private EntitlementCertificate generateEntitlementCert(Entitlement entitlement,
        Subscription sub, Product product, boolean thisIsUeberCert)
        throws GeneralSecurityException, IOException {

        log.debug("Generating entitlement cert for:");
        log.debug("   consumer: {}", entitlement.getConsumer().getUuid());
        log.debug("   product: {}" , product.getId());
        log.debug("entitlement's endDt == subs endDt? {} == {} ?",
            entitlement.getEndDate(), sub.getEndDate());
        Preconditions
            .checkArgument(
                entitlement.getEndDate().getTime() == sub.getEndDate().getTime(),
                "Entitlement #%s 's endDt[%s] must equal Subscription #%s 's endDt[%s]",
                entitlement.getId(), entitlement.getEndDate(), sub.getId(),
                sub.getEndDate());

        KeyPair keyPair = keyPairCurator.getConsumerKeyPair(entitlement.getConsumer());
        CertificateSerial serial = new CertificateSerial(entitlement.getEndDate());
        // We need the sequence generated id before we create the EntitlementCertificate,
        // otherwise we could have used cascading create
        serialCurator.create(serial);

        X509Certificate x509Cert = createX509Certificate(entitlement, sub,
            product, BigInteger.valueOf(serial.getId()), keyPair, !thisIsUeberCert);

        EntitlementCertificate cert = new EntitlementCertificate();
        cert.setSerial(serial);
        cert.setKeyAsBytes(pki.getPemEncoded(keyPair.getPrivate()));
        cert.setCertAsBytes(this.pki.getPemEncoded(x509Cert));
        cert.setEntitlement(entitlement);

        log.debug("Generated cert serial number: " + serial.getId());
        log.debug("Key: " + cert.getKey());
        log.debug("Cert: " + cert.getCert());

        entitlement.getCertificates().add(cert);
        entCertCurator.create(cert);
        return cert;
    }

    private String createDN(Entitlement ent) {
        StringBuilder sb = new StringBuilder("CN=");
        sb.append(ent.getId());
        return sb.toString();
    }

    private static final Predicate<Product>
    PROD_FILTER_PREDICATE = new Predicate<Product>() {
        @Override
        public boolean apply(Product product) {
            return product != null && StringUtils.isNumeric(product.getId());
        }
    };
}
