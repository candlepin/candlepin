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
package org.candlepin.async.tasks;

import org.candlepin.async.ArgumentConversionException;
import org.candlepin.async.AsyncJob;
import org.candlepin.async.JobArguments;
import org.candlepin.async.JobConfig;
import org.candlepin.async.JobConfigValidationException;
import org.candlepin.async.JobConstraints;
import org.candlepin.async.JobExecutionContext;
import org.candlepin.async.JobExecutionException;
import org.candlepin.controller.ManifestManager;
import org.candlepin.exceptions.NotFoundException;
import org.candlepin.model.ImportRecord;
import org.candlepin.model.Owner;
import org.candlepin.model.OwnerCurator;
import org.candlepin.sync.ConflictOverrides;
import org.candlepin.sync.ImportConflictException;
import org.candlepin.sync.Importer;
import org.candlepin.sync.ImporterException;
import org.candlepin.sync.file.ManifestFileService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Iterator;
import java.util.Objects;

import javax.inject.Inject;



/**
 * Runs an asynchronous manifest import. This job expects that the manifest file was already uploaded.
 */
public class ImportJob implements AsyncJob {
    private static final Logger log = LoggerFactory.getLogger(ImportJob.class);

    public static final String JOB_KEY = "ImportJob";
    public static final String JOB_NAME = "Import Manifest";

    protected static final String OWNER_KEY = "org";
    protected static final String STORED_FILE_ID_KEY = "stored_manifest_file_id";
    protected static final String CONFLICT_OVERRIDES_KEY = "conflict_overrides";
    protected static final String UPLOADED_FILE_NAME_KEY = "uploaded_file_name";

    /**
     * Job configuration object for the import job
     */
    public static class ImportJobConfig extends JobConfig<ImportJobConfig> {
        public ImportJobConfig() {
            this.setJobKey(JOB_KEY)
                .setJobName(JOB_NAME)
                .addConstraint(JobConstraints.uniqueByArguments(OWNER_KEY));
        }

        /**
         * Sets the owner for this import job. The owner is required, and also provides the org
         * context in which the job will be executed.
         *
         * @param owner
         *  the owner to set for this job
         *
         * @return
         *  a reference to this job config
         */
        public ImportJobConfig setOwner(Owner owner) {
            if (owner == null) {
                throw new IllegalArgumentException("owner is null");
            }

            // The owner is both part of context & arguments in this job.
            this.setContextOwner(owner)
                .setJobArgument(OWNER_KEY, owner.getKey());

            return this;
        }

        /**
         * Sets the id of the stored file on the {@link ManifestFileService}.
         *
         * @param storedFileId
         *  the stored file id to set for this job
         *
         * @return
         *  a reference to this job config
         */
        public ImportJobConfig setStoredFileId(String storedFileId) {
            this.setJobArgument(STORED_FILE_ID_KEY, storedFileId);
            return this;
        }

        /**
         * Sets the file name of the uploaded file on the {@link ManifestFileService}.
         *
         * @param uploadedFileName
         *  the uploaded file name to set for this job
         *
         * @return
         *  a reference to this job config
         */
        public ImportJobConfig setUploadedFileName(String uploadedFileName) {
            this.setJobArgument(UPLOADED_FILE_NAME_KEY, uploadedFileName);
            return this;
        }

        /**
         * Sets the conflict overrides the user has specified to this import job.
         *
         * @param conflictOverrides
         *  the conflict overrides to set for this job
         *
         * @return
         *  a reference to this job config
         */
        public ImportJobConfig setConflictOverrides(ConflictOverrides conflictOverrides) {
            this.setJobArgument(CONFLICT_OVERRIDES_KEY, conflictOverrides.asStringArray());
            return this;
        }

        @Override
        public void validate() throws JobConfigValidationException {
            super.validate();

            try {
                JobArguments arguments = this.getJobArguments();

                String ownerKey = arguments.getAsString(OWNER_KEY);
                String storedFileId = arguments.getAsString(STORED_FILE_ID_KEY);
                String uploadedFileName = arguments.getAsString(UPLOADED_FILE_NAME_KEY);

                if (ownerKey == null || ownerKey.isEmpty()) {
                    String errmsg = "owner has not been set, or the provided owner lacks a key";
                    throw new JobConfigValidationException(errmsg);
                }

                if (storedFileId == null || storedFileId.isEmpty()) {
                    String errmsg = "stored file id has not been set, or is empty";
                    throw new JobConfigValidationException(errmsg);
                }

                if (uploadedFileName == null || uploadedFileName.isEmpty()) {
                    String errmsg = "uploaded file name has not been set, or is empty";
                    throw new JobConfigValidationException(errmsg);
                }
            }
            catch (ArgumentConversionException e) {
                String errmsg = "One or more required arguments are of the wrong type";
                throw new JobConfigValidationException(errmsg, e);
            }
        }
    }

    /**
     * The equivalent of {@link ImportConflictException}, but for asynchronous imports.
     * It is used by transforming an {@link org.candlepin.exceptions.CandlepinException} to a
     * {@link JobExecutionException}, fit for propagating to the job management system, without keeping the
     * redundant fields the former has (such as requestUuid & REST return code), while retaining the useful
     * information (list of conflicts, display message) accessible through its toString method.
     */
    private static class ImportConflictJobException extends JobExecutionException {

        private final String message;

        public ImportConflictJobException(ImportConflictException importConflictException) {
            super(importConflictException.getLocalizedMessage(), true);

            StringBuilder str = new StringBuilder(importConflictException.getLocalizedMessage());
            str.append(" The following conflicts were found: [ ");
            Iterator<Importer.Conflict> conflicts = importConflictException.message().getConflicts().iterator();
            while (conflicts.hasNext()) {
                str.append(conflicts.next());
                if (conflicts.hasNext()) {
                    str.append(", ");
                }
            }
            str.append(" ]");
            this.message = str.toString();
        }

        @Override
        public String toString() {
            return message;
        }
    }


    private final OwnerCurator ownerCurator;
    private final ManifestManager manifestManager;

    @Inject
    public ImportJob(OwnerCurator ownerCurator, ManifestManager manifestManager) {
        this.ownerCurator = Objects.requireNonNull(ownerCurator);
        this.manifestManager = Objects.requireNonNull(manifestManager);
    }

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        JobArguments args = context.getJobArguments();

        String ownerKey = args.getAsString(OWNER_KEY);
        ConflictOverrides overrides = new ConflictOverrides(
            args.getAs(CONFLICT_OVERRIDES_KEY, String[].class));
        String storedFileId = args.getAsString(STORED_FILE_ID_KEY);
        String uploadedFileName = args.getAsString(UPLOADED_FILE_NAME_KEY);

        JobExecutionException toThrow = null;
        Throwable caught = null;
        Owner targetOwner = null;

        try {
            targetOwner = ownerCurator.getByKey(ownerKey);
            if (targetOwner == null) {
                throw new NotFoundException(String.format("Owner %s was not found.", ownerKey));
            }

            ImportRecord importRecord = manifestManager.importStoredManifest(targetOwner,
                storedFileId, overrides, uploadedFileName);

            log.info("Async import complete.");
            context.setJobResult(importRecord);

            return;
        }
        // Here, we pass the exceptions we caught to be persisted as part of an ImportRecord failure,
        // while we propagate JobExecutionExceptions to the job management system (marked appropriately as
        // terminal/non-terminal, and in the case of an import conflict, extra conflict information).
        catch (ImportConflictException e) {
            toThrow = new ImportConflictJobException(e);
            caught = e;
        }
        catch (ImporterException e) {
            toThrow = new JobExecutionException(e.getMessage(), e, true);
            caught = e;
        }
        catch (Exception e) {
            toThrow = new JobExecutionException(e.getMessage(), e, false);
            caught = e;
        }

        manifestManager.recordImportFailure(targetOwner, caught, uploadedFileName);

        // If an exception was thrown, the importer's transaction was rolled
        // back. We want to make sure that the file gets deleted so that it
        // doesn't take up disk space. It may be possible that the file was
        // already deleted, but we attempt it anyway.
        manifestManager.deleteStoredManifest(storedFileId);
        throw toThrow;
    }

    /**
     * Creates a JobConfig configured to execute the import job. Callers may further manipulate
     * the JobConfig as necessary before queuing it.
     *
     * @return
     *  a JobConfig instance configured to execute the import job
     */
    public static ImportJobConfig createJobConfig() {
        return new ImportJobConfig();
    }
}
