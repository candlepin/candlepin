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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.candlepin.auth.KeycloakConfiguration;
import org.candlepin.cache.CandlepinCache;
import org.candlepin.cache.StatusCache;
import org.candlepin.common.config.Configuration;
import org.candlepin.config.ConfigProperties;
import org.candlepin.controller.mode.CandlepinModeManager;
import org.candlepin.controller.mode.CandlepinModeManager.Mode;
import org.candlepin.dto.api.v1.StatusDTO;
import org.candlepin.model.CandlepinQuery;
import org.candlepin.model.Rules;
import org.candlepin.model.RulesCurator;
import org.candlepin.policy.js.JsRunnerProvider;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.core.Appender;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.keycloak.representations.adapters.config.AdapterConfig;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.PrintStream;
import java.util.ArrayList;


/**
 * Test suite for the StatusResource class
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class StatusResourceTest {

    @Mock private RulesCurator rulesCurator;
    @Mock private Configuration config;
    @Mock private JsRunnerProvider jsProvider;
    @Mock private CandlepinCache candlepinCache;
    @Mock private StatusCache mockedStatusCache;
    @Mock private CandlepinModeManager modeManager;
    @Mock private KeycloakConfiguration keycloakConfig;
    @Mock private AdapterConfig mockKeycloakAdapterConfig;

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        CandlepinQuery mockCPQuery = mock(CandlepinQuery.class);
        when(mockCPQuery.list()).thenReturn(new ArrayList<Rules>());

        when(rulesCurator.listAll()).thenReturn(mockCPQuery);
        when(rulesCurator.getRules()).thenReturn(new Rules("// Version: 2.0\nBLAH"));

        when(mockedStatusCache.getStatus()).thenReturn(null);
        when(candlepinCache.getStatusCache()).thenReturn(mockedStatusCache);

        when(modeManager.getCurrentMode()).thenReturn(Mode.NORMAL);

        when(keycloakConfig.getAdapterConfig()).thenReturn(mockKeycloakAdapterConfig);
        when(mockKeycloakAdapterConfig.getRealm()).thenReturn("realm");
        when(mockKeycloakAdapterConfig.getAuthServerUrl()).thenReturn("https://example.com/auth");
        when(mockKeycloakAdapterConfig.getResource()).thenReturn("resource");
    }

    private StatusResource createResource() {
        return new StatusResource(this.rulesCurator, this.config, this.jsProvider, this.candlepinCache,
            this.modeManager, this.keycloakConfig);
    }

    @Test
    public void status() throws Exception {
        PrintStream ps = new PrintStream(new File(this.getClass()
            .getClassLoader().getResource("version.properties").toURI()));
        ps.println("version=${version}");
        ps.println("release=${release}");
        StatusResource sr = this.createResource();
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
        StatusResource sr = this.createResource();
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
        StatusResource sr = this.createResource();
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
        StatusResource sr = this.createResource();
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

        StatusResource sr = this.createResource();
        StatusDTO statusDTO = sr.status();
        assertEquals("realm", statusDTO.getKeycloakRealm());
        assertEquals("https://example.com/auth", statusDTO.getKeycloakAuthUrl());
        assertEquals("resource", statusDTO.getKeycloakResource());
    }

    @Test
    public void keycloakParamsMissingWhenKeycloakInactive() {
        when(config.getBoolean(eq(ConfigProperties.KEYCLOAK_AUTHENTICATION))).thenReturn(false);

        StatusResource sr = this.createResource();

        StatusDTO statusDTO = sr.status();
        assertNull(statusDTO.getKeycloakRealm(), "keycloak realm is not null");
        assertNull(statusDTO.getKeycloakAuthUrl(), "keycloak auth URL is not null");
        assertNull(statusDTO.getKeycloakResource(), "keycloak resource is not null");
    }
}
