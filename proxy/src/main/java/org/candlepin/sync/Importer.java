/**
 * Copyright (c) 2009 Red Hat, Inc.
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
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.security.cert.CertificateException;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
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
import org.candlepin.exceptions.ConflictException;
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
import com.wideplay.warp.persist.Transactional;

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
     * @throws IOException thrown if there's a problem reading the file
     * @throws ImporterException thrown if the metadata is invalid.
     */
    public void validateMetadata(String type, Owner owner, File meta, boolean force)
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
            if (!force && lastrun.getExported().compareTo(m.getCreated()) >= 0) {
                throw new ConflictException(i18n.tr("Import is older than existing data"));
            }
            else {
                lastrun.setExported(new Date());
                expMetaCurator.merge(lastrun);
            }
        }
    }

    public void loadExport(Owner owner, File exportFile, boolean force)
        throws ImporterException {
        File tmpDir = null;
        InputStream exportStream = null;
        try {
            tmpDir = new SyncUtils(config).makeTempDir("import");

            extractArchive(tmpDir, exportFile);

            exportStream = new FileInputStream(new File(tmpDir, "consumer_export.zip"));
            boolean verifiedSignature = pki.verifySHA256WithRSAHashWithUpstreamCACert(
                exportStream,
                loadSignature(new File(tmpDir, "signature")));
        /*
            if (!verifiedSignature) {
                throw new ImporterException(i18n.tr("Failed import file hash check."));
            }*/

            File exportDir
                = extractArchive(tmpDir, new File(tmpDir, "consumer_export.zip"));

            Map<String, File> importFiles = new HashMap<String, File>();
            for (File file : exportDir.listFiles()) {
                importFiles.put(file.getName(), file);
            }

            importObjects(owner, importFiles, force);
        }
        catch (CertificateException e) {
            log.error("Exception caught importing archive", e);
            throw new ImportExtractionException("unable to extract export archive", e);
        }
        catch (PersistenceException pe) {
            log.error("Failed to import archive", pe);
            Throwable cause = pe.getCause();
            if (cause != null && cause instanceof ConstraintViolationException) {
                throw new SyncDataFormatException(
                    i18n.tr("This distributor has already been imported by another owner"));
            }
            else {
                throw new ImportExtractionException(i18n.tr("Failed to import archive"), pe);
            }
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
                    log.error("Failed to delete extracted export");
                    log.error(e);
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

    @Transactional(rollbackOn = {IOException.class,
            ImporterException.class, RuntimeException.class})
    public void importObjects(Owner owner, Map<String, File> importFiles, boolean force)
        throws IOException, ImporterException {

        File metadata = importFiles.get(ImportFile.META.fileName());

        // system level elements
        /*
         * Checking a system wide last import date breaks multi-tenant deployments whenever
         * one org imports a manifest slightly older than another org who has already
         * imported. Disabled for now. See bz #769644.
         */
//        validateMetadata(ExporterMetadata.TYPE_SYSTEM, null, metadata, force);


        importRules(importFiles.get(ImportFile.RULES.fileName()).listFiles(), metadata);


        importConsumerTypes(importFiles.get(ImportFile.CONSUMER_TYPE.fileName()).listFiles());

        // per user elements
        validateMetadata(ExporterMetadata.TYPE_PER_USER, owner, metadata, force);
        importConsumer(owner, importFiles.get(ImportFile.CONSUMER.fileName()));

        // If the consumer has no entitlements, this products directory will end up empty.
        // This also implies there will be no entitlements to import.
        if (importFiles.get(ImportFile.PRODUCTS.fileName()) != null) {
            Set<Product> importedProducts = importProducts(importFiles.get(ImportFile.PRODUCTS.fileName()).listFiles());
            importEntitlements(owner, importedProducts,
                importFiles.get(ImportFile.ENTITLEMENTS.fileName()).listFiles());
        }
        else {
            log.warn("No products found to import, skipping product and entitlement import.");
        }

        poolManager.refreshPools(owner);
    }

    public void importRules(File[] rulesFiles, File metadata) throws IOException {

        // only import rules if versions are ok
        Meta m = mapper.readValue(metadata, Meta.class);

        if (VersionUtil.getRulesVersionCompatibility(m.getVersion())) {
            // Only importing a single rules file now.
            Reader reader = null;
            try {
                reader = new FileReader(rulesFiles[0]);
                rulesImporter.importObject(reader);
            }
            finally {
                if (reader != null) {
                    reader.close();
                }
            }
        }
        else {
            log.warn(
                i18n.tr("Incompatible rules: import version {0} older than our version {1}.",
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

    public void importConsumer(Owner owner, File consumerFile) throws IOException,
        SyncDataFormatException {
        ConsumerImporter importer = new ConsumerImporter(ownerCurator, i18n);
        Reader reader = null;
        try {
            reader = new FileReader(consumerFile);
            ConsumerDto consumer = importer.createObject(mapper, reader);
            importer.store(owner, consumer);
        }
        finally {
            if (reader != null) {
                reader.close();
            }
        }
    }

    public Set<Product> importProducts(File[] products) throws IOException {
        ProductImporter importer = new ProductImporter(productCurator, contentCurator, poolManager);
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
        importer.store(productsToImport);
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
    private File extractArchive(File tempDir, File exportFile) throws IOException {
        log.info("Extracting archive to: " + tempDir.getAbsolutePath());
        byte[] buf = new byte[1024];
        ZipInputStream zipinputstream = null;
        ZipEntry zipentry;
        zipinputstream = new ZipInputStream(new FileInputStream(exportFile));

        zipentry = zipinputstream.getNextEntry();
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
