/**
 * Copyright (c) 2009 - 2012 Red Hat, Inc.
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

import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerCurator;
import org.candlepin.model.ContentAccessCertificateCurator;
import org.candlepin.model.EnvironmentCurator;
import org.candlepin.model.ExporterMetadata;
import org.candlepin.model.ExporterMetadataCurator;
import org.candlepin.model.ImportRecord;
import org.candlepin.model.ImportRecordCurator;
import org.candlepin.model.Owner;
import org.candlepin.model.OwnerContentCurator;
import org.candlepin.model.OwnerCurator;
import org.candlepin.model.OwnerEnvContentAccessCurator;
import org.candlepin.model.OwnerProductCurator;
import org.candlepin.model.PermissionBlueprint;
import org.candlepin.model.PermissionBlueprintCurator;
import org.candlepin.model.Pool;
import org.candlepin.model.UeberCertificateCurator;
import org.candlepin.model.activationkeys.ActivationKey;
import org.candlepin.model.activationkeys.ActivationKeyCurator;
import org.candlepin.service.ContentAccessCertServiceAdapter;
import org.candlepin.service.OwnerServiceAdapter;

import com.google.inject.Inject;
import com.google.inject.persist.Transactional;

import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang3.StringUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;



/**
 * Used to perform operations on Owners that need more than just the owner
 * curator.
 */
public class OwnerManager {

    private static Logger log = LoggerFactory.getLogger(OwnerManager.class);

    @Inject private ConsumerCurator consumerCurator;
    @Inject private PoolManager poolManager;
    @Inject private ActivationKeyCurator activationKeyCurator;
    @Inject private EnvironmentCurator envCurator;
    @Inject private ExporterMetadataCurator exportCurator;
    @Inject private ImportRecordCurator importRecordCurator;
    @Inject private PermissionBlueprintCurator permissionCurator;
    @Inject private OwnerProductCurator ownerProductCurator;
    @Inject private ProductManager productManager;
    @Inject private OwnerContentCurator ownerContentCurator;
    @Inject private ContentManager contentManager;
    @Inject private OwnerCurator ownerCurator;
    @Inject private ContentAccessCertServiceAdapter contentAccessCertService;
    @Inject private ContentAccessCertificateCurator contentAccessCertCurator;
    @Inject private OwnerEnvContentAccessCurator ownerEnvContentAccessCurator;
    @Inject private UeberCertificateCurator uberCertificateCurator;
    @Inject private OwnerServiceAdapter ownerServiceAdapter;

    @Transactional
    public void cleanupAndDelete(Owner owner, boolean revokeCerts) {
        log.info("Cleaning up owner: {}", owner);

        Collection<String> consumerIds = this.ownerCurator.getConsumerIds(owner).list();
        Collection<Consumer> consumers = this.consumerCurator.lockAndLoadByIds(consumerIds);

        for (Consumer consumer : consumers) {
            log.info("Removing all entitlements for consumer: {}", consumer);
            poolManager.revokeAllEntitlements(consumer, revokeCerts);
        }

        // Actual consumer deletion is done out of the loop above since all
        // entitlements need to be removed before the deletion occurs. This
        // is due to the sourceConsumer that was added to Pool. Deleting an
        // entitlement may result in the deletion of a sub pool, which would
        // cause issues.
        // FIXME Perhaps this can be handled a little better.
        for (Consumer consumer : consumers) {
            // need to check if this has been removed due to a
            // parent being deleted
            // TODO: There has to be a more efficient way to do this...
            log.info("Deleting consumer: {}", consumer);
            Consumer next = consumerCurator.find(consumer.getId());
            if (next != null) {
                consumerCurator.delete(next);
            }
        }

        for (ActivationKey key : activationKeyCurator.listByOwner(owner)) {
            log.info("Deleting activation key: {}", key);
            activationKeyCurator.delete(key);
        }

        log.debug("Deleting environments for owner: {}", owner);
        envCurator.deleteEnvironmentsForOwner(owner);

        // Delete the ueber certificate for this owner, if one exists.
        log.debug("Deleting uber certificate for owner: {}", owner);
        this.uberCertificateCurator.deleteForOwner(owner);

        for (Pool p : poolManager.listPoolsByOwner(owner)) {
            log.info("Deleting pool: {}", p);
            poolManager.deletePool(p);
        }

        ExporterMetadata m = exportCurator.lookupByTypeAndOwner(ExporterMetadata.TYPE_PER_USER, owner);
        if (m != null) {
            log.info("Deleting export metadata: {}", m);
            exportCurator.delete(m);
        }

        for (ImportRecord record : importRecordCurator.findRecords(owner)) {
            log.info("Deleting import record:  {}", record);
            importRecordCurator.delete(record);
        }

        for (PermissionBlueprint perm : permissionCurator.findByOwner(owner)) {
            log.info("Deleting permission: {}", perm.getAccess());
            perm.getRole().getPermissions().remove(perm);
            permissionCurator.delete(perm);
        }

        log.info("Deleting all products...");
        this.productManager.removeAllProducts(owner);

        log.info("Deleting all content...");
        this.contentManager.removeAllContent(owner, false);

        log.info("Deleting owner: {}", owner);
        ownerCurator.delete(owner);

        ownerCurator.flush();
    }

    /**
     * Refreshes the content access mode and content access mode list for the given owner using the
     * default owner service adapter.
     *
     * @param owner
     *  The owner to refresh
     *
     * @throws IllegalArgumentException
     *  if adapter is null or owner is null or invalid
     */
    public void refreshContentAccessMode(Owner owner) {
        this.refreshContentAccessMode(this.ownerServiceAdapter, owner);
    }

    /**
     * Refreshes the content access mode and content access mode list for the given owner using the
     * specified owner service adapter.
     *
     * @param adapter
     *  The OwnerServiceAdapter instance to use for refreshing the owner's content access
     *
     * @param owner
     *  The owner to refresh
     *
     * @throws IllegalArgumentException
     *  if adapter is null or owner is null or invalid
     */
    @Transactional
    public void refreshContentAccessMode(OwnerServiceAdapter adapter, Owner owner) {
        if (adapter == null) {
            throw new IllegalArgumentException("adapter is null");
        }

        if (owner == null) {
            throw new IllegalArgumentException("owner is null");
        }

        // Lock the owner
        owner = ownerCurator.lockAndLoad(owner);

        // Fetch the upstream list and mode
        String upstreamList = adapter.getContentAccessModeList(owner.getKey());
        String upstreamMode = adapter.getContentAccessMode(owner.getKey());
        String currentMode = owner.getContentAccessMode();

        // This shouldn't happen, but in the event our upstream source is having issues, let's
        // not put ourselves in a bad state as well.
        if (upstreamList != null && !upstreamList.isEmpty()) {
            // Not empty list. Verify that the upstream mode is present.
            String[] list = upstreamList.split(",");

            if (!StringUtils.isEmpty(upstreamMode) && !ArrayUtils.contains(list, upstreamMode)) {
                throw new IllegalStateException("Upstream access mode is not present in access mode list");
            }
        }
        else {
            // Empty list. Verify the upstream mode is also empty.
            if (!StringUtils.isEmpty(upstreamMode)) {
                throw new IllegalStateException("Upstream access mode is not present in access mode list");
            }
        }

        // Set new values
        owner.setContentAccessModeList(upstreamList);

        // If the content access mode changed, we'll need to update it and refresh the access certs
        if (currentMode != null ? !currentMode.equals(upstreamMode) : upstreamMode != null) {
            owner.setContentAccessMode(upstreamMode);

            ownerCurator.merge(owner);
            ownerCurator.flush();

            this.refreshOwnerForContentAccess(owner);
        }
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
        owner = ownerCurator.lockAndLoad(owner);

        if (owner.contentAccessMode().equals(ContentAccessCertServiceAdapter.DEFAULT_CONTENT_ACCESS_MODE)) {
            contentAccessCertCurator.deleteForOwner(owner);
        }

        // removed cached versions of content access cert data
        ownerEnvContentAccessCurator.removeAllForOwner(owner.getId());
        ownerCurator.flush();
    }
}
