/*
 * Copyright (c) 2009 - 2024 Red Hat, Inc.
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
import org.candlepin.cache.AnonymousCertContentCache;
import org.candlepin.config.ConfigProperties;
import org.candlepin.config.Configuration;
import org.candlepin.controller.util.ContentPathBuilder;
import org.candlepin.controller.util.PromotedContent;
import org.candlepin.model.AnonymousCloudConsumer;
import org.candlepin.model.AnonymousCloudConsumerCurator;
import org.candlepin.model.AnonymousContentAccessCertificate;
import org.candlepin.model.AnonymousContentAccessCertificateCurator;
import org.candlepin.model.CertificateSerial;
import org.candlepin.model.CertificateSerialCurator;
import org.candlepin.model.Consumer;
import org.candlepin.model.Entitlement;
import org.candlepin.model.Owner;
import org.candlepin.model.Pool;
import org.candlepin.model.Product;
import org.candlepin.model.ProductContent;
import org.candlepin.model.dto.Content;
import org.candlepin.pki.DistinguishedName;
import org.candlepin.pki.KeyPairGenerator;
import org.candlepin.pki.OID;
import org.candlepin.pki.PemEncoder;
import org.candlepin.pki.X509Extension;
import org.candlepin.pki.impl.Signer;
import org.candlepin.service.ProductServiceAdapter;
import org.candlepin.service.model.ContentInfo;
import org.candlepin.service.model.ProductContentInfo;
import org.candlepin.service.model.ProductInfo;
import org.candlepin.util.Util;
import org.candlepin.util.X509V3ExtensionUtil;

import com.google.inject.persist.Transactional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.cert.X509Certificate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;


/**
 * The class responsible for creation of anonymous content access certificates.
 */
@Singleton
public class AnonymousCertificateGenerator {
    private static final Logger log = LoggerFactory.getLogger(AnonymousCertificateGenerator.class);
    private static final String BASIC_ENTITLEMENT_TYPE = "basic";

    private final Configuration config;
    private final CertificateSerialCurator serialCurator;
    private final X509V3ExtensionUtil v3extensionUtil;
    private final EntitlementPayloadGenerator payloadGenerator;
    private final AnonymousCloudConsumerCurator anonCloudConsumerCurator;
    private final AnonymousContentAccessCertificateCurator anonContentAccessCertCurator;
    private final ProductServiceAdapter prodAdapter;
    private final AnonymousCertContentCache contentCache;
    private final PemEncoder pemEncoder;
    private final KeyPairGenerator keyPairGenerator;
    private final Signer signer;
    private final Provider<X509CertificateBuilder> certificateBuilder;

    @Inject
    public AnonymousCertificateGenerator(
        Configuration config,
        X509V3ExtensionUtil v3extensionUtil,
        EntitlementPayloadGenerator payloadGenerator,
        CertificateSerialCurator serialCurator,
        AnonymousCloudConsumerCurator anonCloudConsumerCurator,
        AnonymousContentAccessCertificateCurator anonContentAccessCertCurator,
        ProductServiceAdapter prodAdapter,
        AnonymousCertContentCache contentCache,
        PemEncoder pemEncoder,
        KeyPairGenerator keyPairGenerator,
        Signer signer,
        Provider<X509CertificateBuilder> certificateBuilder) {

        this.serialCurator = Objects.requireNonNull(serialCurator);
        this.payloadGenerator = Objects.requireNonNull(payloadGenerator);
        this.v3extensionUtil = Objects.requireNonNull(v3extensionUtil);
        this.anonCloudConsumerCurator = Objects.requireNonNull(anonCloudConsumerCurator);
        this.anonContentAccessCertCurator = Objects.requireNonNull(anonContentAccessCertCurator);
        this.prodAdapter = Objects.requireNonNull(prodAdapter);
        this.contentCache = Objects.requireNonNull(contentCache);
        this.pemEncoder = Objects.requireNonNull(pemEncoder);
        this.keyPairGenerator = Objects.requireNonNull(keyPairGenerator);
        this.signer = Objects.requireNonNull(signer);
        this.certificateBuilder = Objects.requireNonNull(certificateBuilder);
        this.config = Objects.requireNonNull(config);
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
     * @throws IOException
     *  if there is an i/o problem
     *
     * @throws GeneralSecurityException
     *  if unable to create x509 certificate due to security issue
     *
     * @return
     *  the retrieved or generated certificate
     */
    @Transactional
    public AnonymousContentAccessCertificate generate(AnonymousCloudConsumer consumer) {
        if (this.isStandalone()) {
            String msg = "cannot retrieve or create content access certificate in standalone mode";
            throw new CertificateCreationException(msg);
        }

        if (consumer == null) {
            throw new IllegalArgumentException("anonymous cloud consumer is null");
        }

        AnonymousContentAccessCertificate cert = consumer.getContentAccessCert();
        Date now = new Date();

        boolean isCertificateValid = cert != null && cert.getSerial() != null &&
            cert.getSerial().getExpiration().after(now);
        if (isCertificateValid) {
            log.debug("Returning existing and valid anonymous certificate for consumer: \"{}\"",
                consumer.getUuid());
            return cert;
        }

        return this.createCertificate(consumer);
    }

    private boolean isStandalone() {
        return this.config.getBoolean(ConfigProperties.STANDALONE);
    }

    /**
     * Creates a new certificate. Certificate content and payload is cached and reused.
     *
     * @param consumer An anonymous consumer for which to create a certificate.
     * @return Anonymous content access certificate
     */
    private AnonymousContentAccessCertificate createCertificate(AnonymousCloudConsumer consumer) {
        // Generate a new certificate if one does not exist or the existing certificate is expired.
        // First attempt to retrieve an already built and cached content access payload based on the
        // anonymous cloud consumer's top level product IDs, or retrieve the data through adapters if
        // we hava a cache miss.
        String payload;
        List<Content> content;
        AnonymousCertContent cached = this.contentCache.get(consumer.getProductIds());
        if (cached != null) {
            log.debug("Anonymous content access certificate content retrieved from cache");
            payload = cached.contentAccessDataPayload();
            content = cached.content();
        }
        else {
            log.debug("Retrieving anonymous content access certificate content from product adapter");
            // Get product information from adapters to build the content access certificate
            List<ProductInfo> products = this.prodAdapter.getChildrenByProductIds(consumer.getProductIds());
            if (products == null || products.isEmpty()) {
                String msg = "Unable to retrieve products for anonymous cloud consumer: %s"
                    .formatted(consumer.getUuid());
                throw new CertificateCreationException(msg);
            }

            Map<String, ProductContent> activeContent = translateProductInfo(products);
            payload = createAnonPayloadAndSignature(activeContent);
            content = convertProductContentToContentDto(activeContent.values());

            // Cache the generated content for future requests
            this.contentCache.put(consumer.getProductIds(), new AnonymousCertContent(payload, content));
        }

        return createAnonContentAccessCertificate(consumer, payload, content);
    }

    private AnonymousContentAccessCertificate createAnonContentAccessCertificate(
        AnonymousCloudConsumer consumer, String payloadAndSignature, List<Content> certificateContent) {

        if (consumer == null) {
            throw new IllegalArgumentException("anonymous cloud consumer is null");
        }

        if (payloadAndSignature == null) {
            throw new IllegalArgumentException("content access payload is null");
        }

        if (certificateContent == null || certificateContent.isEmpty()) {
            throw new IllegalArgumentException("certificate content is null or empty");
        }

        log.info("Generating anonymous content access certificate for consumer: \"{}\"",
            consumer.getUuid());

        OffsetDateTime start = OffsetDateTime.now().minusHours(1L);
        OffsetDateTime end = start.plusDays(2L);

        CertificateSerial serial = createSerial(end);
        KeyPair keyPair = this.keyPairGenerator.generateKeyPair();

        org.candlepin.model.dto.Product container = new org.candlepin.model.dto.Product();
        container.setContent(certificateContent);
        X509Certificate x509Cert = createX509Cert(consumer.getUuid(), null,
            serial, keyPair, container, start, end);

        AnonymousContentAccessCertificate caCert = new AnonymousContentAccessCertificate();
        caCert.setSerial(serial);
        caCert.setKeyAsBytes(this.pemEncoder.encodeAsBytes(keyPair.getPrivate()));
        caCert.setCert(this.pemEncoder.encodeAsString(x509Cert) + payloadAndSignature);
        caCert = this.anonContentAccessCertCurator.create(caCert);

        consumer.setContentAccessCert(caCert);
        this.anonCloudConsumerCurator.merge(consumer);

        return caCert;
    }

    private String createAnonPayloadAndSignature(Map<String, ProductContent> productContentMap) {
        PromotedContent promotedContent = new PromotedContent(ContentPathBuilder.from(null, null));
        byte[] data = this.createContentAccessDataPayload(null, productContentMap, promotedContent);
        return this.createPayloadAndSignature(data);
    }

    private CertificateSerial createSerial(OffsetDateTime end) {
        CertificateSerial serial = new CertificateSerial(Date.from(end.toInstant()));
        // We need the sequence generated id before we create the Certificate,
        // otherwise we could have used cascading create
        this.serialCurator.create(serial);
        return serial;
    }

    private X509Certificate createX509Cert(String consumerUuid, Owner owner, CertificateSerial serial,
        KeyPair keyPair, org.candlepin.model.dto.Product product, OffsetDateTime start, OffsetDateTime end) {

        log.info("Generating X509 certificate for consumer \"{}\"...", consumerUuid);
        Set<X509Extension> extensions = new HashSet<>(
            prepareV3Extensions(AnonymousCertificateGenerator.BASIC_ENTITLEMENT_TYPE));
        extensions.addAll(prepareV3ByteExtensions(product));
        DistinguishedName dn = new DistinguishedName(consumerUuid, owner);

        return this.certificateBuilder.get()
            .withDN(dn)
            .withSerial(serial.getSerial())
            .withValidity(start.toInstant(), end.toInstant())
            .withKeyPair(keyPair)
            .withExtensions(extensions)
            .build();
    }

    private String createPayloadAndSignature(byte[] payloadBytes) {
        String payload = "-----BEGIN ENTITLEMENT DATA-----\n";
        payload += Util.toBase64(payloadBytes);
        payload += "-----END ENTITLEMENT DATA-----\n";

        byte[] bytes = this.signer.sign(new ByteArrayInputStream(payloadBytes));
        String signature = "-----BEGIN RSA SIGNATURE-----\n";
        signature += Util.toBase64(bytes);
        signature += "-----END RSA SIGNATURE-----\n";
        return payload + signature;
    }

    private Set<X509Extension> prepareV3Extensions(String entType) {
        Set<X509Extension> extensions = new HashSet<>(v3extensionUtil.getExtensions());
        extensions.add(new X509StringExtension(
            OID.EntitlementType.namespace(), entType));
        return extensions;
    }

    private Set<X509Extension> prepareV3ByteExtensions(org.candlepin.model.dto.Product container) {
        List<org.candlepin.model.dto.Product> products = new ArrayList<>();
        products.add(container);
        try {
            return v3extensionUtil.getByteExtensions(products);
        }
        catch (IOException e) {
            throw new CertificateCreationException("Failed to prepare product extensions!", e);
        }
    }

    private byte[] createContentAccessDataPayload(Consumer consumer,
        Map<String, ProductContent> activeContent, PromotedContent promotedContent) {

        String consumerUuid = consumer != null ? consumer.getUuid() : null;
        log.info("Generating ACCA payload for consumer \"{}\"...", consumerUuid);

        Product engProduct = new Product()
            .setId("anonymous_cloud_content_access")
            .setName("Anonymous Cloud Content Access")
            .setProductContent(activeContent.values());

        Product skuProduct = new Product()
            .setId("anonymous_cloud_content_access")
            .setName("Anonymous Cloud Content Access");

        Pool emptyPool = new Pool()
            .setProduct(skuProduct)
            .setStartDate(new Date())
            .setEndDate(new Date());

        Entitlement emptyEnt = new Entitlement();
        emptyEnt.setPool(emptyPool);
        emptyEnt.setConsumer(consumer);

        Set<String> entitledProductIds = new HashSet<>();
        entitledProductIds.add("content-access");

        org.candlepin.model.dto.Product productModel = v3extensionUtil.mapProduct(engProduct, skuProduct,
            promotedContent, consumer, emptyPool, entitledProductIds);

        List<org.candlepin.model.dto.Product> productModels = new ArrayList<>();
        productModels.add(productModel);

        return this.payloadGenerator.generate(productModels, consumerUuid, emptyPool, null);
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
            .map(productContent -> {
                Content contentContainer = new Content();
                contentContainer.setPath(productContent.getContent().getContentUrl());
                return contentContainer;
            })
            .toList();
    }
}
