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

import org.candlepin.common.config.Configuration;
import org.candlepin.common.util.VersionUtil;
import org.candlepin.config.ConfigProperties;
import org.candlepin.guice.PrincipalProvider;
import org.candlepin.model.Cdn;
import org.candlepin.model.CdnCurator;
import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerType;
import org.candlepin.model.ConsumerTypeCurator;
import org.candlepin.model.DistributorVersion;
import org.candlepin.model.DistributorVersionCurator;
import org.candlepin.model.Entitlement;
import org.candlepin.model.EntitlementCertificate;
import org.candlepin.model.EntitlementCurator;
import org.candlepin.model.IdentityCertificate;
import org.candlepin.model.Pool;
import org.candlepin.model.Product;
import org.candlepin.model.ProductCertificate;
import org.candlepin.pki.PKIUtility;
import org.candlepin.policy.js.export.ExportRules;
import org.candlepin.service.EntitlementCertServiceAdapter;
import org.candlepin.service.ProductServiceAdapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Exporter
 */
public class Exporter {
    private static Logger log = LoggerFactory.getLogger(Exporter.class);

    private ObjectMapper mapper;

    private MetaExporter meta;
    private ConsumerExporter consumerExporter;
    private ProductExporter productExporter;
    private ProductCertExporter productCertExporter;
    private ConsumerTypeExporter consumerType;
    private RulesExporter rules;
    private EntitlementCertExporter entCert;
    private EntitlementExporter entExporter;
    private DistributorVersionCurator distVerCurator;
    private DistributorVersionExporter distVerExporter;
    private CdnCurator cdnCurator;
    private CdnExporter cdnExporter;

    private ConsumerTypeCurator consumerTypeCurator;
    private EntitlementCertServiceAdapter entCertAdapter;
    private ProductServiceAdapter productAdapter;
    private EntitlementCurator entitlementCurator;
    private PKIUtility pki;
    private Configuration config;
    private ExportRules exportRules;
    private PrincipalProvider principalProvider;

    private static final String LEGACY_RULES_FILE = "/rules/default-rules.js";

    @Inject
    public Exporter(ConsumerTypeCurator consumerTypeCurator, MetaExporter meta,
        ConsumerExporter consumerExporter, ConsumerTypeExporter consumerType,
        RulesExporter rules, EntitlementCertExporter entCert,
        EntitlementCertServiceAdapter entCertAdapter, ProductExporter productExporter,
        ProductServiceAdapter productAdapter, ProductCertExporter productCertExporter,
        EntitlementCurator entitlementCurator, EntitlementExporter entExporter,
        PKIUtility pki, Configuration config, ExportRules exportRules,
        PrincipalProvider principalProvider, DistributorVersionCurator distVerCurator,
        DistributorVersionExporter distVerExporter,
        CdnCurator cdnCurator,
        CdnExporter cdnExporter) {

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
        this.principalProvider = principalProvider;
        this.distVerCurator = distVerCurator;
        this.distVerExporter = distVerExporter;
        this.cdnCurator = cdnCurator;
        this.cdnExporter = cdnExporter;

        mapper = SyncUtils.getObjectMapper(this.config);
    }

    public File getFullExport(Consumer consumer) throws ExportCreationException {
        return getFullExport(consumer, null, null, null);
    }

    public File getFullExport(Consumer consumer, String cdnKey, String webAppPrefix,
        String apiUrl)
        throws ExportCreationException {
        // TODO: need to delete tmpDir (which contains the archive,
        // which we need to return...)
        try {
            File tmpDir = new SyncUtils(config).makeTempDir("export");
            File baseDir = new File(tmpDir.getAbsolutePath(), "export");
            baseDir.mkdir();

            exportMeta(baseDir, cdnKey);
            exportConsumer(baseDir, consumer, webAppPrefix, apiUrl);
            exportIdentityCertificate(baseDir, consumer);
            exportEntitlements(baseDir, consumer);
            exportEntitlementsCerts(baseDir, consumer, null, true);
            exportProducts(baseDir, consumer);
            exportConsumerTypes(baseDir);
            exportRules(baseDir);
            exportDistributorVersions(baseDir);
            exportContentDeliveryNetworks(baseDir);
            return makeArchive(consumer, tmpDir, baseDir);
        }
        catch (IOException e) {
            log.error("Error generating entitlement export", e);
            throw new ExportCreationException("Unable to create export archive", e);
        }
    }

    public File getEntitlementExport(Consumer consumer, Set<Long> serials) throws ExportCreationException {
        // TODO: need to delete tmpDir (which contains the archive,
        // which we need to return...)
        try {
            File tmpDir = new SyncUtils(config).makeTempDir("export");
            File baseDir = new File(tmpDir.getAbsolutePath(), "export");
            baseDir.mkdir();

            exportMeta(baseDir, null);
            exportEntitlementsCerts(baseDir, consumer, serials, false);
            return makeArchive(consumer, tmpDir, baseDir);
        }
        catch (IOException e) {
            log.error("Error generating entitlement export", e);
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
        FileInputStream in = null;
        try {
            in = new FileInputStream(file);

            byte [] buf = new byte[1024];
            int len;
            while ((len = in.read(buf)) > 0) {
                out.write(buf, 0, len);
            }
            out.closeEntry();
        }
        finally {
            if (in != null) {
                in.close();
            }
        }
    }

    private void addSignatureToArchive(ZipOutputStream out, byte[] signature)
        throws IOException, FileNotFoundException {

        log.debug("Adding signature to archive.");
        out.putNextEntry(new ZipEntry("signature"));
        out.write(signature, 0, signature.length);
        out.closeEntry();
    }

    private void exportMeta(File baseDir, String cdnKey)
        throws IOException {
        File file = new File(baseDir.getCanonicalPath(), "meta.json");
        FileWriter writer = null;
        try {
            writer = new FileWriter(file);
            Meta m = new Meta(getVersion(), new Date(),
                principalProvider.get().getPrincipalName(),
                null, cdnKey);
            meta.export(mapper, writer, m);
        }
        finally {
            if (writer != null) {
                writer.close();
            }
        }
    }

    private String getPrefixWebUrl(String override) {
        String prefixWebUrl = config.getString(ConfigProperties.PREFIX_WEBURL);
        if (!StringUtils.isBlank(override)) {
            return override;
        }
        if (StringUtils.isBlank(prefixWebUrl)) {
            return null;
        }
        return prefixWebUrl;
    }

    private String getPrefixApiUrl(String override) {
        String prefixApiUrl = config.getString(ConfigProperties.PREFIX_APIURL);
        if (!StringUtils.isBlank(override)) {
            return override;
        }
        if (StringUtils.isBlank(prefixApiUrl)) {
            return null;
        }
        return prefixApiUrl;
    }

    private String getVersion() {
        Map<String, String> map = VersionUtil.getVersionMap();
        return map.get("version") + "-" + map.get("release");
    }

    private void exportConsumer(File baseDir, Consumer consumer, String webAppPrefix,
        String apiUrl)
        throws IOException {
        File file = new File(baseDir.getCanonicalPath(), "consumer.json");
        FileWriter writer = null;
        try {
            writer = new FileWriter(file);
            this.consumerExporter.export(mapper, writer, consumer,
                getPrefixWebUrl(webAppPrefix), getPrefixApiUrl(apiUrl));
        }
        finally {
            if (writer != null) {
                writer.close();
            }
        }
    }

    private void exportEntitlementsCerts(File baseDir, Consumer consumer,
        Set<Long> serials, boolean manifest)
        throws IOException {

        File entCertDir = new File(baseDir.getCanonicalPath(), "entitlement_certificates");
        entCertDir.mkdir();

        for (EntitlementCertificate cert : entCertAdapter.listForConsumer(consumer)) {
            if (manifest && !this.exportRules.canExport(cert.getEntitlement())) {
                if (log.isDebugEnabled()) {
                    log.debug("Skipping export of entitlement cert with product: {}",
                        cert.getEntitlement().getPool().getProductId());
                }
                continue;
            }

            if ((serials == null) || (serials.contains(cert.getSerial().getId()))) {
                log.debug("Exporting entitlement certificate: " + cert.getSerial());
                File file = new File(entCertDir.getCanonicalPath(),
                    cert.getSerial().getId() + ".pem");
                FileWriter writer = null;
                try {
                    writer = new FileWriter(file);
                    entCert.export(writer, cert);
                }
                finally {
                    if (writer != null) {
                        writer.close();
                    }
                }
            }
        }
    }

    private void exportIdentityCertificate(File baseDir, Consumer consumer)
        throws IOException {

        File idcertdir = new File(baseDir.getCanonicalPath(), "upstream_consumer");
        idcertdir.mkdir();

        IdentityCertificate cert = consumer.getIdCert();
        File file = new File(idcertdir.getCanonicalPath(),
            cert.getSerial().getId() + ".json");

        // paradigm dictates this should go in an exporter.export method
        FileWriter writer = null;

        try {
            writer = new FileWriter(file);
            mapper.writeValue(writer, cert);
        }
        finally {
            if (writer != null) {
                writer.close();
            }
        }
    }

    private void exportEntitlements(File baseDir, Consumer consumer)
        throws IOException, ExportCreationException {
        File entCertDir = new File(baseDir.getCanonicalPath(), "entitlements");
        entCertDir.mkdir();

        for (Entitlement ent : entitlementCurator.listByConsumer(consumer)) {
            if (ent.isDirty()) {
                log.error("Entitlement " + ent.getId() + " is marked as dirty.");
                throw new ExportCreationException("Attempted to export dirty entitlements");
            }

            if (!this.exportRules.canExport(ent)) {
                if (log.isDebugEnabled()) {
                    log.debug("Skipping export of entitlement with product: {}",
                        ent.getPool().getProductId());
                }

                continue;
            }

            if (log.isDebugEnabled()) {
                log.debug("Exporting entitlement for product" + ent.getPool().getProductId());
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
            Pool pool = entitlement.getPool();

            for (Product providedProduct : pool.getProvidedProducts()) {
                products.put(providedProduct.getId(), providedProduct);
            }

            // Don't forget the 'main' product!
            Product product = pool.getProduct();
            products.put(product.getId(), product);

            // Also need to check for sub products
            Product derivedProduct = pool.getDerivedProduct();
            if (derivedProduct != null) {
                products.put(derivedProduct.getId(), derivedProduct);
            }

            for (Product derivedProvidedProduct : pool.getDerivedProvidedProducts()) {
                products.put(derivedProvidedProduct.getId(), derivedProvidedProduct);
            }
        }

        for (Product product : products.values()) {
            // Clear the owner and UUID so they can be re-generated/assigned on import
            // product.setUuid(null);
            // product.setOwner(null);

            String path = productDir.getCanonicalPath();
            String productId = product.getId();
            File file = new File(path, productId + ".json");
            FileWriter writer = null;
            try {
                writer = new FileWriter(file);
                productExporter.export(mapper, writer, product);
            }
            finally {
                if (writer != null) {
                    writer.close();
                }
            }

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
            FileWriter writer = null;
            try {
                writer = new FileWriter(file);
                consumerType.export(mapper, writer, type);
            }
            finally {
                if (writer != null) {
                    writer.close();
                }
            }
        }
    }

    private void exportRules(File baseDir) throws IOException {
        // Because old candlepin servers assume to import a file in rules dir, we had to
        // move to a new directory for versioned rules file:
        File newRulesDir = new File(baseDir.getCanonicalPath(), "rules2");
        newRulesDir.mkdir();
        File newRulesFile = new File(newRulesDir.getCanonicalPath(), "rules.js");
        FileWriter writer = null;
        try {
            writer = new FileWriter(newRulesFile);
            rules.export(writer);
        }
        finally {
            if (writer != null) {
                writer.close();
            }
        }

        exportLegacyRules(baseDir);
    }

    /*
     * We still need to export a copy of the deprecated default-rules.js so new manifests
     * can still be imported by old candlepin servers.
     */
    private void exportLegacyRules(File baseDir) throws IOException {
        File oldRulesDir = new File(baseDir.getCanonicalPath(), "rules");
        oldRulesDir.mkdir();
        File oldRulesFile = new File(oldRulesDir.getCanonicalPath(), "default-rules.js");

        // TODO: does this need a "exporter" object as well?
        FileUtils.copyFile(new File(
            this.getClass().getResource(LEGACY_RULES_FILE).getPath()),
            oldRulesFile);
    }

    private void exportDistributorVersions(File baseDir) throws IOException {
        List<DistributorVersion> versions = distVerCurator.findAll();
        if (versions == null || versions.isEmpty()) { return; }

        File distVerDir = new File(baseDir.getCanonicalPath(), "distributor_version");
        distVerDir.mkdir();

        FileWriter writer = null;
        for (DistributorVersion dv : versions) {
            if (log.isDebugEnabled()) {
                log.debug("Exporting Distributor Version" + dv.getName());
            }
            try {
                File file = new File(distVerDir.getCanonicalPath(), dv.getName() + ".json");
                writer = new FileWriter(file);
                distVerExporter.export(mapper, writer, dv);
            }
            finally {
                if (writer != null) {
                    writer.close();
                }
            }
        }
    }

    private void exportContentDeliveryNetworks(File baseDir) throws IOException {
        List<Cdn> cdns = cdnCurator.list();
        if (cdns == null || cdns.isEmpty()) { return; }

        File cdnDir = new File(baseDir.getCanonicalPath(), "content_delivery_network");
        cdnDir.mkdir();

        FileWriter writer = null;
        for (Cdn cdn : cdns) {
            if (log.isDebugEnabled()) {
                log.debug("Exporting Content Delivery Network" + cdn.getName());
            }
            try {
                File file = new File(cdnDir.getCanonicalPath(), cdn.getLabel() + ".json");
                writer = new FileWriter(file);
                cdnExporter.export(mapper, writer, cdn);
            }
            finally {
                if (writer != null) {
                    writer.close();
                }
            }
        }
    }
}
