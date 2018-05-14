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

import org.candlepin.common.exceptions.BadRequestException;
import org.candlepin.common.exceptions.IseException;
import org.candlepin.common.exceptions.NotFoundException;
import org.candlepin.common.filter.LoggingFilter;
import org.candlepin.controller.ManifestManager;
import org.candlepin.model.ImportRecord;
import org.candlepin.model.Owner;
import org.candlepin.model.OwnerCurator;
import org.candlepin.pinsetter.core.model.JobStatus;
import org.candlepin.sync.ConflictOverrides;
import org.candlepin.sync.ImporterException;
import org.candlepin.sync.SyncDataFormatException;
import org.candlepin.util.Util;

import org.apache.log4j.MDC;
import org.quartz.JobDataMap;
import org.quartz.JobDetail;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.Inject;

/**
 * Runs an asynchronous manifest import. This job expects that the manifest file
 * was already uploaded.
 */
public class ImportJob extends UniqueByEntityJob {

    protected static final String STORED_FILE_ID = "stored_manifest_file_id";
    protected static final String CONFLICT_OVERRIDES = "conflict_overrides";
    protected static final String UPLOADED_FILE_NAME = "uploaded_file_name";

    private static Logger log = LoggerFactory.getLogger(ImportJob.class);

    private OwnerCurator ownerCurator;
    private ManifestManager manifestManager;

    @Inject
    public ImportJob(OwnerCurator ownerCurator, ManifestManager manifestManager) {
        this.ownerCurator = ownerCurator;
        this.manifestManager = manifestManager;
    }

    @Override
    public void toExecute(JobExecutionContext context) throws JobExecutionException {
        JobDataMap map = context.getMergedJobDataMap();
        String ownerKey = (String) map.get(JobStatus.TARGET_ID);
        ConflictOverrides overrides = new ConflictOverrides((String[]) map.get(CONFLICT_OVERRIDES));
        String storedFileId = (String) map.get(STORED_FILE_ID);
        String uploadedFileName = (String) map.get(UPLOADED_FILE_NAME);

        Throwable caught = null;
        Owner targetOwner = null;
        try {
            targetOwner = ownerCurator.getByKey(ownerKey);
            if (targetOwner == null) {
                throw new NotFoundException(String.format("Owner %s was not found.", ownerKey));
            }

            ImportRecord importRecord = manifestManager.importStoredManifest(targetOwner,
                storedFileId, overrides, uploadedFileName);
            context.setResult(importRecord);
        }

        // Catch and handle SyncDataFormatException and ImporterExceptions
        // as the OwnerResource.importManifest does which will provide a little more
        // info about the exception that was thrown (CandlepinException).
        catch (SyncDataFormatException e) {
            caught = new BadRequestException(e.getMessage(), e);
        }
        catch (ImporterException e) {
            caught = new IseException(e.getMessage(), e);
        }
        catch (Exception e) {
            caught = e;
        }

        if (caught != null) {
            log.error("ImportJob encountered a problem.", caught);
            manifestManager.recordImportFailure(targetOwner, caught,
                uploadedFileName);
            context.setResult(caught.getMessage());
            // If an exception was thrown, the importer's transaction was rolled
            // back. We want to make sure that the file gets deleted so that it
            // doesn't take up disk space. It may be possible that the file was
            // already deleted, but we attempt it anyway.
            manifestManager.deleteStoredManifest(storedFileId);
            throw new JobExecutionException(caught.getMessage(), caught, false);
        }
    }

    /**
     * Schedules the import of the specified stored manifest file targeting the specified {@link Owner}.
     *
     * @param owner the target owner.
     * @param storedFileId the ID of the manifest file to import.
     * @param uploadedFileName the original uploaded file name.
     * @param overrides the {@link ConflictOverrides} to apply when importing the manifest.
     * @return the details of the configured job.
     */
    public static JobDetail scheduleImport(Owner owner, String storedFileId, String uploadedFileName,
        ConflictOverrides overrides) {
        JobDataMap map = new JobDataMap();
        map.put(JobStatus.OWNER_ID, owner.getKey());
        map.put(JobStatus.OWNER_LOG_LEVEL, owner.getLogLevel());
        map.put(JobStatus.TARGET_TYPE, JobStatus.TargetType.OWNER);
        map.put(JobStatus.TARGET_ID, owner.getKey());
        map.put(STORED_FILE_ID, storedFileId);
        map.put(UPLOADED_FILE_NAME, uploadedFileName);
        map.put(CONFLICT_OVERRIDES, overrides.asStringArray());
        map.put(JobStatus.CORRELATION_ID, MDC.get(LoggingFilter.CSID));

        JobDetail detail = newJob(ImportJob.class)
            .withIdentity("import_" + Util.generateUUID())
            .usingJobData(map)
            .build();
        return detail;
    }
}
