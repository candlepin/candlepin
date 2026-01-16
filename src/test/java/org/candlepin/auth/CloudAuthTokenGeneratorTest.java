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
package org.candlepin.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.candlepin.config.ConfigProperties;
import org.candlepin.config.DevConfig;
import org.candlepin.config.TestConfig;
import org.candlepin.pki.CertificateReader;
import org.candlepin.pki.impl.BouncyCastlePrivateKeyReader;
import org.candlepin.util.Util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import tools.jackson.databind.ObjectMapper;

import java.util.Base64;
import java.util.HashMap;
import java.util.Map;


public class CloudAuthTokenGeneratorTest {
    private static final String TOKEN_SUBJECT_DEFAULT = "cloud_auth";

    private DevConfig config;
    private CertificateReader certificateReader;

    private CloudAuthTokenGenerator tokenGenerator;

    @BeforeEach
    public void beforeEach() {
        this.config = TestConfig.defaults();

        try {
            ClassLoader loader = getClass().getClassLoader();
            String caCert = loader.getResource("test-ca.crt").toURI().getPath();
            String caKey = loader.getResource("test-ca.key").toURI().getPath();

            this.config.setProperty(ConfigProperties.CA_CERT, caCert);
            this.config.setProperty(ConfigProperties.CA_KEY, caKey);
            this.config.setProperty(ConfigProperties.CA_KEY_PASSWORD, "password");

            certificateReader = new CertificateReader(config, new BouncyCastlePrivateKeyReader());
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }

        tokenGenerator = new CloudAuthTokenGenerator(config, certificateReader);
    }

    @Test
    public void testBuildStandardRegistrationTokenWithNullPrincipal() {
        assertThrows(IllegalArgumentException.class,
            () -> tokenGenerator.buildStandardRegistrationToken(null, "owner-key"));
    }

    @ParameterizedTest(name = "{displayName} {index}: {0}")
    @NullAndEmptySource
    @ValueSource(strings = { "  " })
    public void testBuildStandardRegistrationTokenWithInvalidOwnerKey(String ownerKey) {
        Principal principal = new UserPrincipal("test_user", null, false);

        assertThrows(IllegalArgumentException.class,
            () -> tokenGenerator.buildStandardRegistrationToken(principal, ownerKey));
    }

    @Test
    public void testBuildStandardRegistrationToken() {
        String expectedOwnerKey = "owner-key";
        String username = "test-user";
        Principal principal = new UserPrincipal(username, null, false);

        String token = tokenGenerator.buildStandardRegistrationToken(principal, expectedOwnerKey);

        assertToken(CloudAuthTokenType.STANDARD.toString(), expectedOwnerKey, username, token);
    }

    @Test
    public void testBuildStandardRegistrationTokenWithNullPrincipalUsername() {
        String expectedOwnerKey = "owner-key";
        Principal principal = new UserPrincipal(null, null, false);

        String token = tokenGenerator.buildStandardRegistrationToken(principal, expectedOwnerKey);

        assertToken(CloudAuthTokenType.STANDARD.toString(), expectedOwnerKey, TOKEN_SUBJECT_DEFAULT, token);
    }

    @Test
    public void testBuildAnonymousRegistrationTokenWithNullPrincipal() {
        assertThrows(IllegalArgumentException.class,
            () -> tokenGenerator.buildAnonymousRegistrationToken(null, "owner-key"));
    }

    @ParameterizedTest(name = "{displayName} {index}: {0}")
    @NullAndEmptySource
    @ValueSource(strings = { "  " })
    public void testBuildAnonymousRegistrationTokenWithInvalidOwnerKey(String ownerKey) {
        Principal principal = new UserPrincipal("test-user", null, false);

        assertThrows(IllegalArgumentException.class,
            () -> tokenGenerator.buildAnonymousRegistrationToken(principal, ownerKey));
    }

    @Test
    public void testBuildAnonymousRegistrationToken() {
        String consumerUuid = Util.generateUUID();
        String username = "test-user";
        Principal principal = new UserPrincipal(username, null, false);

        String token = tokenGenerator.buildAnonymousRegistrationToken(principal, consumerUuid);

        assertToken(CloudAuthTokenType.ANONYMOUS.toString(), consumerUuid, username, token);
    }

    @Test
    public void testBuildAnonymousRegistrationTokenWithNullPrincipalUsername() {
        String expectedOwnerKey = "owner-key";
        Principal principal = new UserPrincipal(null, null, false);

        String token = tokenGenerator.buildAnonymousRegistrationToken(principal, expectedOwnerKey);

        assertToken(CloudAuthTokenType.ANONYMOUS.toString(), expectedOwnerKey, TOKEN_SUBJECT_DEFAULT, token);
    }

    private void assertToken(String expectedType, String expectedAudience, String subject, String token) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("token type is null or blank");
        }

        String[] chunks = token.split("\\.");
        if (chunks.length < 2) {
            throw new RuntimeException("unable to read token body");
        }

        Base64.Decoder decoder = Base64.getUrlDecoder();
        String body = new String(decoder.decode(chunks[1]));
        ObjectMapper mapper = new ObjectMapper();
        Map<String, String> bodyMap = mapper.readValue(body, HashMap.class);

        assertEquals(expectedType, bodyMap.get("typ"));
        assertEquals(expectedAudience, bodyMap.get("aud"));
        assertEquals(subject, bodyMap.get("sub"));
    }

}
