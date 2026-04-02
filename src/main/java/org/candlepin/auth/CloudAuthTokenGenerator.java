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

import org.candlepin.config.ConfigProperties;
import org.candlepin.config.Configuration;
import org.candlepin.config.ConfigurationException;
import org.candlepin.pki.CertificateReader;
import org.candlepin.pki.PrivateKeyReader;
import org.candlepin.util.Util;

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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.KeyException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Objects;

import javax.inject.Inject;



/**
 * A class for generating and validating cloud authentication tokens
 */
public class CloudAuthTokenGenerator {
    private static final Logger log = LoggerFactory.getLogger(CloudAuthTokenGenerator.class);

    private static final String TOKEN_ALGORITHM = Algorithm.RS512;
    private static final String TOKEN_SUBJECT_DEFAULT = "cloud_auth";

    private final Configuration config;
    private final CertificateReader certificateReader;
    private final PrivateKeyReader privateKeyReader;

    private final String jwtIssuer;

    private final X509Certificate certificate;
    private final PublicKey publicKey;
    private final PrivateKey privateKey;

    @Inject
    public CloudAuthTokenGenerator(Configuration config, CertificateReader certificateReader,
        PrivateKeyReader privateKeyReader) {

        this.config = Objects.requireNonNull(config);
        this.certificateReader = Objects.requireNonNull(certificateReader);
        this.privateKeyReader = Objects.requireNonNull(privateKeyReader);

        this.jwtIssuer = this.config.getString(ConfigProperties.JWT_ISSUER);

        String certPath = this.config.getOptionalString(ConfigProperties.JWT_CRYPTO_CERT)
            .or(() -> this.config.getOptionalString(ConfigProperties.LEGACY_CA_CERT))
            .orElseThrow(() ->
                new ConfigurationException(String.format("No JWT certificate path defined for %s",
                    ConfigProperties.JWT_CRYPTO_CERT)));
        String keyPath = this.config.getOptionalString(ConfigProperties.JWT_CRYPTO_KEY)
            .or(() -> this.config.getOptionalString(ConfigProperties.LEGACY_CA_KEY))
            .orElseThrow(() ->
                new ConfigurationException(String.format("No JWT certificate key defined for %s",
                    ConfigProperties.JWT_CRYPTO_KEY)));
        String keyPassword = this.config.getOptionalString(ConfigProperties.JWT_CRYPTO_KEY_PASSWORD)
            .or(() -> this.config.getOptionalString(ConfigProperties.LEGACY_CA_KEY_PASSWORD))
            .orElse(null);

        try {
            this.certificate = this.certificateReader.read(certPath);
            this.publicKey = this.certificate.getPublicKey();
            this.privateKey = this.privateKeyReader.read(keyPath, keyPassword);
        }
        catch (CertificateException | KeyException e) {
            throw new ConfigurationException(
                String.format("Unable to load public and private keys (cert=%s, key=%s)", certPath, keyPath),
                e);
        }
    }

    /**
     * Creates a new cloud authentication token.
     *
     * @param principal
     *  the principal for which the token will be generated
     *
     * @param audience
     *  value that will populate the JWT token's audience field
     *
     * @param type
     *  value that will populate the JWT token's type field
     *
     * @param ttl
     *  the number of seconds that the generated token should be considered active
     *
     * @return a generated cloud authentication token
     */
    public String generateAuthToken(Principal principal, String audience, String type, long ttl) {
        if (principal == null) {
            throw new IllegalArgumentException("principal is null");
        }

        String keyId = KeyUtils.createKeyId(this.publicKey);

        // Try to use the username present in the principal; otherwise use the default
        String username = principal.getUsername();
        if (username == null) {
            username = TOKEN_SUBJECT_DEFAULT;
        }

        KeyWrapper wrapper = new KeyWrapper();
        wrapper.setAlgorithm(TOKEN_ALGORITHM);
        wrapper.setCertificate(this.certificate);
        wrapper.setKid(keyId);
        wrapper.setPrivateKey(this.privateKey);
        wrapper.setPublicKey(this.publicKey);
        wrapper.setUse(KeyUse.SIG);
        wrapper.setType(KeyType.RSA);

        long ctSeconds = System.currentTimeMillis() / 1000;

        JsonWebToken token = new JsonWebToken()
            .id(Util.generateUUID())
            .type(type)
            .issuer(this.jwtIssuer)
            .subject(username)
            .audience(audience)
            .iat(ctSeconds)
            .exp(ctSeconds + ttl)
            .nbf(ctSeconds);

        return new JWSBuilder()
            .kid(keyId)
            .type("JWT")
            .jsonContent(token)
            .sign(new AsymmetricSignatureSignerContext(wrapper));
    }

    /**
     * Validates the authenticity of the provided JWT token by verifying the signature and if the token is
     * active.
     *
     * @param token
     *  the serialized token to validate
     *
     * @throws VerificationException
     *  if the provided token is not valid
     *
     * @return the deserialized token if the validation was successful
     */
    public JsonWebToken validateToken(String token) throws VerificationException {
        if (token == null) {
            throw new VerificationException("token is null");
        }

        TokenVerifier<JsonWebToken> verifier = TokenVerifier.create(token, JsonWebToken.class)
            .publicKey(publicKey)
            .verify();

        JsonWebToken webToken = verifier.getToken();
        if (!webToken.isActive()) {
            throw new VerificationException("Token is not active or has expired");
        }

        return webToken;
    }

}
