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
package org.candlepin.common.logging;

import org.candlepin.common.config.Configuration;
import org.candlepin.common.config.ConfigurationPrefixes;

import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;

/**
 * Sets the log4j logging levels dynamically based on values from the candlepin.conf file.
 * This removes the need to crack the log4j.properties file.
 *
 * Since we are actually adjusting logging configuration, we have to access the
 * underlying logger implementation instead of going through slf4j.
 *
 * See http://slf4j.org/faq.html#when
 */
public class LoggingConfigurator {
    private LoggingConfigurator() {
        // Static methods only
    }

    public static void init(Configuration config) {
        LoggerContext lc = (LoggerContext) LoggerFactory.getILoggerFactory();
        Configuration logLevels = config.strippedSubset(ConfigurationPrefixes.LOGGING_CONFIG_PREFIX);

        for (String key : logLevels.getKeys()) {
            lc.getLogger(key).setLevel(Level.toLevel(logLevels.getString(key)));
        }
    }
}
