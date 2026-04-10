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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

import org.candlepin.config.DevConfig;
import org.candlepin.config.TestConfig;
import org.candlepin.pki.CryptoManager;
import org.candlepin.pki.Scheme;
import org.candlepin.test.CryptoUtil;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.stream.Stream;

public class SchemeFileExporterTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static Stream<Arguments> schemeSource() {
        return CryptoUtil.SUPPORTED_SCHEMES.values()
            .stream()
            .map(Arguments::of);
    }

    private CryptoManager cryptoManager;

    @BeforeEach
    public void beforeEach() {
        DevConfig config = TestConfig.defaults();
        this.cryptoManager = CryptoUtil.getCryptoManager(config);
    }

    @Test
    public void testExportWithNullWriter() {
        SchemeFileExporter exporter = new SchemeFileExporter(MAPPER);
        Scheme scheme = cryptoManager.getDefaultCryptoScheme();

        assertThrows(IllegalArgumentException.class, () -> {
            exporter.export(null, scheme);
        });
    }

    @Test
    public void testExportWithNullScheme() {
        SchemeFileExporter exporter = new SchemeFileExporter(MAPPER);
        Writer writer = mock(Writer.class);

        assertThrows(IllegalArgumentException.class, () -> {
            exporter.export(writer, null);
        });
    }

    @ParameterizedTest
    @MethodSource("schemeSource")
    public void testExport(Scheme scheme) throws Exception {
        File file = File.createTempFile("temp", "json");
        SchemeFileExporter exporter = new SchemeFileExporter(MAPPER);

        try (FileWriter writer = new FileWriter(file)) {
            exporter.export(writer, scheme);
        }

        SchemeFile expectedSchemeFile = SchemeFile.from(scheme);
        JsonNode expected = MAPPER.valueToTree(expectedSchemeFile);

        this.assertSchemeFile(expected, file);
    }

    @Test
    public void testExportWithSerializationError() throws Exception {
        Scheme scheme = this.cryptoManager.getDefaultCryptoScheme();
        File file = File.createTempFile("temp", "json");

        ObjectMapper mockMapper = mock(ObjectMapper.class);
        doThrow(JacksonException.class).when(mockMapper).writeValue(any(Writer.class), any(SchemeFile.class));

        SchemeFileExporter exporter = new SchemeFileExporter(mockMapper);
        try (FileWriter writer = new FileWriter(file)) {
            assertThrows(IOException.class, () -> exporter.export(writer, scheme));
        }
    }

    private void assertSchemeFile(JsonNode expected, File file) throws IOException {
        JsonNode actualNode = MAPPER.readTree(file);

        assertThat(actualNode)
            .isEqualTo(expected);
    }

}
