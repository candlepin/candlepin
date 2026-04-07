/*
 * Copyright (c) 2009 - 2026 Red Hat, Inc.
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

import org.candlepin.cache.AnonymousCertContent;
import org.candlepin.config.ConfigProperties;
import org.candlepin.config.Configuration;
import org.candlepin.config.ConfigurationException;
import org.candlepin.controller.util.ContentPathBuilder;
import org.candlepin.controller.util.PromotedContent;
import org.candlepin.model.AnonymousCloudConsumer;
import org.candlepin.model.AnonymousCloudConsumerCurator;
import org.candlepin.model.AnonymousContentAccessCertificate;
import org.candlepin.model.AnonymousContentAccessCertificateCurator;
import org.candlepin.model.CertificateSerial;
import org.candlepin.model.CertificateSerialCurator;
import org.candlepin.model.Pool;
import org.candlepin.model.Product;
import org.candlepin.model.ProductContent;
import org.candlepin.model.dto.Content;
import org.candlepin.pki.CryptoCapabilitiesException;
import org.candlepin.pki.CryptoManager;
import org.candlepin.pki.DistinguishedName;
import org.candlepin.pki.OID;
import org.candlepin.pki.PemEncoder;
import org.candlepin.pki.Scheme;
import org.candlepin.pki.X509Extension;
import org.candlepin.service.ProductServiceAdapter;
import org.candlepin.service.model.ContentInfo;
import org.candlepin.service.model.ProductContentInfo;
import org.candlepin.service.model.ProductInfo;
import org.candlepin.util.Util;
import org.candlepin.util.X509V3ExtensionUtil;
import org.candlepin.util.function.CheckedFunction;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.security.KeyException;
import java.security.KeyPair;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import javax.inject.Inject;
import javax.inject.Singleton;


/**
 * The class responsible for creation of anonymous content access certificates.
 */
@Singleton
public class AnonymousCertificateGenerator {
    private static final Logger log = LoggerFactory.getLogger(AnonymousCertificateGenerator.class);
    private static final String BASIC_ENTITLEMENT_TYPE = "basic";

    /**
     * The key that will be used for caching content access payloads.
     *
     * @param signatureAlgorithm
     *  the algorithm used to generate the payload this cache key will represent
     *
     * @param productIds
     *  the IDs of the products in the payload this cache key will represent; duplicates and null values in
     *  the collection will be silently discarded
     */
    public static record CacheKey(String signatureAlgorithm, Collection<String> productIds) {
        public CacheKey {
            if (signatureAlgorithm == null || signatureAlgorithm.isBlank()) {
                throw new IllegalArgumentException("signatureAlgorithm is null or empty");
            }

            if (productIds == null) {
                throw new IllegalArgumentException("productIds is null");
            }

            productIds = productIds.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toUnmodifiableSet());
        }
    }

    // TODO: reorder these
    private final Configuration config;
    private final CertificateSerialCurator serialCurator;
    private final X509V3ExtensionUtil v3ExtensionUtil;
    private final EntitlementPayloadGenerator payloadGenerator;
    private final AnonymousCloudConsumerCurator anonCloudConsumerCurator;
    private final AnonymousContentAccessCertificateCurator anonContentAccessCertCurator;
    private final ProductServiceAdapter prodAdapter;
    private final PemEncoder pemEncoder;
    private final CryptoManager cryptoManager;

    private final int certDuration;
    private final boolean standalone;

    private final Cache<CacheKey, AnonymousCertContent> payloadCache;

    @Inject
    public AnonymousCertificateGenerator(
        // TODO: reorder these
        Configuration config,
        X509V3ExtensionUtil v3ExtensionUtil,
        EntitlementPayloadGenerator payloadGenerator,
        CertificateSerialCurator serialCurator,
        AnonymousCloudConsumerCurator anonCloudConsumerCurator,
        AnonymousContentAccessCertificateCurator anonContentAccessCertCurator,
        ProductServiceAdapter prodAdapter,
        PemEncoder pemEncoder,
        CryptoManager cryptoManager) {

        // TODO: reorder these
        this.serialCurator = Objects.requireNonNull(serialCurator);
        this.payloadGenerator = Objects.requireNonNull(payloadGenerator);
        this.v3ExtensionUtil = Objects.requireNonNull(v3ExtensionUtil);
        this.anonCloudConsumerCurator = Objects.requireNonNull(anonCloudConsumerCurator);
        this.anonContentAccessCertCurator = Objects.requireNonNull(anonContentAccessCertCurator);
        this.prodAdapter = Objects.requireNonNull(prodAdapter);
        this.pemEncoder = Objects.requireNonNull(pemEncoder);
        this.config = Objects.requireNonNull(config);
        this.cryptoManager = Objects.requireNonNull(cryptoManager);

        try {
            this.certDuration = this.config.getInt(ConfigProperties.ANON_CERT_DURATION);
            if (this.certDuration <= 0) {
                throw new ConfigurationException("Anonymous certificate duration config is less than 1 day");
            }

            if (this.certDuration > ConfigProperties.CERT_MAX_DURATION) {
                String msg = String.format("Anonymous certificate duration config exceeds %d days",
                    ConfigProperties.CERT_MAX_DURATION);
                throw new ConfigurationException(msg);
            }
        }
        catch (NumberFormatException e) {
            throw new ConfigurationException("Invalid value for anonymous certificate duration", e);
        }

        this.standalone = this.config.getBoolean(ConfigProperties.STANDALONE);

        // Prime the CA cache
        this.payloadCache = this.initContentAccessPayloadCache(config);
    }

    /**
     * Creates a new Cache instance configured with values from the given configuration.
     *
     * @param config
     *  the config object from which to get values to configure a cache instance
     *
     * @throws ConfigurationException
     *  if the required configuration values are missing or out of range
     *
     * @return
     *  a new cache instance for storing content access payloads
     */
    private Cache<CacheKey, AnonymousCertContent> initContentAccessPayloadCache(Configuration config) {
        long expirationDuration = config.getLong(ConfigProperties.CACHE_ANON_CERT_CONTENT_TTL);
        if (expirationDuration <= 0) {
            String msg = ConfigProperties.CACHE_ANON_CERT_CONTENT_TTL + " value must be larger than 0";
            throw new ConfigurationException(msg);
        }

        long maxEntries = config.getLong(ConfigProperties.CACHE_ANON_CERT_CONTENT_MAX_ENTRIES);
        if (maxEntries < 0) {
            String msg = ConfigProperties.CACHE_ANON_CERT_CONTENT_MAX_ENTRIES +
                " must be larger than or equal to 0";
            throw new ConfigurationException(msg);
        }

        return Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofMillis(expirationDuration))
            .maximumSize(maxEntries)
            .build();
    }

    /**
     * Checks if an anonymous content access certificate is valid. If the given certificate is null or has
     * expired, this method returns false.
     *
     * @param cert
     *  the certificate to check
     *
     * @return
     *  true if the certificate is non-null and has not yet expired; false otherwise
     */
    private boolean isCertificateValid(AnonymousContentAccessCertificate cert) {
        return Optional.ofNullable(cert)
            .map(AnonymousContentAccessCertificate::getSerial)
            .map(CertificateSerial::getExpiration)
            .map(exp -> exp.after(new Date()))
            .orElse(false);
    }

    /**
     * Builds and signs a new content access payload data blob, consisting of the payload and its signature
     * in PEM format. This method will always build a new payload, and will never pull from cache.
     *
     * @param scheme
     *  the scheme to use to sign the payload
     *
     * @param consumer
     *  the consumer for which to build the payload
     *
     * @param activeContent
     *  the active content to include in the payload
     *
     * @return
     *  a new content access payload data blob
     */
    private String buildAnonContentAccessPayload(Scheme scheme, AnonymousCloudConsumer consumer,
        Map<String, ProductContent> activeContent) {

        PromotedContent promotedContent = new PromotedContent(ContentPathBuilder.from(null, null));

        log.info("Generating anonymous content access payload for consumer: {}", consumer.getUuid());

        Date now = new Date();

        Product engProduct = new Product()
            .setId("anonymous_cloud_content_access")
            .setName("Anonymous Cloud Content Access")
            .setProductContent(activeContent.values());

        Product skuProduct = new Product()
            .setId("anonymous_cloud_content_access")
            .setName("Anonymous Cloud Content Access");

        Pool pool = new Pool()
            .setProduct(skuProduct)
            .setStartDate(now)
            .setEndDate(now); // this should probably be now + 1yr or some such, no?

        Set<String> entitledProductIds = Set.of(skuProduct.getId());

        org.candlepin.model.dto.Product container = this.v3ExtensionUtil.mapProduct(engProduct, skuProduct,
            promotedContent, null, pool, entitledProductIds);

        byte[] payload = this.payloadGenerator.generate(List.of(container), consumer.getUuid(), pool, null);
        byte[] signature = this.cryptoManager.getSigner(scheme)
            .sign(payload);

        return new StringBuilder("-----BEGIN ENTITLEMENT DATA-----\n")
            .append(Util.toBase64(payload))
            .append("-----END ENTITLEMENT DATA-----\n")
            .append("-----BEGIN SIGNATURE-----\n")
            .append(Util.toBase64(signature))
            .append("-----END SIGNATURE-----\n")
            .toString();
    }

    /**
     * Fetches a content access payload from the payload cache, generating a new payload as necessary.
     *
     * @param scheme
     *  the scheme to use for signing the payload
     *
     * @param consumer
     *  the consumer for which to generate the payload
     *
     * @throws CertificateException
     *  if an exception occurs while attempting to fetch data necessary to create the payload
     *
     * @return
     *  an AnonymousCertContent instance containing the content access payload
     */
    private AnonymousCertContent getContentAccessPayload(Scheme scheme, AnonymousCloudConsumer consumer)
        throws CertificateException {

        CheckedFunction<CacheKey, AnonymousCertContent, CertificateException> payloadBuilder = key -> {
            log.debug("Retrieving anonymous content access certificate content from product adapter");

            List<ProductInfo> products = this.prodAdapter.getChildrenByProductIds(key.productIds());
            if (products == null || products.isEmpty()) {
                log.error("Unable to retrieve products for anonymous consumer: {}", consumer.getUuid());
                throw new CertificateException("Unable to retrieve products for anonymous consumer: " +
                    consumer.getUuid());
            }

            Map<String, ProductContent> activeContent = this.translateProductInfo(products);

            String payload = this.buildAnonContentAccessPayload(scheme, consumer, activeContent);
            List<Content> content = this.convertProductContentToContentDto(activeContent.values());

            return new AnonymousCertContent(payload, content);
        };

        CacheKey cacheKey = new CacheKey(scheme.signatureAlgorithm(), consumer.getProductIds());
        return this.payloadCache.get(cacheKey, CheckedFunction.rethrow(payloadBuilder));
    }

    private CertificateSerial createCertificateSerial(OffsetDateTime end) {
        CertificateSerial serial = new CertificateSerial(Date.from(end.toInstant()));

        // We need the sequence generated id before we create the Certificate, otherwise we could have used
        // cascading create
        return this.serialCurator.create(serial, false);
    }

    private Set<X509Extension> buildX509Extensions(List<Content> content) throws CertificateException {
        try {
            Set<X509Extension> extensions = new HashSet<>();

            // Add the entitlement type
            extensions.add(new X509StringExtension(OID.EntitlementType.namespace(), BASIC_ENTITLEMENT_TYPE));

            // Add the general v3 entitlement extensions
            extensions.addAll(this.v3ExtensionUtil.getExtensions());

            // Add the product extensions
            org.candlepin.model.dto.Product container = new org.candlepin.model.dto.Product();
            container.setContent(content);

            extensions.addAll(this.v3ExtensionUtil.getByteExtensions(List.of(container)));

            return extensions;
        }
        catch (IOException e) {
            throw new CertificateException("Unexpected exception occurred while building X509 extensions", e);
        }
    }

    /**
     * Creates a new X509 certificate, wrapped in a new AnonymousContentAccessCertificate container instance.
     *
     * @param scheme
     *  the cryptographic scheme to use for generating the crypto assets needed to generate the certificate
     *
     * @param consumer
     *  the consumer for which to generate the certificate
     *
     * @param payload
     *  the content access payload to include with the certificate
     *
     * @throws CertificateException
     *  if an unexpected exception occurs while generating the certificate container, or the x509 certificate
     *
     * @return
     *  a new, fully populated AnonymousContentAccessCertificate instance
     */
    private AnonymousContentAccessCertificate createCertificate(Scheme scheme,
        AnonymousCloudConsumer consumer, AnonymousCertContent payload)
        throws CertificateException {

        try {
            log.info("Generating X509 certificate for anonymous consumer: {}", consumer.getUuid());

            DistinguishedName dn = new DistinguishedName(consumer.getUuid(), (String) null);

            OffsetDateTime start = OffsetDateTime.now().minusHours(1L);
            OffsetDateTime end = start.plusDays(this.certDuration);

            CertificateSerial serial = this.createCertificateSerial(end);

            KeyPair keyPair = this.cryptoManager.getKeyPairGenerator(scheme)
                .generateKeyPair();

            Set<X509Extension> extensions = this.buildX509Extensions(payload.content());

            X509Certificate x509Cert = this.cryptoManager.getCertificateBuilder(scheme)
                .withDN(dn)
                .withSerial(serial.getSerial())
                .withValidity(start.toInstant(), end.toInstant())
                .withKeyPair(keyPair)
                .withExtensions(extensions)
                .build();

            return new AnonymousContentAccessCertificate()
                .setSerial(serial)
                .setKeyAsBytes(this.pemEncoder.encodeAsBytes(keyPair.getPrivate()))
                .setCert(this.pemEncoder.encodeAsString(x509Cert) + payload.contentAccessDataPayload());
        }
        catch (KeyException e) {
            throw new CertificateException("Unexpected exception occurred while generating key pair", e);
        }
    }

    /**
     * Translates all of the {@link ContentInfo} and their enabled (true/false) status from the provided
     * {@link ProductInfo} into a Map of content IDs to ProductContent (which includes the enabled/disabled
     * value). In case more than one of the same content, but different enabled values are provided, the
     * enabled=true value is kept.
     * IMPORTANT: The rationale for this method is that {@link ContentInfo}, {@link ProductContentInfo} and
     * {@link ProductInfo} are interfaces, without guaranteed hashCode/equals methods that compare based on
     * content ID. Rather, the known implementations of those use field-by-field comparison, which we want
     * to avoid, in case we ever need to deal with different content objects but with the same content ID.
     *
     * @param prodInfo
     *  the product info that contains content info
     *
     * @return a map that contains the unique content IDs mapped to a ProductContent instance (which embeds
     * the enabled/disabled value in it).
     */
    private Map<String, ProductContent> translateProductInfo(Collection<ProductInfo> prodInfo) {
        Function<ProductContent, String> cidFetcher = (pcinfo) -> pcinfo.getContent().getId();

        return prodInfo.stream()
            .filter(Objects::nonNull)
            .map(ProductInfo::getProductContent)
            .flatMap(Collection::stream)
            .filter(Objects::nonNull)
            .map(this::translateProductContentInfo)
            .collect(Collectors.toMap(cidFetcher, Function.identity(), (v1, v2) ->
                new ProductContent(v2.getContent(), v1.isEnabled() || v2.isEnabled())));
    }

    private ProductContent translateProductContentInfo(ProductContentInfo pcinfo) {
        ContentInfo cinfo = pcinfo.getContent();

        org.candlepin.model.Content converted = new org.candlepin.model.Content(cinfo.getId())
            .setName(cinfo.getName())
            .setType(cinfo.getType())
            .setLabel(cinfo.getLabel())
            .setVendor(cinfo.getVendor())
            .setGpgUrl(cinfo.getGpgUrl())
            .setMetadataExpiration(cinfo.getMetadataExpiration())
            .setRequiredTags(cinfo.getRequiredTags())
            .setArches(cinfo.getArches())
            .setContentUrl(cinfo.getContentUrl());

        return new ProductContent(converted, pcinfo.isEnabled());
    }

    /**
     * Translates a collection of {@link ProductContent} instances into a list of {@link Content} where the
     * only populated field is the path (contentUrl), for the purpose of generating the X509 part of the
     * cert ('Authorized content Urls' is the only content-related information in that part of the cert).
     *
     * @param productContentSet A collection of ProductContent instances
     *
     * @return A list of Content dtos with only the path populated
     */
    public List<Content> convertProductContentToContentDto(Collection<ProductContent> productContentSet) {
        return productContentSet.stream()
            .map(ProductContent::getContent)
            .map(org.candlepin.model.Content::getContentUrl)
            .map(url -> new Content().setPath(url))
            .toList();
    }

    /**
     * Creates a new certificate. Certificate content and payload is cached and reused.
     *
     * @param consumer An anonymous consumer for which to create a certificate.
     * @return Anonymous content access certificate
     */
    private AnonymousContentAccessCertificate buildContentAccessCertificate(AnonymousCloudConsumer consumer)
        throws CertificateException, CryptoCapabilitiesException, KeyException {

        // Fetch the scheme we'll be using for this consumer
        Scheme scheme = this.cryptoManager.getCryptoScheme(consumer);

        // Fetch or build payload
        AnonymousCertContent payload = this.getContentAccessPayload(scheme, consumer);

        // Create X509 certificate + CP cert container
        AnonymousContentAccessCertificate certificate = this.createCertificate(scheme, consumer, payload);

        certificate = this.anonContentAccessCertCurator.create(certificate);
        consumer.setContentAccessCert(certificate);

        return certificate;
    }

    /**
     * Retrieves an existing {@link AnonymousContentAccessCertificate} for the {@link AnonymousCloudConsumer}
     * or creates a new certificate if the existing certificate is expired or does not exist.
     *
     * @param consumer
     *  the anonymous consumer to retrieve or create an {@link AnonymousContentAccessCertificate} for
     *
     * @throws IllegalArgumentException
     *  if the provided anonymous cloud consumer is null
     *
     * @throws CryptoCapabilitiesException
     *  if the consumer specifies cryptographic capabilities which do not support any known, configured
     *  cryptographic scheme
     *
     * @throws CertificateException
     *  if an unexpected exception occurs while generating the certificate
     *
     * @return
     *  the retrieved or generated certificate
     */
    public AnonymousContentAccessCertificate generate(AnonymousCloudConsumer consumer)
        throws CertificateException, CryptoCapabilitiesException {

        if (consumer == null) {
            throw new IllegalArgumentException("anonymous cloud consumer is null");
        }

        if (this.standalone) {
            String msg = "cannot retrieve or create content access certificate in standalone mode";
            throw new CertificateException(msg);
        }

        // Get the current certificate for this consumer. If it exists and is still valid; just return it and
        // nothing more is needed.
        // Impl note: At the time of writing, this is safe as there is no way to modify an anonymous cloud
        // consumer after registration. This means that any crypto capabilities present on the consumer are
        // those that were present at time of registration, and what will be present in the future up until
        // they convert to a "regular" consumer or are deleted entirely.
        AnonymousContentAccessCertificate cert = consumer.getContentAccessCert();
        if (this.isCertificateValid(cert)) {
            log.debug("Returning existing and valid anonymous certificate for consumer: \"{}\"",
                consumer.getUuid());
            return cert;
        }

        try {
            // Otherwise, they either don't have a cert, or it's no longer valid. Generate or regenerate a new
            // one.
            return this.buildContentAccessCertificate(consumer);
        }
        catch (KeyException e) {
            throw new CertificateCreationException("Exception occurred while building certificate", e);
        }
    }

}
