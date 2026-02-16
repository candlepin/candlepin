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
import org.candlepin.model.AnonymousCloudConsumer;
import org.candlepin.model.AnonymousCloudConsumerCurator;
import org.candlepin.resteasy.filter.AuthUtil;

import org.jboss.resteasy.spi.HttpRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

import javax.inject.Inject;



/**
 * AuthenticationProvider that authenticates anonymous access tokens generated from the
 * CloudRegistration endpoint
 */
public class AnonymousCloudRegistrationAuth implements AuthProvider {
    private static final Logger log = LoggerFactory.getLogger(AnonymousCloudRegistrationAuth.class);

    private static final String AUTH_TYPE = "Bearer";

    private final Configuration config;
    private final AnonymousCloudConsumerCurator anonymousCloudConsumerCurator;
    private final CloudAuthTokenGenerator cloudTokenGenerator;

    private final boolean enabled;


    @Inject
    public AnonymousCloudRegistrationAuth(Configuration config,
        AnonymousCloudConsumerCurator anonymousCloudConsumerCurator,
        CloudAuthTokenGenerator cloudTokenGenerator) {

        this.config = Objects.requireNonNull(config);
        this.anonymousCloudConsumerCurator = Objects.requireNonNull(anonymousCloudConsumerCurator);
        this.cloudTokenGenerator = Objects.requireNonNull(cloudTokenGenerator);

        this.enabled = this.config.getBoolean(ConfigProperties.CLOUD_AUTHENTICATION);
    }

    @Override
    public Principal getPrincipal(HttpRequest httpRequest) {
        if (!this.enabled) {
            log.debug("Cloud authentication is disabled");
            return null;
        }

        String auth = AuthUtil.getHeader(httpRequest, "Authorization");
        if (auth.isEmpty()) {
            log.debug("Header with key value \"Authorization\" is missing in http request");
            return null;
        }

        String[] authChunks = auth.split(" ");
        if (!AUTH_TYPE.equalsIgnoreCase(authChunks[0]) || authChunks.length != 2) {
            // Not a type we handle; ignore it and hope another auth filter picks it up
            log.debug("Header is not Bearer token or unable to parse token");
            return null;
        }

        String token = authChunks[1];
        ValidationResult result = this.cloudTokenGenerator.validateToken(token, CloudAuthTokenType.ANONYMOUS);
        if (!result.isValid()) {
            return null;
        }

        String consumerUuid = result.audience();

        log.info("Token type used for authentication: {}", CloudAuthTokenType.ANONYMOUS);
        return this.createCloudUserPrincipal(consumerUuid);
    }

    private AnonymousCloudConsumerPrincipal createCloudUserPrincipal(String consumerUuid) {
        AnonymousCloudConsumer consumer = anonymousCloudConsumerCurator.getByUuid(consumerUuid);
        if (consumer == null) {
            log.debug("Anonymous cloud consumer with UUID {} could not be found", consumerUuid);
            return null;
        }

        log.info("Principal created for anonymous cloud consumer UUID {}", consumerUuid);
        return new AnonymousCloudConsumerPrincipal(consumer);
    }

}
