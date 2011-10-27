/**
 * Copyright (c) 2009 Red Hat, Inc.
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
package org.candlepin.config;

import java.util.Map;
import java.util.Map.Entry;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;

import com.google.inject.Inject;

/**
 * Sets the log4j logging levels dynamically based on values from the candlepin.conf file.
 * This removes the need to crack the log4j.properties file.
 */
public class LoggingConfig {

    public static final String PREFIX = "log4j.logger.";

    @Inject
    public LoggingConfig(Config config) {
        configure(config);
    }

    public void configure(Config config) {
        Map<String, String> logLevels = config.configurationWithPrefix(PREFIX);
        for (Entry<String, String> entry : logLevels.entrySet()) {
            String key = entry.getKey().replace(PREFIX, "");
            Logger.getLogger(key).setLevel(Level.toLevel(entry.getValue()));
        }
    }
}
