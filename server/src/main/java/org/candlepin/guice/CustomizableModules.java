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

import org.candlepin.common.config.Configuration;

import com.google.inject.Module;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
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
    public Set<Module> load(Configuration config) {
        Configuration moduleConfig = config.subset(MODULE_CONFIG_PREFIX);

        return customizedConfiguration(moduleConfig);
    }

    /**
     * Reads the given configuration and returns the set of modules.
     * @param moduleConfig configuration to parse.
     * @return Set of configured modules.
     */
    @SuppressWarnings("unchecked")
    public Set<Module> customizedConfiguration(Configuration moduleConfig) {
        try {
            Set<Module> toReturn = new HashSet<Module>();

            for (String key : moduleConfig.getKeys()) {
                log.info("Found custom module " + key);
                Class<? extends Module> c = (Class<? extends Module>)
                    Class.forName(moduleConfig.getString(key));
                toReturn.add(c.newInstance());
            }

            return toReturn;
        }
        catch (Exception e) {
            throw new RuntimeException("Exception when instantiation guice module.", e);
        }
    }
}
