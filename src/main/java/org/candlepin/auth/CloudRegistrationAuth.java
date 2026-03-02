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
import org.candlepin.model.Owner;
import org.candlepin.model.OwnerCurator;
import org.candlepin.resteasy.filter.AuthUtil;

import org.jboss.resteasy.spi.HttpRequest;
import org.keycloak.common.VerificationException;
import org.keycloak.representations.AccessToken;
import org.keycloak.representations.JsonWebToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

import javax.inject.Inject;



/**
 * AuthenticationProvider that accepts an {@link AccessToken} generated from an earlier call to the
 * CloudRegistration authorize endpoint
 */
public class CloudRegistrationAuth implements AuthProvider {
    private static final Logger log = LoggerFactory.getLogger(CloudRegistrationAuth.class);

    private static final String AUTH_TYPE = "Bearer";

    private final Configuration config;
    private final OwnerCurator ownerCurator;
    private final CloudAuthTokenGenerator cloudTokenGenerator;

    private final boolean enabled;

    @Inject
    public CloudRegistrationAuth(Configuration config,
        OwnerCurator ownerCurator,
        CloudAuthTokenGenerator cloudTokenManager) {

        this.config = Objects.requireNonNull(config);
        this.ownerCurator = Objects.requireNonNull(ownerCurator);
        this.cloudTokenGenerator = Objects.requireNonNull(cloudTokenManager);

        this.enabled = this.config.getBoolean(ConfigProperties.CLOUD_AUTHENTICATION);
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
            JsonWebToken token = this.cloudTokenGenerator.validateToken(authChunks[1]);

            String actualType = token.getType();
            if (!CloudAuthTokenType.STANDARD.equalsType(actualType)) {
                log.debug("Invalid token type. Expected: {}, but was {}",
                    CloudAuthTokenType.STANDARD, actualType);
                return null;
            }

            String subject = token.getSubject();
            if (subject == null || subject.isEmpty()) {
                log.debug("Token contains a null or empty subject");
                return null;
            }

            String[] audiences = token.getAudience();
            String audience = audiences != null && audiences.length > 0 ? audiences[0] : null;
            if (audience == null || audience.isEmpty()) {
                log.debug("Token contains a null or empty audience");
                return null;
            }

            String ownerKey = audience;

            log.info("Token type used for authentication: {}", CloudAuthTokenType.STANDARD);
            return this.createPrincipal(ownerKey, subject);
        }
        catch (VerificationException e) {
            log.debug("Invalid token", e);
        }

        return null;
    }

    /**
     * Creates a principal with the minimum amount of information and access to complete a
     * user registration.
     *
     * @param ownerKey
     *  the key of an organization in which the principal will have authorization to register clients
     *
     * @param username
     *  the username used to create the principal
     *
     * @return
     *  a minimal {@link Principal} representing the cloud registration token
     */
    private CloudConsumerPrincipal createPrincipal(String ownerKey, String username) {
        Owner owner = this.ownerCurator.getByKey(ownerKey);
        if (owner != null) {
            return new CloudConsumerPrincipal(owner, username);
        }
        return null;
    }

}
