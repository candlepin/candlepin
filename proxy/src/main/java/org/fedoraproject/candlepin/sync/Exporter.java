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
package org.fedoraproject.candlepin.sync;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.codehaus.jackson.map.ObjectMapper;
import org.fedoraproject.candlepin.config.Config;
import org.fedoraproject.candlepin.model.Consumer;
import org.fedoraproject.candlepin.model.ConsumerType;
import org.fedoraproject.candlepin.model.ConsumerTypeCurator;
import org.fedoraproject.candlepin.model.Entitlement;
import org.fedoraproject.candlepin.model.EntitlementCertificate;
import org.fedoraproject.candlepin.model.EntitlementCurator;
import org.fedoraproject.candlepin.model.Product;
import org.fedoraproject.candlepin.model.ProductCertificate;
import org.fedoraproject.candlepin.model.ProvidedProduct;
import org.fedoraproject.candlepin.pki.PKIUtility;
import org.fedoraproject.candlepin.policy.js.export.JsExportRules;
import org.fedoraproject.candlepin.service.EntitlementCertServiceAdapter;
import org.fedoraproject.candlepin.service.ProductServiceAdapter;

import com.google.inject.Inject;

/**
 * Exporter
 */
public class Exporter {
    private static Logger log = Logger.getLogger(Exporter.class);

    private ObjectMapper mapper;

    private MetaExporter meta;
    private ConsumerExporter consumerExporter;
    private ProductExporter productExporter;
    private ProductCertExporter productCertExporter;
    private ConsumerTypeExporter consumerType;
    private RulesExporter rules;
    private EntitlementCertExporter entCert;
    private EntitlementExporter entExporter;

    private ConsumerTypeCurator consumerTypeCurator;
    private EntitlementCertServiceAdapter entCertAdapter;
    private ProductServiceAdapter productAdapter;
    private EntitlementCurator entitlementCurator;
    private PKIUtility pki;
    private Config config;
    private JsExportRules exportRules;

    @Inject
    public Exporter(ConsumerTypeCurator consumerTypeCurator, MetaExporter meta,
        ConsumerExporter consumerExporter, ConsumerTypeExporter consumerType,
        RulesExporter rules, EntitlementCertExporter entCert,
        EntitlementCertServiceAdapter entCertAdapter, ProductExporter productExporter,
        ProductServiceAdapter productAdapter, ProductCertExporter productCertExporter,
        EntitlementCurator entitlementCurator, EntitlementExporter entExporter,
        PKIUtility pki, Config config, JsExportRules exportRules) {

        this.consumerTypeCurator = consumerTypeCurator;

        this.meta = meta;
        this.consumerExporter = consumerExporter;
        this.consumerType = consumerType;
        this.rules = rules;
        this.entCert = entCert;
        this.entCertAdapter = entCertAdapter;
        this.productExporter = productExporter;
        this.productAdapter = productAdapter;
        this.productCertExporter = productCertExporter;
        this.entitlementCurator = entitlementCurator;
        this.entExporter = entExporter;
        this.pki = pki;
        this.config = config;
        this.exportRules = exportRules;

        mapper = SyncUtils.getObjectMapper(this.config);
    }

    public File getFullExport(Consumer consumer) throws ExportCreationException {
        // TODO: need to delete tmpDir (which contains the archive,
        // which we need to return...)
        try {
            File tmpDir = new SyncUtils(config).makeTempDir("export");
            File baseDir = new File(tmpDir.getAbsolutePath(), "export");
            baseDir.mkdir();

            exportMeta(baseDir);
            exportConsumer(baseDir, consumer);
            exportEntitlements(baseDir, consumer);
            exportEntitlementsCerts(baseDir, consumer, null);
            exportProducts(baseDir, consumer);
            exportConsumerTypes(baseDir);
            exportRules(baseDir);
            return makeArchive(consumer, tmpDir, baseDir);
        }
        catch (IOException e) {
            throw new ExportCreationException("Unable to create export archive", e);
        }
    }

    public File getEntitlementExport(Consumer consumer,
                        Set<Long> serials) throws ExportCreationException {
        // TODO: need to delete tmpDir (which contains the archive,
        // which we need to return...)
        try {
            File tmpDir = new SyncUtils(config).makeTempDir("export");
            File baseDir = new File(tmpDir.getAbsolutePath(), "export");
            baseDir.mkdir();

            exportMeta(baseDir);
            exportEntitlementsCerts(baseDir, consumer, serials);
            return makeArchive(consumer, tmpDir, baseDir);
        }
        catch (IOException e) {
            throw new ExportCreationException("Unable to create export archive", e);
        }
    }

    /**
     * Create a tar.gz archive of the exported directory.
     *
     * @param exportDir Directory where Candlepin data was exported.
     * @return File reference to the new archive zip.
     */
    private File makeArchive(Consumer consumer, File tempDir, File exportDir)
        throws IOException {
        String exportFileName = exportDir.getName() + ".zip";
        log.info("Creating archive of " + exportDir.getAbsolutePath() + " in: " +
            exportFileName);

        File archive = createZipArchiveWithDir(
            tempDir, exportDir, "consumer_export.zip",
            "Candlepin export for " + consumer.getUuid());

        InputStream archiveInputStream = null;
        try {
            archiveInputStream = new FileInputStream(archive);
            File signedArchive = createSignedZipArchive(
                tempDir, archive, exportFileName,
                pki.getSHA256WithRSAHash(archiveInputStream),
                "signed Candlepin export for " + consumer.getUuid());

            log.debug("Returning file: " + archive.getAbsolutePath());
            return signedArchive;
        }
        finally {
            if (archiveInputStream != null) {
                try {
                    archiveInputStream.close();
                }
                catch (Exception e) {
                    // nothing to do
                }
            }
        }
    }

    private File createZipArchiveWithDir(File tempDir, File exportDir,
        String exportFileName, String comment)
        throws FileNotFoundException, IOException {

        File archive = new File(tempDir, exportFileName);
        ZipOutputStream out = null;
        try {
            out = new ZipOutputStream(new FileOutputStream(archive));
            out.setComment(comment);
            addFilesToArchive(out, exportDir.getParent().length() + 1, exportDir);
        }
        finally {
            if (out != null) {
                out.close();
            }
        }
        return archive;
    }

    private File createSignedZipArchive(File tempDir, File toAdd,
        String exportFileName, byte[] signature, String comment)
        throws FileNotFoundException, IOException {

        File archive = new File(tempDir, exportFileName);
        ZipOutputStream out = null;
        try {
            out = new ZipOutputStream(new FileOutputStream(archive));
            out.setComment(comment);
            addFileToArchive(out, toAdd.getParent().length() + 1, toAdd);
            addSignatureToArchive(out, signature);
        }
        finally {
            if (out != null) {
                out.close();
            }
        }
        return archive;
    }



    /**
     * @param out
     * @param exportDir
     * @throws IOException
     */
    private void addFilesToArchive(ZipOutputStream out, int charsToDropFromName,
        File directory) throws IOException {
        for (File file : directory.listFiles()) {
            if (file.isDirectory()) {
                addFilesToArchive(out, charsToDropFromName, file);
            }
            else {
                addFileToArchive(out, charsToDropFromName, file);
            }
        }
    }

    private void addFileToArchive(ZipOutputStream out, int charsToDropFromName,
        File file) throws IOException, FileNotFoundException {
        log.debug("Adding file to archive: " +
            file.getAbsolutePath().substring(charsToDropFromName));
        out.putNextEntry(new ZipEntry(
            file.getAbsolutePath().substring(charsToDropFromName)));
        FileInputStream in = new FileInputStream(file);

        byte [] buf = new byte[1024];
        int len;
        while ((len = in.read(buf)) > 0) {
            out.write(buf, 0, len);
        }
        out.closeEntry();
        in.close();
    }

    private void addSignatureToArchive(ZipOutputStream out, byte[] signature)
        throws IOException, FileNotFoundException {

        log.debug("Adding signature to archive.");
        out.putNextEntry(new ZipEntry("signature"));
        out.write(signature, 0, signature.length);
        out.closeEntry();
    }

    private void exportMeta(File baseDir) throws IOException {
        File file = new File(baseDir.getCanonicalPath(), "meta.json");
        FileWriter writer = new FileWriter(file);
        Meta m = new Meta();
        m.setCreated(new Date());
        meta.export(mapper, writer, m);
        writer.close();
    }

    private void exportConsumer(File baseDir, Consumer consumer) throws IOException {
        File file = new File(baseDir.getCanonicalPath(), "consumer.json");
        FileWriter writer = new FileWriter(file);
        this.consumerExporter.export(mapper, writer, consumer);
        writer.close();
    }

    private void exportEntitlementsCerts(File baseDir, Consumer consumer, Set<Long> serials)
        throws IOException {

        File entCertDir = new File(baseDir.getCanonicalPath(), "entitlement_certificates");
        entCertDir.mkdir();

        for (EntitlementCertificate cert : entCertAdapter.listForConsumer(consumer)) {
            if (!this.exportRules.canExport(cert.getEntitlement())) {
                if (log.isDebugEnabled()) {
                    log.debug("Skipping export of entitlement cert with product:  " +
                            cert.getEntitlement().getProductId());
                }

                continue;
            }

            if ((serials == null) || (serials.contains(cert.getSerial()))) {
                log.debug("Exporting entitlement certificate: " + cert.getSerial());
                File file = new File(entCertDir.getCanonicalPath(),
                    cert.getSerial().getId() + ".pem");
                FileWriter writer = new FileWriter(file);
                entCert.export(writer, cert);
                writer.close();
            }
        }
    }

    private void exportEntitlements(File baseDir, Consumer consumer) throws IOException {
        File entCertDir = new File(baseDir.getCanonicalPath(), "entitlements");
        entCertDir.mkdir();

        for (Entitlement ent : entitlementCurator.listByConsumer(consumer)) {
            if (!this.exportRules.canExport(ent)) {
                if (log.isDebugEnabled()) {
                    log.debug("Skipping export of entitlement with product:  " +
                            ent.getProductId());
                }

                continue;
            }

            if (log.isDebugEnabled()) {
                log.debug("Exporting entitlement for product" + ent.getProductId());
            }
            FileWriter writer = null;
            try {
                File file = new File(entCertDir.getCanonicalPath(), ent.getId() + ".json");
                writer = new FileWriter(file);
                entExporter.export(mapper, writer, ent);
            }
            finally {
                if (writer != null) {
                    writer.close();
                }
            }
        }
    }

    private void exportProducts(File baseDir, Consumer consumer) throws IOException {
        File productDir = new File(baseDir.getCanonicalPath(), "products");
        productDir.mkdir();

        Map<String, Product> products = new HashMap<String, Product>();
        for (Entitlement entitlement : consumer.getEntitlements()) {

            for (ProvidedProduct providedProduct : entitlement.getPool().
                getProvidedProducts()) {
                // Don't want to call the adapter if not needed, it can be expensive.
                if (!products.containsKey(providedProduct.getProductId())) {
                    products.put(providedProduct.getProductId(),
                        productAdapter.getProductById(providedProduct.getProductId()));
                }
            }

            // Don't forget the 'main' product!
            String productId = entitlement.getPool().getProductId();
            if (!products.containsKey(productId)) {
                products.put(productId, productAdapter.getProductById(productId));
            }
        }

        for (Product product : products.values()) {
            String path = productDir.getCanonicalPath();
            String productId = product.getId();
            File file = new File(path, productId + ".json");
            FileWriter writer = new FileWriter(file);
            productExporter.export(mapper, writer, product);
            writer.close();

            // Real products have a numeric id.
            if (StringUtils.isNumeric(product.getId())) {
                ProductCertificate cert = productAdapter.getProductCertificate(product);
                // XXX: not all product adapters implement getProductCertificate,
                // so just skip over this if we get null back
                // XXX: need to decide if the cert should always be in the export, or never.
                if (cert != null) {
                    file = new File(productDir.getCanonicalPath(),
                        product.getId() + ".pem");
                    writer = new FileWriter(file);
                    productCertExporter.export(writer, cert);
                    writer.close();
                }
            }
        }
    }

    private void exportConsumerTypes(File baseDir) throws IOException {
        File typeDir = new File(baseDir.getCanonicalPath(), "consumer_types");
        typeDir.mkdir();

        for (ConsumerType type : consumerTypeCurator.listAll()) {
            File file = new File(typeDir.getCanonicalPath(), type.getLabel() + ".json");
            FileWriter writer = new FileWriter(file);
            consumerType.export(mapper, writer, type);
            writer.close();
        }
    }

    private void exportRules(File baseDir) throws IOException {
        File file = new File(baseDir.getCanonicalPath(), "rules");
        file.mkdir();

        file = new File(file.getCanonicalPath(), "rules.js");
        FileWriter writer = new FileWriter(file);
        rules.export(writer);
        writer.close();
    }
}
