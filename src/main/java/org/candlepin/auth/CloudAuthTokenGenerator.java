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

import org.candlepin.config.ConfigProperties;
import org.candlepin.config.Configuration;
import org.candlepin.pki.CryptoManager;
import org.candlepin.pki.Scheme;
import org.candlepin.util.Util;

import org.keycloak.common.util.KeyUtils;
import org.keycloak.crypto.Algorithm;
import org.keycloak.crypto.AsymmetricSignatureSignerContext;
import org.keycloak.crypto.KeyType;
import org.keycloak.crypto.KeyUse;
import org.keycloak.crypto.KeyWrapper;
import org.keycloak.jose.jws.JWSBuilder;
import org.keycloak.representations.JsonWebToken;

import java.security.PublicKey;
import java.util.Objects;

import javax.inject.Inject;



/**
 * A class for generating cloud authentication tokens
 */
public class CloudAuthTokenGenerator {
    private static final String TOKEN_ALGORITHM = Algorithm.RS512;
    private static final String TOKEN_SUBJECT_DEFAULT = "cloud_auth";

    private final Configuration config;
    private final CryptoManager cryptoManager;

    private final String jwtIssuer;
    private final int jwtTokenTTL; // seconds
    private final int anonJwtTokenTTL; // seconds

    private final Scheme scheme;
    private final PublicKey publicKey;

    @Inject
    public CloudAuthTokenGenerator(Configuration config, CryptoManager cryptoManager) {
        this.config = Objects.requireNonNull(config);
        this.cryptoManager = Objects.requireNonNull(cryptoManager);

        this.jwtIssuer = this.config.getString(ConfigProperties.JWT_ISSUER);
        this.jwtTokenTTL = this.config.getInt(ConfigProperties.JWT_TOKEN_TTL);
        this.anonJwtTokenTTL = this.config.getInt(ConfigProperties.ANON_JWT_TOKEN_TTL);

        // TODO: FIXME: This needs to be updated to be more scheme-aware. This will come in later work, but
        // for now we'll just use the default/legacy scheme.
        this.scheme = this.cryptoManager.getDefaultCryptoScheme();
        this.publicKey = this.scheme.certificate().getPublicKey();

        if (this.scheme.privateKey().isEmpty()) {
            throw new IllegalStateException("scheme does not include a private key");
        }
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
        wrapper.setAlgorithm(TOKEN_ALGORITHM);
        wrapper.setCertificate(this.scheme.certificate());
        wrapper.setKid(keyId);
        wrapper.setPrivateKey(this.scheme.privateKey().get());
        wrapper.setPublicKey(this.publicKey);
        wrapper.setUse(KeyUse.SIG);
        wrapper.setType(KeyType.RSA);

        int ctSeconds = (int) (System.currentTimeMillis() / 1000);

        JsonWebToken token = new JsonWebToken()
            .id(Util.generateUUID())
            .type(CloudAuthTokenType.STANDARD.toString())
            .issuer(this.jwtIssuer)
            .subject(username)
            .audience(ownerKey)
            .issuedAt(ctSeconds)
            .expiration(ctSeconds + this.jwtTokenTTL)
            .notBefore(ctSeconds);

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
        wrapper.setAlgorithm(TOKEN_ALGORITHM);
        wrapper.setCertificate(this.scheme.certificate());
        wrapper.setKid(keyId);
        wrapper.setPrivateKey(this.scheme.privateKey().get());
        wrapper.setPublicKey(this.publicKey);
        wrapper.setUse(KeyUse.SIG);
        wrapper.setType(KeyType.RSA);

        int ctSeconds = (int) (System.currentTimeMillis() / 1000);

        JsonWebToken token = new JsonWebToken()
            .id(Util.generateUUID())
            .type(CloudAuthTokenType.ANONYMOUS.toString())
            .issuer(this.jwtIssuer)
            .subject(username)
            .audience(consumerUuid)
            .issuedAt(ctSeconds)
            .expiration(ctSeconds + this.anonJwtTokenTTL)
            .notBefore(ctSeconds);

        return new JWSBuilder()
            .kid(keyId)
            .type("JWT")
            .jsonContent(token)
            .sign(new AsymmetricSignatureSignerContext(wrapper));
    }

}
