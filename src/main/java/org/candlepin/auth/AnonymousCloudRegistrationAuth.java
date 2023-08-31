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
import org.candlepin.config.ConversionException;
import org.candlepin.model.AnonymousCloudConsumer;
import org.candlepin.model.AnonymousCloudConsumerCurator;
import org.candlepin.pki.CertificateReader;
import org.candlepin.resteasy.filter.AuthUtil;

import org.jboss.resteasy.spi.HttpRequest;
import org.keycloak.TokenVerifier;
import org.keycloak.common.VerificationException;
import org.keycloak.representations.JsonWebToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.PublicKey;
import java.security.cert.X509Certificate;
import java.util.Objects;

import javax.inject.Inject;



/**
 * AuthenticationProvider that authenticates anonymous access tokens generated from the CloudRegistration endpoint
 */
public class AnonymousCloudRegistrationAuth implements AuthProvider {
    private static Logger log = LoggerFactory.getLogger(AnonymousCloudRegistrationAuth.class);

    private static final String AUTH_TYPE = "Bearer";

    private final Configuration config;
    private final AnonymousCloudConsumerCurator anonymousCloudConsumerCurator;
    private final CertificateReader certificateReader;

    private final boolean enabled;
    private final PublicKey publicKey;

    @Inject
    public AnonymousCloudRegistrationAuth(Configuration config,
        AnonymousCloudConsumerCurator anonymousCloudConsumerCurator,
        CertificateReader certificateReader) {
        this.config = Objects.requireNonNull(config);
        this.anonymousCloudConsumerCurator = Objects.requireNonNull(anonymousCloudConsumerCurator);
        this.certificateReader = Objects.requireNonNull(certificateReader);

        // Pre-parse config values
        try {
            this.enabled = this.config.getBoolean(ConfigProperties.CLOUD_AUTHENTICATION);
        }
        catch (ConversionException e) {
            // Try to pretty up the exception for easy debugging
            throw new RuntimeException("Invalid value(s) found while parsing JWT configuration", e);
        }

        // Fetch our keys
        try {
            X509Certificate certificate = this.certificateReader.getCACert();
            this.publicKey = certificate.getPublicKey();
        }
        catch (Exception e) {
            throw new RuntimeException("Unable to load public and private keys", e);
        }
    }

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
            if (CloudAuthTokenType.ANONYMOUS.equalsType(token.getType())) {
                String subject = token.getSubject();
                String consumerUuid = audiences != null && audiences.length > 0 ? audiences[0] : null;

                if (subject == null || subject.isEmpty()) {
                    throw new VerificationException("Token contains an invalid subject: " + subject);
                }

                if (consumerUuid == null || consumerUuid.isEmpty()) {
                    throw new VerificationException("Token contains an invalid audience: " + consumerUuid);
                }

                log.info("Token type used for authentication: {}", CloudAuthTokenType.ANONYMOUS);
                return this.createCloudUserPrincipal(subject, consumerUuid);
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

    private UserPrincipal createCloudUserPrincipal(String subject, String consumerUuid) {
        AnonymousCloudConsumer consumer = anonymousCloudConsumerCurator.getByUuid(consumerUuid);

        // TODO: Handle as part of CANDLEPIN-625

        return null;
    }

}
