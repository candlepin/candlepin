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
package org.candlepin.service.impl;

import java.io.ByteArrayInputStream;
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

import org.candlepin.config.Config;
import org.candlepin.model.CertificateSerial;
import org.candlepin.model.CertificateSerialCurator;
import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerCapability;
import org.candlepin.model.ConsumerType.ConsumerTypeEnum;
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
import org.candlepin.pki.X509ByteExtensionWrapper;
import org.candlepin.pki.X509ExtensionWrapper;
import org.candlepin.service.BaseEntitlementCertServiceAdapter;
import org.candlepin.service.ProductServiceAdapter;
import org.candlepin.util.CertificateSizeException;
import org.candlepin.util.Util;
import org.candlepin.util.X509ExtensionUtil;
import org.candlepin.util.X509Util;
import org.candlepin.util.X509V3ExtensionUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xnap.commons.i18n.I18n;

import com.google.common.collect.Collections2;
import com.google.inject.Inject;

/**
 * DefaultEntitlementCertServiceAdapter
 */
public class DefaultEntitlementCertServiceAdapter extends
    BaseEntitlementCertServiceAdapter {

    private PKIUtility pki;
    private X509ExtensionUtil extensionUtil;
    private X509V3ExtensionUtil v3extensionUtil;
    private KeyPairCurator keyPairCurator;
    private CertificateSerialCurator serialCurator;
    private ProductServiceAdapter productAdapter;
    private EntitlementCurator entCurator;
    private I18n i18n;
    private Config config;

    private static Logger log =
        LoggerFactory.getLogger(DefaultEntitlementCertServiceAdapter.class);

    @Inject
    public DefaultEntitlementCertServiceAdapter(PKIUtility pki,
        X509ExtensionUtil extensionUtil,
        X509V3ExtensionUtil v3extensionUtil,
        EntitlementCertificateCurator entCertCurator,
        KeyPairCurator keyPairCurator,
        CertificateSerialCurator serialCurator,
        ProductServiceAdapter productAdapter,
        EntitlementCurator entCurator, I18n i18n,
        Config config) {

        this.pki = pki;
        this.extensionUtil = extensionUtil;
        this.v3extensionUtil = v3extensionUtil;
        this.entCertCurator = entCertCurator;
        this.keyPairCurator = keyPairCurator;
        this.serialCurator = serialCurator;
        this.productAdapter = productAdapter;
        this.entCurator = entCurator;
        this.i18n = i18n;
        this.config = config;
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
        // TODO: eliminate the use of subscription here by looking up products in a batch
        // somehow, and we can eliminate all use of subscriptions during bind.
        if (sub != null) {
            // need to use the sub provided products if creating an
            // entitlement for derived pool who's sub specifies a
            // sub product.
            boolean derived = pool.hasAttribute("pool_derived");
            providedProducts = derived && sub.getDerivedProduct() != null ?
                sub.getDerivedProvidedProducts() : sub.getProvidedProducts();
        }
        else {
            // If this pool doesn't have a subscription associated with it, we need to
            // lookup all the Product objects manually:
            for (ProvidedProduct providedProduct : pool.getProvidedProducts()) {
                providedProducts.add(
                    productAdapter.getProductById(providedProduct.getProductId()));
            }
        }
        return providedProducts;
    }

    public X509Certificate createX509Certificate(Entitlement ent,
        Product product, Set<Product> products, BigInteger serialNumber,
        KeyPair keyPair, boolean useContentPrefix)
        throws GeneralSecurityException, IOException {

        // oidutil is busted at the moment, so do this manually
        Set<X509ExtensionWrapper> extensions;
        Set<X509ByteExtensionWrapper> byteExtensions =
            new LinkedHashSet<X509ByteExtensionWrapper>();
        products.add(product);

        Map<String, EnvironmentContent> promotedContent = getPromotedContent(ent);
        String contentPrefix = getContentPrefix(ent, useContentPrefix);


        if (shouldGenerateV3(ent)) {
            extensions = prepareV3Extensions(ent, contentPrefix, promotedContent);
            byteExtensions = prepareV3ByteExtensions(products, ent, contentPrefix,
                promotedContent);
        }
        else {
            extensions = prepareV1Extensions(products, ent, contentPrefix,
                promotedContent);
        }

        X509Certificate x509Cert =  this.pki.createX509Certificate(
                createDN(ent), extensions, byteExtensions, ent.getPool().getStartDate(),
                ent.getPool().getEndDate(), keyPair, serialNumber, null);
        return x509Cert;
    }

    private boolean shouldGenerateV3(Entitlement entitlement) {
        Consumer consumer = entitlement.getConsumer();

        if (consumer.getType().isManifest()) {
            for (ConsumerCapability capability : consumer.getCapabilities()) {
                if ("cert_v3".equals(capability.getName())) {
                    return true;
                }
            }
            return false;
        }
        else if (consumer.getType().getLabel().equals(
            ConsumerTypeEnum.HYPERVISOR.getLabel())) {
            // Hypervisors in this context don't use content, so allow v3
            return true;
        }
        else {
            String entitlementVersion = consumer.getFact("system.certificate_version");
            return entitlementVersion != null && entitlementVersion.startsWith("3.");
        }
    }

    /**
     * @param ent
     * @param useContentPrefix
     * @return
     * @throws IOException
     */
    private String getContentPrefix(Entitlement ent, boolean useContentPrefix)
        throws IOException {
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
        return contentPrefix;
    }

    /**
     * @param ent
     * @return
     */
    private Map<String, EnvironmentContent> getPromotedContent(Entitlement ent) {
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
        return promotedContent;
    }

    public Set<X509ExtensionWrapper> prepareV1Extensions(Set<Product> products,
        Entitlement ent, String contentPrefix,
        Map<String, EnvironmentContent> promotedContent) {
        Set<X509ExtensionWrapper> result =  new LinkedHashSet<X509ExtensionWrapper>();

        int contentCounter = 0;
        boolean enableEnvironmentFiltering = config.environmentFilteringEnabled();
        for (Product prod : Collections2
            .filter(products, X509Util.PROD_FILTER_PREDICATE)) {
            result.addAll(extensionUtil.productExtensions(prod));
            Set<ProductContent> filteredContent =
                extensionUtil.filterProductContent(prod, ent, entCurator,
                    promotedContent, enableEnvironmentFiltering);

            filteredContent = extensionUtil.filterContentByContentArch(filteredContent,
                ent.getConsumer(), prod);

            // Keep track of the number of content sets that are being added.
            contentCounter += filteredContent.size();

            result.addAll(extensionUtil.contentExtensions(filteredContent,
                contentPrefix, promotedContent, ent.getConsumer()));
        }

        // For V1 certificates we're going to error out if we exceed a limit which is
        // likely going to generate a certificate too large for the CDN, and return an
        // informative error message to the user.
        if (contentCounter > X509ExtensionUtil.V1_CONTENT_LIMIT) {
            String cause = i18n.tr("Too many content sets for certificate {0}. A newer " +
                    "client may be available to address this problem. " +
                    "See kbase https://access.redhat.com/knowledge/node/129003 for more " +
                    "information.", ent.getPool().getProductName());
            throw new CertificateSizeException(cause);
        }

        result.addAll(extensionUtil.subscriptionExtensions(ent));

        result.addAll(extensionUtil.entitlementExtensions(ent));
        result.addAll(extensionUtil.consumerExtensions(ent.getConsumer()));

        if (log.isDebugEnabled()) {
            for (X509ExtensionWrapper eWrapper : result) {
                log.debug(String.format("Extension %s with value %s",
                    eWrapper.getOid(), eWrapper.getValue()));
            }
        }
        return result;
    }

    public Set<X509ExtensionWrapper> prepareV3Extensions(Entitlement ent,
        String contentPrefix, Map<String, EnvironmentContent> promotedContent) {
        Set<X509ExtensionWrapper> result =  v3extensionUtil.getExtensions(ent,
            contentPrefix, promotedContent);
        return result;
    }

    public Set<X509ByteExtensionWrapper> prepareV3ByteExtensions(Set<Product> products,
        Entitlement ent, String contentPrefix,
        Map<String, EnvironmentContent> promotedContent)
        throws IOException {
        Set<X509ByteExtensionWrapper> result =  v3extensionUtil.getByteExtensions(products,
            ent, contentPrefix, promotedContent);
        return result;
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

        KeyPair keyPair = keyPairCurator.getConsumerKeyPair(entitlement.getConsumer());
        CertificateSerial serial = new CertificateSerial(entitlement.getEndDate());
        // We need the sequence generated id before we create the EntitlementCertificate,
        // otherwise we could have used cascading create
        serial = serialCurator.create(serial);

        Set<Product> products = new HashSet<Product>(getProvidedProducts(entitlement
            .getPool(), sub));
        X509Certificate x509Cert = createX509Certificate(entitlement,
            product, products, BigInteger.valueOf(serial.getId()), keyPair,
            !thisIsUeberCert);

        EntitlementCertificate cert = new EntitlementCertificate();
        cert.setSerial(serial);
        cert.setKeyAsBytes(pki.getPemEncoded(keyPair.getPrivate()));

        products.add(product);
        Map<String, EnvironmentContent> promotedContent = getPromotedContent(entitlement);
        String contentPrefix = getContentPrefix(entitlement, !thisIsUeberCert);

        String pem = new String(this.pki.getPemEncoded(x509Cert));

        if (shouldGenerateV3(entitlement)) {
            byte[] payloadBytes = v3extensionUtil.createEntitlementDataPayload(products,
                entitlement, contentPrefix, promotedContent);
            String payload = "-----BEGIN ENTITLEMENT DATA-----\n";
            payload += Util.toBase64(payloadBytes);
            payload += "-----END ENTITLEMENT DATA-----\n";

            byte[] bytes = pki.getSHA256WithRSAHash(new ByteArrayInputStream(payloadBytes));
            String signature = "-----BEGIN RSA SIGNATURE-----\n";
            signature += Util.toBase64(bytes);
            signature += "-----END RSA SIGNATURE-----\n";

            pem += payload + signature;
        }

        cert.setCert(pem);

        cert.setEntitlement(entitlement);

        if (log.isDebugEnabled()) {
            log.debug("Generated cert serial number: " + serial.getId());
            log.debug("Key: " + cert.getKey());
            log.debug("Cert: " + cert.getCert());
        }

        entitlement.getCertificates().add(cert);
        entCertCurator.create(cert);
        return cert;
    }

    private String createDN(Entitlement ent) {
        StringBuilder sb = new StringBuilder("CN=");
        sb.append(ent.getId());
        return sb.toString();
    }
}
