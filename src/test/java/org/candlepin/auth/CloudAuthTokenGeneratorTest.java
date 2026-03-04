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

import org.candlepin.config.ConfigProperties;
import org.candlepin.config.ConfigurationException;
import org.candlepin.config.DevConfig;
import org.candlepin.config.TestConfig;
import org.candlepin.pki.CertificateReader;
import org.candlepin.pki.CryptoManager;
import org.candlepin.pki.PrivateKeyReader;
import org.candlepin.pki.Scheme;
import org.candlepin.test.CryptoUtil;
import org.candlepin.test.TestUtil;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.keycloak.TokenVerifier;
import org.keycloak.common.VerificationException;
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
    private ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    public void beforeEach() {
        this.config = TestConfig.defaults();

        CertificateReader certReader = CryptoUtil.getCertificateReader();
        PrivateKeyReader privateKeyReader = CryptoUtil.getPrivateKeyReader();
        this.tokenGenerator = new CloudAuthTokenGenerator(this.config, certReader, privateKeyReader);
    }

    @Test
    public void testConstructorWithNoCertConfigurations() {
        DevConfig modifiedConfig = TestConfig.defaults();
        modifiedConfig.clearProperty(ConfigProperties.JWT_CRYPTO_CERT);
        modifiedConfig.clearProperty(ConfigProperties.LEGACY_CA_CERT);

        CertificateReader certReader = CryptoUtil.getCertificateReader();
        PrivateKeyReader privateKeyReader = CryptoUtil.getPrivateKeyReader();

        assertThrows(ConfigurationException.class, () -> {
            new CloudAuthTokenGenerator(modifiedConfig, certReader, privateKeyReader);
        });
    }

    @Test
    public void testConstructorWithNoKeyConfigurations() {
        DevConfig modifiedConfig = TestConfig.defaults();
        modifiedConfig.clearProperty(ConfigProperties.JWT_CRYPTO_KEY);
        modifiedConfig.clearProperty(ConfigProperties.LEGACY_CA_KEY);

        CertificateReader certReader = CryptoUtil.getCertificateReader();
        PrivateKeyReader privateKeyReader = CryptoUtil.getPrivateKeyReader();

        assertThrows(ConfigurationException.class, () -> {
            new CloudAuthTokenGenerator(modifiedConfig, certReader, privateKeyReader);
        });
    }

    @Test
    public void testConstructorShouldPrioritizeJWTCertAndKeyConfigurations() throws Exception {
        String expectedPassword = TestUtil.randomString();

        String signatureAlgorithm = "SHA256WithRSA";
        String keyAlgorithm = "rsa";
        int keySize = 4096;

        KeyPair keyPair = CryptoUtil.generateKeyPair(keyAlgorithm, keySize);
        X509Certificate certificate = CryptoUtil.generateX509Certificate(keyPair, signatureAlgorithm);

        File keyFile = File.createTempFile("cp_test_other_key", ".pem");
        keyFile.deleteOnExit();
        CryptoUtil.writePrivateKeyToFile(keyPair.getPrivate(), keyFile, expectedPassword);

        File certFile = File.createTempFile("cp_test_other_cert", ".pem");
        certFile.deleteOnExit();
        CryptoUtil.writeCertificateToFile(certificate, certFile);

        DevConfig otherConfig  = TestConfig.defaults();
        otherConfig.setProperty(ConfigProperties.JWT_CRYPTO_CERT, certFile.getAbsolutePath());
        otherConfig.setProperty(ConfigProperties.JWT_CRYPTO_KEY, keyFile.getAbsolutePath());
        otherConfig.setProperty(ConfigProperties.JWT_CRYPTO_KEY_PASSWORD, expectedPassword);
        otherConfig.setProperty(ConfigProperties.LEGACY_CA_KEY_PASSWORD, TestUtil.randomString());

        CertificateReader certReader = CryptoUtil.getCertificateReader();
        PrivateKeyReader privateKeyReader = CryptoUtil.getPrivateKeyReader();
        CloudAuthTokenGenerator otherTokenGenerator =
            new CloudAuthTokenGenerator(otherConfig, certReader, privateKeyReader);

        Principal principal = new UserPrincipal(null, null, false);

        String token = otherTokenGenerator.generateAuthToken(principal, "owner", "type", 1000L);

        // Assert that a ConfigurationException is not thrown when reading the private key configuration
        // because the legacy key password was used

        assertThat(token)
            .isNotNull();

        // Verify that we signed our token with the JWT crypto cert and key configurations
        TokenVerifier.create(token, JsonWebToken.class)
            .publicKey(certificate.getPublicKey())
            .verify();
    }

    @Test
    public void testConstructorShouldDefaultToLegacyCertAndKeyConfigurations() throws Exception {
        String expectedPassword = TestUtil.randomString();

        String signatureAlgorithm = "SHA256WithRSA";
        String keyAlgorithm = "rsa";
        int keySize = 4096;

        KeyPair keyPair = CryptoUtil.generateKeyPair(keyAlgorithm, keySize);
        X509Certificate certificate = CryptoUtil.generateX509Certificate(keyPair, signatureAlgorithm);

        File keyFile = File.createTempFile("cp_test_other_key", ".pem");
        keyFile.deleteOnExit();
        CryptoUtil.writePrivateKeyToFile(keyPair.getPrivate(), keyFile, expectedPassword);

        File certFile = File.createTempFile("cp_test_other_cert", ".pem");
        certFile.deleteOnExit();
        CryptoUtil.writeCertificateToFile(certificate, certFile);

        DevConfig otherConfig  = TestConfig.defaults();
        otherConfig.clearProperty(ConfigProperties.JWT_CRYPTO_CERT);
        otherConfig.clearProperty(ConfigProperties.JWT_CRYPTO_KEY);
        otherConfig.clearProperty(ConfigProperties.JWT_CRYPTO_KEY_PASSWORD);

        otherConfig.setProperty(ConfigProperties.LEGACY_CA_CERT, certFile.getAbsolutePath());
        otherConfig.setProperty(ConfigProperties.LEGACY_CA_KEY, keyFile.getAbsolutePath());
        otherConfig.setProperty(ConfigProperties.LEGACY_CA_KEY_PASSWORD, expectedPassword);

        CertificateReader certReader = CryptoUtil.getCertificateReader();
        PrivateKeyReader privateKeyReader = CryptoUtil.getPrivateKeyReader();
        CloudAuthTokenGenerator otherTokenGenerator =
            new CloudAuthTokenGenerator(otherConfig, certReader, privateKeyReader);

        Principal principal = new UserPrincipal(null, null, false);

        String token = otherTokenGenerator.generateAuthToken(principal, "owner", "type", 1000L);

        // Assert that a ConfigurationException is not thrown when reading the private key configuration

        assertThat(token)
            .isNotNull();

        // Verify that we signed our token with the legacy crypto cert and key configurations
        TokenVerifier.create(token, JsonWebToken.class)
            .publicKey(certificate.getPublicKey())
            .verify();
    }

    @Test
    public void testConstructorWithInvalidCertPath() {
        DevConfig otherConfig  = TestConfig.defaults();
        otherConfig.setProperty(ConfigProperties.JWT_CRYPTO_CERT, "bad-path");

        CertificateReader certReader = CryptoUtil.getCertificateReader();
        PrivateKeyReader privateKeyReader = CryptoUtil.getPrivateKeyReader();
        assertThrows(ConfigurationException.class, () -> {
            new CloudAuthTokenGenerator(otherConfig, certReader, privateKeyReader);
        });
    }

    @Test
    public void testConstructorWithIncorrectPrivateKeyPassword() throws Exception {
        String signatureAlgorithm = "SHA256WithRSA";
        String keyAlgorithm = "rsa";
        int keySize = 4096;

        KeyPair keyPair = CryptoUtil.generateKeyPair(keyAlgorithm, keySize);
        X509Certificate certificate = CryptoUtil.generateX509Certificate(keyPair, signatureAlgorithm);

        File keyFile = File.createTempFile("cp_test_other_key", ".pem");
        keyFile.deleteOnExit();
        CryptoUtil.writePrivateKeyToFile(keyPair.getPrivate(), keyFile, "password");

        File certFile = File.createTempFile("cp_test_other_cert", ".pem");
        certFile.deleteOnExit();
        CryptoUtil.writeCertificateToFile(certificate, certFile);

        DevConfig otherConfig  = TestConfig.defaults();
        otherConfig.setProperty(ConfigProperties.JWT_CRYPTO_CERT, certFile.getAbsolutePath());
        otherConfig.setProperty(ConfigProperties.JWT_CRYPTO_KEY, keyFile.getAbsolutePath());
        otherConfig.setProperty(ConfigProperties.JWT_CRYPTO_KEY_PASSWORD, "bad-password");

        CertificateReader certReader = CryptoUtil.getCertificateReader();
        PrivateKeyReader privateKeyReader = CryptoUtil.getPrivateKeyReader();
        assertThrows(ConfigurationException.class, () -> {
            new CloudAuthTokenGenerator(otherConfig, certReader, privateKeyReader);
        });
    }

    @Test
    public void testGenerateAuthTokenWithNullPrincipal() {
        assertThrows(IllegalArgumentException.class, () -> {
            this.tokenGenerator.generateAuthToken(null, "audience", "type", 1000L);
        });
    }

    @Test
    public void testGenerateAuthToken() throws Exception {
        String expectedAudience = TestUtil.randomString("audience-");
        String expectedType = TestUtil.randomString("type-");
        String expectedSubject = TestUtil.randomString("subject-");
        Principal principal = new UserPrincipal(expectedSubject, null, false);

        String token = this.tokenGenerator
            .generateAuthToken(principal, expectedAudience, expectedType, 1000L);

        assertThat(token)
            .isNotNull();

        assertToken(expectedType, expectedAudience, expectedSubject, token);
    }

    @Test
    public void testGenerateAuthTokenWithNullAudience() throws Exception {
        String expectedType = TestUtil.randomString("type-");
        String expectedSubject = TestUtil.randomString("subject-");
        Principal principal = new UserPrincipal(expectedSubject, null, false);

        String token = this.tokenGenerator
            .generateAuthToken(principal, null, expectedType, 1000L);

        assertThat(token)
            .isNotNull();

        assertToken(expectedType, null, expectedSubject, token);
    }

    @Test
    public void testGenerateAuthTokenWithNullType() throws Exception {
        String expectedAudience = TestUtil.randomString("audience-");
        String expectedSubject = TestUtil.randomString("subject-");
        Principal principal = new UserPrincipal(expectedSubject, null, false);

        String token = this.tokenGenerator
            .generateAuthToken(principal, expectedAudience, null, 1000L);

        assertThat(token)
            .isNotNull();

        assertToken(null, expectedAudience, expectedSubject, token);
    }

    @Test
    public void testGenerateAuthTokenWithNullSubject() throws Exception {
        String expectedAudience = TestUtil.randomString("audience-");
        String expectedType = TestUtil.randomString("type-");
        Principal principal = new UserPrincipal(null, null, false);

        String token = this.tokenGenerator
            .generateAuthToken(principal, expectedAudience, expectedType, 1000L);

        assertThat(token)
            .isNotNull();

        assertToken(expectedType, expectedAudience, TOKEN_SUBJECT_DEFAULT, token);
    }

    @ParameterizedTest
    @NullAndEmptySource
    public void testValidateTokenWithNullOrEmptyToken(String token) {
        assertThrows(VerificationException.class, () -> this.tokenGenerator.validateToken(token));
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
        otherConfig.setProperty(ConfigProperties.JWT_CRYPTO_CERT, certFile.getAbsolutePath());
        otherConfig.setProperty(ConfigProperties.JWT_CRYPTO_KEY, keyFile.getAbsolutePath());

        CertificateReader certReader = CryptoUtil.getCertificateReader();
        PrivateKeyReader privateKeyReader = CryptoUtil.getPrivateKeyReader();
        CloudAuthTokenGenerator otherTokenGenerator =
            new CloudAuthTokenGenerator(otherConfig, certReader, privateKeyReader);

        Principal principal = new UserPrincipal("test-user", null, false);
        String token = otherTokenGenerator.generateAuthToken(principal, "owner", "type", 1000L);

        assertThrows(VerificationException.class, () -> this.tokenGenerator.validateToken(token));
    }

    @Test
    public void testValidateToken() throws Exception {
        String expectedOwnerKey = "owner-key";
        String expectedSubject = "test-user";
        Principal principal = new UserPrincipal(expectedSubject, null, false);
        String token = this.tokenGenerator.generateAuthToken(principal, expectedOwnerKey, "type", 1000L);

        JsonWebToken actual = this.tokenGenerator.validateToken(token);

        assertThat(actual)
            .isNotNull();
    }

    @Test
    public void testValidateTokenWithExpiredToken() {
        long now = System.currentTimeMillis() / 1000;
        String token = this.createTokenFromDefaultScheme(new JsonWebToken()
            .type("type")
            .subject("subject")
            .audience("owner-key")
            .iat(now - 1000)
            .nbf(now - 1000)
            .exp(now - 500));

        assertThrows(VerificationException.class, () -> this.tokenGenerator.validateToken(token));
    }

    @Test
    public void testValidateTokenWithNotYetActiveToken() {
        long now = System.currentTimeMillis() / 1000;
        String token = this.createTokenFromDefaultScheme(new JsonWebToken()
            .type("type")
            .subject("subject")
            .audience("owner-key")
            .iat(now)
            .nbf(now + 500)
            .exp(now + 1000));

        assertThrows(VerificationException.class, () -> this.tokenGenerator.validateToken(token));
    }

    private String createTokenFromDefaultScheme(JsonWebToken token) {
        CryptoManager manager = CryptoUtil.getCryptoManager();
        Scheme scheme = manager.getDefaultCryptoScheme();

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
        Map<String, String> bodyMap = this.objectMapper.readValue(body, HashMap.class);

        assertEquals(expectedType, bodyMap.get("typ"));
        assertEquals(expectedAudience, bodyMap.get("aud"));
        assertEquals(subject, bodyMap.get("sub"));
    }

}
