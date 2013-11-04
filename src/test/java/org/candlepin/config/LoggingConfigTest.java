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
package org.candlepin.config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;

import org.junit.Before;
import org.junit.Test;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * LoggingConfigTest
 */
public class LoggingConfigTest {

    private Config config;

    private LoggingConfig lc;

    @Before
    public void init() {
        config = mock(Config.class);
        lc = new LoggingConfig(config);
    }

    @Test
    public void configure() {
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        Logger l = context.getLogger(LoggingConfigTest.class);
        assertNotNull(l);
        assertNull(l.getLevel());

        Map<String, String> loglevels = new HashMap<String, String>();
        loglevels.put(LoggingConfigTest.class.getName(), "DEBUG");

        when(config.configurationWithPrefix(LoggingConfig.PREFIX)).thenReturn(loglevels);

        lc.configure(config);
        assertNotNull(l.getLevel());
        assertEquals(Level.DEBUG, l.getLevel());
    }

    @Test(expected = NullPointerException.class)
    public void expectNull() {
        when(config.configurationWithPrefix(LoggingConfig.PREFIX)).thenReturn(null);
        lc.configure(config);
    }
}
