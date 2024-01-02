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
package org.candlepin.spec.bootstrap.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.function.Supplier;

/**
 * Class for loading spec test configuration from external config files.
 * <p>
 * The order of config file paths determines their priority. Paths specified
 * later have higher priority.
 */
public class ExternalProperties implements Supplier<Properties> {

    private static final Logger log = LoggerFactory.getLogger(ExternalProperties.class);

    private final List<Path> sources;

    public ExternalProperties(Path... sources) {
        if (sources == null || sources.length == 0) {
            throw new IllegalArgumentException("At least one path to configuration file must be provided!");
        }
        this.sources = Arrays.asList(sources);
    }

    @Override
    public Properties get() {
        Properties properties = new Properties();

        this.sources.stream()
            .filter(this::isRegularFile)
            .forEach(path -> loadProperties(properties, path));

        return properties;
    }

    private boolean isRegularFile(Path path) {
        if (Files.isRegularFile(path)) {
            return true;
        }
        log.warn("External config file: {} was not found!", path);
        return false;
    }

    private void loadProperties(Properties properties, Path path) {
        try (InputStream input = Files.newInputStream(path)) {
            properties.load(input);
        }
        catch (IOException e) {
            throw new RuntimeException("Error occurred while reading the external properties!", e);
        }
    }

}
