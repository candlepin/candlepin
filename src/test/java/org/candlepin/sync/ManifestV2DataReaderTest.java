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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.candlepin.config.TestConfig;
import org.candlepin.dto.manifest.v1.ProductDTO;
import org.candlepin.dto.manifest.v1.SubscriptionDTO;
import org.candlepin.model.Owner;
import org.candlepin.util.ObjectMapperFactory;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.xnap.commons.i18n.I18n;
import org.xnap.commons.i18n.I18nFactory;

import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

class ManifestV2DataReaderTest {
    @TempDir
    private Path directory;
    private ManifestV2DataReader reader;
    private Owner owner;

    @BeforeEach
    void setup() throws Exception {
        ObjectMapper mapper = ObjectMapperFactory.getSyncObjectMapper(TestConfig.defaults());
        I18n i18n = I18nFactory.getI18n(this.getClass(), Locale.US, I18nFactory.FALLBACK);
        this.reader = new ManifestV2DataReader(mapper, i18n, new ManifestFormatDetector(i18n));
        this.owner = new Owner().setId("local-owner").setKey("local-key");
        this.write("meta.json", """
            {"manifestVersion":"2.0.0","version":"6.0.0","created":"2026-01-01T00:00:00.000+0000",
             "principalName":"exporter","webAppPrefix":"https://upstream.example"}
            """);
        this.write("upstream_consumer/consumer.json", """
            {"uuid":"distributor","name":"Distributor","ownerId":"upstream-owner",
             "type":{"id":"upstream-type-id","label":"candlepin"}}
            """);
    }

    @Test
    void importsEmptyScaManifestWithoutLegacyDirectories() throws Exception {
        ManifestV2Data data = this.reader.read(this.directory, this.owner);
        assertTrue(data.getSubscriptions().isEmpty());
        assertEquals("org_environment", data.getConsumer().getContentAccessMode());
        assertEquals("upstream-owner", data.getConsumer().getOwner().getId());
        assertEquals("candlepin", data.getConsumer().getType().getLabel());
        assertEquals("https://upstream.example", data.getConsumer().getUrlWeb());
        assertEquals("6.0.0", data.getMeta().getVersion());
        assertEquals("exporter", data.getMeta().getPrincipalName());
    }

    @Test
    void resolvesProductsAndNormalizesUnlimitedSubscriptions() throws Exception {
        this.write("products/root.json", """
            {"id":"root","name":"Root","attributes":{"arch":"x86_64"},
             "providedProducts":["child"],"derivedProduct":"derived",
             "branding":[{"productId":"child","name":"Brand","type":"OS"}],
             "productContent":{"content":{"id":"content","name":"Repository","type":"yum",
               "label":"repo","enabled":true,"vendor":"","contentUrl":"/repo","gpgUrl":"/key",
               "arches":"x86_64","releaseVer":"9","requiredTags":"tag"}}}
            """);
        this.write("products/child.json", "{\"id\":\"child\",\"name\":\"Child\"}");
        this.write("products/derived.json", "{\"id\":\"derived\",\"name\":\"Derived\"}");
        this.subscription("root");
        ManifestV2Data data = this.reader.read(this.directory, this.owner);
        assertEquals(1, data.getSubscriptions().size());
        SubscriptionDTO subscription = data.getSubscriptions().getFirst();
        assertNotNull(subscription.getId());
        assertEquals(-1L, subscription.getQuantity());
        assertEquals("upstream-pool", subscription.getUpstreamPoolId());
        assertEquals("distributor", subscription.getUpstreamConsumerId());
        assertNull(subscription.getUpstreamEntitlementId());
        assertNull(subscription.getCertificate());
        assertEquals("local-owner", subscription.getOwner().getId());
        assertEquals("contract", subscription.getContractNumber());
        assertEquals("account", subscription.getAccountNumber());
        ProductDTO product = subscription.getProduct();
        assertEquals(1L, product.getMultiplier());
        assertNull(product.getUuid());
        assertEquals("derived", product.getDerivedProduct().getId());
        assertEquals("child", product.getProvidedProducts().iterator().next().getId());
        assertEquals("x86_64", product.getAttributeValue("arch"));
        assertEquals("Brand", product.getBranding().iterator().next().getName());
        assertTrue(product.getProductContent("content").isEnabled());
        assertEquals("unknown", product.getContent("content").getVendor());
        assertEquals(1L, product.getContent("content").getMetadataExpiration());
        assertEquals("9", product.getContent("content").getReleaseVersion());
        assertEquals("/key", product.getContent("content").getGpgUrl());
    }

    @Test
    void readsPayloadFromArchiveFileSystem() throws Exception {
        this.write("products/root.json", "{\"id\":\"root\",\"name\":\"Root\"}");
        this.subscription("root");
        Path archive = this.directory.resolve("manifest.zip");
        try (FileSystem filesystem = FileSystems.newFileSystem(archive, Map.of("create", "true"))) {
            Path root = filesystem.getPath("/");
            try (Stream<Path> entries = Files.walk(this.directory)) {
                for (Path entry : entries.filter(path -> !path.equals(archive)).toList()) {
                    Path target = root.resolve(this.directory.relativize(entry).toString());
                    if (Files.isDirectory(entry)) {
                        Files.createDirectories(target);
                    }
                    else {
                        Files.copy(entry, target);
                    }
                }
            }
            ManifestV2Data data = this.reader.read(root, this.owner);
            assertEquals("6.0.0", data.getMeta().getVersion());
            assertEquals("distributor", data.getConsumer().getUuid());
            assertEquals(1, data.getSubscriptions().size());
            SubscriptionDTO subscription = data.getSubscriptions().getFirst();
            assertEquals("upstream-pool", subscription.getUpstreamPoolId());
            assertEquals("root", subscription.getProduct().getId());
            assertEquals("Root", subscription.getProduct().getName());
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"meta.json", "upstream_consumer/consumer.json"})
    void rejectsMissingRequiredFiles(String path) throws Exception {
        Files.delete(this.directory.resolve(path));
        assertThrows(SyncDataFormatException.class, () -> this.reader.read(this.directory, this.owner));
    }

    @ParameterizedTest
    @ValueSource(strings = {"null", "{}", "{\"type\":{\"label\":\"candlepin\"}}",
        "{\"uuid\":\"id\"}", "{\"uuid\":\"id\",\"type\":{\"label\":\"\"}}"})
    void rejectsInvalidConsumers(String json) throws Exception {
        this.write("upstream_consumer/consumer.json", json);
        assertThrows(SyncDataFormatException.class, () -> this.reader.read(this.directory, this.owner));
    }

    @Test
    void rejectsMissingReferencedProduct() throws Exception {
        this.subscription("missing");
        assertThrows(SyncDataFormatException.class, () -> this.reader.read(this.directory, this.owner));
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"id\":\"wrong\",\"name\":\"Root\"}",
        "{\"id\":\"root\"}", "null",
        "{\"id\":\"root\",\"name\":\"Root\",\"providedProducts\":[\"missing\"]}",
        "{\"id\":\"root\",\"name\":\"Root\",\"derivedProduct\":\"root\"}",
        "{\"id\":\"root\",\"name\":\"Root\",\"productContent\":{\"wrong\":{\"id\":\"c\"}}}"})
    void rejectsInvalidProductGraph(String json) throws Exception {
        this.write("products/root.json", json);
        assertThrows(SyncDataFormatException.class, () -> this.reader.read(this.directory, this.owner));
    }

    @Test
    void rejectsIndirectCycles() throws Exception {
        this.write("products/a.json", "{\"id\":\"a\",\"name\":\"A\",\"providedProducts\":[\"b\"]}");
        this.write("products/b.json", "{\"id\":\"b\",\"name\":\"B\",\"derivedProduct\":\"a\"}");
        assertThrows(SyncDataFormatException.class, () -> this.reader.read(this.directory, this.owner));
    }

    @Test
    void rejectsUnexpectedCollectionEntries() throws Exception {
        this.write("products/not-json.txt", "invalid");
        assertThrows(SyncDataFormatException.class, () -> this.reader.read(this.directory, this.owner));
    }

    @Test
    void rejectsReversedSubscriptionDates() throws Exception {
        this.write("subscriptions/pool.json", """
            {"startDate":"2027-01-01T00:00:00.000+0000","endDate":"2026-01-01T00:00:00.000+0000"}
            """);
        assertThrows(SyncDataFormatException.class, () -> this.reader.read(this.directory, this.owner));
    }

    @ParameterizedTest
    @ValueSource(strings = {"{", "null", "{}", "{\"manifestVersion\":\"2.0.0\",\"version\":\"6.0.0\"}"})
    void rejectsMalformedOrIncompleteMetadata(String json) throws Exception {
        this.write("meta.json", json);
        assertThrows(SyncDataFormatException.class, () -> this.reader.read(this.directory, this.owner));
    }

    @Test
    void rejectsNullArguments() {
        assertThrows(NullPointerException.class, () -> this.reader.read(null, this.owner));
        assertThrows(NullPointerException.class, () -> this.reader.read(this.directory, null));
    }

    private void subscription(String product) throws IOException {
        this.write("subscriptions/upstream-pool.json", """
            {"subscriptionId":"source-subscription","productId":"%s","contractNumber":"contract",
             "accountNumber":"account","startDate":"2026-01-01T00:00:00.000+0000",
             "endDate":"2027-01-01T00:00:00.000+0000"}
            """.formatted(product));
    }

    private void write(String name, String content) throws IOException {
        Path file = this.directory.resolve(name);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }
}
