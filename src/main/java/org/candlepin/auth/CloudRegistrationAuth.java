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

import org.candlepin.auth.CloudAuthTokenGenerator.ValidationResult;
import org.candlepin.config.ConfigProperties;
import org.candlepin.config.Configuration;
import org.candlepin.model.Owner;
import org.candlepin.model.OwnerCurator;
import org.candlepin.resteasy.filter.AuthUtil;

import org.jboss.resteasy.spi.HttpRequest;
import org.keycloak.representations.AccessToken;
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

        String token = authChunks[1];
        ValidationResult result = this.cloudTokenGenerator.validateToken(token, CloudAuthTokenType.STANDARD);
        if (!result.isValid()) {
            return null;
        }

        String ownerKey = result.audience();

        log.info("Token type used for authentication: {}", CloudAuthTokenType.STANDARD);
        return this.createPrincipal(ownerKey);
    }

    /**
     * Creates a principal with the minimum amount of information and access to complete a
     * user registration.
     *
     * @param ownerKey
     *  the key of an organization in which the principal will have authorization to register clients
     * @return
     *  a minimal {@link Principal} representing the cloud registration token
     */
    private CloudConsumerPrincipal createPrincipal(String ownerKey) {
        Owner owner = this.ownerCurator.getByKey(ownerKey);
        if (owner != null) {
            return new CloudConsumerPrincipal(owner);
        }
        return null;
    }

}
