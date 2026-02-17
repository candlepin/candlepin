/*
 * Copyright (c) 2009 - 2025 Red Hat, Inc.
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
import org.candlepin.model.ConsumerCurator;
import org.candlepin.model.ContentAccessCertificateCurator;
import org.candlepin.model.ContentAccessPayload;
import org.candlepin.model.ContentAccessPayloadCurator;
import org.candlepin.model.ContentCurator;
import org.candlepin.model.Entitlement;
import org.candlepin.model.Environment;
import org.candlepin.model.EnvironmentCurator;
import org.candlepin.model.Owner;
import org.candlepin.model.Pool;
import org.candlepin.model.Product;
import org.candlepin.model.ProductContent;
import org.candlepin.model.SCACertificate;
import org.candlepin.model.dto.Content;
import org.candlepin.pki.DistinguishedName;
import org.candlepin.pki.KeyPairGenerator;
import org.candlepin.pki.OID;
import org.candlepin.pki.PemEncoder;
import org.candlepin.pki.X509Extension;
import org.candlepin.pki.impl.Signer;
import org.candlepin.util.Util;
import org.candlepin.util.X509V3ExtensionUtil;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.KeyPair;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;
import javax.persistence.PersistenceException;


/**
 * Responsible for creating {@link SCACertificate}s.
 */
@Singleton
public class SCACertificateGenerator {
    private static final Logger log = LoggerFactory.getLogger(SCACertificateGenerator.class);

    private static final String SCA_ENTITLEMENT_TYPE = "OrgLevel";

    private final V3CapabilityCheck v3CapabilityCheck;
    private final CertificateSerialCurator serialCurator;
    private final ContentCurator contentCurator;
    private final ContentAccessCertificateCurator contentAccessCertificateCurator;
    private final ContentAccessPayloadCurator contentAccessPayloadCurator;
    private final X509V3ExtensionUtil v3extensionUtil;
    private final EntitlementPayloadGenerator payloadGenerator;
    private final EnvironmentCurator environmentCurator;
    private final PemEncoder pemEncoder;
    private final KeyPairGenerator keyPairGenerator;
    private final Signer signer;
    private final Provider<X509CertificateBuilder> certificateBuilder;
    private final Configuration configuration;

    private final int x509CertExpirationThreshold;

    @Inject
    public SCACertificateGenerator(
        X509V3ExtensionUtil v3extensionUtil,
        V3CapabilityCheck v3CapabilityCheck,
        EntitlementPayloadGenerator payloadGenerator,
        ContentAccessCertificateCurator contentAccessCertificateCurator,
        ContentAccessPayloadCurator contentAccessPayloadCurator,
        CertificateSerialCurator serialCurator,
        ContentCurator contentCurator,
        ConsumerCurator consumerCurator,
        EnvironmentCurator environmentCurator,
        PemEncoder pemEncoder,
        KeyPairGenerator keyPairGenerator,
        Signer signer,
        Provider<X509CertificateBuilder> certificateBuilder,
        Configuration configuration) {

        this.v3CapabilityCheck = Objects.requireNonNull(v3CapabilityCheck);
        this.contentAccessCertificateCurator = Objects.requireNonNull(contentAccessCertificateCurator);
        this.contentAccessPayloadCurator = Objects.requireNonNull(contentAccessPayloadCurator);
        this.serialCurator = Objects.requireNonNull(serialCurator);
        this.v3extensionUtil = Objects.requireNonNull(v3extensionUtil);
        this.payloadGenerator = Objects.requireNonNull(payloadGenerator);
        this.contentCurator = Objects.requireNonNull(contentCurator);
        this.environmentCurator = Objects.requireNonNull(environmentCurator);
        this.pemEncoder = Objects.requireNonNull(pemEncoder);
        this.keyPairGenerator = Objects.requireNonNull(keyPairGenerator);
        this.signer = Objects.requireNonNull(signer);
        this.certificateBuilder = Objects.requireNonNull(certificateBuilder);
        this.configuration = Objects.requireNonNull(configuration);

        x509CertExpirationThreshold = this.configuration
            .getInt(ConfigProperties.SCA_X509_CERT_EXPIRY_THRESHOLD);
    }

    /**
     * Retrieves a {@link ContentAccessPayload} component of a {@link SCACertificate} for a provided
     * v3 cert capable consumer that is part of an owner using simple content access. A payload will be
     * generated if one does not exist or regenerated if there has been changes in the consumer's content.
     * The consumer's content consists of content from the consumer's {@link Owner} and the consumer's
     * {@link Environment}s.
     *
     * @param consumer
     *  the consumer to retrieve a content access payload for
     *
     * @throws IllegalArgumentException
     *  if the provided consumer is null
     *
     * @throws ConcurrentContentPayloadCreationException
     *  if a concurrent request persists the content payload and causes a database constraint violation
     *
     * @return the content access payload for the consumer, or null if the owner is not in SCA mode or the
     *  consumer is not v3 cert capable
     */
    public ContentAccessPayload getContentPayload(Consumer consumer)
        throws ConcurrentContentPayloadCreationException {

        if (consumer == null) {
            throw new IllegalArgumentException("consumer is null");
        }

        Owner owner = consumer.getOwner();
        if (owner == null || !owner.isUsingSimpleContentAccess()) {
            log.debug("Consumer is not in SCA mode");
            return null;
        }

        if (!v3CapabilityCheck.isCertV3Capable(consumer)) {
            log.debug("Consumer is not v3 cert capable");
            return null;
        }

        // Impl note:
        // These need to be ordered according to priority! At the time of writing, getConsumerEnvironments
        // does this, but if that ever changes, we absolutely need that sorting here.
        List<Environment> environments = environmentCurator.getConsumerEnvironments(consumer);

        return getContentAccessPayload(owner, consumer, environments);
    }

    /**
     * Retrieves the X509 certificate component of a {@link SCACertificate} for a provided v3 cert capable
     * consumer that is part of an owner using simple content access. The X509 certificate will be generated
     * for the consumer if one does not exist or if the existing certificate is expired. A certificate is
     * considered expired if the {@link CertificateSerial}'s expiration date is after the configured X509
     * certificate exiration threshold.
     *
     * @param consumer
     *  the consumer to retrieve a X509 certificate for
     *
     * @throws IllegalArgumentException
     *  if the provided consumer is null
     *
     * @return the X509 certificate for the consumer, or null if the owner is not in SCA mode or the
     *  consumer is not v3 cert capable
     */
    public SCACertificate getX509Certificate(Consumer consumer) {

        if (consumer == null) {
            throw new IllegalArgumentException("consumer is null");
        }

        Owner owner = consumer.getOwner();
        if (owner == null || !owner.isUsingSimpleContentAccess()) {
            log.debug("Consumer is not in SCA mode");
            return null;
        }

        if (!v3CapabilityCheck.isCertV3Capable(consumer)) {
            log.debug("Consumer is not v3 cert capable");
            return null;
        }

        List<Environment> environments = environmentCurator.getConsumerEnvironments(consumer);

        return getCertificate(owner, consumer, environments);
    }

    /**
     * Retrieves a {@link SCACertificate} containing both the X509 certificate and
     * {@link ContentAccessPayload} for a provided v3 cert capable consumer that is part of an owner using
     * simple content access. The SCA certificate will be created if one does not exist or recreated if either
     * the X509 certificate is expired or the content payload is out of date. To retrieve the individual
     * components seperately, see {@link #getContentPayload} and {@link #getX509Certificate}.
     *
     * @param consumer
     *  the consumer to retrieve a SCA certificate for
     *
     * @throws IllegalArgumentException
     *  if the provided consumer is null
     *
     * @throws ConcurrentContentPayloadCreationException
     *  if a concurrent request persists the content payload and causes a database constraint violation
     *
     * @return the SCA certificate for the consumer, or null if the owner is not in SCA mode or the
     *  consumer is not v3 cert capable
     */
    public SCACertificate generate(Consumer consumer) throws ConcurrentContentPayloadCreationException {
        if (consumer == null) {
            throw new IllegalArgumentException("consumer is null");
        }

        Owner owner = consumer.getOwner();
        if (owner == null || !owner.isUsingSimpleContentAccess()) {
            log.debug("Consumer is not in SCA mode");
            return null;
        }

        if (!v3CapabilityCheck.isCertV3Capable(consumer)) {
            log.debug("Consumer is not v3 cert capable");
            return null;
        }

        // Impl note:
        // These need to be ordered according to priority! At the time of writing, getConsumerEnvironments
        // does this, but if that ever changes, we absolutely need that sorting here.
        List<Environment> environments = environmentCurator.getConsumerEnvironments(consumer);

        SCACertificate cert = getCertificate(owner, consumer, environments);
        ContentAccessPayload payload = getContentAccessPayload(owner, consumer, environments);

        Date certUpdated = cert.getUpdated();
        Date payloadUpdated = payload.getTimestamp();

        SCACertificate combined = new SCACertificate();
        combined.setCert(cert.getCert() + payload.getPayload());
        combined.setCreated(cert.getCreated());
        combined.setUpdated(payloadUpdated.after(certUpdated) ? payloadUpdated : certUpdated);
        combined.setId(cert.getId());
        combined.setKey(cert.getKey());
        combined.setSerial(cert.getSerial());

        return combined;
    }

    private SCACertificate getCertificate(Owner owner, Consumer consumer, List<Environment> environments) {
        SCACertificate scaCertificate = consumer.getContentAccessCert();
        if (scaCertificate == null) {
            log.info("Generating new SCA certificate key for consumer: \"{}\"", consumer.getUuid());

            KeyPair keypair = keyPairGenerator.getKeyPair(consumer);

            scaCertificate = new SCACertificate();
            scaCertificate.setConsumer(consumer);
            scaCertificate.setKeyAsBytes(pemEncoder.encodeAsBytes(keypair.getPrivate()));
            scaCertificate.setCert(""); // we'll correct this later

            scaCertificate = contentAccessCertificateCurator.create(scaCertificate);

            consumer.setContentAccessCert(scaCertificate);
        }

        Date regenerationCutoff = Date.from(Instant.now()
            .plus(Long.valueOf(x509CertExpirationThreshold), ChronoUnit.DAYS));

        CertificateSerial serial = scaCertificate.getSerial();
        if (serial == null ||
            serial.getExpiration() == null ||
            regenerationCutoff.after(serial.getExpiration())) {

            log.info("Generating new SCA x509 certificate for consumer: \"{}\"", consumer.getUuid());
            if (serial != null) {
                serialCurator.revokeById(serial.getId());
            }

            OffsetDateTime start = OffsetDateTime.now().minusHours(1L);
            OffsetDateTime end = start.plusYears(1L);

            serial = createSerial(end);
            KeyPair keypair = keyPairGenerator.getKeyPair(consumer);
            org.candlepin.model.dto.Product container = createProductContainer(owner, environments);

            X509Certificate x509Cert = createX509Cert(consumer.getUuid(), owner, serial, keypair,
                container, start, end);

            scaCertificate.setSerial(serial);
            scaCertificate.setCert(pemEncoder.encodeAsString(x509Cert));
        }

        return scaCertificate;
    }

    private ContentAccessPayload getContentAccessPayload(Owner owner, Consumer consumer,
        List<Environment> environments) throws ConcurrentContentPayloadCreationException {

        String payloadKey = new ContentAccessPayloadKeyBuilder(consumer)
            .setEnvironments(environments)
            .build();

        ContentAccessPayload payload = contentAccessPayloadCurator
            .getContentAccessPayload(owner.getId(), payloadKey);

        if (payload == null || isPayloadExpired(payload, owner, environments)) {
            log.info("Building content access payload for: {}, {}", owner.getKey(), payloadKey);

            Date timestamp = new Date();
            String payloadData = buildContentAccessPayload(owner, consumer, environments);
            if (payload == null) {
                payload = new ContentAccessPayload()
                    .setOwner(owner)
                    .setPayloadKey(payloadKey)
                    .setTimestamp(timestamp)
                    .setPayload(payloadData);

                try {
                    contentAccessPayloadCurator.create(payload);
                }
                catch (PersistenceException e) {
                    throw new ConcurrentContentPayloadCreationException(e);
                }
            }
            else {
                payload.setTimestamp(timestamp)
                    .setPayload(payloadData);
            }
        }

        return payload;
    }

    private String buildContentAccessPayload(Owner owner, Consumer consumer, List<Environment> environments) {
        ContentPathBuilder contentPathBuilder = ContentPathBuilder.from(owner, environments);
        PromotedContent promotedContent = new PromotedContent(contentPathBuilder).withAll(environments);

        Function<ProductContent, String> cidFetcher = pcinfo -> pcinfo.getContent().getId();
        Map<String, ProductContent> ownerContent = contentCurator
            .getActiveContentByOwner(owner.getId())
            .stream()
            .collect(Collectors.toMap(cidFetcher, Function.identity(),
                (v1, v2) -> new ProductContent(v2.getContent(), v1.isEnabled() || v2.isEnabled())));

        byte[] payloadBytes = createContentAccessDataPayload(consumer, ownerContent, promotedContent);

        return createPayloadAndSignature(payloadBytes);
    }

    private boolean isPayloadExpired(ContentAccessPayload payload, Owner owner,
        List<Environment> environments) {

        Date payloadTimestamp = payload.getTimestamp();

        Stream<Date> ownerContentUpdate = Stream.of(owner.getLastContentUpdate());
        Stream<Date> envContentUpdates = environments.stream()
            .map(Environment::getLastContentUpdate);

        return Stream.concat(ownerContentUpdate, envContentUpdates)
            .anyMatch(contentUpdate -> contentUpdate.after(payloadTimestamp));
    }

    private org.candlepin.model.dto.Product createProductContainer(Owner owner,
        List<Environment> environments) {

        List<Content> dtoContents = new ArrayList<>();
        for (Environment environment : environments) {
            dtoContents.add(createContent(owner, environment));
        }

        if (dtoContents.isEmpty()) {
            dtoContents.add(createContent(owner, null));
        }

        org.candlepin.model.dto.Product container = new org.candlepin.model.dto.Product();
        container.setContent(dtoContents);

        return container;
    }

    private CertificateSerial createSerial(OffsetDateTime end) {
        CertificateSerial serial = new CertificateSerial(Date.from(end.toInstant()));
        // We need the sequence generated id before we create the Certificate,
        // otherwise we could have used cascading create
        return serialCurator.create(serial);
    }

    private X509Certificate createX509Cert(String consumerUuid, Owner owner, CertificateSerial serial,
        KeyPair keyPair, org.candlepin.model.dto.Product product, OffsetDateTime start, OffsetDateTime end) {

        log.info("Generating X509 certificate for consumer \"{}\"...", consumerUuid);
        Set<X509Extension> extensions = new HashSet<>(prepareV3Extensions(SCA_ENTITLEMENT_TYPE));
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

    // FIXME: TODO: Why are we not using the same path-building logic in every cert generator?
    private Content createContent(Owner owner, Environment environment) {
        List<String> components = new ArrayList<>();

        if (owner != null) {
            components.add(owner.getKey());
        }

        if (environment != null) {
            components.add(environment.getName());
        }

        // add more components here as necessary

        String path = components.stream()
            .flatMap(component -> Arrays.stream(component.split("/")))
            .filter(segment -> !segment.isBlank())
            .map(Util::encodeUrl)
            .collect(Collectors.joining("/", "/", ""));

        return new Content()
            .setPath(path);
    }

    private String createPayloadAndSignature(byte[] payloadBytes) {
        String payload = "-----BEGIN ENTITLEMENT DATA-----\n";
        payload += Util.toBase64(payloadBytes);
        payload += "-----END ENTITLEMENT DATA-----\n";

        byte[] bytes = this.signer.sign(new ByteArrayInputStream(payloadBytes));
        String signature = "-----BEGIN MLDSA SIGNATURE-----\n";
        signature += Util.toBase64(bytes);
        signature += "-----END MLDSA SIGNATURE-----\n";
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
            throw new CertificateCreationException("Failed to prepare extensions", e);
        }
    }

    private byte[] createContentAccessDataPayload(Consumer consumer,
        Map<String, ProductContent> activateContent, PromotedContent promotedContent) {

        String consumerUuid = consumer != null ? consumer.getUuid() : null;
        log.info("Generating SCA payload for consumer \"{}\"...", consumerUuid);

        Product engProduct = new Product()
            .setId("content_access")
            .setName(" Content Access")
            .setProductContent(activateContent.values());

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

}
