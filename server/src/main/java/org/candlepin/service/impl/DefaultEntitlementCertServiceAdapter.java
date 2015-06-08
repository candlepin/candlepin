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

import org.candlepin.common.config.Configuration;
import org.candlepin.config.ConfigProperties;
import org.candlepin.model.CertificateSerial;
import org.candlepin.model.CertificateSerialCurator;
import org.candlepin.model.Consumer;
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
import org.candlepin.pki.PKIUtility;
import org.candlepin.pki.X509ByteExtensionWrapper;
import org.candlepin.pki.X509ExtensionWrapper;
import org.candlepin.service.BaseEntitlementCertServiceAdapter;
import org.candlepin.util.CertificateSizeException;
import org.candlepin.util.Util;
import org.candlepin.util.X509ExtensionUtil;
import org.candlepin.util.X509Util;
import org.candlepin.util.X509V3ExtensionUtil;

import com.google.common.collect.Collections2;
import com.google.inject.Inject;

import org.apache.commons.lang.BooleanUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xnap.commons.i18n.I18n;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.net.URLEncoder;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.cert.X509Certificate;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
    private EntitlementCurator entCurator;
    private I18n i18n;
    private Configuration config;

    private static Logger log =
        LoggerFactory.getLogger(DefaultEntitlementCertServiceAdapter.class);

    @Inject
    public DefaultEntitlementCertServiceAdapter(PKIUtility pki,
        X509ExtensionUtil extensionUtil,
        X509V3ExtensionUtil v3extensionUtil,
        EntitlementCertificateCurator entCertCurator,
        KeyPairCurator keyPairCurator,
        CertificateSerialCurator serialCurator,
        EntitlementCurator entCurator, I18n i18n,
        Configuration config) {

        this.pki = pki;
        this.extensionUtil = extensionUtil;
        this.v3extensionUtil = v3extensionUtil;
        this.entCertCurator = entCertCurator;
        this.keyPairCurator = keyPairCurator;
        this.serialCurator = serialCurator;
        this.entCurator = entCurator;
        this.i18n = i18n;
        this.config = config;
    }


    // NOTE: we use entitlement here, but it version does not...
    // NOTE: we can get consumer from entitlement.getConsumer()
    @Override
    public EntitlementCertificate generateEntitlementCert(Entitlement entitlement, Product product)
        throws GeneralSecurityException, IOException {
        return generateEntitlementCert(entitlement, product, false);
    }

    @Override
    public EntitlementCertificate generateUeberCert(Entitlement entitlement, Product product)
        throws GeneralSecurityException, IOException {
        return generateEntitlementCert(entitlement, product, true);
    }

    private Set<Product> getDerivedProductsForDistributor(Entitlement ent) {
        Set<Product> derivedProducts = new HashSet<Product>();
        boolean derived = ent.getPool().hasAttribute("pool_derived");
        if (!derived && ent.getConsumer().getType().isManifest() &&
            ent.getPool().getDerivedProduct() != null) {
            derivedProducts.add(ent.getPool().getDerivedProduct());
            derivedProducts.addAll(ent.getPool().getDerivedProvidedProducts());
        }
        return derivedProducts;
    }

    // TODO: productModels not used by V1 certificates. This whole v1/v3 split needs
    // a re-org. Passing them here because it eliminates a substantial performance hit
    // recalculating this for the entitlement body in v3 certs.
    public X509Certificate createX509Certificate(Entitlement ent,
        Product product, Set<Product> products,
        List<org.candlepin.json.model.Product> productModels,
        BigInteger serialNumber,
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
            byteExtensions = prepareV3ByteExtensions(product, productModels,
                    ent, contentPrefix, promotedContent);
        }
        else {
            extensions = prepareV1Extensions(products, ent, contentPrefix,
                promotedContent);
        }

        setupEntitlementEndDate(ent);
        X509Certificate x509Cert =  this.pki.createX509Certificate(
                createDN(ent), extensions, byteExtensions, ent.getStartDate(),
                ent.getEndDate(), keyPair, serialNumber, null);
        return x509Cert;
    }

    /**
     * Modify the entitlements end date
     * @param ent
     */
    private void setupEntitlementEndDate(Entitlement ent) {
        Pool pool = ent.getPool();
        Consumer consumer = ent.getConsumer();

        Date startDate = new Date();
        if (consumer.getCreated() != null) {
            startDate = consumer.getCreated();
        }

        boolean isUnmappedGuestPool = BooleanUtils.toBoolean(
                pool.getAttributeValue("unmapped_guests_only"));

        if (isUnmappedGuestPool) {
            Date oneDayFromRegistration = new Date(startDate.getTime() + 24L * 60L * 60L * 1000L);
            log.info("Setting 24h expiration for unmapped guest pool entilement: " +
                    oneDayFromRegistration);
            ent.setEndDateOverride(oneDayFromRegistration);
            entCurator.merge(ent);
        }
    }

    private boolean shouldGenerateV3(Entitlement entitlement) {
        Consumer consumer = entitlement.getConsumer();
        return consumer != null && consumer.isCertV3Capable();
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
                log.debug("  promoted content: " + envContent.getContent().getId());
                promotedContent.put(envContent.getContent().getId(), envContent);
            }
        }
        return promotedContent;
    }

    public Set<X509ExtensionWrapper> prepareV1Extensions(Set<Product> products,
        Entitlement ent, String contentPrefix,
        Map<String, EnvironmentContent> promotedContent) {
        Set<X509ExtensionWrapper> result =  new LinkedHashSet<X509ExtensionWrapper>();

        Set<String> entitledProductIds = entCurator.listEntitledProductIds(
                ent.getConsumer(), ent.getPool().getStartDate(),
                ent.getPool().getEndDate());

        int contentCounter = 0;
        boolean enableEnvironmentFiltering = config.getBoolean(ConfigProperties.ENV_CONTENT_FILTERING);

        Product skuProd = ent.getPool().getProduct();

        for (Product prod : Collections2.filter(products, X509Util.PROD_FILTER_PREDICATE)) {
            log.debug("Adding X509 extensions for product: {}", prod);
            result.addAll(extensionUtil.productExtensions(prod));

            Set<ProductContent> filteredContent =
                extensionUtil.filterProductContent(prod, ent, entCurator,
                    promotedContent, enableEnvironmentFiltering, entitledProductIds);

            filteredContent = extensionUtil.filterContentByContentArch(filteredContent,
                ent.getConsumer(), prod);

            // Keep track of the number of content sets that are being added.
            contentCounter += filteredContent.size();

            log.debug("Adding X509 extensions for content: {}", filteredContent);
            result.addAll(extensionUtil.contentExtensions(filteredContent,
                contentPrefix, promotedContent, ent.getConsumer(), skuProd));
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
                log.debug("Extension {} with value {}", eWrapper.getOid(), eWrapper.getValue());
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

    public Set<X509ByteExtensionWrapper> prepareV3ByteExtensions(Product sku,
            List<org.candlepin.json.model.Product> productModels,
            Entitlement ent, String contentPrefix,
            Map<String, EnvironmentContent> promotedContent) throws IOException {

        Set<X509ByteExtensionWrapper> result =  v3extensionUtil.getByteExtensions(sku,
                productModels, ent, contentPrefix, promotedContent);
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

    private EntitlementCertificate generateEntitlementCert(Entitlement entitlement, Product product,
        boolean thisIsUeberCert) throws GeneralSecurityException, IOException {

        log.info("Generating entitlement cert for entitlement: {}", entitlement);

        KeyPair keyPair = keyPairCurator.getConsumerKeyPair(entitlement.getConsumer());
        CertificateSerial serial = new CertificateSerial(entitlement.getEndDate());
        // We need the sequence generated id before we create the EntitlementCertificate,
        // otherwise we could have used cascading create
        serial = serialCurator.create(serial);

        Set<Product> products = new HashSet<Product>(entitlement.getPool().getProvidedProducts());

        // If creating a certificate for a distributor, we need
        // to add any derived products as well so that their content
        // is available in the upstream certificate.
        products.addAll(getDerivedProductsForDistributor(entitlement));
        products.add(product);

        Map<String, EnvironmentContent> promotedContent = getPromotedContent(entitlement);
        String contentPrefix = getContentPrefix(entitlement, !thisIsUeberCert);

        log.info("Creating X509 cert for product: {}", product);
        log.debug("Provided products: {}", products);
        List<org.candlepin.json.model.Product> productModels =
                v3extensionUtil.createProducts(product, products, contentPrefix,
                        promotedContent,
                        entitlement.getConsumer(), entitlement);

        X509Certificate x509Cert = createX509Certificate(entitlement,
            product, products, productModels, BigInteger.valueOf(serial.getId()), keyPair,
            !thisIsUeberCert);

        EntitlementCertificate cert = new EntitlementCertificate();
        cert.setSerial(serial);
        cert.setKeyAsBytes(pki.getPemEncoded(keyPair.getPrivate()));

        log.info("Getting PEM encoded cert.");
        String pem = new String(this.pki.getPemEncoded(x509Cert));

        if (shouldGenerateV3(entitlement)) {
            log.debug("Generating v3 entitlement data");

            byte[] payloadBytes = v3extensionUtil.createEntitlementDataPayload(product,
                    productModels, entitlement, contentPrefix, promotedContent);

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

        log.info("Persisting cert.");
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
