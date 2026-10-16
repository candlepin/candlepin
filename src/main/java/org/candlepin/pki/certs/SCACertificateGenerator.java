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

import org.candlepin.model.CertificateSerial;
import org.candlepin.model.Consumer;
import org.candlepin.model.ContentAccessPayload;
import org.candlepin.model.Environment;
import org.candlepin.model.Owner;
import org.candlepin.model.SCACertificate;
import org.candlepin.pki.CryptoCapabilitiesException;
import org.candlepin.pki.CryptoManager;
import org.candlepin.pki.Scheme;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.KeyException;
import java.util.Date;
import java.util.List;
import java.util.Objects;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;



/**
 * Responsible for creating {@link SCACertificate}s.
 */
@Singleton
public class SCACertificateGenerator {
    private static final Logger log = LoggerFactory.getLogger(SCACertificateGenerator.class);

    private final CryptoManager cryptoManager;
    private final V3CapabilityCheck v3CapabilityCheck;
    private final ContentAccessPayloadBuilderProvider caPayloadBuilderProvider;
    private final ScaCertificateDataProvider certificateDataProvider;
    private final ScaCertificateLifecycle certificateLifecycle;

    @Inject
    public SCACertificateGenerator(CryptoManager cryptoManager, V3CapabilityCheck v3CapabilityCheck,
        ContentAccessPayloadBuilderProvider caPayloadBuilderProvider,
        ScaCertificateDataProvider certificateDataProvider, ScaCertificateLifecycle certificateLifecycle) {

        this.cryptoManager = Objects.requireNonNull(cryptoManager);
        this.v3CapabilityCheck = Objects.requireNonNull(v3CapabilityCheck);
        this.caPayloadBuilderProvider = Objects.requireNonNull(caPayloadBuilderProvider);
        this.certificateDataProvider = Objects.requireNonNull(certificateDataProvider);
        this.certificateLifecycle = Objects.requireNonNull(certificateLifecycle);
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
        throws ConcurrentContentPayloadCreationException, CryptoCapabilitiesException {

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
        List<Environment> environments = this.certificateDataProvider.getEnvironments(consumer);
        Scheme scheme = this.cryptoManager.getCryptoScheme(consumer);

        return this.getContentAccessPayload(scheme, owner, consumer, environments);
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
    public SCACertificate getX509Certificate(Consumer consumer) throws CryptoCapabilitiesException {
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
        List<Environment> environments = this.certificateDataProvider.getEnvironments(consumer);
        Scheme scheme = this.cryptoManager.getCryptoScheme(consumer);

        try {
            return this.certificateLifecycle.getCertificate(scheme, consumer, environments);
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
    public SCACertificate generate(Consumer consumer)
        throws ConcurrentContentPayloadCreationException, CryptoCapabilitiesException {

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
        List<Environment> environments = this.certificateDataProvider.getEnvironments(consumer);
        Scheme scheme = this.cryptoManager.getCryptoScheme(consumer);

        try {
            SCACertificate cert = this.certificateLifecycle.getCertificate(scheme, consumer, environments);
            ContentAccessPayload payload = this.getContentAccessPayload(scheme, owner, consumer,
                environments);

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

    /**
     * Generates an organization-level ExportV2 certificate with global content from non-expired pools
     *
     * @param consumer
     *     consumer supplying the organization and signing scheme
     * @return separate export artifact, or null when SCA or v3 certificates are unavailable
     *
     * @throws CertificateCreationException
     *     if the consumer doesn't support SCA or v3 certificates
     */
    public SCACertificate generateScaCertificate(Consumer consumer)
        throws ConcurrentContentPayloadCreationException, CryptoCapabilitiesException {

        if (consumer == null) {
            throw new IllegalArgumentException("consumer is null");
        }
        Owner owner = consumer.getOwner();
        if (owner == null || !owner.isUsingSimpleContentAccess() ||
            !this.v3CapabilityCheck.isCertV3Capable(consumer)) {
            throw new CertificateCreationException("Consumer doesn't support SCA or v3 certificates");
        }

        Scheme scheme = this.cryptoManager.getCryptoScheme(consumer);
        ContentAccessPayload payload = this.caPayloadBuilderProvider.get()
            .forExportV2()
            .setCryptoScheme(scheme)
            .setOwner(owner)
            .setConsumer(consumer)
            .setEnvironments(List.of())
            .build();
        try {
            SCACertificate certificate = this.certificateLifecycle.createExportV2Certificate(scheme, consumer);
            certificate.setCert(certificate.getCert() + payload.getPayload());
            return certificate;
        }
        catch (KeyException e) {
            throw new CertificateCreationException("Exception occurred while building export certificate", e);
        }
    }

    private ContentAccessPayload getContentAccessPayload(Scheme scheme, Owner owner, Consumer consumer,
        List<Environment> environments) throws ConcurrentContentPayloadCreationException {

        return this.caPayloadBuilderProvider.get()
            .setCryptoScheme(scheme)
            .setOwner(owner)
            .setConsumer(consumer)
            .setEnvironments(environments)
            .build();
    }
}
