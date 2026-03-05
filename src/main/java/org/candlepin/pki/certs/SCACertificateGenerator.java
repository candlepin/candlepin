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

import org.candlepin.config.ConfigProperties;
import org.candlepin.config.Configuration;
import org.candlepin.model.CertificateSerial;
import org.candlepin.model.CertificateSerialCurator;
import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerCurator;
import org.candlepin.model.ContentAccessCertificateCurator;
import org.candlepin.model.ContentAccessPayload;
import org.candlepin.model.ContentAccessPayloadCurator;
import org.candlepin.model.ContentCurator;
import org.candlepin.model.Environment;
import org.candlepin.model.EnvironmentCurator;
import org.candlepin.model.Owner;
import org.candlepin.model.ProductContent;
import org.candlepin.model.SCACertificate;
import org.candlepin.model.dto.Content;
import org.candlepin.pki.CryptoManager;
import org.candlepin.pki.DistinguishedName;
import org.candlepin.pki.OID;
import org.candlepin.pki.PemEncoder;
import org.candlepin.pki.Scheme;
import org.candlepin.pki.Signer;
import org.candlepin.pki.X509Extension;
import org.candlepin.pki.util.ConsumerKeyPairGenerator;
import org.candlepin.util.Util;
import org.candlepin.util.X509V3ExtensionUtil;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.security.KeyException;
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
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.persistence.PersistenceException;


/**
 * Responsible for creating {@link SCACertificate}s.
 */
@Singleton
public class SCACertificateGenerator {
    private static final Logger log = LoggerFactory.getLogger(SCACertificateGenerator.class);

    private static final String SCA_ENTITLEMENT_TYPE = "OrgLevel";
    private static final long DURATION = 1L; // In years

    private final Configuration configuration;

    private final CryptoManager cryptoManager;
    private final PemEncoder pemEncoder;
    private final ConsumerKeyPairGenerator keyPairGenerator;
    private final X509V3ExtensionUtil v3ExtensionUtil;
    private final V3CapabilityCheck v3CapabilityCheck;
    private final ContentAccessPayloadFactory contentAccessPayloadFactory;

    private final CertificateSerialCurator serialCurator;
    private final ContentCurator contentCurator;
    private final ContentAccessCertificateCurator contentAccessCertificateCurator;
    private final ContentAccessPayloadCurator contentAccessPayloadCurator;
    private final EnvironmentCurator environmentCurator;

    private final int x509CertExpirationThreshold;

    @Inject
    public SCACertificateGenerator(
        Configuration configuration,
        CryptoManager cryptoManager,
        PemEncoder pemEncoder,
        ConsumerKeyPairGenerator keyPairGenerator,
        X509V3ExtensionUtil v3ExtensionUtil,
        V3CapabilityCheck v3CapabilityCheck,
        ContentAccessCertificateCurator contentAccessCertificateCurator,
        ContentAccessPayloadCurator contentAccessPayloadCurator,
        CertificateSerialCurator serialCurator,
        ContentCurator contentCurator,
        ConsumerCurator consumerCurator,
        EnvironmentCurator environmentCurator,
        ContentAccessPayloadFactory contentAccessPayloadFactory) {

        this.configuration = Objects.requireNonNull(configuration);

        this.cryptoManager = Objects.requireNonNull(cryptoManager);
        this.pemEncoder = Objects.requireNonNull(pemEncoder);
        this.keyPairGenerator = Objects.requireNonNull(keyPairGenerator);
        this.v3ExtensionUtil = Objects.requireNonNull(v3ExtensionUtil);
        this.v3CapabilityCheck = Objects.requireNonNull(v3CapabilityCheck);
        this.contentAccessPayloadFactory = Objects.requireNonNull(contentAccessPayloadFactory);

        this.contentAccessCertificateCurator = Objects.requireNonNull(contentAccessCertificateCurator);
        this.contentAccessPayloadCurator = Objects.requireNonNull(contentAccessPayloadCurator);
        this.serialCurator = Objects.requireNonNull(serialCurator);
        this.contentCurator = Objects.requireNonNull(contentCurator);
        this.environmentCurator = Objects.requireNonNull(environmentCurator);

        this.x509CertExpirationThreshold = this.configuration
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

        if (!this.v3CapabilityCheck.isCertV3Capable(consumer)) {
            log.debug("Consumer is not v3 cert capable");
            return null;
        }

        // Impl note:
        // These need to be ordered according to priority! At the time of writing, getConsumerEnvironments
        // does this, but if that ever changes, we absolutely need that sorting here.
        List<Environment> environments = this.environmentCurator.getConsumerEnvironments(consumer);

        return this.getContentAccessPayload(owner, consumer, environments);
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

        if (!this.v3CapabilityCheck.isCertV3Capable(consumer)) {
            log.debug("Consumer is not v3 cert capable");
            return null;
        }

        List<Environment> environments = this.environmentCurator.getConsumerEnvironments(consumer);

        try {
            return this.getCertificate(owner, consumer, environments);
        }
        catch (KeyException e) {
            throw new CertificateCreationException("Exception occurred while building certificate", e);
        }
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

        if (!this.v3CapabilityCheck.isCertV3Capable(consumer)) {
            log.debug("Consumer is not v3 cert capable");
            return null;
        }

        // Impl note:
        // These need to be ordered according to priority! At the time of writing, getConsumerEnvironments
        // does this, but if that ever changes, we absolutely need that sorting here.
        List<Environment> environments = this.environmentCurator.getConsumerEnvironments(consumer);

        try {
            SCACertificate cert = this.getCertificate(owner, consumer, environments);
            ContentAccessPayload payload = this.getContentAccessPayload(owner, consumer, environments);

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
        catch (KeyException e) {
            throw new CertificateCreationException("Exception occurred while building certificate", e);
        }
    }

    private SCACertificate getCertificate(Owner owner, Consumer consumer, List<Environment> environments)
        throws KeyException {

        SCACertificate scaCertificate = consumer.getContentAccessCert();
        if (scaCertificate != null && !this.isExpired(scaCertificate.getSerial())) {
            return scaCertificate;
        }

        log.info("Generating new SCA x509 certificate for consumer: \"{}\"", consumer.getUuid());

        if (scaCertificate != null) {
            CertificateSerial serial = scaCertificate.getSerial();
            if (serial != null) {
                this.serialCurator.revokeById(serial.getId());
            }
        }

        OffsetDateTime start = OffsetDateTime.now().minusHours(1L);
        OffsetDateTime end = start.plusYears(DURATION);

        CertificateSerial serial = this.createSerial(end);

        Scheme scheme = this.getScheme(consumer);
        KeyPair keyPair = this.keyPairGenerator.getConsumerKeyPair(consumer);
        org.candlepin.model.dto.Product container = this.createProductContainer(owner, environments);
        X509Certificate x509Cert = this.createX509Cert(consumer.getUuid(), owner, serial, keyPair,
            container, scheme, start, end);

        scaCertificate = new SCACertificate();
        scaCertificate.setConsumer(consumer);
        scaCertificate.setKeyAsBytes(this.pemEncoder.encodeAsBytes(keyPair.getPrivate()));
        scaCertificate.setSerial(serial);
        scaCertificate.setCert(this.pemEncoder.encodeAsString(x509Cert));

        scaCertificate = this.contentAccessCertificateCurator.create(scaCertificate);

        consumer.setContentAccessCert(scaCertificate);

        return scaCertificate;
    }

    private boolean isExpired(CertificateSerial serial) {
        Date regenerationCutOff = Date.from(Instant.now()
            .plus(Long.valueOf(this.x509CertExpirationThreshold), ChronoUnit.DAYS));

        return serial == null ||
            serial.getExpiration() == null ||
            regenerationCutOff.after(serial.getExpiration());
    }

    private ContentAccessPayload getContentAccessPayload(Owner owner, Consumer consumer,
        List<Environment> environments) throws ConcurrentContentPayloadCreationException {

        String payloadKey = new ContentAccessPayloadKeyBuilder(consumer)
            .setEnvironments(environments)
            .build();

        ContentAccessPayload payload = this.contentAccessPayloadCurator
            .getContentAccessPayload(owner.getId(), payloadKey);

        if (payload != null && !this.isPayloadExpired(payload, owner, environments)) {
            return payload;
        }

        log.info("Building content access payload for: {}, {}", owner.getKey(), payloadKey);

        List<ProductContent> content = this.contentCurator
            .getActiveContentByOwner(owner.getId());

        Scheme scheme = this.getScheme(consumer);
        Signer signer = this.cryptoManager.getSigner(scheme);

        Date timestamp = new Date();

        String payloadData = contentAccessPayloadFactory.builder()
            .setOwner(owner)
            .setConsumer(consumer)
            .setEnvironments(environments)
            .setContent(content)
            .setSigner(signer)
            .build();

        if (payload == null) {
            // Create and persist new payload
            payload = new ContentAccessPayload()
                .setOwner(owner)
                .setPayloadKey(payloadKey)
                .setTimestamp(timestamp)
                .setPayload(payloadData);

            try {
                payload = this.contentAccessPayloadCurator.create(payload);
            }
            catch (PersistenceException e) {
                throw new ConcurrentContentPayloadCreationException(e);
            }
        }
        else {
            // Update existing record
            payload.setTimestamp(timestamp)
                .setPayload(payloadData);
        }

        return payload;
    }

    private Scheme getScheme(Consumer consumer) {
        return this.cryptoManager.getCryptoScheme(consumer)
            .orElse(this.cryptoManager.getDefaultCryptoScheme());
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
        return this.serialCurator.create(serial);
    }

    private X509Certificate createX509Cert(String consumerUuid, Owner owner, CertificateSerial serial,
        KeyPair keyPair, org.candlepin.model.dto.Product product, Scheme scheme, OffsetDateTime start,
        OffsetDateTime end) {

        log.info("Generating X509 certificate for consumer \"{}\"...", consumerUuid);
        Set<X509Extension> extensions = new HashSet<>(this.prepareV3Extensions(SCA_ENTITLEMENT_TYPE));
        extensions.addAll(this.prepareV3ByteExtensions(product));
        DistinguishedName dn = new DistinguishedName(consumerUuid, owner);

        return this.cryptoManager.getCertificateBuilder(scheme)
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

    private Set<X509Extension> prepareV3Extensions(String entType) {
        Set<X509Extension> extensions = new HashSet<>(this.v3ExtensionUtil.getExtensions());
        extensions.add(new X509StringExtension(
            OID.EntitlementType.namespace(), entType));
        return extensions;
    }

    private Set<X509Extension> prepareV3ByteExtensions(org.candlepin.model.dto.Product container) {
        List<org.candlepin.model.dto.Product> products = new ArrayList<>();
        products.add(container);
        try {
            return this.v3ExtensionUtil.getByteExtensions(products);
        }
        catch (IOException e) {
            throw new CertificateCreationException("Failed to prepare extensions", e);
        }
    }

}
