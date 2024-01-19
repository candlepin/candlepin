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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.candlepin.config.ConfigProperties;
import org.candlepin.config.DevConfig;
import org.candlepin.config.TestConfig;
import org.candlepin.model.Owner;
import org.candlepin.model.OwnerCurator;
import org.candlepin.pki.CertificateReader;
import org.candlepin.pki.impl.BouncyCastlePrivateKeyReader;
import org.candlepin.service.CloudRegistrationAdapter;
import org.candlepin.service.model.CloudRegistrationInfo;
import org.candlepin.util.Util;

import org.jboss.resteasy.mock.MockHttpRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.keycloak.common.util.KeyUtils;
import org.keycloak.crypto.Algorithm;
import org.keycloak.crypto.AsymmetricSignatureSignerContext;
import org.keycloak.crypto.KeyType;
import org.keycloak.crypto.KeyUse;
import org.keycloak.crypto.KeyWrapper;
import org.keycloak.jose.jws.JWSBuilder;
import org.keycloak.representations.JsonWebToken;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.cert.X509Certificate;
import java.util.HashMap;
import java.util.Map;



@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class CloudRegistrationAuthTest {
    private static final String CLAIMANT_KEY = "claimant_key";
    private DevConfig config;
    private CertificateReader certificateReader;
    private CloudRegistrationAdapter mockCloudRegistrationAdapter;
    private OwnerCurator mockOwnerCurator;

    @BeforeEach
    public void init() {
        this.config = TestConfig.defaults();
        this.certificateReader = this.setupCertificateReader();

        this.mockCloudRegistrationAdapter = mock(CloudRegistrationAdapter.class);
        this.mockOwnerCurator = mock(OwnerCurator.class);

        Map<String, Owner> ownerCache = new HashMap<>();

        doAnswer(iom -> {
            String ownerKey = (String) iom.getArguments()[0];
            return ownerCache.computeIfAbsent(ownerKey, key -> new Owner()
                .setKey(key)
                .setDisplayName(key)
                .setClaimed(true)
                .setClaimantOwner(CLAIMANT_KEY)
                .setId(Util.generateUUID()));
        }).when(this.mockOwnerCurator).getByKey(anyString());

        doAnswer(iom -> {
            CloudRegistrationInfo cinfo = (CloudRegistrationInfo) iom.getArguments()[0];
            return cinfo != null ? cinfo.getType() : null;
        }).when(this.mockCloudRegistrationAdapter)
            .resolveCloudRegistrationData(any(CloudRegistrationInfo.class));

        // Default the cloud auth feature to true for most tests
        this.config.setProperty(ConfigProperties.CLOUD_AUTHENTICATION, "true");
    }

    private CertificateReader setupCertificateReader() {
        try {
            ClassLoader loader = getClass().getClassLoader();
            String caCert = loader.getResource("test-ca.crt").toURI().getPath();
            String caKey = loader.getResource("test-ca.key").toURI().getPath();

            this.config.setProperty(ConfigProperties.CA_CERT, caCert);
            this.config.setProperty(ConfigProperties.CA_KEY, caKey);
            this.config.setProperty(ConfigProperties.CA_KEY_PASSWORD, "password");

            return new CertificateReader(config, new BouncyCastlePrivateKeyReader());
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private CloudRegistrationAuth buildAuthProvider() {
        return new CloudRegistrationAuth(this.config, this.mockOwnerCurator, this.certificateReader);
    }

    private MockHttpRequest buildHttpRequest() {
        try {
            return MockHttpRequest.create("GET", "http://localhost/candlepin/status");
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private int getCurrentSeconds() {
        return (int) (System.currentTimeMillis() / 1000);
    }

    private String buildMalformedToken(JsonWebToken token) {
        X509Certificate certificate;
        PublicKey publicKey;
        PrivateKey privateKey;

        // Fetch our keys
        try {
            certificate = this.certificateReader.getCACert();
            publicKey = certificate.getPublicKey();
            privateKey = this.certificateReader.getCaKey();
        }
        catch (Exception e) {
            throw new RuntimeException("Unable to load public and private keys", e);
        }

        String keyId = KeyUtils.createKeyId(publicKey);

        KeyWrapper wrapper = new KeyWrapper();
        wrapper.setAlgorithm(Algorithm.RS512);
        wrapper.setCertificate(certificate);
        wrapper.setKid(keyId);
        wrapper.setPrivateKey(privateKey);
        wrapper.setPublicKey(publicKey);
        wrapper.setUse(KeyUse.SIG);
        wrapper.setType(KeyType.RSA);

        token.id(Util.generateUUID())
            .issuer("candlepin-test");

        return new JWSBuilder()
            .kid(keyId)
            .type("JWT")
            .jsonContent(token)
            .sign(new AsymmetricSignatureSignerContext(wrapper));
    }

    @Test
    public void testGetPrincipalWithCloudAuthDisabled() {
        this.config.setProperty(ConfigProperties.CLOUD_AUTHENTICATION, "false");

        Principal principal = this.buildAuthProvider()
            .getPrincipal(this.buildHttpRequest());

        assertNull(principal);
    }

    @Test
    public void testGetPrincipalAbortsIfHeaderIsAbsent() {
        MockHttpRequest request = this.buildHttpRequest();

        Principal principal = this.buildAuthProvider()
            .getPrincipal(request);

        assertNull(principal);
    }

    /**
     * This test is to verify that the token generation method provided by this test suite will
     * generate a valid token if properly populated. This test must pass for the malformed token
     * tests to be considered valid.
     */
    @Test
    public void testGetPrincipalAcceptsTestToken() {
        String ownerKey = "test_org";
        int ctSeconds = this.getCurrentSeconds() - 5;

        String token = this.buildMalformedToken(new JsonWebToken()
            .type(CloudAuthTokenType.STANDARD.toString())
            .subject("test_subject")
            .audience(ownerKey)
            .issuedAt(ctSeconds)
            .notBefore(ctSeconds)
            .expiration(ctSeconds + 300));

        MockHttpRequest request = this.buildHttpRequest();
        request.header("Authorization", "Bearer " + token);

        Principal principal = this.buildAuthProvider()
            .getPrincipal(request);

        assertNotNull(principal);
        assertTrue(principal instanceof CloudConsumerPrincipal);
        assertEquals(AuthenticationMethod.CLOUD, principal.getAuthenticationMethod());

        // Test note:
        // The owner key resolves to the cloud registration type due to the mock adapter implementation
        // we've defined in our test init, which *always* uses the type as the owner key. In the real
        // implementation, this isn't guaranteed.
        Owner owner = this.mockOwnerCurator.getByKey(ownerKey);
        assertFalse(principal.canAccess(owner, SubResource.CONSUMERS, Access.CREATE));
    }

    @Test
    public void testGetPrincipalWithClaimantPermissions() {
        String ownerKey = "test_org";
        int ctSeconds = this.getCurrentSeconds() - 5;

        String token = this.buildMalformedToken(new JsonWebToken()
            .type(CloudAuthTokenType.STANDARD.toString())
            .subject("test_subject")
            .audience(ownerKey)
            .issuedAt(ctSeconds)
            .notBefore(ctSeconds)
            .expiration(ctSeconds + 300));

        MockHttpRequest request = this.buildHttpRequest();
        request.header("Authorization", "Bearer " + token);

        Principal principal = this.buildAuthProvider()
            .getPrincipal(request);

        assertNotNull(principal);
        assertTrue(principal instanceof CloudConsumerPrincipal);
        assertEquals(AuthenticationMethod.CLOUD, principal.getAuthenticationMethod());

        Owner claimant = this.mockOwnerCurator.getByKey(CLAIMANT_KEY);
        assertFalse(principal.canAccess(claimant, SubResource.CONSUMERS, Access.CREATE));
    }

    @Test
    public void testGetPrincipalRequiresOwnerToExist() {
        String ownerKey = "test_org";
        when(this.mockOwnerCurator.getByKey(anyString())).thenReturn(null);
        int ctSeconds = this.getCurrentSeconds() - 5;

        String token = this.buildMalformedToken(new JsonWebToken()
            .type(CloudAuthTokenType.STANDARD.toString())
            .subject("test_subject")
            .audience(ownerKey)
            .issuedAt(ctSeconds)
            .notBefore(ctSeconds)
            .expiration(ctSeconds + 300));

        MockHttpRequest request = this.buildHttpRequest();
        request.header("Authorization", "Bearer " + token);

        Principal principal = this.buildAuthProvider()
            .getPrincipal(request);

        assertNull(principal);
    }

    @Test
    public void testGetPrincipalIgnoresTokensWithWrongType() {
        int ctSeconds = this.getCurrentSeconds() - 5;

        String token = this.buildMalformedToken(new JsonWebToken()
            .type("invalid_token_type")
            .subject("test_subject")
            .audience("test_org")
            .issuedAt(ctSeconds)
            .notBefore(ctSeconds)
            .expiration(ctSeconds + 300));

        MockHttpRequest request = this.buildHttpRequest();
        request.header("Authorization", "Bearer " + token);

        Principal principal = this.buildAuthProvider()
            .getPrincipal(request);

        assertNull(principal);
    }

    @Test
    public void testGetPrincipalIgnoresTokensLackingType() {
        int ctSeconds = this.getCurrentSeconds() - 5;

        String token = this.buildMalformedToken(new JsonWebToken()
            .subject("test_subject")
            .audience("test_org")
            .issuedAt(ctSeconds)
            .notBefore(ctSeconds)
            .expiration(ctSeconds + 300));

        MockHttpRequest request = this.buildHttpRequest();
        request.header("Authorization", "Bearer " + token);

        Principal principal = this.buildAuthProvider()
            .getPrincipal(request);

        assertNull(principal);
    }

    @Test
    public void testGetPrincipalIgnoresTokensLackingSubject() {
        int ctSeconds = this.getCurrentSeconds() - 5;

        String token = this.buildMalformedToken(new JsonWebToken()
            .type(CloudAuthTokenType.STANDARD.toString())
            .audience("test_org")
            .issuedAt(ctSeconds)
            .notBefore(ctSeconds)
            .expiration(ctSeconds + 300));

        MockHttpRequest request = this.buildHttpRequest();
        request.header("Authorization", "Bearer " + token);

        Principal principal = this.buildAuthProvider()
            .getPrincipal(request);

        assertNull(principal);
    }

    @Test
    public void testGetPrincipalIgnoresTokensLackingAudience() {
        int ctSeconds = this.getCurrentSeconds() - 5;

        String token = this.buildMalformedToken(new JsonWebToken()
            .type(CloudAuthTokenType.STANDARD.toString())
            .subject("test_subject")
            .issuedAt(ctSeconds)
            .notBefore(ctSeconds)
            .expiration(ctSeconds + 300));

        MockHttpRequest request = this.buildHttpRequest();
        request.header("Authorization", "Bearer " + token);

        Principal principal = this.buildAuthProvider()
            .getPrincipal(request);

        assertNull(principal);
    }

    @Test
    public void testGetPrincipalIgnoresExpiredTokens() {
        int ctSeconds = this.getCurrentSeconds();

        String token = this.buildMalformedToken(new JsonWebToken()
            .type(CloudAuthTokenType.STANDARD.toString())
            .subject("test_subject")
            .audience("test_org")
            .issuedAt(ctSeconds - 15)
            .notBefore(ctSeconds - 15)
            .expiration(ctSeconds - 10));

        MockHttpRequest request = this.buildHttpRequest();
        request.header("Authorization", "Bearer " + token);

        Principal principal = this.buildAuthProvider()
            .getPrincipal(request);

        assertNull(principal);
    }

    @Test
    public void testGetPrincipalIgnoresInactiveTokens() {
        int ctSeconds = this.getCurrentSeconds();

        String token = this.buildMalformedToken(new JsonWebToken()
            .type(CloudAuthTokenType.STANDARD.toString())
            .subject("test_subject")
            .audience("test_org")
            .issuedAt(ctSeconds + 15)
            .notBefore(ctSeconds + 15)
            .expiration(ctSeconds + 25));

        MockHttpRequest request = this.buildHttpRequest();
        request.header("Authorization", "Bearer " + token);

        Principal principal = this.buildAuthProvider()
            .getPrincipal(request);

        assertNull(principal);
    }
}
