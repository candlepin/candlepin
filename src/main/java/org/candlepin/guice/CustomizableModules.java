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
package org.candlepin.guice;

import org.candlepin.config.Configuration;

import com.google.inject.Module;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class CustomizableModules {

    private static final Logger log = LoggerFactory.getLogger(CustomizableModules.class);
    private static final String MODULE_CONFIG_PREFIX = "module.config";


    /**
     * @return returns the set of modules to use.
     */
    public Set<Module> load(Configuration config) {
        Map<String, String> moduleConfig = config.getValuesByPrefix(MODULE_CONFIG_PREFIX);

        return customizedConfiguration(moduleConfig);
    }

    /**
     * Reads the given configuration and returns the set of modules.
     * @param moduleConfig configuration to parse.
     * @return Set of configured modules.
     */
    @SuppressWarnings("unchecked")
    public Set<Module> customizedConfiguration(Map<String, String> moduleConfig) {
        try {
            Set<Module> toReturn = new HashSet<>();

            for (Map.Entry<String, String> entry : moduleConfig.entrySet()) {
                log.info("Found custom module {}", entry.getKey());
                Class<? extends Module> c = (Class<? extends Module>)
                    Class.forName(entry.getValue());
                toReturn.add(c.newInstance());
            }

            return toReturn;
        }
        catch (Exception e) {
            throw new RuntimeException("Exception when instantiation guice module.", e);
        }
    }
}
