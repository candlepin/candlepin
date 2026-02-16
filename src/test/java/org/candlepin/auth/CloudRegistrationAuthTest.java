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
import org.candlepin.pki.Scheme;
import org.candlepin.pki.SchemeReader;
import org.candlepin.service.CloudRegistrationAdapter;
import org.candlepin.service.model.CloudRegistrationInfo;
import org.candlepin.test.CryptoUtil;
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

import java.util.HashMap;
import java.util.Map;



@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class CloudRegistrationAuthTest {
    private static final String CLAIMANT_KEY = "claimant_key";
    private DevConfig config;
    private SchemeReader schemeReader;
    private CloudRegistrationAdapter mockCloudRegistrationAdapter;
    private OwnerCurator mockOwnerCurator;

    @BeforeEach
    public void init() {
        this.config = TestConfig.defaults();
        this.schemeReader = CryptoUtil.getSchemeReader(this.config);

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

    private CloudRegistrationAuth buildAuthProvider() {
        CloudAuthTokenGenerator generator = new CloudAuthTokenGenerator(this.config, this.schemeReader);
        return new CloudRegistrationAuth(this.config, this.mockOwnerCurator, generator);
    }

    private MockHttpRequest buildHttpRequest() {
        try {
            return MockHttpRequest.create("GET", "http://localhost/candlepin/status");
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private String buildToken(JsonWebToken token) {
        // Get the scheme we believe the auth layer will be using. This needs to align with the internal
        // logic of the auth impl under test.
        Scheme scheme = this.schemeReader.readScheme("jwt-scheme",
            ConfigProperties.JWT_CRYPTO_SCHEME_CERT,
            ConfigProperties.JWT_CRYPTO_SCHEME_KEY,
            ConfigProperties.JWT_CRYPTO_SCHEME_KEY_PASSWORD,
            ConfigProperties.JWT_CRYPTO_SCHEME_SIGNATURE_ALGORITHM,
            ConfigProperties.JWT_CRYPTO_SCHEME_KEY_ALGORITHM,
            ConfigProperties.JWT_CRYPTO_SCHEME_KEY_SIZE);

        String keyId = KeyUtils.createKeyId(scheme.certificate().getPublicKey());

        KeyWrapper wrapper = new KeyWrapper();
        wrapper.setAlgorithm(Algorithm.RS512);
        wrapper.setCertificate(scheme.certificate());
        wrapper.setKid(keyId);
        wrapper.setPrivateKey(scheme.privateKey().get());
        wrapper.setPublicKey(scheme.certificate().getPublicKey());
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
        long ctSeconds = System.currentTimeMillis() / 1000;

        String token = this.buildToken(new JsonWebToken()
            .type(CloudAuthTokenType.STANDARD.toString())
            .subject("test_subject")
            .audience(ownerKey)
            .iat(ctSeconds)
            .nbf(ctSeconds)
            .exp(ctSeconds + 300));

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
        long ctSeconds = System.currentTimeMillis() / 1000;

        String token = this.buildToken(new JsonWebToken()
            .type(CloudAuthTokenType.STANDARD.toString())
            .subject("test_subject")
            .audience(ownerKey)
            .iat(ctSeconds)
            .nbf(ctSeconds)
            .exp(ctSeconds + 300));

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
        long ctSeconds = System.currentTimeMillis() / 1000;

        String token = this.buildToken(new JsonWebToken()
            .type(CloudAuthTokenType.STANDARD.toString())
            .subject("test_subject")
            .audience(ownerKey)
            .iat(ctSeconds)
            .nbf(ctSeconds)
            .exp(ctSeconds + 300));

        MockHttpRequest request = this.buildHttpRequest();
        request.header("Authorization", "Bearer " + token);

        Principal principal = this.buildAuthProvider()
            .getPrincipal(request);

        assertNull(principal);
    }

    @Test
    public void testGetPrincipalIgnoresTokensWithWrongType() {
        long ctSeconds = System.currentTimeMillis() / 1000;

        String token = this.buildToken(new JsonWebToken()
            .type("invalid_token_type")
            .subject("test_subject")
            .audience("test_org")
            .iat(ctSeconds)
            .nbf(ctSeconds)
            .exp(ctSeconds + 300));

        MockHttpRequest request = this.buildHttpRequest();
        request.header("Authorization", "Bearer " + token);

        Principal principal = this.buildAuthProvider()
            .getPrincipal(request);

        assertNull(principal);
    }

    @Test
    public void testGetPrincipalIgnoresTokensLackingType() {
        long ctSeconds = System.currentTimeMillis() / 1000;

        String token = this.buildToken(new JsonWebToken()
            .subject("test_subject")
            .audience("test_org")
            .iat(ctSeconds)
            .nbf(ctSeconds)
            .exp(ctSeconds + 300));

        MockHttpRequest request = this.buildHttpRequest();
        request.header("Authorization", "Bearer " + token);

        Principal principal = this.buildAuthProvider()
            .getPrincipal(request);

        assertNull(principal);
    }

    @Test
    public void testGetPrincipalIgnoresTokensLackingSubject() {
        long ctSeconds = System.currentTimeMillis() / 1000;

        String token = this.buildToken(new JsonWebToken()
            .type(CloudAuthTokenType.STANDARD.toString())
            .audience("test_org")
            .iat(ctSeconds)
            .nbf(ctSeconds)
            .exp(ctSeconds + 300));

        MockHttpRequest request = this.buildHttpRequest();
        request.header("Authorization", "Bearer " + token);

        Principal principal = this.buildAuthProvider()
            .getPrincipal(request);

        assertNull(principal);
    }

    @Test
    public void testGetPrincipalIgnoresTokensLackingAudience() {
        long ctSeconds = System.currentTimeMillis() / 1000;

        String token = this.buildToken(new JsonWebToken()
            .type(CloudAuthTokenType.STANDARD.toString())
            .subject("test_subject")
            .iat(ctSeconds)
            .nbf(ctSeconds)
            .exp(ctSeconds + 300));

        MockHttpRequest request = this.buildHttpRequest();
        request.header("Authorization", "Bearer " + token);

        Principal principal = this.buildAuthProvider()
            .getPrincipal(request);

        assertNull(principal);
    }

    @Test
    public void testGetPrincipalIgnoresExpiredTokens() {
        long ctSeconds = System.currentTimeMillis() / 1000;

        String token = this.buildToken(new JsonWebToken()
            .type(CloudAuthTokenType.STANDARD.toString())
            .subject("test_subject")
            .audience("test_org")
            .iat(ctSeconds - 15)
            .nbf(ctSeconds - 15)
            .exp(ctSeconds - 10));

        MockHttpRequest request = this.buildHttpRequest();
        request.header("Authorization", "Bearer " + token);

        Principal principal = this.buildAuthProvider()
            .getPrincipal(request);

        assertNull(principal);
    }

    @Test
    public void testGetPrincipalIgnoresInactiveTokens() {
        long ctSeconds = System.currentTimeMillis() / 1000;

        String token = this.buildToken(new JsonWebToken()
            .type(CloudAuthTokenType.STANDARD.toString())
            .subject("test_subject")
            .audience("test_org")
            .iat(ctSeconds + 15)
            .nbf(ctSeconds + 15)
            .exp(ctSeconds + 25));

        MockHttpRequest request = this.buildHttpRequest();
        request.header("Authorization", "Bearer " + token);

        Principal principal = this.buildAuthProvider()
            .getPrincipal(request);

        assertNull(principal);
    }
}
