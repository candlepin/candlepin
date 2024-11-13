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
package org.candlepin.pki.certs;

import org.candlepin.config.ConfigProperties;
import org.candlepin.config.Configuration;
import org.candlepin.controller.util.ContentPathBuilder;
import org.candlepin.controller.util.PromotedContent;
import org.candlepin.model.CertificateSerial;
import org.candlepin.model.CertificateSerialCurator;
import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerCapability;
import org.candlepin.model.ConsumerType;
import org.candlepin.model.ConsumerType.ConsumerTypeEnum;
import org.candlepin.model.ConsumerTypeCurator;
import org.candlepin.model.Entitlement;
import org.candlepin.model.EntitlementCertificate;
import org.candlepin.model.EntitlementCertificateCurator;
import org.candlepin.model.EntitlementCurator;
import org.candlepin.model.Environment;
import org.candlepin.model.EnvironmentCurator;
import org.candlepin.model.Owner;
import org.candlepin.model.OwnerCurator;
import org.candlepin.model.Pool;
import org.candlepin.model.PoolQuantity;
import org.candlepin.model.Product;
import org.candlepin.model.ProductContent;
import org.candlepin.pki.DistinguishedName;
import org.candlepin.pki.KeyPairGenerator;
import org.candlepin.pki.OID;
import org.candlepin.pki.PemEncoder;
import org.candlepin.pki.X509Extension;
import org.candlepin.pki.impl.Signer;
import org.candlepin.util.CertificateSizeException;
import org.candlepin.util.Util;
import org.candlepin.util.X509ExtensionUtil;
import org.candlepin.util.X509Util;
import org.candlepin.util.X509V3ExtensionUtil;

import com.google.common.collect.Collections2;

import org.apache.commons.lang3.BooleanUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xnap.commons.i18n.I18n;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.security.KeyPair;
import java.security.cert.X509Certificate;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;


/**
 * This generator is responsible for generation of entitlement certificates.
 */
@Singleton
public class EntitlementCertificateGenerator {
    private static final Logger log = LoggerFactory.getLogger(EntitlementCertificateGenerator.class);

    private final EntitlementCertificateCurator entCertCurator;
    private final X509ExtensionUtil extensionUtil;
    private final X509V3ExtensionUtil v3extensionUtil;
    private final EntitlementPayloadGenerator payloadGenerator;
    private final CertificateSerialCurator serialCurator;
    private final OwnerCurator ownerCurator;
    private final EntitlementCurator entCurator;
    private final I18n i18n;
    private final Configuration config;
    private final ConsumerTypeCurator consumerTypeCurator;
    private final EnvironmentCurator environmentCurator;
    private final KeyPairGenerator keyPairGenerator;
    private final PemEncoder pemEncoder;
    private final Signer signer;
    private final Provider<X509CertificateBuilder> certificateBuilder;

    @Inject
    public EntitlementCertificateGenerator(
        X509ExtensionUtil extensionUtil,
        X509V3ExtensionUtil v3extensionUtil,
        EntitlementPayloadGenerator payloadGenerator,
        EntitlementCertificateCurator entCertCurator,
        CertificateSerialCurator serialCurator,
        OwnerCurator ownerCurator,
        EntitlementCurator entCurator, I18n i18n,
        Configuration config,
        ConsumerTypeCurator consumerTypeCurator,
        EnvironmentCurator environmentCurator,
        KeyPairGenerator keyPairGenerator,
        PemEncoder pemEncoder,
        Signer signer,
        Provider<X509CertificateBuilder> certificateBuilder) {

        this.extensionUtil = Objects.requireNonNull(extensionUtil);
        this.v3extensionUtil = Objects.requireNonNull(v3extensionUtil);
        this.payloadGenerator = Objects.requireNonNull(payloadGenerator);
        this.entCertCurator = Objects.requireNonNull(entCertCurator);
        this.serialCurator = Objects.requireNonNull(serialCurator);
        this.ownerCurator = Objects.requireNonNull(ownerCurator);
        this.entCurator = Objects.requireNonNull(entCurator);
        this.i18n = Objects.requireNonNull(i18n);
        this.config = Objects.requireNonNull(config);
        this.consumerTypeCurator = Objects.requireNonNull(consumerTypeCurator);
        this.environmentCurator = Objects.requireNonNull(environmentCurator);
        this.keyPairGenerator = Objects.requireNonNull(keyPairGenerator);
        this.pemEncoder = Objects.requireNonNull(pemEncoder);
        this.signer = Objects.requireNonNull(signer);
        this.certificateBuilder = Objects.requireNonNull(certificateBuilder);
    }

    /**
     * Method takes pool, entitlement and product data and generates entitlement
     * certificates for the given consumer. The certificates are indexed by pool id.
     *
     * @param consumer
     *  The consumer to use for certificate generation.
     * @param poolQuantities
     *  Pools and their quantities to use for certificate generation.
     * @param entitlements
     *  A map of entitlements indexed by pool ids to generate the certs.
     * @param products
     *  A map of respective products indexed by pool id.
     * @param save
     *  A flag whether to store the created certs in the DB
     * @return The respective entitlement certs indexed by pool id
     */
    public Map<String, EntitlementCertificate> generate(Consumer consumer,
        Map<String, PoolQuantity> poolQuantities,
        Map<String, Entitlement> entitlements,
        Map<String, Product> products,
        boolean save) {

        Owner owner = this.ownerCurator.findOwnerById(consumer.getOwnerId());

        log.debug("Generating entitlement cert for entitlements");
        KeyPair keyPair = this.keyPairGenerator.getKeyPair(consumer);
        byte[] pemEncodedKeyPair = this.pemEncoder.encodeAsBytes(keyPair.getPrivate());

        Map<String, CertificateSerial> serialMap = createSerials(poolQuantities);
        Set<Pool> entitledPools = poolQuantities.values().stream()
            .map(PoolQuantity::getPool)
            .collect(Collectors.toSet());

        List<Environment> environments = this.environmentCurator.getConsumerEnvironments(consumer);
        ContentPathBuilder contentPathBuilder = ContentPathBuilder.from(owner, environments);
        PromotedContent promotedContent = new PromotedContent(contentPathBuilder)
            .withAll(environments);

        Map<String, EntitlementCertificate> entitlementCerts = new HashMap<>();
        boolean shouldCreateV3Certificate = shouldGenerateV3(consumer);
        for (Entry<String, PoolQuantity> entry : poolQuantities.entrySet()) {
            Pool pool = entry.getValue().getPool();
            Entitlement ent = entitlements.get(entry.getKey());
            CertificateSerial serial = serialMap.get(entry.getKey());
            Product product = products.get(entry.getKey());

            log.info("Generating entitlement cert for pool: {} quantity: {} entitlement id: {}",
                pool,
                ent.getQuantity(),
                ent.getId());

            Set<Product> providedProducts = new HashSet<>(pool.getProduct().getProvidedProducts());

            // If creating a certificate for a distributor, we need
            // to add any derived products as well so that their content
            // is available in the upstream certificate.
            providedProducts.addAll(getDerivedProductsForDistributor(pool, consumer));
            providedProducts.add(product);

            log.info("Creating X509 cert for product: {}", product);
            log.debug("Provided products: {}", providedProducts);
            List<org.candlepin.model.dto.Product> productModels = v3extensionUtil.createProducts(product,
                providedProducts, promotedContent, consumer, pool, entitledPools);

            providedProducts.add(product);
            Set<X509Extension> extensions = prepareExtensions(consumer, pool, productModels,
                providedProducts, ent, promotedContent, entitledPools);
            X509Certificate x509Cert = createX509Certificate(consumer, owner, pool, ent,
                BigInteger.valueOf(serial.getId()), keyPair, extensions);

            log.debug("Getting PEM encoded cert.");
            String pem = this.pemEncoder.encodeAsString(x509Cert);

            if (shouldCreateV3Certificate) {
                pem = createPayloadAndSignature(consumer, productModels, pool, ent, pem);
            }

            // Build a skeleton cert as part of the entitlement processing.
            EntitlementCertificate cert = new EntitlementCertificate();
            cert.setKeyAsBytes(pemEncodedKeyPair);
            cert.setCert(pem);
            if (save) {
                cert.setEntitlement(ent);
            }

            if (log.isDebugEnabled()) {
                log.debug("Generated cert serial number: {}", serial.getId());
                log.debug("Key: {}", cert.getKey());
                log.debug("Cert: {}", cert.getCert());
            }

            entitlementCerts.put(entry.getKey(), cert);
        }

        // Now that the serials have been saved, update the newly created
        // certs with their serials and add them to the entitlements.
        for (Entry<String, PoolQuantity> entry : poolQuantities.entrySet()) {
            CertificateSerial nextSerial = serialMap.get(entry.getKey());
            if (nextSerial == null) {
                // This should never happen, but checking to be safe.
                throw new CertificateCreationException(
                    "Certificate serial not found for entitlement during cert generation.");
            }

            EntitlementCertificate nextCert = entitlementCerts.get(entry.getKey());
            if (nextCert == null) {
                // This should never happen, but checking to be safe.
                throw new CertificateCreationException(
                    "Entitlement certificate not found for entitlement during cert generation");
            }

            nextCert.setSerial(nextSerial);
            if (save) {
                Entitlement ent = entitlements.get(entry.getKey());
                ent.addCertificate(nextCert);
            }
        }

        if (save) {
            log.info("Persisting certs.");
            entCertCurator.saveOrUpdateAll(entitlementCerts.values(), false, false);
        }

        return entitlementCerts;
    }

    private Set<Product> getDerivedProductsForDistributor(Pool pool, Consumer consumer) {
        Set<Product> derivedProducts = new HashSet<>();

        if (!pool.hasAttribute(Pool.Attributes.DERIVED_POOL) && this.isManifestDistributor(consumer)) {
            Product derivedProduct = pool.getDerivedProduct();

            if (derivedProduct != null) {
                derivedProducts.add(derivedProduct);
                derivedProducts.addAll(derivedProduct.getProvidedProducts());
            }
        }

        return derivedProducts;
    }

    private X509Certificate createX509Certificate(Consumer consumer, Owner owner, Pool pool, Entitlement ent,
        BigInteger serialNumber, KeyPair keyPair, Set<X509Extension> extensions) {

        Date endDate = setupEntitlementEndDate(pool, consumer);
        ent.setEndDateOverride(endDate);
        Calendar calNow = Calendar.getInstance();
        Calendar calMinusHour = Calendar.getInstance();
        calMinusHour.add(Calendar.HOUR, -1);
        Date startDate = pool.getStartDate();
        if (pool.getStartDate().getTime() > calMinusHour.getTime().getTime() &&
            pool.getStartDate().getTime() < calNow.getTime().getTime()) {
            startDate = calMinusHour.getTime();
        }
        DistinguishedName dn = new DistinguishedName(ent.getId(), owner);

        return this.certificateBuilder.get()
            .withDN(dn)
            .withSerial(serialNumber)
            .withValidity(startDate.toInstant(), endDate.toInstant())
            .withKeyPair(keyPair)
            .withExtensions(extensions)
            .build();
    }

    private Set<X509Extension> prepareV3ByteExtensions(List<org.candlepin.model.dto.Product> productModels) {
        try {
            return this.v3extensionUtil.getByteExtensions(productModels);
        }
        catch (IOException e) {
            throw new CertificateCreationException("Failed to create extensions!", e);
        }
    }

    private Date setupEntitlementEndDate(Pool pool, Consumer consumer) {
        Date startDate = new Date();
        if (consumer.getCreated() != null) {
            startDate = consumer.getCreated();
        }

        boolean isUnmappedGuestPool = BooleanUtils.toBoolean(
            pool.getAttributeValue(Pool.Attributes.UNMAPPED_GUESTS_ONLY));

        if (isUnmappedGuestPool) {
            Date sevenDaysFromRegistration = new Date(startDate.getTime() + 7L * 24L * 60L * 60L * 1000L);
            log.info("Setting 7 day expiration for unmapped guest pool entilement: {}",
                sevenDaysFromRegistration);
            return sevenDaysFromRegistration;
        }

        return pool.getEndDate();
    }

    /**
     * Checks if the specified consumer is capable of using v3 certificates
     *
     * @param consumer
     *  The consumer to check
     *
     * @return
     *  true if the consumer should use v3 certificates; false otherwise
     */
    private boolean shouldGenerateV3(Consumer consumer) {
        if (consumer != null) {
            ConsumerType type = this.consumerTypeCurator.getConsumerType(consumer);

            if (type.isManifest()) {
                for (ConsumerCapability capability : consumer.getCapabilities()) {
                    if ("cert_v3".equals(capability.getName())) {
                        return true;
                    }
                }

                return false;
            }
            else if (type.isType(ConsumerTypeEnum.HYPERVISOR)) {
                // Hypervisors in this context don't use content, so V3 is allowed
                return true;
            }

            // Consumer isn't a special type, check their certificate_version fact
            String entitlementVersion = consumer.getFact(Consumer.Facts.SYSTEM_CERTIFICATE_VERSION);
            return entitlementVersion != null && entitlementVersion.startsWith("3.");
        }

        return false;
    }

    /**
     * Checks if the specified consumer is a manifest distributor
     *
     * @param consumer
     *  The consumer to check
     *
     * @return
     *  true if the consumer is a manifest distributor; false otherwise
     */
    private boolean isManifestDistributor(Consumer consumer) {
        if (consumer != null) {
            ConsumerType type = this.consumerTypeCurator.getConsumerType(consumer);
            return type.isManifest();
        }

        return false;
    }

    private Set<X509Extension> prepareExtensions(Consumer consumer, Pool pool,
        List<org.candlepin.model.dto.Product> productModels, Set<Product> products, Entitlement ent,
        PromotedContent promotedContent, Set<Pool> entitledPools) {
        Set<X509Extension> extensions = new HashSet<>();
        if (shouldGenerateV3(consumer)) {
            extensions.addAll(prepareV3Extensions(pool));
            extensions.addAll(prepareV3ByteExtensions(productModels));
        }
        else {
            extensions.addAll(prepareV1Extensions(products, pool, consumer, ent.getQuantity(),
                promotedContent, entitledPools));
        }
        return extensions;
    }

    private Set<X509Extension> prepareV1Extensions(Set<Product> products, Pool pool, Consumer consumer,
        Integer quantity, PromotedContent promotedContent, Set<Pool> entitledPools) {
        Set<X509Extension> result = new LinkedHashSet<>();

        Set<String> entitledProductIds = entCurator.listEntitledProductIds(
            consumer, pool, entitledPools);

        int contentCounter = 0;
        boolean enableEnvironmentFiltering = config.getBoolean(ConfigProperties.ENV_CONTENT_FILTERING);

        Product skuProd = pool.getProduct();

        for (Product prod : Collections2.filter(products, X509Util.PROD_FILTER_PREDICATE)) {
            log.debug("Adding X509 extensions for product: {}", prod);
            result.addAll(extensionUtil.productExtensions(prod));

            Set<ProductContent> filteredContent = extensionUtil.filterProductContent(
                prod, consumer, promotedContent, enableEnvironmentFiltering, entitledProductIds,
                consumer.getOwner().isUsingSimpleContentAccess());

            filteredContent = extensionUtil.filterContentByContentArch(filteredContent,
                consumer, prod);

            // Keep track of the number of content sets that are being added.
            contentCounter += filteredContent.size();

            log.debug("Adding X509 extensions for content: {}", filteredContent);
            result.addAll(extensionUtil.contentExtensions(filteredContent,
                promotedContent, consumer, skuProd));
        }

        // For V1 certificates we're going to error out if we exceed a limit which is
        // likely going to generate a certificate too large for the CDN, and return an
        // informative error message to the user.
        if (contentCounter > X509ExtensionUtil.V1_CONTENT_LIMIT) {
            String cause = i18n.tr("Too many content sets for certificate {0}. A newer " +
                "client may be available to address this problem. " +
                "See knowledge database https://access.redhat.com/knowledge/node/129003 for more " +
                "information.", pool.getProductName());
            throw new CertificateSizeException(cause);
        }

        result.addAll(extensionUtil.subscriptionExtensions(pool));

        result.addAll(extensionUtil.entitlementExtensions(quantity));
        result.addAll(extensionUtil.consumerExtensions(consumer));

        if (log.isDebugEnabled()) {
            for (X509Extension eWrapper : result) {
                log.debug("Extension {} with value {}", eWrapper.oid(), eWrapper.value());
            }
        }
        return result;
    }

    private Set<X509Extension> prepareV3Extensions(Pool pool) {
        Set<X509Extension> result = new HashSet<>(v3extensionUtil.getExtensions());
        result.add(new X509StringExtension(OID.EntitlementType.namespace(), "Basic"));

        String namespace = pool.getProductNamespace();
        if (namespace != null && !namespace.isBlank()) {
            result.add(new X509StringExtension(OID.EntitlementNamespace.namespace(), namespace));
        }

        return result;
    }

    private String createPayloadAndSignature(Consumer consumer,
        List<org.candlepin.model.dto.Product> productModels, Pool pool, Entitlement ent, String pem) {
        log.debug("Generating v3 entitlement data");
        byte[] payloadBytes = this.payloadGenerator.generate(
            productModels, consumer.getUuid(), pool, ent.getQuantity());

        String payload = "-----BEGIN ENTITLEMENT DATA-----\n";
        payload += Util.toBase64(payloadBytes);
        payload += "-----END ENTITLEMENT DATA-----\n";

        byte[] bytes = this.signer.sign(new ByteArrayInputStream(payloadBytes));
        String signature = "-----BEGIN RSA SIGNATURE-----\n";
        signature += Util.toBase64(bytes);
        signature += "-----END RSA SIGNATURE-----\n";

        pem += payload + signature;
        return pem;
    }

    private Map<String, CertificateSerial> createSerials(Map<String, PoolQuantity> poolQuantities) {
        Map<String, CertificateSerial> serialMap = new HashMap<>();
        for (Entry<String, PoolQuantity> entry : poolQuantities.entrySet()) {
            serialMap.put(entry.getKey(), new CertificateSerial(entry.getValue().getPool().getEndDate()));
        }

        // Serials need to be saved to get generated ID.
        log.debug("Persisting new certificate serials");
        this.serialCurator.saveOrUpdateAll(serialMap.values(), false, false);

        return serialMap;
    }

}
