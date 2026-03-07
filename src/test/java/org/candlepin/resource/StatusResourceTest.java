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
package org.candlepin.resource;

import static org.assertj.core.api.Assertions.assertThat;
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
import org.candlepin.config.ConfigProperties;
import org.candlepin.config.Configuration;
import org.candlepin.controller.mode.CandlepinModeManager;
import org.candlepin.controller.mode.CandlepinModeManager.Mode;
import org.candlepin.dto.ModelTranslator;
import org.candlepin.dto.api.server.v1.CryptographyDTO;
import org.candlepin.dto.api.server.v1.SchemeDTO;
import org.candlepin.dto.api.server.v1.StatusDTO;
import org.candlepin.dto.api.v1.SchemeTranslator;
import org.candlepin.model.Rules;
import org.candlepin.model.RulesCurator;
import org.candlepin.pki.CryptoManager;
import org.candlepin.pki.Scheme;
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
import java.security.cert.X509Certificate;
import java.util.LinkedList;
import java.util.List;
import java.util.function.Function;


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
    @Mock private CryptoManager mockCryptoManager;
    @Mock private ModelTranslator mockModelTranslator;

    private Scheme defaultScheme;

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        when(rulesCurator.listAll()).thenReturn(new LinkedList<>());
        when(rulesCurator.getRules()).thenReturn(new Rules("// Version: 2.0\nBLAH"));

        when(mockedStatusCache.getStatus()).thenReturn(null);
        when(candlepinCache.getStatusCache()).thenReturn(mockedStatusCache);

        when(modeManager.getCurrentMode()).thenReturn(Mode.NORMAL);

        when(keycloakConfig.getAdapterConfig()).thenReturn(mockKeycloakAdapterConfig);
        when(mockKeycloakAdapterConfig.getRealm()).thenReturn("realm");
        when(mockKeycloakAdapterConfig.getAuthServerUrl()).thenReturn("https://example.com/auth");
        when(mockKeycloakAdapterConfig.getResource()).thenReturn("resource");

        this.defaultScheme = new Scheme.Builder()
            .setName("legacy")
            .setCertificate(mock(X509Certificate.class))
            .setSignatureAlgorithm("SHA256withRSA")
            .setKeyAlgorithm("RSA")
            .setKeySize(4096)
            .build();
        when(mockCryptoManager.getCryptoSchemes()).thenReturn(List.of(this.defaultScheme));
        when(mockCryptoManager.getDefaultCryptoScheme()).thenReturn(this.defaultScheme);

        SchemeTranslator schemeTranslator = new SchemeTranslator();
        when(mockModelTranslator.getStreamMapper(Scheme.class, SchemeDTO.class))
            .thenAnswer(invocation -> (Function<Scheme, SchemeDTO>) schemeTranslator::translate);
    }

    private StatusResource createResource() {
        return new StatusResource(this.rulesCurator, this.config, this.jsProvider, this.candlepinCache,
            this.modeManager, this.keycloakConfig, this.mockCryptoManager, mockModelTranslator);
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

        AdapterConfig config = this.mockKeycloakAdapterConfig;

        StatusResource sr = this.createResource();
        StatusDTO statusDTO = sr.status();
        assertEquals(config.getRealm(), statusDTO.getKeycloakRealm());
        assertEquals(config.getAuthServerUrl(), statusDTO.getKeycloakAuthUrl());
        assertEquals(config.getResource(), statusDTO.getKeycloakResource());

        // Also verify that keycloak's presence populates the generic device auth fields
        assertEquals(config.getRealm(), statusDTO.getDeviceAuthRealm());
        assertEquals(config.getAuthServerUrl(), statusDTO.getDeviceAuthUrl());
        assertEquals(config.getResource(), statusDTO.getDeviceAuthClientId());
        assertEquals("", statusDTO.getDeviceAuthScope());
    }

    @Test
    public void keycloakParamsMissingWhenKeycloakInactive() {
        when(config.getBoolean(eq(ConfigProperties.KEYCLOAK_AUTHENTICATION))).thenReturn(false);

        StatusResource sr = this.createResource();

        StatusDTO statusDTO = sr.status();
        assertNull(statusDTO.getKeycloakRealm(), "keycloak realm is not null");
        assertNull(statusDTO.getKeycloakAuthUrl(), "keycloak auth URL is not null");
        assertNull(statusDTO.getKeycloakResource(), "keycloak resource is not null");

        assertNull(statusDTO.getDeviceAuthRealm());
        assertNull(statusDTO.getDeviceAuthUrl());
        assertNull(statusDTO.getDeviceAuthClientId());
        assertNull(statusDTO.getDeviceAuthScope());
    }

    @Test
    public void testCryptographyFieldAlwaysPresent() {
        StatusResource sr = this.createResource();
        StatusDTO statusDTO = sr.status();

        CryptographyDTO cryptography = statusDTO.getCryptography();
        assertThat(cryptography)
            .isNotNull()
            .returns(this.defaultScheme.name(), CryptographyDTO::getDefaultScheme);

        assertThat(cryptography.getSchemes())
            .isNotNull()
            .hasSize(1);
    }

    @Test
    public void testCryptographyFieldWithMultipleSchemes() {
        Scheme rsaScheme = new Scheme.Builder()
            .setName("rsa")
            .setCertificate(mock(X509Certificate.class))
            .setSignatureAlgorithm("SHA256withRSA")
            .setKeyAlgorithm("RSA")
            .setKeySize(4096)
            .build();
        Scheme mldsaScheme = new Scheme.Builder()
            .setName("mldsa")
            .setCertificate(mock(X509Certificate.class))
            .setSignatureAlgorithm("ML-DSA-87")
            .setKeyAlgorithm("ML-DSA")
            .build();

        when(mockCryptoManager.getCryptoSchemes()).thenReturn(List.of(mldsaScheme, rsaScheme));
        when(mockCryptoManager.getDefaultCryptoScheme()).thenReturn(rsaScheme);

        StatusResource sr = this.createResource();
        StatusDTO statusDTO = sr.status();

        CryptographyDTO cryptography = statusDTO.getCryptography();
        assertThat(cryptography)
            .isNotNull()
            .returns(rsaScheme.name(), CryptographyDTO::getDefaultScheme);

        assertThat(cryptography.getSchemes())
            .isNotNull()
            .extracting(SchemeDTO::getName)
            .containsExactly(mldsaScheme.name(), rsaScheme.name());
    }

    @Test
    public void testCryptographySchemeWithoutKeySize() {
        Scheme mldsaScheme = new Scheme.Builder()
            .setName("mldsa")
            .setCertificate(mock(X509Certificate.class))
            .setSignatureAlgorithm("ML-DSA-87")
            .setKeyAlgorithm("ML-DSA")
            .build();

        when(mockCryptoManager.getCryptoSchemes()).thenReturn(List.of(mldsaScheme));
        when(mockCryptoManager.getDefaultCryptoScheme()).thenReturn(mldsaScheme);

        StatusResource sr = this.createResource();
        StatusDTO statusDTO = sr.status();

        CryptographyDTO cryptography = statusDTO.getCryptography();
        assertThat(cryptography)
            .isNotNull();

        assertThat(cryptography.getSchemes())
            .singleElement()
            .returns(mldsaScheme.name(), SchemeDTO::getName)
            .returns(mldsaScheme.signatureAlgorithm(), SchemeDTO::getSignatureAlgorithm)
            .returns(mldsaScheme.keyAlgorithm(), SchemeDTO::getKeyAlgorithm)
            .returns(null, SchemeDTO::getKeySize);
    }
}
