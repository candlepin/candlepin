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

import org.candlepin.async.JobConfig;
import org.candlepin.async.tasks.ExportJob;
import org.candlepin.async.tasks.ImportJob;
import org.candlepin.audit.EventFactory;
import org.candlepin.audit.EventSink;
import org.candlepin.common.exceptions.BadRequestException;
import org.candlepin.common.exceptions.ForbiddenException;
import org.candlepin.common.exceptions.IseException;
import org.candlepin.common.exceptions.NotFoundException;
import org.candlepin.guice.PrincipalProvider;
import org.candlepin.model.CdnCurator;
import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerCurator;
import org.candlepin.model.ConsumerType;
import org.candlepin.model.ConsumerTypeCurator;
import org.candlepin.model.EntitlementCurator;
import org.candlepin.model.ImportRecord;
import org.candlepin.model.Owner;
import org.candlepin.service.ExportExtensionAdapter;
import org.candlepin.sync.ConflictOverrides;
import org.candlepin.sync.ExportCreationException;
import org.candlepin.sync.ExportResult;
import org.candlepin.sync.Exporter;
import org.candlepin.sync.Importer;
import org.candlepin.sync.ImporterException;
import org.candlepin.sync.file.ManifestFile;
import org.candlepin.sync.file.ManifestFileService;
import org.candlepin.sync.file.ManifestFileServiceException;
import org.candlepin.sync.file.ManifestFileType;
import org.candlepin.util.Util;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringUtils;
import org.quartz.JobDetail;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.xnap.commons.i18n.I18n;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Set;

import javax.servlet.http.HttpServletResponse;

/**
 * This class serves as a controller layer for manifest export and import and encapsulates
 * the {@link Importer} and {@link Exporter} functionality so that it isn't spread out
 * across multiple classes.
 *
 */
@Component
@Scope("prototype")
public class ManifestManager {

    private static Logger log = LoggerFactory.getLogger(ManifestManager.class);
    private ManifestFileService manifestFileService;
    private Exporter exporter;
    private Importer importer;
    private EntitlementCurator entitlementCurator;
    private PoolManager poolManager;
    private ConsumerCurator consumerCurator;
    private ConsumerTypeCurator consumerTypeCurator;
    private CdnCurator cdnCurator;
    private PrincipalProvider principalProvider;
    private I18n i18n;
    private EventSink sink;
    private EventFactory eventFactory;

    @Autowired
    public ManifestManager(ManifestFileService manifestFileService, Exporter exporter, Importer importer,
        ConsumerCurator consumerCurator, ConsumerTypeCurator consumerTypeCurator,
        EntitlementCurator entitlementCurator, CdnCurator cdnCurator, PoolManager poolManager,
        PrincipalProvider principalProvider, I18n i18n, EventSink eventSink, EventFactory eventFactory) {

        this.manifestFileService = manifestFileService;
        this.exporter = exporter;
        this.importer = importer;
        this.consumerCurator = consumerCurator;
        this.consumerTypeCurator = consumerTypeCurator;
        this.cdnCurator = cdnCurator;
        this.entitlementCurator = entitlementCurator;
        this.poolManager = poolManager;
        this.principalProvider = principalProvider;
        this.i18n = i18n;
        this.sink = eventSink;
        this.eventFactory = eventFactory;
    }

    /**
     * Asynchronously generates a manifest for the target consumer.
     *
     * @param consumerUuid the target consumer's UUID.
     * @param cdnLabel the CDN label to store in the meta file.
     * @param webUrl the URL pointing to the manifest's originating web application.
     * @param apiUrl the API URL pointing to the manifest's originating candlepin API.
     * @param extensionData data to be passed to the {@link ExportExtensionAdapter} when creating
     *                      a new export of the target consumer.
     * @return the details of the async export job.
     */
    public JobConfig generateManifestAsync(String consumerUuid, Owner owner, String cdnLabel,
        String webUrl, String apiUrl, Map<String, String> extensionData) {

        log.info("Scheduling Async Export for consumer {}", consumerUuid);
        Consumer consumer = validateConsumerForExport(consumerUuid, cdnLabel);

        return ExportJob.createJobConfig()
            .setConsumer(consumer)
            .setOwner(owner)
            .setCdnLabel(cdnLabel)
            .setWebAppPrefix(webUrl)
            .setApiUrl(apiUrl)
            .setExtensionData(extensionData);
    }

    /**
     * Generates a manifest for the specified consumer.
     *
     * @param consumerUuid the target consumer's UUID.
     * @param cdnLabel the CDN label to store in the meta file.
     * @param webUrl the URL pointing to the manifest's originating web application.
     * @param apiUrl the API URL pointing to the manifest's originating candlepin API.
     * @param extensionData data to be passed to the {@link ExportExtensionAdapter} when creating
     *                      a new export of the target consumer.
     * @return an archive of the target consumer
     * @throws ExportCreationException when an export fails.
     */
    public File generateManifest(String consumerUuid, String cdnLabel, String webUrl, String apiUrl,
        Map<String, String> extensionData) throws ExportCreationException {

        log.info("Exporting consumer {}", consumerUuid);

        Consumer consumer = validateConsumerForExport(consumerUuid, cdnLabel);
        poolManager.regenerateDirtyEntitlements(consumer);

        File export = exporter.getFullExport(consumer, cdnLabel, webUrl, apiUrl, extensionData);
        sink.queueEvent(eventFactory.exportCreated(consumer));

        return export;
    }

    /**
     * Stores the specified archive via the {@link ManifestFileService} and triggers an
     * asynchronous manifest import.
     *
     * @param owner the target owner.
     * @param archive the manifest file archive.
     * @param uploadedFileName the name of the file as uploaded (archive will contain the cached name).
     * @param overrides any {@link ConflictOverrides}s to apply during the import process.
     * @return the {@link JobDetail} that represents the asynchronous import job to start.
     * @throws ManifestFileServiceException if the archive could not be stored.
     */
    public JobConfig importManifestAsync(Owner owner, File archive, String uploadedFileName,
        ConflictOverrides overrides) throws ManifestFileServiceException {
        ManifestFile manifestRecordId = storeImport(archive, owner);
        return ImportJob.createJobConfig()
            .setOwner(owner)
            .setStoredFileId(manifestRecordId.getId())
            .setUploadedFileName(uploadedFileName)
            .setConflictOverrides(overrides);
    }

    /**
     * Imports the specified manifest archive into the specifed {@link Owner}.
     *
     * @param owner the target owner.
     * @param archive the archive to import
     * @param uploadedFileName the name of the originally uploaded file.
     * @param overrides the {@link ConflictOverrides} to apply during the import process.
     * @return the result of the import.
     * @throws ImporterException if there is an issue importing the manifest.
     */
    public ImportRecord importManifest(Owner owner, File archive, String uploadedFileName,
        ConflictOverrides overrides) throws ImporterException {
        return importer.loadExport(owner, archive, overrides, uploadedFileName);
    }

    /**
     * Records a failed import in the database.
     *
     * @param owner the target owner.
     * @param error the error that caused the failure.
     * @param filename the uploaded filename.
     */
    public void recordImportFailure(Owner owner, Throwable error, String filename) {
        importer.recordImportFailure(owner, error, filename);
    }

    /**
     * Imports a stored manifest file into the target {@link Owner}. The stored file is deleted
     * as soon as the import is complete.
     *
     * @param targetOwner the target owner.
     * @param fileId the manifest file ID.
     * @param overrides the {@link ConflictOverrides} to apply to the import process.
     * @param uploadedFileName the originally uploaded file name.
     * @return the result of the import.
     * @throws BadRequestException if the file is not found in the {@link ManifestFileService}
     * @throws ImporterException if there is an issue importing the file.
     */
    @Transactional
    public ImportRecord importStoredManifest(Owner targetOwner, String fileId, ConflictOverrides overrides,
        String uploadedFileName) throws BadRequestException, ImporterException {
        ManifestFile manifest = manifestFileService.get(fileId);
        if (manifest == null) {
            throw new BadRequestException(i18n.tr("The requested manifest file was not found: {0}", fileId));
        }
        ImportRecord importResult = importer.loadStoredExport(manifest, targetOwner, overrides,
            uploadedFileName);
        deleteStoredManifest(manifest.getId());
        return importResult;
    }

    /**
     * Performs a cleanup of the manifest records. It will remove records that
     * are of the specified age (in minutes) and will clean up all related files.
     * Because the uploaded/downloaded manifest files are provided by a service
     * any rogue records are removed as well.
     *
     * @param maxAgeInMinutes the maximum age of the file in minutes. A negative value
     *                        indicates no expiry.
     * @return the number of expired exports that were deleted.
     * @throws ManifestFileServiceException if an error occurs when cleaning up records.
     */
    @Transactional
    public int cleanup(int maxAgeInMinutes) throws ManifestFileServiceException {
        if (maxAgeInMinutes < 0) {
            return 0;
        }
        return manifestFileService.deleteExpired(Util.addMinutesToDt(maxAgeInMinutes * -1));
    }

    /**
     * Write the stored manifest file to the specified response output stream and update
     * the appropriate response data.
     *
     * @param exportId the id of the manifest file to find.
     * @param exportedConsumerUuid the UUID of the consumer the export was generated for.
     * @param response the response to write the file to.
     * @throws ManifestFileServiceException if there was an issue getting the file from the service
     * @throws NotFoundException if the manifest file is not found
     * @throws BadRequestException if the manifests target consumer does not match the specified
     *                             consumer.
     * @throws IseException if there was an issue writing the file to the response.
     */
    @Transactional
    public void writeStoredExportToResponse(String exportId, String exportedConsumerUuid,
        HttpServletResponse response) throws ManifestFileServiceException, NotFoundException,
        BadRequestException, IseException {
        Consumer exportedConsumer = consumerCurator.verifyAndLookupConsumer(exportedConsumerUuid);

        // In order to stream the results from the DB to the client
        // we write the file contents directly to the response output stream.
        //
        // NOTE: Passing the database input stream to the response builder seems
        //       like it would be a correct approach here, but large object streaming
        //       can only be done inside a single transaction, so we have to stream it
        //       manually.
        ManifestFile manifest = manifestFileService.get(exportId);
        if (manifest == null) {
            throw new NotFoundException(
                i18n.tr("Unable to find specified manifest by id: {0}", exportId));
        }

        // The specified consumer must match that of the manifest.
        if (!exportedConsumer.getUuid().equals(manifest.getTargetId())) {
            throw new BadRequestException(
                i18n.tr("Could not validate export against specifed consumer: {0}",
                    exportedConsumer.getUuid()));
        }

        BufferedOutputStream output = null;
        InputStream input = null;
        try {
            response.setContentType("application/zip");
            response.setHeader("Content-Disposition", "attachment; filename=" + manifest.getName());

            // NOTE: Input and output streams are expected to be closed by their creators.
            input = manifest.getInputStream();
            output = new BufferedOutputStream(response.getOutputStream());
            int data = input.read();
            while (data != -1) {
                output.write(data);
                data = input.read();
            }
            output.flush();
        }
        catch (Exception e) {
            // Reset the response data so that a json response can be returned,
            // by RestEasy.
            response.setContentType("text/json");
            response.setHeader("Content-Disposition", "");
            throw new IseException(i18n.tr("Unable to download manifest: {0}", exportId), e);
        }
    }

    private Consumer validateConsumerForExport(String consumerUuid, String cdnLabel) {
        // FIXME Should this be testing the CdnLabel as well?
        Consumer consumer = consumerCurator.verifyAndLookupConsumer(consumerUuid);
        ConsumerType ctype = this.consumerTypeCurator.getConsumerType(consumer);

        if (ctype == null || !ctype.isManifest()) {
            throw new ForbiddenException(
                i18n.tr("Unit {0} cannot be exported. A manifest cannot be made for units of type \"{1}\".",
                    consumerUuid, ctype != null ? ctype.getLabel() : "unknown type"));
        }

        if (!StringUtils.isBlank(cdnLabel) && cdnCurator.getByLabel(cdnLabel) == null) {
            throw new ForbiddenException(
                i18n.tr("A CDN with label {0} does not exist on this system.", cdnLabel));
        }

        return consumer;
    }

    /**
     * Generates a manifest for the specifed consumer and stores the resulting file via the
     * {@link ManifestFileService}.
     *
     * @param consumerUuid the target consumer's UUID.
     * @param cdnLabel the CDN label to store in the meta file.
     * @param webUrl the URL pointing to the manifest's originating web application.
     * @param apiUrl the API URL pointing to the manifest's originating candlepin API.
     * @param extensionData data to be passed to the {@link ExportExtensionAdapter} when creating
     *                      a new export of the target consumer.
     * @return an {@link ExportResult} containing the details of the stored file.
     * @throws ExportCreationException if there are any issues generating the manifest.
     */
    public ExportResult generateAndStoreManifest(String consumerUuid, String cdnLabel, String webUrl,
        String apiUrl, Map<String, String> extensionData) throws ExportCreationException {

        Consumer consumer = validateConsumerForExport(consumerUuid, cdnLabel);

        File export = null;
        try {
            poolManager.regenerateDirtyEntitlements(entitlementCurator.listByConsumer(consumer));
            export = exporter.getFullExport(consumer, cdnLabel, webUrl, apiUrl, extensionData);
            ManifestFile manifestFile = storeExport(export, consumer);
            sink.queueEvent(eventFactory.exportCreated(consumer));
            return new ExportResult(consumer.getUuid(), manifestFile.getId());
        }
        catch (ManifestFileServiceException e) {
            throw new ExportCreationException("Unable to create export archive", e);
        }
        finally {
            // We no longer need the export work directory since the archive has been saved in the DB.
            if (export != null) {
                File workDir = export.getParentFile();
                try {
                    FileUtils.deleteDirectory(workDir);
                }
                catch (IOException ioe) {
                    // It'll get cleaned up by the ManifestCleanerJob if it couldn't
                    // be deleted for some reason.
                }
            }
        }
    }

    /**
     * Deletes the manifest file stored by the {@link ManifestFileService}. If there was
     * an issue deleting the manifest, the exception is just logged. The file will eventually
     * be deleted by the {@link ManifestCleanerJob}.
     *
     * @param manifestFileId the ID of the manifest to be deleted.
     */
    @Transactional
    public void deleteStoredManifest(String manifestFileId) {
        try {
            log.info("Deleting stored manifest file: {}", manifestFileId);
            manifestFileService.delete(manifestFileId);
        }
        catch (Exception e) {
            // Just log any exception here. This will eventually get cleaned up by
            // a cleaner job.
            log.warn("Could not delete import file by id: {}", manifestFileId, e);
        }
    }

    /**
     * Generates an archive of the specified consumer's entitlements.
     *
     * @param consumer the target consumer
     * @param serials the entitlement serials to export.
     * @return an archive to the specified consumer's entitlements.
     * @throws ExportCreationException if the archive could not be created.
     */
    public File generateEntitlementArchive(Consumer consumer, Set<Long> serials)
        throws ExportCreationException {

        log.debug("Getting client certificate zip file for consumer: {}", consumer.getUuid());
        poolManager.regenerateDirtyEntitlements(consumer);

        return exporter.getEntitlementExport(consumer, serials);
    }

    /**
     * Stores the specified manifest import file via the {@link ManifestFileService}.
     *
     * @param importFile the manifest import {@link File} to store
     * @return the id of the stored manifest file.
     */
    @Transactional
    protected ManifestFile storeImport(File importFile, Owner targetOwner) throws ManifestFileServiceException {
        // Store the manifest record, and then store the file.
        return storeFile(importFile, ManifestFileType.IMPORT, targetOwner.getKey());
    }

    /**
     * Stores the specified manifest export file.
     *
     * @param exportFile the manifest export {@link File} to store.
     * @return the id of the stored manifest file.
     * @throws ManifestFileServiceException
     */
    @Transactional
    protected ManifestFile storeExport(File exportFile, Consumer distributor)
        throws ManifestFileServiceException {
        // Only allow a single export for a consumer at a time. Delete all others before
        // storing the new one.
        int count = manifestFileService.delete(ManifestFileType.EXPORT, distributor.getUuid());
        log.debug("Deleted {} existing export files for distributor {}.", count, distributor.getUuid());
        return storeFile(exportFile, ManifestFileType.EXPORT, distributor.getUuid());
    }

    private ManifestFile storeFile(File targetFile, ManifestFileType type, String targetId)
        throws ManifestFileServiceException {
        return manifestFileService.store(type, targetFile, principalProvider.get().getName(), targetId);
    }

}
