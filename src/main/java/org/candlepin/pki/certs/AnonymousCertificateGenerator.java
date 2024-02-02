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
import org.candlepin.model.dto.Content;
import org.candlepin.pki.CertificateCreationException;
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
import org.candlepin.util.Arch;
import org.candlepin.util.Util;
import org.candlepin.util.X509V3ExtensionUtil;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.KeyPair;
import java.security.cert.X509Certificate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import javax.inject.Inject;
import javax.inject.Provider;


/**
 * The ContentAccessManager provides management operations for organization and consumer level
 * content access modes.
 */
public class AnonymousCertificateGenerator {
    private static final Logger log = LoggerFactory.getLogger(AnonymousCertificateGenerator.class);
    private static final String BASIC_ENTITLEMENT_TYPE = "basic";

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
    }

    public AnonymousContentAccessCertificate createCertificate(AnonymousCloudConsumer consumer) {
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

            payload = createAnonPayloadAndSignature(products);
            List<ContentInfo> contentInfo = getContentInfo(products);
            content = convertContentInfoToContentDto(contentInfo);

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

    private String createAnonPayloadAndSignature(Collection<ProductInfo> prodInfo) {
        List<ContentInfo> contentInfo = getContentInfo(prodInfo);
        List<org.candlepin.model.Content> contents = convertContentInfoToContent(contentInfo);
        Map<org.candlepin.model.Content, Boolean> activeContent = new HashMap<>();
        contents.forEach(content -> activeContent.put(content, true));
        PromotedContent promotedContent = new PromotedContent(ContentPathBuilder.from(null, null));
        byte[] data = createContentAccessDataPayload(null, activeContent, promotedContent);

        return createPayloadAndSignature(data);
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

    // todo Unify payload creation
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
        Map<org.candlepin.model.Content, Boolean> ownerContent, PromotedContent promotedContent) {

        String consumerUuid = consumer != null ? consumer.getUuid() : null;
        log.info("Generating SCA payload for consumer \"{}\"...", consumerUuid);

        Product engProduct = new Product()
            .setId("content_access")
            .setName(" Content Access");

        ownerContent.forEach(engProduct::addContent);

        Product skuProduct = new Product()
            .setId("content_access")
            .setName("Content Access");

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
     * Retrieves all of the {@link ContentInfo} from the provided {@link ProductInfo}
     *
     * @param prodInfo
     *  the product info that contains content info
     *
     * @return all of the content info from the provided product info
     */
    private List<ContentInfo> getContentInfo(Collection<ProductInfo> prodInfo) {
        return prodInfo.stream()
            .filter(Objects::nonNull)
            .map(ProductInfo::getProductContent)
            .flatMap(Collection::stream)
            .filter(Objects::nonNull)
            .map(ProductContentInfo::getContent)
            .toList();
    }

    /**
     * Converts {@link ContentInfo} to {@link org.candlepin.model.Content}.
     *
     * @param contentInfo
     *  the content to convert
     *
     * @return the converted {@link org.candlepin.model.Content} objects
     */
    private List<org.candlepin.model.Content> convertContentInfoToContent(
        Collection<ContentInfo> contentInfo) {
        return contentInfo.stream()
            .filter(Objects::nonNull)
            .map(content -> {
                org.candlepin.model.Content converted = new org.candlepin.model.Content();
                converted.setId(content.getId());
                converted.setName(content.getName());
                converted.setType(content.getType());
                converted.setLabel(content.getLabel());
                converted.setVendor(content.getVendor());
                converted.setGpgUrl(content.getGpgUrl());
                converted.setMetadataExpiration(content.getMetadataExpiration());
                converted.setRequiredTags(content.getRequiredTags());
                converted.setArches(content.getArches());
                converted.setContentUrl(content.getContentUrl());
                converted.setReleaseVersion(content.getReleaseVersion());

                return converted;
            })
            .toList();
    }


    /**
     * Converts {@link ContentInfo} into {@link Content}
     *
     * @param contentInfo
     *  the content to convert
     *
     * @return the converted {@link ContentInfo} objects
     */
    private List<Content> convertContentInfoToContentDto(Collection<ContentInfo> contentInfo) {
        return contentInfo.stream()
            .filter(Objects::nonNull)
            .map(content -> {
                Content converted = new Content();
                converted.setId(content.getId());
                converted.setName(content.getName());
                converted.setType(content.getType());
                converted.setLabel(content.getLabel());
                converted.setVendor(content.getVendor());
                converted.setGpgUrl(content.getGpgUrl());
                converted.setMetadataExpiration(content.getMetadataExpiration());
                converted.setRequiredTags(Util.toList(content.getRequiredTags()));
                converted.setArches(new ArrayList<>(Arch.parseArches(content.getArches())));
                converted.setPath(content.getContentUrl());

                return converted;
            })
            .toList();
    }

}
