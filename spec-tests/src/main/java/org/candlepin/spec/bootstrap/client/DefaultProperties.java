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

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import java.util.function.Supplier;

/**
 * Class for loading spec test configuration from internal config file.
 */
public class DefaultProperties implements Supplier<Properties> {

    @Override
    public Properties get() {
        Properties properties = new Properties();
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        try (InputStream inputStream = loader.getResourceAsStream("settings.properties")) {
            if (inputStream == null) {
                throw new IllegalStateException("Could not find settings.properties");
            }
            loadProperties(inputStream, properties);
        }
        catch (IOException e) {
            throw new RuntimeException("Error loading properties", e);
        }
        return properties;
    }


    private void loadProperties(InputStream inputStream, Properties properties) {
        try {
            properties.load(inputStream);
        }
        catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
