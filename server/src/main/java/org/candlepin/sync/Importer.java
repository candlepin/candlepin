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
package org.candlepin.sync;

import org.candlepin.audit.EventSink;
import org.candlepin.common.config.Configuration;
import org.candlepin.controller.PoolManager;
import org.candlepin.controller.Refresher;
import org.candlepin.model.Cdn;
import org.candlepin.model.CdnCurator;
import org.candlepin.model.CertificateSerialCurator;
import org.candlepin.model.ConsumerType;
import org.candlepin.model.ConsumerTypeCurator;
import org.candlepin.model.ContentCurator;
import org.candlepin.model.DistributorVersion;
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
import org.candlepin.model.Product;
import org.candlepin.model.ProductCurator;
import org.candlepin.model.UpstreamConsumer;
import org.candlepin.model.dto.Subscription;
import org.candlepin.pki.PKIUtility;
import org.candlepin.service.SubscriptionServiceAdapter;
import org.candlepin.service.impl.ImportSubscriptionServiceAdapter;
import org.candlepin.sync.file.ManifestFile;
import org.candlepin.sync.file.ManifestFileServiceException;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringUtils;
import org.hibernate.exception.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xnap.commons.i18n.I18n;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.security.cert.CertificateException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import javax.persistence.PersistenceException;


/**
 * Importer
 */
public class Importer {
    private static Logger log = LoggerFactory.getLogger(Importer.class);

    /**
     *
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


    private ConsumerTypeCurator consumerTypeCurator;
    private ProductCurator productCurator;
    private ObjectMapper mapper;
    private RulesImporter rulesImporter;
    private OwnerCurator ownerCurator;
    private ContentCurator contentCurator;
    private IdentityCertificateCurator idCertCurator;
    private PoolManager poolManager;
    private PKIUtility pki;
    private Configuration config;
    private ExporterMetadataCurator expMetaCurator;
    private CertificateSerialCurator csCurator;
    private CdnCurator cdnCurator;
    private EventSink sink;
    private I18n i18n;
    private DistributorVersionCurator distVerCurator;
    private ImportRecordCurator importRecordCurator;
    private SyncUtils syncUtils;

    @Inject
    public Importer(ConsumerTypeCurator consumerTypeCurator, ProductCurator productCurator,
        RulesImporter rulesImporter, OwnerCurator ownerCurator,
        IdentityCertificateCurator idCertCurator,
        ContentCurator contentCurator, PoolManager pm,
        PKIUtility pki, Configuration config, ExporterMetadataCurator emc,
        CertificateSerialCurator csc, EventSink sink, I18n i18n,
        DistributorVersionCurator distVerCurator,
        CdnCurator cdnCurator, SyncUtils syncUtils, ImportRecordCurator importRecordCurator) {

        this.config = config;
        this.consumerTypeCurator = consumerTypeCurator;
        this.productCurator = productCurator;
        this.rulesImporter = rulesImporter;
        this.ownerCurator = ownerCurator;
        this.idCertCurator = idCertCurator;
        this.contentCurator = contentCurator;
        this.poolManager = pm;
        this.syncUtils = syncUtils;
        this.mapper = syncUtils.getObjectMapper();
        this.pki = pki;
        this.expMetaCurator = emc;
        this.csCurator = csc;
        this.sink = sink;
        this.i18n = i18n;
        this.distVerCurator = distVerCurator;
        this.cdnCurator = cdnCurator;
        this.importRecordCurator = importRecordCurator;
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
            ImportRecord result = doExport(owner, extractFromService(export), overrides, uploadedFileName);
            return result;
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

        List<Subscription> subscriptions = (List<Subscription>) data.get("subscriptions");
        boolean activeSubscriptionFound = false, expiredSubscriptionFound = false;
        Date currentDate = new Date();
        for (Subscription subscription : subscriptions) {
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
            msg += i18n.tr("No active subscriptions found in the file.");
            record.recordStatus(ImportRecord.Status.SUCCESS_WITH_WARNING, msg);
        }
        else if (expiredSubscriptionFound) {
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
     * @param storedFileId the manifest's file ID.
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
    protected void validateMetadata(String type, Owner owner, File meta, ConflictOverrides forcedConflicts)
        throws IOException, ImporterException {

        Meta m = mapper.readValue(meta, Meta.class);
        if (type == null) {
            throw new ImporterException(i18n.tr("Wrong metadata type"));
        }

        ExporterMetadata lastrun = null;
        if (ExporterMetadata.TYPE_SYSTEM.equals(type)) {
            lastrun = expMetaCurator.lookupByType(type);
        }
        else if (ExporterMetadata.TYPE_PER_USER.equals(type)) {
            if (owner == null) {
                throw new ImporterException(i18n.tr("Invalid owner"));
            }
            lastrun = expMetaCurator.lookupByTypeAndOwner(type, owner);
        }

        if (lastrun == null) {
            // this is our first import, let's create a new entry
            lastrun = new ExporterMetadata(type, m.getCreated(), owner);
            lastrun = expMetaCurator.create(lastrun);
        }
        else {
            if (lastrun.getExported().after(m.getCreated())) {
                if (!forcedConflicts.isForced(Importer.Conflict.MANIFEST_OLD)) {
                    throw new ImportConflictException(i18n.tr(
                        "Import is older than existing data"),
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
                        throw new ImportConflictException(i18n.tr(
                            "Import is the same as existing data"),
                            Importer.Conflict.MANIFEST_SAME);
                    }
                    else {
                        log.warn("Manifest same as existing data.");
                    }
                }
            }

            lastrun.setExported(m.getCreated());
            expMetaCurator.merge(lastrun);
        }
    }

    private ImportRecord doExport(Owner owner, File exportDir, ConflictOverrides overrides,
        String uploadedFileName) throws ImporterException {
        Map<String, Object> result = new HashMap<String, Object>();
        try {
            File signature = new File(exportDir, "signature");
            if (signature.length() == 0) {
                throw new ImportExtractionException(i18n.tr("The archive does not " +
                                          "contain the required signature file"));
            }

            boolean verifiedSignature = pki.verifySHA256WithRSAHashAgainstCACerts(
                new File(exportDir, "consumer_export.zip"),
                loadSignature(new File(exportDir, "signature")));
            if (!verifiedSignature) {
                log.warn("Archive signature check failed.");
                if (!overrides
                    .isForced(Conflict.SIGNATURE_CONFLICT)) {

                    /*
                     * Normally for import conflicts that can be overridden, we try to
                     * report them all the first time so if the user intends to override,
                     * they can do so with just one more request. However in the case of
                     * a bad signature, we're going to report immediately due to the nature
                     * of what this might mean.
                     */
                    throw new ImportConflictException(
                        i18n.tr("Archive failed signature check"),
                        Conflict.SIGNATURE_CONFLICT);
                }
                else {
                    log.warn("Ignoring signature check failure.");
                }
            }

            File consumerExport = new File(exportDir, "consumer_export.zip");
            File consumerExportDir = extractArchive(exportDir, consumerExport.getName(),
                new FileInputStream(consumerExport));

            Map<String, File> importFiles = new HashMap<String, File>();
            File[] listFiles = consumerExportDir.listFiles();
            if (listFiles == null || listFiles.length == 0) {
                throw new ImportExtractionException(i18n.tr("The consumer_export " +
                    "archive has no contents"));
            }
            for (File file : listFiles) {
                importFiles.put(file.getName(), file);
            }

            // Need the rules file as well which is in a nested dir:
            File rulesFile = new File(consumerExportDir, ImportFile.RULES_FILE.fileName());
            importFiles.put(ImportFile.RULES_FILE.fileName(), rulesFile);

            List<Subscription> importSubs = importObjects(owner, importFiles, overrides);
            Meta m = mapper.readValue(importFiles.get(ImportFile.META.fileName()),
                Meta.class);
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
            throw new ImporterException(i18n.tr("Failed to import archive"),
                cve, result);
        }
        catch (PersistenceException pe) {
            log.error("Failed to import archive", pe);
            throw new ImporterException(i18n.tr("Failed to import archive"),
                pe, result);
        }
        catch (IOException e) {
            log.error("Exception caught importing archive", e);
            throw new ImportExtractionException(
                i18n.tr("Unable to extract export archive"), e, result);
        }
        catch (CertificateException e) {
            log.error("Certificate exception checking archive signature", e);
            throw new ImportExtractionException(
                i18n.tr("Certificate exception checking archive signature"), e, result);
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

    @Transactional(rollbackOn = {IOException.class, ImporterException.class,
        RuntimeException.class, ImportConflictException.class})
    // WARNING: Keep this method public, otherwise @Transactional is ignored:
    public List<Subscription> importObjects(Owner owner, Map<String, File> importFiles,
        ConflictOverrides overrides)
        throws IOException, ImporterException {

        log.debug("Importing objects for owner: {}", owner);

        File metadata = importFiles.get(ImportFile.META.fileName());
        if (metadata == null) {
            throw new ImporterException(i18n.tr("The archive does not contain the " +
                                   "required meta.json file"));
        }
        File consumerTypes = importFiles.get(ImportFile.CONSUMER_TYPE.fileName());
        if (consumerTypes == null) {
            throw new ImporterException(i18n.tr("The archive does not contain the " +
                                    "required consumer_types directory"));
        }
        File consumerFile = importFiles.get(ImportFile.CONSUMER.fileName());
        if (consumerFile == null) {
            throw new ImporterException(i18n.tr("The archive does not contain the " +
                "required consumer.json file"));
        }
        File products = importFiles.get(ImportFile.PRODUCTS.fileName());
        File entitlements = importFiles.get(ImportFile.ENTITLEMENTS.fileName());
        if (products != null && entitlements == null) {
            throw new ImporterException(i18n.tr("The archive does not contain the " +
                                        "required entitlements directory"));
        }


        // system level elements
        /*
         * Checking a system wide last import date breaks multi-tenant deployments whenever
         * one org imports a manifest slightly older than another org who has already
         * imported. Disabled for now. See bz #769644.
         */
//        validateMetadata(ExporterMetadata.TYPE_SYSTEM, null, metadata, force);

        // If any calls find conflicts we'll assemble them into one exception detailing all
        // the conflicts which occurred, so the caller can override them all at once
        // if desired:
        List<ImportConflictException> conflictExceptions =
            new LinkedList<ImportConflictException>();

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
            validateMetadata(ExporterMetadata.TYPE_PER_USER, owner, metadata,
                overrides);
        }
        catch (ImportConflictException e) {
            conflictExceptions.add(e);
        }

        ConsumerDto consumer = null;
        try {
            Meta m = mapper.readValue(metadata, Meta.class);
            File upstreamFile = importFiles.get(ImportFile.UPSTREAM_CONSUMER.fileName());
            File[] dafiles = new File[0];
            if (upstreamFile != null) {
                dafiles = upstreamFile.listFiles();
            }
            consumer = importConsumer(owner, consumerFile,
                dafiles, overrides, m);
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

        // If the consumer has no entitlements, this products directory will end up empty.
        // This also implies there will be no entitlements to import.
        Meta meta = mapper.readValue(metadata, Meta.class);
        List<Subscription> importSubs = new ArrayList<Subscription>();
        if (importFiles.get(ImportFile.PRODUCTS.fileName()) != null) {
            ProductImporter importer = new ProductImporter();

            Set<Product> productsToImport = importProducts(
                importFiles.get(ImportFile.PRODUCTS.fileName()).listFiles(), importer, owner
            );

            importSubs = importEntitlements(owner, productsToImport,
                    entitlements.listFiles(), consumer, meta);
        }
        else {
            log.warn("No products found to import, skipping product import.");
            log.warn("No entitlements in manifest, removing all subscriptions for owner.");
            importEntitlements(owner, new HashSet<Product>(), new File[]{}, consumer, meta);
        }

        // Setup our import subscription adapter with the subscriptions imported:
        SubscriptionServiceAdapter adapter =
            new ImportSubscriptionServiceAdapter(importSubs);
        Refresher refresher = poolManager.getRefresher(adapter);
        refresher.add(owner);
        refresher.run();

        return importSubs;
    }

    protected void importRules(File rulesFile, File metadata) throws IOException {

        Reader reader = null;
        try {
            reader = new FileReader(rulesFile);
            rulesImporter.importObject(reader);
        }
        catch (FileNotFoundException fnfe) {
            log.warn("Skipping rules import, manifest does not contain rules file: " +
                ImportFile.RULES_FILE.fileName());
            return;
        }
        finally {
            if (reader != null) {
                reader.close();
            }
        }
    }

    protected void importConsumerTypes(File[] consumerTypes) throws IOException {
        ConsumerTypeImporter importer = new ConsumerTypeImporter(consumerTypeCurator);
        Set<ConsumerType> consumerTypeObjs = new HashSet<ConsumerType>();
        for (File consumerType : consumerTypes) {
            Reader reader = null;
            try {
                reader = new FileReader(consumerType);
                consumerTypeObjs.add(importer.createObject(mapper, reader));
            }
            finally {
                if (reader != null) {
                    reader.close();
                }
            }
        }
        importer.store(consumerTypeObjs);
    }

    protected ConsumerDto importConsumer(Owner owner, File consumerFile,
        File[] upstreamConsumer, ConflictOverrides forcedConflicts, Meta meta)
        throws IOException, SyncDataFormatException {

        IdentityCertificate idcert = null;
        for (File uc : upstreamConsumer) {
            if (uc.getName().endsWith(".json")) {
                log.debug("Import upstream consumeridentity certificate: " +
                    uc.getName());
                Reader reader = null;
                try {
                    reader = new FileReader(uc);
                    idcert = mapper.readValue(reader, IdentityCertificate.class);
                }
                finally {
                    if (reader != null) {
                        reader.close();
                    }
                }
            }
            else {
                log.warn("Extra file found in upstream_consumer directory: " +
                    uc.getName());
            }
        }

        ConsumerImporter importer = new ConsumerImporter(ownerCurator, idCertCurator,
            i18n, csCurator);
        Reader reader = null;
        ConsumerDto consumer = null;
        try {
            reader = new FileReader(consumerFile);
            consumer = importer.createObject(mapper, reader);
            // we can not rely on the actual ConsumerType in the ConsumerDto
            // because it could have an id not in our database. We need to
            // stick with the label. Hence we need to lookup the ACTUAL type
            // by label here before attempting to store the UpstreamConsumer
            ConsumerType type = consumerTypeCurator.lookupByLabel(
                consumer.getType().getLabel());
            consumer.setType(type);

            // in older manifests the web app prefix will not
            // be on the consumer, we can use the one stored in
            // the metadata
            if (StringUtils.isEmpty(consumer.getUrlWeb())) {
                consumer.setUrlWeb(meta.getWebAppPrefix());
            }
            importer.store(owner, consumer, forcedConflicts, idcert);
        }
        finally {
            if (reader != null) {
                reader.close();
            }
        }
        return consumer;
    }

    protected Set<Product> importProducts(File[] products, ProductImporter importer, Owner owner)
        throws IOException {
        Set<Product> productsToImport = new HashSet<Product>();
        for (File product : products) {
            // Skip product.pem's, we just need the json to import:
            if (product.getName().endsWith(".json")) {
                log.debug("Importing product {} for owner {}", product.getName(), owner.getKey());

                Reader reader = null;
                try {
                    reader = new FileReader(product);
                    productsToImport.add(importer.createObject(mapper, reader, owner));
                }
                finally {
                    if (reader != null) {
                        reader.close();
                    }
                }
            }
        }

        // TODO: Do we need to cleanup unused products? Looked at this earlier and it
        // looks somewhat complex and a little bit dangerous, so we're leaving them
        // around for now.

        return productsToImport;
    }

    protected List<Subscription> importEntitlements(Owner owner, Set<Product> products, File[] entitlements,
        ConsumerDto consumer, Meta meta)
        throws IOException, SyncDataFormatException {

        log.debug("Importing entitlements for owner: {}", owner);

        EntitlementImporter importer = new EntitlementImporter(csCurator, cdnCurator,
            i18n, productCurator);

        Map<String, Product> productsById = new HashMap<String, Product>();
        for (Product product : products) {
            log.debug("Adding product owned by {} to ID map", owner.getKey());

            // Note: This may actually be causing problems with subscriptions receiving the wrong
            // version of a product
            productsById.put(product.getId(), product);
        }

        List<Subscription> subscriptionsToImport = new ArrayList<Subscription>();
        for (File entitlement : entitlements) {
            Reader reader = null;
            try {
                log.debug("Import entitlement: " + entitlement.getName());
                reader = new FileReader(entitlement);
                subscriptionsToImport.add(importer.importObject(mapper, reader, owner,
                    productsById, consumer, meta));
            }
            finally {
                if (reader != null) {
                    reader.close();
                }
            }
        }

        return subscriptionsToImport;
    }

    /**
     * Create a tar.gz archive of the exported directory.
     *
     * @param exportDir Directory where Candlepin data was exported.
     * @return File reference to the new archive tar.gz.
     */
    private File extractArchive(File tempDir, String exportFileName, InputStream exportFileStream)
        throws IOException, ImportExtractionException {
        log.debug("Extracting archive to: " + tempDir.getAbsolutePath());
        byte[] buf = new byte[1024];

        ZipInputStream zipinputstream = null;

        try {
            zipinputstream = new ZipInputStream(exportFileStream);
            ZipEntry zipentry = zipinputstream.getNextEntry();

            if (zipentry == null) {
                throw new ImportExtractionException(i18n.tr("The archive {0} is not " +
                    "a properly compressed file or is empty", exportFileName));
            }

            while (zipentry != null) {
                //for each entry to be extracted
                String entryName = zipentry.getName();
                if (log.isDebugEnabled()) {
                    log.debug("entryname " + entryName);
                }
                File newFile = new File(entryName);
                String directory = newFile.getParent();
                if (directory != null) {
                    new File(tempDir, directory).mkdirs();
                }

                FileOutputStream fileoutputstream = null;
                try {
                    fileoutputstream = new FileOutputStream(new File(tempDir, entryName));
                    int n;
                    while ((n = zipinputstream.read(buf, 0, 1024)) > -1) {
                        fileoutputstream.write(buf, 0, n);
                    }
                }
                finally {
                    if (fileoutputstream != null) {
                        fileoutputstream.close();
                    }
                }

                zipinputstream.closeEntry();
                zipentry = zipinputstream.getNextEntry();
            }
        }
        finally {
            if (zipinputstream != null) {
                zipinputstream.close();
            }
        }

        return new File(tempDir.getAbsolutePath(), "export");
    }

    private byte[] loadSignature(File signatureFile) throws IOException {
        FileInputStream signature = null;
        // signature is never going to be a huge file, therefore cast is a-okay
        byte[] signatureBytes = new byte[(int) signatureFile.length()];

        try {
            signature = new FileInputStream(signatureFile);

            int offset = 0;
            int numRead = 0;
            while (offset < signatureBytes.length && numRead >= 0) {
                numRead = signature.read(signatureBytes, offset,
                    signatureBytes.length - offset);
                offset += numRead;
            }
            return signatureBytes;
        }
        finally {
            if (signature != null) {
                try {
                    signature.close();
                }
                catch (IOException e) {
                    // nothing we can do about this
                }
            }
        }
    }

    protected void importDistributorVersions(File[] versionFiles) throws IOException {
        DistributorVersionImporter importer =
            new DistributorVersionImporter(distVerCurator);
        Set<DistributorVersion> distVers = new HashSet<DistributorVersion>();
        for (File verFile : versionFiles) {
            Reader reader = null;
            try {
                reader = new FileReader(verFile);
                distVers.add(importer.createObject(mapper, reader));
            }
            finally {
                if (reader != null) {
                    reader.close();
                }
            }
        }
        importer.store(distVers);
    }

    protected void importContentDeliveryNetworks(File[] cdnFiles) throws IOException {
        CdnImporter importer =
            new CdnImporter(cdnCurator);
        Set<Cdn> cdns = new HashSet<Cdn>();
        for (File cdnFile : cdnFiles) {
            Reader reader = null;
            try {
                reader = new FileReader(cdnFile);
                cdns.add(importer.createObject(mapper, reader));
            }
            finally {
                if (reader != null) {
                    reader.close();
                }
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
            throw new ImportExtractionException(
                i18n.tr("Unable to extract export archive"), e);
        }
    }

}
