/**
 * Copyright (c) 2009 - 2016 Red Hat, Inc.
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
import org.candlepin.common.config.Configuration;
import org.candlepin.config.ConfigProperties;
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
import org.candlepin.model.Entitlement;
import org.candlepin.model.Environment;
import org.candlepin.model.EnvironmentContent;
import org.candlepin.model.EnvironmentCurator;
import org.candlepin.model.KeyPairCurator;
import org.candlepin.model.Owner;
import org.candlepin.model.OwnerContentCurator;
import org.candlepin.model.OwnerCurator;
import org.candlepin.model.OwnerEnvContentAccess;
import org.candlepin.model.OwnerEnvContentAccessCurator;
import org.candlepin.model.Pool;
import org.candlepin.model.Product;
import org.candlepin.pki.PKIUtility;
import org.candlepin.pki.X509ByteExtensionWrapper;
import org.candlepin.pki.X509ExtensionWrapper;
import org.candlepin.util.OIDUtil;
import org.candlepin.util.Util;
import org.candlepin.util.X509V3ExtensionUtil;

import com.google.inject.Inject;
import com.google.inject.persist.Transactional;

import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import javax.naming.ldap.Rdn;



/**
 * The ContentAccessManager provides management operations for organization and consumer level
 * content access modes.
 */
public class ContentAccessManager {
    private static Logger log = LoggerFactory.getLogger(ContentAccessManager.class);

    /**
     * The ContentAccessMode enum specifies the supported content access modes, as well as some
     * utility methods for resolving, matching, and converting to database-compatible values.
     */
    public static enum ContentAccessMode {
        ENTITLEMENT,
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
            return ENTITLEMENT;
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

    private Configuration config;
    private PKIUtility pki;
    private KeyPairCurator keyPairCurator;
    private CertificateSerialCurator serialCurator;
    private OwnerCurator ownerCurator;
    private OwnerContentCurator ownerContentCurator;
    private ContentAccessCertificateCurator contentAccessCertificateCurator;
    private X509V3ExtensionUtil v3extensionUtil;
    private OwnerEnvContentAccessCurator ownerEnvContentAccessCurator;
    private ConsumerCurator consumerCurator;
    private ConsumerTypeCurator consumerTypeCurator;
    private EnvironmentCurator environmentCurator;
    private ContentAccessCertificateCurator contentAccessCertCurator;
    private EventSink eventSink;

    private boolean standalone;

    @Inject
    public ContentAccessManager(
        Configuration config,
        PKIUtility pki,
        X509V3ExtensionUtil v3extensionUtil,
        ContentAccessCertificateCurator contentAccessCertificateCurator,
        KeyPairCurator keyPairCurator,
        CertificateSerialCurator serialCurator,
        OwnerCurator ownerCurator,
        OwnerContentCurator ownerContentCurator,
        OwnerEnvContentAccessCurator ownerEnvContentAccessCurator,
        ConsumerCurator consumerCurator,
        ConsumerTypeCurator consumerTypeCurator,
        EnvironmentCurator environmentCurator,
        ContentAccessCertificateCurator contentAccessCertCurator,
        EventSink eventSink) {

        this.config = Objects.requireNonNull(config);
        this.pki = Objects.requireNonNull(pki);
        this.contentAccessCertificateCurator = Objects.requireNonNull(contentAccessCertificateCurator);
        this.keyPairCurator = Objects.requireNonNull(keyPairCurator);
        this.serialCurator = Objects.requireNonNull(serialCurator);
        this.v3extensionUtil = Objects.requireNonNull(v3extensionUtil);
        this.ownerCurator = Objects.requireNonNull(ownerCurator);
        this.ownerContentCurator = Objects.requireNonNull(ownerContentCurator);
        this.ownerEnvContentAccessCurator = Objects.requireNonNull(ownerEnvContentAccessCurator);
        this.consumerCurator = Objects.requireNonNull(consumerCurator);
        this.consumerTypeCurator = Objects.requireNonNull(consumerTypeCurator);
        this.environmentCurator = Objects.requireNonNull(environmentCurator);
        this.contentAccessCertCurator = Objects.requireNonNull(contentAccessCertCurator);
        this.eventSink = Objects.requireNonNull(eventSink);
        this.standalone = this.config.getBoolean(ConfigProperties.STANDALONE, true);
    }

    /**
     * Generate an entitlement certificate, used to grant access to some content.
     * End date specified explicitly to allow for flexible termination policies.
     *
     * @return Client entitlement certificates.
     * @throws IOException thrown if there's a problem reading the cert.
     * @throws GeneralSecurityException thrown security problem
     */
    @Transactional
    public ContentAccessCertificate getCertificate(Consumer consumer)
        throws GeneralSecurityException, IOException {

        // TODO: FIXME: Redesign all of this.

        Owner owner = consumer.getOwner();

        // Ensure the org is in SCA mode and the consumer is able to process the cert we'll be
        // generating for them.
        if (owner == null || !owner.isUsingSimpleContentAccess() || !this.consumerIsCertV3Capable(consumer)) {
            return null;
        }

        Environment env = this.environmentCurator.getConsumerEnvironment(consumer);

        ContentAccessCertificate existing = consumer.getContentAccessCert();
        OwnerEnvContentAccess oeca = this.ownerEnvContentAccessCurator
            .getContentAccess(owner.getId(), env == null ? null : env.getId());

        StringBuilder pem = new StringBuilder();
        boolean regenerate = false;

        // If the cert was last updated before the org's last content update, or the cert has expired,
        // or the payload has changed, regenerate the header.
        // TODO: we should probably just store this all in one thing rather than two things.
        if (existing != null) {
            Date now = new Date();
            Date expiration = existing.getSerial().getExpiration();
            Date contentUpdate = owner.getLastContentUpdate();

            if (expiration.before(now) || !contentUpdate.before(existing.getUpdated()) ||
                oeca == null || !contentUpdate.before(oeca.getUpdated())) {

                consumer.setContentAccessCert(null);
                this.contentAccessCertificateCurator.delete(existing);

                regenerate = true;
            }
        }

        if (existing == null || regenerate) {
            log.info("Generating new SCA certificate for organization \"{}\"...", owner.getKey());

            Calendar cal = Calendar.getInstance();
            cal.add(Calendar.HOUR, -1);
            Date startDate = cal.getTime();
            cal.add(Calendar.YEAR, 1);
            Date endDate = cal.getTime();

            CertificateSerial serial = new CertificateSerial(endDate);
            // We need the sequence generated id before we create the Certificate,
            // otherwise we could have used cascading create
            serialCurator.create(serial);

            KeyPair keyPair = this.keyPairCurator.getConsumerKeyPair(consumer);
            byte[] pemEncodedKeyPair = pki.getPemEncoded(keyPair.getPrivate());

            X509Certificate x509Cert = this.createX509Certificate(consumer, owner,
                BigInteger.valueOf(serial.getId()), keyPair, startDate, endDate);

            existing = new ContentAccessCertificate();
            existing.setSerial(serial);
            existing.setKeyAsBytes(pemEncodedKeyPair);
            existing.setConsumer(consumer);

            pem.append(new String(this.pki.getPemEncoded(x509Cert)));
            existing.setCert(pem.toString());
            consumer.setContentAccessCert(existing);

            existing = this.contentAccessCertificateCurator.create(existing);
            consumer = this.consumerCurator.merge(consumer);
        }
        else {
            pem.append(existing.getCert());
        }

        // Generate the cert body/payload as necessary
        if (oeca == null || regenerate) {
            log.info("Generating new SCA payload for organization \"{}\"...", owner.getKey());

            String contentJson = this.createPayloadAndSignature(owner, env);
            oeca = new OwnerEnvContentAccess(owner, env, contentJson);
            oeca = this.ownerEnvContentAccessCurator.saveOrUpdate(oeca);
        }

        pem.append(oeca.getContentJson());

        // Impl note:
        // We want our cert to represent the correct dates so the question of "when was this last
        // updated?" can be answered as accurately as possible. We're also exploiting the fact that
        // our created dates for a given DB entity will always be before the updated dates (assuming
        // no one is messing with dates maliciously).
        Date created = existing.getCreated().before(oeca.getCreated()) ? existing.getCreated() :
            oeca.getCreated();
        Date updated = existing.getUpdated().after(oeca.getUpdated()) ? existing.getUpdated() :
            oeca.getUpdated();

        ContentAccessCertificate result = new ContentAccessCertificate();
        result.setCert(pem.toString());
        result.setCreated(created);
        result.setUpdated(updated);
        result.setId(existing.getId());
        result.setConsumer(existing.getConsumer());
        result.setKey(existing.getKey());
        result.setSerial(existing.getSerial());

        return result;
    }

    private String createPayloadAndSignature(Owner owner, Environment environment)
        throws IOException {

        byte[] payloadBytes = createContentAccessDataPayload(owner, environment);

        String payload = "-----BEGIN ENTITLEMENT DATA-----\n";
        payload += Util.toBase64(payloadBytes);
        payload += "-----END ENTITLEMENT DATA-----\n";

        byte[] bytes = pki.getSHA256WithRSAHash(new ByteArrayInputStream(payloadBytes));
        String signature = "-----BEGIN RSA SIGNATURE-----\n";
        signature += Util.toBase64(bytes);
        signature += "-----END RSA SIGNATURE-----\n";
        return payload + signature;
    }

    private X509Certificate createX509Certificate(Consumer consumer, Owner owner, BigInteger serialNumber,
        KeyPair keyPair, Date startDate, Date endDate)
        throws GeneralSecurityException, IOException {

        // fake a product dto as a container for the org content
        org.candlepin.model.dto.Product container = new org.candlepin.model.dto.Product();
        org.candlepin.model.dto.Content dContent = new org.candlepin.model.dto.Content();
        List<org.candlepin.model.dto.Content> dtoContents = new ArrayList<>();
        dtoContents.add(dContent);

        Environment environment = this.environmentCurator.getConsumerEnvironment(consumer);
        dContent.setPath(this.getContainerContentPath(owner, environment));

        container.setContent(dtoContents);

        Set<X509ExtensionWrapper> extensions = prepareV3Extensions();
        Set<X509ByteExtensionWrapper> byteExtensions = prepareV3ByteExtensions(container);

        X509Certificate x509Cert =  this.pki.createX509Certificate(
            createDN(consumer, owner), extensions, byteExtensions, startDate,
            endDate, keyPair, serialNumber, null);

        return x509Cert;
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
        String entitlementVersion = consumer.getFact("system.certificate_version");
        return entitlementVersion != null && entitlementVersion.startsWith("3.");
    }

    private Map<String, EnvironmentContent> getPromotedContent(Environment environment) {
        // Build a set of all content IDs promoted to the consumer's environment so
        // we can determine if anything needs to be skipped:
        Map<String, EnvironmentContent> promotedContent = new HashMap<>();
        if (environment != null) {
            log.debug("Consumer has an environment, checking for promoted content in: {}", environment);

            for (EnvironmentContent envContent : environment.getEnvironmentContent()) {
                log.debug("  promoted content: {}", envContent.getContent().getId());
                promotedContent.put(envContent.getContent().getId(), envContent);
            }
        }

        return promotedContent;
    }

    /**
     * Fetches the path to use for content used to generate the ENTITLEMENT_DATA certificate
     * extension
     *
     * @param owner
     *  the owner of the entitlement for which the content path is being generated
     *
     * @param environment
     *  the environment to which the consumer receiving the entitlement belongs
     *
     * @return
     *  the container content path for the given owner and environment
     */
    private String getContainerContentPath(Owner owner, Environment environment) throws IOException {
        // Fix for BZ 1866525:
        // - In hosted, SCA entitlement content path needs to use a dummy value to prevent existing
        //   clients from breaking, while still being clear the path is present for SCA
        // - In satellite (standalone), the path should simply be the content prefix

        return this.standalone ?
            this.getContentPrefix(owner, environment) :
            "/sca/" + URLEncoder.encode(owner.getKey(), StandardCharsets.UTF_8.toString());
    }

    /**
     * Fetches the content prefix to apply to content specified in the entitlement payload.
     *
     * @param owner
     *  the owner of the entitlement for which the content prefix is being generated
     *
     * @param environment
     *  the environment to which the consumer receiving the entitlement belongs
     *
     * @return
     *  the content prefix for the given owner and environment
     */
    private String getContentPrefix(Owner owner, Environment environment) throws IOException {
        StringBuilder prefix = new StringBuilder();

        // Fix for BZ 1866525:
        // - In hosted, SCA entitlement content paths should not have any prefix
        // - In satellite (standalone), the prefix should use the owner key and environment name
        //   if available

        if (this.standalone) {
            String charset =  StandardCharsets.UTF_8.toString();

            prefix.append('/').append(URLEncoder.encode(owner.getKey(), charset));

            if (environment != null) {
                for (String chunk : environment.getName().split("/")) {
                    if (!chunk.isEmpty()) {
                        prefix.append("/");
                        prefix.append(URLEncoder.encode(chunk, charset));
                    }
                }
            }
        }

        return prefix.toString();
    }

    private String createDN(Consumer consumer, Owner owner) {
        StringBuilder sb = new StringBuilder("CN=")
            .append(consumer.getUuid())
            .append(", O=")
            .append(Rdn.escapeValue(owner.getKey()));

        if (consumer.getEnvironmentId() != null) {
            Environment environment = this.environmentCurator.getConsumerEnvironment(consumer);
            sb.append(", OU=").append(Rdn.escapeValue(environment.getName()));
        }

        return sb.toString();
    }

    private Set<X509ExtensionWrapper> prepareV3Extensions() {
        Set<X509ExtensionWrapper> result = v3extensionUtil.getExtensions();
        X509ExtensionWrapper typeExtension = new X509ExtensionWrapper(OIDUtil.REDHAT_OID + "." +
            OIDUtil.TOPLEVEL_NAMESPACES.get(OIDUtil.ENTITLEMENT_TYPE_KEY), false, "OrgLevel");

        result.add(typeExtension);
        return result;
    }

    private Set<X509ByteExtensionWrapper> prepareV3ByteExtensions(org.candlepin.model.dto.Product container)
        throws IOException {
        List<org.candlepin.model.dto.Product> products = new ArrayList<>();
        products.add(container);
        Set<X509ByteExtensionWrapper> result = v3extensionUtil.getByteExtensions(null, products, null,  null);
        return result;
    }

    private byte[] createContentAccessDataPayload(Owner owner, Environment environment) throws IOException {
        // fake a product dto as a container for the org content
        Set<Product> containerSet = new HashSet<>();
        Set<String> entitledProductIds = new HashSet<>();
        List<org.candlepin.model.dto.Product> productModels = new ArrayList<>();
        Map<String, EnvironmentContent> promotedContent = getPromotedContent(environment);
        String contentPrefix = this.getContentPrefix(owner, environment);
        Product container = new Product();
        Entitlement emptyEnt = new Entitlement();
        Pool emptyPool = new Pool();
        Product skuProduct = new Product();
        Consumer emptyConsumer = new Consumer();
        emptyConsumer.setOwner(owner);

        containerSet.add(container);
        container.setId("content_access");
        container.setName(" Content Access");

        this.ownerContentCurator.getActiveContentByOwner(owner.getId())
            .forEach((content, enabled) -> container.addContent(content, enabled));

        emptyConsumer.setEnvironment(environment);
        emptyEnt.setPool(emptyPool);
        emptyEnt.setConsumer(emptyConsumer);
        emptyPool.setProduct(skuProduct);
        emptyPool.setStartDate(new Date());
        emptyPool.setEndDate(new Date());
        skuProduct.setName("Content Access");
        skuProduct.setId("content_access");
        entitledProductIds.add("content-access");

        org.candlepin.model.dto.Product productModel = v3extensionUtil.mapProduct(container, skuProduct,
            contentPrefix, promotedContent, emptyConsumer, emptyPool, entitledProductIds);

        productModels.add(productModel);

        return v3extensionUtil.createEntitlementDataPayload(productModels,
            emptyConsumer, emptyPool, null);
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
            if (cert == null || date.before(cert.getUpdated()) ||
                date.after(cert.getSerial().getExpiration())) {
                return true;
            }

            // Check payload
            Environment env = this.environmentCurator.getConsumerEnvironment(consumer);
            OwnerEnvContentAccess oeca = this.ownerEnvContentAccessCurator
                .getContentAccess(consumer.getOwnerId(), env == null ? null : env.getId());

            if (oeca == null || date.before(oeca.getUpdated())) {
                return true;
            }
        }

        return false;
    }

    @Transactional
    public void removeContentAccessCert(Consumer consumer) {
        if (consumer.getContentAccessCert() == null) {
            return;
        }

        this.contentAccessCertificateCurator.delete(consumer.getContentAccessCert());
        consumer.setContentAccessCert(null);
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
        String currentList = this.resolveContentAccessValue(owner.getContentAccessModeList(), true);
        String currentMode = this.resolveContentAccessValue(owner.getContentAccessMode(), true);

        // Resolve the updated list and mode
        updatedList = this.resolveContentAccessValue(updatedList, false);
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

            this.refreshOwnerForContentAccess(owner);
            this.eventSink.emitOwnerContentAccessModeChanged(owner);
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
     * Refreshes the content access certificates for the given owner.
     *
     * @param owner
     *  The owner for which to refresh content access
     *
     * @return
     *  the owner provided
     */
    @Transactional
    public Owner refreshOwnerForContentAccess(Owner owner) {
        if (owner == null) {
            throw new IllegalArgumentException("owner is null");
        }

        // we need to update the owner's consumers if the content access mode has changed
        this.ownerCurator.lock(owner);

        if (!owner.isUsingSimpleContentAccess()) {
            this.contentAccessCertCurator.deleteForOwner(owner);
        }

        // removed cached versions of content access cert data
        this.ownerEnvContentAccessCurator.removeAllForOwner(owner.getId());
        this.ownerCurator.flush();

        return owner;
    }

    /**
     * Invalidates the SCA content body for the given environment
     *
     * @param environment
     *  the environment for which to refresh content access certificates
     *
     * @throws IllegalArgumentException
     *  if environment is null
     *
     * @return
     *  the environment provided
     */
    @Transactional
    public Environment refreshEnvironmentContentAccessCerts(Environment environment) {
        if (environment == null) {
            throw new IllegalArgumentException("environment is null");
        }

        this.ownerEnvContentAccessCurator.removeAllForEnvironment(environment.getId());
        return environment;
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

}
