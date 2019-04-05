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

import com.google.inject.persist.Transactional;
import org.apache.log4j.MDC;
import org.candlepin.auth.Principal;
import org.candlepin.common.filter.LoggingFilter;
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

import javax.inject.Inject;
import java.util.Date;
import java.util.List;

import static org.quartz.JobBuilder.newJob;

/**
 * Asynchronous job for removing pools created during manifest import.
 *
 * {@link Owner}.
 */
public class UndoImportsJob extends UniqueByEntityJob {

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
    @Transactional
    public void toExecute(JobExecutionContext context) throws JobExecutionException {
        JobDataMap map = context.getMergedJobDataMap();
        String ownerId = map.getString(JobStatus.TARGET_ID);
        String ownerKey = map.getString(JobStatus.OWNER_ID);
        Owner owner = this.ownerCurator.lockAndLoadById(ownerId);
        Principal principal = (Principal) map.get(PinsetterJobListener.PRINCIPAL_KEY);

        // TODO: Should we check the principal again here?

        if (owner == null) {
            log.debug("Owner no longer exists: {}", ownerKey);
            context.setResult("Nothing to do; owner no longer exists: " + ownerKey);
            return;
        }

        String displayName = owner.getDisplayName();

        // Remove imports
        ExporterMetadata metadata = this.exportCurator.getByTypeAndOwner(
            ExporterMetadata.TYPE_PER_USER, owner);

        if (metadata == null) {
            log.debug("No imports exist for owner {}", displayName);
            context.setResult("Nothing to do; imports no longer exist for owner: " + displayName);
            return;
        }

        log.info("Deleting all pools originating from manifests for owner/org: {}", displayName);

        List<Pool> pools = this.poolManager.listPoolsByOwner(owner).list();
        for (Pool pool : pools) {
            if (this.poolManager.isManaged(pool)) {
                this.poolManager.deletePool(pool);
            }
        }

        // Clear out upstream ID so owner can import from other distributors:
        UpstreamConsumer uc = owner.getUpstreamConsumer();
        owner.setUpstreamConsumer(null);

        this.exportCurator.delete(metadata);
        this.recordManifestDeletion(owner, principal.getUsername(), uc);

        context.setResult("Imported pools removed for owner " + displayName);
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
        map.put(JobStatus.OWNER_ID, owner.getKey());
        map.put(JobStatus.OWNER_LOG_LEVEL, owner.getLogLevel());
        map.put(JobStatus.TARGET_TYPE, JobStatus.TargetType.OWNER);
        map.put(JobStatus.TARGET_ID, owner.getId());
        map.put(LAZY_REGEN, lazy);
        map.put(JobStatus.CORRELATION_ID, MDC.get(LoggingFilter.CSID_KEY));

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
