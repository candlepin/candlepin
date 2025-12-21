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

import org.candlepin.config.ConfigProperties;
import org.candlepin.config.Configuration;
import org.candlepin.dto.ModelTranslator;
import org.candlepin.dto.manifest.v1.CertificateDTO;
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
import org.candlepin.model.SCACertificate;
import org.candlepin.pki.certs.ConcurrentContentPayloadCreationException;
import org.candlepin.pki.certs.SCACertificateGenerator;
import org.candlepin.pki.impl.Signer;
import org.candlepin.policy.js.export.ExportRules;
import org.candlepin.service.EntitlementCertServiceAdapter;
import org.candlepin.version.VersionUtil;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import tools.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import javax.inject.Inject;
import javax.inject.Named;


/**
 * Exporter
 */
public class Exporter {
    private static final Logger log = LoggerFactory.getLogger(Exporter.class);

    private final ObjectMapper mapper;
    private final MetaExporter meta;
    private final ConsumerExporter consumerExporter;
    private final ProductExporter productExporter;
    private final ConsumerTypeExporter consumerType;
    private final RulesExporter rules;
    private final EntitlementExporter entExporter;
    private final DistributorVersionCurator distVerCurator;
    private final DistributorVersionExporter distVerExporter;
    private final CdnCurator cdnCurator;
    private final CdnExporter cdnExporter;
    private final ConsumerTypeCurator consumerTypeCurator;
    private final EntitlementCertServiceAdapter entCertAdapter;
    private final EntitlementCurator entitlementCurator;
    private final Signer signer;
    private final Configuration config;
    private final ExportRules exportRules;
    private final PrincipalProvider principalProvider;
    private final ModelTranslator translator;
    private final SCACertificateGenerator scaCertificateGenerator;
    private final SyncUtils syncUtils;

    @Inject
    public Exporter(ConsumerTypeCurator consumerTypeCurator, MetaExporter meta,
        ConsumerExporter consumerExporter, ConsumerTypeExporter consumerType,
        RulesExporter rules,
        EntitlementCertServiceAdapter entCertAdapter, ProductExporter productExporter,
        EntitlementCurator entitlementCurator, EntitlementExporter entExporter,
        Signer signer, Configuration config, ExportRules exportRules,
        PrincipalProvider principalProvider, DistributorVersionCurator distVerCurator,
        DistributorVersionExporter distVerExporter,
        CdnCurator cdnCurator,
        CdnExporter cdnExporter,
        SyncUtils syncUtils,
        @Named("ExportObjectMapper") ObjectMapper mapper,
        ModelTranslator translator,
        SCACertificateGenerator scaCertificateGenerator) {

        this.consumerTypeCurator = Objects.requireNonNull(consumerTypeCurator);
        this.meta = Objects.requireNonNull(meta);
        this.consumerExporter = Objects.requireNonNull(consumerExporter);
        this.consumerType = Objects.requireNonNull(consumerType);
        this.rules = Objects.requireNonNull(rules);
        this.entCertAdapter = Objects.requireNonNull(entCertAdapter);
        this.productExporter = Objects.requireNonNull(productExporter);
        this.entitlementCurator = Objects.requireNonNull(entitlementCurator);
        this.entExporter = Objects.requireNonNull(entExporter);
        this.signer = Objects.requireNonNull(signer);
        this.config = Objects.requireNonNull(config);
        this.exportRules = Objects.requireNonNull(exportRules);
        this.principalProvider = Objects.requireNonNull(principalProvider);
        this.distVerCurator = Objects.requireNonNull(distVerCurator);
        this.distVerExporter = Objects.requireNonNull(distVerExporter);
        this.cdnCurator = Objects.requireNonNull(cdnCurator);
        this.cdnExporter = Objects.requireNonNull(cdnExporter);
        this.syncUtils = Objects.requireNonNull(syncUtils);
        this.mapper = Objects.requireNonNull(mapper);
        this.translator = Objects.requireNonNull(translator);
        this.scaCertificateGenerator = Objects.requireNonNull(scaCertificateGenerator);
    }

    /**
     * Creates a manifest archive for the target {@link Consumer}.
     *
     * @param consumer the target consumer to export.
     * @param cdnLabel the CDN label to store in the meta file.
     * @param webUrl the URL pointing to the manifest's originating web application.
     * @param apiUrl the API URL pointing to the manifest's originating candlepin API.
     * @return a newly created manifest file for the target consumer.
     * @throws ExportCreationException when an error occurs while creating the manifest file.
     */
    public File getFullExport(Consumer consumer, String cdnLabel, String webUrl,
        String apiUrl) throws ExportCreationException {
        try {
            File tmpDir = syncUtils.makeTempDir("export");
            File baseDir = new File(tmpDir.getAbsolutePath(), "export");
            baseDir.mkdir();

            exportMeta(baseDir, cdnLabel);
            exportConsumer(baseDir, consumer, webUrl, apiUrl);
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

    public File getEntitlementExport(Consumer consumer, Set<Long> serials)
        throws ExportCreationException, ConcurrentContentPayloadCreationException {
        // TODO: need to delete tmpDir (which contains the archive,
        // which we need to return...)
        try {
            File tmpDir = syncUtils.makeTempDir("export");
            File baseDir = new File(tmpDir.getAbsolutePath(), "export");
            baseDir.mkdir();

            exportMeta(baseDir, null);
            exportEntitlementsCerts(baseDir, consumer, serials, false);
            exportContentAccessCerts(baseDir, consumer, serials);
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
    private File makeArchive(Consumer consumer, File tempDir, File exportDir) throws IOException {
        String exportFileName = String.format("%s-%s.zip", consumer.getUuid(), exportDir.getName());
        log.info("Creating archive of {} in: {}", exportDir.getAbsolutePath(), exportFileName);

        File archive = createZipArchiveWithDir(tempDir, exportDir, "consumer_export.zip",
            "Candlepin export for " + consumer.getUuid());

        try (InputStream archiveInputStream = new FileInputStream(archive)) {
            File signedArchive = createSignedZipArchive(
                tempDir, archive, exportFileName,
                this.signer.sign(archiveInputStream),
                "signed Candlepin export for " + consumer.getUuid());

            log.debug("Returning file: {}", archive.getAbsolutePath());
            return signedArchive;
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

            byte[] buf = new byte[1024];
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
                principalProvider.get().getName(),
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

    private void exportConsumer(File baseDir, Consumer consumer, String webAppPrefix, String apiUrl)
        throws IOException {

        File file = new File(baseDir.getCanonicalPath(), "consumer.json");
        try (FileWriter writer = new FileWriter(file)) {
            this.consumerExporter.export(mapper, writer, consumer,
                getPrefixWebUrl(webAppPrefix), getPrefixApiUrl(apiUrl));
        }
    }

    private void exportEntitlementsCerts(File baseDir, Consumer consumer,
        Set<Long> serials, boolean manifest)
        throws IOException {

        File entCertDir = new File(baseDir.getCanonicalPath(), "entitlement_certificates");
        entCertDir.mkdir();

        for (EntitlementCertificate cert : entCertAdapter.listForConsumer(consumer)) {
            if (manifest && !this.exportRules.canExport(cert.getEntitlement())) {
                log.debug("Skipping export of entitlement cert with product: {}",
                    cert.getEntitlement().getPool().getProductId());

                continue;
            }

            if ((serials == null) || (serials.contains(cert.getSerial().getId()))) {
                log.debug("Exporting entitlement certificate: {}", cert.getSerial());
                File file = new File(entCertDir.getCanonicalPath(), cert.getSerial().getId() + ".pem");

                new CertificateExporter().exportCertificate(cert, file);
            }
        }
    }

    /**
     * Exports content access certificates for a consumer.
     * Consumer must belong to owner with SCA enabled.
     *
     * @param baseDir
     *  Base directory path.
     *
     * @param consumer
     *  Consumer for which content access certificates needs to be exported.
     *
     * @param serials
     *  certificate serials used to filter content access certificates.
     *
     * @throws IOException
     *  Throws IO exception if unable to export content access certs for the consumer.
     *
     * @throws ConcurrentContentPayloadCreationException
     *  if a concurrent request persists the content payload and causes a database constraint violation
     */
    private void exportContentAccessCerts(File baseDir, Consumer consumer,
        Set<Long> serials) throws IOException, ConcurrentContentPayloadCreationException {
        SCACertificate contentAccessCert = this.scaCertificateGenerator.generate(consumer);

        if (contentAccessCert != null &&
            (serials == null || contentAccessCert.getSerial() == null ||
                serials.contains(contentAccessCert.getSerial().getId()))) {
            File contentAccessCertDir = new File(baseDir.getCanonicalPath(), "content_access_certificates");
            contentAccessCertDir.mkdir();

            log.debug("Exporting content access certificate: {}", contentAccessCert.getSerial());
            File file = new File(contentAccessCertDir.getCanonicalPath(),
                contentAccessCert.getSerial().getId() + ".pem");

            new CertificateExporter().exportCertificate(contentAccessCert, file);
        }
    }

    private void exportIdentityCertificate(File baseDir, Consumer consumer)
        throws IOException {

        File idcertdir = new File(baseDir.getCanonicalPath(), "upstream_consumer");
        idcertdir.mkdir();

        IdentityCertificate cert = consumer.getIdCert();
        if (cert == null) {
            throw new RuntimeException("The consumer for export does not have a valid identity certificate");
        }

        // paradigm dictates this should go in an exporter.export method
        File file = new File(idcertdir.getCanonicalPath(), cert.getSerial().getId() + ".json");
        try (FileWriter writer = new FileWriter(file)) {
            mapper.writeValue(writer, this.translator.translate(cert, CertificateDTO.class));
        }
    }

    private void exportEntitlements(File baseDir, Consumer consumer)
        throws IOException, ExportCreationException {
        File entCertDir = new File(baseDir.getCanonicalPath(), "entitlements");
        entCertDir.mkdir();

        for (Entitlement ent : entitlementCurator.listByConsumer(consumer)) {
            if (ent.isDirty()) {
                log.error("Entitlement {} is marked as dirty.", ent.getId());
                throw new ExportCreationException("Attempted to export dirty entitlements");
            }

            if (!this.exportRules.canExport(ent)) {
                log.debug("Skipping export of entitlement with product: {}", ent.getPool().getProductId());
                continue;
            }

            log.debug("Exporting entitlement for product {}", ent.getPool().getProductId());

            File file = new File(entCertDir.getCanonicalPath(), ent.getId() + ".json");
            try (FileWriter writer = new FileWriter(file)) {
                entExporter.export(mapper, writer, ent);
            }
        }
    }

    private void exportProducts(File baseDir, Consumer consumer) throws IOException {
        File productDir = new File(baseDir.getCanonicalPath(), "products");
        productDir.mkdir();

        // TODO: This could be bulked into a single query rather than iterating and likely hitting
        // a bunch of lazy lookups.
        Map<String, Product> productMap = new HashMap<>();
        for (Entitlement entitlement : consumer.getEntitlements()) {
            Pool pool = entitlement.getPool();

            this.collectProducts(pool.getProduct(), productMap);
        }

        for (Product product : productMap.values()) {
            String path = productDir.getCanonicalPath();
            String productId = product.getId();

            File file = new File(path, productId + ".json");
            try (FileWriter writer = new FileWriter(file)) {
                productExporter.export(mapper, writer, product);
            }
        }
    }

    /**
     * Adds the specified product and all of its children products to the given product map, using
     * products' ID (*not* UUID) as the key in the map. If the specified product is null or does not
     * have a product ID, it will be silently ignored.
     *
     * @param product
     *  the product to add to the map
     *
     * @param productMap
     *  the map in which to collect products
     */
    private void collectProducts(Product product, Map<String, Product> productMap) {
        if (product == null || product.getId() == null) {
            return;
        }

        productMap.put(product.getId(), product);

        // Add provided products (if applicable)
        Collection<Product> providedProducts = product.getProvidedProducts();
        if (providedProducts != null) {
            for (Product provided : providedProducts) {
                this.collectProducts(provided, productMap);
            }
        }

        // Recursively add derived product (if applicable)
        this.collectProducts(product.getDerivedProduct(), productMap);
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
    }

    private void exportDistributorVersions(File baseDir) throws IOException {
        List<DistributorVersion> versions = distVerCurator.listAll();
        if (versions == null || versions.isEmpty()) {
            return;
        }

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
        List<Cdn> cdns = this.cdnCurator.listAll();

        if (cdns != null && !cdns.isEmpty()) {
            File cdnDir = new File(baseDir.getCanonicalPath(), "content_delivery_network");
            cdnDir.mkdir();

            for (Cdn cdn : cdns) {
                log.debug("Exporting CDN: {}", cdn.getName());
                File file = new File(cdnDir.getCanonicalPath(), cdn.getLabel() + ".json");

                try (FileWriter writer = new FileWriter(file)) {
                    cdnExporter.export(mapper, writer, cdn);
                }
            }
        }
    }
}
