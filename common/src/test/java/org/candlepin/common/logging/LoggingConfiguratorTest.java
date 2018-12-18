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

import static org.junit.jupiter.api.Assertions.*;

import org.candlepin.common.config.ConfigurationPrefixes;
import org.candlepin.common.config.MapConfiguration;

import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;

import java.util.HashMap;
import java.util.Map;

/**
 * LoggingConfigTest
 */
public class LoggingConfiguratorTest {
    @Test
    public void configure() {
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        Logger l = context.getLogger(LoggingConfiguratorTest.class);
        assertNotNull(l);
        assertNull(l.getLevel());

        Map<String, String> logLevels = new HashMap<>();
        String key = ConfigurationPrefixes.LOGGING_CONFIG_PREFIX + LoggingConfiguratorTest.class.getName();
        logLevels.put(key, "DEBUG");
        LoggingConfigurator.init(new MapConfiguration(logLevels));
        assertNotNull(l.getLevel());
        assertEquals(Level.DEBUG, l.getLevel());
    }
}
