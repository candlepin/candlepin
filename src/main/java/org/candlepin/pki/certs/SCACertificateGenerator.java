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

import org.candlepin.controller.util.ContentPathBuilder;
import org.candlepin.controller.util.PromotedContent;
import org.candlepin.model.Certificate;
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
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;


/**
 * Handles creation of the content access certificates.
 */
@Singleton
public class SCACertificateGenerator {
    private static final Logger log = LoggerFactory.getLogger(SCACertificateGenerator.class);
    private static final String SCA_ENTITLEMENT_TYPE = "OrgLevel";

    private static final int SCA_CONTENT_ACCESS_PAYLOAD_KEY_VERSION = 1;

    private static final HexFormat HEX_FORMATTER = HexFormat.of();

    private final V3CapabilityCheck v3CapabilityCheck;
    private final CertificateSerialCurator serialCurator;
    private final ContentCurator contentCurator;
    private final ContentAccessCertificateCurator contentAccessCertificateCurator;
    private final ContentAccessPayloadCurator contentAccessPayloadCurator;
    private final X509V3ExtensionUtil v3extensionUtil;
    private final EntitlementPayloadGenerator payloadGenerator;
    private final ConsumerCurator consumerCurator;
    private final EnvironmentCurator environmentCurator;
    private final PemEncoder pemEncoder;
    private final KeyPairGenerator keyPairGenerator;
    private final Signer signer;
    private final Provider<X509CertificateBuilder> certificateBuilder;

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
        Provider<X509CertificateBuilder> certificateBuilder) {

        this.v3CapabilityCheck = Objects.requireNonNull(v3CapabilityCheck);
        this.contentAccessCertificateCurator = Objects.requireNonNull(contentAccessCertificateCurator);
        this.contentAccessPayloadCurator = Objects.requireNonNull(contentAccessPayloadCurator);
        this.serialCurator = Objects.requireNonNull(serialCurator);
        this.v3extensionUtil = Objects.requireNonNull(v3extensionUtil);
        this.payloadGenerator = Objects.requireNonNull(payloadGenerator);
        this.contentCurator = Objects.requireNonNull(contentCurator);
        this.consumerCurator = Objects.requireNonNull(consumerCurator);
        this.environmentCurator = Objects.requireNonNull(environmentCurator);
        this.pemEncoder = Objects.requireNonNull(pemEncoder);
        this.keyPairGenerator = Objects.requireNonNull(keyPairGenerator);
        this.signer = Objects.requireNonNull(signer);
        this.certificateBuilder = Objects.requireNonNull(certificateBuilder);
    }



    private SCACertificate combineCertAndPayload(SCACertificate certificate, ContentAccessPayload payload) {
        Date certUpdated = certificate.getUpdated();
        Date payloadUpdated = payload.getTimestamp();

        SCACertificate combined = new SCACertificate();
        combined.setCert(certificate.getCert() + payload.getPayload());
        combined.setCreated(certificate.getCreated());
        combined.setUpdated(payloadUpdated.after(certUpdated) ? payloadUpdated : certUpdated);
        combined.setId(certificate.getId());
        combined.setKey(certificate.getKey());
        combined.setSerial(certificate.getSerial());

        return combined;
    }

    private SCACertificate getCertificate(Owner owner, Consumer consumer, List<Environment> environments) {
        SCACertificate scaCertificate = consumer.getContentAccessCert();

        if (scaCertificate == null) {
            log.info("Generating new SCA certificate key for consumer: \"{}\"", consumer.getUuid());

            KeyPair keypair = this.keyPairGenerator.getKeyPair(consumer);

            scaCertificate = new SCACertificate();
            scaCertificate.setConsumer(consumer);
            scaCertificate.setKeyAsBytes(this.pemEncoder.encodeAsBytes(keypair.getPrivate()));
            scaCertificate.setCert(""); // we'll correct this later

            this.contentAccessCertificateCurator.create(scaCertificate);
            consumer.setContentAccessCert(scaCertificate);
        }

        // TODO: FIXME: cutoff should be configurable or something.
        Date regenerationCutoff = Date.from(Instant.now().plus(10L, ChronoUnit.DAYS));

        // TODO: FIXME: DANGER: serial.getExpiration is not guaranteed to be non-null
        CertificateSerial serial = scaCertificate.getSerial();
        if (serial == null || regenerationCutoff.after(serial.getExpiration())) {
            log.info("Generating new SCA x509 certificate for consumer: \"{}\"", consumer.getUuid());

            // Revoke the previous serial, if we had one...
            if (serial != null) {
                this.serialCurator.revokeById(serial.getId());
            }

            KeyPair keypair = this.keyPairGenerator.getKeyPair(consumer);

            OffsetDateTime start = OffsetDateTime.now().minusHours(1L);
            OffsetDateTime end = start.plusYears(1L);

            org.candlepin.model.dto.Product container = this.createSCAProdContainer(owner, consumer,
                environments);

            serial = this.createSerial(end);
            X509Certificate x509Cert = this.createX509Cert(consumer.getUuid(), owner, serial, keypair,
                container, start, end);

            scaCertificate.setSerial(serial);
            scaCertificate.setCert(this.pemEncoder.encodeAsString(x509Cert));

            // TODO: Is it safe to rely on Hibernate's auto-commit here? Merging doesn't really force
            // an update anyway... I really dislike magic like this. Just let me say when I want things
            // to be persisted!
            // this.contentAccessCertificateCurator.saveOrUpdate(scaCertificate);
        }

        return scaCertificate;
    }






    public SCACertificate getSCAX509Certificate(Consumer consumer) {
        if (consumer == null) {
            throw new IllegalArgumentException("consumer is null");
        }

        Owner owner = consumer.getOwner();
        if (owner == null || !owner.isUsingSimpleContentAccess() ||
            !this.v3CapabilityCheck.isCertV3Capable(consumer)) {

            log.debug("Consumer is not in SCA mode, or is not v3cert capable: {}, {}, {}",
                owner == null, !owner.isUsingSimpleContentAccess(),
                !this.v3CapabilityCheck.isCertV3Capable(consumer));

            return null;
        }

        List<Environment> environments = this.environmentCurator.getConsumerEnvironments(consumer);

        return this.getCertificate(owner, consumer, environments);
    }

    public ContentAccessPayload getSCAContentPayload(Consumer consumer) {
        if (consumer == null) {
            throw new IllegalArgumentException("consumer is null");
        }

        Owner owner = consumer.getOwner();
        if (owner == null || !owner.isUsingSimpleContentAccess() ||
            !this.v3CapabilityCheck.isCertV3Capable(consumer)) {

            log.debug("Consumer is not in SCA mode, or is not v3cert capable: {}, {}, {}",
                owner == null, !owner.isUsingSimpleContentAccess(),
                !this.v3CapabilityCheck.isCertV3Capable(consumer));

            return null;
        }

        // Impl note:
        // These need to be ordered according to priority! At the time of writing, getConsumerEnvironments
        // does this, but if that ever changes, we absolutely need that sorting here.
        List<Environment> environments = this.environmentCurator.getConsumerEnvironments(consumer);

        // Fetch the components for our SCA cert
        return this.getContentAccessPayload(owner, consumer, environments);
    }



    public SCACertificate generate(Consumer consumer) {
        if (consumer == null) {
            throw new IllegalArgumentException("consumer is null");
        }

        Owner owner = consumer.getOwner();
        if (owner == null || !owner.isUsingSimpleContentAccess() ||
            !this.v3CapabilityCheck.isCertV3Capable(consumer)) {

            log.debug("Consumer is not in SCA mode, or is not v3cert capable: {}, {}, {}",
                owner == null, !owner.isUsingSimpleContentAccess(),
                !this.v3CapabilityCheck.isCertV3Capable(consumer));

            return null;
        }

        try {
            // Impl note:
            // These need to be ordered according to priority! At the time of writing, getConsumerEnvironments
            // does this, but if that ever changes, we absolutely need that sorting here.
            List<Environment> environments = this.environmentCurator.getConsumerEnvironments(consumer);

            // Fetch the components for our SCA cert
            SCACertificate cert = this.getCertificate(owner, consumer, environments);
            ContentAccessPayload payload = this.getContentAccessPayload(owner, consumer, environments);

            // Combine both components into a fake cert... thing...
            return this.combineCertAndPayload(cert, payload);
        }
        catch (Exception e) {
            // Is this necessary? Why are we eating the whole exception? We're likely very broken at
            // this point
            log.error("Unexpected exception occurred while fetching SCA certificate for consumer: {}",
                consumer, e);
        }

        return null;
    }

    // /**
    //  * Fetch and potentially regenerate a Simple Content Access certificate, used to grant access to
    //  * some content. The certificate will be regenerated if it is missing, or new content has been made
    //  * available. Otherwise, it will be simply fetched.
    //  *
    //  * @return Client entitlement certificates
    //  */
    // public SCACertificate generate(Consumer consumer) {
    //     // Ensure the org is in SCA mode and the consumer is able to process the cert we'll be
    //     // generating for them.
    //     Owner owner = consumer.getOwner();
    //     if (owner == null || !owner.isUsingSimpleContentAccess() ||
    //         !this.v3CapabilityCheck.isCertV3Capable(consumer)) {

    //         log.debug("Consumer is not in SCA mode, or is not v3cert capable: {}, {}, {}",
    //             owner == null, !owner.isUsingSimpleContentAccess(),
    //             !this.v3CapabilityCheck.isCertV3Capable(consumer));

    //         return null;
    //     }

    //     try {
    //         SCACertificate result = this.generateFor(consumer, owner);
    //         return this.wrap(result);
    //     }
    //     catch (Exception e) {
    //         log.error("Unexpected exception occurred while fetching SCA certificate for consumer: {}",
    //             consumer, e);
    //     }

    //     return null;
    // }

    // private SCACertificate wrap(SCACertificate cert) {
    //     SCACertificate result = new SCACertificate();
    //     result.setCert(cert.getCert() + cert.getContent());
    //     result.setCreated(cert.getCreated());
    //     result.setUpdated(cert.getUpdated());
    //     result.setId(cert.getId());
    //     result.setConsumer(cert.getConsumer());
    //     result.setKey(cert.getKey());
    //     result.setSerial(cert.getSerial());
    //     return result;
    // }

    // /**
    //  * Method create a new {@link SCACertificate}. If certificate
    //  * already exists it is reused and updated.
    //  *
    //  * @param consumer A consumer for which to create a certificate
    //  * @param owner An owner to be used for certificate creation
    //  * @return Content access certificate
    //  */
    // private SCACertificate generateFor(Consumer consumer, Owner owner) {
    //     SCACertificate existing = consumer.getContentAccessCert();

    //     SCACertificate certificate = existing != null ?
    //         this.updateCertificate(consumer, owner, existing) :
    //         this.createCertificate(consumer, owner);

    //     // Due to the shenanigans we do to wrap and juggle the data in this cert, it's critical we invoke
    //     // Hibernate's persist and update hooks by flushing the creation/update operations above so the
    //     // dates are correct in the output cert object.
    //     this.contentAccessCertificateCurator.flush();

    //     return certificate;
    // }

    private boolean payloadExpired(ContentAccessPayload payload, Owner owner,
        List<Environment> environments) {

        Date payloadTimestamp = payload.getTimestamp();

        Stream<Date> ownerContentUpdate = Stream.of(owner.getLastContentUpdate());
        Stream<Date> envContentUpdates = environments.stream()
            .map(Environment::getLastContentUpdate);

        return Stream.concat(ownerContentUpdate, envContentUpdates)
            .anyMatch(contentUpdate -> contentUpdate.after(payloadTimestamp));
    }

    private String getNoramalizedArchitectures(Consumer consumer) {
        SortedSet<String> sorted = new TreeSet<>();

        String supportedArches = consumer.getFact(Consumer.Facts.SUPPORTED_ARCHITECTURES);

        String arch = consumer.getFact(Consumer.Facts.ARCHITECTURE);
        if (arch != null) {
            sorted.add(arch.strip().toLowerCase());
        }

        if (supportedArches != null) {
            List<String> sarches = Util.toList(supportedArches.strip().toLowerCase());
            sorted.addAll(sarches);
        }

        return String.join(",", sorted);
    }

    private String getNormalizedEnvironments(List<Environment> environments) {
        return environments.stream()
            .map(Environment::getId)
            .collect(Collectors.joining(","));
    }

    // TODO: Find a better home for this. Maybe it makes sense here, I guess; but I could also see other
    // places being acceptable as well.
    private String buildPayloadKey(Consumer consumer, List<Environment> environments) {
        String input = new StringBuilder("arches:")
            .append(this.getNoramalizedArchitectures(consumer))
            .append("-environments:")
            .append(this.getNormalizedEnvironments(environments))
            .toString();

        try {
            byte[] hash = MessageDigest.getInstance("SHA-256") // this really should be a constant somewhere
                .digest(input.getBytes(StandardCharsets.UTF_8));

            return new StringBuilder("v")
                .append(SCA_CONTENT_ACCESS_PAYLOAD_KEY_VERSION)
                .append(":")
                .append(HEX_FORMATTER.formatHex(hash))
                .toString();
        }
        catch (NoSuchAlgorithmException e) {
            // TODO: This should probably be named something better, or something. Maybe. It shouldn't
            // ever happen, but I know eventually it will.
            throw new RuntimeException(e);
        }
    }

    private String buildContentAccessPayload(Owner owner, Consumer consumer, List<Environment> environments) {
        ContentPathBuilder contentPathBuilder = ContentPathBuilder.from(owner, environments);
        PromotedContent promotedContent = new PromotedContent(contentPathBuilder).withAll(environments);

        Function<ProductContent, String> cidFetcher = pcinfo -> pcinfo.getContent().getId();
        Map<String, ProductContent> ownerContent = this.contentCurator
            .getActiveContentByOwner(owner.getId())
            .stream()
            .collect(Collectors.toMap(cidFetcher, Function.identity(),
                (v1, v2) -> new ProductContent(v2.getContent(), v1.isEnabled() || v2.isEnabled())));

        byte[] payloadBytes = this.createContentAccessDataPayload(consumer, ownerContent, promotedContent);
        String payload = this.createPayloadAndSignature(payloadBytes);

        return payload;
    }

    private ContentAccessPayload getContentAccessPayload(Owner owner, Consumer consumer,
        List<Environment> environments) {

        String payloadKey = this.buildPayloadKey(consumer, environments);

        ContentAccessPayload container = this.contentAccessPayloadCurator.getContentAccessPayload(
            owner.getId(), payloadKey);

        if (container == null || this.payloadExpired(container, owner, environments)) {
            log.info("Building content access payload for: {}, {}", owner.getKey(), payloadKey);

            Date timestamp = new Date();
            String payload = this.buildContentAccessPayload(owner, consumer, environments);

            if (container == null) {
                container = new ContentAccessPayload()
                    .setOwner(owner)
                    .setPayloadKey(payloadKey)
                    .setTimestamp(timestamp)
                    .setPayload(payload);

                this.contentAccessPayloadCurator.persist(container);
            }
            else {
                container.setTimestamp(timestamp)
                    .setPayload(payload);

                // TODO: Is it safe to rely on Hibernate's auto-commit here? Merging doesn't really force
                // an update anyway... I really dislike magic like this. Just let me say when I want things
                // to be persisted!
                // this.contentAccessPayloadCurator.merge(payload);
            }
        }

        return container;
    }

    // private SCACertificate createCertificate(Consumer consumer, Owner owner) {
    //     log.info("Generating new SCA certificate for consumer: \"{}\"", consumer.getUuid());
    //     OffsetDateTime start = OffsetDateTime.now().minusHours(1L);
    //     OffsetDateTime end = start.plusYears(1L);

    //     CertificateSerial serial = createSerial(end);

    //     KeyPair keyPair = this.keyPairGenerator.getKeyPair(consumer);
    //     org.candlepin.model.dto.Product container = createSCAProdContainer(owner, consumer);

    //     X509Certificate x509Cert = createX509Cert(consumer.getUuid(), owner, serial,
    //         keyPair, container, start, end);

    //     SCACertificate existing = new SCACertificate();
    //     existing.setSerial(serial);
    //     existing.setConsumer(consumer);
    //     existing.setKeyAsBytes(this.pemEncoder.encodeAsBytes(keyPair.getPrivate()));
    //     existing.setCert(this.pemEncoder.encodeAsString(x509Cert));

    //     SCACertificate savedCert = this.contentAccessCertificateCurator.create(existing);
    //     consumer.setContentAccessCert(savedCert);
    //     this.consumerCurator.merge(consumer);
    //     return savedCert;
    // }

    // private SCACertificate updateCertificate(Consumer consumer, Owner owner, SCACertificate existing) {
    //     Date now = new Date();
    //     Date expiration = existing.getSerial().getExpiration();
    //     boolean isX509CertExpired = expiration.before(now);

    //     if (isX509CertExpired) {
    //         OffsetDateTime start = OffsetDateTime.now().minusHours(1L);
    //         OffsetDateTime end = start.plusYears(1L);

    //         KeyPair keyPair = this.keyPairGenerator.getKeyPair(consumer);
    //         this.serialCurator.revokeById(existing.getSerial().getId());
    //         CertificateSerial serial = createSerial(end);
    //         org.candlepin.model.dto.Product container = createSCAProdContainer(owner, consumer);
    //         X509Certificate x509Cert = createX509Cert(consumer.getUuid(), owner,
    //             serial, keyPair, container, start, end);

    //         existing.setSerial(serial);
    //         existing.setCert(this.pemEncoder.encodeAsString(x509Cert));
    //         this.contentAccessCertificateCurator.saveOrUpdate(existing);
    //     }

    //     Date contentUpdate = owner.getLastContentUpdate();
    //     boolean shouldUpdateContent = !contentUpdate.before(existing.getUpdated());
    //     if (shouldUpdateContent || isX509CertExpired) {
    //         ContentPathBuilder contentPathBuilder = ContentPathBuilder.from(owner, environments);
    //         PromotedContent promotedContent = new PromotedContent(contentPathBuilder).withAll(environments);

    //         Function<ProductContent, String> cidFetcher = pcinfo -> pcinfo.getContent().getId();

    //         Map<String, ProductContent> ownerContent = this.contentCurator
    //             .getActiveContentByOwner(owner.getId())
    //             .stream()
    //             .collect(Collectors.toMap(cidFetcher, Function.identity(),
    //                 (v1, v2) -> new ProductContent(v2.getContent(), v1.isEnabled() || v2.isEnabled())));

    //         byte[] payloadBytes = createContentAccessDataPayload(consumer, ownerContent, promotedContent);
    //         existing.setContent(this.createPayloadAndSignature(payloadBytes));
    //         this.contentAccessCertificateCurator.saveOrUpdate(existing);
    //     }

    //     return existing;
    // }

    private org.candlepin.model.dto.Product createSCAProdContainer(Owner owner, Consumer consumer,
        List<Environment> environments) {

        org.candlepin.model.dto.Product container = new org.candlepin.model.dto.Product();
        List<Content> dtoContents = new ArrayList<>();

        for (Environment environment : environments) {
            dtoContents.add(createContent(owner, environment));
        }

        if (dtoContents.isEmpty()) {
            dtoContents.add(createContent(owner, null));
        }

        container.setContent(dtoContents);

        return container;
    }

    private CertificateSerial createSerial(OffsetDateTime end) {
        CertificateSerial serial = new CertificateSerial(Date.from(end.toInstant()));
        // We need the sequence generated id before we create the Certificate,
        // otherwise we could have used cascading create
        serialCurator.create(serial);
        return serial;
    }

    private X509Certificate createX509Cert(String consumerUuid, Owner owner, CertificateSerial serial,
        KeyPair keyPair, org.candlepin.model.dto.Product product, OffsetDateTime start, OffsetDateTime end) {

        // log.info("Generating X509 certificate for consumer \"{}\"...", consumerUuid);
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
            throw new CertificateCreationException("Failed to prepare extensions", e);
        }
    }

    private byte[] createContentAccessDataPayload(Consumer consumer,
        Map<String, ProductContent> activateContent, PromotedContent promotedContent) {

        String consumerUuid = consumer != null ? consumer.getUuid() : null;
        // log.info("Generating SCA payload for consumer \"{}\"...", consumerUuid);

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
