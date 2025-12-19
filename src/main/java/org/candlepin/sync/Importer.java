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
package org.candlepin.sync;

import org.candlepin.audit.EventSink;
import org.candlepin.controller.RefresherFactory;
import org.candlepin.dto.ModelTranslator;
import org.candlepin.dto.manifest.v1.CdnDTO;
import org.candlepin.dto.manifest.v1.CertificateDTO;
import org.candlepin.dto.manifest.v1.ConsumerDTO;
import org.candlepin.dto.manifest.v1.ConsumerTypeDTO;
import org.candlepin.dto.manifest.v1.DistributorVersionDTO;
import org.candlepin.dto.manifest.v1.ProductDTO;
import org.candlepin.dto.manifest.v1.SubscriptionDTO;
import org.candlepin.model.CdnCurator;
import org.candlepin.model.CertificateSerialCurator;
import org.candlepin.model.ConsumerType;
import org.candlepin.model.ConsumerTypeCurator;
import org.candlepin.model.DistributorVersionCurator;
import org.candlepin.model.ExporterMetadata;
import org.candlepin.model.ExporterMetadataCurator;
import org.candlepin.model.IdentityCertificate;
import org.candlepin.model.IdentityCertificateCurator;
import org.candlepin.model.ImportRecord;
import org.candlepin.model.ImportRecordCurator;
import org.candlepin.model.ImportUpstreamConsumer;
import org.candlepin.model.Owner;
import org.candlepin.model.OwnerCurator;
import org.candlepin.model.UpstreamConsumer;
import org.candlepin.pki.impl.Signer;
import org.candlepin.service.SubscriptionServiceAdapter;
import org.candlepin.service.impl.ImportSubscriptionServiceAdapter;
import org.candlepin.sync.file.ManifestFile;
import org.candlepin.sync.file.ManifestFileService;
import org.candlepin.sync.file.ManifestFileServiceException;

import com.google.inject.persist.Transactional;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.hibernate.exception.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xnap.commons.i18n.I18n;

import tools.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import javax.inject.Inject;
import javax.inject.Named;
import javax.persistence.PersistenceException;


public class Importer {
    private static final Logger log = LoggerFactory.getLogger(Importer.class);

    /**
     * files we use to perform import
     */
    enum ImportFile {
        META("meta.json"),
        CONSUMER_TYPE("consumer_types"),
        CONSUMER("consumer.json"),
        ENTITLEMENTS("entitlements"),
        ENTITLEMENT_CERTIFICATES("entitlement_certificates"),
        PRODUCTS("products"),
        RULES_FILE("rules2/rules.js"),
        UPSTREAM_CONSUMER("upstream_consumer"),
        DISTRIBUTOR_VERSIONS("distributor_version"),
        CONTENT_DELIVERY_NETWORKS("content_delivery_network");

        private String fileName;

        ImportFile(String fileName) {
            this.fileName = fileName;
        }

        public String fileName() {
            return fileName;
        }
    }

    /**
     * Keys representing the various errors that can occur during a manifest
     * import, but be overridden with forces.
     */
    public enum Conflict {
        MANIFEST_OLD, MANIFEST_SAME, DISTRIBUTOR_CONFLICT, SIGNATURE_CONFLICT
    }

    private final ConsumerTypeCurator consumerTypeCurator;
    private final ObjectMapper mapper;
    private final RulesImporter rulesImporter;
    private final OwnerCurator ownerCurator;
    private final IdentityCertificateCurator idCertCurator;
    private final RefresherFactory refresherFactory;
    private final Signer signer;
    private final ExporterMetadataCurator expMetaCurator;
    private final CertificateSerialCurator csCurator;
    private final CdnCurator cdnCurator;
    private final EventSink sink;
    private final I18n i18n;
    private final DistributorVersionCurator distVerCurator;
    private final SyncUtils syncUtils;
    private final ImportRecordCurator importRecordCurator;
    private final SubscriptionReconciler subscriptionReconciler;
    private final ModelTranslator translator;

    @Inject
    public Importer(ConsumerTypeCurator consumerTypeCurator,
        RulesImporter rulesImporter, OwnerCurator ownerCurator, IdentityCertificateCurator idCertCurator,
        RefresherFactory refresherFactory, Signer signer, ExporterMetadataCurator emc,
        CertificateSerialCurator csc, EventSink sink, I18n i18n, DistributorVersionCurator distVerCurator,
        CdnCurator cdnCurator, SyncUtils syncUtils, @Named("ImportObjectMapper") ObjectMapper mapper,
        ImportRecordCurator importRecordCurator, SubscriptionReconciler subscriptionReconciler,
        ModelTranslator translator) {

        this.consumerTypeCurator = Objects.requireNonNull(consumerTypeCurator);
        this.rulesImporter = Objects.requireNonNull(rulesImporter);
        this.ownerCurator = Objects.requireNonNull(ownerCurator);
        this.idCertCurator = Objects.requireNonNull(idCertCurator);
        this.refresherFactory = Objects.requireNonNull(refresherFactory);
        this.syncUtils = Objects.requireNonNull(syncUtils);
        this.mapper = Objects.requireNonNull(mapper);
        this.signer = Objects.requireNonNull(signer);
        this.expMetaCurator = Objects.requireNonNull(emc);
        this.csCurator = Objects.requireNonNull(csc);
        this.sink = Objects.requireNonNull(sink);
        this.i18n = Objects.requireNonNull(i18n);
        this.distVerCurator = Objects.requireNonNull(distVerCurator);
        this.cdnCurator = Objects.requireNonNull(cdnCurator);
        this.importRecordCurator = Objects.requireNonNull(importRecordCurator);
        this.subscriptionReconciler = Objects.requireNonNull(subscriptionReconciler);
        this.translator = Objects.requireNonNull(translator);
    }

    public ImportRecord loadExport(Owner owner, File archive, ConflictOverrides overrides,
        String uploadedFileName) throws ImporterException {
        try {
            return doExport(owner, unpackExportFile(archive.getName(), new FileInputStream(archive)),
                overrides, uploadedFileName);
        }
        catch (FileNotFoundException e) {
            log.error(String.format("Could not find import archive: %s", archive.getAbsolutePath()));
            throw new ImporterException(i18n.tr("Uploaded manifest file does not exist."), e);
        }
    }

    /**
     * Loads a manifest from the {@link ManifestFileService}'s stored location.
     *
     * @param export the exported manifest file to load.
     * @param owner the {@link Owner} to import data into.
     * @param overrides the conflicts that are to be overridden.
     * @param uploadedFileName the name of the file that was initially uploaded.
     * @return the resulting {@link ImportRecord}
     * @throws ImporterException if the export could not be loaded.
     */
    public ImportRecord loadStoredExport(ManifestFile export, Owner owner, ConflictOverrides overrides,
        String uploadedFileName) throws ImporterException {
        try {
            return doExport(owner, extractFromService(export), overrides, uploadedFileName);
        }
        catch (ManifestFileServiceException e) {
            throw new ImporterException("Could not load stored manifest file for async import", e);
        }
    }

    /**
     * Records a successful import of a manifest.
     *
     * @param owner the owner that the manifest was imported into.
     * @param data the data to store in this record.
     * @param forcedConflicts the conflicts that were forced.
     * @param filename the name of the originally uploaded file.
     * @return the newly created {@link ImportRecord}.
     */
    public ImportRecord recordImportSuccess(Owner owner, Map<String, Object> data,
        ConflictOverrides forcedConflicts, String filename) {

        ImportRecord record = new ImportRecord(owner);
        Meta meta = (Meta) data.get("meta");
        if (meta != null) {
            record.setGeneratedBy(meta.getPrincipalName());
            record.setGeneratedDate(meta.getCreated());
        }
        record.setUpstreamConsumer(createImportUpstreamConsumer(owner, null));
        record.setFileName(filename);

        List<SubscriptionDTO> subscriptions = (List<SubscriptionDTO>) data.get("subscriptions");
        boolean activeSubscriptionFound = false;
        boolean expiredSubscriptionFound = false;
        Date currentDate = new Date();
        for (SubscriptionDTO subscription : subscriptions) {
            if (subscription.getEndDate() == null || subscription.getEndDate().after(currentDate)) {
                activeSubscriptionFound = true;
            }
            else {
                expiredSubscriptionFound = true;
                sink.emitSubscriptionExpired(subscription);
            }
        }

        String msg = i18n.tr("{0} file imported successfully.", owner.getKey());
        if (!forcedConflicts.isEmpty()) {
            msg = i18n.tr("{0} file imported forcibly.", owner.getKey());
        }

        if (!activeSubscriptionFound) {
            msg += " ";
            msg += i18n.tr("No active subscriptions found in the file.");
            record.recordStatus(ImportRecord.Status.SUCCESS_WITH_WARNING, msg);
        }
        else if (expiredSubscriptionFound) {
            msg += " ";
            msg += i18n.tr("One or more inactive subscriptions found in the file.");
            record.recordStatus(ImportRecord.Status.SUCCESS_WITH_WARNING, msg);
        }
        else {
            record.recordStatus(ImportRecord.Status.SUCCESS, msg);
        }

        this.importRecordCurator.create(record);
        return record;
    }

    public void recordImportFailure(Owner owner, Throwable error, String filename) {
        ImportRecord record = new ImportRecord(owner);
        log.error("Recording import failure", error);

        if (error instanceof ImporterException) {
            Meta meta = (Meta) ((ImporterException) error).getCollectedData().get("meta");
            if (meta != null) {
                record.setGeneratedBy(meta.getPrincipalName());
                record.setGeneratedDate(meta.getCreated());
            }
        }
        record.setUpstreamConsumer(createImportUpstreamConsumer(owner, null));
        record.setFileName(filename);

        record.recordStatus(ImportRecord.Status.FAILURE, error.getMessage());

        this.importRecordCurator.create(record);
    }

    // NOTE: Some DBs, such as postgres, require large object streaming to be in a single transaction.
    //       Because of this, we make this method transactional.

    /**
     * Pulls the manifest from the {@link ManifestFileService} and unpacks it. The manifest file
     * is deleted as soon as it is extracted.
     *
     * @param export the manifest's file.
     * @return a {@link File} pointing to the unpacked manifest directory.
     * @throws ManifestFileServiceException
     * @throws ImporterException
     */
    @Transactional
    protected File extractFromService(ManifestFile export)
        throws ManifestFileServiceException, ImporterException {
        return unpackExportFile(export.getId(), export.getInputStream());
    }

    /**
     * Check to make sure the meta data is newer than the imported data.
     * @param type ExporterMetadata.TYPE_PER_USER or TYPE_SYSTEM
     * @param owner Owner in the case of PER_USER
     * @param meta meta.json file
     * @param forcedConflicts Conflicts we will override if encountered
     * @throws IOException thrown if there's a problem reading the file
     * @throws ImporterException thrown if the metadata is invalid.
     */
    protected ExporterMetadata validateMetadata(String type, Owner owner, File meta,
        ConflictOverrides forcedConflicts) throws IOException, ImporterException {

        Meta m = mapper.readValue(meta, Meta.class);
        if (type == null) {
            throw new ImporterException(i18n.tr("Wrong metadata type"));
        }

        ExporterMetadata lastrun = null;
        if (ExporterMetadata.TYPE_SYSTEM.equals(type)) {
            lastrun = expMetaCurator.getByType(type);
        }
        else if (ExporterMetadata.TYPE_PER_USER.equals(type)) {
            if (owner == null) {
                throw new ImporterException(i18n.tr("Invalid owner"));
            }

            lastrun = expMetaCurator.getByTypeAndOwner(type, owner);
        }

        if (lastrun == null) {
            // this is our first import, let's create a new entry
            lastrun = new ExporterMetadata(type, m.getCreated(), owner);
        }
        else {
            if (lastrun.getExported().after(m.getCreated())) {
                if (!forcedConflicts.isForced(Importer.Conflict.MANIFEST_OLD)) {
                    throw new ImportConflictException(i18n.tr("Import is older than existing data"),
                        Importer.Conflict.MANIFEST_OLD);
                }
                else {
                    log.warn("Manifest older than existing data.");
                }
            }
            else {
                /*
                 *  Prior to 5.6.4, MySQL did not store fractions of a second in
                 *  temporal values.  Consequently, the manifest metadata can end up
                 *  with a created date that is a fraction of a second ahead of
                 *  the created date in the cp_export_metadata table.  So we throw away
                 *  the fractions of a second.
                 */
                long exported = lastrun.getExported().getTime() / 1000;
                long created = m.getCreated().getTime() / 1000;
                if (exported == created) {
                    if (!forcedConflicts.isForced(Importer.Conflict.MANIFEST_SAME)) {
                        throw new ImportConflictException(i18n.tr("Import is the same as existing data"),
                            Importer.Conflict.MANIFEST_SAME);
                    }
                    else {
                        log.warn("Manifest same as existing data.");
                    }
                }
            }

            lastrun.setExported(m.getCreated());
        }

        return lastrun;
    }

    private ImportRecord doExport(Owner owner, File exportDir, ConflictOverrides overrides,
        String uploadedFileName) throws ImporterException {

        Map<String, Object> result = new HashMap<>();
        try {
            File signature = new File(exportDir, "signature");
            if (signature.length() == 0) {
                throw new ImportExtractionException(
                    i18n.tr("The archive does not contain the required signature file"));
            }

            boolean verifiedSignature = this.signer.verifySignature(
                new File(exportDir, "consumer_export.zip"),
                loadSignature(new File(exportDir, "signature"))
            );

            if (!verifiedSignature) {
                log.warn("Archive signature check failed.");

                if (!overrides.isForced(Conflict.SIGNATURE_CONFLICT)) {
                    /*
                     * Normally for import conflicts that can be overridden, we try to
                     * report them all the first time so if the user intends to override,
                     * they can do so with just one more request. However in the case of
                     * a bad signature, we're going to report immediately due to the nature
                     * of what this might mean.
                     */
                    throw new ImportConflictException(i18n.tr("Archive failed signature check"),
                        Conflict.SIGNATURE_CONFLICT);
                }
                else {
                    log.warn("Ignoring signature check failure.");
                }
            }

            File consumerExport = new File(exportDir, "consumer_export.zip");
            File consumerExportDir = extractArchive(exportDir, consumerExport.getName(),
                new FileInputStream(consumerExport));

            Map<String, File> importFiles = new HashMap<>();
            File[] listFiles = consumerExportDir.listFiles();
            if (listFiles == null || listFiles.length == 0) {
                throw new ImportExtractionException(
                    i18n.tr("The provided manifest has no content in the exported consumer archive"));
            }
            for (File file : listFiles) {
                importFiles.put(file.getName(), file);
            }

            // Need the rules file as well which is in a nested dir:
            File rulesFile = new File(consumerExportDir, ImportFile.RULES_FILE.fileName());
            importFiles.put(ImportFile.RULES_FILE.fileName(), rulesFile);

            List<SubscriptionDTO> importSubs = importObjects(owner, importFiles, overrides);
            Meta m = mapper.readValue(importFiles.get(ImportFile.META.fileName()), Meta.class);

            result.put("subscriptions", importSubs);
            result.put("meta", m);

            sink.emitImportCreated(owner);
            return recordImportSuccess(owner, result, overrides, uploadedFileName);
        }
        catch (FileNotFoundException fnfe) {
            log.error("Archive file does not contain consumer_export.zip", fnfe);
            throw new ImportExtractionException(i18n.tr("The archive does not contain " +
                "the required consumer_export.zip file"));
        }
        catch (ConstraintViolationException cve) {
            log.error("Failed to import archive", cve);
            throw new ImporterException(i18n.tr("Failed to import archive"), cve, result);
        }
        catch (PersistenceException pe) {
            log.error("Failed to import archive", pe);
            throw new ImporterException(i18n.tr("Failed to import archive"), pe, result);
        }
        catch (IOException e) {
            log.error("Exception caught importing archive", e);
            throw new ImportExtractionException(i18n.tr("Unable to extract export archive"), e, result);
        }
        finally {
            if (exportDir != null) {
                try {
                    FileUtils.deleteDirectory(exportDir);
                }
                catch (IOException e) {
                    log.error("Failed to delete extracted export", e);
                }
            }
        }
    }

    @SuppressWarnings("checkstyle:methodlength")
    @Transactional(rollbackOn = { IOException.class, ImporterException.class,
        RuntimeException.class, ImportConflictException.class })
    // WARNING: Keep this method public, otherwise @Transactional is ignored:
    public List<SubscriptionDTO> importObjects(Owner owner, Map<String, File> importFiles,
        ConflictOverrides overrides) throws IOException, ImporterException {

        ownerCurator.lock(owner);

        log.debug("Importing objects for owner: {}", owner);

        File metadata = importFiles.get(ImportFile.META.fileName());
        if (metadata == null) {
            throw new ImporterException(i18n.tr("The archive does not contain the required meta.json file"));
        }
        File consumerTypes = importFiles.get(ImportFile.CONSUMER_TYPE.fileName());
        if (consumerTypes == null) {
            throw new ImporterException(
                i18n.tr("The archive does not contain the required consumer_types directory"));
        }
        File consumerFile = importFiles.get(ImportFile.CONSUMER.fileName());
        if (consumerFile == null) {
            throw new ImporterException(
                i18n.tr("The archive does not contain the required consumer.json file"));
        }
        File products = importFiles.get(ImportFile.PRODUCTS.fileName());
        File entitlements = importFiles.get(ImportFile.ENTITLEMENTS.fileName());
        if (products != null && entitlements == null) {
            throw new ImporterException(
                i18n.tr("The archive does not contain the required entitlements directory"));
        }

        // Exporter metadata we need to persist after we validate everything
        List<ExporterMetadata> exporterMetadata = new ArrayList<>();

        // system level elements
        /*
         * Checking a system wide last import date breaks multi-tenant deployments whenever
         * one org imports a manifest slightly older than another org who has already
         * imported. Disabled for now. See bz #769644.
         */
        // exporterMetadata.add(this.validateMetadata(ExporterMetadata.TYPE_SYSTEM, null, metadata, force));

        // If any calls find conflicts we'll assemble them into one exception detailing all
        // the conflicts which occurred, so the caller can override them all at once
        // if desired:
        List<ImportConflictException> conflictExceptions = new LinkedList<>();

        File rules = importFiles.get(ImportFile.RULES_FILE.fileName());
        importRules(rules, metadata);

        importConsumerTypes(consumerTypes.listFiles());

        File distributorVersions = importFiles.get(ImportFile.DISTRIBUTOR_VERSIONS.fileName());
        if (distributorVersions != null) {
            importDistributorVersions(distributorVersions.listFiles());
        }

        File cdns = importFiles.get(ImportFile.CONTENT_DELIVERY_NETWORKS.fileName());
        if (cdns != null) {
            importContentDeliveryNetworks(cdns.listFiles());
        }

        // per user elements
        try {
            exporterMetadata.add(
                this.validateMetadata(ExporterMetadata.TYPE_PER_USER, owner, metadata, overrides));
        }
        catch (ImportConflictException e) {
            conflictExceptions.add(e);
        }

        ConsumerDTO consumer = null;
        try {
            Meta m = mapper.readValue(metadata, Meta.class);
            File upstreamFile = importFiles.get(ImportFile.UPSTREAM_CONSUMER.fileName());
            File[] dafiles = new File[0];

            if (upstreamFile != null) {
                dafiles = upstreamFile.listFiles();
            }

            consumer = importConsumer(owner, consumerFile, dafiles, overrides, m);
        }
        catch (ImportConflictException e) {
            conflictExceptions.add(e);
        }

        // At this point we're done checking for any potential conflicts:
        if (!conflictExceptions.isEmpty()) {
            log.error("Conflicts occurred during import that were not overridden:");
            for (ImportConflictException e : conflictExceptions) {
                log.error("{}", e.message().getConflicts());
            }

            throw new ImportConflictException(conflictExceptions);
        }

        if (consumer == null) {
            throw new IllegalStateException("No consumer found during import");
        }

        // Persist the exporter metadata now that we're validated and at the point where we can
        // commit data
        this.expMetaCurator.saveOrUpdateAll(exporterMetadata, true, false);

        // If the consumer has no entitlements, this products directory will end up empty.
        // This also implies there will be no entitlements to import.
        Meta meta = mapper.readValue(metadata, Meta.class);
        Map<String, ProductDTO> importedProductsMap;
        List<SubscriptionDTO> importedSubs;

        // TODO: If EntitlementImporter is ever updated to be more on-demand like the ProductImporter
        // refactor, update this block/class to not be doing half of the entitlement importing bits.
        File productsDir = importFiles.get(ImportFile.PRODUCTS.fileName());
        if (productsDir != null) {
            ProductImporter productImporter = new ProductImporter(productsDir, this.mapper, this.i18n);

            importedProductsMap = productImporter.importProductMap();
            importedSubs = this.importEntitlements(owner, importedProductsMap, entitlements.listFiles(),
                consumer.getUuid(), meta);
        }
        else {
            log.warn("No products found to import, skipping product import.");
            log.warn("No entitlements in manifest, removing all subscriptions for owner.");

            importedProductsMap = null;
            importedSubs = this.importEntitlements(owner, importedProductsMap, null, consumer.getUuid(),
                meta);
        }

        // Setup our import subscription adapter with the subscriptions imported:
        SubscriptionServiceAdapter subAdapter = new ImportSubscriptionServiceAdapter(importedSubs);

        this.refresherFactory.getRefresher(subAdapter)
            .add(owner)
            .run();

        return importedSubs;
    }

    protected void importRules(File rulesFile, File metadata) throws IOException {
        try (Reader reader = new FileReader(rulesFile)) {
            rulesImporter.importObject(reader);
        }
        catch (FileNotFoundException e) {
            log.warn("Skipping rules import, manifest does not contain rules file: {}",
                ImportFile.RULES_FILE.fileName(), e);
        }
    }

    protected void importConsumerTypes(File[] consumerTypes) throws IOException {
        ConsumerTypeImporter importer = new ConsumerTypeImporter(consumerTypeCurator);
        Set<ConsumerType> consumerTypeObjs = new HashSet<>();

        for (File consumerType : consumerTypes) {
            try (Reader reader = new FileReader(consumerType)) {
                consumerTypeObjs.add(importer.createObject(mapper, reader));
            }
        }

        importer.store(consumerTypeObjs);
    }

    protected ConsumerDTO importConsumer(Owner owner, File consumerFile, File[] upstreamConsumer,
        ConflictOverrides forcedConflicts, Meta meta) throws IOException, SyncDataFormatException {

        IdentityCertificate idcert = null;
        for (File uc : upstreamConsumer) {
            if (uc.getName().endsWith(".json")) {
                log.debug("Import upstream consumeridentity certificate: {}", uc.getName());

                try (Reader reader = new FileReader(uc)) {
                    CertificateDTO dtoCert = mapper.readValue(reader, CertificateDTO.class);
                    idcert = new IdentityCertificate();
                    ImporterUtils.populateEntity(idcert, dtoCert);
                    idcert.setId(dtoCert.getId());
                }
            }
            else {
                log.warn("Extra file found in upstream_consumer directory: {}", uc.getName());
            }
        }

        ConsumerImporter importer = new ConsumerImporter(ownerCurator, idCertCurator, i18n, csCurator);
        ConsumerDTO consumer = null;

        try (Reader reader = new FileReader(consumerFile)) {
            consumer = importer.createObject(mapper, reader);
            // we can not rely on the actual ConsumerType in the ConsumerDto
            // because it could have an id not in our database. We need to
            // stick with the label. Hence we need to lookup the ACTUAL type
            // by label here before attempting to store the UpstreamConsumer
            ConsumerType type = consumerTypeCurator.getByLabel(consumer.getType().getLabel());
            consumer.setType(this.translator.translate(type, ConsumerTypeDTO.class));

            // in older manifests the web app prefix will not
            // be on the consumer, we can use the one stored in
            // the metadata
            if (StringUtils.isEmpty(consumer.getUrlWeb())) {
                consumer.setUrlWeb(meta.getWebAppPrefix());
            }

            importer.store(owner, consumer, forcedConflicts, idcert);
        }

        return consumer;
    }

    protected List<SubscriptionDTO> importEntitlements(Owner owner,
        Map<String, ProductDTO> importedProductsMap, File[] entitlementFiles, String consumerUuid, Meta meta)
        throws IOException, SyncDataFormatException {

        log.debug("Importing entitlements for owner: {}", owner);

        List<SubscriptionDTO> subscriptionsToImport = new ArrayList<>();

        if (importedProductsMap != null && entitlementFiles != null) {
            EntitlementImporter importer = new EntitlementImporter(cdnCurator, i18n, translator,
                importedProductsMap);

            for (File entitlement : entitlementFiles) {
                try (Reader reader = new FileReader(entitlement)) {
                    log.debug("Importing entitlement from file: {}", entitlement.getName());
                    subscriptionsToImport.add(
                        importer.importObject(mapper, reader, owner, consumerUuid, meta));
                }
            }
        }

        // Reconcile the subscriptions so they line up with pools we're tracking
        this.subscriptionReconciler.reconcile(owner, subscriptionsToImport);

        return subscriptionsToImport;
    }

    /**
     * Create a tar.gz archive of the exported directory.
     *
     * @param tempDir Directory where Candlepin data was exported.
     * @return File reference to the new archive tar.gz.
     */
    private File extractArchive(File tempDir, String exportFileName, InputStream exportFileStream)
        throws IOException, ImportExtractionException {

        log.debug("Extracting archive to: {}", tempDir.getAbsolutePath());

        byte[] buf = new byte[1024];

        try (ZipInputStream zipinputstream = new ZipInputStream(exportFileStream)) {
            ZipEntry zipentry = zipinputstream.getNextEntry();

            if (zipentry == null) {
                throw new ImportExtractionException(i18n.tr(
                    "The archive {0} is not a properly compressed file or is empty", exportFileName));
            }

            while (zipentry != null) {
                //for each entry to be extracted
                String entryName = zipentry.getName();
                log.debug("entryname {}", entryName);

                File newFile = new File(tempDir, entryName).getCanonicalFile();

                // Check if the file is outside of the target directory
                if (!newFile.getPath().startsWith(tempDir.getCanonicalPath())) {
                    throw new ImportExtractionException(i18n.tr(
                        "Entry is outside of the target directory: {0}", entryName));
                }

                String directory = newFile.getParent();
                if (directory != null) {
                    new File(directory).mkdirs();
                }

                try (FileOutputStream fileoutputstream = new FileOutputStream(newFile)) {
                    int n;
                    while ((n = zipinputstream.read(buf, 0, 1024)) > -1) {
                        fileoutputstream.write(buf, 0, n);
                    }
                }

                zipinputstream.closeEntry();
                zipentry = zipinputstream.getNextEntry();
            }
        }

        return new File(tempDir.getAbsolutePath(), "export");
    }

    private byte[] loadSignature(File signatureFile) throws IOException {
        // signature is never going to be a huge file, therefore cast is a-okay
        byte[] signatureBytes = new byte[(int) signatureFile.length()];
        try (FileInputStream signature = new FileInputStream(signatureFile)) {
            int offset = 0;
            int numRead = 0;
            while (offset < signatureBytes.length && numRead >= 0) {
                numRead = signature.read(signatureBytes, offset, signatureBytes.length - offset);
                offset += numRead;
            }

            return signatureBytes;
        }
    }

    protected void importDistributorVersions(File[] versionFiles) throws IOException {
        DistributorVersionImporter importer = new DistributorVersionImporter(distVerCurator);
        Set<DistributorVersionDTO> distVers = new HashSet<>();

        for (File verFile : versionFiles) {
            try (Reader reader = new FileReader(verFile)) {
                distVers.add(importer.createObject(mapper, reader));
            }
        }
        importer.store(distVers);
    }

    protected void importContentDeliveryNetworks(File[] cdnFiles) throws IOException {
        CdnImporter importer = new CdnImporter(cdnCurator);
        Set<CdnDTO> cdns = new HashSet<>();

        for (File cdnFile : cdnFiles) {
            try (Reader reader = new FileReader(cdnFile)) {
                cdns.add(importer.createObject(mapper, reader));
            }
        }

        importer.store(cdns);
    }

    private ImportUpstreamConsumer createImportUpstreamConsumer(Owner owner, UpstreamConsumer uc) {
        ImportUpstreamConsumer iup = null;
        if (uc == null && owner != null) {
            uc = owner.getUpstreamConsumer();
        }

        // The owner may not have the upstream consumer set so we need
        // to check.
        if (uc != null) {
            iup = new ImportUpstreamConsumer(uc);
        }

        return iup;
    }

    private File unpackExportFile(String fileName, InputStream exportInputStream)
        throws ImportExtractionException {
        try {
            File tmpDir = syncUtils.makeTempDir("import");
            extractArchive(tmpDir, fileName, exportInputStream);
            return tmpDir;
        }
        catch (IOException e) {
            log.error("Unable to extract export archive", e);
            throw new ImportExtractionException(i18n.tr("Unable to extract export archive"), e);
        }
    }

}
