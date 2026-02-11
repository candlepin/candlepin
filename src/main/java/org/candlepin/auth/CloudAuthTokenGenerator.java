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
import org.candlepin.config.ConversionException;
import org.candlepin.pki.CertificateReader;
import org.candlepin.util.Util;

import org.keycloak.TokenVerifier;
import org.keycloak.common.VerificationException;
import org.keycloak.common.util.KeyUtils;
import org.keycloak.crypto.AsymmetricSignatureSignerContext;
import org.keycloak.crypto.KeyType;
import org.keycloak.crypto.KeyUse;
import org.keycloak.crypto.KeyWrapper;
import org.keycloak.jose.jws.JWSBuilder;
import org.keycloak.representations.JsonWebToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.cert.X509Certificate;
import java.util.Objects;

import javax.inject.Inject;



/**
 * A class for generating and validating cloud authentication tokens
 */
public class CloudAuthTokenGenerator {
    private static Logger log = LoggerFactory.getLogger(CloudRegistrationAuth.class);

    private static final String TOKEN_SUBJECT_DEFAULT = "cloud_auth";

    private final Configuration config;
    private final CertificateReader certificateReader;

    private final String jwtIssuer;
    private final int jwtTokenTTL; // seconds
    private final int anonJwtTokenTTL; // seconds
    private String algorithm;

    private final X509Certificate certificate;
    private final PublicKey publicKey;
    private final PrivateKey privateKey;

    @Inject
    public CloudAuthTokenGenerator(Configuration config, CertificateReader certificateReader) {

        this.config = Objects.requireNonNull(config);
        this.certificateReader = Objects.requireNonNull(certificateReader);

        try {
            this.jwtIssuer = this.config.getString(ConfigProperties.JWT_ISSUER);
            this.jwtTokenTTL = this.config.getInt(ConfigProperties.JWT_TOKEN_TTL);
            this.anonJwtTokenTTL = this.config.getInt(ConfigProperties.ANON_JWT_TOKEN_TTL);
        }
        catch (ConversionException e) {
            // Try to pretty up the exception for easy debugging
            throw new RuntimeException("Invalid value(s) found while parsing JWT configuration", e);
        }

        try {
            this.certificate = this.certificateReader.getCACert();
            this.publicKey = certificate.getPublicKey();
            this.privateKey = this.certificateReader.getCaKey();
        }
        catch (Exception e) {
            throw new RuntimeException("Unable to load public and private keys", e);
        }

        this.algorithm = this.config.getString(ConfigProperties.JWT_CRYPTO_SCHEME);
    }

    /**
     * Creates a new standard cloud registration token for the specific owner key. The owner/organization
     * will be set as the subject of the token, and need not explicitly exist locally in Candlepin.
     *
     * @param principal
     *  the principal for which the token will be generated
     *
     * @param ownerKey
     *  the key of the owner/organization for which the token will be generated
     *
     * @throws IllegalArgumentException
     *  if the principal is null, or if the owner key is null or blank
     *
     * @return
     *  an encrypted JWT token string
     */
    public String buildStandardRegistrationToken(Principal principal, String ownerKey) {
        if (principal == null) {
            throw new IllegalArgumentException("principal is null");
        }

        if (ownerKey == null || ownerKey.isBlank()) {
            throw new IllegalArgumentException("owner key is null or blank");
        }

        String keyId = KeyUtils.createKeyId(this.publicKey);

        // Try to use the username present in the principal; otherwise use the default
        String username = principal.getUsername();
        if (username == null) {
            username = TOKEN_SUBJECT_DEFAULT;
        }

        KeyWrapper wrapper = new KeyWrapper();
        wrapper.setAlgorithm(this.algorithm);
        wrapper.setCertificate(this.certificate);
        wrapper.setKid(keyId);
        wrapper.setPrivateKey(this.privateKey);
        wrapper.setPublicKey(this.publicKey);
        wrapper.setUse(KeyUse.SIG);
        wrapper.setType(KeyType.RSA);

        long ctSeconds = System.currentTimeMillis() / 1000;

        JsonWebToken token = new JsonWebToken()
            .id(Util.generateUUID())
            .type(CloudAuthTokenType.STANDARD.toString())
            .issuer(this.jwtIssuer)
            .subject(username)
            .audience(ownerKey)
            .iat(ctSeconds)
            .exp(ctSeconds + this.jwtTokenTTL)
            .nbf(ctSeconds);

        return new JWSBuilder()
            .kid(keyId)
            .type("JWT")
            .jsonContent(token)
            .sign(new AsymmetricSignatureSignerContext(wrapper));
    }

    /**
     * Creates a new anonymous cloud registration token for the specific anonymous consumer UUID.
     * The consumer UUID will be set as the audience of the token.
     *
     * @param principal
     *  the principal for which the token will be generated
     *
     * @param consumerUuid
     *  the UUID of the anonymous consumer for which the token will be generated
     *
     * @throws IllegalArgumentException
     *  if the principal is null, or if the consumer UUID is null or blank
     *
     * @return
     *  an encrypted JWT token string
     */
    public String buildAnonymousRegistrationToken(Principal principal, String consumerUuid) {
        if (principal == null) {
            throw new IllegalArgumentException("principal is null");
        }

        if (consumerUuid == null || consumerUuid.isBlank()) {
            throw new IllegalArgumentException("consumer UUID is null or blank");
        }

        String keyId = KeyUtils.createKeyId(this.publicKey);

        String username = principal.getUsername();
        if (username == null) {
            username = TOKEN_SUBJECT_DEFAULT;
        }

        KeyWrapper wrapper = new KeyWrapper();
        wrapper.setAlgorithm(this.algorithm);
        wrapper.setCertificate(this.certificate);
        wrapper.setKid(keyId);
        wrapper.setPrivateKey(this.privateKey);
        wrapper.setPublicKey(this.publicKey);
        wrapper.setUse(KeyUse.SIG);
        wrapper.setType(KeyType.RSA);

        long ctSeconds = System.currentTimeMillis() / 1000;

        JsonWebToken token = new JsonWebToken()
            .id(Util.generateUUID())
            .type(CloudAuthTokenType.ANONYMOUS.toString())
            .issuer(this.jwtIssuer)
            .subject(username)
            .audience(consumerUuid)
            .iat(ctSeconds)
            .exp(ctSeconds + this.anonJwtTokenTTL)
            .nbf(ctSeconds);

        return new JWSBuilder()
            .kid(keyId)
            .type("JWT")
            .jsonContent(token)
            .sign(new AsymmetricSignatureSignerContext(wrapper));
    }

    /**
     * Validates the authenticity of the provided JWT token by verifying the signature. Expected fields such
     * as the type, subject, and audience are also validated based on the provided {@link CloudAuthTokenType}.
     *
     * @param token
     *  the JWT token the validate
     *
     * @param expectedType
     *  the expected type of cloud auth token
     *
     * @throws IllegalArgumentException
     *  if the provided token type is null
     *
     * @return the result of the validation
     */
    public ValidationResult validateToken(String token, CloudAuthTokenType expectedType) {
        if (expectedType == null) {
            throw new IllegalArgumentException("expected type is null");
        }

        if (token == null || token.isBlank()) {
            log.debug("token is null or blank");
            return new ValidationResult(false, null);
        }

        JsonWebToken webToken = null;
        try {
            TokenVerifier<JsonWebToken> verifier = TokenVerifier.create(token, JsonWebToken.class)
                .publicKey(publicKey)
                .verify();

            webToken = verifier.getToken();
        }
        catch (VerificationException e) {
            log.debug("Unable to verify token signature");
            return new ValidationResult(false, null);
        }

        if (!webToken.isActive()) {
            log.debug("Token is not active or has expired");
            return new ValidationResult(false, null);
        }

        String actualType = webToken.getType();
        if (!expectedType.equalsType(actualType)) {
            log.debug("Invalid token type. Expected: {}, but was {}",  expectedType, actualType);
            return new ValidationResult(false, null);
        }

        String subject = webToken.getSubject();
        if (subject == null || subject.isBlank()) {
            log.debug("Invalid token subject. The subject is either null or blank");
            return new ValidationResult(false, null);
        }

        // Both the standard token and anonymous token expect the first audience to be populated

        String[] audiences = webToken.getAudience();
        String audience = audiences != null && audiences.length > 0 ? audiences[0] : null;
        if (audience == null || audience.isEmpty()) {
            log.debug("Token contains an invalid audience: {}", audience);
            return new ValidationResult(false, null);
        }

        return new ValidationResult(true, audience);
    }

    public record ValidationResult(
        boolean isValid,
        String audienceValue
    ) {}

}
