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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.candlepin.config.ConfigurationPrefixes;
import org.candlepin.config.DevConfig;
import org.candlepin.config.TestConfig;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;

import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.util.Map;


public class LoggingConfiguratorTest {
    @Test
    public void configure() {
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        Logger l = context.getLogger(LoggingConfiguratorTest.class);
        DevConfig config = TestConfig.custom(Map.of(
            ConfigurationPrefixes.LOGGING_CONFIG_PREFIX + LoggingConfiguratorTest.class.getName(), "DEBUG"
        ));
        assertNotNull(l);
        assertNull(l.getLevel());

        LoggingConfigurator.init(config);

        assertNotNull(l.getLevel());
        assertEquals(Level.DEBUG, l.getLevel());
    }
}
