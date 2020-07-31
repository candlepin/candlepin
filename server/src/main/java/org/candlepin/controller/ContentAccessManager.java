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
import org.candlepin.model.CandlepinQuery;
import org.candlepin.model.CertificateSerial;
import org.candlepin.model.CertificateSerialCurator;
import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerCapability;
import org.candlepin.model.ConsumerCurator;
import org.candlepin.model.ConsumerType;
import org.candlepin.model.ConsumerType.ConsumerTypeEnum;
import org.candlepin.model.ConsumerTypeCurator;
import org.candlepin.model.Content;
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

import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigInteger;
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



/**
 * The ContentAccessManager provides management operations for organization and consumer level
 * content access modes.
 */
@Component
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

    private PKIUtility pki;
    private KeyPairCurator keyPairCurator;
    private CertificateSerialCurator serialCurator;
    private OwnerContentCurator ownerContentCurator;
    private OwnerCurator ownerCurator;
    private ContentAccessCertificateCurator contentAccessCertificateCurator;
    private X509V3ExtensionUtil v3extensionUtil;
    private OwnerEnvContentAccessCurator ownerEnvContentAccessCurator;
    private ConsumerCurator consumerCurator;
    private ConsumerTypeCurator consumerTypeCurator;
    private EnvironmentCurator environmentCurator;
    private ContentAccessCertificateCurator contentAccessCertCurator;
    private EventSink eventSink;

    //@Inject
    @Autowired
    public ContentAccessManager(PKIUtility pki,
        X509V3ExtensionUtil v3extensionUtil,
        ContentAccessCertificateCurator contentAccessCertificateCurator,
        KeyPairCurator keyPairCurator,
        CertificateSerialCurator serialCurator,
        OwnerContentCurator ownerContentCurator,
        OwnerCurator ownerCurator,
        OwnerEnvContentAccessCurator ownerEnvContentAccessCurator,
        ConsumerCurator consumerCurator,
        ConsumerTypeCurator consumerTypeCurator,
        EnvironmentCurator environmentCurator,
        ContentAccessCertificateCurator contentAccessCertCurator,
        EventSink eventSink) {

        this.pki = Objects.requireNonNull(pki);
        this.contentAccessCertificateCurator = Objects.requireNonNull(contentAccessCertificateCurator);
        this.keyPairCurator = Objects.requireNonNull(keyPairCurator);
        this.serialCurator = Objects.requireNonNull(serialCurator);
        this.v3extensionUtil = Objects.requireNonNull(v3extensionUtil);
        this.ownerContentCurator = Objects.requireNonNull(ownerContentCurator);
        this.ownerCurator = Objects.requireNonNull(ownerCurator);
        this.ownerEnvContentAccessCurator = Objects.requireNonNull(ownerEnvContentAccessCurator);
        this.consumerCurator = Objects.requireNonNull(consumerCurator);
        this.consumerTypeCurator = Objects.requireNonNull(consumerTypeCurator);
        this.environmentCurator = Objects.requireNonNull(environmentCurator);
        this.contentAccessCertCurator = Objects.requireNonNull(contentAccessCertCurator);
        this.eventSink = Objects.requireNonNull(eventSink);
    }

    /**
     * Generate an entitlement certificate, used to grant access to some
     * content.
     * End date specified explicitly to allow for flexible termination policies.
     *
     * @return Client entitlement certificates.
     * @throws IOException thrown if there's a problem reading the cert.
     * @throws GeneralSecurityException thrown security problem
     */
    @Transactional
    public ContentAccessCertificate getCertificate(Consumer consumer)
        throws GeneralSecurityException, IOException {

        Owner owner = ownerCurator.findOwnerById(consumer.getOwnerId());
        // we only know about one mode right now. If add any, we will need to add the
        // appropriate cert generation
        if (!owner.isContentAccessEnabled() ||
            !this.consumerIsCertV3Capable(consumer)) {

            return null;
        }

        ContentAccessCertificate existing = consumer.getContentAccessCert();
        ContentAccessCertificate result = new ContentAccessCertificate();
        String pem = "";

        if (existing != null &&
            existing.getSerial().getExpiration().getTime() < (new Date()).getTime()) {
            consumer.setContentAccessCert(null);
            contentAccessCertificateCurator.delete(existing);
            existing = null;
        }

        if (existing == null) {
            Calendar cal = Calendar.getInstance();
            cal.add(Calendar.HOUR, -1);
            Date startDate = cal.getTime();
            cal.add(Calendar.YEAR, 1);
            Date endDate = cal.getTime();

            CertificateSerial serial = new CertificateSerial(endDate);
            // We need the sequence generated id before we create the Certificate,
            // otherwise we could have used cascading create
            serialCurator.create(serial);

            KeyPair keyPair = keyPairCurator.getConsumerKeyPair(consumer);
            byte[] pemEncodedKeyPair = pki.getPemEncoded(keyPair.getPrivate());

            X509Certificate x509Cert = createX509Certificate(consumer, owner,
                BigInteger.valueOf(serial.getId()), keyPair, startDate, endDate);

            existing = new ContentAccessCertificate();
            existing.setSerial(serial);
            existing.setKeyAsBytes(pemEncodedKeyPair);
            existing.setConsumer(consumer);

            log.info("Setting PEM encoded cert.");
            pem = new String(this.pki.getPemEncoded(x509Cert));
            existing.setCert(pem);
            consumer.setContentAccessCert(existing);
            contentAccessCertificateCurator.create(existing);
            consumer = consumerCurator.merge(consumer);
        }
        else {
            pem = existing.getCert();
        }

        Environment env = this.environmentCurator.getConsumerEnvironment(consumer);

        // we need to see if this is newer than the previous result
        OwnerEnvContentAccess oeca = ownerEnvContentAccessCurator
            .getContentAccess(owner.getId(), env == null ? null : env.getId());

        if (oeca == null) {
            String contentJson = createPayloadAndSignature(owner, env);
            oeca = new OwnerEnvContentAccess(owner, env, contentJson);
            oeca = ownerEnvContentAccessCurator.saveOrUpdate(oeca);
        }

        pem += oeca.getContentJson();

        result.setCert(pem);
        result.setCreated(existing.getCreated());
        result.setUpdated(existing.getUpdated());
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
        dContent.setPath(getContentPrefix(owner, environment));

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

    private String getContentPrefix(Owner owner, Environment environment) throws IOException {
        StringBuffer contentPrefix = new StringBuffer();
        contentPrefix.append("/");
        contentPrefix.append(owner.getKey());
        if (environment != null) {
            contentPrefix.append("/");
            contentPrefix.append(environment.getName());
        }
        return contentPrefix.toString();
    }

    private String createDN(Consumer consumer, Owner owner) {
        StringBuilder sb = new StringBuilder("CN=");
        sb.append(consumer.getUuid());
        sb.append(", O=");
        sb.append(owner.getKey());

        if (consumer.getEnvironmentId() != null) {
            Environment environment = this.environmentCurator.getConsumerEnvironment(consumer);
            sb.append(", OU=");
            sb.append(environment.getName());
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
        CandlepinQuery<Content> ownerContent = ownerContentCurator.getContentByOwner(owner);
        Set<String> entitledProductIds = new HashSet<>();
        List<org.candlepin.model.dto.Product> productModels = new ArrayList<>();
        Map<String, EnvironmentContent> promotedContent = getPromotedContent(environment);
        String contentPrefix = getContentPrefix(owner, environment);
        Product container = new Product();
        Entitlement emptyEnt = new Entitlement();
        Pool emptyPool = new Pool();
        Product skuProduct = new Product();
        Consumer emptyConsumer = new Consumer();
        emptyConsumer.setOwner(owner);

        containerSet.add(container);
        container.setId("content_access");
        container.setName(" Content Access");
        for (Content c : ownerContent) {
            container.addContent(c, false);
        }

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

    public boolean hasCertChangedSince(Consumer consumer, Date date) {
        if (date == null) {
            return true;
        }

        Environment env = this.environmentCurator.getConsumerEnvironment(consumer);
        OwnerEnvContentAccess oeca = ownerEnvContentAccessCurator
            .getContentAccess(consumer.getOwnerId(), env == null ? null : env.getId());

        return oeca == null ||
            consumer.getContentAccessCert() == null ||
            oeca.getUpdated().getTime() > date.getTime();
    }

    @Transactional
    public void removeContentAccessCert(Consumer consumer) {
        if (consumer.getContentAccessCert() == null) {
            return;
        }
        contentAccessCertificateCurator.delete(consumer.getContentAccessCert());
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
     */
    @Transactional
    public void refreshOwnerForContentAccess(Owner owner) {
        // we need to update the owner's consumers if the content access mode has changed
        this.ownerCurator.lock(owner);

        if (!owner.isContentAccessEnabled()) {
            this.contentAccessCertCurator.deleteForOwner(owner);
        }

        // removed cached versions of content access cert data
        this.ownerEnvContentAccessCurator.removeAllForOwner(owner.getId());
        ownerCurator.flush();
    }

}
