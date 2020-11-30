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
package org.candlepin.async.tasks;

import org.candlepin.async.ArgumentConversionException;
import org.candlepin.async.AsyncJob;
import org.candlepin.async.JobArguments;
import org.candlepin.async.JobConfig;
import org.candlepin.async.JobConfigValidationException;
import org.candlepin.async.JobConstraints;
import org.candlepin.async.JobExecutionContext;
import org.candlepin.async.JobExecutionException;
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
import org.candlepin.service.SubscriptionServiceAdapter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.xnap.commons.i18n.I18n;

import java.util.Date;
import java.util.List;



/**
 * Asynchronous job for removing pools created during manifest import.
 *
 * {@link Owner}.
 */
@Component
public class UndoImportsJob implements AsyncJob {
    private static Logger log = LoggerFactory.getLogger(UndoImportsJob.class);

    public static final String JOB_KEY = "UndoImportsJob";
    public static final String JOB_NAME = "undo imports";

    private static final String OWNER_KEY = "owner_key";

    /**
     * Job configuration object for the undo imports job
     */
    public static class UndoImportsJobConfig extends JobConfig<UndoImportsJobConfig> {
        public UndoImportsJobConfig() {
            this.setJobKey(JOB_KEY)
                .setJobName(JOB_NAME)
                .addConstraint(JobConstraints.uniqueByArguments(OWNER_KEY));
        }

        /**
         * Sets the owner for this job config.
         *
         * @param owner
         *  the owner to set for this job
         *
         * @return
         *  a reference to this job config
         */
        public UndoImportsJobConfig setOwner(Owner owner) {
            if (owner == null) {
                throw new IllegalArgumentException("owner is null");
            }

            this.setContextOwner(owner)
                .setJobArgument(OWNER_KEY, owner.getKey());

            return this;
        }

        @Override
        public void validate() throws JobConfigValidationException {
            super.validate();

            try {
                JobArguments arguments = this.getJobArguments();

                String ownerKey = arguments.getAsString(OWNER_KEY);

                if (ownerKey == null || ownerKey.isEmpty()) {
                    String errmsg = "owner has not been set, or the provided owner lacks a key";
                    throw new JobConfigValidationException(errmsg);
                }
            }
            catch (ArgumentConversionException e) {
                String errmsg = "One or more required arguments are of the wrong type";
                throw new JobConfigValidationException(errmsg, e);
            }
        }
    }


    protected I18n i18n;
    protected OwnerCurator ownerCurator;
    protected PoolManager poolManager;
    protected SubscriptionServiceAdapter subAdapter;
    protected ExporterMetadataCurator exportCurator;
    protected ImportRecordCurator importRecordCurator;

    @Autowired
    public UndoImportsJob(I18n i18n,
        OwnerCurator ownerCurator,
        PoolManager poolManager,
        SubscriptionServiceAdapter subAdapter,
        ExporterMetadataCurator exportCurator,
        ImportRecordCurator importRecordCurator) {

        this.i18n = i18n;
        this.ownerCurator = ownerCurator;
        this.poolManager = poolManager;
        this.subAdapter = subAdapter;
        this.exportCurator = exportCurator;
        this.importRecordCurator = importRecordCurator;
    }

    @Override
    @Transactional
    public Object execute(JobExecutionContext context) throws JobExecutionException {
        JobArguments args = context.getJobArguments();
        String principalName = context.getPrincipalName();

        String ownerKey = args.getAsString(OWNER_KEY);
        Owner owner = this.ownerCurator.lockAndLoadByKey(ownerKey);

        if (owner == null) {
            // Apparently this isn't a failure...?
            log.debug("Owner no longer exists: {}", ownerKey);
            return String.format("Nothing to do; owner no longer exists: %s", ownerKey);
        }

        String displayName = owner.getDisplayName();

        // Remove imports
        ExporterMetadata metadata = this.exportCurator
            .getByTypeAndOwner(ExporterMetadata.TYPE_PER_USER, owner);

        if (metadata == null) {
            log.debug("No imports exist for owner {}", displayName);
            return String.format("Nothing to do; imports no longer exist for owner: %s", displayName);
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
        owner = this.ownerCurator.merge(owner);
        this.ownerCurator.flush();

        this.exportCurator.delete(metadata);
        this.recordManifestDeletion(owner, principalName, uc);

        return String.format("Imported pools removed for owner: %s", displayName);
    }

    private void recordManifestDeletion(Owner owner, String principalName, UpstreamConsumer uc) {
        ImportRecord record = new ImportRecord(owner);
        record.setGeneratedBy(principalName);
        record.setGeneratedDate(new Date());
        String msg = this.i18n.tr("Subscriptions deleted by {0}", principalName);
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
     * Creates a JobConfig configured to execute the undo imports job. Callers may further
     * manipulate the JobConfig as necessary before queuing it.
     *
     * @return
     *  a JobConfig instance configured to execute the undo imports job
     */
    public static UndoImportsJobConfig createJobConfig() {
        return new UndoImportsJobConfig();
    }

}
