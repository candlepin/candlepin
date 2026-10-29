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

import org.candlepin.dto.manifest.v2.ProductDTO;
import org.candlepin.dto.manifest.v2.SubscriptionDTO;
import org.candlepin.model.Consumer;

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
    public static final String SUBSCRIPTIONS = "subscriptions";
    public static final String PRODUCTS = "products";
    public static final String ID_JSON = "%s.json";

    private final ObjectMapper mapper;
    private final ExportDataAggregator exportDataAggregator;

    @Inject
    public ExporterV2(@Named("ExportObjectMapper") ObjectMapper mapper, ExportDataAggregator exportDataAggregator) {
        this.mapper = Objects.requireNonNull(mapper);
        this.exportDataAggregator = Objects.requireNonNull(exportDataAggregator);
    }

    /**
     * Creates a version 2 manifest ZIP archive for the supplied consumer
     * <p>
     * Delegates data gathering and translation to {@link ExportDataAggregator}, then writes JSON entries
     * for export metadata, the upstream consumer, owner subscriptions, and their resolved products.
     * The archive contains {@code meta.json}, {@code upstream_consumer/consumer.json}, subscription files
     * under {@code subscriptions/} named by pool ID, and product files under {@code products/} named by
     * product ID. Namespace filtering and selection of export data are handled by the aggregator.
     * <p>
     * Creates the archive in the configured work directory, creating that directory if necessary.
     * Returns after the archive has been closed. The caller is responsible for storing or serving the
     * completed archive and deleting it when no longer needed. If writing or closing the archive fails
     * with an I/O error, attempts to delete the partial archive before reporting the failure.
     *
     * @param consumer
     *     consumer to represent in the manifest and whose owner supplies the exported subscriptions
     * @throws ExportCreationException
     *     if the archive path cannot be prepared, the ZIP filesystem cannot be opened, or archive I/O fails
     * @throws NullPointerException
     *     if the consumer is null
     * @throws IllegalArgumentException
     *     if a pool or product ID used as a filename contains the archive's path separator
     * @return the path to the completed ZIP archive
     */
    public Path getFullExport(Consumer consumer) throws ExportCreationException {
        boolean success = false;
        ExportData data = this.exportDataAggregator.aggregate(consumer);
        Path archivePath = createArchiveFile(data);
        try {
            try (FileSystem archive = createArchive(archivePath)) {
                writeFile(archive.getPath(META_JSON), data.getMetaDTO());
                writeFile(archive.getPath(UPSTREAM_CONSUMER, CONSUMER_JSON), data.getConsumerDTO());
                for (Map.Entry<String, SubscriptionDTO> entry : data.getSubscriptions().entrySet()) {
                    writeFile(
                        pathBasedOnId(archive, SUBSCRIPTIONS, ID_JSON, entry.getKey()),
                        entry.getValue());
                }
                for (ProductDTO entry : data.getProducts().values()) {
                    writeFile(
                        pathBasedOnId(archive, PRODUCTS, ID_JSON, entry.getId()),
                        entry);
                }
            }
            success = true;
            return archivePath;
        }
        catch (IOException e) {
            log.error("Error generating manifest export V2", e);
            throw new ExportCreationException("Unable to create export archive", e);
        }
        finally {
            // clean-up archive, there was an error generating the export
            if (!success && archivePath != null) {
                deleteArchive(archivePath);
            }
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
