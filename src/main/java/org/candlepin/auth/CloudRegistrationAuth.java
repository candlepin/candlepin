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

import org.candlepin.auth.permissions.OwnerPermission;
import org.candlepin.auth.permissions.Permission;
import org.candlepin.config.ConfigProperties;
import org.candlepin.config.Configuration;
import org.candlepin.config.ConversionException;
import org.candlepin.model.Owner;
import org.candlepin.model.OwnerCurator;
import org.candlepin.pki.CertificateReader;
import org.candlepin.resteasy.filter.AuthUtil;
import org.candlepin.service.CloudRegistrationAdapter;
import org.candlepin.service.exception.CloudRegistrationAuthorizationException;
import org.candlepin.service.exception.MalformedCloudRegistrationException;
import org.candlepin.service.model.CloudRegistrationInfo;
import org.candlepin.util.Util;

import org.jboss.resteasy.spi.HttpRequest;
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
import org.xnap.commons.i18n.I18n;

import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.ws.rs.core.Context;



/**
 * AuthenticationProvider that accepts an {@link AccessToken} generated from an earlier call to the
 * CloudRegistration authorize endpoint
 */
public class CloudRegistrationAuth implements AuthProvider {
    private static Logger log = LoggerFactory.getLogger(CloudRegistrationAuth.class);

    private static final String TOKEN_ALGORITHM = Algorithm.RS512;
    private static final String TOKEN_SUBJECT_DEFAULT = "cloud_auth";
    private static final String AUTH_TYPE = "Bearer";
    private static final String TOKEN_TYPE = "CP-Cloud-Registration";

    @Context private ServletRequest servletRequest;
    @Context private ServletResponse servletResponse;

    private final Configuration config;
    private final Provider<I18n> i18nProvider;
    private final CertificateReader certificateReader;
    private final CloudRegistrationAdapter cloudRegistrationAdapter;
    private final OwnerCurator ownerCurator;

    private final boolean enabled;
    private final String jwtIssuer;
    private final int jwtTokenTTL; // seconds

    private final X509Certificate certificate;
    private final PublicKey publicKey;
    private final PrivateKey privateKey;


    @Inject
    public CloudRegistrationAuth(Configuration config, Provider<I18n> i18nProvider,
        CertificateReader certificateReader, CloudRegistrationAdapter cloudRegistrationAdapter,
        OwnerCurator ownerCurator) {

        this.config = Objects.requireNonNull(config);
        this.i18nProvider = Objects.requireNonNull(i18nProvider);
        this.certificateReader = Objects.requireNonNull(certificateReader);
        this.cloudRegistrationAdapter = Objects.requireNonNull(cloudRegistrationAdapter);
        this.ownerCurator = Objects.requireNonNull(ownerCurator);

        // Pre-parse config values we'll be using a bunch
        try {
            this.enabled = this.config.getBoolean(ConfigProperties.CLOUD_AUTHENTICATION);

            this.jwtIssuer = this.config.getString(ConfigProperties.JWT_ISSUER);
            this.jwtTokenTTL = this.config.getInt(ConfigProperties.JWT_TOKEN_TTL);
        }
        catch (ConversionException e) {
            // Try to pretty up the exception for easy debugging
            throw new RuntimeException("Invalid value(s) found while parsing JWT configuration", e);
        }

        // Fetch our keys
        try {
            this.certificate = this.certificateReader.getCACert();
            this.publicKey = this.certificate.getPublicKey();
            this.privateKey = this.certificateReader.getCaKey();
        }
        catch (Exception e) {
            throw new RuntimeException("Unable to load public and private keys", e);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Principal getPrincipal(HttpRequest httpRequest) {
        if (!this.enabled) {
            // If cloud auth isn't enabled, don't even attempt to validate anything
            return null;
        }

        String auth = AuthUtil.getHeader(httpRequest, "Authorization");
        if (auth.isEmpty()) {
            // Auth header is empty; no type or token provided
            return null;
        }

        String[] authChunks = auth.split(" ");
        if (!AUTH_TYPE.equalsIgnoreCase(authChunks[0]) || authChunks.length != 2) {
            // Not a type we handle; ignore it and hope another auth filter picks it up
            return null;
        }

        try {
            TokenVerifier<JsonWebToken> verifier = TokenVerifier.create(authChunks[1], JsonWebToken.class)
                .publicKey(publicKey)
                .verify();

            JsonWebToken token = verifier.getToken();
            String[] audiences = token.getAudience();

            // Verify that the token is active and hasn't expired
            if (!token.isActive()) {
                throw new VerificationException("Token is not active or has expired");
            }

            // Verify the token has the JWT type we're expecting
            if (TOKEN_TYPE.equalsIgnoreCase(token.getType())) {
                // Pull the subject (username) and owner key(s) out of the token
                String subject = token.getSubject();
                String ownerKey = audiences != null && audiences.length > 0 ? audiences[0] : null;

                if (subject == null || subject.isEmpty()) {
                    throw new VerificationException("Token contains an invalid subject: " + subject);
                }

                if (ownerKey == null || ownerKey.isEmpty()) {
                    throw new VerificationException("Token contains an invalid audience: " + ownerKey);
                }

                log.info("Token type used for authentication: {}", TOKEN_TYPE);
                return this.createCloudUserPrincipal(subject, ownerKey);
            }
        }
        catch (VerificationException e) {
            log.debug("Cloud registration token validation failed:", e);

            // Impl note:
            // Since we're using a common/standard auth type (bearer), we can't immediately fail
            // out here, as it's possible the token will be verified by another provider
        }

        return null;
    }

    /**
     * Creates a dummy user principal with the minimum amount of information and access to complete
     * a user registration. The username of the principal will be the subject provided.
     *
     * @param subject
     *  the subject for which to create the principal; will be used as the username
     *
     * @param ownerKey
     *  the key of an organization in which the principal will have authorization to register
     *  clients
     *
     * @return
     *  a minimal UserPrincipal representing the cloud registration token
     */
    private UserPrincipal createCloudUserPrincipal(String subject, String ownerKey) {
        Owner owner = this.ownerCurator.getByKey(ownerKey);
        if (owner == null) {
            // If the owner does not exist, we might be creating it on client registration, so
            // make a fake owner to pass into our permission object
            owner = new Owner(ownerKey, ownerKey);
        }

        List<Permission> permissions = Arrays.asList(
            new OwnerPermission(owner, Access.CREATE)
            // Add any additional permissions here as needed
        );

        return new UserPrincipal(subject, permissions, false);
    }

    /**
     * Validates the provided cloud registration information, and generates a registration token if
     * valid
     *
     * @param principal
     *  the principal for which to generate the token
     *
     * @param cloudRegistrationInfo
     *  The registration information to validate
     *
     * @throws UnsupportedOperationException
     *  if the current Candlepin configuration does not support cloud registration
     *
     * @throws CloudRegistrationAuthorizationException
     *  if cloud registration is not permitted for the cloud provider or account holder specified by
     *  the cloud registration details
     *
     * @throws MalformedCloudRegistrationException
     *  if the cloud registration details are null, incomplete, or malformed
     *
     * @return
     *  a registration token to be used for completing registration for the client identified by the
     *  specified cloud registration details
     */
    public String generateRegistrationToken(Principal principal, CloudRegistrationInfo cloudRegistrationInfo)
        throws CloudRegistrationAuthorizationException, MalformedCloudRegistrationException {

        if (!this.enabled) {
            throw new UnsupportedOperationException(
                "Cloud registration is not enabled on this Candlepin instance");
        }

        if (principal == null) {
            throw new IllegalArgumentException("principal is null");
        }

        if (cloudRegistrationInfo == null) {
            throw new IllegalArgumentException("cloudRegistrationInfo is null");
        }

        String ownerKey = this.cloudRegistrationAdapter.resolveCloudRegistrationData(cloudRegistrationInfo);
        if (ownerKey == null) {
            String errmsg = this.i18nProvider.get()
                .tr("cloud provider or account details could not be resolved to an organization");

            throw new CloudRegistrationAuthorizationException(errmsg);
        }

        return this.buildRegistrationToken(principal, ownerKey);
    }

    /**
     * Creates a new cloud registration token for the specific owner key. The owner/organization
     * will be set as the subject of the token, and need not explicitly exist locally in Candlepin.
     *
     * @param principal
     *  the principal for which the token will be generated
     *
     * @param ownerKey
     *  The key of the owner/organization for which the token will be generated
     *
     * @return
     *  an encrypted JWT token string
     */
    private String buildRegistrationToken(Principal principal, String ownerKey) {
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

        int ctSeconds = (int) (System.currentTimeMillis() / 1000);

        JsonWebToken token = new JsonWebToken()
            .id(Util.generateUUID())
            .type(TOKEN_TYPE)
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

}
