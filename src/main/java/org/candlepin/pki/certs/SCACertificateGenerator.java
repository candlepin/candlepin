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
import org.candlepin.model.CertificateSerial;
import org.candlepin.model.CertificateSerialCurator;
import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerCurator;
import org.candlepin.model.ContentAccessCertificateCurator;
import org.candlepin.model.ContentCurator;
import org.candlepin.model.Entitlement;
import org.candlepin.model.Environment;
import org.candlepin.model.EnvironmentCurator;
import org.candlepin.model.Owner;
import org.candlepin.model.Pool;
import org.candlepin.model.Product;
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
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import javax.inject.Inject;
import javax.inject.Provider;


/**
 * Handles creation of the content access certificates.
 */
public class SCACertificateGenerator {
    private static final Logger log = LoggerFactory.getLogger(SCACertificateGenerator.class);
    private static final String SCA_ENTITLEMENT_TYPE = "OrgLevel";

    private final V3CapabilityCheck v3CapabilityCheck;
    private final CertificateSerialCurator serialCurator;
    private final ContentCurator contentCurator;
    private final ContentAccessCertificateCurator contentAccessCertificateCurator;
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

    /**
     * Fetch and potentially regenerate a Simple Content Access certificate, used to grant access to
     * some content. The certificate will be regenerated if it is missing, or new content has been made
     * available. Otherwise, it will be simply fetched.
     *
     * @return Client entitlement certificates
     */
    public SCACertificate generate(Consumer consumer) {
        // Ensure the org is in SCA mode and the consumer is able to process the cert we'll be
        // generating for them.
        Owner owner = consumer.getOwner();
        if (owner == null || !owner.isUsingSimpleContentAccess() ||
            !this.v3CapabilityCheck.isCertV3Capable(consumer)) {
            return null;
        }

        try {
            SCACertificate result = this.consumerCurator.<SCACertificate>transactional()
                .allowExistingTransactions()
                .onRollback(status -> log.error("Rolling back SCA cert (re)generation transaction"))
                .execute(args -> this.generateFor(consumer, owner));

            return this.wrap(result);
        }
        catch (Exception e) {
            log.error("Unexpected exception occurred while fetching SCA certificate for consumer: {}",
                consumer, e);
        }

        return null;
    }

    private SCACertificate wrap(SCACertificate cert) {
        SCACertificate result = new SCACertificate();
        result.setCert(cert.getCert() + cert.getContent());
        result.setCreated(cert.getCreated());
        result.setUpdated(cert.getUpdated());
        result.setId(cert.getId());
        result.setConsumer(cert.getConsumer());
        result.setKey(cert.getKey());
        result.setSerial(cert.getSerial());
        return result;
    }

    /**
     * Method create a new {@link SCACertificate}. If certificate
     * already exists it is reused and updated.
     *
     * @param consumer A consumer for which to create a certificate
     * @param owner An owner to be used for certificate creation
     * @return Content access certificate
     */
    private SCACertificate generateFor(Consumer consumer, Owner owner) {
        SCACertificate existing = consumer.getContentAccessCert();

        if (existing == null) {
            return this.createCertificate(consumer, owner);
        }

        return this.updateCertificate(consumer, owner, existing);
    }

    private SCACertificate createCertificate(Consumer consumer, Owner owner) {
        log.info("Generating new SCA certificate for consumer: \"{}\"", consumer.getUuid());
        OffsetDateTime start = OffsetDateTime.now().minusHours(1L);
        OffsetDateTime end = start.plusYears(1L);

        CertificateSerial serial = createSerial(end);

        KeyPair keyPair = this.keyPairGenerator.getKeyPair(consumer);
        org.candlepin.model.dto.Product container = createSCAProdContainer(owner, consumer);

        List<Environment> environments = this.environmentCurator.getConsumerEnvironments(consumer);
        ContentPathBuilder contentPathBuilder = ContentPathBuilder.from(owner, environments);
        PromotedContent promotedContent = new PromotedContent(contentPathBuilder).withAll(environments);

        Map<org.candlepin.model.Content, Boolean> ownerContent = this.contentCurator
            .getActiveContentByOwner(owner.getId());

        byte[] payloadBytes = createContentAccessDataPayload(consumer, ownerContent, promotedContent);

        X509Certificate x509Cert = createX509Cert(consumer.getUuid(), owner, serial,
            keyPair, container, start, end);

        SCACertificate existing = new SCACertificate();
        existing.setSerial(serial);
        existing.setConsumer(consumer);
        existing.setKeyAsBytes(this.pemEncoder.encodeAsBytes(keyPair.getPrivate()));
        existing.setCert(this.pemEncoder.encodeAsString(x509Cert));
        existing.setContent(this.createPayloadAndSignature(payloadBytes));

        SCACertificate savedCert = this.contentAccessCertificateCurator.create(existing);
        consumer.setContentAccessCert(savedCert);
        this.consumerCurator.merge(consumer);
        return savedCert;
    }

    private SCACertificate updateCertificate(Consumer consumer,
        Owner owner, SCACertificate existing) {
        Date now = new Date();
        Date expiration = existing.getSerial().getExpiration();
        boolean isX509CertExpired = expiration.before(now);

        if (isX509CertExpired) {
            OffsetDateTime start = OffsetDateTime.now().minusHours(1L);
            OffsetDateTime end = start.plusYears(1L);

            KeyPair keyPair = this.keyPairGenerator.getKeyPair(consumer);
            this.serialCurator.revokeById(existing.getSerial().getId());
            CertificateSerial serial = createSerial(end);
            org.candlepin.model.dto.Product container = createSCAProdContainer(owner, consumer);
            X509Certificate x509Cert = createX509Cert(consumer.getUuid(), owner,
                serial, keyPair, container, start, end);

            existing.setSerial(serial);
            existing.setCert(this.pemEncoder.encodeAsString(x509Cert));
            this.contentAccessCertificateCurator.saveOrUpdate(existing);
        }

        Date contentUpdate = owner.getLastContentUpdate();
        boolean shouldUpdateContent = !contentUpdate.before(existing.getUpdated());
        if (shouldUpdateContent || isX509CertExpired) {
            List<Environment> environments = this.environmentCurator.getConsumerEnvironments(consumer);
            ContentPathBuilder contentPathBuilder = ContentPathBuilder.from(owner, environments);
            PromotedContent promotedContent = new PromotedContent(contentPathBuilder).withAll(environments);

            Map<org.candlepin.model.Content, Boolean> ownerContent = this.contentCurator
                .getActiveContentByOwner(owner.getId());

            byte[] payloadBytes = createContentAccessDataPayload(consumer, ownerContent, promotedContent);
            existing.setContent(this.createPayloadAndSignature(payloadBytes));
            this.contentAccessCertificateCurator.saveOrUpdate(existing);
        }

        return existing;
    }

    private org.candlepin.model.dto.Product createSCAProdContainer(Owner owner, Consumer consumer) {
        org.candlepin.model.dto.Product container = new org.candlepin.model.dto.Product();
        List<Content> dtoContents = new ArrayList<>();
        List<Environment> environments = this.environmentCurator.getConsumerEnvironments(consumer);

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

    private Content createContent(Owner owner, Environment environment) {
        Content dContent = new Content();

        String path = "";
        if (owner != null) {
            path += "/" + Util.encodeUrl(owner.getKey());
        }
        if (environment != null) {
            path += "/" + Util.encodeUrl(environment.getName());
        }
        dContent.setPath(path);
        return dContent;
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

}
