/*
 * Copyright (c) 2009 - 2026 Red Hat, Inc.
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

import org.candlepin.dto.manifest.v2.CertificateDTO;
import org.candlepin.dto.manifest.v2.ProductDTO;
import org.candlepin.dto.manifest.v2.SubscriptionDTO;
import org.candlepin.model.Consumer;
import org.candlepin.pki.CryptoCapabilitiesException;
import org.candlepin.pki.CryptoManager;
import org.candlepin.pki.Scheme;
import org.candlepin.pki.SignatureException;

import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;
import java.net.URI;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystemAlreadyExistsException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.ProviderNotFoundException;
import java.nio.file.StandardOpenOption;
import java.security.cert.CertificateEncodingException;
import java.util.Map;
import java.util.Objects;

import jakarta.inject.Inject;
import jakarta.inject.Named;

/**
 * Serializes version 2 manifest data into a ZIP archive
 * <p>
 * Delegates data gathering, DTO translation, and certificate generation to {@link ExportDataAggregator}.
 * Owns archive layout, JSON writing, and cleanup of incomplete archives. Completed archives are
 * returned to the caller for delivery and eventual deletion. New payload elements added to {@link ExportData}
 * require corresponding archive entries here.
 */
public class ExporterV2 {
    private static final Logger log = LoggerFactory.getLogger(ExporterV2.class);
    /**
     * Filename for manifest metadata at the archive root
     */
    public static final String META_JSON = "meta.json";
    /**
     * Directory containing the upstream consumer representation
     */
    public static final String UPSTREAM_CONSUMER = "upstream_consumer";
    /**
     * Filename for the upstream consumer JSON representation
     */
    public static final String CONSUMER_JSON = "consumer.json";
    /**
     * Directory for subscription JSON files keyed by pool ID
     */
    public static final String SUBSCRIPTIONS_DIRECTORY = "subscriptions";
    /**
     * Directory for product JSON files keyed by public product ID
     */
    public static final String PRODUCTS_DIRECTORY = "products";
    /**
     * JSON filename pattern with an identifier placeholder
     */
    public static final String ID_JSON = "%s.json";
    /**
     * Directory for SCA certificate and private key JSON files
     */
    public static final String CERTIFICATES_DIRECTORY = "content_access_certificates";
    public static final String CONSUMER_EXPORT_ZIP = "consumer_export.zip";
    public static final String SIGNATURE = "signature";

    private final ObjectMapper mapper;
    private final ExportDataAggregator exportDataAggregator;
    private final CryptoManager cryptoManager;

    /**
     * Initializes manifest serialization and data aggregation dependencies
     *
     * @param mapper
     *     export-configured JSON mapper used to serialize manifest DTOs
     * @param exportDataAggregator
     *     aggregator supplying manifest DTOs, the existing identity certificate, a generated SCA certificate,
     *     and the export work directory
     * @param cryptoManager
     *     manager selecting the consumer's cryptographic scheme and signing the manifest archive
     * @throws NullPointerException
     *     if any dependency is null
     */
    @Inject
    public ExporterV2(@Named("ExportObjectMapper") ObjectMapper mapper, ExportDataAggregator exportDataAggregator,
        CryptoManager cryptoManager) {
        this.mapper = Objects.requireNonNull(mapper);
        this.exportDataAggregator = Objects.requireNonNull(exportDataAggregator);
        this.cryptoManager = Objects.requireNonNull(cryptoManager);
    }

    /**
     * Creates a signed version 2 manifest ZIP archive for the supplied consumer
     * <p>
     * Delegates export data selection, namespace filtering, DTO translation, and SCA certificate generation
     * to {@link ExportDataAggregator}. The inner archive contains {@code meta.json}, {@code scheme.json},
     * {@code upstream_consumer/consumer.json}, subscription JSON files under {@code subscriptions/} named
     * by pool ID, and product JSON files under {@code products/} named by public product ID. The consumer's
     * existing identity certificate, private key, and serial metadata are serialized to
     * {@code upstream_consumer/<identity-certificate-serial-id>.json} for upstream authentication. The SCA
     * certificate, content payload, private key, and serial metadata are serialized as JSON to
     * {@code content_access_certificates/<certificate-serial-id>.json}.
     * <p>
     * Selects a cryptographic scheme before aggregation, using the consumer's capabilities and the
     * crypto manager's negotiation and default-scheme rules. Unsupported capabilities prevent aggregation
     * and archive creation. Includes the selected scheme's certificate and algorithms in {@code scheme.json}.
     * Signs the inner archive using that scheme and packages it as
     * {@code consumer_export.zip} alongside its detached {@code signature} in the returned ZIP archive.
     * <p>
     * Creates archives in the configured work directory, creating that directory if necessary. The caller
     * is responsible for storing or serving the completed archive and deleting it when no longer needed.
     * Attempts to delete the temporary inner archive after export creation and any partial output archive
     * if signing or packaging fails. Cleanup I/O failures are logged and suppressed.
     *
     * @param consumer
     *     non-null consumer whose owner supplies the exported subscriptions; must have an existing identity
     *     certificate with a serial and serial ID
     * @throws ExportCreationException
     *     if no cryptographic scheme can be selected, the identity certificate, serial, or serial ID is missing,
     *     SCA certificate generation fails, an archive path or ZIP filesystem cannot be created, certificate
     *     encoding or archive I/O fails, or signing fails
     * @throws IllegalArgumentException
     *     if the consumer is null or a pool or product ID used as a filename contains the archive's path separator
     * @return the path to the completed, closed outer ZIP archive containing the inner manifest ZIP and its
     *     detached signature; the caller owns cleanup of this file
     */
    public Path getFullExport(Consumer consumer) throws ExportCreationException {
        Scheme scheme = this.getCryptoScheme(consumer);
        ExportData data = this.exportDataAggregator.aggregate(consumer);
        Path archivePath = createArchiveFile(data);
        try {
            CertificateDTO certificate = data.getScaCertificate();
            try (FileSystem archive = createArchive(archivePath)) {
                writeFile(archive.getPath(SchemeFile.FILENAME), SchemeFile.from(scheme));
                writeFile(certificatePath(archive, certificate), certificate);
                writeFile(archive.getPath(META_JSON), data.getMetaDTO());
                writeFile(archive.getPath(UPSTREAM_CONSUMER, CONSUMER_JSON), data.getConsumerDTO());
                CertificateDTO identityCertificate = data.getIdentityCertificate();
                writeFile(pathBasedOnId(archive, UPSTREAM_CONSUMER,
                    identityCertificate.getSerial().getId().toString()), identityCertificate);
                for (Map.Entry<String, SubscriptionDTO> entry : data.getSubscriptions().entrySet()) {
                    writeFile(
                        pathBasedOnId(archive, SUBSCRIPTIONS_DIRECTORY, entry.getKey()),
                        entry.getValue());
                }
                for (ProductDTO entry : data.getProducts()
                    .values()) {
                    writeFile(
                        pathBasedOnId(archive, PRODUCTS_DIRECTORY, entry.getId()),
                        entry);
                }
            }
            return this.signArchive(archivePath, data, scheme);
        }
        catch (IOException | CertificateEncodingException e) {
            log.error("Error generating manifest export V2", e);
            throw new ExportCreationException("Unable to create export archive", e);
        }
        finally {
            deleteArchive(archivePath);
        }
    }

    private Path certificatePath(FileSystem archive, CertificateDTO certificateDTO) {
        return pathBasedOnId(archive, CERTIFICATES_DIRECTORY,
            certificateDTO.getSerial().getId().toString());
    }

    private Scheme getCryptoScheme(Consumer consumer) throws ExportCreationException {
        try {
            return cryptoManager.getCryptoScheme(consumer);
        }
        catch (CryptoCapabilitiesException e) {
            throw new ExportCreationException("Unable to select a cryptographic scheme for export", e);
        }
    }

    private Path signArchive(Path archivePath, ExportData data, Scheme scheme) throws ExportCreationException {

        Path signedArchivePath = this.createArchiveFile(data);
        try (FileSystem signedArchive = this.createArchive(signedArchivePath);
             InputStream input = Files.newInputStream(archivePath)) {
            byte[] signature = this.cryptoManager.getSigner(scheme).sign(input);
            Files.copy(archivePath, signedArchive.getPath(CONSUMER_EXPORT_ZIP));
            Files.write(signedArchive.getPath(SIGNATURE), signature, StandardOpenOption.CREATE_NEW);
            return signedArchivePath;
        }
        catch (IOException | SignatureException e) {
            log.error("Error signing manifest export V2", e);
            this.deleteArchive(signedArchivePath);
            throw new ExportCreationException("Unable to sign export archive", e);
        }
    }

    private void writeFile(Path filePath, Object dto) throws IOException {
        Path parent = filePath.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        try (Writer output = Files.newBufferedWriter(filePath, StandardOpenOption.CREATE_NEW)) {
            mapper.writeValue(output, dto);
        }
    }

    private Path pathBasedOnId(FileSystem fs, String directory, String id) {
        if (id.contains(fs.getSeparator())) {
            throw new IllegalArgumentException("id cannot contain path separator");
        }
        return fs.getPath(directory, String.format(ExporterV2.ID_JSON, id));
    }

    /**
     * Attempts to delete an export archive
     * <p>
     * Logs and suppresses I/O failures, including a missing archive, so cleanup does not hide the original
     * export failure. Callers can also use this method to remove a completed archive after delivery.
     *
     * @param archivePath
     *     archive to delete
     * @throws NullPointerException
     *     if the archive path is null
     */
    public void deleteArchive(Path archivePath) {
        try {
            Files.delete(archivePath);
        }
        catch (IOException e) {
            log.error("Unable to delete export archive: {}", e.getMessage(), e);
        }
    }

    private FileSystem createArchive(Path archivePath) throws ExportCreationException {
        try {
            return FileSystems.newFileSystem(URI.create("jar:" + archivePath.toUri()), getCreateEnv());
        }
        catch (ProviderNotFoundException | FileSystemAlreadyExistsException | IOException e) {
            log.error("Unable to create export archive: {}", e.getMessage(), e);
            throw new ExportCreationException("Unable to create export archive", e);
        }
    }

    private static @NonNull Map<String, String> getCreateEnv() {
        return Map.of("create", "true");
    }

    /**
     * Prepares a unique archive path in the export work directory
     * <p>
     * Creates the directory if necessary, reserves a temporary filename, and attempts to remove the empty
     * placeholder so the ZIP filesystem can create the archive at that path.
     *
     * @param data
     *     export data supplying the work directory
     * @throws ExportCreationException
     *     if the work directory or temporary archive file cannot be created
     * @return the path prepared for ZIP archive creation
     */
    private Path createArchiveFile(ExportData data) throws ExportCreationException {
        Path workDirectory = data.getWorkDirectory();
        try {
            Files.createDirectories(workDirectory);
            Path archiveFile = Files.createTempFile(workDirectory, "export-", ".zip");
            deleteArchive(archiveFile);
            return archiveFile;
        }
        catch (IllegalArgumentException e) {
            log.error("Unable to create export archive: Directory does not exist. ({})", workDirectory, e);
            throw new ExportCreationException("Unable to create export archive", e);
        }
        catch (FileAlreadyExistsException e) {
            log.error("Unable to create export archive: Directory points to an existing file. ({})", workDirectory, e);
            throw new ExportCreationException("Unable to create export archive", e);
        }
        catch (IOException e) {
            log.error("Unable to create export archive: Cannot create archive file.", e);
            throw new ExportCreationException("Unable to create export archive", e);
        }
    }
}
