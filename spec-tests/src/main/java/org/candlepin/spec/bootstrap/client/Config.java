/*
 *  Copyright (c) 2009 - ${YEAR} Red Hat, Inc.
 *
 *  This software is licensed to you under the GNU General Public License,
 *  version 2 (GPLv2). There is NO WARRANTY for this software, express or
 *  implied, including the implied warranties of MERCHANTABILITY or FITNESS
 *  FOR A PARTICULAR PURPOSE. You should have received a copy of GPLv2
 *  along with this software; if not, see
 *  http://www.gnu.org/licenses/old-licenses/gpl-2.0.txt.
 *
 *  Red Hat trademarks are not licensed under GPLv2. No permission is
 *  granted to use or replicate Red Hat trademarks that are incorporated
 *  in this software or its documentation.
 */

package org.candlepin.spec.bootstrap.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Properties;

public class Config {
    private static final Logger log = LoggerFactory.getLogger(Config.class);

    private final Properties properties;

    public Config(Properties properties) {
        this.properties = properties;
    }

    /**
     * TODO
     * @param sources Property sources ordered by priority from low to high
     * @return config combined from all sources with high priority sources overwriting the lower ones
     */
    public static Config fromSources(PropertySource... sources) {
        Properties properties = new Properties();
        for (PropertySource source : sources) {
            properties.putAll(source.get());
        }
        return new Config(properties);
    }

    public String get(ConfigKey key) {
        String property = this.properties.getProperty(key.key());
        log.info("Loading a config property {} with value {}", key, property);
        return property;
    }

    public boolean getBool(ConfigKey key) {
        return Boolean.parseBoolean(this.properties.getProperty(key.key()));
    }

}
