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
package org.candlepin.logging;

import org.candlepin.config.Configuration;
import org.candlepin.config.ConfigurationPrefixes;
import org.candlepin.util.Util;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;

import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * Sets the logback logging levels dynamically based on values from the candlepin.conf file.
 * This removes the need to crack the logback.xml file.
 *
 * Since we are actually adjusting logging configuration, we have to access the
 * underlying logger implementation instead of going through slf4j.
 *
 * See http://slf4j.org/faq.html#when
 */
public final class LoggingConfigurator {
    private LoggingConfigurator() {
        // Static methods only
        throw new UnsupportedOperationException();
    }

    public static void init(Configuration config) {
        LoggerContext lc = (LoggerContext) LoggerFactory.getILoggerFactory();
        Map<String, String> logLevels = config.getValuesByPrefix(ConfigurationPrefixes.LOGGING_CONFIG_PREFIX);

        for (Map.Entry<String, String> logLevel : logLevels.entrySet()) {
            String loggerKey = Util
                .stripPrefix(logLevel.getKey(), ConfigurationPrefixes.LOGGING_CONFIG_PREFIX);

            lc.getLogger(loggerKey).setLevel(Level.toLevel(logLevel.getValue()));
        }
    }

}
