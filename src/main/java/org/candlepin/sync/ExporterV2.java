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

import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.Writer;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Map;

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
    private final Configuration config;
    private final PrincipalProvider principalProvider;
    private final ModelTranslator translator;

    @Inject
    public ExporterV2(@Named("ExportObjectMapper") ObjectMapper mapper, Configuration config, PrincipalProvider principalProvider, ModelTranslator translator) {
        this.mapper = mapper;
        this.config = config;
        this.principalProvider = principalProvider;
        this.translator = translator;
    }

    public Path getFullExport(Consumer consumer) throws ExportCreationException {
        try {
            ExportData data = new ExportData(consumer, translator, config, principalProvider);
            Path archivePath = createArchiveFile(data);
            try (FileSystem archive = createArchive(archivePath)) {
                writeFile(archive.getPath(META_JSON), data.getMetaDTO());
                writeFile(archive.getPath(UPSTREAM_CONSUMER, CONSUMER_JSON), data.getConsumerDTO());
                for (SubscriptionDTO entry : data.getSubscriptions().values()) {
                    writeFile(
                        archive.getPath(SUBSCRIPTIONS, String.format(ID_JSON, entry.subscriptionId())),
                        entry);
                }
                for (ProductDTO entry : data.getProducts().values()) {
                    writeFile(
                        archive.getPath(PRODUCTS, String.format(ID_JSON, entry.id())),
                        entry);
                }
            }
            return archivePath;
        }
        catch (IOException e) {
            log.error("Error generating manifest export V2", e);
            throw new ExportCreationException("Unable to create export archive", e);
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

    private FileSystem createArchive(Path archivePath) throws IOException {
        Files.delete(archivePath);
        return FileSystems.newFileSystem(URI.create("jar:" + archivePath.toUri()), getCreateEnv());
    }

    private static @NonNull Map<String, String> getCreateEnv() {
        return Map.of("create", "true");
    }

    private Path createArchiveFile(ExportData data) throws IOException {
        return Files.createTempFile(data.getWorkDirectory(), "export-", ".zip");
    }
}
