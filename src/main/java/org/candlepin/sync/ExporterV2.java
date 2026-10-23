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

import org.candlepin.config.Configuration;
import org.candlepin.dto.ModelTranslator;
import org.candlepin.dto.manifest.v2.ProductDTO;
import org.candlepin.dto.manifest.v2.SubscriptionDTO;
import org.candlepin.guice.PrincipalProvider;
import org.candlepin.model.Consumer;
import org.candlepin.model.PoolCurator;
import org.candlepin.model.ProductCurator;
import org.candlepin.model.SCACertificate;
import org.candlepin.pki.certs.SCACertificateGenerator;

import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
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
import java.util.Map;
import java.util.Objects;

import jakarta.inject.Inject;
import jakarta.inject.Named;

public class ExporterV2 {
    private static final Logger log = LoggerFactory.getLogger(ExporterV2.class);
    public static final String META_JSON = "meta.json";
    public static final String UPSTREAM_CONSUMER = "upstream_consumer";
    public static final String CONSUMER_JSON = "consumer.json";
    public static final String SUBSCRIPTIONS_DIRECTORY = "subscriptions";
    public static final String PRODUCTS_DIRECTORY = "products";
    public static final String ID_JSON = "%s.json";
    public static final String ID_PEM = "%s.pem";
    public static final String CERTIFICATES_DIRECTORY = "content_access_certificates";

    private final ObjectMapper mapper;
    private final Configuration config;
    private final PoolCurator poolCurator;
    private final ProductCurator productCurator;
    private final PrincipalProvider principalProvider;
    private final ModelTranslator translator;
    private final SCACertificateGenerator scaCertificateGenerator;

    @Inject
    public ExporterV2(@Named("ExportObjectMapper") ObjectMapper mapper, Configuration config, PoolCurator poolCurator,
        ProductCurator productCurator, PrincipalProvider principalProvider, ModelTranslator translator,
        SCACertificateGenerator scaCertificateGenerator) {
        this.poolCurator = Objects.requireNonNull(poolCurator);
        this.productCurator = Objects.requireNonNull(productCurator);
        this.mapper = Objects.requireNonNull(mapper);
        this.config = Objects.requireNonNull(config);
        this.principalProvider = Objects.requireNonNull(principalProvider);
        this.translator = Objects.requireNonNull(translator);
        this.scaCertificateGenerator = Objects.requireNonNull(scaCertificateGenerator);
    }

    public Path getFullExport(Consumer consumer) throws ExportCreationException {
        ExportData data = new ExportData(consumer, translator, config, poolCurator, productCurator, principalProvider,
            this.scaCertificateGenerator);
        Path archivePath = createArchiveFile(data);
        try {
            SCACertificate certificate = data.getScaCertificate();
            try (FileSystem archive = createArchive(archivePath)) {
                writeFile(
                    pathBasedOnId(
                        archive,
                        CERTIFICATES_DIRECTORY,
                        ID_PEM,
                        certificate.getSerial()
                            .getId()
                            .toString()),
                    writer -> writer.write(certificate.getCert() + certificate.getKey()));
                writeFile(archive.getPath(META_JSON), data.getMetaDTO());
                writeFile(archive.getPath(UPSTREAM_CONSUMER, CONSUMER_JSON), data.getConsumerDTO());
                for (SubscriptionDTO entry : data.getSubscriptions()
                    .values()) {
                    writeFile(
                        pathBasedOnId(archive, SUBSCRIPTIONS_DIRECTORY, ID_JSON, entry.getSubscriptionId()),
                        entry);
                }
                for (ProductDTO entry : data.getProducts()
                    .values()) {
                    writeFile(
                        pathBasedOnId(archive, PRODUCTS_DIRECTORY, ID_JSON, entry.getId()),
                        entry);
                }
            }
            return archivePath;
        }
        catch (IOException e) {
            // clean-up archive, there was an error generating the export
            deleteArchive(archivePath);
            log.error("Error generating manifest export V2", e);
            throw new ExportCreationException("Unable to create export archive", e);
        }
    }

    @FunctionalInterface
    interface ContentWriter {
        void write(Writer writer) throws IOException;
    }

    private void writeFile(Path filePath, ContentWriter contentWriter) throws IOException {
        Path parent = filePath.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        try (Writer output = Files.newBufferedWriter(filePath, StandardOpenOption.CREATE_NEW)) {
            contentWriter.write(output);
        }
    }

    private void writeFile(Path filePath, Object dto) throws IOException {
        writeFile(filePath, writer -> mapper.writeValue(writer, dto));
    }

    private Path pathBasedOnId(FileSystem fs, String directory, String namePattern, String id) {
        if (id.contains(fs.getSeparator())) {
            throw new IllegalArgumentException("id cannot contain path separator");
        }
        return fs.getPath(directory, String.format(namePattern, id));
    }

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
