/**
 * Copyright (c) 2009 - 2022 Red Hat, Inc.
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

package org.candlepin.spec.bootstrap.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

class ExternalPropertiesTest {

    private static final String KEY = "content";
    private static final String VALUE = "bar";
    private FileSystem fs;

    @BeforeEach
    void beforeEach() {
        fs = Jimfs.newFileSystem(Configuration.unix());
    }

    @AfterEach
    void afterEach() throws IOException {
        fs.close();
    }

    @Test
    void shouldReadProperties() {
        Path configFile = fs.getPath("/config", ".candlepin_spec.conf");
        createFile(configFile, KEY + "=" + VALUE);

        ExternalProperties properties = new ExternalProperties(configFile);

        assertThat(properties.get())
            .containsEntry(KEY, VALUE);
    }

    @Test
    void shouldGivePriorityToLaterFiles() {
        String priorityValue = VALUE;
        Path configFile1 = fs.getPath("/config", ".candlepin_spec1.conf");
        createFile(configFile1, KEY + "=" + VALUE);
        Path configFile2 = fs.getPath("/other_config", ".candlepin_spec2.conf");
        createFile(configFile2, KEY + "=" + priorityValue);

        ExternalProperties properties = new ExternalProperties(configFile1, configFile2);

        assertThat(properties.get())
            .containsEntry(KEY, priorityValue);
    }

    @Test
    void shouldRequireProvidedConfigPaths() {
        assertThatThrownBy(ExternalProperties::new)
            .isInstanceOf(IllegalArgumentException.class);
    }

    private static void createFile(Path path, String content) {
        try {
            Files.createDirectories(path.getParent());
            Files.createFile(path);
            Files.write(path, List.of(content), StandardCharsets.UTF_8);
        }
        catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
