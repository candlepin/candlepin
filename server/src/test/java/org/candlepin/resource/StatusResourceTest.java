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
package org.candlepin.resource;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import org.candlepin.auth.KeycloakConfiguration;
import org.candlepin.cache.CandlepinCache;
import org.candlepin.cache.StatusCache;
import org.candlepin.common.config.Configuration;
import org.candlepin.config.ConfigProperties;
import org.candlepin.controller.ModeManager;
import org.candlepin.dto.api.v1.KeycloakStatusDTO;
import org.candlepin.dto.api.v1.StatusDTO;
import org.candlepin.model.CandlepinModeChange;
import org.candlepin.model.CandlepinModeChange.Mode;
import org.candlepin.model.CandlepinModeChange.Reason;
import org.candlepin.model.CandlepinQuery;
import org.candlepin.model.Rules;
import org.candlepin.model.RulesCurator;
import org.candlepin.policy.js.JsRunnerProvider;

import org.junit.Before;
import org.junit.Test;
import org.keycloak.representations.adapters.config.AdapterConfig;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.core.Appender;

import java.io.File;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Date;


/**
 * StatusResourceTest
 */
public class StatusResourceTest {

    @Mock private RulesCurator rulesCurator;
    @Mock private Configuration config;
    @Mock private JsRunnerProvider jsProvider;
    @Mock private CandlepinCache candlepinCache;
    @Mock private StatusCache mockedStatusCache;
    @Mock private ModeManager modeManager;
    @Mock private KeycloakConfiguration keycloakConfig;
    @Mock private AdapterConfig mockKeycloakAdapterConfig;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        CandlepinModeChange mockModeChange =
            new CandlepinModeChange(new Date(), Mode.NORMAL, Reason.STARTUP);
        CandlepinQuery mockCPQuery = mock(CandlepinQuery.class);
        when(mockCPQuery.list()).thenReturn(new ArrayList<Rules>());

        when(rulesCurator.listAll()).thenReturn(mockCPQuery);
        when(rulesCurator.getRules()).thenReturn(new Rules("// Version: 2.0\nBLAH"));
        when(mockedStatusCache.getStatus()).thenReturn(null);
        when(candlepinCache.getStatusCache()).thenReturn(mockedStatusCache);
        when(modeManager.getLastCandlepinModeChange()).thenReturn(mockModeChange);
        when(keycloakConfig.getAdapterConfig()).thenReturn(mockKeycloakAdapterConfig);
        when(mockKeycloakAdapterConfig.getRealm()).thenReturn("realm");
        when(mockKeycloakAdapterConfig.getAuthServerUrl()).thenReturn("https://example.com/auth");
        when(mockKeycloakAdapterConfig.getResource()).thenReturn("resource");
    }

    @Test
    public void status() throws Exception {
        PrintStream ps = new PrintStream(new File(this.getClass()
            .getClassLoader().getResource("version.properties").toURI()));
        ps.println("version=${version}");
        ps.println("release=${release}");
        StatusResource sr = new StatusResource(rulesCurator, config, jsProvider, candlepinCache,
            modeManager, keycloakConfig);
        StatusDTO s = sr.status();
        ps.close();
        assertNotNull(s);
        assertEquals("${release}", s.getRelease());
        assertEquals("${version}", s.getVersion());
        assertTrue(s.getResult());
    }

    @Test
    public void unknown() throws Exception {
        PrintStream ps = new PrintStream(new File(this.getClass()
            .getClassLoader().getResource("version.properties").toURI()));
        ps.println("foo");
        StatusResource sr = new StatusResource(rulesCurator, config, jsProvider, candlepinCache,
            modeManager, keycloakConfig);
        StatusDTO s = sr.status();
        ps.close();
        assertNotNull(s);
        assertEquals("Unknown", s.getRelease());
        assertEquals("Unknown", s.getVersion());
        assertTrue(s.getResult());
    }

    @Test
    public void testDBDown() throws Exception {
        PrintStream ps = new PrintStream(new File(this.getClass()
            .getClassLoader().getResource("version.properties").toURI()));
        ps.println("version=${version}");
        ps.println("release=${release}");
        when(rulesCurator.getUpdatedFromDB()).thenThrow(new RuntimeException());
        StatusResource sr = new StatusResource(rulesCurator, config, jsProvider, candlepinCache,
            modeManager, keycloakConfig);
        StatusDTO s = sr.status();
        ps.close();
        assertNotNull(s);
        assertEquals("${release}", s.getRelease());
        assertEquals("${version}", s.getVersion());
        assertFalse(s.getResult());
    }

    @Test
    public void simulateVersionFilter() throws Exception {
        // setup logger to see if we actually log anything
        LoggerContext lc = (LoggerContext) LoggerFactory.getILoggerFactory();
        Logger srLogger = lc.getLogger(StatusResource.class);
        Appender mockapp = mock(Appender.class);
        srLogger.addAppender(mockapp);
        srLogger.setLevel(Level.DEBUG);
        ArgumentCaptor<LoggingEvent> message = ArgumentCaptor.forClass(LoggingEvent.class);

        PrintStream ps = new PrintStream(new File(this.getClass()
            .getClassLoader().getResource("version.properties").toURI()));
        ps.println("version=${version}");
        ps.println("release=${release}");
        StatusResource sr = new StatusResource(rulesCurator, config, jsProvider, candlepinCache,
            modeManager, keycloakConfig);
        StatusDTO s = sr.status();
        ps.close();

        // make sure we did not log anything which indicates
        // an exception
        verify(mockapp, never()).doAppend(message.capture());
        assertEquals("${release}", s.getRelease());
        assertEquals("${version}", s.getVersion());
        assertTrue(s.getResult());
        assertFalse(s.getStandalone());
    }

    @Test
    public void keycloakParamsPresentWhenKeycloakActive() {
        when(config.getBoolean(eq(ConfigProperties.KEYCLOAK_AUTHENTICATION))).thenReturn(true);

        StatusResource sr = new StatusResource(rulesCurator, config, jsProvider, candlepinCache,
            modeManager, keycloakConfig);

        StatusDTO s = sr.status();
        assertTrue("not a keycloak-enabled status", s instanceof KeycloakStatusDTO);
        KeycloakStatusDTO keycloakStatus = (KeycloakStatusDTO) s;
        assertEquals("realm", keycloakStatus.getKeycloakRealm());
        assertEquals("https://example.com/auth", keycloakStatus.getKeycloakAuthUrl());
        assertEquals("resource", keycloakStatus.getKeycloakResource());
    }

    @Test
    public void keycloakParamsMissingWhenKeycloakInactive() {
        when(config.getBoolean(eq(ConfigProperties.KEYCLOAK_AUTHENTICATION))).thenReturn(false);

        StatusResource sr = new StatusResource(rulesCurator, config, jsProvider, candlepinCache,
            modeManager, keycloakConfig);

        StatusDTO s = sr.status();
        assertFalse("is a keycloak-enabled status", s instanceof KeycloakStatusDTO);
    }
}
