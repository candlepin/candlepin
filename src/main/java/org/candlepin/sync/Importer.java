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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import javax.persistence.PersistenceException;

import org.apache.commons.io.FileUtils;
import org.apache.log4j.Logger;
import org.candlepin.audit.EventSink;
import org.candlepin.config.Config;
import org.candlepin.controller.PoolManager;
import org.candlepin.controller.Refresher;
import org.candlepin.model.CertificateSerialCurator;
import org.candlepin.model.ConsumerType;
import org.candlepin.model.ConsumerTypeCurator;
import org.candlepin.model.ContentCurator;
import org.candlepin.model.ExporterMetadata;
import org.candlepin.model.ExporterMetadataCurator;
import org.candlepin.model.Owner;
import org.candlepin.model.OwnerCurator;
import org.candlepin.model.Product;
import org.candlepin.model.ProductCurator;
import org.candlepin.model.Subscription;
import org.candlepin.model.SubscriptionCurator;
import org.candlepin.pki.PKIUtility;
import org.candlepin.util.VersionUtil;
import org.codehaus.jackson.map.ObjectMapper;
import org.hibernate.exception.ConstraintViolationException;
import org.xnap.commons.i18n.I18n;

import com.google.inject.Inject;
import com.google.inject.persist.Transactional;


/**
 * Importer
 */
public class Importer {
    private static Logger log = Logger.getLogger(Importer.class);

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
        RULES("rules");

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
    private SubscriptionCurator subCurator;
    private PoolManager poolManager;
    private PKIUtility pki;
    private Config config;
    private ExporterMetadataCurator expMetaCurator;
    private CertificateSerialCurator csCurator;
    private EventSink sink;
    private I18n i18n;

    @Inject
    public Importer(ConsumerTypeCurator consumerTypeCurator, ProductCurator productCurator,
        RulesImporter rulesImporter, OwnerCurator ownerCurator,
        ContentCurator contentCurator, SubscriptionCurator subCurator, PoolManager pm,
        PKIUtility pki, Config config, ExporterMetadataCurator emc,
        CertificateSerialCurator csc, EventSink sink, I18n i18n) {

        this.config = config;
        this.consumerTypeCurator = consumerTypeCurator;
        this.productCurator = productCurator;
        this.rulesImporter = rulesImporter;
        this.ownerCurator = ownerCurator;
        this.contentCurator = contentCurator;
        this.subCurator = subCurator;
        this.poolManager = pm;
        this.mapper = SyncUtils.getObjectMapper(this.config);
        this.pki = pki;
        this.expMetaCurator = emc;
        this.csCurator = csc;
        this.sink = sink;
        this.i18n = i18n;
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
    public void validateMetadata(String type, Owner owner, File meta,
        ConflictOverrides forcedConflicts)
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
            if (lastrun.getExported().compareTo(m.getCreated()) > 0) {
                if (!forcedConflicts.isForced(Importer.Conflict.MANIFEST_OLD)) {
                    throw new ImportConflictException(i18n.tr(
                        "Import is older than existing data"),
                        Importer.Conflict.MANIFEST_OLD);
                }
                else {
                    log.warn("Manifest older than existing data.");
                }
            }
            else if (lastrun.getExported().compareTo(m.getCreated()) == 0) {
                if (!forcedConflicts.isForced(Importer.Conflict.MANIFEST_SAME)) {
                    throw new ImportConflictException(i18n.tr(
                        "Import is the same as existing data"),
                        Importer.Conflict.MANIFEST_SAME);
                }
                else {
                    log.warn("Manifest same as existing data.");
                }
            }

            lastrun.setExported(m.getCreated());
            expMetaCurator.merge(lastrun);
        }
    }

    public Map<String, Object> loadExport(Owner owner, File exportFile,
        ConflictOverrides overrides)
        throws ImporterException {
        File tmpDir = null;
        InputStream exportStream = null;
        Map<String, Object> result = new HashMap<String, Object>();
        try {
            tmpDir = new SyncUtils(config).makeTempDir("import");
            extractArchive(tmpDir, exportFile);

//           only need this call when sig file is verified
//            exportStream = new FileInputStream(new File(tmpDir, "consumer_export.zip"));

            /*
             * Disabling this once again for a little bit longer. Dependent projects
             * are not yet ready for it, and we're having some difficulty with the actual
             * upstream cert to use.
             *
             * When we bring this back, we should probably report this conflict
             * immediately, rather than continuing to extract and trying to find any
             * other conflicts to pass back.
             */
//            boolean verifiedSignature = pki.verifySHA256WithRSAHashWithUpstreamCACert(
//                exportStream,
//                loadSignature(new File(tmpDir, "signature")));
//            if (!verifiedSignature) {
//                log.warn("Manifest signature check failed.");
//                if (!forcedConflicts
//                    .isForced(ImportConflicts.Conflict.SIGNATURE_CONFLICT)) {
//                    conflicts.addConflict(
//                        i18n.tr("Failed import file hash check."),
//                        ImportConflicts.Conflict.SIGNATURE_CONFLICT);
//                }
//                else {
//                    log.warn("Ignoring signature check failure.");
//                }
//            }

            File signature = new File(tmpDir, "signature");
            if (signature.length() == 0) {
                throw new ImportExtractionException(i18n.tr("The archive does not " +
                                          "contain the required signature file"));
            }

            File consumerExport = new File(tmpDir, "consumer_export.zip");
            File exportDir = extractArchive(tmpDir, consumerExport);

            Map<String, File> importFiles = new HashMap<String, File>();
            File[] listFiles = exportDir.listFiles();
            if (listFiles == null || listFiles.length == 0) {
                throw new ImportExtractionException(i18n.tr("The consumer_export " +
                    "archive has no contents"));
            }
            for (File file : listFiles) {
                importFiles.put(file.getName(), file);
            }

            ConsumerDto consumer = importObjects(owner, importFiles, overrides);
            Meta m = mapper.readValue(importFiles.get(ImportFile.META.fileName()),
                Meta.class);
            result.put("consumer", consumer);
            result.put("meta", m);
            return result;
        }
//        catch (CertificateException e) {
//            log.error("Exception caught importing archive", e);
//            throw new ImportExtractionException("unable to extract export archive", e);
//        }
        catch (FileNotFoundException fnfe) {
            log.error("Archive file does not contain consumer_export.zip", fnfe);
            throw new ImportExtractionException(i18n.tr("The archive does not contain " +
                                           "the required consumer_export.zip file"));
        }
        catch (ConstraintViolationException cve) {
            log.error("Failed to import archive", cve);
            throw new ImporterException(i18n.tr("Failed to import archive"),
                cve);
        }
        catch (PersistenceException pe) {
            log.error("Failed to import archive", pe);
            throw new ImporterException(i18n.tr("Failed to import archive"),
                pe);
        }
        catch (IOException e) {
            log.error("Exception caught importing archive", e);
            throw new ImportExtractionException("unable to extract export archive", e);
        }
        finally {
            if (tmpDir != null) {
                try {
                    FileUtils.deleteDirectory(tmpDir);
                }
                catch (IOException e) {
                    log.error("Failed to delete extracted export", e);
                }
            }
            if (exportStream != null) {
                try {
                    exportStream.close();
                }
                catch (Exception e) {
                    // nothing we can do.
                }
            }
        }
    }

    @Transactional(rollbackOn = {IOException.class, ImporterException.class,
            RuntimeException.class, ImportConflictException.class})
    // WARNING: Keep this method public, otherwise @Transactional is ignored:
    ConsumerDto importObjects(Owner owner, Map<String, File> importFiles,
        ConflictOverrides overrides)
        throws IOException, ImporterException {

        File metadata = importFiles.get(ImportFile.META.fileName());
        if (metadata == null) {
            throw new ImporterException(i18n.tr("The archive does not contain the " +
                                   "required meta.json file"));
        }
        File rules = importFiles.get(ImportFile.RULES.fileName());
        if (rules == null) {
            throw new ImporterException(i18n.tr("The archive does not contain the " +
                                    "required rules directory"));
        }
        if (rules.listFiles().length == 0) {
            throw new ImporterException(i18n.tr("The archive does not contain the " +
                "required rules file(s)"));
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

        importRules(rules.listFiles(), metadata);

        importConsumerTypes(consumerTypes.listFiles());

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
            consumer = importConsumer(owner, consumerFile, overrides);
        }
        catch (ImportConflictException e) {
            conflictExceptions.add(e);
        }

        // At this point we're done checking for any potential conflicts:
        if (!conflictExceptions.isEmpty()) {
            log.error("Conflicts occurred during import that were not overridden:");
            for (ImportConflictException e : conflictExceptions) {
                log.error(e.message().getConflicts());
            }
            throw new ImportConflictException(conflictExceptions);
        }

        // If the consumer has no entitlements, this products directory will end up empty.
        // This also implies there will be no entitlements to import.
        if (importFiles.get(ImportFile.PRODUCTS.fileName()) != null) {
            Refresher refresher = poolManager.getRefresher();
            ProductImporter importer = new ProductImporter(productCurator, contentCurator, poolManager);

            Set<Product> productsToImport = importProducts(
                importFiles.get(ImportFile.PRODUCTS.fileName()).listFiles(),
                importer);

            Set<Product> modifiedProducts = importer.getChangedProducts(productsToImport);
            for (Product product : modifiedProducts) {
                refresher.add(product);
            }

            importer.store(productsToImport);

            importEntitlements(owner, productsToImport, entitlements.listFiles());

            refresher.add(owner);
            refresher.run();
        }
        else {
            log.warn("No products found to import, skipping product and entitlement import.");
        }


        return consumer;
    }

    public void importRules(File[] rulesFiles, File metadata) throws IOException {

        // only import rules if versions are ok
        Meta m = mapper.readValue(metadata, Meta.class);

        if (VersionUtil.getRulesVersionCompatibility(m.getVersion())) {
            // Only importing a single rules file now.
            Reader reader = null;
            try {
                reader = new FileReader(rulesFiles[0]);
                rulesImporter.importObject(reader, m.getVersion());
            }
            finally {
                if (reader != null) {
                    reader.close();
                }
            }
        }
        else {
            log.warn(
                i18n.tr(
                    "Incompatible rules: import version {0} older than our version {1}.",
                    m.getVersion(), VersionUtil.getVersionString()));
            log.warn(
                i18n.tr("Manifest data will be imported without rules import."));
        }

    }

    public void importConsumerTypes(File[] consumerTypes) throws IOException {
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

    public ConsumerDto importConsumer(Owner owner, File consumerFile,
        ConflictOverrides forcedConflicts)
        throws IOException, SyncDataFormatException {
        ConsumerImporter importer = new ConsumerImporter(ownerCurator, i18n);
        Reader reader = null;
        ConsumerDto consumer = null;
        try {
            reader = new FileReader(consumerFile);
            consumer = importer.createObject(mapper, reader);
            importer.store(owner, consumer, forcedConflicts);
        }
        finally {
            if (reader != null) {
                reader.close();
            }
        }
        return consumer;
    }

    public Set<Product> importProducts(File[] products, ProductImporter importer)
        throws IOException {
        Set<Product> productsToImport = new HashSet<Product>();
        for (File product : products) {
            // Skip product.pem's, we just need the json to import:
            if (product.getName().endsWith(".json")) {
                log.debug("Import product: " + product.getName());
                Reader reader = null;
                try {
                    reader = new FileReader(product);
                    productsToImport.add(importer.createObject(mapper, reader));
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

    public void importEntitlements(Owner owner, Set<Product> products, File[] entitlements)
        throws IOException, SyncDataFormatException {
        EntitlementImporter importer = new EntitlementImporter(subCurator, csCurator,
            sink, i18n);

        Map<String, Product> productsById = new HashMap<String, Product>();
        for (Product product : products) {
            productsById.put(product.getId(), product);
        }

        Set<Subscription> subscriptionsToImport = new HashSet<Subscription>();
        for (File entitlement : entitlements) {
            Reader reader = null;
            try {
                log.debug("Import entitlement: " + entitlement.getName());
                reader = new FileReader(entitlement);
                subscriptionsToImport.add(importer.importObject(mapper, reader, owner, productsById));
            }
            finally {
                if (reader != null) {
                    reader.close();
                }
            }
        }

        importer.store(owner, subscriptionsToImport);
    }

    /**
     * Create a tar.gz archive of the exported directory.
     *
     * @param exportDir Directory where Candlepin data was exported.
     * @return File reference to the new archive tar.gz.
     */
    private File extractArchive(File tempDir, File exportFile)
        throws IOException, ImportExtractionException {
        log.info("Extracting archive to: " + tempDir.getAbsolutePath());
        byte[] buf = new byte[1024];
        ZipInputStream zipinputstream = new ZipInputStream(new FileInputStream(exportFile));
        ZipEntry zipentry = zipinputstream.getNextEntry();

        if (zipentry == null) {
            throw new ImportExtractionException(i18n.tr("The archive {0} is not " +
                "a properly compressed file or is empty", exportFile.getName()));
        }

        while (zipentry != null) {
            //for each entry to be extracted
            String entryName = zipentry.getName();
            if (log.isDebugEnabled()) {
                log.debug("entryname " + entryName);
            }
            FileOutputStream fileoutputstream;
            File newFile = new File(entryName);
            String directory = newFile.getParent();
            if (directory != null) {
                new File(tempDir, directory).mkdirs();
            }

            fileoutputstream = new FileOutputStream(new File(tempDir, entryName));

            int n;
            while ((n = zipinputstream.read(buf, 0, 1024)) > -1) {
                fileoutputstream.write(buf, 0, n);
            }

            fileoutputstream.close();
            zipinputstream.closeEntry();
            zipentry = zipinputstream.getNextEntry();

        }

        zipinputstream.close();

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
}
