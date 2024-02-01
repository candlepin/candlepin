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
package org.candlepin.controller;

import org.candlepin.audit.EventSink;
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
import org.candlepin.model.ConsumerCapability;
import org.candlepin.model.ConsumerCurator;
import org.candlepin.model.ConsumerType;
import org.candlepin.model.ConsumerType.ConsumerTypeEnum;
import org.candlepin.model.ConsumerTypeCurator;
import org.candlepin.model.ContentAccessCertificate;
import org.candlepin.model.ContentAccessCertificateCurator;
import org.candlepin.model.ContentCurator;
import org.candlepin.model.Entitlement;
import org.candlepin.model.Environment;
import org.candlepin.model.EnvironmentCurator;
import org.candlepin.model.Owner;
import org.candlepin.model.OwnerCurator;
import org.candlepin.model.Pool;
import org.candlepin.model.Product;
import org.candlepin.model.dto.Content;
import org.candlepin.pki.DistinguishedName;
import org.candlepin.pki.KeyPairGenerator;
import org.candlepin.pki.OID;
import org.candlepin.pki.PKIUtility;
import org.candlepin.pki.PemEncoder;
import org.candlepin.pki.X509Extension;
import org.candlepin.pki.certs.X509StringExtension;
import org.candlepin.service.ProductServiceAdapter;
import org.candlepin.service.model.ContentInfo;
import org.candlepin.service.model.ProductContentInfo;
import org.candlepin.service.model.ProductInfo;
import org.candlepin.util.Arch;
import org.candlepin.util.Util;
import org.candlepin.util.X509V3ExtensionUtil;

import com.google.inject.persist.Transactional;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.security.GeneralSecurityException;
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


/**
 * The ContentAccessManager provides management operations for organization and consumer level
 * content access modes.
 */
public class ContentAccessManager {
    private static final Logger log = LoggerFactory.getLogger(ContentAccessManager.class);

    /**
     * The ContentAccessMode enum specifies the supported content access modes, as well as some
     * utility methods for resolving, matching, and converting to database-compatible values.
     */
    public enum ContentAccessMode {
        /** traditional entitlement mode requiring clients to consume pools to access content */
        ENTITLEMENT,

        /** simple content access (SCA) mode; clients have access to all org content by default */
        ORG_ENVIRONMENT;

        /**
         * Returns the a value to represent this ContentAccessMode instance in a database, which
         * can be resolved back to a valid ContentAccessMode.
         *
         * @return
         *  a database value to represent this ContentAccessMode instance
         */
        public String toDatabaseValue() {
            return this.name().toLowerCase();
        }

        /**
         * Checks if the provided content access mode name matches the name of this
         * ContentAccessMode instance.
         *
         * @param name
         *  the content access mode name to check
         *
         * @return
         *  true if the content access mode name matches the name of this ContentAccessMode
         *  instance; false otherwise
         */
        public boolean matches(String name) {
            return this.toDatabaseValue().equals(name);
        }

        /**
         * Fetches the default content access mode
         *
         * @return
         *  the default ContentAccessMode instance
         */
        public static ContentAccessMode getDefault() {
            return ORG_ENVIRONMENT;
        }

        /**
         * Resolves the mode name to a ContentAccessMode enum, using the default mode for empty
         * values. If the content access mode name is null, this function returns null.
         *
         * @param name
         *  the name to resolve
         *
         * @throws IllegalArgumentException
         *  if the name cannot be resolved to a valid content access mode
         *
         * @return
         *  the ContentAccessMode value representing the given mode name, or null if the provided
         *  content access mode name is null
         */
        public static ContentAccessMode resolveModeName(String name) {
            return ContentAccessMode.resolveModeName(name, false);
        }

        /**
         * Resolves the mode name to a ContentAccessMode enum, using the default mode for empty
         * values. If resolveNull is set to true, null values will be converted into the default
         * content access mode as well, otherwise this function will return null.
         *
         * @param name
         *  the name to resolve
         *
         * @param resolveNull
         *  whether or not to treat null values as empty values for resolution
         *
         * @throws IllegalArgumentException
         *  if the name cannot be resolved to a valid content access mode
         *
         * @return
         *  the ContentAccessMode value representing the given mode name, or null if the provided
         *  content access mode name isn null and resolveNull is not set
         */
        public static ContentAccessMode resolveModeName(String name, boolean resolveNull) {
            if (name == null) {
                return resolveNull ? ContentAccessMode.getDefault() : null;
            }

            if (name.isEmpty()) {
                return ContentAccessMode.getDefault();
            }

            for (ContentAccessMode mode : ContentAccessMode.values()) {
                if (mode.matches(name)) {
                    return mode;
                }
            }

            throw new IllegalArgumentException("Content access mode name cannot be resolved: " + name);
        }
    }

    /**
     * Fetches the default content access mode list database value
     *
     * @return the default content access mode list database value as a string
     */
    public static String getListDefaultDatabaseValue() {
        return String.join(",", ContentAccessMode.ENTITLEMENT.toDatabaseValue(),
            ContentAccessMode.ORG_ENVIRONMENT.toDatabaseValue());
    }

    private static final String BASIC_ENTITLEMENT_TYPE = "basic";
    private static final String SCA_ENTITLEMENT_TYPE = "OrgLevel";

    private final PKIUtility pki;
    private final CertificateSerialCurator serialCurator;
    private final OwnerCurator ownerCurator;
    private final ContentCurator contentCurator;
    private final ContentAccessCertificateCurator contentAccessCertificateCurator;
    private final X509V3ExtensionUtil v3extensionUtil;
    private final ConsumerCurator consumerCurator;
    private final ConsumerTypeCurator consumerTypeCurator;
    private final EnvironmentCurator environmentCurator;
    private final ContentAccessCertificateCurator contentAccessCertCurator;
    private final EventSink eventSink;
    private final AnonymousCloudConsumerCurator anonCloudConsumerCurator;
    private final AnonymousContentAccessCertificateCurator anonContentAccessCertCurator;
    private final ProductServiceAdapter prodAdapter;
    private final AnonymousCertContentCache contentCache;
    private final KeyPairGenerator keyPairGenerator;
    private final PemEncoder pemEncoder;

    private final boolean standalone;

    @Inject
    public ContentAccessManager(
        Configuration config,
        PKIUtility pki,
        X509V3ExtensionUtil v3extensionUtil,
        ContentAccessCertificateCurator contentAccessCertificateCurator,
        CertificateSerialCurator serialCurator,
        OwnerCurator ownerCurator,
        ContentCurator contentCurator,
        ConsumerCurator consumerCurator,
        ConsumerTypeCurator consumerTypeCurator,
        EnvironmentCurator environmentCurator,
        ContentAccessCertificateCurator contentAccessCertCurator,
        EventSink eventSink,
        AnonymousCloudConsumerCurator anonCloudConsumerCurator,
        AnonymousContentAccessCertificateCurator anonContentAccessCertCurator,
        ProductServiceAdapter prodAdapter,
        AnonymousCertContentCache contentCache,
        KeyPairGenerator keyPairGenerator,
        PemEncoder pemEncoder) {

        this.pki = Objects.requireNonNull(pki);
        this.contentAccessCertificateCurator = Objects.requireNonNull(contentAccessCertificateCurator);
        this.serialCurator = Objects.requireNonNull(serialCurator);
        this.v3extensionUtil = Objects.requireNonNull(v3extensionUtil);
        this.ownerCurator = Objects.requireNonNull(ownerCurator);
        this.contentCurator = Objects.requireNonNull(contentCurator);
        this.consumerCurator = Objects.requireNonNull(consumerCurator);
        this.consumerTypeCurator = Objects.requireNonNull(consumerTypeCurator);
        this.environmentCurator = Objects.requireNonNull(environmentCurator);
        this.contentAccessCertCurator = Objects.requireNonNull(contentAccessCertCurator);
        this.eventSink = Objects.requireNonNull(eventSink);
        this.anonCloudConsumerCurator = Objects.requireNonNull(anonCloudConsumerCurator);
        this.anonContentAccessCertCurator = Objects.requireNonNull(anonContentAccessCertCurator);
        this.prodAdapter = Objects.requireNonNull(prodAdapter);
        this.contentCache = Objects.requireNonNull(contentCache);
        this.keyPairGenerator = Objects.requireNonNull(keyPairGenerator);
        this.pemEncoder = Objects.requireNonNull(pemEncoder);
        this.standalone = config.getBoolean(ConfigProperties.STANDALONE);
    }

    /**
     * Fetch and potentially regenerate a Simple Content Access certificate, used to grant access to
     * some content. The certificate will be regenerated if it is missing, or new content has been made
     * available. Otherwise, it will be simply fetched.
     *
     * @return Client entitlement certificates
     */
    public ContentAccessCertificate getCertificate(Consumer consumer) {
        // TODO: FIXME: Redesign all of this.

        // Ensure the org is in SCA mode and the consumer is able to process the cert we'll be
        // generating for them.
        Owner owner = consumer.getOwner();
        if (owner == null || !owner.isUsingSimpleContentAccess() || !this.consumerIsCertV3Capable(consumer)) {
            return null;
        }

        try {
            ContentAccessCertificate result = this.consumerCurator.<ContentAccessCertificate>transactional()
                .allowExistingTransactions()
                .onRollback(status -> log.error("Rolling back SCA cert (re)generation transaction"))
                .execute(args -> {
                    ContentAccessCertificate existing = consumer.getContentAccessCert();
                    return existing == null ?
                        createNewScaCertificate(consumer, owner) :
                        updateScaCertificate(consumer, owner, existing);
                });

            return this.wrap(result);
        }
        catch (Exception e) {
            log.error("Unexpected exception occurred while fetching SCA certificate for consumer: {}",
                consumer, e);
        }

        return null;
    }

    private ContentAccessCertificate createNewScaCertificate(Consumer consumer, Owner owner)
        throws IOException, GeneralSecurityException {
        log.info("Generating new SCA certificate for consumer: \"{}\"", consumer.getUuid());
        OffsetDateTime start = OffsetDateTime.now().minusHours(1L);
        OffsetDateTime end = start.plusYears(1L);

        CertificateSerial serial = createSerial(end);

        KeyPair keyPair = this.keyPairGenerator.getKeyPair(consumer);
        byte[] pemEncodedKeyPair = this.pemEncoder.encodeAsBytes(keyPair.getPrivate());
        org.candlepin.model.dto.Product container = createSCAProdContainer(owner, consumer);

        List<Environment> environments = this.environmentCurator.getConsumerEnvironments(consumer);
        ContentPathBuilder contentPathBuilder = ContentPathBuilder.from(owner, environments);
        PromotedContent promotedContent = new PromotedContent(contentPathBuilder).withAll(environments);

        Map<org.candlepin.model.Content, Boolean> ownerContent = this.contentCurator
            .getActiveContentByOwner(owner.getId());

        byte[] payloadBytes = createContentAccessDataPayload(consumer, ownerContent, promotedContent);

        ContentAccessCertificate existing = new ContentAccessCertificate();
        existing.setSerial(serial);
        existing.setKeyAsBytes(pemEncodedKeyPair);
        existing.setConsumer(consumer);

        existing.setCert(createX509Cert(consumer.getUuid(), owner, serial, keyPair, container,
            SCA_ENTITLEMENT_TYPE, start, end));
        existing.setContent(this.createPayloadAndSignature(payloadBytes));
        ContentAccessCertificate savedCert = this.contentAccessCertificateCurator.create(existing);
        consumer.setContentAccessCert(savedCert);
        this.consumerCurator.merge(consumer);
        return savedCert;
    }

    private ContentAccessCertificate updateScaCertificate(Consumer consumer, Owner owner,
        ContentAccessCertificate existing) throws GeneralSecurityException, IOException {
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

            existing.setSerial(serial);
            existing.setCert(createX509Cert(consumer.getUuid(), owner, serial, keyPair, container,
                SCA_ENTITLEMENT_TYPE, start, end));
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
        List<org.candlepin.model.dto.Content> dtoContents = new ArrayList<>();
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

    private ContentAccessCertificate wrap(ContentAccessCertificate cert) {
        ContentAccessCertificate result = new ContentAccessCertificate();
        result.setCert(cert.getCert() + cert.getContent());
        result.setCreated(cert.getCreated());
        result.setUpdated(cert.getUpdated());
        result.setId(cert.getId());
        result.setConsumer(cert.getConsumer());
        result.setKey(cert.getKey());
        result.setSerial(cert.getSerial());
        return result;
    }

    private CertificateSerial createSerial(OffsetDateTime end) {
        CertificateSerial serial = new CertificateSerial(Date.from(end.toInstant()));
        // We need the sequence generated id before we create the Certificate,
        // otherwise we could have used cascading create
        serialCurator.create(serial);
        return serial;
    }

    private String createX509Cert(String consumerUuid, Owner owner, CertificateSerial serial, KeyPair keyPair,
        org.candlepin.model.dto.Product product, String entType, OffsetDateTime start, OffsetDateTime end)
        throws GeneralSecurityException, IOException {

        log.info("Generating X509 certificate for consumer \"{}\"...", consumerUuid);
        Set<X509Extension> extensions = new HashSet<>(prepareV3Extensions(entType));
        extensions.addAll(prepareV3ByteExtensions(product));
        DistinguishedName dn = new DistinguishedName(consumerUuid, owner);

        X509Certificate x509Cert = this.pki.createX509Certificate(
            dn, extensions, Date.from(start.toInstant()),
            Date.from(end.toInstant()), keyPair, BigInteger.valueOf(serial.getId()), null);

        return this.pemEncoder.encodeAsString(x509Cert);
    }

    private Content createContent(Owner owner, Environment environment) {
        Content dContent = new Content();
        // TODO What content path do we want for SCA certs? Are we ok with /{ownerKey}/{envName}?
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

    private String createPayloadAndSignature(byte[] payloadBytes)
        throws IOException {
        String payload = "-----BEGIN ENTITLEMENT DATA-----\n";
        payload += Util.toBase64(payloadBytes);
        payload += "-----END ENTITLEMENT DATA-----\n";

        byte[] bytes = pki.getSHA256WithRSAHash(new ByteArrayInputStream(payloadBytes));
        String signature = "-----BEGIN RSA SIGNATURE-----\n";
        signature += Util.toBase64(bytes);
        signature += "-----END RSA SIGNATURE-----\n";
        return payload + signature;
    }

    /**
     * Checks if the specified consumer is capable of using v3 certificates
     *
     * @param consumer
     *  The consumer to check
     *
     * @return
     *  true if the consumer is capable of using v3 certificates; false otherwise
     */
    private boolean consumerIsCertV3Capable(Consumer consumer) {
        if (consumer == null || consumer.getTypeId() == null) {
            throw new IllegalArgumentException("consumer is null or lacks a consumer type");
        }

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

    private Set<X509Extension> prepareV3Extensions(String entType) {
        Set<X509Extension> extensions = new HashSet<>(v3extensionUtil.getExtensions());
        extensions.add(new X509StringExtension(
            OID.EntitlementType.namespace(), entType));
        return extensions;
    }

    private Set<X509Extension> prepareV3ByteExtensions(org.candlepin.model.dto.Product container)
        throws IOException {
        List<org.candlepin.model.dto.Product> products = new ArrayList<>();
        products.add(container);
        return v3extensionUtil.getByteExtensions(products);
    }

    private byte[] createContentAccessDataPayload(Consumer consumer,
        Map<org.candlepin.model.Content, Boolean> ownerContent, PromotedContent promotedContent)
        throws IOException {

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

        return v3extensionUtil.createEntitlementDataPayload(productModels, consumerUuid, emptyPool, null);
    }

    /**
     * Checks if the content view for the given consumer has changed since date provided. This will
     * be true if the consumer is currently operating in simple content access mode (SCA), and any
     * of the following are true:
     *
     *  - the consumer does not currently have a certificate or certificate body/payload
     *  - the consumer's certificate or payload was generated after the date provided
     *  - the consumer's certificate has expired since the date provided
     *  - the consumer's certificate or payload was generated before the date provided, but the
     *    organization's content view has changed since the date provided
     *
     * If the consumer is not operating in SCA mode, or none of the above conditions are met, this
     * method returns false.
     *
     * @param consumer
     *  the consumer to check for certificate updates
     *
     * @param date
     *  the date to use for update checks
     *
     * @throws IllegalArgumentException
     *  if consumer or date are null
     *
     * @return
     *  true if the cert or its content has changed since the date provided; false otherwise
     */
    public boolean hasCertChangedSince(Consumer consumer, Date date) {
        if (consumer == null) {
            throw new IllegalArgumentException("consumer is null");
        }

        if (date == null) {
            throw new IllegalArgumentException("date is null");
        }

        Owner owner = consumer.getOwner();

        // Impl note:
        // This method is kinda... sketch, and prone to erroneous results. Since the cert, cert
        // serial, and payload  have different created/updated timestamps, they can be updated
        // individually. Depending on which date is used as input, this may or may not trigger an
        // update when an update is not strictly necessary.
        // We try to minimize this by providing the "best" creation/update date above, but that still
        // doesn't prevent this method from being called with a datetime that lies between the
        // persistence of the cert and the payload, triggering a false positive.
        // When time permits to refactor all of this logic, we should examine everything surrounding
        // this design and determine whether or not we need two separate objects, or if this question
        // has any real value.

        if (owner.isUsingSimpleContentAccess()) {
            // Check if the owner's content view has changed since the date
            if (!date.after(owner.getLastContentUpdate())) {
                return true;
            }

            // Check cert properties
            ContentAccessCertificate cert = consumer.getContentAccessCert();
            if (cert == null) {
                return true;
            }

            // The date provided by the client does not preserve milliseconds, so we need to round down
            // the dates preserved on the server by removing milliseconds for a proper date comparison
            Date certUpdatedDate = Util.roundDownToSeconds(cert.getUpdated());
            Date certExpirationDate = Util.roundDownToSeconds(cert.getSerial().getExpiration());
            return date.before(certUpdatedDate) ||
                date.after(certExpirationDate);
        }

        return false;
    }

    @Transactional
    public void removeContentAccessCert(Consumer consumer) {
        if (consumer.getContentAccessCert() != null) {
            this.contentAccessCertificateCurator.delete(consumer.getContentAccessCert());
            consumer.setContentAccessCert(null);
        }
    }

    /**
     * Updates the content access mode state for the given owner using the updated content access mode
     * list and content access mode provided.
     *
     * @param owner
     *  The owner to refresh
     *
     * @param updatedList
     *  the updated content access mode list to apply, or an empty string to restore the default value
     *
     * @param updatedMode
     *  the updated content access mode to apply, or an empty string to restore the default value
     *
     * @throws IllegalStateException
     *  if the requested content access mode is not in the provided content access mode list
     *
     * @return
     *  the refreshed owner; may be a different instance than the input owner
     */
    @Transactional
    public Owner updateOwnerContentAccess(Owner owner, String updatedList, String updatedMode) {
        if (owner == null) {
            throw new IllegalArgumentException("owner is null");
        }

        boolean listUpdated = false;
        boolean modeUpdated = false;

        this.ownerCurator.lock(owner);

        final String defaultMode = ContentAccessMode.getDefault().toDatabaseValue();

        // Grab the current list and mode
        String currentList = this.resolveContentAccessListValue(owner.getContentAccessModeList(), true);
        String currentMode = this.resolveContentAccessValue(owner.getContentAccessMode(), true);

        // Resolve the updated list and mode
        updatedList = this.resolveContentAccessListValue(updatedList, false);
        updatedMode = this.resolveContentAccessValue(updatedMode, false);

        if (updatedList != null) {
            String[] modes = updatedList.split(",");

            // We're not interested in storing access modes that we don't support
            for (String mode : modes) {
                // This will throw an IAE if the mode isn't one we support.
                ContentAccessMode.resolveModeName(mode);
            }

            listUpdated = !updatedList.equals(currentList);
            currentList = updatedList;

            // If we're not updating the mode as well, we need to ensure the mode is either valid
            // with the change, or becomes valid by force.
            if (updatedMode == null) {
                // if the current mode is no longer valid, check if we have the default mode
                // available. If so, use it. Otherwise, use the first mode in the list.
                if (!ArrayUtils.contains(modes, currentMode)) {
                    updatedMode = ArrayUtils.contains(modes, defaultMode) ? defaultMode : modes[0];
                }
            }
        }

        if (updatedMode != null) {
            // Verify that the mode is present in the access mode list
            String[] modes = currentList.split(",");

            if (!ArrayUtils.contains(modes, updatedMode)) {
                throw new IllegalArgumentException(
                    "Content access mode is not present in the owner's access mode list");
            }

            // If the current mode is empty, we want to treat that as the default value and assume
            // it hasn't been set yet. Otherwise, do a standard equality check here.
            modeUpdated = !(StringUtils.isEmpty(currentMode) ?
                ContentAccessMode.getDefault().matches(updatedMode) :
                currentMode.equals(updatedMode));
        }

        if (listUpdated) {
            // Set new values & refresh as necessary
            owner.setContentAccessModeList(updatedList);
        }

        // If the content access mode changed, we'll need to update it and refresh the access certs
        if (modeUpdated) {
            owner.setContentAccessMode(updatedMode);

            owner = this.ownerCurator.merge(owner);
            ownerCurator.flush();

            // Delete the SCA cert if we're leaving SCA mode
            if (this.isTransitioningFrom(currentMode, updatedMode, ContentAccessMode.ORG_ENVIRONMENT)) {
                this.contentAccessCertCurator.deleteForOwner(owner);
            }

            // Update sync times & report
            this.syncOwnerLastContentUpdate(owner);
            this.eventSink.emitOwnerContentAccessModeChanged(owner);

            log.info("Content access mode changed from {} to {} for owner {}", currentMode,
                updatedMode, owner.getKey());
        }
        else if (listUpdated) {
            owner = ownerCurator.merge(owner);
            ownerCurator.flush();
        }

        if (listUpdated) {
            // Ensure that the org's consumers are not using any modes which are no longer present
            // in the lists
            int culled = this.consumerCurator.cullInvalidConsumerContentAccess(owner, updatedList.split(","));
            log.debug("Corrected {} consumers with content access modes which are no longer valid", culled);
        }

        return owner;
    }

    /**
     * Checks if the content access mode is transitioning and, if so, if it is transitioning away
     * the target mode. That is, the current mode and updated modes are not equal, and the current
     * mode is equal to the target mode.
     *
     * @param current
     *  the current content access mode name
     *
     * @param updated
     *  the updated content access mode name
     *
     * @param target
     *  the targeted content access mode to check
     *
     * @return
     *  true if the content access mode is transitioning away the target mode; false otherwise
     */
    private boolean isTransitioningFrom(String current, String updated, ContentAccessMode target) {
        ContentAccessMode currentMode = ContentAccessMode.resolveModeName(current, true);
        ContentAccessMode updatedMode = ContentAccessMode.resolveModeName(updated, true);

        return currentMode != updatedMode && currentMode == target;
    }

    /**
     * Resolve the value of a content access mode string by returning the default if empty.
     * @param value The value as a string or null.
     * @param resolveNull if true, the default will be returned if the value is null.
     * @return the input value or the default content access mode.
     */
    private String resolveContentAccessValue(String value, boolean resolveNull) {
        if (value == null) {
            return resolveNull ? ContentAccessMode.getDefault().toDatabaseValue() : null;
        }

        if (value.isEmpty()) {
            return ContentAccessMode.getDefault().toDatabaseValue();
        }

        return value;
    }

    /**
     * Resolve the value of a content access mode list string by returning the default if empty.
     * @param value The value as a string or null.
     * @param resolveNull if true, the default will be returned if the value is null.
     * @return the input value or the default content access mode list.
     */
    private String resolveContentAccessListValue(String value, boolean resolveNull) {
        if (value == null) {
            return resolveNull ? ContentAccessManager.getListDefaultDatabaseValue() : null;
        }

        if (value.isEmpty()) {
            return ContentAccessManager.getListDefaultDatabaseValue();
        }

        return value;
    }

    /**
     * Synchronizes the last content update time for the given owner and persists the update.
     *
     * @param owner
     *  the owner for which to synchronize the last content update time
     *
     * @throws IllegalArgumentException
     *  if owner is null
     *
     * @return
     *  the synchronized owner
     */
    @Transactional
    public Owner syncOwnerLastContentUpdate(Owner owner) {
        if (owner == null) {
            throw new IllegalArgumentException("owner is null");
        }

        owner.syncLastContentUpdate();
        return this.ownerCurator.merge(owner);
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
     *  the retieved or generated certificate
     */
    @Transactional
    public AnonymousContentAccessCertificate getCertificate(AnonymousCloudConsumer consumer)
        throws GeneralSecurityException, IOException {
        if (standalone) {
            String msg = "cannot retrieve or create content access certificate in standalone mode";
            throw new RuntimeException(msg);
        }

        if (consumer == null) {
            throw new IllegalArgumentException("anonymous cloud consumer is null");
        }

        AnonymousContentAccessCertificate cert = consumer.getContentAccessCert();
        Date now = new Date();

        // Return a valid certificate if the anonymous cloud consumer has one
        if (cert != null && cert.getSerial() != null && cert.getSerial().getExpiration().after(now)) {
            log.debug("Returning existing and valid anonymous certificate for consumer: \"{}\"",
                consumer.getUuid());
            return cert;
        }

        // Generate a new certificate if one does not exist or the existing certificate is expired.
        // First attempt to retrieve an already built and cached content access payload based on the
        // anonymous cloud consumer's top level product IDs, or retrieve the data through adapters if
        // we hava a cache miss.
        String payload;
        List<Content> content;
        AnonymousCertContent cached = contentCache.get(consumer.getProductIds());
        if (cached != null) {
            log.debug("Anonymous content access certificate content retrieved from cache");
            payload = cached.contentAccessDataPayload();
            content = cached.content();
        }
        else {
            log.debug("Retrieving anonymous content access certificate content from product adapter");
            // Get product information from adapters to build the content access certificate
            List<ProductInfo> products = prodAdapter.getChildrenByProductIds(consumer.getProductIds());
            if (products == null || products.isEmpty()) {
                String msg = "Unable to retrieve products for anonymous cloud consumer: " +
                    consumer.getUuid();
                throw new RuntimeException(msg);
            }

            payload = createAnonPayloadAndSignature(products);
            List<ContentInfo> contentInfo = getContentInfo(products);
            content = convertContentInfoToContentDto(contentInfo);

            // Cache the generated content for future requests
            contentCache.put(consumer.getProductIds(), new AnonymousCertContent(payload, content));
        }

        return createAnonContentAccessCertificate(consumer, payload, content);
    }

    private AnonymousContentAccessCertificate createAnonContentAccessCertificate(
        AnonymousCloudConsumer consumer, String payloadAndSignature, List<Content> certificateContent)
        throws IOException, GeneralSecurityException {

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
        byte[] pemEncodedKeyPair = this.pemEncoder.encodeAsBytes(keyPair.getPrivate());

        org.candlepin.model.dto.Product container = new org.candlepin.model.dto.Product();
        container.setContent(certificateContent);
        String x509Cert = createX509Cert(consumer.getUuid(), null, serial, keyPair, container,
            BASIC_ENTITLEMENT_TYPE, start, end);

        AnonymousContentAccessCertificate caCert = new AnonymousContentAccessCertificate();
        caCert.setSerial(serial);
        caCert.setKeyAsBytes(pemEncodedKeyPair);
        caCert.setCert(x509Cert + payloadAndSignature);
        caCert = anonContentAccessCertCurator.create(caCert);

        consumer.setContentAccessCert(caCert);
        this.anonCloudConsumerCurator.merge(consumer);

        return caCert;
    }

    private String createAnonPayloadAndSignature(Collection<ProductInfo> prodInfo) throws IOException {
        List<ContentInfo> contentInfo = getContentInfo(prodInfo);
        List<org.candlepin.model.Content> contents = convertContentInfoToContent(contentInfo);
        Map<org.candlepin.model.Content, Boolean> activeContent = new HashMap<>();
        contents.forEach(content -> activeContent.put(content, true));
        PromotedContent promotedContent = new PromotedContent(ContentPathBuilder.from(null, null));
        byte[] data = createContentAccessDataPayload(null, activeContent, promotedContent);

        return createPayloadAndSignature(data);
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

}
