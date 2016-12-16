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
import org.candlepin.model.Content;
import org.candlepin.model.ContentAccessCertificateCurator;
import org.candlepin.model.Environment;
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
import org.candlepin.model.activationkeys.ActivationKey;
import org.candlepin.model.activationkeys.ActivationKeyCurator;
import org.candlepin.service.ContentAccessCertServiceAdapter;

import com.google.inject.Inject;
import com.google.inject.persist.Transactional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;



/**
 * Used to perform operations on Owners that need more than just the owner
 * curator.
 */
public class OwnerManager {

    @Inject private static Logger log = LoggerFactory.getLogger(OwnerManager.class);
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

    @Transactional
    public void cleanupAndDelete(Owner owner, boolean revokeCerts) {
        log.info("Cleaning up owner: {}", owner);

        List<String> ids = ownerCurator.getConsumerUuids(owner.getKey()).list();
        List<Consumer> consumers = consumerCurator.lockAndLoadBatch(ids);

        for (Consumer c : consumers) {
            log.info("Removing all entitlements for consumer: {}", c);

            poolManager.revokeAllEntitlements(c, revokeCerts);
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
        for (Environment e : owner.getEnvironments()) {
            log.info("Deleting environment: {}", e.getId());
            envCurator.delete(e);
        }

        for (Pool p : poolManager.listPoolsByOwner(owner)) {
            log.info("Deleting pool: {}", p);
            poolManager.deletePool(p);
        }

        /*
         * The pool created when generating a uebercert do not appear in the
         * normal list of pools for that owner, and so do not get cleaned up by
         * the normal operations. Instead we must check if they exist and
         * explicitly delete them.
         */
        Pool ueberPool = poolManager.findUeberPool(owner);
        if (ueberPool != null) {
            poolManager.deletePool(ueberPool);
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
    }

    public void determineContentAccessCertState(Owner owner) {
        if (!owner.isContentAccessModeDirty()) {
            return;
        }
        if (owner.contentAccessMode()
            .equals(ContentAccessCertServiceAdapter.DEFAULT_CONTENT_ACCESS_MODE)) {
            contentAccessCertCurator.deleteForOwner(owner);
        }
        owner.setContentAccessModeDirty(false);
        ownerCurator.merge(owner);
    }

    @Transactional
    public void refreshOwnerForContentAccess(Owner owner) {
        // we need to update the owner's consumers if the content access mode has changed
        owner = ownerCurator.findAndLock(owner.getKey());
        this.determineContentAccessCertState(owner);
        // removed cached versions of content access cert data
        ownerEnvContentAccessCurator.removeAllForOwner(owner.getId());
        ownerCurator.flush();
    }
}
