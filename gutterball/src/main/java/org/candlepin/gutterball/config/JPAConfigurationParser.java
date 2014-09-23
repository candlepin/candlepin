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

package org.candlepin.gutterball.config;

import org.candlepin.common.config.Configuration;
import org.candlepin.common.config.ConfigurationParser;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * Parses all JPA related config options from the config file.
 *
 */
//FIXME: This class should be removed and the one currently in CP should
//       be used when the configuration refactor is done.
//       THERE IS CURRENTLY NO ENCRYPTION SUPPORT.

public class JPAConfigurationParser extends ConfigurationParser {


    /** JPA configuration prefix */
    public static final String JPA_CONFIG_PREFIX = "jpa.config";
    private Configuration config;

    public JPAConfigurationParser(Configuration config) {
        this.config = config;
    }

    public String getPrefix() {
        return JPA_CONFIG_PREFIX;
    }

    public Properties parseConfig() {
        return this.parseConfig(configToMap());
    }

    public Map<String, String> configToMap() {
        HashMap<String, String> all = new HashMap<String, String>();
        for (String key : config.getKeys()) {
            all.put(key, config.getString(key));
        }

        return all;
    }
}
