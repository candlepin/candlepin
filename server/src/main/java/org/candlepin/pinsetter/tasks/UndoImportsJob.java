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
package org.candlepin.pinsetter.tasks;

import static org.quartz.JobBuilder.newJob;

import org.candlepin.auth.Principal;
import org.candlepin.controller.PoolManager;
import org.candlepin.model.ExporterMetadata;
import org.candlepin.model.ExporterMetadataCurator;
import org.candlepin.model.ImportRecord;
import org.candlepin.model.ImportRecordCurator;
import org.candlepin.model.ImportUpstreamConsumer;
import org.candlepin.model.Owner;
import org.candlepin.model.OwnerCurator;
import org.candlepin.model.Pool;
import org.candlepin.model.UpstreamConsumer;
import org.candlepin.pinsetter.core.PinsetterJobListener;
import org.candlepin.pinsetter.core.RetryJobException;
import org.candlepin.pinsetter.core.model.JobStatus;
import org.candlepin.service.SubscriptionServiceAdapter;
import org.candlepin.util.Util;

import org.quartz.JobDataMap;
import org.quartz.JobDetail;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xnap.commons.i18n.I18n;

import java.sql.SQLException;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.inject.Inject;
import javax.persistence.PersistenceException;



/**
 * Asynchronous job for removing pools created during manifest import.
 *
 * {@link Owner}.
 */
public class UndoImportsJob extends UniqueByOwnerJob {

    private static Logger log = LoggerFactory.getLogger(UndoImportsJob.class);

    public static final String LAZY_REGEN = "lazy_regen";
    public static final String JOB_NAME_PREFIX = "undo_imports_";

    protected I18n i18n;
    protected OwnerCurator ownerCurator;
    protected PoolManager poolManager;
    protected SubscriptionServiceAdapter subAdapter;
    protected ExporterMetadataCurator exportCurator;
    protected ImportRecordCurator importRecordCurator;



    @Inject
    public UndoImportsJob(I18n i18n, OwnerCurator ownerCurator, PoolManager poolManager,
        SubscriptionServiceAdapter subAdapter, ExporterMetadataCurator exportCurator,
        ImportRecordCurator importRecordCurator) {

        this.i18n = i18n;
        this.ownerCurator = ownerCurator;
        this.poolManager = poolManager;
        this.subAdapter = subAdapter;
        this.exportCurator = exportCurator;
        this.importRecordCurator = importRecordCurator;
    }

    /**
     * {@inheritDoc}
     *
     * Executes {@link PoolManager#refreshPools(org.candlepin.model.Owner)}
     * as a pinsetter job.
     *
     * @param context the job's execution context
     */
    public void toExecute(JobExecutionContext context) throws JobExecutionException {
        try {
            JobDataMap map = context.getMergedJobDataMap();
            String ownerKey = map.getString(JobStatus.TARGET_ID);
            Boolean lazy = map.getBoolean(LAZY_REGEN);
            Principal principal = (Principal) map.get(PinsetterJobListener.PRINCIPAL_KEY);
            Owner owner = this.ownerCurator.lookupByKey(ownerKey);

            // TODO: Should we check the principal again here?

            if (owner == null) {
                log.debug("Owner no longer exists: {}", ownerKey);
                context.setResult("Nothing to do; owner no longer exists: " + ownerKey);
                return;
            }

            // Remove imports
            ExporterMetadata metadata = this.exportCurator.lookupByTypeAndOwner(
                ExporterMetadata.TYPE_PER_USER, owner
            );

            if (metadata == null) {
                log.debug("No imports exist for owner {}", ownerKey);
                context.setResult("Nothing to do; imports no longer exist for owner: " + ownerKey);
                return;
            }

            log.info("Deleting all pools from manifests for owner: {}", ownerKey);

            Set<String> subscriptions = new HashSet<String>();

            List<Pool> pools = this.poolManager.listPoolsByOwner(owner);
            for (Pool pool : pools) {
                if (pool.getUpstreamPoolId() != null) {
                    subscriptions.add(pool.getSubscriptionId());
                }
            }

            this.poolManager.deletePoolsForSubscriptions(subscriptions);

            // Clear out upstream ID so owner can import from other distributors:
            UpstreamConsumer uc = owner.getUpstreamConsumer();
            owner.setUpstreamConsumer(null);

            this.exportCurator.delete(metadata);
            this.recordManifestDeletion(owner, principal.getUsername(), uc);


            // Refresh pools (is this still necessary...?)
            // Assume that we verified the request in the resource layer:
            this.poolManager.getRefresher(subAdapter, lazy).setUnitOfWork(unitOfWork).add(owner).run();
            context.setResult("Imported pools removed for owner " + owner.getDisplayName());
        }
        catch (PersistenceException e) {
            throw new RetryJobException("UndoImportsJob encountered a problem.", e);
        }
        catch (RuntimeException e) {
            Throwable cause = e.getCause();
            while (cause != null) {
                if (SQLException.class.isAssignableFrom(cause.getClass())) {
                    log.warn("Caught a runtime exception wrapping an SQLException.");
                    throw new RetryJobException("UndoImportsJob encountered a problem.", e);
                }
                cause = cause.getCause();
            }

            // Otherwise throw as we would normally for any generic Exception:
            log.error("UndoImportsJob encountered a problem.", e);
            context.setResult(e.getMessage());
            throw new JobExecutionException(e.getMessage(), e, false);
        }
        // Catch any other exception that is fired and re-throw as a
        // JobExecutionException so that the job will be properly
        // cleaned up on failure.
        catch (Exception e) {
            log.error("UndoImportsJob encountered a problem.", e);
            context.setResult(e.getMessage());
            throw new JobExecutionException(e.getMessage(), e, false);
        }
    }

    private void recordManifestDeletion(Owner owner, String username, UpstreamConsumer uc) {
        ImportRecord record = new ImportRecord(owner);
        record.setGeneratedBy(username);
        record.setGeneratedDate(new Date());
        String msg = this.i18n.tr("Subscriptions deleted by {0}", username);
        record.recordStatus(ImportRecord.Status.DELETE, msg);
        record.setUpstreamConsumer(this.createImportUpstreamConsumer(owner, uc));

        this.importRecordCurator.create(record);
    }

    private ImportUpstreamConsumer createImportUpstreamConsumer(Owner owner, UpstreamConsumer uc) {
        ImportUpstreamConsumer iup = null;

        if (uc == null) {
            uc = owner.getUpstreamConsumer();
        }

        if (uc != null) {
            iup = new ImportUpstreamConsumer();
            iup.setOwnerId(uc.getOwnerId());
            iup.setName(uc.getName());
            iup.setUuid(uc.getUuid());
            iup.setType(uc.getType());
            iup.setWebUrl(uc.getWebUrl());
            iup.setApiUrl(uc.getApiUrl());
        }

        return iup;
    }

    /**
     * Creates a {@link JobDetail} that runs this job for the given {@link Owner}.
     *
     * @param owner the owner to refresh
     * @return a {@link JobDetail} that describes the job run
     */
    public static JobDetail forOwner(Owner owner, Boolean lazy) {
        JobDataMap map = new JobDataMap();
        map.put(JobStatus.TARGET_TYPE, JobStatus.TargetType.OWNER);
        map.put(JobStatus.TARGET_ID, owner.getKey());
        map.put(LAZY_REGEN, lazy);

        // Not sure if this is the best way to go:
        // Give each job a UUID to ensure that it is unique
        JobDetail detail = newJob(UndoImportsJob.class)
            .withIdentity(JOB_NAME_PREFIX + Util.generateUUID())
            .requestRecovery(true) // recover the job upon restarts
            .usingJobData(map)
            .storeDurably(true) // required if we have to postpone the job
            .build();

        return detail;
    }

}
