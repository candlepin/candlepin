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
package org.candlepin.controller;

import org.candlepin.async.JobException;
import org.candlepin.async.JobManager;
import org.candlepin.async.tasks.RevokeEntitlementsJob.RevokeEntitlementsJobConfig;
import org.candlepin.audit.EventSink;
import org.candlepin.exceptions.IseException;
import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerCurator;
import org.candlepin.model.ContentAccessCertificateCurator;
import org.candlepin.model.Owner;
import org.candlepin.model.OwnerCurator;

import com.google.inject.Provider;
import com.google.inject.persist.Transactional;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xnap.commons.i18n.I18n;

import java.util.Objects;

import javax.inject.Inject;


/**
 * The ContentAccessManager provides management operations for organization and consumer level
 * content access modes.
 */
public class ContentAccessManager {
    private static final Logger log = LoggerFactory.getLogger(ContentAccessManager.class);

    private final OwnerCurator ownerCurator;
    private final ContentAccessCertificateCurator contentAccessCertificateCurator;
    private final ConsumerCurator consumerCurator;
    private final Provider<EventSink> eventSink;
    private final JobManager jobManager;
    private final I18n i18n;

    @Inject
    public ContentAccessManager(
        ContentAccessCertificateCurator contentAccessCertificateCurator,
        OwnerCurator ownerCurator,
        ConsumerCurator consumerCurator,
        Provider<EventSink> eventSink,
        JobManager jobManager,
        I18n i18n) {

        this.contentAccessCertificateCurator = Objects.requireNonNull(contentAccessCertificateCurator);
        this.ownerCurator = Objects.requireNonNull(ownerCurator);
        this.consumerCurator = Objects.requireNonNull(consumerCurator);
        this.eventSink = Objects.requireNonNull(eventSink);
        this.jobManager = Objects.requireNonNull(jobManager);
        this.i18n = Objects.requireNonNull(i18n);
    }

    /**
     * Fetches the default content access mode list database value
     *
     * @return the default content access mode list database value as a string
     */
    public static String defaultContentAccessModeList() {
        return ContentAccessMode.ORG_ENVIRONMENT.toDatabaseValue();
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

            // Revoke entitlements and remove activation key pools if moving from entitlement mode to SCA mode
            if (this.isTransitioningFrom(currentMode, updatedMode, ContentAccessMode.ENTITLEMENT)) {
                try {
                    RevokeEntitlementsJobConfig jobConfig = new RevokeEntitlementsJobConfig();
                    jobConfig.setOwner(owner);
                    jobManager.queueJob(jobConfig);
                }
                catch (JobException e) {
                    throw new IseException(i18n.tr("Unable to create revoke entitlements job"), e);
                }
            }

            // Update sync times & report
            owner.syncLastContentUpdate();
            this.eventSink.get().emitOwnerContentAccessModeChanged(owner);

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
            return resolveNull ? defaultContentAccessModeList() : null;
        }

        if (value.isEmpty()) {
            return defaultContentAccessModeList();
        }

        return value;
    }

}
