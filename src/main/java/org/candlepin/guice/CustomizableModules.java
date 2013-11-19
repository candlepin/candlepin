/**
 * Copyright (c) 2009 - 2012 Red Hat, Inc.
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
package org.candlepin.guice;

import org.candlepin.config.Config;

import com.google.inject.Module;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.Set;

/**
 * CustomizableModules
 */
public class CustomizableModules {

    public static final String MODULE_CONFIG_PREFIX = "module.config";
    private static Logger log = LoggerFactory.getLogger(CustomizableModules.class);

    /**
     * @return returns the set of modules to use.
     */
    public Set<Module> load() {
        Map<String, String> loaded =
            configuration().configurationWithPrefix(MODULE_CONFIG_PREFIX);

        return customizedConfiguration(loaded);
    }

    /**
     * Reads the given configuration and returns the set of modules.
     * @param loadedConfiguration configuration to parse.
     * @return Set of configured modules.
     */
    @SuppressWarnings("unchecked")
    public Set<Module> customizedConfiguration(Map<String, String> loadedConfiguration) {
        try {
            Set toReturn = new HashSet();

            for (Entry<String, String> entry : loadedConfiguration.entrySet()) {
                log.info("Found custom module " + entry.getKey());
                toReturn.add(Class.forName(entry.getValue()).newInstance());
            }

            return toReturn;
        }
        catch (Exception e) {
            throw new RuntimeException("Exception when instantiation guice module.", e);
        }
    }

    /**
     * Load custom configuration from the given input stream.
     * @param input Stream containing configuration information.
     * @return Map of the configuration.
     * @throws IOException thrown if there is a problem reading the stream.
     */
    @SuppressWarnings("unchecked")
    public Map<String, String> loadCustomConfiguration(InputStream input)
        throws IOException {

        Properties loaded = new Properties();
        loaded.load(input);
        return new HashMap(loaded);
    }

    protected Config configuration() {
        return new Config();
    }
}
