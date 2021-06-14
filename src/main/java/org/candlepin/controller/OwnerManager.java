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

import org.candlepin.audit.EventSink;
import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerCurator;
import org.candlepin.model.EnvironmentCurator;
import org.candlepin.model.ExporterMetadata;
import org.candlepin.model.ExporterMetadataCurator;
import org.candlepin.model.ImportRecord;
import org.candlepin.model.ImportRecordCurator;
import org.candlepin.model.Owner;
import org.candlepin.model.OwnerContentCurator;
import org.candlepin.model.OwnerCurator;
import org.candlepin.model.OwnerProductCurator;
import org.candlepin.model.PermissionBlueprint;
import org.candlepin.model.PermissionBlueprintCurator;
import org.candlepin.model.Pool;
import org.candlepin.model.UeberCertificateCurator;
import org.candlepin.model.activationkeys.ActivationKey;
import org.candlepin.model.activationkeys.ActivationKeyCurator;
import org.candlepin.service.OwnerServiceAdapter;

import com.google.inject.Inject;
import com.google.inject.persist.Transactional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Date;



/**
 * Used to perform operations on Owners that need more than just the owner
 * curator.
 */
public class OwnerManager {

    private static Logger log = LoggerFactory.getLogger(OwnerManager.class);

    /* PoolManager is injected separately so that we do not create a circular reference with the
       PoolManager implementation which has an injection of this class. This method causes
       the injection to occur after the constructor.
     */
    @Inject PoolManager poolManager;
    private ConsumerCurator consumerCurator;
    private ActivationKeyCurator activationKeyCurator;
    private EnvironmentCurator envCurator;
    private ExporterMetadataCurator exportCurator;
    private ImportRecordCurator importRecordCurator;
    private PermissionBlueprintCurator permissionCurator;
    private OwnerProductCurator ownerProductCurator;
    private OwnerContentCurator ownerContentCurator;
    private OwnerCurator ownerCurator;
    private UeberCertificateCurator uberCertificateCurator;
    private OwnerServiceAdapter ownerServiceAdapter;
    private EventSink sink;

    @Inject
    public OwnerManager(ConsumerCurator consumerCurator,
        ActivationKeyCurator activationKeyCurator,
        EnvironmentCurator envCurator,
        ExporterMetadataCurator exportCurator,
        ImportRecordCurator importRecordCurator,
        PermissionBlueprintCurator permissionCurator,
        OwnerProductCurator ownerProductCurator,
        OwnerContentCurator ownerContentCurator,
        OwnerCurator ownerCurator,
        UeberCertificateCurator uberCertificateCurator,
        OwnerServiceAdapter ownerServiceAdapter,
        EventSink sink) {

        this.consumerCurator = consumerCurator;
        this.activationKeyCurator = activationKeyCurator;
        this.envCurator = envCurator;
        this.exportCurator = exportCurator;
        this.importRecordCurator = importRecordCurator;
        this.permissionCurator = permissionCurator;
        this.ownerProductCurator = ownerProductCurator;
        this.ownerContentCurator = ownerContentCurator;
        this.ownerCurator = ownerCurator;
        this.uberCertificateCurator = uberCertificateCurator;
        this.ownerServiceAdapter = ownerServiceAdapter;
        this.sink = sink;
    }

    @Transactional
    public void cleanupAndDelete(Owner owner, boolean revokeCerts) {
        log.info("Cleaning up owner: {}", owner);

        Collection<String> consumerIds = this.ownerCurator.getConsumerIds(owner).list();
        Collection<Consumer> consumers = this.consumerCurator.lockAndLoad(consumerIds);

        for (Consumer consumer : consumers) {
            log.info("Removing all entitlements for consumer: {}", consumer);

            // We're about to delete these consumers; no need to regen/dirty their dependent
            // entitlements or recalculate status.
            poolManager.revokeAllEntitlements(consumer, false);
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
            Consumer next = consumerCurator.get(consumer.getId());
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

        ExporterMetadata m = exportCurator.getByTypeAndOwner(ExporterMetadata.TYPE_PER_USER, owner);
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
        this.removeAllProductsForOwner(owner);

        log.info("Deleting all content...");
        this.removeAllContentForOwner(owner);

        log.info("Deleting owner: {}", owner);
        ownerCurator.delete(owner);

        ownerCurator.flush();
    }

    public Owner updateRefreshDate(Owner owner) {
        owner.setLastRefreshed(new Date());
        return ownerCurator.merge(owner);
    }

    /**
     * Removes all known products for the given owner
     *
     * @param owner
     *  the owner for which to remove all products
     */
    private void removeAllProductsForOwner(Owner owner) {
        Collection<String> productUuids = this.ownerProductCurator.getProductUuidsByOwner(owner);
        this.ownerProductCurator.removeOwnerProductReferences(owner, productUuids);
    }

    /**
     * Removes all known content for the given owner
     *
     * @param owner
     *  the owner for which to remove all content
     */
    private void removeAllContentForOwner(Owner owner) {
        Collection<String> contentUuids = this.ownerContentCurator.getContentUuidsByOwner(owner);
        this.ownerContentCurator.removeOwnerContentReferences(owner, contentUuids);
    }
}
