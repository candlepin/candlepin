/*
 * Copyright (c) 2009 - 2026 Red Hat, Inc.
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.candlepin.auth.CloudAuthTokenGenerator.ValidationResult;
import org.candlepin.config.ConfigProperties;
import org.candlepin.config.DevConfig;
import org.candlepin.config.TestConfig;
import org.candlepin.pki.Scheme;
import org.candlepin.pki.SchemeReader;
import org.candlepin.test.CryptoUtil;
import org.candlepin.util.Util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.keycloak.common.util.KeyUtils;
import org.keycloak.crypto.Algorithm;
import org.keycloak.crypto.AsymmetricSignatureSignerContext;
import org.keycloak.crypto.KeyType;
import org.keycloak.crypto.KeyUse;
import org.keycloak.crypto.KeyWrapper;
import org.keycloak.jose.jws.JWSBuilder;
import org.keycloak.representations.JsonWebToken;

import java.io.File;
import java.security.Key;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.cert.X509Certificate;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;



public class CloudAuthTokenGeneratorTest {
    private static final String TOKEN_SUBJECT_DEFAULT = "cloud_auth";

    private DevConfig config;
    private CloudAuthTokenGenerator tokenGenerator;
    private SchemeReader schemeReader;
    private ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    public void beforeEach() {
        this.config = TestConfig.defaults();
        this.schemeReader = CryptoUtil.getSchemeReader(this.config);
        this.tokenGenerator = new CloudAuthTokenGenerator(this.config, this.schemeReader);
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
    public void testBuildStandardRegistrationTokenWithNonDefaultAlgorithm() {
        String expectedAlgorithm = Algorithm.RS384;
        this.config.setProperty(ConfigProperties.JWT_CRYPTO_SCHEME_KEY_ALGORITHM, expectedAlgorithm);
        SchemeReader schemeReader = CryptoUtil.getSchemeReader(this.config);
        CloudAuthTokenGenerator generator = new CloudAuthTokenGenerator(this.config, schemeReader);
        Principal principal = new UserPrincipal("test_user", null, false);

        String token = generator.buildStandardRegistrationToken(principal, "owner-key");

        assertAlgorithm(expectedAlgorithm, token);
    }

    @Test
    public void testBuildStandardRegistrationToken() throws Exception {
        String expectedOwnerKey = "owner-key";
        String username = "test-user";
        Principal principal = new UserPrincipal(username, null, false);

        String token = tokenGenerator.buildStandardRegistrationToken(principal, expectedOwnerKey);

        assertToken(CloudAuthTokenType.STANDARD.toString(), expectedOwnerKey, username, token);
    }

    @Test
    public void testBuildStandardRegistrationTokenWithNullPrincipalUsername() throws Exception {
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
    public void testBuildAnonymousRegistrationToken() throws Exception {
        String consumerUuid = Util.generateUUID();
        String username = "test-user";
        Principal principal = new UserPrincipal(username, null, false);

        String token = tokenGenerator.buildAnonymousRegistrationToken(principal, consumerUuid);

        assertToken(CloudAuthTokenType.ANONYMOUS.toString(), consumerUuid, username, token);
    }

    @Test
    public void testBuildAnonymousRegistrationTokenWithNullPrincipalUsername() throws Exception {
        String expectedOwnerKey = "owner-key";
        Principal principal = new UserPrincipal(null, null, false);

        String token = tokenGenerator.buildAnonymousRegistrationToken(principal, expectedOwnerKey);

        assertToken(CloudAuthTokenType.ANONYMOUS.toString(), expectedOwnerKey, TOKEN_SUBJECT_DEFAULT, token);
    }

    @ParameterizedTest
    @NullAndEmptySource
    public void testValidateTokenWithNullOrEmptyToken(String token) {
        ValidationResult actual = this.tokenGenerator.validateToken(token, CloudAuthTokenType.STANDARD);

        assertThat(actual)
            .isNotNull()
            .returns(false, ValidationResult::isValid)
            .returns(null, ValidationResult::audience)
            .returns(null, ValidationResult::subject);
    }

    @Test
    public void testValidateTokenWithNullType() {
        assertThrows(IllegalArgumentException.class, () -> {
            this.tokenGenerator.validateToken("token", null);
        });
    }

    @Test
    public void testValidateTokenWithInvalidSignature() throws Exception {
        String signatureAlgorithm = "SHA256WithRSA";
        String keyAlgorithm = "rsa";
        int keySize = 4096;

        // Generate a JWT token using a different certificate and private key than what the
        // CloudAuthTokenGenerator is using to validate the token. This should result in a validation failure
        // due to an invalid signature.

        KeyPair keyPair = CryptoUtil.generateKeyPair(keyAlgorithm, keySize);
        X509Certificate certificate = CryptoUtil.generateX509Certificate(keyPair, signatureAlgorithm);

        File keyFile = File.createTempFile("cp_test_other_key", ".pem");
        keyFile.deleteOnExit();
        CryptoUtil.writePrivateKeyToFile(keyPair.getPrivate(), keyFile, null);

        File certFile = File.createTempFile("cp_test_other_cert", ".pem");
        certFile.deleteOnExit();
        CryptoUtil.writeCertificateToFile(certificate, certFile);

        DevConfig otherConfig  = TestConfig.defaults();
        otherConfig.setProperty(ConfigProperties.JWT_CRYPTO_SCHEME_CERT, certFile.getAbsolutePath());
        otherConfig.setProperty(ConfigProperties.JWT_CRYPTO_SCHEME_KEY, keyFile.getAbsolutePath());

        SchemeReader otherSchemeReader = CryptoUtil.getSchemeReader(otherConfig);
        CloudAuthTokenGenerator otherTokenGenerator =
            new CloudAuthTokenGenerator(otherConfig, otherSchemeReader);

        Principal principal = new UserPrincipal("test-user", null, false);
        String token = otherTokenGenerator.buildStandardRegistrationToken(principal, "owner-key");

        ValidationResult actual = this.tokenGenerator.validateToken(token, CloudAuthTokenType.STANDARD);

        assertThat(actual)
            .isNotNull()
            .returns(false, ValidationResult::isValid)
            .returns(null, ValidationResult::audience)
            .returns(null, ValidationResult::subject);
    }

    @Test
    public void testValidateTokenWithValidStandardToken() {
        String expectedOwnerKey = "owner-key";
        String expectedSubject = "test-user";
        Principal principal = new UserPrincipal(expectedSubject, null, false);
        String token = this.tokenGenerator.buildStandardRegistrationToken(principal, expectedOwnerKey);

        ValidationResult actual = this.tokenGenerator.validateToken(token, CloudAuthTokenType.STANDARD);

        assertThat(actual)
            .isNotNull()
            .returns(true, ValidationResult::isValid)
            .returns(expectedOwnerKey, ValidationResult::audience)
            .returns(expectedSubject, ValidationResult::subject);
    }

    @Test
    public void testValidateTokenWithValidAnonymousToken() {
        String expectedOwnerKey = "owner-key";
        String expectedSubject = "test-user";
        Principal principal = new UserPrincipal(expectedSubject, null, false);
        String token = this.tokenGenerator.buildAnonymousRegistrationToken(principal, expectedOwnerKey);

        ValidationResult actual = this.tokenGenerator.validateToken(token, CloudAuthTokenType.ANONYMOUS);

        assertThat(actual)
            .isNotNull()
            .returns(true, ValidationResult::isValid)
            .returns(expectedOwnerKey, ValidationResult::audience)
            .returns(expectedSubject, ValidationResult::subject);
    }

    @Test
    public void testValidateTokenWithWrongTokenType() {
        Principal principal = new UserPrincipal("test-user", null, false);
        String anonToken = this.tokenGenerator
            .buildAnonymousRegistrationToken(principal, "owner-key");
        String standardToken = this.tokenGenerator
            .buildStandardRegistrationToken(principal, "owner-key");

        assertThat(this.tokenGenerator.validateToken(anonToken, CloudAuthTokenType.STANDARD))
            .isNotNull()
            .returns(false, ValidationResult::isValid)
            .returns(null, ValidationResult::audience)
            .returns(null, ValidationResult::subject);

        assertThat(this.tokenGenerator.validateToken(standardToken, CloudAuthTokenType.ANONYMOUS))
            .isNotNull()
            .returns(false, ValidationResult::isValid)
            .returns(null, ValidationResult::audience)
            .returns(null, ValidationResult::subject);
    }

    @Test
    public void testValidateTokenWithExpiredToken() {
        long now = System.currentTimeMillis() / 1000;
        String token = this.createTokenFromCACert(new JsonWebToken()
            .type(CloudAuthTokenType.STANDARD.toString())
            .subject("subject")
            .audience("owner-key")
            .iat(now - 1000)
            .nbf(now - 1000)
            .exp(now - 500));

        assertThat(this.tokenGenerator.validateToken(token, CloudAuthTokenType.STANDARD))
            .isNotNull()
            .returns(false, ValidationResult::isValid)
            .returns(null, ValidationResult::audience)
            .returns(null, ValidationResult::subject);
    }

    @Test
    public void testValidateTokenWithNotYetActiveToken() {
        long now = System.currentTimeMillis() / 1000;
        String token = this.createTokenFromCACert(new JsonWebToken()
            .type(CloudAuthTokenType.STANDARD.toString())
            .subject("subject")
            .audience("owner-key")
            .iat(now)
            .nbf(now + 500)
            .exp(now + 1000));

        assertThat(this.tokenGenerator.validateToken(token, CloudAuthTokenType.STANDARD))
            .isNotNull()
            .returns(false, ValidationResult::isValid)
            .returns(null, ValidationResult::audience)
            .returns(null, ValidationResult::subject);
    }

    @ParameterizedTest
    @NullAndEmptySource
    public void testValidateTokenWithInvalidSubject(String subject) {
        long now = (System.currentTimeMillis() / 1000) - 5;
        String token = this.createTokenFromCACert(new JsonWebToken()
            .type(CloudAuthTokenType.STANDARD.toString())
            .subject(subject)
            .audience("owner-key")
            .iat(now)
            .nbf(now)
            .exp(now + 1000));

        assertThat(this.tokenGenerator.validateToken(token, CloudAuthTokenType.STANDARD))
            .isNotNull()
            .returns(false, ValidationResult::isValid)
            .returns(null, ValidationResult::audience)
            .returns(null, ValidationResult::subject);
    }

    @ParameterizedTest
    @NullAndEmptySource
    public void testValidateTokenWithInvalidAudience(String audience) {
        long now = (System.currentTimeMillis() / 1000) - 5;
        String token = this.createTokenFromCACert(new JsonWebToken()
            .type(CloudAuthTokenType.STANDARD.toString())
            .subject("subject")
            .audience(audience)
            .iat(now)
            .nbf(now)
            .exp(now + 1000));

        assertThat(this.tokenGenerator.validateToken(token, CloudAuthTokenType.STANDARD))
            .isNotNull()
            .returns(false, ValidationResult::isValid)
            .returns(null, ValidationResult::audience)
            .returns(null, ValidationResult::subject);
    }

    private String createTokenFromCACert(JsonWebToken token) {
        Scheme scheme = this.schemeReader.readScheme("jwt-scheme",
            ConfigProperties.JWT_CRYPTO_SCHEME_CERT,
            ConfigProperties.JWT_CRYPTO_SCHEME_KEY,
            ConfigProperties.JWT_CRYPTO_SCHEME_KEY_PASSWORD,
            ConfigProperties.JWT_CRYPTO_SCHEME_SIGNATURE_ALGORITHM,
            ConfigProperties.JWT_CRYPTO_SCHEME_KEY_ALGORITHM,
            ConfigProperties.JWT_CRYPTO_SCHEME_KEY_SIZE);
        PrivateKey privateKey = scheme.privateKey()
            .orElseThrow(() -> new RuntimeException("missing private key"));
        PublicKey publicKey = scheme.certificate().getPublicKey();

        return createToken(privateKey, publicKey, scheme.certificate(), token);
    }

    private String createToken(Key privateKey, Key publicKey, X509Certificate certificate,
        JsonWebToken token) {

        String keyId = KeyUtils.createKeyId(publicKey);

        KeyWrapper wrapper = new KeyWrapper();
        wrapper.setAlgorithm(Algorithm.RS512);
        wrapper.setCertificate(certificate);
        wrapper.setKid(keyId);
        wrapper.setPrivateKey(privateKey);
        wrapper.setPublicKey(publicKey);
        wrapper.setUse(KeyUse.SIG);
        wrapper.setType(KeyType.RSA);

        return new JWSBuilder()
            .kid(keyId)
            .type("JWT")
            .jsonContent(token)
            .sign(new AsymmetricSignatureSignerContext(wrapper));
    }

    private void assertToken(String expectedType, String expectedAudience, String subject, String token)
        throws JsonMappingException, JsonProcessingException {
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

    private void assertAlgorithm(String expected, String token) {
        String[] chunks = token.split("\\.");
        Base64.Decoder decoder = Base64.getUrlDecoder();
        String header = chunks[0];
        byte[] decoded = decoder.decode(header);
        String headerJson = new String(decoded);

        try {
            JsonNode node = objectMapper.readTree(headerJson);
            String actual = node.path("alg").asText();
            assertEquals(expected, actual);
        }
        catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

}
