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
import org.candlepin.config.ConfigProperties;
import org.candlepin.config.Configuration;
import org.candlepin.model.AnonymousCloudConsumer;
import org.candlepin.model.AnonymousContentAccessCertificate;
import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerCapability;
import org.candlepin.model.ConsumerCurator;
import org.candlepin.model.ConsumerType;
import org.candlepin.model.ConsumerType.ConsumerTypeEnum;
import org.candlepin.model.ConsumerTypeCurator;
import org.candlepin.model.ContentAccessCertificate;
import org.candlepin.model.ContentAccessCertificateCurator;
import org.candlepin.model.Owner;
import org.candlepin.model.OwnerCurator;
import org.candlepin.pki.CertificateCreationException;
import org.candlepin.pki.certs.AnonymousCertificateGenerator;
import org.candlepin.pki.certs.ContentAccessCertificateGenerator;
import org.candlepin.util.Util;

import com.google.inject.persist.Transactional;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Date;
import java.util.Objects;

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

    private final OwnerCurator ownerCurator;
    private final ContentAccessCertificateCurator contentAccessCertificateCurator;
    private final ConsumerCurator consumerCurator;
    private final ConsumerTypeCurator consumerTypeCurator;
    private final EventSink eventSink;
    private final ContentAccessCertificateGenerator contentAccessCertificateGenerator;
    private final AnonymousCertificateGenerator anonymousCertificateGenerator;

    private final boolean standalone;

    @Inject
    public ContentAccessManager(
        Configuration config,
        ContentAccessCertificateCurator contentAccessCertificateCurator,
        OwnerCurator ownerCurator,
        ConsumerCurator consumerCurator,
        ConsumerTypeCurator consumerTypeCurator,
        EventSink eventSink,
        ContentAccessCertificateGenerator contentAccessCertificateGenerator,
        AnonymousCertificateGenerator anonymousCertificateGenerator) {

        this.contentAccessCertificateCurator = Objects.requireNonNull(contentAccessCertificateCurator);
        this.ownerCurator = Objects.requireNonNull(ownerCurator);
        this.consumerCurator = Objects.requireNonNull(consumerCurator);
        this.consumerTypeCurator = Objects.requireNonNull(consumerTypeCurator);
        this.eventSink = Objects.requireNonNull(eventSink);
        this.contentAccessCertificateGenerator = Objects.requireNonNull(contentAccessCertificateGenerator);
        this.anonymousCertificateGenerator = Objects.requireNonNull(anonymousCertificateGenerator);
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
                .execute(args -> this.contentAccessCertificateGenerator.generate(consumer, owner));

            return this.wrap(result);
        }
        catch (Exception e) {
            log.error("Unexpected exception occurred while fetching SCA certificate for consumer: {}",
                consumer, e);
        }

        return null;
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
                this.contentAccessCertificateCurator.deleteForOwner(owner);
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
     *  the retrieved or generated certificate
     */
    @Transactional
    public AnonymousContentAccessCertificate getCertificate(AnonymousCloudConsumer consumer)
        throws GeneralSecurityException, IOException {
        if (standalone) {
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

        return this.anonymousCertificateGenerator.createCertificate(consumer);
    }

}
